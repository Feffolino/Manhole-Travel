"""Looks for the animated manhole renderer (and the static full models derived from them).

For every look it writes:
  models/block/parts/<look>_base_closed.json, <look>_base_open.json, <look>_lid_<i>.json  (split, for the BER)
  assets/manholes/looks/<look>.json                                                    (animation descriptor)
  full static models: block/<look>_full(_open).json for the manholes, block/variant_<look>(_open).json otherwise

open spec (model px, north-facing frame): "rotate" {axis, angle, origin} first, then "translate" [x,y,z].
Lid elements are authored in the CLOSED position. Every lid opens AWAY from the ladder: hatch ladder on the east wall (hinge west), city on the north wall.

Run from the project root:  python art/make_looks.py   (after make_assets.py; replaces make_variants' main)
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
import make_assets as A  # noqa: E402
import make_variants as V  # noqa: E402

box, pit, model, tex = V.box, V.pit, V.model, V.tex
V.TEX.update({
    "cover": "minecraft:block/polished_deepslate",    # dark cast-iron disc of the city cover
    "seat": "minecraft:block/black_concrete",
    "slot": "minecraft:block/black_concrete",
    "lip": "minecraft:block/stone_bricks",
    "bricks": "minecraft:block/stone_bricks",
    "trapdoor_iron": "minecraft:block/iron_trapdoor",
    "plate": "minecraft:block/cut_copper",
    "curb": "minecraft:block/polished_andesite",      # light kerb frame: reads apart from the dark cover
})


# ---------------------------------------------------------------- stepped discs / rings (city cover)
def disc_rects(radius, cx=8.0, cz=8.0):
    """Non-overlapping row bands of a stepped disc; a 1 px cell belongs to it when its centre is inside."""
    rects = []
    r = int(radius) + 1
    for z in range(int(cz) - r, int(cz) + r):
        dz = z + 0.5 - cz
        a = 0
        while (a + 0.5) ** 2 + dz * dz <= radius ** 2:
            a += 1
        if a == 0:
            continue
        x0, x1 = int(cx) - a, int(cx) + a
        if rects and rects[-1][0] == x0 and rects[-1][2] == x1 and rects[-1][3] == z:
            rects[-1] = (x0, rects[-1][1], x1, z + 1)
        else:
            rects.append((x0, z, x1, z + 1))
    return rects


def ring_rects(r_out, r_in, cx=8.0, cz=8.0):
    """Cells inside r_out but outside r_in, as row segments (a stepped annulus), non-overlapping."""
    out = []
    r = int(r_out) + 1
    for z in range(int(cz) - r, int(cz) + r):
        dz = z + 0.5 - cz
        run = None
        for x in range(int(cx) - r, int(cx) + r + 1):
            d2 = (x + 0.5 - cx) ** 2 + dz * dz
            inside = r_in ** 2 < d2 <= r_out ** 2
            if inside and run is None:
                run = x
            if not inside and run is not None:
                out.append((run, z, x, z + 1))
                run = None
    return out


def city_frame():
    # flush kerb (below the cover's 0.5 px underside) so the sliding cover never cuts through it
    return (V.split_x("frame_n", -4, 20, 0, 0.45, -4, -1, "#curb")
            + V.split_x("frame_s", -4, 20, 0, 0.45, 17, 20, "#curb")
            + V.split_z("frame_w", -4, -1, 0, 0.4, -1, 17, "#curb")
            + V.split_z("frame_e", 17, 20, 0, 0.4, -1, 17, "#curb"))


def city_cover(y=0.5):
    """Round cover (radius 9, same cut as the custom manhole): dark cast-iron disc, raised iron rim ring,
    raised centre boss, two black pry slots. Each layer sits ON the one below (no coplanar faces)."""
    top = y + 0.9
    els = [box(f"disc_{i}", (x0, y, z0), (x1, top, z1), "#iron", top="#cover")
           for i, (x0, z0, x1, z1) in enumerate(disc_rects(9.0))]
    els += [box(f"rim_{i}", (x0, top, z0), (x1, top + 0.35, z1), "#iron")
            for i, (x0, z0, x1, z1) in enumerate(ring_rects(9.0, 7.6))]
    els += [box(f"boss_{i}", (x0, top, z0), (x1, top + 0.3, z1), "#iron")
            for i, (x0, z0, x1, z1) in enumerate(disc_rects(2.6))]
    for n, x0 in (("slot_w", 2.5), ("slot_e", 12.5)):
        els.append(box(n, (x0, top, 7.5), (x0 + 1, top + 0.03, 8.5), "#slot", faces=("up",)))
    return els


def shaft_lips(var):
    return [
        box("lip_n", (-1, 0.04, -1), (17, 0.35, 0), var, faces=("up", "south")),
        box("lip_s", (-1, 0.04, 16), (17, 0.35, 17), var, faces=("up", "north")),
        box("lip_w", (-1, 0.05, 0), (0, 0.33, 16), var, faces=("up", "east")),
        box("lip_e", (16, 0.05, 0), (17, 0.33, 16), var, faces=("up", "west")),
    ]


def ladder_solid(wall, var, top=2.8):
    """Solid-pixel ladder (real cubes, no cutout texture) fixed to the inner face of one shaft wall:
    two 1x1 rails and 1 px rungs every 2 px, poking a little out of the shaft. Kept 0.1 px off the wall
    and the lip planes so nothing is coplanar."""
    els = []
    y0 = -1.0   # below ground level is hidden by the block under the cover anyway
    if wall == "north":            # inner wall face at z = -1
        z0, z1 = -0.9, 0.1
        for n, x in (("rail_l", 4.2), ("rail_r", 10.8)):
            els.append(box(f"ladder_{n}", (x, y0, z0), (x + 1, top, z1), var))
        for i, y in enumerate((0.45, 1.75)):
            els.append(box(f"ladder_rung_{i}", (5.2, y, z0 + 0.15), (10.8, y + 0.5, z1 - 0.15), var))
    elif wall == "east":           # inner wall face at x = 17
        x0, x1 = 15.9, 16.9
        for n, z in (("rail_l", 4.0), ("rail_r", 11.0)):
            els.append(box(f"ladder_{n}", (x0, y0, z), (x1, top, z + 1), var))
        for i, y in enumerate((0.45, 1.75)):
            els.append(box(f"ladder_rung_{i}", (x0 + 0.15, y, 5.0), (x1 - 0.15, y + 0.5, 11.0), var))
    elif wall == "west":           # inner wall face at x = -1
        x0, x1 = -0.9, 0.1
        for n, z in (("rail_l", 4.0), ("rail_r", 11.0)):
            els.append(box(f"ladder_{n}", (x0, y0, z), (x1, top, z + 1), var))
        for i, y in enumerate((0.45, 1.75)):
            els.append(box(f"ladder_rung_{i}", (x0 + 0.15, y, 5.0), (x1 - 0.15, y + 0.5, 11.0), var))
    elif wall == "south":          # inner wall face at z = 17
        z0, z1 = 15.9, 16.9
        for n, x in (("rail_l", 4.0), ("rail_r", 11.0)):
            els.append(box(f"ladder_{n}", (x, y0, z0), (x + 1, top, z1), var))
        for i, y in enumerate((0.45, 1.75)):
            els.append(box(f"ladder_rung_{i}", (5.0, y, z0 + 0.15), (11.0, y + 0.5, z1 - 0.15), var))
    else:
        raise ValueError(wall)
    return els


LADDER_DROP = 3.2   # closed: the ladder waits this far down the shaft (hidden by the ground block)


def ladder_part(wall, var, top=2.8, drop=LADDER_DROP):
    """The ladder as an animated lid part: authored lowered into the shaft, it slides UP into place as the
    cover opens (and back down when it closes). A bigger drop makes it surface later in the animation."""
    els = []
    for e in ladder_solid(wall, var, top):
        e["from"][1] = round(e["from"][1] - drop, 4)
        e["to"][1] = round(e["to"][1] - drop, 4)
        els.append(e)
    return ("ladder", els, {"translate": [0, drop, 0]})


# ---------------------------------------------------------------- looks
def hinge(axis, pivot, knuckles, brackets, bracket_out, bracket_floor, span, var, knuckle=0.35):
    """Real hinge hardware on the pivot line, so the lid visibly turns on something.
    axis 'z': hinge line along z through (pivot[0], pivot[1]); axis 'x': along x through (pivot[2], pivot[1]).
      knuckles: [(a0, a1)] segments along the axis -> LID parts (0.8 px barrels centred on the pivot: they turn
                in place, so they never sweep into anything)
      brackets: [(a0, a1)] segments between the knuckles -> STATIC, from the pin out to `bracket_out` (a cross
                coordinate on the side the lid never swings through) and down to `bracket_floor` (frame top)
      span:     (a0, a1) of the static pin through all of them
    Returns (lid_elements, static_elements)."""
    k, pin = knuckle, 0.15   # knuckle half-size: its corners must clear the frame top while it turns
    lid, static = [], []
    if axis == "z":
        px, py = pivot[0], pivot[1]
        for i, (a0, a1) in enumerate(knuckles):
            lid.append(box(f"knuckle_{i}", (px - k, py - k, a0), (px + k, py + k, a1), var))
        # brackets start at the pin centre (half the pin sits in them) and go outward, away from the lid
        lo, hi = (bracket_out, px) if bracket_out < px else (px, bracket_out)
        for i, (a0, a1) in enumerate(brackets):
            static.append(box(f"bracket_{i}", (lo, bracket_floor, a0), (hi, py + pin + 0.05, a1), var))
        static.append(box("hinge_pin", (px - pin, py - pin, span[0]), (px + pin, py + pin, span[1]), var))
    else:
        pz, py = pivot[2], pivot[1]
        for i, (a0, a1) in enumerate(knuckles):
            lid.append(box(f"knuckle_{i}", (a0, py - k, pz - k), (a1, py + k, pz + k), var))
        lo, hi = (bracket_out, pz) if bracket_out < pz else (pz, bracket_out)
        for i, (a0, a1) in enumerate(brackets):
            static.append(box(f"bracket_{i}", (a0, bracket_floor, lo), (a1, py + pin + 0.05, hi), var))
        static.append(box("hinge_pin", (span[0], py - pin, pz - pin), (span[1], py + pin, pz + pin), var))
    return lid, static


def turn180(look):
    """Rotate a whole look 180 deg about the vertical axis through the block centre (8, *, 8): elements,
    element rotations, and the open specs (rotate origin/angle, translate)."""
    def el(e):
        e = json.loads(json.dumps(e))
        (x0, y0, z0), (x1, y1, z1) = e["from"], e["to"]
        e["from"], e["to"] = [16 - x1, y0, 16 - z1], [16 - x0, y1, 16 - z0]
        swap = {"north": "south", "south": "north", "east": "west", "west": "east"}
        e["faces"] = {swap.get(k, k): v for k, v in e["faces"].items()}
        if "rotation" in e:
            r = e["rotation"]
            o = r["origin"]
            r["origin"] = [16 - o[0], o[1], 16 - o[2]]
            if r["axis"] in ("x", "z"):
                r["angle"] = -r["angle"]
        return e

    def spec(sp):
        sp = json.loads(json.dumps(sp))
        if "rotate" in sp:
            r = sp["rotate"]
            o = r["origin"]
            r["origin"] = [16 - o[0], o[1], 16 - o[2]]
            if r["axis"] in ("x", "z"):
                r["angle"] = -r["angle"]
        if "translate" in sp:
            d = sp["translate"]
            sp["translate"] = [-d[0], d[1], -d[2]]
        return sp

    look["base_closed"] = [el(e) for e in look["base_closed"]]
    look["base_open"] = [el(e) for e in look["base_open"]]
    look["lids"] = [(n, [el(e) for e in els], spec(sp)) for n, els, sp in look["lids"]]
    return look


def look_hatch():
    return turn180(_look_hatch())   # user: the whole wooden hatch faces the other way


def _look_hatch():
    t = tex("log", "planks", "trapdoor", "iron", "pit", "pit_rim", particle="planks")
    k_lid, k_static = hinge("z", (-1, 2.25, 8), knuckles=[(0.8, 3.2), (6.8, 9.2), (12.8, 15.2)],
                            brackets=[(4.0, 6.0), (10.0, 12.0)], bracket_out=-2.4, bracket_floor=1.9,
                            span=(0.6, 15.4), var="#iron", knuckle=0.24)   # corners clear the 1.9 px beam while turning
    return dict(textures=t, cutout=True,
                base_closed=V.log_frame() + pit(rim=False) + k_static,
                base_open=V.log_frame() + pit() + k_static,
                # hinge on the lid's TOP west edge (above the straps): every lid point is below the pivot, so it swings
                # up and east over the pit and never sweeps into the west beam; ~100 deg = propped fully open
                lids=[("lid", V.hatch_lid() + k_lid, {"rotate": {"axis": "z", "angle": 100, "origin": [-1, 2.25, 8]}}),
                      ladder_part("south", "#planks")])


def look_grate():
    t = tex("tiles", "iron", "pit", particle="tiles")
    k_lid, k_static = hinge("x", (8, 1.6, 17), knuckles=[(1.0, 3.0), (7.0, 9.0), (13.0, 15.0)],
                            brackets=[(4.0, 6.0), (10.0, 12.0)], bracket_out=18.4, bracket_floor=1.0,
                            span=(0.6, 15.4), var="#iron")
    base = V.tile_frame() + pit(rim=False) + k_static
    return dict(textures=t, cutout=False, base_closed=base, base_open=base,
                # hinge on the grate's TOP south edge (top of the cross bars): nothing sweeps into the south frame
                lids=[("grate", V.grate_bars() + k_lid, {"rotate": {"axis": "x", "angle": 100, "origin": [8, 1.6, 17]}}),
                      ladder_part("north", "#iron")])


def look_cave():
    """Rubble-choked pit: a FIXED ring of rocks and three big irregular boulders (each several cubes) that plug
    the opening. Opening: the boulders give way and sink straight down into the shaft (below the pit floor,
    hidden by it), so nothing moves sideways and nothing ever crosses the ring rocks. A 1 px strip is left
    along the north wall (the placer's side) for the wooden ladder, which rises there as the boulders sink."""
    t = tex("cobble", "mossy", "tuff", "andesite", "stone", "pit", "planks", particle="cobble")
    ring = [box(f"rock_{i}", (x0, 0, z0), (x1, h, z1), tx) for i, (x0, z0, x1, z1, h, tx) in enumerate(V.ROCKS)]
    base = ring + pit(rim=False)
    boulders = {
        "a": [("a_main", (-1, 0.05, 2), (7, 4.0, 14), "#stone"), ("a_lobe_n", (3, 0.1, 0.1), (7, 3.2, 2), "#mossy"),
              ("a_lobe_s", (6.01, 0.15, 14), (7, 3.0, 17), "#stone"), ("a_top", (0, 4.0, 4), (5, 5.0, 11), "#mossy")],
        "b": [("b_main", (7, 0.1, 1), (16, 3.6, 9), "#andesite"), ("b_lobe", (7.01, 0.2, 0.1), (11, 3.0, 1), "#andesite"),
              ("b_top", (9, 3.6, 3), (14, 4.6, 7), "#stone")],
        "c": [("c_main", (7, 0.2, 9), (15, 3.3, 17), "#tuff"), ("c_top", (8, 3.3, 11), (13, 4.3, 15), "#mossy")],
    }
    lids = []
    for n, parts in boulders.items():
        top = max(to[1] for _, _, to, _ in parts)
        lids.append((f"boulder_{n}", [box(pn, f, to, tx) for pn, f, to, tx in parts],
                     {"translate": [0, -round(top + 0.3, 2), 0]}))      # sink fully below the pit floor
    # wooden ladder on the NORTH wall (the placer's side), kept clear of the north-east rock (x < 11)
    drop = LADDER_DROP
    lad = [box("ladder_rail_l", (4.2, -1 - drop, -0.9), (5.2, 2.5 - drop, 0.1), "#planks"),
           box("ladder_rail_r", (9.8, -1 - drop, -0.9), (10.8, 2.5 - drop, 0.1), "#planks"),
           box("ladder_rung_0", (5.2, 0.45 - drop, -0.75), (9.8, 0.95 - drop, -0.05), "#planks"),
           box("ladder_rung_1", (5.2, 1.75 - drop, -0.75), (9.8, 2.25 - drop, -0.05), "#planks")]
    lids.append(("ladder", lad, {"translate": [0, drop, 0]}))
    return dict(textures=t, cutout=False, base_closed=base, base_open=base, lids=lids)


def look_city():
    t = tex("iron", "curb", "cover", "seat", "slot", "pit", "lip", particle="iron")
    return dict(textures=t, cutout=True,
                base_closed=city_frame() + [box("seat", (-1, 0, -1), (17, 0.5, 17), "#seat")],
                base_open=city_frame() + [pit(rim=False)[0]] + shaft_lips("#lip"),
                lids=[("cover", city_cover(), {"translate": [0, 0.1, 10]}), ladder_part("north", "#iron")])


def _retex(els, var):
    out = []
    for e in els:
        e = json.loads(json.dumps(e))
        for f in e["faces"].values():
            f["texture"] = var
        out.append(e)
    return out


def look_home():
    """Personal home manhole, vanilla textures to match the other covers and its recipe (iron ingots, stone
    bricks, iron trapdoor): stone-brick kerb, square iron trapdoor lid with a grip on the player's side and three
    iron hinges (plate + knuckle on the lid, bracket + pin on the kerb) on the far (south) edge; it props up
    ~100 deg away from the player. The iron ladder rises on the north wall, toward the player."""
    t = tex("bricks", "trapdoor_iron", "iron", "seat", "pit", "lip", particle="bricks")
    frame = (V.split_x("frame_n", -4, 20, 0, 1.2, -4, -1, "#bricks")
             + V.split_x("frame_s", -4, 20, 0, 1.2, 17, 20, "#bricks")
             + V.split_z("frame_w", -4, -1, 0, 1.1, -1, 17, "#bricks")
             + V.split_z("frame_e", 17, 20, 0, 1.1, -1, 17, "#bricks"))
    lid = [
        # one piece so the iron-trapdoor pattern shows whole (uv 0..16 over the 18 px lid)
        box("lid", (-1, 0.5, -0.8), (17, 1.5, 17), "#iron", top="#trapdoor_iron"),   # 0.2 px short: clears the north kerb
        box("handle_bar", (6.5, 1.5, 1.5), (9.5, 2.1, 2.5), "#iron"),   # grip on the player's side
    ]
    k_lid, k_static = hinge("x", (8, 2.2, 17), knuckles=[(1.0, 3.0), (7.0, 9.0), (13.0, 15.0)],
                            brackets=[(4.0, 6.0), (10.0, 12.0)], bracket_out=18.4, bracket_floor=1.2,
                            span=(0.6, 15.4), var="#iron")
    for i, (a0, a1) in enumerate([(1.0, 3.0), (7.0, 9.0), (13.0, 15.0)]):
        lid.append(box(f"hinge_plate_{i}", (a0 + 0.1, 1.5, 14.6), (a1 - 0.1, 1.8, 16.6), "#iron"))
    lid += k_lid
    return dict(textures=t, cutout=True,
                base_closed=frame + [box("seat", (-1, 0, -1), (17, 0.5, 17), "#seat")] + k_static,
                base_open=frame + [pit(rim=False)[0]] + shaft_lips("#lip") + k_static,
                # model north = the placer's side: ladder there, hinge on the far (south) edge, opens away
                lids=[("lid", lid, {"rotate": {"axis": "x", "angle": 100, "origin": [8, 2.2, 17]}}),
                      ladder_part("north", "#iron")])


LOOKS = {
    "home_manhole": look_home,
    "hatch": look_hatch,
    "grate": look_grate,
    "cave": look_cave,
    "city": look_city,
}


def moved(el, spec):
    """Static copy of a lid element in its OPEN pose (item models / no-animation fallback)."""
    e = json.loads(json.dumps(el))
    if "rotate" in spec:
        if "rotation" in e:
            raise ValueError(f"{e['name']}: already rotated, can't add the open rotation")
        r = spec["rotate"]
        # static fallback: vanilla block models only allow -45..45 in 22.5 steps; the animated renderer uses
        # the full angle from the look file
        ang = max(-45.0, min(45.0, r["angle"]))
        e["rotation"] = {"angle": ang, "axis": r["axis"], "origin": list(r["origin"])}
    if "translate" in spec:
        d = spec["translate"]
        e["from"] = [round(e["from"][i] + d[i], 4) for i in range(3)]
        e["to"] = [round(e["to"][i] + d[i], 4) for i in range(3)]
        if "rotation" in e and "rotate" not in spec:
            e["rotation"]["origin"] = [round(e["rotation"]["origin"][i] + d[i], 4) for i in range(3)]
    return e


def write_look(look):
    L = LOOKS[look]()
    t, cut = L["textures"], L["cutout"]
    A.save_json(model(t, L["base_closed"], cut), f"block/parts/{look}_base_closed.json")
    A.save_json(model(t, L["base_open"], cut), f"block/parts/{look}_base_open.json")
    lid_parts = []
    for i, (_, els, spec) in enumerate(L["lids"]):
        A.save_json(model(t, els, cut), f"block/parts/{look}_lid_{i}.json")
        lid_parts.append({"model": f"manholes:block/parts/{look}_lid_{i}", "open": spec})
    closed = L["base_closed"] + [e for _, els, _ in L["lids"] for e in els]
    opened = L["base_open"] + [moved(e, spec) for _, els, spec in L["lids"] for e in els]
    stem = "block/home_manhole" if look == "home_manhole" else f"block/variant_{look}"
    A.save_json(model(t, closed, cut), f"{stem}.json")
    A.save_json(model(t, opened, cut), f"{stem}_open.json")
    desc = {
        "base_closed": f"manholes:block/parts/{look}_base_closed",
        "base_open": f"manholes:block/parts/{look}_base_open",
        "lid_parts": lid_parts,
        "duration_ticks": 12,
        "sound_open": "manholes:manhole.slide",
        "sound_close": "manholes:manhole.slide",
    }
    path = os.path.join(A.ROOT, "looks", f"{look}.json")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(desc, f, indent=2)


def main():
    for look in LOOKS:
        write_look(look)
    print("looks:", ", ".join(LOOKS))
    import make_conditions   # re-adds the condition overlays (visible rust) to the freshly written looks
    make_conditions.main()


if __name__ == "__main__":
    main()
