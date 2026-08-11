"""Overlay a coord grid on a screenshot for measuring component bounds.

Usage:
    python grid.py <input.jpg> <output.png> [step]

step = grid spacing in pixels (default 50). Labels every 2*step on top/left.

Tip: use on a device screenshot (1080xN) to read off (x, y, w, h) of a
component, then feed those to crop.py.
"""
import sys
from PIL import Image, ImageDraw, ImageFont


def main():
    src = sys.argv[1]
    out = sys.argv[2]
    step = int(sys.argv[3]) if len(sys.argv) > 3 else 50

    img = Image.open(src).convert("RGBA")
    overlay = Image.new("RGBA", img.size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(overlay)
    W, H = img.size

    for x in range(0, W, step):
        draw.line([(x, 0), (x, H)], fill=(0, 200, 255, 110), width=1)
    for y in range(0, H, step):
        draw.line([(0, y), (W, y)], fill=(0, 200, 255, 110), width=1)

    try:
        font = ImageFont.truetype("arial.ttf", 14)
    except Exception:
        font = ImageFont.load_default()

    label_every = step * 2
    for x in range(0, W, label_every):
        draw.text((x + 2, 2), str(x), fill=(255, 255, 0, 230), font=font)
    for y in range(0, H, label_every):
        draw.text((2, y + 2), str(y), fill=(255, 255, 0, 230), font=font)

    result = Image.alpha_composite(img, overlay).convert("RGB")
    result.save(out, "PNG", optimize=True)
    print(f"OK: {out} ({W}x{H}) — step={step}")


if __name__ == "__main__":
    main()
