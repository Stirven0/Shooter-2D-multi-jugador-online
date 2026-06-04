# MEMORY.md — Punto de control del proyecto

## Sesión actual: Reglas y skills OpenCode (Junio 4 2026)

## Estado
- **Rama**: `develop`
- **Build**: `mvn clean install -DskipTests` → BUILD SUCCESS
- **Tests servidor**: 57/57 pasan (0 failures, 0 errors — unitarias, excluyendo integración)
- **Tests UI cliente**: 20/20 pasan (0 failures, 0 errors — LoginScreen 9, GameOverScreen 6, TitleBarTest 5)
- **Servidor** y **Cliente** compilan y ejecutan correctamente.

## Cambios aplicados

### FASE 0 — Bugfixes (mapas, login, game flow)
### FASE 1 — Colisiones jugador-obstáculo
### FASE 2 — Fin de partida + scoreboard
### FASE 3 — Reconexión básica + timeout checker

### FASE 4 — UI/UX (completada)
- Obstacles en GameState, renderizado en cliente
- Cámara con límites de mapa, crosshair
- Scoreboard en HUD (K/D, ordenado por kills)
- Pantallas: Login rediseñado, Lobby con sala propia + salas disponibles
- Barrra de título personalizada (TitleBar)
- Overlay de pausa, ayuda, ajustes
- Debug overlay (F3): FPS, tick, hitboxes
- Ajustes: pantalla completa, volumen general/efectos/música
- Idle kick: 30s → cuenta regresiva 10s → expulsión
- Fullscreen: canvas + cámara se redimensionan
- Arreglado ghost abandonar partida (markPlayerDisconnected en LEAVE_ROOM)
- Race condition: disconnect/reconnect encolados en ConcurrentLinkedQueue

### Documentación (completada)
- Javadoc en español en 15 clases principales
- README.md general del proyecto
- AGENTS.md actualizado
- .gitattributes, .env.example creados
- Licencia MIT

### Tests UI (nuevos)
- LoginScreenTest (9 tests): título, campos, toggle modo registro, ayuda, error
- GameOverScreenTest (6 tests): winner, scoreboard, draw, botón volver
- TitleBarTest (5 tests): título, cerrar, minimizar
- Infraestructura: TestFX 4.0.18 + JUnit 5 + Mockito, headless via Xvfb

### Refactoring MCP del cliente (completado, Mayo 26 2026)
- ClientMcpServer dividido en 11 archivos aplicando SOLID y patrones de diseño
- Transport layer: McpTransport (interface), McpTcpTransport, McpStdioTransport (Strategy)
- McpToolRegistry centraliza registro de 22 tools, McpJsonRpcHandler para protocolo TCP
- McpGameContext unifica acceso a GameState (elimina 6+ patrones repetidos)
- 6 tool providers en mcp/tools/: StatusTools, UiTools, UiSyncTools, GameTools, GameControlTools, GameObservabilityTools
- Agregar una tool nueva = editar 1 archivo (OCP), sin tocar ClientMcpServer

### Tile Engine Review — 6 fases (completado, Mayo 26 2026)
#### FASE 1: MAP_DATA — TileMap fuera de cada tick
- `MapDataMessage` (nuevo): contiene `mapId` + `TileMap`, enviado 1 vez en `GameInstance.start()`
- `GameState.tileMap` eliminado del snapshot; cliente cachea en `GameClientState.cachedTileMap` y `Renderer.cachedTileMap`

#### FASE 2: Arreglar MapListMessage + limpieza
- `MapListMessage` usa `MapDataMessage` en vez de `GameMap` serializado inline

#### FASE 3: Crear mapas TMJ + TiledMapLoader
- 4 mapas `.tmj` (80×80, 100×100, 120×120, 140×140) con `tilewidth: 32`
- `TiledMapLoader` (191L): parsea .tmj, horizontal run merging para obstáculos
- `MapManager` con fallback a JSON legacy

#### FASE 4: TileRenderer + viewport culling
- `TileRenderer` (112L): renderizado con sprite sheet, viewport culling
- `TileColors` (51L): paleta de colores con hash determinista

#### FASE 5: HudRenderer + IScreen + HelpOverlay (SOLID refactor)
- `HudRenderer` (204L): HP bar, shield, armas, skills, scoreboard, debug overlay
- `IScreen` interface: `createScene(Stage)`, `getErrorMessage()`, `setError(String)`
- `HelpOverlay` (68L): componente compartido entre Login y Game

#### FASE 6: Fixes gameplay
- Tick 30Hz → 20Hz (`ServerConfig`)
- Move throttle 50ms mínimo (cliente)
- Usernames en lobby (no raw IDs) + crown para host
- Fullscreen persistente entre pantallas (F11 global)
- `style.css` para scrollbars dark

### Room host transfer + fixes (Mayo 2026)
- Room.hostId mutable con transferencia automática al salir el admin
- RoomUpdatedMessage incluye hostId para UI (corona)
- send_key movement bypassea eventos JavaFX
- Reset de game state en lobby/leaveRoom
- Guard en creación de MCP server

### Reglas y skills OpenCode (Junio 4 2026)
- `.opencode/rules/SOLID-RULES.md` — 87 líneas, SOLID con ejemplos reales del codebase
- `.opencode/rules/TILE-RULES.md` — 57 líneas, motor de tiles TMJ (rama `tile-engine`) + legacy
- `.opencode/rules/MULTIPLAYER-RULES.md` — 157 líneas, arquitectura server-authoritative completa
- `.opencode/skills/tile-engine-development.md` — guía detallada para trabajar con tiles/tilesets
- `.opencode/skills/code-quality.md` — SOLID, testing, refactoring, thread safety
- `.opencode/skills/subagent-orchestration.md` — uso de sub-agents (explore/general)
- `.opencode/skills/session-checkpoint.md` — procedimiento "terminamos por hoy" → actualiza MEMORY.md
- `gameplay-skills.md` corregido: ejemplo DASH usa `setPosition(add(multiply()))` en vez de mutación inválida
- `AGENTS.md` actualizado: python → python3, referencias a rules/skills, sub-agents, gotchas nuevos
- `opencode.json` actualizado: instructions referencia 4 archivos, nuevos comandos
- `MEMORY.md` actualizado con test counts frescos (server: 57 unitarias, client: 20)

## Ramas activas
- `develop` — principal (estable)
- `mcp` — features experimentales MCP
- `tile-engine` — motor de tiles TMJ (mergeado a develop Junio 4 2026)

## Pendiente
- Cablear ADRENALINE en MovementSystem/ShootingSystem
- Cablear reducción de daño de DamageSystem en CollisionSystem
- Posible soporte para más tilesets (ahora solo warehouse)
- Revisar FASE 5 si aplica (no planificada)
