# MEMORY.md — Punto de control del proyecto

## Sesión actual: Fixes de gameplay y UI (Mayo 26 2026)

## Estado
- **Rama**: `tile-engine`
- **Build**: `mvn clean install -DskipTests` → BUILD SUCCESS
- **Tests servidor**: pendiente ejecución
- **Tests UI cliente**: pendiente ejecución

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

#### FASE 2: Arreglar MapListMessage
- `MapListMessage.java`: `super(MessageType.ROOM_LIST)` → `super(MessageType.MAP_LIST)`

#### FASE 3: TileRenderer — quitar hardcode, TileColors dinámico
- `TileRenderer.isWall()` eliminado → usa `TileSet.isSolid(tileId)`
- `TileColors` dinámico: colores procedurales para IDs > 10

#### FASE 4: .tmj para map_02, map_03, map_04
- Script `tools/convert_map_to_tmj.py` y generados los 3 .tmj

#### FASE 5: Documentación de inmutabilidad
- `TileMap.java` doc: inmutable post-carga

#### FASE 6: Texturizado con sprite sheet
- Script `tools/generate_tileset.py` → `warehouse.png` (160×64, 10 tiles de 32×32)
- `TileRenderer` usa `drawImage()` con fallback a `TileColors`

### Merge develop → tile-engine (Mayo 26 2026)
- Conflictos resueltos en: AGENTS.md, GameClient.java, GameInstance.java
- Cambios de develop incluidos: MCP SOLID refactoring, host transfer, settings UI

### Fixes de gameplay y UI (Mayo 26 2026, sesión actual)

#### Tick rate reducido: 30 → 20 Hz
- `ServerConfig.TICK_RATE = 20` (default), derivados `TICK_DURATION_MS = 50ms`
- `ServerConfig.java` ahora lee system properties (`-DTICK_RATE=20`) con fallback a defaults
- `.env.example` documenta todas las system properties + defaults sincronizados
- Comentario de `GameLoop.java` corregido

#### Movimiento: fix de velocidad (teletransporte)
- `MovementSystem.update()` procesaba **todos** los `MOVE_INPUT` acumulados por tick
- Cada input aplicaba `speed * deltaTime` → 3 inputs/tick = 3× velocidad (antes 2× con 30Hz)
- Fix: procesa solo el **último** `MOVE_INPUT` por jugador por tick (`Set<String> processed`)

#### Throttle de MOVE_INPUT en cliente
- `GameClient.java`: solo envía `MOVE_INPUT` cada 50ms (`System.currentTimeMillis()`)
- Reduce tráfico ~60% (de 60 msg/s a 20 msg/s)

#### Lobby: nombres de usuario en vez de IDs
- `RoomUpdatedMessage` + `playerUsernames` (Map<String, String>)
- Server puebla usernames desde `ClientConnection.getUsername()`
- Lobby muestra nombres reales con 👑 para host

#### F3 debug overlay: posición corregida
- Vuelto a top-left (5, 5), rectángulo 240×210px con altura suficiente
- `TextAlignment.LEFT` explícito para evitar desborde de texto

#### Fullscreen: persistencia entre screens + F11 global
- `ScreenManager.switchScene()` preserva estado fullscreen al cambiar de screen
- F11 funciona en Login, Lobby, GameOver gracias a `addFullScreenHandler()`
- ESC de JavaFX bloqueado (`KeyCombination.NO_MATCH`) para evitar conflicto con ajustes
- Todas las pantallas usan Scene dinámico (no tamaño fijo 800×466)

#### Fullscreen: sin salto de ventana al cambiar de screen
- `switchScene()` ya no fuerza `setWidth()`/`setHeight()` en cada transición
- Tamaño inicial seteado una sola vez en `init()`

#### GameScreen: fix de overlays y controles
- Pause overlay: `setMaxHeight(360)` para evitar que se estire a todo el alto
- Settings scrollbar: estilizado con CSS (`style.css` — track transparente, thumb #30363d)
- ESC: settings overlay tiene prioridad en el handler
- Ayuda actualizada con Q, E, F, F11 documentados

#### LoginScreen: form no se estira
- `setMaxSize(320, USE_PREF_SIZE)` para que el VBox use su alto natural

### Nuevos archivos (esta sesión)
```
client/src/main/resources/style.css               — estilos para scrollbar de settings
server/src/main/resources/maps/map_02.tmj, map_03.tmj, map_04.tmj  — mapas TMJ
client/src/main/resources/maps/warehouse.png       — sprite sheet
shared/src/main/java/com/aa/shared/message/MapDataMessage.java
tools/generate_tileset.py, convert_map_to_tmj.py
```

### Archivos modificados (esta sesión)
```
ServerConfig.java, MovementSystem.java, GameLoop.java, MessageHandler.java,
GameServer.java, RoomUpdatedMessage.java, GameClient.java, TileColors.java,
Renderer.java, GameScreen.java, LobbyScreen.java, LoginScreen.java,
GameOverScreen.java, ScreenManager.java, SettingsOverlay.java, .env.example
```

## Pendiente
- Ejecutar tests para verificar conteo actual
- Posible soporte para más tilesets (ahora solo warehouse)
