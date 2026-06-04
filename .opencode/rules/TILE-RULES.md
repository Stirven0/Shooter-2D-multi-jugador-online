# Reglas del Motor de Tiles 2D

## Ramas y estados del sistema

- **`develop`** (stable actual): Sistema legacy de mapas con obstáculos rectangulares JSON.
- **`tile-engine`** (futuro, sin mergear): Reemplaza el sistema legacy con tilemaps Tiled `.tmj`. No tocar `develop` con código de tile-engine hasta que se mergen.

## Formato TMJ (`tile-engine`)

Archivos `.tmj` en `server/src/main/resources/maps/` y `client/src/main/resources/maps/`:
- 4 mapas: `map_01.tmj` (80×80), `map_02.tmj` (100×100), `map_03.tmj` (120×120), `map_04.tmj` (140×140). Todos con `tilewidth: 32`, `tileheight: 32`.
- 10 tipos de tile (GID 1–10): floor, wall, crate, floor_alt, floor_dark, barrel, pipe, vent, hazard, computer.
- Propiedad `"solid": true` en el tileset de cada GID determina colisión. Solo se chequea esa propiedad — no se usa lógica de capas para colisiones.
- 2 capas: `"ground"` (toda GID 1 = floor) y `"walls"` (GIDs mixtos con marcadores `solid`).
- Capas adicionales `"objectgroup"` opcionales para spawns, pickups, etc.

## Modelos compartidos (`shared/src/main/java/.../model/`)

- **`TileMap.java`**: Contenedor inmutable tras carga. `width`, `height` (en tiles), dimensiones en píxeles (`width * 32`, `height * 32`), `List<TileLayer>`, `List<TileSet>`. Método clave: `isSolid(int col, int row)` — consulta colisión por coordenada de tile.
- **`TileLayer.java`**: Tipo `"tilelayer"` con `int[][] data` o `"objectgroup"` con `List<TileObject>`.
- **`TileSet.java`**: `firstGid`, `tileCount`, `columns`, `image` (ruta al sprite sheet), mapa de propiedades por tile (`"solid": true`).
- **`TileObject.java`**: `id`, `name`, `type`, `position` (x,y en píxeles), `width`, `height`, `rotation`, `properties`.
- **`MapDataMessage.java`** (`MessageType.MAP_DATA`): Contiene `mapId` + `TileMap` completo. Se envía **exactamente una vez** al iniciar la partida. Nunca incluir datos de tiles en los ticks de GameState.

## Carga en servidor (`tile-engine`)

- **`server/.../game/map/TiledMapLoader.java`** (191 líneas): Parsea `.tmj` con `JsonParser` de Gson. Aplica *horizontal run merging*: tiles sólidos adyacentes en una fila se fusionan en un solo `Obstacle` para el motor de física legacy. Devuelve `GameMap` con `List<Obstacle>` y `TileMap`.
- **`server/.../game/map/MapManager.java`**: Intenta `TiledMapLoader.loadFromTmj("/maps/map_XX.tmj")` primero. Si falla (archivo no existe, parseo roto), hace fallback a `MapLoader.loadFromJson()` (legacy). Esta lógica permite que ambas ramas coexistan en `tile-engine`.
- Solamente el servidor genera obstáculos de colisión. El cliente recibe el `TileMap` vía `MapDataMessage` y lo usa exclusivamente para renderizado.

## Renderizado en cliente (`tile-engine`)

- **`client/.../render/TileRenderer.java`** (112 líneas): Renderiza tiles con viewport culling. Calcula `startCol/endCol` y `startRow/endRow` según la cámara. Si el tileset tiene imagen (`warehouse.png`), usa `GraphicsContext.drawImage()` con subregiones de 32×32. Si no, usa colores de `TileColors`. Los tiles sólidos reciben un stroke oscuro de borde. **Obligatorio el culling** — jamás iterar el mapa entero (80×80 = 6400 tiles, 140×140 = 19600 tiles).
- **`client/.../render/TileColors.java`** (51 líneas): 11 colores predefinidos para GIDs 0–10. Para GIDs desconocidos, usa hash determinista (`tileId * 2654435761L`) con caché. Soporta override de paleta vía `setPalette()` / `resetPalette()`.
- **`client/.../ui/HudRenderer.java`** (204 líneas): Extrae todo el dibujo de HUD del `Renderer`: barra HP, barra de escudo, info de armas, skills (teclas E/F con cooldowns), scoreboard, kill feed, overlay de debug. Separado del renderizado de juego para mantener `Renderer` enfocado en el mundo.

## Sprite sheet

- `client/src/main/resources/maps/warehouse.png`: 160×64 px (5 columnas × 2 filas = 10 tiles de 32×32).
- Scripts auxiliares: `tools/generate_tileset.py` (genera el sprite sheet con Pillow) y `tools/convert_map_to_tmj.py` (convierte `map_XX.json` legacy a `.tmj` mapeando obstáculos rectangulares a grid de tiles).

## Sistema legacy (`develop`)

- Mapas JSON en `server/src/main/resources/maps/map_0X.json`.
- Formato: `{"id":"map_01", "width":2500, "height":2500, "spawnPoints":[...], "obstacles":[{"x":200,"y":200,"w":400,"h":50,"type":"wall"},...]}`.
- **`Obstacle`** es un `record` con `x`, `y`, `width`, `height`. Colisión vía `intersectsCircle()`.
- No hay tiles, capas ni sprites. Todo es rectángulos con color sólido.

## Reglas obligatorias

1. **Preferir siempre TMJ** para mapas nuevos. Legacy JSON solo para compatibilidad hacia atrás con `develop`.
2. **`MapDataMessage` se envía UNA sola vez** en `GameInstance.start()`. Jamás incluir datos de tile en los ticks de `GameState` (30 Hz). El cliente cachea en `GameClientState.cachedTileMap` y `Renderer.cachedTileMap`. Limpiar caché en `GAME_END`, `LOGOUT` y al salir de sala.
3. **GID 0 = vacío** (sin tile). Las consultas de colisión solo chequean GIDs con propiedad `"solid": true` en el tileset.
4. **Viewport culling obligatorio** en `TileRenderer`. No iterar tiles fuera de la cámara. Un mapa 140×140 sin culling son ~19600 draw calls por frame — inaceptable.
5. **Sprite sheets en `client/src/main/resources/maps/`**. Cargar con `getClass().getResourceAsStream("/maps/warehouse.png")`.
6. **Tamaño de tile fijo: 32×32 píxeles**. Asumido hardcoded en `TileRenderer`, `TiledMapLoader`, y `TileMap`. Si se cambia, requiere refactor en los 3 archivos.
7. **No mezclar imports de ramas**: En `develop`, no existen `TileMap`, `TiledMapLoader`, `TileRenderer`, ni `MapDataMessage`. En `tile-engine`, el `MapManager` hace fallback automático a legacy — no se necesitan condicionales extra.
