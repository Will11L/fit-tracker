"""Side-by-side comparison of two images (fig | orig).

Usage:
    python compare.py <fig.png> <orig.png> <output.png>

If sizes differ, fig is resized to orig.
"""
import sys
from PIL import Image


def main():
    fig_p = sys.argv[1]
    orig_p = sys.argv[2]
    out_p = sys.argv[3]

    fig = Image.open(fig_p).convert("RGB")
    orig = Image.open(orig_p).convert("RGB")
    if fig.size != orig.size:
        fig = fig.resize(orig.size, Image.LANCZOS)

    W, H = orig.size
    panel = Image.new("RGB", (W * 2, H), (0, 0, 0))
    panel.paste(fig, (0, 0))
    panel.paste(orig, (W, 0))
    panel.save(out_p, "PNG", optimize=True)
    print(f"OK: {out_p} ({W * 2}x{H})")


if __name__ == "__main__":
    main()
