"""Per-type 16x16 map icons (hand-placed pixels), item style: dark outline, light top-left, shade bottom-right.

Writes assets/manholes/textures/gui/map_icon_<look>.png for city, grate, hatch, cave, home_manhole.
Run from the project root:  python art/make_map_icons.py   (existing files are kept unless --force)
"""
import os
import sys

from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "manholes",
                   "textures", "gui")

PAL = {
    ".": (0, 0, 0, 0),
    "o": (20, 20, 22, 255),       # outline
    "s": (0, 0, 0, 90),           # drop shadow
    # iron / stone greys
    "W": (196, 196, 204, 255), "L": (150, 150, 158, 255), "M": (104, 104, 112, 255),
    "D": (66, 66, 72, 255), "K": (36, 36, 40, 255), "B": (8, 8, 10, 255),
    # curb (light andesite)
    "c": (176, 178, 172, 255), "C": (138, 140, 134, 255),
    # rust
    "r": (150, 82, 40, 255),
    # wood
    "w": (170, 122, 72, 255), "n": (128, 88, 50, 255), "d": (86, 58, 32, 255),
    # moss
    "g": (86, 122, 54, 255), "G": (60, 90, 38, 255),
    # home paint
    "h": (96, 132, 86, 255), "H": (62, 90, 56, 255), "y": (226, 176, 40, 255),
}

ICONS = {
    # round cast-iron cover on a light kerb, two pry slots, centre boss
    "city": [
        "..oooooooooooo..",
        ".occccccccccccCo",
        ".ocCooooooooCcCo",
        ".ocoLLWWWWLLoCCo",
        ".ocoLMMMMMMLoCCo",
        ".ooLMDMDMDMMLoCo",
        ".oLBMMDLLDMMBLoo",
        ".oLMDMLWWLMDMLoo",
        ".oLMMDLLLLDMMLoo",
        ".oLBMMDMMDMMBLos",
        ".ooLMDMDMDMMLoCo",
        ".ocoLMMMMMMLoCCo",
        ".ocoLLMMMMLLoCCo",
        ".ocCooooooooCCCo",
        ".oCCCCCCCCCCCCCo",
        "..oooooooooooos.",
    ],
    # square iron drain grate: frame + dark slots between bars
    "grate": [
        "oooooooooooooooo",
        "oWLLLLLLLLLLLLMo",
        "oLMMMMMMMMMMMMDo",
        "oLMBLBLBLBLBLMDo",
        "oLMBLBLBLBLBLMDo",
        "oLMMMMMMMMMMMMDo",
        "oLMBLBLBLBLBLMDo",
        "oLMBLBLBrBLBLMDo",
        "oLMBLBLBLBLBLMDo",
        "oLMMMMMMMMMMMMDo",
        "oLMBLBLBLBLBLMDo",
        "oLMBLBLBLBLBLMDo",
        "oLMMMMMMMMMMMMDo",
        "oMDDDDDDDDDDDDDo",
        "oooooooooooooooo",
        ".sssssssssssssss",
    ],
    # wooden trapdoor: planks, two iron straps from the left hinges, ring handle
    "hatch": [
        "oooooooooooooooo",
        "odddddddddddddno",
        "odwwwnwwwwnwwwdo",
        "odLLLLLLLLwwwwdo",
        "odwwwnwwwwnwwwdo",
        "odnnnnnnnnnnnndo",
        "odwwwnwwwwnwKKdo",
        "odwwwnwwwwnKwKdo",
        "odwwwnwwwwnwKKdo",
        "odnnnnnnnnnnnndo",
        "odwwwnwwwwnwwwdo",
        "odLLLLLLLLwwwwdo",
        "odwwwnwwwwnwwwdo",
        "odddddddddddddno",
        "oooooooooooooooo",
        ".sssssssssssssss",
    ],
    # ring of mossy rocks around a black pit
    "cave": [
        "....oooo.ooo....",
        "..ooLLMooLWMoo..",
        ".oLWMMDoMMMDgGo.",
        ".oLMDooBBBBoDGo.",
        "oLMDoBBBBBBBoDMo",
        "oWMoBBBBBBBBBoMo",
        "oLDoBBBBBBBBBoDo",
        "ogGoBBBBBBBBBoMo",
        "oGDoBBBBBBBBBoLo",
        "oMMoBBBBBBBBBoDo",
        "oLMDoBBBBBBBoDgo",
        ".oMDDooBBBooMGGo",
        ".oLMMDMooMDMMGo.",
        "..ooMMDoWLMDoos.",
        "....oooo.oooos..",
        "......ssss......",
    ],
    # home: green-painted round cover with a yellow painted cross, on dark iron
    "home_manhole": [
        "....oooooooo....",
        "..ooHHHHHHHHoo..",
        ".oHHhhhhhhhhHHo.",
        ".oHhhhhhhhhhhHo.",
        "oHhhhhhyyhhhhhHo",
        "oHhhhhhyyhhhhhHo",
        "oHhhhhhyyhhhhhHo",
        "oHhyyyyyyyyyyhHo",
        "oHhyyyyyyyyyyhHo",
        "oHhhhhhyyhhhhhHo",
        "oHhhhhhyyhhhhhHo",
        ".oHhhhhyyhhhhHos",
        ".oHHhhhhhhhhHHos",
        "..ooHHHHHHHHoos.",
        "....oooooooos...",
        "......ssss......",
    ],
}


def city_rows():
    """Computed so the circle is clean: light kerb square, dark round iron cover with a lit rim,
    diamond grid, two black pry slots and a centre boss."""
    rows = []
    for y in range(16):
        row = ""
        for x in range(16):
            edge = x in (0, 15) or y in (0, 15)
            dx, dy = x - 7.5, y - 7.5
            r = (dx * dx + dy * dy) ** 0.5
            if edge:
                ch = "o"
            elif r > 6.2:
                ch = "c" if (x + y) < 16 else "C"          # kerb, lit top-left
            elif r > 5.4:
                ch = "o"                                    # cut edge of the cover
            elif r > 4.5:
                ch = "W" if (dx + dy) < -1 else ("L" if (dx + dy) < 2 else "D")   # rim
            elif r < 1.3:
                ch = "W"                                    # boss
            elif abs(dy) < 1 and 3.0 < abs(dx) < 4.5:
                ch = "B"                                    # pry slots
            else:
                ch = "D" if (x + y) % 3 == 0 or (x - y) % 3 == 0 else "M"   # grid
            row += ch
        rows.append(row)
    return rows


def main():
    ICONS["city"] = city_rows()
    force = "--force" in sys.argv
    os.makedirs(OUT, exist_ok=True)
    for look, rows in ICONS.items():
        assert len(rows) == 16 and all(len(r) == 16 for r in rows), look
        path = os.path.join(OUT, f"map_icon_{look}.png")
        if os.path.exists(path) and not force:
            print("kept", os.path.basename(path))
            continue
        img = Image.new("RGBA", (16, 16))
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                img.putpixel((x, y), PAL[ch])
        img.save(path)
        print("wrote", os.path.basename(path))


if __name__ == "__main__":
    main()
