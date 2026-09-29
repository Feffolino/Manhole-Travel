"""Software preview of a Java item model in the inventory GUI (orthographic, like ItemRenderer).

Applies the model's `display.gui` (translate, rotationXYZ, scale), the -0.5 centring, element rotations,
per-face uvs and vanilla directional face shading, then paints texel quads back to front.
Usage: python art/preview_item.py <item_model.json> <out.png> [slot_px=48]
"""
import json
import math
import os
import sys

from PIL import Image, ImageDraw

ASSETS = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets")
SHADE = {"up": 1.0, "down": 0.5, "north": 0.8, "south": 0.8, "west": 0.6, "east": 0.6}


def load_model(ref):
    ns, path = ref.split(":") if ":" in ref else ("minecraft", ref)
    with open(os.path.join(ASSETS, ns, "models", path + ".json"), encoding="utf-8") as f:
        return json.load(f)


def resolve(model):
    """Merge parent chain: elements/textures from the nearest model that has them, display per slot."""
    chain = [model]
    while "parent" in chain[-1]:
        chain.append(load_model(chain[-1]["parent"]))
    els, tex, disp = None, {}, {}
    for m in reversed(chain):
        tex.update(m.get("textures", {}))
        disp.update(m.get("display", {}))
        if "elements" in m:
            els = m["elements"]
    return els, tex, disp


def tex_image(tex, var):
    ref = var
    while ref.startswith("#"):
        ref = tex[ref[1:]]
    ns, path = ref.split(":")
    return Image.open(os.path.join(ASSETS, ns, "textures", path + ".png")).convert("RGBA")


def rot_axis(p, axis, ang, o):
    a = math.radians(ang)
    c, s = math.cos(a), math.sin(a)
    x, y, z = p[0] - o[0], p[1] - o[1], p[2] - o[2]
    if axis == "x":
        y, z = y * c - z * s, y * s + z * c
    elif axis == "y":
        x, z = x * c + z * s, -x * s + z * c
    else:
        x, y = x * c - y * s, x * s + y * c
    return (x + o[0], y + o[1], z + o[2])


def rot_xyz(v, r):
    # JOML rotationXYZ(ax, ay, az): v' = Rx * Ry * Rz * v
    v = rot_axis(v, "z", r[2], (0, 0, 0))
    v = rot_axis(v, "y", r[1], (0, 0, 0))
    return rot_axis(v, "x", r[0], (0, 0, 0))


def face_grid(e, face):
    """Corner positions (model px) of the face, as a function (s, t) in 0..1 -> xyz, matching uv u->s, v->t."""
    (x0, y0, z0), (x1, y1, z1) = e["from"], e["to"]
    if face == "up":
        return lambda s, t: (x0 + (x1 - x0) * s, y1, z0 + (z1 - z0) * t)
    if face == "down":
        return lambda s, t: (x0 + (x1 - x0) * s, y0, z1 - (z1 - z0) * t)
    if face == "north":
        return lambda s, t: (x1 - (x1 - x0) * s, y1 - (y1 - y0) * t, z0)
    if face == "south":
        return lambda s, t: (x0 + (x1 - x0) * s, y1 - (y1 - y0) * t, z1)
    if face == "west":
        return lambda s, t: (x0, y1 - (y1 - y0) * t, z0 + (z1 - z0) * s)
    return lambda s, t: (x1, y1 - (y1 - y0) * t, z1 - (z1 - z0) * s)


def render(model_json, out, slot=48, scale_up=4):
    model = json.load(open(model_json, encoding="utf-8"))
    els, tex, disp = resolve(model)
    g = disp.get("gui", {})
    tr = [v / 16 for v in g.get("translation", [0, 0, 0])]
    rt = g.get("rotation", [0, 0, 0])
    sc = g.get("scale", [1, 1, 1])
    R = slot * scale_up
    quads = []
    for e in els:
        for face, fd in e["faces"].items():
            img = tex_image(tex, fd["texture"])
            tw, th = img.size
            u0, v0, u1, v1 = fd.get("uv", [0, 0, 16, 16])
            n_u = max(1, int(round(abs(u1 - u0) * tw / 16)))
            n_v = max(1, int(round(abs(v1 - v0) * th / 16)))
            grid = face_grid(e, face)

            def world(s, t):
                p = grid(s, t)
                if "rotation" in e:
                    r = e["rotation"]
                    p = rot_axis(p, r["axis"], r["angle"], r["origin"])
                v = tuple(((p[i] / 16) - 0.5) * sc[i] for i in range(3))
                v = rot_xyz(v, rt)
                return tuple(v[i] + tr[i] for i in range(3))

            for i in range(n_u):
                for j in range(n_v):
                    s0, s1 = i / n_u, (i + 1) / n_u
                    t0, t1 = j / n_v, (j + 1) / n_v
                    pts = [world(s0, t0), world(s1, t0), world(s1, t1), world(s0, t1)]
                    uu = u0 + (u1 - u0) * (i + 0.5) / n_u
                    vv = v0 + (v1 - v0) * (j + 0.5) / n_v
                    c = img.getpixel((min(tw - 1, int(uu * tw / 16)), min(th - 1, int(vv * th / 16))))
                    if c[3] < 128:
                        continue
                    k = SHADE[face]
                    col = (int(c[0] * k), int(c[1] * k), int(c[2] * k), 255)
                    depth = sum(p[2] for p in pts) / 4
                    quads.append((depth, pts, col))
    quads.sort(key=lambda q: q[0])
    canvas = Image.new("RGBA", (R, R), (0, 0, 0, 0))
    d = ImageDraw.Draw(canvas)
    for _, pts, col in quads:
        d.polygon([((p[0] + 0.5) * R, (0.5 - p[1]) * R) for p in pts], fill=col)
    icon = canvas.resize((slot, slot), Image.BOX)
    # inventory slot mock-up: vanilla-like grey slot, then the icon at 1x (slot px) and a big 6x view
    slot_bg = Image.new("RGBA", (slot + 4, slot + 4), (139, 139, 139, 255))
    ImageDraw.Draw(slot_bg).rectangle([0, 0, slot + 3, slot + 3], outline=(55, 55, 55, 255))
    slot_bg.paste(icon, (2, 2), icon)
    big = slot_bg.resize(((slot + 4) * 5, (slot + 4) * 5), Image.NEAREST)
    sheet = Image.new("RGBA", (big.width + slot + 40, big.height + 20), (198, 198, 198, 255))
    sheet.paste(slot_bg, (10, 10), slot_bg)
    sheet.paste(big, (slot + 30, 10), big)
    sheet.save(out)


if __name__ == "__main__":
    render(sys.argv[1], sys.argv[2], int(sys.argv[3]) if len(sys.argv) > 3 else 48)
