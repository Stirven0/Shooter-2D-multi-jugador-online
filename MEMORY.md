# MEMORY.md — Punto de control del proyecto

## Sesión actual: FASE 4 (UI/UX) + Documentación (Mayo 14 2026)

## Estado
- **Rama**: `develop`
- **Build**: `mvn clean install -DskipTests` → BUILD SUCCESS
- **Tests servidor**: 58/58 pasan (0 failures, 0 errors)
- **Tests UI cliente**: 21/21 pasan (0 failures, 0 errors)
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
- GameOverScreenTest (7 tests): winner, scoreboard, draw, botón volver
- TitleBarTest (5 tests): título, cerrar, minimizar
- Infraestructura: TestFX 4.0.18 + JUnit 5 + Mockito, headless via Xvfb

### Refactoring MCP del cliente (completado, Mayo 26 2026)
- ClientMcpServer dividido en 11 archivos aplicando SOLID y patrones de diseño
- Transport layer: McpTransport (interface), McpTcpTransport, McpStdioTransport (Strategy)
- McpToolRegistry centraliza registro de 22 tools, McpJsonRpcHandler para protocolo TCP
- McpGameContext unifica acceso a GameState (elimina 6+ patrones repetidos)
- 6 tool providers en mcp/tools/: StatusTools, UiTools, UiSyncTools, GameTools, GameControlTools, GameObservabilityTools
- Agregar una tool nueva = editar 1 archivo (OCP), sin tocar ClientMcpServer

## Pendiente
- Revisar FASE 5 si aplica (no planificada)
