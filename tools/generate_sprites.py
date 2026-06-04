#!/usr/bin/env python3
"""Generate all game sprites from scratch using Pillow. Replaces empty PNGs."""

import math
import os
from PIL import Image, ImageDraw, ImageFont

RESOURCES = os.path.join(os.path.dirname(__file__), "..", "client", "src", "main", "resources", "sprites")
os.makedirs(RESOURCES, exist_ok=True)

# ── Helpers ──────────────────────────────────────────

def circle_mask(draw, cx, cy, r, fill):
    draw.ellipse([cx - r, cy - r, cx + r, cy + r], fill=fill)

def player_body(draw, color_dark, color_light, facing_right=True):
    """Top-down humanoid: head, body, gun arm, legs. 32x32 canvas."""
    w, h = 32, 32
    # Body (torso)
    draw.rounded_rectangle([12, 14, 20, 24], radius=2, fill=color_light)
    # Head
    circle_mask(draw, 16, 9, 5, color_light)
    # Eyes (direction indicator)
    ex = 19 if facing_right else 13
    draw.ellipse([ex - 1, 7, ex + 1, 9], fill=(255, 255, 255))
    # Arms
    draw.rounded_rectangle([6, 14, 11, 18], radius=2, fill=color_dark)  # left arm
    if facing_right:
        draw.rounded_rectangle([21, 13, 27, 16], radius=1, fill=color_dark)  # gun arm right
    else:
        draw.rounded_rectangle([5, 13, 11, 16], radius=1, fill=color_dark)  # gun arm left
    # Legs
    draw.rounded_rectangle([12, 25, 16, 31], radius=1, fill=color_dark)
    draw.rounded_rectangle([16, 25, 20, 31], radius=1, fill=color_dark)
    # Boots
    draw.rectangle([11, 30, 17, 31], fill=(30, 30, 30))
    draw.rectangle([15, 30, 21, 31], fill=(30, 30, 30))
    # Outline
    circle_mask(draw, 16, 9, 5, None)
    draw.ellipse([15, 3, 17, 5], fill=color_light)  # head top highlight

def player_dead(draw):
    """Dead player: flat/horizontal gray figure."""
    # Body horizontal
    draw.rounded_rectangle([6, 12, 26, 20], radius=3, fill=(80, 80, 80))
    # Head turned sideways
    circle_mask(draw, 4, 16, 4, (60, 60, 60))
    # X eyes
    draw.line([2, 14, 6, 18], fill=(40, 40, 40), width=1)
    draw.line([6, 14, 2, 18], fill=(40, 40, 40), width=1)
    # Limbs
    draw.rounded_rectangle([22, 10, 28, 13], radius=1, fill=(70, 70, 70))
    draw.rounded_rectangle([22, 19, 28, 22], radius=1, fill=(70, 70, 70))

# ── Sprites ──────────────────────────────────────────

def gen_player_blue():
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    player_body(draw, color_dark=(30, 100, 200), color_light=(60, 140, 255), facing_right=True)
    return img

def gen_player_red():
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    player_body(draw, color_dark=(200, 40, 40), color_light=(255, 80, 80), facing_right=True)
    return img

def gen_player_dead():
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    player_dead(draw)
    return img

