# Writes src/test_datapack/data/manholes_test/structure/manhole_sample.nbt:
# a 3x4x3 box: stone-brick pad with a closed manhole in the middle whose block entity sets name "Sample Sewer".
# Minimal NBT writer, no dependencies.
import gzip
import os
import struct

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "test_datapack", "data", "manholes_test", "structure", "manhole_sample.nbt")

TAG_END, TAG_INT, TAG_STRING, TAG_LIST, TAG_COMPOUND = 0, 3, 8, 9, 10


class Int(int):
    pass


def _name(s):
    b = s.encode("utf-8")
    return struct.pack(">H", len(b)) + b


def _payload(v):
    if isinstance(v, Int):
        return struct.pack(">i", v)
    if isinstance(v, str):
        return _name(v)
    if isinstance(v, dict):
        out = b""
        for k, x in v.items():
            out += bytes([_type(x)]) + _name(k) + _payload(x)
        return out + bytes([TAG_END])
    if isinstance(v, list):
        t = _type(v[0]) if v else TAG_END
        return bytes([t]) + struct.pack(">i", len(v)) + b"".join(_payload(x) for x in v)
    raise TypeError(type(v))


def _type(v):
    if isinstance(v, Int):
        return TAG_INT
    if isinstance(v, str):
        return TAG_STRING
    if isinstance(v, dict):
        return TAG_COMPOUND
    if isinstance(v, list):
        return TAG_LIST
    raise TypeError(type(v))


def main():
    palette = [
        {"Name": "minecraft:stone_bricks"},
        {"Name": "manholes:manhole", "Properties": {"facing": "north", "open": "false"}},
    ]
    blocks = []
    for x in range(3):
        for z in range(3):
            blocks.append({"pos": [Int(x), Int(0), Int(z)], "state": Int(0)})
            if (x, z) == (1, 1):
                blocks.append({"pos": [Int(1), Int(1), Int(1)], "state": Int(1),
                               "nbt": {"id": "manholes:manhole", "name": "Sample Sewer"}})
    root = {
        "DataVersion": Int(3955),  # 1.21.1
        # 3 free layers above the pad (no air entries: existing blocks stay)
        "size": [Int(3), Int(4), Int(3)],
        "palette": palette,
        "blocks": blocks,
        "entities": [],
    }
    data = bytes([TAG_COMPOUND]) + _name("") + _payload(root)
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with gzip.open(OUT, "wb") as f:
        f.write(data)
    print("wrote", OUT)


if __name__ == "__main__":
    main()
