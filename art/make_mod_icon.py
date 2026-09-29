"""Mod icon (CurseForge project avatar) as 64x64 pixel art, scaled x16 to 1024x1024 with nearest neighbour.

Scene: night asphalt, a round cast-iron manhole slid half open (dark shaft, ladder rungs on the far wall, a faint
light rising from below) and the red crowbar lying across the kerb.
Run: python art/make_mod_icon.py  -> art/mod_icon_1024.png (+ a 256 copy as the in-game mod list logo)
"""
import math
import os
import random
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(__file__))
import make_assets as A  # noqa: E402

N = 64
rgb, mix, shade = A.rgb, A.mix, A.shade
rnd = random.Random(7)
img = Image.new("RGBA", (N, N))
p = img.load()

# ---- asphalt with grit, a worn lane marking and a soft vignette
for y in range(N):
    for x in range(N):
        c = rgb("#2b2c31") if rnd.random() < 0.55 else rgb("#25262a")
        if rnd.random() < 0.06:
            c = rgb("#3a3b41")
        if 44 <= x + y // 3 <= 46 and y > 40:                      # faded lane paint, bottom-left
            c = mix(c, rgb("#b9a66a"), 0.35)
        d = math.hypot(x - 31.5, y - 31.5) / 45
        p[x, y] = shade(c, 1.0 - 0.45 * d * d)

CX, CY, R = 31.5, 29.5, 20.0

# ---- iron frame ring (square seat under a round cover, like the City Manhole)
for y in range(N):
    for x in range(N):
        d = math.hypot(x - CX, y - CY)
        if R - 0.5 < d <= R + 2.2:
            lit = ((CX - x) + (CY - y)) / (2 * R)
            c = mix(A.IRON["base"], A.IRON["edge"] if lit > 0 else A.IRON["dark"], min(1, abs(lit) * 1.6))
            if d > R + 1.4:
                c = rgb("#141416")
            p[x, y] = c

# ---- the shaft: dark, brick courses, ladder on the far (north) wall, faint light from below
for y in range(N):
    for x in range(N):
        d = math.hypot(x - CX, y - CY)
        if d <= R - 0.5:
            depth = d / R
            course = int(d * 1.3)
            brick = rgb("#4f3426") if course % 2 else rgb("#3f2a1f")
            c = mix((6, 7, 9, 255), brick, max(0.0, depth - 0.35) ** 1.4)
            glow = max(0.0, 1 - d / 9)
            c = mix(c, rgb("#5e7a6a"), 0.35 * glow * glow)           # cold light far below
            p[x, y] = c
for ry, t in [(12, 1.0), (15, 0.7), (18, 0.45), (21, 0.25)]:
    for x in range(26, 38):
        p[x, ry] = mix(p[x, ry], rgb("#a4a4ac"), t)
for ry in range(11, 23):
    t = max(0.0, 1 - (ry - 11) / 12)
    for x in (25, 38):
        p[x, ry] = mix(p[x, ry], rgb("#7a7a82"), t)

# ---- the cover, slid toward the bottom-right, resting on the frame (a lens of it overlaps the hole)
OX, OY = CX + 11, CY + 13
for y in range(N):
    for x in range(N):
        d = math.hypot(x - OX, y - OY)
        ds = math.hypot(x - OX - 1.5, y - OY - 2)
        if d > R + 0.6:
            if ds <= R + 0.6 and math.hypot(x - CX, y - CY) > R - 0.5:
                p[x, y] = shade(p[x, y], 0.55)                       # drop shadow on the asphalt
            continue
        if d > R - 0.4:
            c = rgb("#141416")
        else:
            lit = ((OX - x) + (OY - y)) / (2 * R)
            if d > R - 2.5:
                c = mix(A.IRON["base"], A.IRON["edge"] if lit > 0 else A.IRON["dark"], min(1, abs(lit) * 1.7))
            elif d > R - 3.5:
                c = A.IRON["dark"]
            elif d < 4.2:
                c = A.IRON["light"] if d < 3.2 else A.IRON["dark"]
            elif 7.5 < d < 8.6:
                c = A.IRON["dark"]
            else:
                c = mix(A.IRON["base"], A.IRON["light"], 0.25 + 0.35 * max(0, lit))
                if (x + y) % 4 == 0 or (x - y) % 4 == 0:
                    c = mix(c, A.IRON["dark"], 0.5)
            if rnd.random() < 0.04:
                c = mix(c, rnd.choice(A.RUST), 0.6)
        p[x, y] = c
for sx in (-13, 13):                                                # pry slots
    for dy in (0, 1):
        p[int(OX + sx), int(OY) + dy] = rgb("#050506")

# ---- crowbar across the bottom-left, 2x the inventory sprite
bar = A.draw_crowbar_icon().resize((32, 32), Image.NEAREST)
shadow = Image.new("RGBA", bar.size, (0, 0, 0, 0))
sp = shadow.load()
bp = bar.load()
for y in range(32):
    for x in range(32):
        if bp[x, y][3]:
            sp[x, y] = (0, 0, 0, 120)
img.alpha_composite(shadow, (3, 31))
img.alpha_composite(bar, (1, 29))

out = os.path.join(os.path.dirname(__file__), "mod_icon_1024.png")
img.resize((1024, 1024), Image.NEAREST).save(out)
img.resize((256, 256), Image.NEAREST).save(os.path.join(os.path.dirname(__file__), "..", "src", "main",
                                                        "resources", "manholes_logo.png"))
print("wrote", out)
