#!/usr/bin/env python3
"""Generate warehouse tileset sprite sheet (160x64, 10 tiles of 32x32)."""

from PIL import Image, ImageDraw
import os
import sys

TILE_SIZE = 32
COLS = 5
ROWS = 2
SHEET_W = COLS * TILE_SIZE
SHEET_H = ROWS * TILE_SIZE


def draw_floor(draw, x, y):
    """GID 1 — Dark grey floor with horizontal plank effect."""
    for row_off in range(0, TILE_SIZE, 4):
        shade = 33 + (row_off // 4) * 2
        draw.rectangle([x, y + row_off, x + TILE_SIZE - 1, y + row_off + 3], fill=(shade, 38, 45))
    draw.line([x, y + 15, x + TILE_SIZE - 1, y + 15], fill=(28, 33, 40), width=1)
    draw.line([x, y + 31, x + TILE_SIZE - 1, y + 31], fill=(28, 33, 40), width=1)


def draw_wall_top(draw, x, y):
    """GID 2 — Grey brick wall."""
    bricks = [
        (0, 0, 14, 7), (16, 0, 31, 7),
        (8, 8, 23, 15), (0, 8, 7, 15), (24, 8, 31, 15),
        (0, 16, 14, 23), (16, 16, 31, 23),
        (8, 24, 23, 31), (0, 24, 7, 31), (24, 24, 31, 31),
    ]
    for bx, by, bx2, by2 in bricks:
        draw.rectangle([x + bx, y + by, x + bx2, y + by2], fill=(48, 54, 61))
        draw.rectangle([x + bx, y + by, x + bx2, y + by2], outline=(28, 33, 40), width=1)


def draw_crate(draw, x, y):
    """GID 3 — Brown wooden crate with cross lines."""
    draw.rectangle([x, y, x + TILE_SIZE - 1, y + TILE_SIZE - 1], fill=(139, 115, 85))
    draw.rectangle([x, y, x + TILE_SIZE - 1, y + TILE_SIZE - 1], outline=(100, 80, 55), width=2)
    draw.line([x, y, x + TILE_SIZE - 1, y + TILE_SIZE - 1], fill=(120, 100, 70), width=2)
    draw.line([x + TILE_SIZE - 1, y, x, y + TILE_SIZE - 1], fill=(120, 100, 70), width=2)
    draw.rectangle([x + 6, y + 6, x + TILE_SIZE - 7, y + TILE_SIZE - 7], outline=(100, 80, 55), width=1)


def draw_floor_alt(draw, x, y):
    """GID 4 — Blue-tinted variant of floor."""
    for row_off in range(0, TILE_SIZE, 4):
        shade = 37 + (row_off // 4) * 2
        draw.rectangle([x, y + row_off, x + TILE_SIZE - 1, y + row_off + 3], fill=(shade, 42, 51))
    draw.line([x, y + 15, x + TILE_SIZE - 1, y + 15], fill=(30, 35, 44), width=1)


def draw_floor_dark(draw, x, y):
    """GID 5 — Darker floor with subtle grid."""
    for row_off in range(0, TILE_SIZE, 4):
        draw.rectangle([x, y + row_off, x + TILE_SIZE - 1, y + row_off + 3], fill=(26, 31, 39))
    for c in range(0, TILE_SIZE, 8):
        draw.line([x + c, y, x + c, y + TILE_SIZE - 1], fill=(22, 27, 34), width=1)
    for r in range(0, TILE_SIZE, 8):
        draw.line([x, y + r, x + TILE_SIZE - 1, y + r], fill=(22, 27, 34), width=1)


def draw_barrel(draw, x, y):
    """GID 6 — Brown-red barrel with metal bands."""
    draw.ellipse([x + 4, y, x + TILE_SIZE - 5, y + TILE_SIZE - 1], fill=(107, 76, 59))
    draw.ellipse([x + 4, y, x + TILE_SIZE - 5, y + TILE_SIZE - 1], outline=(80, 55, 40), width=1)
    for band_y in [y + 4, y + 14, y + 24]:
        draw.line([x + 2, band_y, x + TILE_SIZE - 3, band_y], fill=(140, 130, 120), width=2)
    draw.ellipse([x + 12, y + 2, x + TILE_SIZE - 13, y + 6], fill=(160, 150, 140))


def draw_pipe(draw, x, y):
    """GID 7 — Grey metal pipe vertical."""
    draw.rectangle([x + 10, y, x + TILE_SIZE - 11, y + TILE_SIZE - 1], fill=(72, 79, 88))
    draw.rectangle([x + 10, y, x + TILE_SIZE - 11, y + TILE_SIZE - 1], outline=(55, 60, 68), width=1)
    draw.line([x + 14, y, x + 14, y + TILE_SIZE - 1], fill=(90, 97, 106), width=1)
    draw.line([x + TILE_SIZE - 15, y, x + TILE_SIZE - 15, y + TILE_SIZE - 1], fill=(55, 60, 68), width=1)
    for seg_y in [y + 8, y + 20]:
        draw.rectangle([x + 8, seg_y, x + TILE_SIZE - 9, seg_y + 3], fill=(55, 60, 68))


def draw_vent(draw, x, y):
    """GID 8 — Metal vent with cross grid."""
    draw.rectangle([x, y, x + TILE_SIZE - 1, y + TILE_SIZE - 1], fill=(26, 31, 39))
    draw.rectangle([x + 2, y + 2, x + TILE_SIZE - 3, y + TILE_SIZE - 3], fill=(38, 44, 52))
    draw.rectangle([x + 2, y + 2, x + TILE_SIZE - 3, y + TILE_SIZE - 3], outline=(55, 60, 68), width=1)
    for c in range(4, TILE_SIZE, 8):
        draw.line([x + c, y + 2, x + c, y + TILE_SIZE - 3], fill=(55, 60, 68), width=1)
    for r in range(4, TILE_SIZE, 8):
        draw.line([x + 2, y + r, x + TILE_SIZE - 3, y + r], fill=(55, 60, 68), width=1)


def draw_hazard(draw, x, y):
    """GID 9 — Yellow-black diagonal hazard stripes."""
    for i in range(-TILE_SIZE, TILE_SIZE * 2, 8):
        draw.polygon(
            [(x + i, y), (x + i + 4, y), (x + i - 4, y + TILE_SIZE), (x + i, y + TILE_SIZE)],
            fill=(210, 153, 34),
        )
        draw.polygon(
            [(x + i + 4, y), (x + i + 8, y), (x + i, y + TILE_SIZE), (x + i + 4, y + TILE_SIZE)],
            fill=(28, 33, 40),
        )


def draw_computer(draw, x, y):
    """GID 10 — Dark monitor/panel with LEDs."""
    draw.rectangle([x + 4, y + 2, x + TILE_SIZE - 5, y + TILE_SIZE - 3], fill=(22, 27, 34))
    draw.rectangle([x + 4, y + 2, x + TILE_SIZE - 5, y + TILE_SIZE - 3], outline=(48, 54, 61), width=1)
    draw.rectangle([x + 8, y + 6, x + TILE_SIZE - 9, y + TILE_SIZE - 10], fill=(13, 17, 23))
    draw.rectangle([x + 8, y + 6, x + TILE_SIZE - 9, y + TILE_SIZE - 10], outline=(48, 54, 61), width=1)
    draw.rectangle([x + 10, y + 18, x + TILE_SIZE - 11, y + TILE_SIZE - 11], fill=(13, 17, 23))
    draw.ellipse([x + 8, y + TILE_SIZE - 8, x + 12, y + TILE_SIZE - 4], fill=(46, 160, 67))
    draw.ellipse([x + 16, y + TILE_SIZE - 8, x + 20, y + TILE_SIZE - 4], fill=(248, 81, 73))
    draw.ellipse([x + 24, y + TILE_SIZE - 8, x + 28, y + TILE_SIZE - 4], fill=(46, 160, 67))


TILE_DRAWERS = [
    draw_floor,
    draw_wall_top,
    draw_crate,
    draw_floor_alt,
    draw_floor_dark,
    draw_barrel,
    draw_pipe,
    draw_vent,
    draw_hazard,
    draw_computer,
]


def generate_sprite_sheet(output_path):
    img = Image.new("RGBA", (SHEET_W, SHEET_H), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    for gid in range(1, 11):
        idx = gid - 1
        col = idx % COLS
        row = idx // COLS
        x = col * TILE_SIZE
        y = row * TILE_SIZE
        TILE_DRAWERS[idx](draw, x, y)

    img.save(output_path, "PNG")
    actual_size = os.path.getsize(output_path)
    print(f"Generated {output_path} ({SHEET_W}x{SHEET_H}, {actual_size} bytes)")


if __name__ == "__main__":
    script_dir = os.path.dirname(os.path.abspath(__file__))
    default_output = os.path.join(script_dir, "..", "client", "src", "main", "resources", "maps", "warehouse.png")
    output_path = sys.argv[1] if len(sys.argv) > 1 else default_output
    output_path = os.path.abspath(output_path)
    os.makedirs(os.path.dirname(output_path), exist_ok=True)
    generate_sprite_sheet(output_path)
