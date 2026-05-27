# MEMORY.md — Punto de control del proyecto

## Sesión actual: Merge tile-engine ← develop + Tile Engine Review (Mayo 26 2026)

## Estado
- **Rama**: `tile-engine` (merged con `develop`)
- **Build**: `mvn clean install -DskipTests` → BUILD SUCCESS (pendiente verificar)
- **Tests servidor**: pendiente ejecución
- **Tests UI cliente**: pendiente ejecución
- **Servidor** y **Cliente** compilan correctamente.

## Cambios aplicados

### FASE 0 — Bugfixes (mapas, login, game flow)
### FASE 1 — Colisiones jugador-obstáculo
### FASE 2 — Fin de partida + scoreboard
### FASE 3 — Reconexión básica + timeout checker
### FASE 4 — UI/UX (completada)
### Documentación + Tests UI + Refactoring MCP (completado, Mayo 26 2026)

### Tile Engine Review — 6 fases (completado, Mayo 26 2026)

#### FASE 1 (CRÍTICA): MAP_DATA — TileMap fuera de cada tick
- `MapDataMessage` (nuevo, shared): contiene `mapId` + `TileMap`
- `MessageType.MAP_DATA` + `MAP_LIST` añadidos al enum
- `GameState.tileMap` eliminado: campo, getter, setter, copia en `copy()`
- `GameInstance.start()` envía `MapDataMessage` 1 vez vía `messageSender` antes del loop
- `GameClientState` + `cachedTileMap` (getter/setter), `GameClient` lo recibe y cachea
- `Renderer` usa `cachedTileMap` en vez de `state.getTileMap()`
- Limpieza en `GAME_END`, `LOGOUT`, y al finalizar partida
- Ahorro: ~100 KB por tick por cliente (antes se enviaba en cada GAME_STATE a 30 Hz)

#### FASE 2: Arreglar MapListMessage
- `MapListMessage.java`: `super(MessageType.ROOM_LIST)` → `super(MessageType.MAP_LIST)`
- `MessageType.MAP_LIST` + case en `JsonUtil.MessageAdapter`

#### FASE 3: TileRenderer — quitar hardcode, TileColors dinámico
- `TileRenderer.isWall()` eliminado → usa `TileSet.isSolid(tileId)` con tilesets
- `TileColors` dinámico: `setPalette()` para override, colores procedurales para IDs > 10

#### FASE 4: .tmj para map_02, map_03, map_04
- Script `tools/convert_map_to_tmj.py`: convierte JSON legacy → Tiled TMJ
- `map_02.tmj` (94×94, 3008×3008 px), `map_03.tmj` (110×110, 3520×3520 px), `map_04.tmj` (125×125, 4000×4000 px)
- `MapManager.loadDefaults()` intenta TMJ primero para los 4 mapas (loop, no hardcode)

#### FASE 5: Documentación de inmutabilidad
- `TileMap.java` doc: inmutable post-carga, compartido entre GameMap y MapDataMessage

#### FASE 6: Texturizado con sprite sheet
- Script `tools/generate_tileset.py`: genera `warehouse.png` (160×64, 10 tiles de 32×32) con Pillow
- `map_01.tmj`: añadidos `image`, `imagewidth`, `imageheight` al tileset
- `TileRenderer`: carga sprite sheets via `getClassLoader().getResourceAsStream()`, usa `drawImage()` con source rect calculado (GID → fila/columna en sheet)
- Fallback a `TileColors.fillRect()` si no hay imagen cargada
- Archivos huérfanos eliminados: `client/.../maps/map_1..4.png` (512×512 sin usar)

### Merge develop → tile-engine (Mayo 26 2026)
- Conflictos resueltos en: AGENTS.md, GameClient.java, GameInstance.java
- Cambios de develop incluidos: MCP SOLID refactoring, host transfer, settings UI, etc.

## Nuevos archivos
```
shared/.../message/MapDataMessage.java        — mensaje para envío único de TileMap
server/.../maps/map_02.tmj, map_03.tmj, map_04.tmj  — mapas convertidos a Tiled
client/.../maps/warehouse.png                 — sprite sheet (160×64, 10 tiles)
tools/generate_tileset.py                     — generador procedural de sprite sheet
tools/convert_map_to_tmj.py                   — conversor JSON legacy → Tiled TMJ
```

## Archivos modificados (tile engine review)
```
shared: MessageType.java, MapListMessage.java, JsonUtil.java, GameState.java, TileMap.java
server: GameInstance.java, MapManager.java, map_01.tmj
client: GameClient.java, GameClientState.java, Renderer.java, TileRenderer.java, TileColors.java
```

## Pendiente
- Ejecutar tests para verificar build y conteo actual
- Posible soporte para más tilesets (ahora solo warehouse)
