# Tile Engine Development — Motor de tiles 2D

## Descripción
Guía completa para trabajar con el motor de tiles 2D del shooter multijugador: mapas TMJ (Tiled JSON), tilesets, sprite sheets, renderizado con viewport culling, generación de obstáculos y herramientas auxiliares. Todo el código está en la rama `tile-engine` (aún no fusionado a `develop`).

## Cuándo usar este skill
- Crear o modificar mapas `.tmj` (nuevo formato Tiled JSON)
- Añadir nuevos tipos de tile, texturas o colores
- Cambiar propiedades de tiles (solid, damage, spawn points)
- Convertir mapas legacy JSON → TMJ
- Depurar problemas de renderizado de tiles
- Generar o modificar el sprite sheet `warehouse.png`
- Añadir nuevos tilesets con sprite sheets adicionales

---

## Arquitectura general

```
shared/model/TileMap.java          — Modelo inmutable post-carga
shared/model/TileLayer.java        — Capas: tilelayer / objectgroup
shared/model/TileSet.java          — Metadatos: firstGid, tileCount, columns, image, tileProperties
shared/model/TileObject.java       — Objetos de capa objectgroup
shared/message/MapDataMessage.java — Enviado 1 sola vez al iniciar partida (NO per-tick)
shared/message/MessageType.java    — Incluye MAP_DATA

server/game/map/TiledMapLoader.java — Parsea .tmj con Gson, genera Obstacle[] por horizontal run merging
server/game/map/MapManager.java     — Intenta TMJ primero, fallback a JSON legacy (maps 01-04)
server/network/GameServer.java      — Envía MapDataMessage vía messageSender antes del game loop

client/render/TileRenderer.java    — Renderizado con viewport culling, sprite sheet blitting
client/render/TileColors.java      — Paleta de colores fallback (10 GIDs predefinidos + generación procedural)
client/game/GameClientState.java   — cachedTileMap, recibido 1 vez en handleMessage() → MAP_DATA

tools/convert_map_to_tmj.py        — Convierte mapas legacy JSON → TMJ
tools/generate_tileset.py          — Genera warehouse.png programáticamente con Pillow
```

### Flujo de carga

```
1. Server inicia → MapManager.loadDefaults()
2. Para cada map_01..map_04: TiledMapLoader.loadFromTmj("/maps/map_XX.tmj")
3. Si falla (archivo no existe) → fallback a GameMap.loadFromJson()
4. GameInstance.start() → envía MapDataMessage con TileMap completo al cliente
5. Cliente recibe MAP_DATA → state.setCachedTileMap(tileMap)
6. Renderer.render() → TileRenderer.render(gc, cachedTileMap, w, h)
```

### Reglas críticas
- **Tamaño de tile**: SIEMPRE 32×32 píxeles (hardcodeado en TileRenderer, TiledMapLoader, TileMap)
- **GID 0 = vacío**: sin tile, sin colisión. Floor = GID 1, Wall = GID 2, Crate = GID 3
- **MapDataMessage**: se envía UNA SOLA VEZ al iniciar partida, nunca en cada tick (30 Hz)
- **Viewport culling**: obligatorio para rendimiento — solo se dibujan tiles visibles en cámara
- **Horizontal run merging**: el servidor agrupa tiles sólidos consecutivos en la misma fila en un solo Obstacle para optimizar colisiones
- **TileMap inmutable post-carga**: se comparte entre GameMap y MapDataMessage sin copia
- **No mezclar con `develop`**: TileMap, TileRenderer, TiledMapLoader no existen en `develop`

---

## Formato de mapa TMJ

```json
{
  "id": "map_01",
  "name": "Warehouse",
  "width": 80, "height": 80,
  "tilewidth": 32, "tileheight": 32,
  "infinite": false,
  "tilesets": [{
    "firstgid": 1,
    "name": "warehouse",
    "tilewidth": 32, "tileheight": 32,
    "tilecount": 10, "columns": 5,
    "image": "maps/warehouse.png",
    "imagewidth": 160, "imageheight": 64,
    "tiles": [
      {"id": 0, "type": "floor"},
      {"id": 1, "type": "wall", "properties": [{"name": "solid", "type": "bool", "value": true}]},
      {"id": 2, "type": "crate", "properties": [{"name": "solid", "type": "bool", "value": true}]}
    ]
  }],
  "layers": [
    {"id": 1, "name": "ground", "type": "tilelayer", "width": 80, "height": 80,
     "visible": true, "opacity": 1, "data": [1, 1, 1, ...]},
    {"id": 2, "name": "walls", "type": "tilelayer", "width": 80, "height": 80,
     "visible": true, "opacity": 1, "data": [0, 0, 2, ...]}
  ]
}
```

