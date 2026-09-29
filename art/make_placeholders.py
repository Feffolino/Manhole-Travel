# Placeholder textures for Manhole Travel. NEVER overwrites an existing file: textures are hand-drawn.
# Usage: python art/make_placeholders.py   (from the project root)
import math
import os
import random

from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "manholes", "textures", "block")


def cover(base, dark, light, open_=False, home=False):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    rnd = random.Random(7 if home else 3)
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if d > 7.9:
                c = dark  # corners: street around the cover
            elif d > 6.6:
                c = light  # rim
            elif open_:
                c = (12, 12, 14, 255) if d < 5.8 else (40, 38, 36, 255)  # the hole
            else:
                c = base
                if (x + y) % 4 == 0 or (x - y) % 4 == 0:
                    c = light  # grip pattern
                if home and 6 <= x <= 9 and 6 <= y <= 9:
                    c = (150, 110, 60, 255)
            n = rnd.randint(-8, 8)
            img.putpixel((x, y), (max(0, min(255, c[0] + n)), max(0, min(255, c[1] + n)), max(0, min(255, c[2] + n)), 255))
    return img


def side(base):
    img = Image.new("RGBA", (16, 16), base)
    for x in range(16):
        img.putpixel((x, 14), tuple(min(255, v + 25) for v in base[:3]) + (255,))
    return img


def main():
    os.makedirs(OUT, exist_ok=True)
    iron = (92, 92, 96, 255)
    rust = (110, 78, 58, 255)
    street = (58, 58, 60, 255)
    textures = {
        "manhole_top": cover(rust, street, (140, 100, 70, 255)),
        "manhole_open_top": cover(rust, street, (140, 100, 70, 255), open_=True),
        "manhole_side": side((96, 70, 52, 255)),
        "home_manhole_top": cover(iron, street, (130, 130, 136, 255), home=True),
        "home_manhole_open_top": cover(iron, street, (130, 130, 136, 255), open_=True, home=True),
        "home_manhole_side": side((80, 80, 84, 255)),
    }
    for name, img in textures.items():
        path = os.path.join(OUT, name + ".png")
        if os.path.exists(path):
            print("keep", name)
            continue
        img.save(path)
        print("made", name)


if __name__ == "__main__":
    main()
