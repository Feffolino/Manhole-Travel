"""Visible condition (rust level 1-3) for the covers: transparent decal textures + overlay shells of the lid parts.

For each look with a condition it writes:
  textures/block/cond_<material>_<level>.png           16x16 decals (transparent where the cover shows through)
  models/block/parts/<look>_lid_<i>_cond_<level>.json  the lid part's elements inflated by 0.02 px, every face
                                                        textured with the decal (cutout)
and adds "condition_overlays" to assets/manholes/looks/<look>.json.

Materials: iron -> rust (orange-brown flakes spreading from the edges, streaks at level 3);
           wood -> water stains and green mould; rock -> dirt and moss.
Run AFTER make_looks.py:  python art/make_conditions.py
"""
import json
import math
import os
import random
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(__file__))
import make_assets as A  # noqa: E402

TEX = os.path.join(A.ROOT, "textures", "block")
MOD = os.path.join(A.ROOT, "models", "block", "parts")
LOOKS = os.path.join(A.ROOT, "looks")
rgb, mix = A.rgb, A.mix

PALETTES = {
    "rust": [rgb("#8a4a22"), rgb("#a45d2a"), rgb("#6e3b1f"), rgb("#c07a3a"), rgb("#5a2f18")],
    "wood": [rgb("#2e2418"), rgb("#3a2e1e"), rgb("#4d5a2a"), rgb("#3e4a22"), rgb("#241c12")],
    "rock": [rgb("#4a3f30"), rgb("#5b4d3a"), rgb("#4f6b35"), rgb("#3f5a2a"), rgb("#6b7a3a")],
}
# how much of the 16x16 is covered at each level
COVERAGE = {1: 0.14, 2: 0.30, 3: 0.52}
# which lid parts get the overlay (ladders never do)
LOOK_MATERIAL = {"city": "rust", "grate": "rust", "hatch": "wood", "cave": "rock"}
BASE_OVERLAY = {"hatch"}   # looks whose frame weathers too (the wooden hatch's log frame)


def value_noise(seed):
    """Smooth 16x16 noise in 0..1 (bilinear from a 5x5 lattice), plus an edge bias: decay starts at the rims."""
    rnd = random.Random(seed)
    g = [[rnd.random() for _ in range(5)] for _ in range(5)]
    out = [[0.0] * 16 for _ in range(16)]
    for y in range(16):
        for x in range(16):
            gx, gy = x / 15 * 4, y / 15 * 4
            x0, y0 = int(gx), int(gy)
            x1, y1 = min(4, x0 + 1), min(4, y0 + 1)
            fx, fy = gx - x0, gy - y0
            a = g[y0][x0] * (1 - fx) + g[y0][x1] * fx
            b = g[y1][x0] * (1 - fx) + g[y1][x1] * fx
            v = a * (1 - fy) + b * fy
            edge = min(x, y, 15 - x, 15 - y) / 7.5          # 0 at the rim, 1 in the middle
            out[y][x] = 0.65 * v + 0.35 * (1 - edge) + rnd.random() * 0.12
    return out


def decal(material, level):
    """Clustered decal: the same noise field is thresholded higher at each level, so level 3 contains level 1."""
    pal = PALETTES[material]
    field = value_noise({"rust": 11, "wood": 23, "rock": 37}[material])
    flat = sorted(v for row in field for v in row)
    thr = flat[int(len(flat) * (1 - COVERAGE[level]))]
    rnd = random.Random(level * 101 + len(material))
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    p = img.load()
    for y in range(16):
        for x in range(16):
            v = field[y][x]
            if v >= thr:
                k = min(1.0, (v - thr) / 0.25)
                c = pal[int(k * 2.99)] if rnd.random() > 0.25 else rnd.choice(pal)
                p[x, y] = c
    if material == "rust" and level == 3:                     # rust streaks running down
        for x in (3, 8, 12):
            for y in range(0, 13):
                if p[x, y][3] == 0 and rnd.random() < 0.7:
                    p[x, y] = mix(pal[0], pal[2], rnd.random())
    return img