- `data` es un array plano 1D de `width * height` elementos (row-major)
- Capas: "ground" (piso, sin colisión), "walls" (tiles sólidos), pueden añadirse "objects" (objectgroup)
- Dimensiones en píxeles = `width * tilewidth` y `height * tileheight`
- `image` es relativo al classpath (se carga con `getClassLoader().getResourceAsStream()`)

---

## Sprite sheet: warehouse.png

```
Dimensiones: 160×64 px (5 columnas × 2 filas, 10 tiles de 32×32)
Columnas:    col0     col1     col2     col3     col4
Fila 0:      floor    wall     crate    —        —
Fila 1:      —        —        —        —        —
```

Mapeo GID → textura en TileRenderer:
```java
int localId = tileId - ts.getFirstGid();  // GID 1 → localId 0 (floor)
int srcCol = localId % ts.getColumns();    // columna en el sprite sheet
int srcRow = localId / ts.getColumns();    // fila
gc.drawImage(sheet, srcCol*32, srcRow*32, 32, 32, screenX, screenY, 32, 32);
```

---

## TileRenderer — Renderizado con viewport culling

`TileRenderer.render(GraphicsContext gc, TileMap tileMap, double canvasWidth, double canvasHeight)`

Algoritmo:
1. Si `tileMap == null` → retorna (modo legacy, usa drawObstacles)
2. Calcula columnas/filas visibles desde la cámara con margen de +2 tiles
3. Itera capas visibles de tipo "tilelayer"
4. Por cada tile con GID > 0:
   - Busca su `TileSet` (findTileset por rango firstGid)
   - Si el tileset tiene `image` → carga sprite sheet (cacheado en `spriteSheetCache`)
   - Si no hay sprite sheet o la imagen falla → fallback a `TileColors.getColor(tileId)`
   - Si es sólido → dibuja borde de depuración (gris oscuro, 0.5px)

Caché de sprite sheets:
```java
private Image getSpriteSheet(String imagePath) {
    if (spriteSheetCache.containsKey(imagePath)) return cached;
    Image img = new Image(getClass().getClassLoader().getResourceAsStream(imagePath));
    if (!img.isError()) spriteSheetCache.put(imagePath, img);
}
```

---

## TileColors — Paleta de colores fallback

```java
// GIDs predefinidos:
0  → TRANSPARENT
1  → rgb(33, 38, 45)   // floor
2  → rgb(48, 54, 61)   // wall
3  → rgb(30, 35, 42)   // crate
4  → rgb(28, 33, 40)
5  → rgb(45, 51, 59)
6  → rgb(22, 27, 34)
7  → rgb(38, 44, 52)
8  → rgba(88, 166, 255, 0.3)  // water/special
9  → rgba(46, 160, 67, 0.3)   // grass/special
10 → rgba(210, 153, 34, 0.3)  // sand/special

// Si el GID no está en la paleta → color procedural determinista:
// hash = tileId * 0x9E3779B1 (golden ratio)
// R = (hash & 0xFF) % 80 + 20, G = ((hash >> 8) & 0xFF) % 80 + 20, etc.
```

Para modificar la paleta:
```java
TileColors.setPalette(Map.of(1, Color.rgb(50, 50, 50), 2, Color.rgb(80, 40, 40)));
TileColors.resetPalette(); // volver a defaults
```

---

## TiledMapLoader — Carga y extracción de obstáculos

`TiledMapLoader.loadFromTmj(String resourcePath)` → `GameMap`

1. Parsea el JSON con Gson (JsonParser estándar, NO JsonUtil)
2. Construye `TileMap` con tilesets y capas
3. **Horizontal run merging** sobre la capa "walls":
   - Recorre fila por fila, columna por columna
   - Si encuentra un tile sólido → extiende el "run" hacia la derecha mientras haya tiles sólidos consecutivos
   - Genera un `Obstacle(ox, oy, ow, oh)` que cubre todo el run horizontal
   - Marca tiles como visitados para no duplicar
