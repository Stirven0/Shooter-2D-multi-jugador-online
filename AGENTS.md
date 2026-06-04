# AGENTS.md — Multiplayer Shooter Game

> Reglas detalladas en `.opencode/rules/`: SOLID-RULES.md, TILE-RULES.md, MULTIPLAYER-RULES.md
> Skills de desarrollo en `.opencode/skills/`: build-test, gameplay-skills, tile-engine-development, code-quality, game-client-mcp, mcp-bridge, ai-agent, subagent-orchestration, session-checkpoint

## Branch workflow
- Todo el trabajo en `develop`. Nunca commitear a `main`.
- Rama `mcp` para características experimentales de IA/MCP.
- Rama `tile-engine` para el motor de tiles TMJ (ya mergeada a `develop`).

## Quick start
```bash
mvn clean install -DskipTests              # build & install (4 módulos: shared, server, client, mcp-bridge)
java -jar server/target/server-1.0-SNAPSHOT.jar   # servidor :8080
mvn javafx:run -pl client                  # lanzar cliente
mvn javafx:run -pl client -Djavafx.args="--mcp"  # cliente en modo MCP
java -jar mcp-bridge/target/mcp-bridge-1.0-SNAPSHOT.jar --username ai_player  # MCP bridge
python3 tools/test_client.py                # bot headless (pip install websocket-client)
python3 tools/load_test.py 5                # stress test (5 bots)
```

## OpenCode shortcuts
Definidos en `opencode.json`:
```bash
build          → mvn clean install -DskipTests
test-server    → mvn test -pl server -Dtest="!*IntegrationTest"
test-client    → mvn test -pl client
run-server     → java -jar server/target/server-1.0-SNAPSHOT.jar
run-client     → mvn javafx:run -pl client
```

## Test commands
```bash
mvn test -pl server                         # server tests
mvn test -pl server -Dtest="!*IntegrationTest"  # solo unitarias
mvn test -pl server -Dtest="com.aa.server.game.system.*"  # paquete específico
Xvfb :99 -ac -screen 0 1280x720x24 &       # display virtual para UI tests (Linux)
DISPLAY=:99 mvn test -pl client             # UI tests (requiere Xvfb)
mvn test -pl server,client                  # ambos módulos
```
Nota: Los count de tests cambian — ejecutar los comandos para ver cifras actuales.
`prism.order=sw` en argLine del surefire-client permite headless sin GPU.

## Sub-agent usage
- **explore**: Buscar archivos por patrón, código por keywords, responder preguntas de arquitectura. Especificar `thoroughness`: quick/medium/very thorough. Usar para: encontrar definiciones de clases, rastrear usos de métodos.
- **general**: Tareas multi-paso complejas (escribir código, investigar, operaciones de archivos). Usar para: crear archivos, debuggear, analizar arquitectura.
- **Paralelismo**: Lanzar múltiples sub-agents en paralelo cuando las tareas son independientes (ej: explorar game engine + test infrastructure + tile engine simultáneamente).
- **Pasar contexto explícito**: Los sub-agents pierden el contexto de la conversación. Incluir paths de archivos, nombres de clases, y detalles verificados en el prompt.
- **NO usar sub-agents para**: lecturas simples de archivos (Read tool), grep simples (Grep tool), definiciones de una sola clase (Grep directo).
- **Verificar siempre**: Correr tests/lint después de cambios de código.

## Supplementary docs
- `MEMORY.md` — checkpoint de sesión (actualizar con "terminamos por hoy")
- `ENV-REVIEW.md` — informe de viabilidad del entorno (build, test, load test)
- `.opencode/skills/` — 9 skills de desarrollo
- `.opencode/rules/` — 3 archivos de reglas detalladas (SOLID, tiles, multiplayer)

## Loose Python scripts in root
`fight.py`, `fight2.py`, `fight3.py`, `fight_fast.py`, `duel.py`, `direct_test.py` son scripts de prueba/bot. No son parte de la aplicación. Los scripts oficiales están en `tools/`.