def gen_bullet_normal():
    img = Image.new("RGBA", (8, 8), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    # Glow outer
    circle_mask(draw, 4, 4, 3, (255, 200, 50, 60))
    # Core
    circle_mask(draw, 4, 4, 2, (255, 220, 80, 255))
    # Hot center
    circle_mask(draw, 4, 4, 1, (255, 255, 200, 255))
    return img

def gen_bullet_tracer():
    img = Image.new("RGBA", (16, 4), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    # Tracer tail
    draw.rectangle([0, 1, 10, 3], fill=(255, 200, 50, 100))
    # Bright head
    draw.ellipse([10, 0, 16, 4], fill=(255, 220, 80, 255))
    draw.ellipse([12, 1, 15, 3], fill=(255, 255, 200))
    return img

def gen_crosshair():
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    cx, cy = 16, 16
    g = 5   # gap from center
    L = 12  # line length
    w = 2   # line width
    color = (240, 246, 252, 220)
    # Top
    draw.rectangle([cx - w//2, cy - L - g, cx + w//2, cy - g], fill=color)
    # Bottom
    draw.rectangle([cx - w//2, cy + g, cx + w//2, cy + L + g], fill=color)
    # Left
    draw.rectangle([cx - L - g, cy - w//2, cx - g, cy + w//2], fill=color)
    # Right
    draw.rectangle([cx + g, cy - w//2, cx + L + g, cy + w//2], fill=color)
    # Center dot
    circle_mask(draw, cx, cy, 1, (88, 166, 255, 200))
    return img

def gen_hp_bar():
    img = Image.new("RGBA", (180, 16), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    # Background
    draw.rounded_rectangle([0, 0, 179, 15], radius=3, fill=(13, 17, 23, 200))
    # Border
    draw.rounded_rectangle([0, 0, 179, 15], radius=3, outline=(48, 54, 61, 255), width=1)
    # Inner bar background hint
    draw.rounded_rectangle([2, 2, 177, 13], radius=2, fill=(22, 27, 34, 180))
    return img

def gen_pistol():
    img = Image.new("RGBA", (32, 16), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    # Barrel
    draw.rectangle([2, 3, 18, 5], fill=(180, 180, 190))
    # Body/grip
    draw.rectangle([18, 3, 22, 9], fill=(140, 140, 150))
    # Trigger guard
    draw.arc([20, 6, 26, 12], 180, 270, fill=(120, 120, 130), width=1)
    # Grip
    draw.rectangle([22, 9, 26, 14], fill=(100, 80, 70))
    # Muzzle
    draw.rectangle([0, 2, 2, 6], fill=(220, 220, 230))
    # Barrel highlight
    draw.line([3, 3, 17, 3], fill=(220, 220, 240), width=1)
    return img

def gen_rifle():
    img = Image.new("RGBA", (32, 16), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    # Long barrel
    draw.rectangle([0, 3, 20, 5], fill=(160, 160, 170))
    # Stock
    draw.rectangle([20, 2, 28, 6], fill=(100, 80, 60))
    # Magazine
    draw.rectangle([12, 6, 16, 11], fill=(80, 80, 90))
    # Scope
    draw.rectangle([8, 0, 14, 3], fill=(60, 60, 70))
    draw.ellipse([9, -2, 13, 2], fill=(80, 160, 255, 150))
    # Grip
    draw.rectangle([16, 6, 20, 13], fill=(90, 70, 50))
    # Muzzle
    draw.rectangle([0, 2, 3, 6], fill=(200, 200, 210))
    # Barrel highlight
    draw.line([3, 3, 19, 3], fill=(200, 200, 220), width=1)
    return img

def gen_muzzle_flash():
    img = Image.new("RGBA", (24, 24), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    cx, cy = 12, 12
    # Outer glow
    circle_mask(draw, cx, cy, 11, (255, 180, 40, 80))
    # Star shape
    circle_mask(draw, cx, cy, 8, (255, 200, 50, 180))
    # Core
    circle_mask(draw, cx, cy, 4, (255, 255, 180, 255))
    # Hot center
    circle_mask(draw, cx, cy, 2, (255, 255, 255, 255))
    # Rays
    for angle in range(0, 360, 45):
        rad = math.radians(angle)
        x1 = cx + math.cos(rad) * 4
        y1 = cy + math.sin(rad) * 4
        x2 = cx + math.cos(rad) * 10
        y2 = cy + math.sin(rad) * 10
        draw.line([x1, y1, x2, y2], fill=(255, 220, 60, 150), width=2)
    return img

def gen_explosion_01():
    img = Image.new("RGBA", (48, 48), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    cx, cy = 24, 24
    # Outer ring
    circle_mask(draw, cx, cy, 22, (255, 80, 20, 60))
    # Mid burst
    circle_mask(draw, cx, cy, 16, (255, 140, 30, 140))
    # Inner fire
    circle_mask(draw, cx, cy, 10, (255, 200, 50, 200))
    # Core
    circle_mask(draw, cx, cy, 5, (255, 255, 180, 255))
    # Irregular edges via rays
    for angle in range(0, 360, 30):
        rad = math.radians(angle)
        length = 14 + (angle % 60) * 0.2
        x1 = cx + math.cos(rad) * 8
        y1 = cy + math.sin(rad) * 8
        x2 = cx + math.cos(rad) * length
        y2 = cy + math.sin(rad) * length
        draw.line([x1, y1, x2, y2], fill=(255, 120, 20, 100), width=3)
    # Smoke puffs (small gray circles at edges)
    smoke_positions = [
        (8, 10), (38, 8), (40, 36), (10, 38),
        (24, 6), (42, 22), (6, 24),
    ]
    for sx, sy in smoke_positions:
        circle_mask(draw, sx, sy, 5, (100, 100, 100, 80))
    return img

def gen_blood_splat():
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    cx, cy = 16, 16
    # Main splatter
    draw.ellipse([4, 6, 28, 26], fill=(180, 30, 30, 220))
    # Irregular spots
    spots = [(8, 8, 4), (24, 10, 5), (6, 18, 3), (26, 20, 4), (16, 4, 3), (10, 24, 3)]
    for sx, sy, sr in spots:
        circle_mask(draw, sx, sy, sr, (200, 20, 20, 200))
    # Darker core
    draw.ellipse([10, 10, 22, 22], fill=(140, 10, 10, 230))
    return img


# ── Main ─────────────────────────────────────────────

SPRITES = {
    "player/player_blue.png":     gen_player_blue,
    "player/player_red.png":      gen_player_red,
    "player/player_dead.png":     gen_player_dead,
    "bullet/bullet_normal.png":   gen_bullet_normal,
    "bullet/bullet_tracer.png":   gen_bullet_tracer,
    "ui/crosshair.png":           gen_crosshair,
    "ui/hp_bar.png":              gen_hp_bar,
    "weapon/pistol.png":          gen_pistol,
    "weapon/rifle.png":           gen_rifle,
    "effects/muzzle_flash.png":   gen_muzzle_flash,
    "effects/explosion_01.png":   gen_explosion_01,
    "effects/blood_splat.png":    gen_blood_splat,
}

if __name__ == "__main__":
    for path, gen_fn in SPRITES.items():
        full = os.path.join(RESOURCES, path)
        os.makedirs(os.path.dirname(full), exist_ok=True)
        img = gen_fn()
        img.save(full, "PNG")
        size = os.path.getsize(full)
        print(f"  ✓ {path} ({img.width}x{img.height}, {size} bytes)")
    print(f"\n{len(SPRITES)} sprites generated → {RESOURCES}")
