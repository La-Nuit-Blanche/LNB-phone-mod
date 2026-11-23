"""Temporary sprites for the smooth rendering spike (task 3). Not the final set (task 9 draws that).

Writes white-on-transparent masks into apps/demo/src/main/resources/assets/lnbphone_demo/textures/gui/spike/:
- the art direction's shape sprites (section 16.2) at 2 px per dp, to compare them with the masks the phone
  generates at the screen's own resolution;
- two line icons exported at 1, 1.5 and 2 px per dp, to compare one 2x sprite filtered down with one export per
  density drawn 1:1.

Run with: python -I apps/demo/scripts/gen_spike_sprites.py
"""

import math
import os

import numpy as np
from PIL import Image, ImageDraw

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "lnbphone_demo",
                   "textures", "gui", "spike")
SS = 16  # supersampling per axis


def save(alpha, name):
    a = np.clip(alpha * 255.0 + 0.5, 0, 255).astype(np.uint8)
    img = np.zeros((a.shape[0], a.shape[1], 4), np.uint8)
    img[..., 0:3] = 255
    img[..., 3] = a
    Image.fromarray(img, "RGBA").save(os.path.join(OUT, name + ".png"))


def rounded_rect(w, h, r, stroke=0):
    """Coverage of a w x h px rounded rect of radius r px (only an inner stroke if stroke > 0), by supersampling."""
    ys, xs = np.mgrid[0:h * SS, 0:w * SS]
    x = (xs + 0.5) / SS
    y = (ys + 0.5) / SS

    def inside(iw, ih, ir):
        dx = np.maximum(np.abs(x - w / 2) - (iw / 2 - ir), 0)
        dy = np.maximum(np.abs(y - h / 2) - (ih / 2 - ir), 0)
        return (dx * dx + dy * dy <= ir * ir) & (np.abs(x - w / 2) <= iw / 2) & (np.abs(y - h / 2) <= ih / 2)

    shape = inside(w, h, r)
    if stroke > 0:
        shape &= ~inside(w - 2 * stroke, h - 2 * stroke, max(r - stroke, 0))
    return shape.reshape(h, SS, w, SS).mean(axis=(1, 3))


def stroke_icon(size_dp, density, width_dp, paths):
    """Draws polylines (in dp, round caps and joins) at `density` px per dp, supersampled then box-filtered."""
    px = int(round(size_dp * density))
    scale = density * SS
    img = Image.new("L", (px * SS, px * SS), 0)
    d = ImageDraw.Draw(img)
    w = width_dp * scale
    for pts in paths:
        p = [(x * scale, y * scale) for x, y in pts]
        d.line(p, fill=255, width=int(round(w)), joint="curve")
        for x, y in p:
            d.ellipse([x - w / 2, y - w / 2, x + w / 2, y + w / 2], fill=255)
    a = np.asarray(img, np.float64) / 255.0
    return a.reshape(px, SS, px, SS).mean(axis=(1, 3))


def heart_path():
    # Classic parametric heart, fitted into the 16 dp grid's live area (2..14) minus half the 1.5 dp stroke.
    pts = []
    for i in range(0, 361, 3):
        t = math.radians(i)
        x = 16 * math.sin(t) ** 3
        y = 13 * math.cos(t) - 5 * math.cos(2 * t) - 2 * math.cos(3 * t) - math.cos(4 * t)
        pts.append((x, -y))
    xs = [p[0] for p in pts]
    ys = [p[1] for p in pts]
    span = 14 - 2 - 1.5
    s = span / max(max(xs) - min(xs), max(ys) - min(ys))
    cx = (max(xs) + min(xs)) / 2
    cy = (max(ys) + min(ys)) / 2
    return [(8 + (x - cx) * s, 8 + (y - cy) * s) for x, y in pts]


def main():
    os.makedirs(OUT, exist_ok=True)
    save(rounded_rect(48, 48, 20), "shape_r10")
    save(rounded_rect(48, 48, 20, stroke=2), "shape_r10_stroke1")
    save(rounded_rect(64, 64, 28), "shape_r14")
    save(rounded_rect(96, 96, 48), "shape_pill")
    heart = [heart_path()]
    back = [[(12.5, 4.5), (7, 10), (12.5, 15.5)]]
    for density, suffix in ((1.0, "1x"), (1.5, "1_5x"), (2.0, "2x")):
        save(stroke_icon(16, density, 1.5, heart), "heart_" + suffix)
        save(stroke_icon(20, density, 2.0, back), "back_" + suffix)


if __name__ == "__main__":
    main()