## Architecture rules
- **Server-authoritative**: Client NEVER sends positions. Only normalized inputs (-1..1).
- **Thread safety**: Only GameLoop mutates GameState. Network thread enqueues inputs via `ConcurrentLinkedQueue` in `GameInstance`.
- **JSON**: Use `JsonUtil.toJson()` / `parseMessage()` exclusively. Never manual parse except for lobby messages in MessageHandler.
- **Broadcast**: Always `state.copy()` before serializing. Never send mutable GameState reference.
- **GameLoop**: Fixed 30Hz timestep (`ServerConfig.TICK_RATE`). Drift resets `nextTick` to avoid death spiral.
- **Weapon System**: 5 tipos (PISTOL/SHOTGUN/RIFLE/SNIPER/SMG), 2 slots (primaria/secundaria), Q para swap. Stats embebidos en enum `WeaponType`. Pickups spawn aleatorios en mapa (PISTOL excluido de spawn).
- **Power-ups**: 7 tipos (Speed/Damage+/FireRate/Shield/Health, Slow/Debilidad como debuffs). Temporales (15s) con respawn. Se recogen automáticamente al colisionar.
- **Upgrade System**: 5 niveles por kills acumulados en partida (2/5/9/14/20). Mejoras pasivas: daño, cadencia, velocidad, HP max, reducción daño. Persiste al morir.
- **Player Skills (Activas)**: 6 tipos (DASH/SHIELD_BURST/HEAL/ADRENALINE/EMP/STEALTH), 2 slots por jugador, teclas E y F, cooldowns en HUD. Skills aleatorias al spawn.
- **DB persistence**: Tabla `player_stats` (total_kills, total_deaths, total_wins, total_games, upgrade_points). Stats persistidos vía `DatabaseManager.savePlayerStats()` al terminar partida. SQLite por defecto (`shooter.db`), HikariCP connection pool.
- **Room admin (host)**: El creador de la sala es admin. Si se desconecta, el rol se transfiere al siguiente jugador. En UI se muestra 👑 junto al nombre del host. Solo el admin puede iniciar la partida (`handleStartGame` verifica `isHost()`).
- **Client reconnect**: `GameClient.connect()` siempre crea un `NetworkClient` nuevo (Java-WebSocket no reusa objetos). `logout()` limpia estado y lleva a login.

## MCP Integration
- **mcp-bridge module**: Standalone MCP server (stdio transport) que se conecta como bot al game server via WebSocket. 7 tools: `get_state`, `get_map`, `move`, `shoot`, `swap_weapon`, `use_skill`, `get_inventory`. Incluye AI loop autónomo: persigue enemigos y dispara.
- **Client MCP mode**: Flag `--mcp` en el cliente JavaFX. Expone **22 tools** en 5 categorías:
  - *Status* (2): `get_screen_info`, `get_last_error`
  - *UI* (8): `ui_login`, `ui_create_room`, `ui_join_room`, `ui_start_game`, `ui_leave_room`, `ui_request_room_list`, `ui_back_to_lobby`, `ui_logout`
  - *UI Sync* (1): `wait_for_screen`
  - *Game Observability* (7): `screenshot`, `get_hud_info`, `get_player_position`, `get_game_state`, `get_other_players`, `get_bullets`, `get_map_pickups`
  - *Game Control* (4): `send_key`, `aim_at`, `mouse_move`, `aim_direction`
- **Client MCP architecture (refactored)**: `ClientMcpServer` delega en 11 clases:
  - `McpTransport` interface + `McpTcpTransport` (TCP) / `McpStdioTransport` (stdio) implementations
  - `McpToolRegistry` — registro centralizado de tools (nombre, descripcion, handler)
  - `McpJsonRpcHandler` — protocolo JSON-RPC 2.0 para transporte TCP
  - `McpGameContext` — acceso seguro a GameState, jugadores, pickups, info de pantalla
  - 6 tool providers en `mcp/tools/`: `StatusTools`, `UiTools`, `UiSyncTools`, `GameTools`, `GameObservabilityTools`, `GameControlTools`
