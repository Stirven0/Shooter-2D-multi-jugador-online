# Multiplayer Shooter Game

Juego de disparos multijugador en 2D con arquitectura servidor-autoritario.
Cliente JavaFX, servidor WebSocket en Java 25. Incluye integración con
**Model Context Protocol (MCP)** para agentes de IA.

## Requisitos

- Java 25 (JDK)
- Maven 3.9+
- Python 3 (para scripts de test)
- Xvfb (opcional, para ejecutar tests UI)

## Quick start

```bash
# Compilar e instalar todo (5 módulos)
mvn clean install -DskipTests

# Terminal 1: Servidor
java -jar server/target/server-1.0-SNAPSHOT.jar

# Terminal 2: Cliente (JavaFX)
mvn javafx:run -pl client

# Terminal 3: Cliente en modo MCP (para agente IA)
mvn javafx:run -pl client -Dexec.args="--mcp"

# Terminal 4: MCP Bridge standalone (bot IA)
java -jar mcp-bridge/target/mcp-bridge-1.0-SNAPSHOT.jar --username ai_player

# Terminal 5: Test bot (Python)
pip install websocket-client
python tools/test_client.py
```

## Estructura del proyecto

```
shared/      → message/, model/, state/, util/  (código compartido)
server/      → network/, handler/, auth/, room/, game/ (engine/, system/, map/), db/, util/
client/      → network/, game/, input/, render/, ui/, mcp/, asset/, util/
mcp-bridge/  → standalone MCP server para agentes IA
.opencode/skills/ → skills de desarrollo para opencode
tools/       → Scripts Python para testing
```

## Módulos

| Módulo | Artefacto | Puerto/Transporte | Descripción |
|--------|-----------|-------------------|-------------|
| `shared` | `com.aa:shared` | — | Modelos, mensajes, utilidades |
| `server` | `com.aa:server` | `:8080` (WS) | Servidor autoritario del juego |
| `client` | `com.aa:client` | JavaFX GUI | Cliente gráfico |
| `mcp-bridge` | `com.aa:mcp-bridge` | stdio (MCP) | Bridge para agentes IA |

## Tests

```bash
# Servidor (57 tests)
mvn test -pl server

# Servidor — solo unitarias
mvn test -pl server -Dtest="!*IntegrationTest"

# Cliente — UI tests (requiere Xvfb)
Xvfb :99 -ac -screen 0 1280x720x24 &
DISPLAY=:99 mvn test -pl client

# Servidor + Cliente
mvn test -pl server,client
```

## Arquitectura

- **Server-authoritative**: El cliente nunca envía posiciones; solo inputs normalizados (-1..1)
- **GameLoop a 30 Hz**: Timestep fijo configurable en `ServerConfig.TICK_RATE`, deriva corregida tick a tick
- **Thread-safe**: Solo el GameLoop muta GameState; hilo de red encola inputs vía `ConcurrentLinkedQueue`
- **Broadcast**: Siempre `state.copy()` antes de serializar — nunca se envía referencia mutable
- **Sistema de armas**: 5 tipos (Pistol/Shotgun/Rifle/Sniper/SMG), 2 slots, tecla Q para swap, pickups en mapa
- **Power-ups**: 7 tipos temporales (15s) con respawn, recolección automática al colisionar
- **Mejoras por kills**: 5 niveles pasivos (daño, cadencia, velocidad, HP, reducción daño), persisten al morir
- **Habilidades activas**: 6 skills (Dash/Shield Burst/Heal/Adrenaline/EMP/Stealth) con teclas E y F, cooldowns en HUD
- **Persistencia DB**: Tabla `player_stats` con kills/deaths/wins/games/upgrade_points guardados al finalizar partida

## Integración MCP (AI Agent)

El proyecto expone dos servidores MCP para que agentes de IA (Claude Desktop, etc.)
puedan jugar:

### MCP Bridge (standalone)

```json
{
  "mcpServers": {
    "multiplayer-bridge": {
      "command": "java",
      "args": [
        "-jar", "/ruta/a/mcp-bridge/target/mcp-bridge-1.0-SNAPSHOT.jar",
        "--host", "localhost",
        "--port", "8080",
        "--username", "ai_player"
      ]
    }
  }
}
```

**Tools disponibles**: `get_state`, `get_map`, `move`, `shoot`, `swap_weapon`, `use_skill`, `get_inventory`

### Client MCP (embebido)

El cliente JavaFX expone un servidor MCP con el flag `--mcp`:
- `screenshot` — captura del viewport como base64
- `send_key` — simula teclas (WASD, Q, E, F, clic)
- `get_hud_info` — health, arma, kills, buffs
- `get_player_position` — coordenadas del jugador
- `get_game_state` — estado completo del juego

## Licencia

MIT
