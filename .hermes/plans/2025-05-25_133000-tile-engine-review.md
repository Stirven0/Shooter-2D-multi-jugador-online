# Plan: Revisión del Tile Engine

**Rama:** `tile-engine` (worktree `/home/stirven/shooter-tile`)
**Fecha:** 2025-05-25
**Objetivo:** Auditar la implementación actual del tile engine, identificar bugs y gaps, y proponer mejoras.

---

## 1. Diagnóstico: qué hay hoy

### 1.1 Arquitectura del tile engine

La rama `tile-engine` añade 4 capas al juego:

| Capa | Archivos nuevos | Rol |
|------|----------------|-----|
| **Modelos** (shared) | `TileMap`, `TileLayer`, `TileSet`, `TileObject` | Estructuras de datos para mapas Tiled |
| **Server map** | `GameMap.tileMap`, `TiledMapLoader`, `MapLoader` (legacy) | Cargar `.tmj` → `TileMap` + `Obstacle` |
| **GameState** | +`TileMap tileMap` | Adjuntar el mapa al estado que se envía al cliente |
| **Client render** | `TileRenderer`, `TileColors`, `Renderer` modificado | Dibujar tiles en vez de obstáculos genéricos |

### 1.2 Flujo actual

```
Server:
  MapManager.loadDefaults()
    → TiledMapLoader.loadFromTmj("/maps/map_01.tmj")   ← solo map_01 tiene .tmj
      → parseTileSet() → TileSet (solid properties)
      → parseLayer() → TileLayer (int[][] data)
      → genera Obstacle[] desde capa "walls" uniendo tiles sólidos horizontales
    → MapLoader.loadFromJson() para map_02, map_03, map_04  ← legacy

  GameInstance(..., GameMap map)
    → state.setObstacles(map.obstacles())   ← colisiones server-side
    → state.setTileMap(map.getTileMap())    ← render client-side

Client (Renderer.render):
  if (state.getTileMap() != null)
    tileRenderer.render(gc, state.getTileMap(), cw, ch)   ← colores sólidos
  else
    drawObstacles(gc, state.getObstacles())               ← fallback legacy
```

### 1.3 Lo que funciona

- `TiledMapLoader` parsea correctamente el formato `.tmj` (Tiled Map JSON)
- Convierte tiles sólidos de la capa `walls` en `Obstacle[]` (merge horizontal)
- `TileRenderer` solo dibuja tiles visibles en el viewport (culling)
- `Renderer` tiene fallback: si no hay TileMap, dibuja obstáculos legacy
- `GameState.copy()` copia `tileMap` (aunque por referencia, ver issue abajo)

---

## 2. Problemas encontrados

### 🔴 CRÍTICO: TileMap se envía en cada GameState (30 Hz)

**El problema:** `GameStateMessage` se envía 30 veces por segundo a cada cliente. Ahora incluye `TileMap` con capas de 80×80 = 6400 enteros cada una. El `map_01.tmj` tiene 2 capas tilelayer (ground + walls) = ~12,800 enteros serializados en JSON cada tick.

**Impacto:** ~1 MB/s por cliente solo en datos del mapa. Con 4 jugadores = ~4 MB/s solo de mapa.

**Solución propuesta:**
- El `TileMap` NO debe ir en `GameState` (que cambia cada tick)
- Debe enviarse una sola vez en un mensaje dedicado (`MAP_DATA`) al iniciar la partida
- El cliente lo cachea localmente y el `Renderer` lo usa desde ahí
- `GameState.tileMap` se elimina (o se marca `transient` para que Gson no lo serialice)

### 🟡 MEDIO: `MapListMessage` usa `MessageType.ROOM_LIST`

```java
public MapListMessage() {
    super(MessageType.ROOM_LIST); // ← debería ser MAP_LIST
}
```

No hay `MAP_LIST` en `MessageType.java`. Esto causa que el servidor intente rutear `MAP_LIST` como si fuera `ROOM_LIST`.

**Solución:** Añadir `MAP_LIST` al enum `MessageType` y cablearlo en `JsonUtil.MessageAdapter`.

### 🟡 MEDIO: `TileRenderer.isWall()` hardcodea tile IDs

```java
private boolean isWall(int tileId) {
    return tileId == 2 || tileId == 3;  // ← magic numbers
}
```

El `TileSet` ya tiene `isSolid(tileId)` que usa las propiedades del tileset. El renderer debería usar eso para saber si un tile es pared.

### 🟡 MEDIO: Solo `map_01` tiene `.tmj`

Los mapas 2, 3, 4 siguen usando JSON legacy con obstáculos manuales. No tienen tile data, así que el cliente los renderiza con el fallback `drawObstacles()`.