- **Screen validation**: Todos los tools UI validan que el cliente esté en la pantalla correcta antes de ejecutarse (ej. `ui_create_room` solo funciona desde lobby).
- **TCP transport**: `--mcp` solo ya activa TCP en `localhost:4567`. Usar `--hostmcp` / `--portmcp` para override. JSON-RPC 2.0 con delimitador newline.
- **MCP SDK**: `io.modelcontextprotocol.sdk:mcp-bom:0.17.2` (BOM import en root, usar `mcp-core`/`mcp-json`/`mcp-json-jackson2` directamente). Requiere `jackson-databind` para `JacksonMcpJsonMapper`.
- **opencode → cliente**: `opencode.json` tiene un MCP server `game-client` que conecta via `nc` al TCP del cliente. Requiere cliente corriendo con `--mcp` (TCP activo por defecto en puerto 4567).

## Client CLI flags
Todas las flags se pasan con `-Djavafx.args="..."` en `mvn javafx:run` (NO con `exec.args`):

| Flag | Default | Descripción |
|------|---------|-------------|
| `--mcp` | — | Activa el servidor MCP del cliente (TCP en localhost:4567) |
| `--host <ip>` | `localhost` | IP del servidor WebSocket del juego |
| `--port <puerto>` | `8080` | Puerto del servidor WebSocket |
| `--hostmcp <ip>` | `localhost` | IP para exponer MCP vía TCP (requiere `--mcp`) |
| `--portmcp <puerto>` | `4567` | Puerto para MCP TCP (requiere `--mcp`, override de puerto) |

Login, registro, creación/unión a salas e inicio de partida se realizan exclusivamente a través de las tools MCP del cliente (`ui_login`, `ui_create_room`, `ui_join_room`, `ui_start_game`, etc.).

Ejemplo:
```bash
mvn javafx:run -pl client -Djavafx.args="--mcp --host 10.0.0.5 --portmcp 9000"
```

## Gotchas (important quirks)
- **Gson recursion split**: Two Gson instances in `JsonUtil` — `gsonPlain` (no `MessageAdapter`, para tipos concretos como `MoveMessage`) and `gson` (con `MessageAdapter`, para `parseMessage()` y `toJson()`). Usar el correcto.
- **CREATE_ROOM maps to `LoginMessage.class`** en `MessageAdapter.getTargetClass()` — hack intencional. CREATE_ROOM se parsea manualmente desde `JsonObject` en `MessageHandler`. No "arreglar".
- **JUnit 3.8.1 en root `dependencyManagement`** es código muerto. Testing real usa JUnit 5 (Jupiter) desde server/pom.xml y client/pom.xml.
- **Reconnection (server-only)**: Server valida token + encola reactivación en `GameInstance`. Cliente nunca inicia reconexión — al desconectar resetea a lobby.
- **PING/PONG**: Server tracks nothing. Client ignores PING, server ignores PONG. No latency tracking.
- **Byte Buddy + JDK 25**: Requiere `-Dnet.bytebuddy.experimental=true`. Ya está en `argLine` del surefire plugin en ambos módulos.
- **ServerConfig hardcodes values** (TICK_RATE=30, PLAYER_SPEED=200, etc.): No carga `.env` pese a existir `.env.example`. Editar `ServerConfig.java` para cambiar. Atención: `.env.example` tiene `TICK_RATE=20` — no coincide con el hardcode de `30`.
- **GameLoop.java** comentario dice "20 Hz" pero realmente usa `ServerConfig.TICK_DURATION_MS` (~33ms = 30Hz). No confiar en el comentario.
- **No CI/CD**: No `.github`, no Actions, no pre-commit hooks.
- **Unused `MessageType` values**: `ROTATE_INPUT`, `DELTA_STATE`, `ENTITY_SPAWN`, `ENTITY_DESTROY`, `PLAYER_DEATH` definidos en enum pero sin cablear en `MessageAdapter` ni handlers. `USE_ABILITY` renombrado a `USE_SKILL`.
- **MCP AsyncServer no tiene `start()`**: `McpServer.async(transport).build()` devuelve servidor ya iniciado. Usar `server.addTool()` post-build para registrar tools.
- **`Vector2` es un `record`**: No tiene setters ni `add(double, double)`. Usar `add(Vector2)`, `multiply(double)`, y `setPosition(new Vector2(...))`.
- **WebSocketClient no reutilizable**: `GameClient.connect()` siempre crea un `new NetworkClient()`. Nunca reusar un `WebSocketClient` cerrado — `connectBlocking()` lanza `IllegalStateException`.
- **Host transfer en sala**: `Room.hostId` mutable. `RoomManager.leaveRoom()` transfiere host si el que se va es el admin y quedan jugadores. `RoomUpdatedMessage` incluye `hostId`.
- **GAME_STATE filtrado por room**: El cliente ignora `GAME_STATE` si `gameId` no coincide con `currentRoomId`. Evita entrar a partidas de otras salas.
- **System.exit(0) on close**: `Main.java` hace `System.exit(0)` al cerrar ventana para matar threads non-daemon de Reactor/MCP SDK.
- **DamageSystem no cableado**: `CollisionSystem` aplica daño sin pasar por reducción de `DamageSystem` — la reducción de daño del UpgradeSystem no tiene efecto.
- **ADRENALINE sin efecto real**: `SkillSystem` activa un timer pero `MovementSystem`/`ShootingSystem` no consultan multiplicadores de ADRENALINE.