def decal_wood_soft(level):
    """Soft water stains and mould: alpha fades in with the noise (no hard edges), colours drift from a dark
    wet brown to a muted green. Needs the translucent render type."""
    field = value_noise(23)
    flat = sorted(v for row in field for v in row)
    thr = flat[int(len(flat) * (1 - COVERAGE[level] * 1.35))]
    wet, mould, moss = rgb("#2a2014"), rgb("#4a5530"), rgb("#5e6b38")
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    p = img.load()
    for y in range(16):
        for x in range(16):
            v = field[y][x]
            if v < thr:
                continue
            k = min(1.0, (v - thr) / 0.35)                      # 0 at the stain's edge, 1 in its core
            c = mix(wet, mould if (x * 3 + y * 5) % 7 else moss, k * 0.8)
            a = int(70 + 170 * k ** 1.1)                        # soft falloff
            p[x, y] = (c[0], c[1], c[2], a)
    return img


def inflate(e, d=0.02):
    e = json.loads(json.dumps(e))
    e["from"] = [round(v - d, 4) for v in e["from"]]
    e["to"] = [round(v + d, 4) for v in e["to"]]
    return e


def main():
    for material in PALETTES:
        for level in (1, 2, 3):
            img = decal_wood_soft(level) if material == "wood" else decal(material, level)
            img.save(os.path.join(TEX, f"cond_{material}_{level}.png"))
    for look, material in LOOK_MATERIAL.items():
        path = os.path.join(LOOKS, f"{look}.json")
        desc = json.load(open(path, encoding="utf-8"))
        overlays = []
        for i, lp in enumerate(desc["lid_parts"]):
            if lp["model"].endswith("ladder") or "ladder" in json.dumps(lp):
                pass
            part_file = os.path.join(A.ROOT, "models", "block", lp["model"].split("block/")[1] + ".json")
            part = json.load(open(part_file, encoding="utf-8"))
            if any(e["name"].startswith("ladder") for e in part["elements"]):
                continue                                      # never on the ladder
            levels = {}
            for level in (1, 2, 3):
                els = []
                for e in part["elements"]:
                    o = inflate(e)
                    for f in o["faces"].values():
                        f["texture"] = "#cond"
                    els.append(o)
                m = {"credit": "Manhole Travel - generated by art/make_conditions.py", "ambientocclusion": False,
                     "render_type": "minecraft:translucent" if material == "wood" else "minecraft:cutout",
                     "textures": {"cond": f"manholes:block/cond_{material}_{level}",
                                  "particle": f"manholes:block/cond_{material}_{level}"},
                     "elements": els}
                name = f"{look}_lid_{i}_cond_{level}"
                with open(os.path.join(MOD, name + ".json"), "w", encoding="utf-8") as fh:
                    json.dump(m, fh, indent=2)
                levels[str(level)] = f"manholes:block/parts/{name}"
            overlays.append({"part": i, "levels": levels})
        desc["condition_overlays"] = overlays
        if look in BASE_OVERLAY:
            base_ov = {}
            for state in ("closed", "open"):
                base = json.load(open(os.path.join(MOD, f"{look}_base_{state}.json"), encoding="utf-8"))
                frame = [e for e in base["elements"] if e["name"].startswith(("frame", "rim"))]
                levels = {}
                for level in (1, 2, 3):
                    els = []
                    for e in frame:
                        o = inflate(e)
                        for f in o["faces"].values():
                            f["texture"] = "#cond"
                        els.append(o)
                    m = {"credit": "Manhole Travel - generated by art/make_conditions.py", "ambientocclusion": False,
                         "render_type": "minecraft:translucent" if material == "wood" else "minecraft:cutout",
                         "textures": {"cond": f"manholes:block/cond_{material}_{level}",
                                      "particle": f"manholes:block/cond_{material}_{level}"},
                         "elements": els}
                    name = f"{look}_base_{state}_cond_{level}"
                    with open(os.path.join(MOD, name + ".json"), "w", encoding="utf-8") as fh:
                        json.dump(m, fh, indent=2)
                    levels[str(level)] = f"manholes:block/parts/{name}"
                base_ov[state] = levels
            desc["base_condition_overlays"] = base_ov
        with open(path, "w", encoding="utf-8") as fh:
            json.dump(desc, fh, indent=2)
        print(look, "->", len(overlays), "overlaid parts")


if __name__ == "__main__":
    main()
