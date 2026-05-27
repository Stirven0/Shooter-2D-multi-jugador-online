# Tile Engine Skill

## Description
Working with Tiled .tmj maps, tile rendering with sprite sheet textures, solid tile collision extraction, map loading, and MapDataMessage protocol.

## When to use
- Creating or modifying Tiled .tmj map files
- Changing tile rendering, textures, or fallback colors
- Adding new solid tile types or collision logic
- Debugging map loading or tile culling
- Converting legacy JSON maps to TMJ format
- Generating or modifying the sprite sheet (warehouse.png)

## Tile Engine Architecture

- Maps stored in Tiled JSON format (.tmj) in `server/src/main/resources/maps/`
- `TiledMapLoader.java` parses .tmj with Gson, extracts obstacles via horizontal run merging
- `TileMap` / `TileLayer` / `TileSet` / `TileObject` in shared module
- `TileRenderer.java` in client: viewport-culled rendering with sprite sheet textures (`drawImage()`), fallback to `TileColors` for tiles without textures
- **MapDataMessage**: TileMap se envía 1 sola vez al iniciar partida (NO en cada GameState a 30 Hz)
- `GameInstance.start()` envía `MapDataMessage` vía `messageSender` antes del game loop
- `GameClientState.cachedTileMap` — caché en cliente, `Renderer` usa `setCachedTileMap()`
- `MapManager` intenta TMJ primero para los 4 mapas, fallback a JSON legacy
- Sprite sheet: `client/.../maps/warehouse.png` (160×64, 10 tiles de 32×32 en 5 columnas)
- GID → textura: `localId = gid - firstGid`, `srcRow = localId / columns`, `srcCol = localId % columns`

## Steps for adding a new TMJ map

### 1. Create map file
Place `map_XX.tmj` in `server/src/main/resources/maps/`.

### 2. Convert from legacy JSON (if applicable)
```bash
python3 tools/convert_map_to_tmj.py
```
The script reads `server/.../maps/map_XX.json` and generates `.tmj` with floor + walls layers.

### 3. Define tile properties
In the TMJ tileset, mark solid tiles with:
```json
"properties": [{"name": "solid", "type": "bool", "value": true}]
```

### 4. Add placeholder color in `TileColors.java` (optional — procedural fallback exists)
```java
Map.entry(tileId, Color.rgb(R, G, B))
```

### 5. Registration is automatic
`MapManager.loadDefaults()` loops through `map_01` to `map_04` and tries `.tmj` first.

## Steps for adding new tile textures

### 1. Update `tools/generate_tileset.py`
Add a new tile drawer function and append to `TILE_DRAWERS` list. Each function receives `(draw, x, y)` and paints a 32×32 area.

### 2. Regenerate sprite sheet
```bash
python3 tools/generate_tileset.py
```

### 3. Update TMJ tileset metadata
If tile count changes, update `tilecount` in the TMJ tileset.

### 4. Add solid property if needed
New tile types that should block movement need `"solid": true` in the tileset.

## MapDataMessage protocol
```
Server (1 vez al iniciar):
  MapDataMessage { mapId, tileMap (TileSet[] + TileLayer[]) }
  
Client:
  case MAP_DATA → state.setCachedTileMap(), renderer.setCachedTileMap()
  Renderer.render() → if (cachedTileMap != null) → TileRenderer with textures
                      else → drawObstacles() fallback legacy
```

## Testing tile maps
```bash
mvn clean install -DskipTests && mvn test -pl server
```
The TMJ loads at startup; check logs for "No TMJ for map_NN" to debug loading issues.