**Solución:** Crear `.tmj` para map_02, map_03, map_04. O al menos generar `TileMap` proceduralmente desde los obstáculos existentes.

### 🟡 MEDIO: `TileMap` no se copia profundamente en `GameState.copy()`

```java
copy.tileMap = this.tileMap;  // ← copia por referencia, no deep copy
```

Si dos threads modifican el mismo TileMap (aunque hoy no ocurre), sería un bug. Para ser thread-safe, debería ser deep copy o al menos documentar que TileMap es inmutable después de carga.

### 🟢 BAJO: PNGs de mapas en `client/src/main/resources/maps/` sin usar

Los archivos `map_1.png` a `map_4.png` no son referenciados por ningún código. El `TileRenderer` solo usa colores sólidos de `TileColors`. Las imágenes probablemente eran para un sprite-based renderer que nunca se implementó.

**Solución:** Eliminarlos o implementar texturizado real usando `Tileset.image`.

### 🟢 BAJO: `TileColors` tiene paleta fija de 10 colores

Solo soporta tile IDs 1-10. Si un `.tmj` usa IDs > 10, se muestran con color default gris.

**Solución:** Usar `Tileset.tileProperties` para leer colores por tile, o cargar una paleta desde el `.tmj`.

---

## 3. Plan de correcciones (ordenado por prioridad)

### Fase 1: Arreglar el envío de TileMap (CRÍTICO)

| # | Archivo | Cambio |
|---|---------|--------|
| 1.1 | `shared/.../message/MessageType.java` | Añadir `MAP_DATA` al enum |
| 1.2 | `shared/.../message/` | Crear `MapDataMessage.java` (contiene solo `TileMap tileMap` + `String mapId`) |
| 1.3 | `shared/.../util/JsonUtil.java` | Añadir `case MAP_DATA -> MapDataMessage.class` en `MessageAdapter` |
| 1.4 | `server/.../game/GameInstance.java` | Enviar `MapDataMessage` UNA vez al iniciar (antes del primer `GAME_STATE`). Quitar `state.setTileMap()`. |
| 1.5 | `shared/.../state/GameState.java` | Eliminar campo `tileMap` (o marcarlo `transient`). Ya no se serializa en cada tick. |
| 1.6 | `client/.../game/GameClient.java` | Añadir `onMapDataReceived(TileMap)` en `handleMessage()`. Cachear TileMap en `GameClientState`. |
| 1.7 | `client/.../game/GameClientState.java` | Añadir `private TileMap cachedTileMap` con getter/setter |
| 1.8 | `client/.../render/Renderer.java` | Usar `clientState.getCachedTileMap()` en vez de `state.getTileMap()` |

**Validación:** El primer `GAME_STATE` ya no incluye los 12K enteros del mapa. Solo se envían una vez en `MAP_DATA`.

### Fase 2: Arreglar `MapListMessage` (MEDIO)

| # | Archivo | Cambio |
|---|---------|--------|
| 2.1 | `shared/.../message/MessageType.java` | Añadir `MAP_LIST` |
| 2.2 | `shared/.../message/MapListMessage.java` | Cambiar `super(MessageType.ROOM_LIST)` → `super(MessageType.MAP_LIST)`. Añadir getters/setters completos. |
| 2.3 | `shared/.../util/JsonUtil.java` | `case MAP_LIST -> MapListMessage.class` |

### Fase 3: Mejoras al renderer (MEDIO)

| # | Archivo | Cambio |
|---|---------|--------|
| 3.1 | `client/.../render/TileRenderer.java` | Usar `TileSet.isSolid(tileId)` en vez de `isWall()` hardcodeado. Pasar `tileMap.getTilesets()` al constructor. |
| 3.2 | `client/.../render/TileColors.java` | Hacer dinámico: aceptar `Map<Integer, Color>` desde el tileset o generar colores proceduralmente para IDs > 10 |
| 3.3 | `client/.../render/TileRenderer.java` | Eliminar método `isWall()` |

### Fase 4: TMJ para map_02, map_03, map_04 (MEDIO)

| # | Archivo | Cambio |
|---|---------|--------|
| 4.1 | `server/.../resources/maps/map_02.tmj` | Crear desde `map_02.json` (convertir obstáculos a tiles) |
| 4.2 | `server/.../resources/maps/map_03.tmj` | Ídem |
| 4.3 | `server/.../resources/maps/map_04.tmj` | Ídem |
| 4.4 | `server/.../map/MapManager.java` | Cambiar `loadDefaults()` para intentar `.tmj` primero en los 4 mapas |

### Fase 5: Limpieza (BAJO)

| # | Archivo | Cambio |
|---|---------|--------|
| 5.1 | `shared/.../state/GameState.java` | Documentar que `tileMap` (si se mantiene) es inmutable post-carga |