4. Construye y retorna `GameMap(id, name, pixelWidth, pixelHeight, obstacles, tileMap)`

Ejemplo — 5 tiles de pared consecutivos → 1 Obstacle de 160×32 px en lugar de 5 Obstacles de 32×32.

---

## MapDataMessage — Protocolo de envío

```java
// Server: GameInstance.start()
MapDataMessage mapMsg = new MapDataMessage(gameMap.getMapId(), gameMap.getTileMap());
messageSender.accept(JsonUtil.toJson(mapMsg));

// Client: GameClient.handleMessage()
case MAP_DATA -> {
    MapDataMessage msg = JsonUtil.parseMessage(payload, MapDataMessage.class);
    state.setCachedTileMap(msg.getTileMap());
}

// Renderer: en render()
if (cachedTileMap != null && tileRenderer != null) {
    tileRenderer.render(gc, cachedTileMap, canvasWidth, canvasHeight);
} else {
    drawObstacles(gc, gameState); // fallback legacy
}
```

---

## Crear un nuevo mapa TMJ

### 1. Crear el archivo .tmj
```bash
# Crear map_05.tmj en server/src/main/resources/maps/
```

### 2. Estructura mínima
```json
{
  "id": "map_05", "name": "Arena", "width": 60, "height": 60,
  "tilewidth": 32, "tileheight": 32, "infinite": false,
  "tilesets": [{ "firstgid": 1, "name": "warehouse", "tilewidth": 32, "tileheight": 32,
    "tilecount": 10, "columns": 5, "image": "maps/warehouse.png",
    "imagewidth": 160, "imageheight": 64,
    "tiles": [
      {"id": 0, "type": "floor"},
      {"id": 1, "type": "wall", "properties": [{"name": "solid", "type": "bool", "value": true}]}
    ]
  }],
  "layers": [
    {"id": 1, "name": "ground", "type": "tilelayer", "width": 60, "height": 60,
     "visible": true, "opacity": 1, "data": [/* 3600 × GID 1 */]},
    {"id": 2, "name": "walls", "type": "tilelayer", "width": 60, "height": 60,
     "visible": true, "opacity": 1, "data": [/* 3600 × GID 0, con GID 2 donde haya pared */]}
  ]
}
```

### 3. Registrar en MapManager (si se añade más allá de los 4 por defecto)
```java
// MapManager.loadDefaults() itera map_01 a map_04.
// Para mapas adicionales: MapManager.loadMap("map_05")
```

### 4. Verificar dimensiones
Ancho en píxeles del mapa = `width * 32`. El servidor usa `GameMap.getWidth()` para límites.
Asegurar que `ServerConfig.WORLD_WIDTH` / `WORLD_HEIGHT` o la configuración del mapa coincidan.

---

## Convertir mapa JSON legacy → TMJ

```bash
python3 tools/convert_map_to_tmj.py
# Convierte automáticamente map_02.json, map_03.json, map_04.json en server/src/main/resources/maps/
# map_01.tmj ya existe, el script salta el 01
```

Qué hace el script:
1. Lee `map_XX.json` con obstáculos rectangulares (`{x, y, w, h}`)
2. Convierte cada obstáculo a una región de tiles GID 2 en la cuadrícula
3. El suelo (GID 1) cubre todo el mapa
4. Genera el `.tmj` con capas "ground" + "walls"
5. Las dimensiones se redondean al múltiplo de 32 más cercano

Fórmula de conversión:
```python
col_start = max(0, floor(obs.x / 32))
col_end   = min(map_cols - 1, ceil((obs.x + obs.w) / 32) - 1)
row_start = max(0, floor(obs.y / 32))
row_end   = min(map_rows - 1, ceil((obs.y + obs.h) / 32) - 1)
```

---

## Añadir una nueva textura de tile

### 1. Añadir función de dibujo en `tools/generate_tileset.py`
```python
def draw_water(draw, x, y):
    """GID 4 — Tile de agua con ondas."""
    draw.rectangle([x, y, x+31, y+31], fill=(30, 80, 160))
    for wave_y in range(8, 32, 8):
        draw.line([x, y+wave_y, x+31, y+wave_y], fill=(40, 100, 180), width=2)

TILE_DRAWERS.append(draw_water)
```

