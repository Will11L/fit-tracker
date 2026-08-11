"""4-panel diff between two images: orig | fig | abs-diff | threshold mask.

Usage:
    python diff.py <orig.png> <fig.png> <output.png> [thresh] [W H]

thresh = pixel diff threshold above which a pixel is marked (default 10).
W H    = optional resize both images to this size before diffing.

Prints diff % above threshold for quick read-out.
"""
import sys
from PIL import Image, ImageChops


def main():
    orig_p = sys.argv[1]
    fig_p = sys.argv[2]
    out_p = sys.argv[3]
    thresh = int(sys.argv[4]) if len(sys.argv) > 4 else 10
    target_size = None
    if len(sys.argv) > 6:
        target_size = (int(sys.argv[5]), int(sys.argv[6]))

    orig = Image.open(orig_p).convert("RGB")
    fig = Image.open(fig_p).convert("RGB")

    if target_size:
        orig = orig.resize(target_size, Image.LANCZOS)
        fig = fig.resize(target_size, Image.LANCZOS)
    elif orig.size != fig.size:
        fig = fig.resize(orig.size, Image.LANCZOS)

    diff = ImageChops.difference(orig, fig)
    mask = diff.point(lambda v: 255 if v > thresh else 0).convert("L")
    mask_rgb = Image.merge("RGB", (mask, mask, mask))

    pw, ph = orig.size
    panel = Image.new("RGB", (pw * 4, ph), (0, 0, 0))
    panel.paste(orig, (0, 0))
    panel.paste(fig, (pw, 0))
    panel.paste(diff, (pw * 2, 0))
    panel.paste(mask_rgb, (pw * 3, 0))
    panel.save(out_p, "PNG", optimize=True)

    total = pw * ph
    above = sum(1 for p in mask.getdata() if p > 0)
    pct = 100 * above / total
    print(f"OK: {out_p} ({pw * 4}x{ph}) — diff {above}/{total} ({pct:.2f}%) above thresh={thresh}")


if __name__ == "__main__":
    main()
