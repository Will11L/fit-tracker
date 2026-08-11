"""Crop a region of a screenshot for isolating a component.

Usage:
    python crop.py <input.jpg> <output.png> <x> <y> <w> <h>

All coords in pixels of the source image (e.g., 1080×2400 for device screenshots).

Tip: use grid.py first on the screenshot to identify x/y/w/h of the component.
Then crop.py to extract just that region for diff testing.
"""
import sys
from PIL import Image


def main():
    src = sys.argv[1]
    out = sys.argv[2]
    x = int(sys.argv[3])
    y = int(sys.argv[4])
    w = int(sys.argv[5])
    h = int(sys.argv[6])

    img = Image.open(src).convert("RGB")
    W, H = img.size
    # Clamp to image bounds
    x = max(0, min(x, W - 1))
    y = max(0, min(y, H - 1))
    w = max(1, min(w, W - x))
    h = max(1, min(h, H - y))

    cropped = img.crop((x, y, x + w, y + h))
    cropped.save(out, "PNG", optimize=True)
    print(f"OK: {out} ({w}x{h}) — crop from {src} at ({x}, {y})")


if __name__ == "__main__":
    main()
