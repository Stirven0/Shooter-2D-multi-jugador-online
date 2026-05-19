# AGENTS.md — Multiplayer Shooter Game

## Branch workflow
- Todo el trabajo en `develop`. Nunca commitear a `main`.
- Rama `mcp` para características experimentales de IA/MCP.

## Quick start
```bash
mvn clean install -DskipTests              # build & install todo (5 módulos)
java -jar server/target/server-1.0-SNAPSHOT.jar   # servidor :8080
mvn javafx:run -pl client                  # lanzar cliente
java -jar mcp-bridge/target/mcp-bridge-1.0-SNAPSHOT.jar --username ai_player  # MCP bridge
tools/launch_mcp_client.cmd --mcp --username player --password pass --host localhost --port 8080  # cliente MCP directo (sin Maven)
java --module-path ... --add-modules ... -classpath ... com.aa.client.Main --mcp --host 0.0.0.0 --port 8080  # o via java directo
python tools/test_client.py                 # bot headless (pip install websocket-client)
python tools/load_test.py 5                 # stress test (5 bots)
```

## Test commands
```bash
mvn test -pl server                         # 57 tests server
mvn test -pl server -Dtest="!*IntegrationTest"  # solo unitarias
Xvfb :99 -ac -screen 0 1280x720x24 &       # iniciar display virtual
DISPLAY=:99 mvn test -pl client             # 20 tests UI (requiere Xvfb)
mvn test -pl server,client                  # ambos módulos
```

## Architecture rules
- **Server-authoritative**: Client NEVER sends positions. Only normalized inputs (-1..1).
- **Thread safety**: Only GameLoop mutates GameState. Network thread enqueues inputs via ConcurrentLinkedQueue.
- **JSON**: Use `JsonUtil.toJson()` / `parseMessage()` exclusively. Never manual parse except for lobby messages in MessageHandler.
- **Broadcast**: Always `state.copy()` before serializing. Never send mutable GameState reference.
- **GameLoop**: Fixed 30Hz timestep (configurable en `ServerConfig.TICK_RATE`). Drift resets `nextTick` to avoid death spiral.
- **Weapon System**: 5 tipos (PISTOL/SHOTGUN/RIFLE/SNIPER/SMG), 2 slots (primaria/secundaria), Q para swap. Stats embebidos en enum `WeaponType`. Pickups spawn aleatorios en mapa.
- **Power-ups**: 7 tipos (Speed/Damage+/FireRate/Shield/Health, Slow/Debilidad como debuffs). Temporales (15s) con respawn. Se recogen automáticamente al colisionar.
- **Upgrade System**: 5 niveles por kills acumulados en partida (2/5/9/14/20). Mejoras pasivas: daño, cadencia, velocidad, HP max, reducción daño. Persiste al morir.
- **Player Skills (Activas)**: 6 tipos (DASH/SHIELD_BURST/HEAL/ADRENALINE/EMP/STEALTH), 2 slots por jugador, teclas E y F, cooldowns en HUD. Skills aleatorias al spawn.
- **DB persistence**: Tabla `player_stats` (total_kills, total_deaths, total_wins, total_games, upgrade_points). Stats persistidos vía `DatabaseManager.savePlayerStats()` al terminar partida.

## MCP Integration
- **mcp-bridge module**: Standalone MCP server (stdio transport) que se conecta como bot al game server via WebSocket. Expone 7 tools para agentes IA.
- **Client MCP mode**: Flag `--mcp` en el cliente JavaFX. Expone 5 tools (screenshot, send_key, get_hud_info, get_player_position, get_game_state).
- **MCP SDK**: `io.modelcontextprotocol.sdk:mcp-core:0.17.2` con Jackson mapper. Transporte stdio.
- **Tools bridge**: get_state, get_map, move, shoot, swap_weapon, use_skill, get_inventory.

