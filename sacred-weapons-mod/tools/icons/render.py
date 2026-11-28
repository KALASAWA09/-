"""Renders the compact symbol icons from icons.py.

usage: python3 render.py preview.png [texture_dir]
"""
import colorsys
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from icons import I  # noqa: E402
from PIL import Image, ImageDraw  # noqa: E402

OUTLINE = (34, 20, 30, 255)
SHADOW = (16, 9, 14, 255)
LIGHT = (255, 255, 236)
DARK = (40, 14, 44)


def hexc(h):
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def mix(a, b, t):
    return tuple(int(a[i] * (1 - t) + b[i] * t) for i in range(3))


def punch(c):
    r, g, b = [v / 255 for v in c]
    h, l, s = colorsys.rgb_to_hls(r, g, b)
    r, g, b = colorsys.hls_to_rgb(h, l, min(1.0, s * 1.1))
    return (int(r * 255), int(g * 255), int(b * 255))


def grid(name):
    pal, rows = I[name]
    width = max(len(r) for r in rows)
    bad = [r for r in rows if len(r) != width]
    if bad:
        print("BAD", name, bad)
    cells = {}
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != '.':
                cells[(x, y)] = ch
    xs = [x for x, _ in cells]
    ys = [y for _, y in cells]
    w = max(xs) - min(xs) + 1
    h = max(ys) - min(ys) + 1
    # Centre the shape plus its outline and shadow (w + 3) on the 16px canvas.
    ox = (13 - w) // 2 + 1 - min(xs)
    oy = (13 - h) // 2 + 1 - min(ys)
    return {(x + ox, y + oy): ch for (x, y), ch in cells.items()}, pal


def image(name):
    cells, pal = grid(name)
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for (x, y), ch in cells.items():
        c = punch(hexc(pal[ch]))
        if ch != 'w' and sum(c) > 160:
            lit = ((x, y - 1) not in cells) + ((x - 1, y) not in cells)
            dark = ((x, y + 1) not in cells) + ((x + 1, y) not in cells)
            if lit > dark:
                c = mix(c, LIGHT, 0.38)
            elif dark > lit:
                c = mix(c, DARK, 0.32)
        img.putpixel((x, y), c + (255,))
    for y in range(16):
        for x in range(16):
            if (x, y) in cells:
                continue
            if any((x + dx, y + dy) in cells for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                img.putpixel((x, y), OUTLINE)
    solid = {(x, y) for y in range(16) for x in range(16) if img.getpixel((x, y))[3]}
    for x, y in solid:
        p = (x + 1, y + 1)
        if p[0] < 16 and p[1] < 16 and p not in solid:
            img.putpixel(p, SHADOW)
    return img


names = sorted(I)

if __name__ == "__main__":
    cols, scale, pad = 9, 8, 10
    cell = 16 * scale
    rows = (len(names) + cols - 1) // cols
    sheet = Image.new("RGBA", (cols * (cell + pad) + pad, rows * (cell + pad + 12) + pad), (70, 72, 76, 255))
    d = ImageDraw.Draw(sheet)
    for i, n in enumerate(names):
        im = image(n).resize((cell, cell), Image.NEAREST)
        x = pad + (i % cols) * (cell + pad)
        y = pad + (i // cols) * (cell + pad + 12)
        sheet.alpha_composite(im, (x, y))
        d.text((x, y + cell), n, fill=(220, 220, 220, 255))
    sheet.save(sys.argv[1])
    if len(sys.argv) > 2:
        for n in names:
            image(n).save(os.path.join(sys.argv[2], n + ".png"))
    print(len(names), "icons")
