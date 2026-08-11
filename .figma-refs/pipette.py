"""Sample exact RGB hex at given pixel coordinates of an image.

Usage:
    python pipette.py <input.jpg> x1 y1 [x2 y2 ...]

Prints one line per coord: (x, y) rgb(r,g,b) #rrggbb.

Tip: read coords off a grid.py overlay, sample at the center of a region
to avoid anti-aliased edge pixels.
"""
import sys
from PIL import Image


def main():
    src = sys.argv[1]
    coords = list(map(int, sys.argv[2:]))
    if not coords or len(coords) % 2 != 0:
        sys.exit("usage: pipette.py <img> x1 y1 [x2 y2 ...]")

    img = Image.open(src).convert("RGB")
    W, H = img.size
    for i in range(0, len(coords), 2):
        x, y = coords[i], coords[i + 1]
        if not (0 <= x < W and 0 <= y < H):
            print(f"({x:4},{y:4})  out of bounds (img is {W}x{H})")
            continue
        r, g, b = img.getpixel((x, y))
        print(f"({x:4},{y:4})  rgb({r:3},{g:3},{b:3})  #{r:02x}{g:02x}{b:02x}")


if __name__ == "__main__":
    main()