## Gotchas
- **Gson recursion split**: Two Gson instances — `gsonPlain` (no adapter) and `gson` (with `MessageAdapter`). Use the right one.
- **CREATE_ROOM maps to `LoginMessage.class`** in `MessageAdapter.getTargetClass()` — intentional hack. CREATE_ROOM is parsed manually from `JsonObject` in `MessageHandler`. No "arreglar".
- **JUnit 3.8.1 in root `dependencyManagement`** is dead code. Actual testing uses JUnit 5 (Jupiter) from server/pom.xml.
- **Reconnection (server-only)**: Server valida token + encola reactivación en GameInstance. Cliente nunca inicia reconexión — al desconectar resetea a lobby.
- **PING/PONG**: Server tracks nothing. Client ignores PING, server ignores PONG. No latency tracking.
- **Byte Buddy + JDK 25**: Requiere `-Dnet.bytebuddy.experimental=true` en argLine del surefire plugin para mockear con Mockito.
- **ServerConfig hardcodes values**: No carga .env pese a existir `.env.example`. Editar ServerConfig.java para cambiar TICK_RATE, PLAYER_SPEED, BULLET_DAMAGE, IDLE_THRESHOLD, etc.
- **No CI/CD**: No .github, no Actions, no pre-commit hooks.
- **Unused MessageType values**: `ROTATE_INPUT`, `DELTA_STATE`, `ENTITY_SPAWN`, `ENTITY_DESTROY`, `PLAYER_HIT`, `PLAYER_DEATH` definidos en enum pero sin cablear en MessageAdapter ni handlers. `USE_ABILITY` renombrado a `USE_SKILL`.
- **MCP SDK `mcp` artifact is a BOM**: El artefacto `io.modelcontextprotocol.sdk:mcp` es un POM vacío. Usar `mcp-core`, `mcp-json`, `mcp-json-jackson2` directamente. Requiere `jackson-databind` para `JacksonMcpJsonMapper`.
- **MCP AsyncServer no tiene `start()`**: `McpServer.async(transport).build()` devuelve servidor ya iniciado. Usar `server.addTool()` post-build para registrar tools.
- **Vector2 inmutable**: Es un `record`. No tiene setters ni `add(double, double)`. Usar `add(Vector2)`, `multiply(double)`, y `setPosition(new Vector2(...))`.

## File structure
```
shared/     → message/, model/, state/, util/
server/     → network/, handler/, auth/, room/, game/ (engine/, system/, map/), db/, util/
client/     → network/, game/, input/, render/, ui/, mcp/, asset/, util/
mcp-bridge/ → MCP bridge standalone (McpBridge.java + BridgeGameClient.java)
.opencode/skills/ → opencode development skills
tools/      → Python test scripts
```

## Adding new message types
1. Add enum value to `MessageType`
2. Create message class extending `Message`
3. Add `case TYPE -> NewMessage.class` to `JsonUtil.MessageAdapter.getTargetClass()`
4. Add handler case in `MessageHandler`
5. If needed on client, add handling in `GameClient.handleMessage()`

## Adding new player skills
1. Add enum value to `PlayerSkill.java` (cooldown, duration, category, displayName)
2. Add effect logic case in `SkillSystem.activateSkill()`
3. Add deactivation logic in `SkillSystem.deactivateEffect()` if needed
4. Update HUD in `Renderer.drawHud()` if new visual is needed
5. If skill has pickup, add to `GameInstance.spawnInitialPickups()`

## Dependencies
- Java 25, Gson 2.10.1, Java-WebSocket 1.5.6, JavaFX 25, jbcrypt 0.4, SLF4J 2.0.12, JUnit 5.10.2, Mockito 5.11.0, TestFX 4.0.18
- MCP SDK 0.17.2 (mcp-core, mcp-json, mcp-json-jackson2), Jackson 2.17.1, Reactor
- No Spring/Hibernate.

## Credentials (dev only)
- `player1` / `pass1`, `player2` / `pass2` (creados en AuthService constructor)
