#!/usr/bin/env python3
"""Turns the supporter hat's Blockbench model into the mod's hat: the part table in HatModel.java and the felt texture.

    python3 scripts/hat-model.py [art/cowboy_hat.bbmodel]

The model is a Java block model worn on the head, so it lands where Blockbench's head display view shows it: each
element is carried through the model's head display transform and vanilla's head-item placement into head pixels, as a
model part (pivot and rotation) with a box. Elements named "Band" get the dark band texture, the rest the felt; both
are grey so the supporter's colour tints them. Blockbench's inflate only keeps touching faces apart, so each 0.001 of
it becomes 0.01 of a pixel in game, enough to stop them flickering at a distance.
"""
import json
import math
import os
import random
import re
import struct
import sys
import zlib

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JAVA = os.path.join(ROOT, "src/client/java/dev/horsingaround/client/cosmetic/HatModel.java")
TEXTURE = os.path.join(ROOT, "src/main/resources/assets/horsingaround/textures/entity/cowboy_hat.png")
TEX_SIZE = 64
FELT = (219, 243)
BAND = (114, 126)


def mul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]


def apply(m, v):
    return [sum(m[i][k] * v[k] for k in range(3)) for i in range(3)]


def rot(axis, degrees):
    c, s = math.cos(math.radians(degrees)), math.sin(math.radians(degrees))
    return {
        "x": [[1, 0, 0], [0, c, -s], [0, s, c]],
        "y": [[c, 0, s], [0, 1, 0], [-s, 0, c]],
        "z": [[c, -s, 0], [s, c, 0], [0, 0, 1]],
    }[axis]


def head_transform(display):
    """Block pixel -> head pixel, as (scale, rotation, offset): the head display transform, then vanilla's
    CustomHeadLayer.translateToHead (up 4 pixels, turned round, scaled 0.625 and flipped upright)."""
    r = display.get("rotation", [0, 0, 0])
    t = display.get("translation", [0, 0, 0])
    if display.get("scale", [1, 1, 1]) != [1, 1, 1]:
        sys.exit("a head display scale isn't supported")
    item = mul(mul(rot("x", r[0]), rot("y", r[1])), rot("z", r[2]))  # ItemTransform: rotationXYZ
    flip = [[-1, 0, 0], [0, -1, 0], [0, 0, 1]]  # turned round, then scaled (1, -1, -1)
    rotation = mul(flip, item)
    offset = [a - b for a, b in zip([0, -4 - 0.625 * t[1], 0.625 * t[2]], [0.625 * x for x in apply(rotation, [8, 8, 8])])]
    offset[0] -= 0.625 * t[0]
    return 0.625, rotation, offset


def euler_zyx(m):
    """Rotation matrix -> (x, y, z) radians with m = Rz Ry Rx, the order model parts use."""
    if abs(m[2][0]) < 0.99999:
        y = math.asin(-m[2][0])
        return math.atan2(m[2][1], m[2][2]), y, math.atan2(m[1][0], m[0][0])
    return math.atan2(-m[1][2], m[1][1]), math.copysign(math.pi / 2, -m[2][0]), 0.0


def num(v):
    s = f"{v:.4f}".rstrip("0").rstrip(".")
    return ("0" if s in ("-0", "") else s) + "F"


def parts(model):
    scale, head_rot, offset = head_transform(model["display"]["head"])
    felt, band = [], []
    for e in model["elements"]:
        lo, hi = e["from"], e["to"]
        if sum(1 for a, b in zip(lo, hi) if a == b) >= 2:
            continue  # a line or a point: nothing to draw
        o = e.get("origin", [8, 8, 8])
        rx, ry, rz = e.get("rotation", [0, 0, 0])
        r = mul(head_rot, mul(mul(rot("z", rz), rot("y", ry)), rot("x", rx)))  # Blockbench rotates z, y, x
        pivot = [scale * a + b for a, b in zip(apply(head_rot, o), offset)]
        grow = e.get("inflate", 0) * 10
        corner = [scale * (a - b) - grow for a, b in zip(lo, o)]
        size = [scale * (b - a) + 2 * grow for a, b in zip(lo, hi)]
        row = pivot + list(euler_zyx(r)) + corner + size
        (band if "band" in e["name"].lower() else felt).append(row)
    return felt, band


def uv_extent(rows):
    """Box UV area (width, height) the rows' boxes cover from their texture offset."""
    return (math.ceil(max(2 * (r[9] + r[11]) for r in rows)), math.ceil(max(r[11] + r[10] for r in rows)))


def write_png(path, pixels):
    raw = b"".join(b"\0" + bytes(c for px in row for c in px) for row in pixels)

    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)

    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", TEX_SIZE, TEX_SIZE, 8, 6, 0, 0, 0))
                + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def main():
    source = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, "art/cowboy_hat.bbmodel")
    with open(source) as f:
        felt, band = parts(json.load(f))
    if not band:
        sys.exit("no element named Band")
    felt_size, band_size = uv_extent(felt), uv_extent(band)
    band_v = felt_size[1]
    if max(felt_size[0], band_size[0]) > TEX_SIZE or band_v + band_size[1] > TEX_SIZE:
        sys.exit("the parts don't fit the texture")

    noise = random.Random(7)
    pixels = [[(0, 0, 0, 0)] * TEX_SIZE for _ in range(TEX_SIZE)]
    for (w, h), v0, (lo, hi) in ((felt_size, 0, FELT), (band_size, band_v, BAND)):
        for y in range(v0, v0 + h):
            for x in range(w):
                g = noise.randint(lo, hi)
                pixels[y][x] = (g, g, g, 255)
    write_png(TEXTURE, pixels)

    def table(name, rows):
        lines = [f"\tprivate static final float[][] {name} = {{"]
        lines += ["\t\t{" + ", ".join(num(v) for v in r) + "}," for r in rows]
        return lines + ["\t};"]

    block = [f"\t// Generated by scripts/hat-model.py from art/cowboy_hat.bbmodel. Each row: pivot x, y, z; rotation x, y, z "
             f"(radians);", "\t// box corner x, y, z; box size x, y, z.", f"\tprivate static final int BAND_V = {band_v};"]
    block += table("FELT", felt) + table("BAND", band)
    with open(JAVA) as f:
        java = f.read()
    java, n = re.subn(r"(// <hat-model>\n).*?(\t// </hat-model>)", lambda m: m.group(1) + "\n".join(block) + "\n" + m.group(2),
                      java, flags=re.S)
    if n != 1:
        sys.exit("HatModel.java has no <hat-model> block")
    with open(JAVA, "w") as f:
        f.write(java)
    print(f"{len(felt)} felt and {len(band)} band parts; felt UV {felt_size}, band UV {band_size} at v {band_v}")


if __name__ == "__main__":
    main()