### 2. Regenerar el sprite sheet
```bash
pip install Pillow        # si no está instalado
python3 tools/generate_tileset.py
# Genera warehouse.png en el directorio actual
# Copiar a: client/src/main/resources/maps/warehouse.png
```

### 3. Actualizar el tileset en todos los .tmj
Incrementar `tilecount` y añadir entrada de tile:
```json
{"id": 3, "type": "water", "properties": [{"name": "solid", "type": "bool", "value": true}]}
```

### 4. (Opcional) Añadir color fallback en `TileColors.java`
```java
Map.entry(4, Color.rgb(30, 80, 160))
```

### 5. Usar el nuevo GID en las capas "walls" o crear capa adicional

---

## Depuración de renderizado de tiles

| Síntoma | Causa probable | Solución |
|---------|---------------|----------|
| No se ven tiles (solo fondo negro) | `cachedTileMap == null` | Verificar que el servidor envía `MapDataMessage` y el cliente lo recibe (log `MAP_DATA`) |
| Solo rectángulos de colores, sin texturas | Sprite sheet no encontrado | Verificar que `warehouse.png` existe en `client/src/main/resources/maps/` |
| Tiles desaparecen al mover cámara | Error en viewport culling | Revisar `startCol/endCol/startRow/endRow` en `TileRenderer.render()` |
| Colisiones incorrectas | Horizontal run merging no agrupa bien | Verificar `isSolidTile(tileId, tilesets)` en `TiledMapLoader` |
| Error "No TMJ for map_NN" | Archivo .tmj no existe o ruta incorrecta | `MapManager` busca en classpath: `/maps/map_XX.tmj` |
| Mapas no coinciden con sprites legacy | Se usa JSON legacy en vez de TMJ | Verificar que los archivos .tmj existen para todos los mapas (01-04) |

### F3 debug overlay
Presionar F3 en el cliente para ver:
- Tick rate del game loop
- Posición del jugador (x, y)
- Conteo de balas activas
- Información de la cámara (zoom, offset)

---

## Herramientas auxiliares

| Herramienta | Ubicación | Uso |
|-------------|-----------|-----|
| `convert_map_to_tmj.py` | `tools/` | Convierte map_XX.json → map_XX.tmj |
| `generate_tileset.py` | `tools/` | Genera sprite sheet warehouse.png con Pillow |
| `test_client.py` | `tools/` | Bot headless para probar mapas |
| `load_test.py` | `tools/` | Stress test con N bots simultáneos |

### Verificar carga de mapa TMJ
```bash
mvn clean install -DskipTests
java -jar server/target/server-1.0-SNAPSHOT.jar
# Revisar logs: "Loaded map map_01 (Warehouse) from TMJ"
# Si ves "Loading legacy map" → el .tmj no se encontró
```

---

## Añadir capas de objeto (objectgroup)

Para spawn points, pickups u otros objetos posicionados:
```json
{
  "id": 3,
  "name": "spawns",
  "type": "objectgroup",
  "visible": false,
  "objects": [
    {"id": 1, "name": "spawn_0", "x": 320, "y": 320, "width": 32, "height": 32},
    {"id": 2, "name": "spawn_1", "x": 2240, "y": 2240, "width": 32, "height": 32}
  ]
}
```

Acceso en código:
```java
List<TileObject> spawns = tileMap.getObjectGroup("spawns");
for (TileObject obj : spawns) {
    double spawnX = obj.getX();
    double spawnY = obj.getY();
}
```

---

## Convenciones y buenas prácticas

- Los mapas `.tmj` van en `server/src/main/resources/maps/`; los sprites en `client/src/main/resources/maps/`
- Nombrar mapas `map_XX.tmj` donde XX es numérico (01-04 por defecto)
- Siempre incluir capa "ground" como capa base (GID 1 en todo el mapa)
- Usar `"visible": false` para capas de metadatos (spawns, pickups)
- Documentar GIDs y sus propiedades en el tileset del .tmj
- No subir warehouse.png generado sin haber revisado el diff visual
- Las propiedades de tile se acceden por `localId = tileId - firstGid` (NO por GID absoluto)
- `TileSet.isSolid(tileId)` ya hace la resta internamente