## File structure
```
shared/     → message/, model/, state/, util/
server/     → network/, handler/, auth/, room/, game/ (engine/, system/, map/), db/, util/
client/     → network/, game/, input/, render/, ui/, asset/, util/
              mcp/ → ClientMcpServer, McpTransport (interface), McpTcpTransport,
                     McpStdioTransport, McpToolRegistry, McpJsonRpcHandler,
                     McpGameContext
              mcp/tools/ → StatusTools, UiTools, UiSyncTools, GameTools,
                           GameObservabilityTools, GameControlTools
mcp-bridge/ → MCP bridge standalone (McpBridge.java + BridgeGameClient.java)
.opencode/skills/ → 9 skills de desarrollo
.opencode/rules/ → SOLID-RULES.md, TILE-RULES.md, MULTIPLAYER-RULES.md
tools/      → Python test scripts (test_client.py, load_test.py, multi_client_test.py)
```

## Adding new message types
1. Add enum value to `MessageType` in `shared`
2. Create message class extending `Message` in `shared`
3. Add `case TYPE -> NewMessage.class` to `JsonUtil.MessageAdapter.getTargetClass()`
4. Add handler case in `MessageHandler` (server)
5. If needed on client, add handling in `GameClient.handleMessage()`

## Adding new player skills
1. Add enum value to `PlayerSkill.java` (cooldown, duration, category, displayName)
2. Add effect logic case in `SkillSystem.activateSkill()` — usar `setPosition(new Vector2(...))` NO `getPosition().add(x, y)`
3. Add deactivation logic in `SkillSystem.deactivateEffect()` if needed
4. Update HUD in `Renderer.drawHud()` if new visual is needed
5. If skill has pickup, add to `GameInstance.spawnInitialPickups()`

## Dependencies
- Java 25, Maven 3.9+, Gson 2.10.1, Java-WebSocket 1.5.6, JavaFX 25, jbcrypt 0.4, SLF4J 2.0.12, JUnit 5.10.2, Mockito 5.11.0, ByteBuddy 1.14.15, TestFX 4.0.18
- MCP SDK 0.17.2 (mcp-bom, mcp-core, mcp-json, mcp-json-jackson2), Jackson 2.17.1, HikariCP 5.1.0, SQLite 3.45.1, PostgreSQL 42.7.1
- No Spring/Hibernate.

## Dev credentials (hardcoded en `AuthService`)
- `player1` / `pass1`, `player2` / `pass2`
