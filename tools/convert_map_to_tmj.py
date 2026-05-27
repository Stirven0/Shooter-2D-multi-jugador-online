#!/usr/bin/env python3
"""Convert legacy map JSON to Tiled TMJ format."""

import json
import math
import sys
import os

TILE_SIZE = 32
TILESET_NAME = "warehouse"
TILESET_FIRSTGID = 1
TILESET_TILE_COUNT = 10
TILESET_COLUMNS = 5

TILE_FLOOR = 1
TILE_WALL = 2

TILESET_TILES = [
    {"id": 0, "type": "floor"},
    {"id": 1, "type": "wall", "properties": [{"name": "solid", "type": "bool", "value": True}]},
    {"id": 2, "type": "crate", "properties": [{"name": "solid", "type": "bool", "value": True}]},
]

def obstacles_to_tiles(obstacles, map_cols, map_rows):
    tile_grid = [[0] * map_cols for _ in range(map_rows)]
    for obs in obstacles:
        x, y, w, h = obs["x"], obs["y"], obs["w"], obs["h"]
        col_start = max(0, math.floor(x / TILE_SIZE))
        col_end = min(map_cols - 1, math.ceil((x + w) / TILE_SIZE) - 1)
        row_start = max(0, math.floor(y / TILE_SIZE))
        row_end = min(map_rows - 1, math.ceil((y + h) / TILE_SIZE) - 1)
        for row in range(row_start, row_end + 1):
            for col in range(col_start, col_end + 1):
                tile_grid[row][col] = TILE_WALL
    return tile_grid


def convert_legacy_map(json_path, output_path=None):
    with open(json_path, "r") as f:
        legacy = json.load(f)

    map_id = legacy["id"]
    map_name = legacy.get("name", map_id)
    width_px = legacy["width"]
    height_px = legacy["height"]
    obstacles = legacy.get("obstacles", [])

    map_cols = math.ceil(width_px / TILE_SIZE)
    map_rows = math.ceil(height_px / TILE_SIZE)
    actual_width_px = map_cols * TILE_SIZE
    actual_height_px = map_rows * TILE_SIZE

    print(f"Converting {json_path}: {width_px}x{height_px} -> {map_cols}x{map_rows} tiles ({actual_width_px}x{actual_height_px} px)")

    ground_data = [TILE_FLOOR] * (map_cols * map_rows)

    walls_grid = obstacles_to_tiles(obstacles, map_cols, map_rows)
    walls_data = []
    for row in walls_grid:
        walls_data.extend(row)

    tmj = {
        "id": map_id,
        "name": map_name,
        "width": map_cols,
        "height": map_rows,
        "tilewidth": TILE_SIZE,
        "tileheight": TILE_SIZE,
        "infinite": False,
        "tilesets": [
            {
                "firstgid": TILESET_FIRSTGID,
                "name": TILESET_NAME,
                "tilewidth": TILE_SIZE,
                "tileheight": TILE_SIZE,
                "tilecount": TILESET_TILE_COUNT,
                "columns": TILESET_COLUMNS,
                "tiles": TILESET_TILES,
            }
        ],
        "layers": [
            {
                "id": 1,
                "name": "ground",
                "type": "tilelayer",
                "width": map_cols,
                "height": map_rows,
                "visible": True,
                "opacity": 1,
                "data": ground_data,
            },
            {
                "id": 2,
                "name": "walls",
                "type": "tilelayer",
                "width": map_cols,
                "height": map_rows,
                "visible": True,
                "opacity": 1,
                "data": walls_data,
            },
        ],
    }

    if output_path is None:
        base = os.path.splitext(json_path)[0]
        output_path = base + ".tmj"

    with open(output_path, "w") as f:
        json.dump(tmj, f, indent=2)

    print(f"Wrote {output_path}")


if __name__ == "__main__":
    maps_dir = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                            "server", "src", "main", "resources", "maps")
    for map_num in ["02", "03", "04"]:
        json_path = os.path.join(maps_dir, f"map_{map_num}.json")
        if os.path.exists(json_path):
            convert_legacy_map(json_path)
        else:
            print(f"Not found: {json_path}")
