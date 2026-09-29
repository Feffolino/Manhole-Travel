"""Generates the manhole + crowbar models and pixel-art textures.

Run:  python art/make_assets.py      (from the project root)

Manhole models are 24x24 px (1.5 blocks) centred on the block: they overhang 4 px on each side,
the hitbox in code stays 1x1. The top of each model is textured as ONE top-down picture
(48x48 px, 2 texture px per model px) so the frame and cover line up without seams.
Re-running overwrites only the files listed in OUTPUTS below.
"""
import json
import math
import os
import random
import sys

from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "manholes")
TEX = os.path.join(ROOT, "textures")
MOD = os.path.join(ROOT, "models")

# ---------------------------------------------------------------- palette helpers

def rgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c[:3]) + (c[3],)


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3)) + (255,)


IRON = {"base": rgb("#4a4a4f"), "dark": rgb("#2e2e33"), "light": rgb("#6b6b72"), "edge": rgb("#85858c")}
RUST = [rgb("#6e3b1f"), rgb("#8a4a22"), rgb("#a45d2a"), rgb("#5a2f18")]
HOME = {"base": rgb("#3f5a3a"), "dark": rgb("#2a3d27"), "light": rgb("#58784f"), "edge": rgb("#7d9a72")}
HAZARD = rgb("#d9a520")

# ---------------------------------------------------------------- manhole atlas (64x64)
#  (0,0)-(48,48)   top-down picture of frame + cover   -> uv 0..12
#  rows 48..52     frame side  (1 model px tall)        -> uv v 12..13
#  rows 52..58     cover side  (1.5 model px tall)      -> uv v 13..14.5
#  (48,0)-(64,16)  underside / plain dark iron          -> uv 12..16 , 0..4
S = 2          # texture px per model px on the top picture
OFF = 4        # model overhang: model x=-4 is picture x=0
FRAME_IN = 3   # frame bar width (model px)


COVER_R = 9.0   # cover radius in model px, centre (8, 8)


def _row_half(zc):
    """Half extent (whole model px) of the cover row whose cells have z-centre zc: a cell (x..x+1) belongs to
    the cover when its centre is inside the circle. Rows are therefore symmetric around x = 8."""
    dz = zc - 8
    a = 0
    while (a + 0.5) ** 2 + dz * dz <= COVER_R ** 2:
        a += 1
    return a


def cover_rects():
    """The cover as non-overlapping row bands [(x0, z0, x1, z1)], stepped at 1 model px: this is the PHYSICAL
    edge of the cover. The texture mask below is computed from the same bands, so the painted rim is exactly
    the cut edge (no dark steps, no overhanging art)."""
    rows = []
    for z in range(-2, 18):
        a = _row_half(z + 0.5)
        if a > 0:
            rows.append((z, a))
    rects = []
    for z, a in rows:
        if rects and rects[-1][4] == a and rects[-1][3] == z:
            x0, z0, x1, _, _ = rects[-1]
            rects[-1] = (x0, z0, x1, z + 1, a)
        else:
            rects.append((8 - a, z, 8 + a, z + 1, a))
    return [(x0, z0, x1, z1) for x0, z0, x1, z1, _ in rects]


_COVER_CELLS = set()
for _x0, _z0, _x1, _z1 in cover_rects():
    for _x in range(_x0, _x1):
        for _z in range(_z0, _z1):
            _COVER_CELLS.add((_x, _z))