### Fase 6: Texturizado con sprite sheets (MEJORA VISUAL)

**Objetivo:** Reemplazar los colores sólidos de `TileColors` por texturas reales
cargadas desde un tileset PNG (sprite sheet). Cada tile se recorta de la imagen
según su GID y se dibuja en vez de `fillRect()`.

**Estado actual:** `map_1.png` a `map_4.png` existen en el cliente (512×512 RGBA)
pero no se usan. El `.tmj` no tiene campo `image` en el tileset. `TileRenderer`
solo hace `fillRect()` con colores de `TileColors`.

#### 6.0 Generación del sprite sheet (script Python)

Antes de modificar el código, se necesita crear el sprite sheet `warehouse.png`.
Se usará un script Python con Pillow que genere patrones procedurales para
los 10 tiles del tileset warehouse.

**Layout:** 5 columnas × 2 filas = 10 tiles de 32×32 = imagen final 160×64 píxeles.

**Script:** `tools/generate_tileset.py`

```
Uso: python3 tools/generate_tileset.py [--output client/src/main/resources/maps/warehouse.png]
```

**Tiles a generar (GID 1-10):**

| GID | Nombre | Tipo | Patrón procedural |
|-----|--------|------|-------------------|
| 1 | floor | suelo | Ruido suave gris oscuro (#21262d), efecto tablones horizontales |
| 2 | wall_top | pared solida | Ladrillos grises (#30363d) con juntas más oscuras (#1c2128) |
| 3 | crate | solida | Caja marrón (#8b7355) con borde más oscuro y líneas cruzadas |
| 4 | floor_alt | suelo | Variante de floor con leve ruido azulado (#252a33) |
| 5 | floor_dark | suelo | Variante más oscura (#1a1f27) con patrón de rejilla tenue |
| 6 | barrel | decorativa | Barril marrón rojizo (#6b4c3b) con aros metálicos horizontales |
| 7 | pipe | decorativa | Tubería gris metálica (#484f58) vertical con sombreado tubular |
| 8 | vent | suelo | Rejilla metálica: líneas cruzadas sobre fondo oscuro, con borde |
| 9 | hazard | suelo | Franjas diagonales amarillo-negro (#d29922 + #1c2128) — zona peligro |
| 10 | computer | decorativa | Panel/monitor oscuro con pequeños LEDs (verde #2ea043, rojo #f85149) |

**Dependencias:** Python 3 + Pillow (`pip install pillow`)

**El script debe:**
1. Crear imagen RGBA 160×64
2. Para cada tile, calcular posición `(col*32, row*32)` y pintar el patrón correspondiente
3. Guardar como PNG en `client/src/main/resources/maps/warehouse.png`
4. Imprimir confirmación con path y tamaño

**Validación:** Abrir `warehouse.png` → se ven 10 tiles distintos en grilla 5×2, cada uno de 32×32.

#### 6.1-6.6 Arquitectura de renderizado

```
TiledMapLoader (server)
  └─ parseTileSet() → TileSet.image = "maps/warehouse.png"

MapDataMessage (shared, Fase 1)
  └─ TileSet { image, imageWidth, imageHeight, columns, tileWidth, tileHeight, firstGid }

TileRenderer (client)
  ├─ carga Image desde TileSet.image
  ├─ calcula source rect: (gid - firstGid) % columns * tileW, (gid - firstGid) / columns * tileH
  └─ gc.drawImage(spriteSheet, srcX, srcY, tw, th, dstX, dstY, tw, th)
```

| # | Archivo | Cambio |
|---|---------|--------|
| 6.1 | `server/.../resources/maps/map_01.tmj` | Añadir `"image": "warehouse.png"` al tileset con `imagewidth`, `imageheight`, `columns` |
| 6.2 | `shared/.../model/TileSet.java` | Ya tiene campos `image`, `imageWidth`, `imageHeight`, `columns` — verificar que Gson los serializa/deserializa |
| 6.3 | `client/.../resources/maps/warehouse.png` | **NUEVO** — sprite sheet para el tileset warehouse (reemplaza `map_1.png`) |
| 6.4 | `client/.../render/TileRenderer.java` | Constructor recibe `List<TileSet>` + `TileMap`. Carga `Image` por tileset. `render()` usa `drawImage()` en vez de `fillRect()`. |
| 6.5 | `client/.../render/TileColors.java` | Mantener como fallback cuando no hay imagen cargada (tile sin textura → color sólido) |
| 6.6 | `client/.../resources/maps/map_*.png` | Eliminar los 4 PNGs huérfanos actuales (512×512 sin usar) |

**Sprite sheet layout esperado:**

```
warehouse.png (ej: 160×64 para 10 tiles de 32×32 en 5 columnas)
┌────┬────┬────┬────┬────┐
│ 0  │ 1  │ 2  │ 3  │ 4  │  ← fila 0: floor, wall, crate, ...
├────┼────┼────┼────┼────┤
│ 5  │ 6  │ 7  │ 8  │ 9  │  ← fila 1: ...
└────┴────┴────┴────┴────┘
```

**Mapping GID → textura:**
```
gid = data[row][col]
localId = gid - tileset.firstGid
srcCol = localId % tileset.columns
srcRow = localId / tileset.columns
srcX = srcCol * tileset.tileWidth
srcY = srcRow * tileset.tileHeight
```

**Pitfalls:**
- El `Image` de JavaFX debe cargarse una vez (cache por tileset), no por cada tile
- `drawImage()` es más caro que `fillRect()` — mantener el culling de viewport
- Si `gid == 0` (tile vacío), no dibujar nada
- Si no hay imagen cargada, degradar a `TileColors.getColor(tileId)` + `fillRect()`

---

## 4. Archivos a modificar (resumen)

| Archivo | Fase | Tipo de cambio |
|---------|------|---------------|
| `shared/.../message/MessageType.java` | 1, 2 | +`MAP_DATA`, +`MAP_LIST` |
| `shared/.../message/MapDataMessage.java` | 1 | **NUEVO** |
| `shared/.../message/MapListMessage.java` | 2 | Fix super() + getters/setters |
| `shared/.../util/JsonUtil.java` | 1, 2 | +2 cases en MessageAdapter |
| `shared/.../state/GameState.java` | 1 | Quitar tileMap o hacer transient |
| `server/.../game/GameInstance.java` | 1 | Enviar MapDataMessage, no setTileMap |
| `server/.../game/map/MapManager.java` | 4 | Intentar .tmj en los 4 mapas |
| `client/.../game/GameClient.java` | 1 | Handler MAP_DATA |
| `client/.../game/GameClientState.java` | 1 | +cachedTileMap |
| `client/.../render/Renderer.java` | 1, 3 | Usar cachedTileMap |
| `client/.../render/TileRenderer.java` | 3 | Usar TileSet.isSolid() |
| `client/.../render/TileColors.java` | 3, 6 | Hacer dinámico, mantener como fallback |
| `server/.../resources/maps/map_0{2,3,4}.tmj` | 4 | **NUEVOS** |
| `server/.../resources/maps/map_01.tmj` | 6 | +image/columns al tileset |
| `client/.../resources/maps/warehouse.png` | 6 | **NUEVO** — sprite sheet generado por script |
| `tools/generate_tileset.py` | 6 | **NUEVO** — script Python que genera el sprite sheet |
| `client/.../resources/maps/map_*.png` | 6 | **ELIMINAR** — PNGs huérfanos |

---

## 5. Riesgos

| Riesgo | Mitigación |
|--------|-----------|
| El cambio de `MAP_DATA` rompe compatibilidad con clientes viejos | El mensaje nuevo es aditivo. Cliente viejo ignora `MAP_DATA`, usa fallback de obstáculos. |
| `int[][]` en `TileLayer` puede ser grande para serializar | Se envía 1 vez por partida, no 30/segundo. 80×80×2 capas = 12,800 ints ≈ ~100 KB en JSON. Aceptable. |
| `TiledMapLoader` genera Obstacle[] desde walls — si se cambia el formato de la capa walls, se rompe | Documentar que la capa debe llamarse "walls" y ser tilelayer. |
| Crear `.tmj` para map_02-04 puede cambiar el gameplay (los obstáculos legacy vs tiles no son 1:1) | Hacer conversion tool o script para generar `.tmj` desde `.json`. |
| `drawImage()` es más lento que `fillRect()` | Culling ya implementado. Agregar cache de Image por tileset. Si FPS baja, ofrecer toggle texturas/colores en settings. |
| Sprite sheet grande ocupa memoria GPU | Cada tileset se carga 1 vez. 160×64 en VRAM es despreciable. |

---

## 6. Verificación

- [ ] `mvn test -pl server` — los 58 tests pasan (las colisiones no cambiaron, solo se añadió TileMap)
- [ ] `mvn javafx:run -pl client` — el mapa se ve con texturas reales en vez de colores
- [ ] Inspeccionar tráfico WebSocket: `MAP_DATA` se envía exactamente 1 vez al iniciar partida
- [ ] `GAME_STATE` ya no incluye el array `tileMap` (ahorro de ~100 KB/msg)
- [ ] Texturizado: zoom in/out, movimiento de cámara — los tiles se recortan correctamente del sprite sheet