def cover_mask(px, py):
    """True if picture pixel (2 px per model px) lies on a cell of the physical cover."""
    return (px // S - OFF, py // S - OFF) in _COVER_CELLS


def cover_edge_dist(px, py, limit=4):
    """Distance in texture px from this cover pixel to the nearest non-cover pixel (capped at limit)."""
    for d in range(1, limit + 1):
        for ox, oy in ((d, 0), (-d, 0), (0, d), (0, -d)):
            if not cover_mask(px + ox, py + oy):
                return d
    return limit + 1


def in_frame(px, py):
    mx = px / S - OFF
    my = py / S - OFF
    return mx < -1 or mx >= 17 or my < -1 or my >= 17


def draw_manhole_atlas(pal, seed, home=False, open_=False):
    rnd = random.Random(seed)
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    p = img.load()
    # ---- top picture
    for y in range(48):
        for x in range(48):
            mx, my = x / S - OFF, y / S - OFF
            if in_frame(x, y):
                c = pal["base"]
                # bevel: inner and outer edges lighter
                d_out = min(x, y, 47 - x, 47 - y)
                d_in = min(abs(mx + 1), abs(mx - 17), abs(my + 1), abs(my - 17)) * S
                if d_out == 0:
                    c = pal["dark"]
                elif d_out == 1 or d_in < 1:
                    c = pal["edge"]
                # cast texture: little ridges every 4 px along the bar
                if (x + y) % 6 == 0 and d_out > 1:
                    c = shade(c, 0.88)
                if home and d_out > 1 and d_in >= 1:
                    # hazard chevrons on the home frame
                    if ((x + y) // 4) % 2 == 0:
                        c = HAZARD
                    else:
                        c = rgb("#1e1e1e")
            elif open_:
                c = (0, 0, 0, 0)  # the hole texture covers this area
            elif cover_mask(x, y):
                r = math.hypot(mx - 8 + 0.25, my - 8 + 0.25)
                e = cover_edge_dist(x, y)
                c = pal["base"]
                if e == 1:
                    c = pal["edge"]          # outer lip = the physical cut edge of the cover
                elif e == 2:
                    c = pal["light"]
                elif e == 3:
                    c = pal["dark"]          # groove just inside the lip
                elif 3.4 < r < 4.1:
                    c = pal["dark"]          # inner ring groove
                elif r <= 3.4:
                    c = pal["light"]         # centre boss
                    if r < 1.2:
                        c = pal["edge"]
                else:
                    # diamond anti-slip grid between the rings
                    gx, gy = (x + y) % 6, (x - y) % 6
                    if gx == 0 or gy == 0:
                        c = pal["light"]
                    elif gx == 3 and gy == 3:
                        c = pal["dark"]
                # two pry slots on the east/west rim
                if abs(my - 8) < 1.0 and (6.2 < abs(mx - 8) < 7.4):
                    c = rgb("#0d0d0f")
                if home and r <= 3.4 and (abs(mx - 8) < 0.6 or abs(my - 8) < 0.6):
                    c = HAZARD           # painted cross on the home cover
            else:
                # between round cover and square frame: the seat of the frame, darker
                c = pal["dark"]
            p[x, y] = c
    # ---- rust / grime pass (not on the home one, it's kept clean)
    if not home:
        for _ in range(26):
            cx, cy = rnd.randrange(48), rnd.randrange(48)
            rr = rnd.choice([1, 1, 1, 2, 2])
            col = rnd.choice(RUST)
            for yy in range(cy - rr, cy + rr + 1):
                for xx in range(cx - rr, cx + rr + 1):
                    if 0 <= xx < 48 and 0 <= yy < 48 and p[xx, yy][3] and p[xx, yy] != rgb("#0d0d0f"):
                        if (xx - cx) ** 2 + (yy - cy) ** 2 <= rr * rr and rnd.random() < 0.7:
                            p[xx, yy] = mix(p[xx, yy], col, 0.35 + rnd.random() * 0.25)
    else:
        for _ in range(12):
            cx, cy = rnd.randrange(48), rnd.randrange(48)
            if p[cx, cy][3]:
                p[cx, cy] = shade(p[cx, cy], 0.8)
    # ---- side strips
    for x in range(64):
        for y in range(48, 52):  # frame side
            c = pal["base"] if y in (49, 50) else (pal["edge"] if y == 48 else pal["dark"])
            if not home and rnd.random() < 0.18:
                c = mix(c, rnd.choice(RUST), 0.5)
            if home and y in (49, 50) and ((x // 3) % 2 == 0):
                c = HAZARD
            p[x, y] = c
        for y in range(52, 58):  # cover side
            c = pal["base"] if 53 <= y <= 56 else (pal["edge"] if y == 52 else pal["dark"])
            if x % 4 == 0 and 53 <= y <= 56:
                c = shade(c, 0.85)
            if not home and rnd.random() < 0.15:
                c = mix(c, rnd.choice(RUST), 0.5)
            p[x, y] = c
    # ---- underside
    for y in range(0, 16):
        for x in range(48, 64):
            p[x, y] = shade(pal["dark"], 0.8 + 0.1 * ((x + y) % 2))
    return img


def draw_hole(seed):
    """32x32: looking down the shaft. Round mouth, brick lining fading into black (ladder is geometry)."""
    rnd = random.Random(seed)
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 255))
    p = img.load()
    for y in range(32):
        for x in range(32):
            dx, dy = x - 15.5, y - 15.5
            r = math.hypot(dx, dy)
            if r > 15.2:
                c = IRON["dark"]                       # frame seat in the corners
                if (x + y) % 5 == 0:
                    c = shade(c, 0.8)
            elif r > 13.6:
                c = IRON["edge"] if r > 14.6 else IRON["base"]   # iron mouth ring
            else:
                depth = r / 13.6                        # 1 at the rim, 0 in the centre
                # concentric brick courses, darker the deeper
                course = int(r * 1.6)
                brick = rgb("#5b3a2a") if course % 2 else rgb("#4a2f22")
                ang = math.atan2(dy, dx)
                if int((ang + math.pi) * 6 + course) % 3 == 0:
                    brick = shade(brick, 0.7)          # mortar joints
                c = mix((6, 6, 8, 255), brick, max(0.0, depth - 0.25) ** 1.6)
            p[x, y] = c
    # (no painted ladder: the ladder is real geometry in the model, see make_looks.ladder_solid)
    # a few wet glints
    for _ in range(6):
        x, y = rnd.randrange(8, 24), rnd.randrange(14, 26)
        p[x, y] = rgb("#1c2a33")
    return img


# ---------------------------------------------------------------- manhole models

def top_uv(x0, z0, x1, z1):
    """uv (atlas units, 4px each) of the top picture for model rect x0..x1 / z0..z1."""
    return [(x0 + OFF) / 2, (z0 + OFF) / 2, (x1 + OFF) / 2, (z1 + OFF) / 2]


def cube(frm, to, top, side_v, name, tex="#manhole", shade_=True):
    x0, y0, z0 = frm
    x1, y1, z1 = to
    h = (y1 - y0)
    sv = [side_v, side_v + h * 4 / 4 * 1.0]  # 1 model px of height = 1 atlas unit? no: 4px per unit
    # side strips: frame side rows 48..52 = v 12..13 (1 model px), cover side rows 52..58 = v 13..14.5
    sv = [side_v, side_v + h]
    def su(a, b):
        return [((a + OFF) / 2) % 16, sv[0], ((b + OFF) / 2) % 16 if (b + OFF) / 2 <= 16 else 16, sv[1]]
    faces = {
        "up": {"uv": top, "texture": tex},
        "down": {"uv": [12, 0, 16, 4], "texture": tex},
        "north": {"uv": su(x0, x1), "texture": tex},
        "south": {"uv": su(x0, x1), "texture": tex},
        "west": {"uv": su(z0, z1), "texture": tex},
        "east": {"uv": su(z0, z1), "texture": tex},
    }
    return {"name": name, "from": list(frm), "to": list(to), "shade": shade_, "faces": faces}


FRAME_H = 1.0
COVER_H = 1.25


def frame_cubes():
    bars = [
        ("frame_n", (-4, 0, -4), (20, FRAME_H, -1)),
        ("frame_s", (-4, 0, 17), (20, FRAME_H, 20)),
        ("frame_w", (-4, 0, -1), (-1, FRAME_H, 17)),
        ("frame_e", (17, 0, -1), (20, FRAME_H, 17)),
    ]
    return [cube(f, t, top_uv(f[0], f[2], t[0], t[2]), 12, n) for n, f, t in bars]


SEAT_H = 0.5   # the frame's seat plate the cover rests on (closed model only)


def cover_cubes(dx=0.0, y=SEAT_H, dz=0.0):
    """The round cover built from the non-overlapping row bands of cover_rects(): no coplanar overlaps, and
    the silhouette is exactly the circle painted on the texture. dx/dz shift it (slid open)."""
    out = []
    for i, (xa, za, xb, zb) in enumerate(cover_rects()):
        frm = (xa + dx, y, za + dz)
        to = (xb + dx, y + COVER_H, zb + dz)
        # uv always from the closed picture position (the cover's own art moves with it)
        out.append(cube(frm, to, top_uv(xa, za, xb, zb), 13, f"cover_{i}"))
    return out


def seat_cube():
    """Dark seat plate filling the frame opening under the cover (otherwise the ground shows through
    between the round cover and the square frame)."""
    return cube((-1, 0, -1), (17, SEAT_H, 17), top_uv(-1, -1, 17, 17), 12, "seat")


DISPLAY_BLOCK = {
    "gui": {"rotation": [30, 45, 0], "translation": [0, 2, 0], "scale": [0.45, 0.45, 0.45]},
    "ground": {"translation": [0, 3, 0], "scale": [0.2, 0.2, 0.2]},
    "fixed": {"rotation": [90, 0, 0], "translation": [0, 0, -7], "scale": [0.4, 0.4, 0.4]},
    "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 3, 0], "scale": [0.3, 0.3, 0.3]},
    "firstperson_lefthand": {"rotation": [0, 45, 0], "translation": [0, 3, 0], "scale": [0.3, 0.3, 0.3]},
    "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.28, 0.28, 0.28]},
    "thirdperson_lefthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.28, 0.28, 0.28]},
    "head": {"translation": [0, 14, 0], "scale": [0.6, 0.6, 0.6]},
}


def model_closed():
    return {
        "credit": "Manhole Travel - generated by art/make_assets.py",
        "ambientocclusion": False,
        "textures": {"particle": "#manhole"},
        "elements": frame_cubes() + [seat_cube()] + cover_cubes(),
        "display": DISPLAY_BLOCK,
    }


def model_open():
    hole = {
        "name": "hole", "from": [-1, 0.02, -1], "to": [17, 0.05, 17], "shade": False,
        "faces": {"up": {"uv": [0, 0, 16, 16], "texture": "#hole"}},
    }
    # slid half open toward +z (south), the side OPPOSITE the ladder (painted on the north wall of the hole)
    return {
        "credit": "Manhole Travel - generated by art/make_assets.py",
        "ambientocclusion": False,
        "textures": {"particle": "#manhole"},
        "elements": frame_cubes() + [hole] + cover_cubes(dz=10, y=FRAME_H),
        "display": DISPLAY_BLOCK,
    }


# ---------------------------------------------------------------- crowbar

def draw_crowbar_3d():
    """32x32 texture for the 3D crowbar, 2 texture px per model px. Four vertical material strips,
    8 px (4 uv) wide each, running the full height: 0 red paint, 1 bare steel, 2 black tape, 3 rusty steel.
    Each strip has a lit edge, a shadowed edge, noise, scratches and chips so faces never read flat."""
    rnd = random.Random(23)
    img = Image.new("RGBA", (32, 32))
    p = img.load()
    mats = [
        (rgb("#9c2019"), rgb("#d0463a"), rgb("#5e110c")),   # paint: base, light, dark
        (rgb("#8f939a"), rgb("#c9ccd2"), rgb("#55585e")),   # steel
        (rgb("#232326"), rgb("#4a4a50"), rgb("#101012")),   # tape
        (rgb("#7a4424"), rgb("#a86434"), rgb("#4a2814")),   # rust
    ]
    for strip, (base, light, dark) in enumerate(mats):
        for y in range(32):
            for x in range(8):
                gx = strip * 8 + x
                # cylinder-ish shading across each 2 px (one model px) column: lit, mid, then darker edge
                col = x % 2
                c = mix(base, light, 0.35) if col == 0 else mix(base, dark, 0.25)
                # value noise along the length
                n = rnd.random()
                if n < 0.12:
                    c = mix(c, dark, 0.5)
                elif n > 0.9:
                    c = mix(c, light, 0.5)
                if strip == 0 and rnd.random() < 0.10:
                    c = mix(rgb("#b4b7bd"), rgb("#6c6f75"), rnd.random())   # chipped paint -> steel
                if strip == 1 and y % 7 == 3 and rnd.random() < 0.6:
                    c = light                                                # long scratches
                if strip == 2:
                    if y % 3 == 0:
                        c = mix(c, light, 0.45)                              # tape wrap edges
                    elif y % 3 == 2:
                        c = mix(c, dark, 0.6)
                if strip == 3 and rnd.random() < 0.25:
                    c = mix(c, rnd.choice(RUST), 0.6)                         # rust flakes
                p[gx, y] = c
    return img


def draw_crowbar_icon():
    """16x16 inventory sprite, hand-placed pixels. Legend: o outline, R red, r dark red, L light red,
    s steel, d dark steel, t tape, T tape highlight."""
    art = [
        "..........ooo...",
        ".........oRRLo..",
        "........oRr..Lo.",
        "........oRo..so.",
        ".......oRRo..do.",
        "......oRLo...o..",
        ".....oRro.......",
        "....oRso........",
        "...oTto.........",
        "..otTo..........",
        "..oTto..........",
        ".otTo...........",
        ".oRro...........",
        "osso............",
        "osdo............",
        "ooo.............",
    ]
    pal = {"o": rgb("#1a0907"), "R": rgb("#a8241c"), "r": rgb("#6d140f"), "L": rgb("#d4493a"),
           "s": rgb("#b4b7bd"), "d": rgb("#62656b"), "t": rgb("#1b1b1d"), "T": rgb("#46464b")}
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    p = img.load()
    for y, row in enumerate(art):
        for x, ch in enumerate(row):
            if ch in pal:
                p[x, y] = pal[ch]
    return img


def draw_map_icon():
    """32x32 item-style map marker: a round cast-iron manhole cover seen slightly from above, with a dark
    outline, rim highlight top-left, shadow bottom-right, grid pattern, pry slots and a drop shadow, so it
    reads like an inventory item on the map."""
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    p = img.load()
    cx, cy, R = 15.5, 14.5, 12.5
    out = rgb("#141416")
    for y in range(32):
        for x in range(32):
            d = math.hypot(x - cx, (y - cy) * 1.08)
            ds = math.hypot(x - cx - 1.5, (y - cy - 2.0) * 1.08)      # drop shadow
            if d > R + 1.0:
                if ds <= R + 1.0:
                    p[x, y] = (0, 0, 0, 110)
                continue
            if d > R:
                p[x, y] = out
                continue
            # light from top-left
            light = ((cx - x) + (cy - y)) / (2 * R)
            if d > R - 2.2:                                           # thick rim
                c = mix(IRON["base"], IRON["edge"] if light > 0 else IRON["dark"], min(1, abs(light) * 1.8))
            elif d > R - 3.2:
                c = IRON["dark"]                                      # groove
            elif d < 4.2:
                c = mix(IRON["light"], IRON["edge"], max(0, light))   # centre boss
                if d > 3.3:
                    c = IRON["dark"]
            else:
                c = mix(IRON["base"], IRON["light"], 0.3 + 0.4 * max(0, light))
                if (x + y) % 4 == 0 or (x - y) % 4 == 0:
                    c = mix(c, IRON["dark"], 0.55)                    # diamond grid
            p[x, y] = c
    # pry slots
    for (sx, sy) in [(6, 14), (7, 14), (24, 14), (25, 14)]:
        p[sx, sy] = rgb("#050506")
        p[sx, sy + 1] = rgb("#050506")
    # rust flecks (fixed, readable)
    for (x, y) in [(10, 7), (11, 7), (20, 20), (21, 21), (9, 19), (22, 9)]:
        p[x, y] = mix(p[x, y], RUST[1], 0.7)
    # specular glint on the rim
    for (x, y) in [(8, 5), (9, 4), (10, 4)]:
        p[x, y] = rgb("#b8b8c0")
    return img


def draw_pry_bar():
    """256x64 GUI sheet for the prying HUD bar.
      (0,0)   200x20  frame: riveted cast-iron plate with a recessed dark slot (slot inner area x6..193, y6..13)
      (0,24)  188x8   fill: hazard-amber, lit top edge, diagonal wear stripes (tiles horizontally)
      (0,36)  188x8   fill 'flash' variant (brighter, drawn for 2-3 ticks after each press)
      (0,48)  188x8   fill 'danger' variant (rust red, when the bar is decaying low)
      (200,0) 20x20   crowbar glyph for the left end
      (224,0) 16x16   key-cap background for the 'MASH [SPACE]' hint
    """
    img = Image.new("RGBA", (256, 64), (0, 0, 0, 0))
    p = img.load()
    rnd = random.Random(77)
    # ---- frame
    for y in range(20):
        for x in range(200):
            edge = x in (0, 199) or y in (0, 19)
            c = rgb("#141416") if edge else IRON["base"]
            if not edge and (y == 1 or x == 1):
                c = IRON["edge"]
            elif not edge and (y == 18 or x == 198):
                c = IRON["dark"]
            if 5 <= x <= 194 and 5 <= y <= 14:                  # recessed slot
                c = rgb("#0b0b0d") if (x in (5, 194) or y in (5, 14)) else rgb("#1a1a1e")
                if y == 6:
                    c = rgb("#101013")                              # inner shadow
            elif not edge and rnd.random() < 0.06:
                c = mix(c, rnd.choice(RUST), 0.5)
            p[x, y] = c
    for rx in (3, 196):                                             # rivets
        for ry in (3, 16):
            p[rx, ry] = IRON["edge"]; p[rx + 1 if rx < 100 else rx - 1, ry] = IRON["dark"]
    # tick marks every 25% on the slot's lower lip
    for q in range(1, 4):
        x = 6 + q * 47
        p[x, 15] = IRON["edge"]; p[x, 16] = IRON["light"]
    # ---- fills
    def fill(y0, base, light, dark):
        for y in range(8):
            for x in range(188):
                c = base
                if y == 0:
                    c = light
                elif y == 7:
                    c = dark
                if (x + y) % 8 < 2 and 0 < y < 7:
                    c = mix(c, dark, 0.45)                      # diagonal stripes
                if rnd.random() < 0.05 and 0 < y < 7:
                    c = mix(c, light, 0.5)
                p[x, y0 + y] = c
    fill(24, rgb("#d9a520"), rgb("#ffd95a"), rgb("#8a6410"))
    fill(36, rgb("#ffd24a"), rgb("#fff3b0"), rgb("#b88a1c"))
    fill(48, rgb("#a83a1c"), rgb("#e0643a"), rgb("#5a1c0c"))
    # ---- crowbar glyph (reuse the item sprite, scaled 1:1 into a 20x20 cell)
    icon = draw_crowbar_icon()
    img.paste(icon, (202, 2), icon)
    # ---- key cap
    for y in range(16):
        for x in range(16):
            edge = x in (0, 15) or y in (0, 15)
            c = rgb("#101012") if edge else (rgb("#d8d8dc") if y < 12 else rgb("#9a9aa0"))
            if not edge and y == 1:
                c = rgb("#f4f4f6")
            p[224 + x, y] = c
    return img


def crowbar_3d_model():
    """Gooseneck crowbar standing along +y: chisel at the bottom, hook curling toward +z at the top,
    split claw pointing down. Texture strips (v): 0-4 red paint, 4-8 steel, 8-12 tape, 12-16 rust."""
    STRIP = {"paint": 0, "steel": 4, "tape": 8, "rust": 12}   # uv u-offset of each material strip

    def box(name, frm, to, mat, seed, rot=None):
        """uv sized to the face (1 uv unit per model px = 2 texture px), clamped to its 4-unit strip;
        a per-face v offset from `seed` so faces of the same material show different texels."""
        dx, dy, dz = (to[i] - frm[i] for i in range(3))
        dims = {"north": (dx, dy), "south": (dx, dy), "east": (dz, dy), "west": (dz, dy),
                "up": (dx, dz), "down": (dx, dz)}
        f = {}
        for i, (k, (fu, fv)) in enumerate(dims.items()):
            fu, fv = min(fu, 4.0), min(fv, 16.0)
            u0 = STRIP[mat] + ((seed + i) % 2) * max(0.0, 4.0 - fu) / 2
            v0 = ((seed * 3 + i * 5) % 16) * max(0.0, 16.0 - fv) / 16
            f[k] = {"uv": [round(u0, 3), round(v0, 3), round(u0 + fu, 3), round(v0 + fv, 3)], "texture": "#bar"}
        e = {"name": name, "from": frm, "to": to, "faces": f}
        if rot:
            e["rotation"] = rot
        return e
    # No two elements share a same-facing coplanar face (that z-fights / flickers): each joined piece
    # differs in width by >= 0.1 px, and pieces meet end-to-end instead of overlapping where possible.
    # Anatomy of a gooseneck wrecking bar ("piede di porco"): one steel bar, all bends in ONE plane (y/z here).
    #  - top: ~180 deg gooseneck (a J) opening toward +z; the end comes back down almost parallel to the shaft,
    #    flattened into a claw split by a V notch (two prongs side by side along x, i.e. perpendicular to the
    #    bend plane); the claw tip leans slightly back toward the shaft.
    #  - bottom: flattened chisel, wide along x, kinked ~22.5 deg toward -z (opposite side to the hook opening).
    #  - paint on shaft/neck/bend, bare steel at both working ends.
    # No two elements share a same-facing coplanar face (z-fighting): widths differ by >= 0.1 px.
    els = [
        box("shaft", [7.5, 2, 7.5], [8.5, 12, 8.5], "paint", 1),                    # width 1.0
        box("grip_tape", [7.3, 3, 7.3], [8.7, 7, 8.7], "tape", 2),                  # 1.4
        box("rust_band", [7.4, 9.5, 7.4], [8.6, 10.3, 8.6], "rust", 3),             # 1.2
        box("neck", [7.6, 12, 7.6], [8.4, 14.6, 8.4], "paint", 4,                   # 0.8
            {"angle": 22.5, "axis": "x", "origin": [8, 12, 8]}),
        box("hook_top", [7.45, 14, 8.6], [8.55, 15, 11.6], "paint", 5),             # 1.1, top of the J
        box("hook_down", [7.6, 12.4, 10.8], [8.4, 14, 11.8], "steel", 6),           # 0.8, bare steel
        box("claw_l", [7.35, 11.2, 10.9], [7.9, 12.6, 11.7], "steel", 7,            # prongs lean to the shaft
            {"angle": 22.5, "axis": "x", "origin": [8, 12.6, 11.3]}),
        box("claw_r", [8.1, 11.2, 10.9], [8.65, 12.6, 11.7], "steel", 8,
            {"angle": 22.5, "axis": "x", "origin": [8, 12.6, 11.3]}),
        box("chisel", [7.25, 0.2, 7.75], [8.75, 2.2, 8.25], "steel", 9,             # 1.5 wide flat blade
            {"angle": 22.5, "axis": "x", "origin": [8, 2, 8]}),
    ]
    return {
        "credit": "Manhole Travel - generated by art/make_assets.py",
        "textures": {"bar": "manholes:item/crowbar_bar", "particle": "manholes:item/crowbar_bar"},
        "elements": els,
        "display": {
            # held like a real wrecking bar: gooseneck up, J opening to the OUTSIDE (never toward the target)
            "thirdperson_righthand": {"rotation": [0, 90, 0], "translation": [0, 3.5, 1], "scale": [0.85, 0.85, 0.85]},
            "thirdperson_lefthand": {"rotation": [0, -90, 0], "translation": [0, 3.5, 1], "scale": [0.85, 0.85, 0.85]},
            # first person: original size; J opens AWAY from the camera (y 180) so the two claw teeth sit
            # behind the front bar of the gooseneck
            "firstperson_righthand": {"rotation": [0, 180, 20], "translation": [1.13, 3.2, 1.13], "scale": [0.85, 0.85, 0.85]},
            "firstperson_lefthand": {"rotation": [0, 180, -20], "translation": [1.13, 3.2, 1.13], "scale": [0.85, 0.85, 0.85]},
            "head": {"rotation": [0, 0, 45], "translation": [0, 8, 0]},
            # inventory / JEI icon: pose set by the user in Blockbench (2026-09-29)
            "gui": {"rotation": [-109, -39, -113], "translation": [0.5, -1, 0], "scale": [1.05, 1.05, 1.05]},
            "fixed": {"rotation": [-109, -39, -113], "translation": [0.5, -1, 0], "scale": [1.05, 1.05, 1.05]},
            "ground": {"rotation": [0, 90, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
        },
    }


def crowbar_item_model():
    """The 3D crowbar everywhere, inventory/JEI included; the GUI pose lives in crowbar_3d's display."""
    return {"parent": "manholes:item/crowbar_3d", "gui_light": "side"}


# ---------------------------------------------------------------- write

# Textures the USER draws by hand (16x16 is fine): never overwritten once they exist, unless --force.
USER_DRAWN = {"gui/map_icon.png"}


def save_img(img, rel):
    path = os.path.join(TEX, rel)
    if rel in USER_DRAWN and os.path.exists(path) and "--force" not in sys.argv:
        print(f"kept {rel} (user-drawn; run with --force to regenerate)")
        return path
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)
    return path


def save_json(obj, rel):
    path = os.path.join(MOD, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2)
    return path


def main():
    # the plain world "manhole" block was removed in 1.5.0 (world covers: hatch/grate/cave_hole/city_manhole);
    # its iron atlas is still drawn because the mod logo is made from it (not shipped as a block texture)
    save_img(draw_manhole_atlas(IRON, 9), "block/manhole.png")
    save_img(draw_crowbar_3d(), "item/crowbar_bar.png")
    save_img(draw_map_icon(), "gui/map_icon.png")
    save_img(draw_pry_bar(), "gui/pry_bar.png")

    # the home manhole models/textures are made by make_looks.py (vanilla textures)
    save_json(crowbar_3d_model(), "item/crowbar_3d.json")
    save_json(crowbar_item_model(), "item/crowbar.json")
    print("done")


if __name__ == "__main__":
    main()
