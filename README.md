# Shooter 2D — Multiplayer Game

Juego de disparos multijugador en 2D con arquitectura servidor-autoritario.
Cliente JavaFX, servidor WebSocket en Java 25. Integración con
**Model Context Protocol (MCP)** para agentes de IA.

## 📦 Descargas (v0.1)

| Archivo | SO |
|---------|-----|
| [`Shooter-Server-v0.1.zip`](https://github.com/Stirven0/Shooter-2D-multi-jugador-online/releases/download/v0.1/Shooter-Server-v0.1.zip) | **Windows, Linux, Mac** |
| [`Shooter-Client-v0.1-linux.zip`](https://github.com/Stirven0/Shooter-2D-multi-jugador-online/releases/download/v0.1/Shooter-Client-v0.1-linux.zip) | Linux |
| [`Shooter-Client-v0.1-win.zip`](https://github.com/Stirven0/Shooter-2D-multi-jugador-online/releases/download/v0.1/Shooter-Client-v0.1-win.zip) | Windows |
| [`Shooter-Client-v0.1-mac.zip`](https://github.com/Stirven0/Shooter-2D-multi-jugador-online/releases/download/v0.1/Shooter-Client-v0.1-mac.zip) | Mac |

### Quick Start

```bash
# Servidor (cualquier SO)
unzip Shooter-Server-v0.1.zip && cd Shooter-Server
./run-server.sh          # Linux/Mac
run-server.cmd           # Windows

# Cliente Linux
unzip Shooter-Client-v0.1-linux.zip && cd Shooter-Client
./run-client.sh --host <ip-del-servidor>

# Cliente Windows
run-client.cmd --host <ip-del-servidor>

# Cliente Mac
./run-client.sh --host <ip-del-servidor>
```

## Requisitos (desarrollo)

- Java 25 (JDK)
- Maven 3.9+
- Python 3 (para scripts de test y herramientas)
- Xvfb (opcional, para tests UI en Linux)

## Quick start (desarrollo)

```bash
# Compilar e instalar todo (4 módulos)
mvn clean install -DskipTests

# Terminal 1: Servidor
java -jar server/target/server-1.0-SNAPSHOT.jar

# Terminal 2: Cliente (JavaFX)
mvn javafx:run -pl client

# Terminal 3: Cliente en modo MCP (para agente IA)
mvn javafx:run -pl client -Djavafx.args="--mcp"

# Terminal 4: MCP Bridge standalone (bot IA)
java -jar mcp-bridge/target/mcp-bridge-1.0-SNAPSHOT.jar --username ai_player

# Terminal 5: Test bot (Python)
pip3 install websocket-client
python3 tools/test_client.py
```

## Tests

```bash
# Servidor (57 tests)
mvn test -pl server

# Servidor — solo unitarias
mvn test -pl server -Dtest="!*IntegrationTest"

# Cliente — UI tests (requiere Xvfb en Linux)
Xvfb :99 -ac -screen 0 1280x720x24 &
DISPLAY=:99 mvn test -pl client

# Servidor + Cliente
mvn test -pl server,client
```

## Estructura del proyecto

```
shared/      → message/, model/, state/, util/  (código compartido)
server/      → network/, handler/, auth/, room/, game/ (engine/, system/, map/), db/, util/
client/      → network/, game/, input/, render/, ui/, mcp/, asset/, util/
mcp-bridge/  → standalone MCP server para agentes IA
.opencode/   → rules/ + skills/ para desarrollo con OpenCode
tools/       → Scripts Python (test, release, generación de assets)
```

## Módulos

| Módulo | Artefacto | Puerto/Transporte | Descripción |
|--------|-----------|-------------------|-------------|
| `shared` | `com.aa:shared` | — | Modelos, mensajes, utilidades |
| `server` | `com.aa:server` | `:8080` (WS) | Servidor autoritario del juego |
| `client` | `com.aa:client` | JavaFX GUI | Cliente gráfico |
| `mcp-bridge` | `com.aa:mcp-bridge` | stdio (MCP) | Bridge para agentes IA |

## Arquitectura

- **Server-authoritative**: El cliente nunca envía posiciones; solo inputs normalizados (-1..1)
- **GameLoop a 20 Hz**: Timestep fijo en `ServerConfig.TICK_RATE`, deriva corregida tick a tick
- **Thread-safe**: Solo el GameLoop muta GameState; hilo de red encola inputs vía `ConcurrentLinkedQueue`
- **Broadcast**: Siempre `state.copy()` antes de serializar — nunca se envía referencia mutable
- **Mapas TMJ**: 4 mapas con tile engine (Tiled .tmj + sprite sheet `warehouse.png`, viewport culling)
- **Sistema de armas**: 5 tipos (Pistol/Shotgun/Rifle/Sniper/SMG), 2 slots, tecla Q para swap
- **Power-ups**: 7 tipos temporales (Speed/Damage+/FireRate/Shield/Health/Slow/Weakness)
- **Mejoras por kills**: 5 niveles pasivos (daño, cadencia, velocidad, HP, reducción daño)
- **Habilidades activas**: 6 skills (Dash/Shield Burst/Heal/Adrenaline/EMP/Stealth), teclas E y F
- **Persistencia DB**: Tabla `player_stats` con kills/deaths/wins/games/upgrade_points (SQLite)
- **Efectos visuales**: Sprites 12 PNGs, muzzle flash, indicador de dirección de daño
- **Audio**: 8 SFX + 3 temas musicales (WAV, AudioClip nativo sin GStreamer)

## Integración MCP (AI Agent)

El proyecto expone dos servidores MCP para que agentes de IA puedan jugar:

### Client MCP (embebido, 22 tools)

```bash
mvn javafx:run -pl client -Djavafx.args="--mcp"
```

TCP en `localhost:4567`, JSON-RPC 2.0. Categories: Status, UI, UI Sync, Game Observability, Game Control.

### MCP Bridge (standalone, 7 tools)

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

**Tools**: `get_state`, `get_map`, `move`, `shoot`, `swap_weapon`, `use_skill`, `get_inventory`

## OpenCode

El proyecto incluye configuración completa para desarrollo asistido por IA:

```
opencode.json          → comandos (build, test, run) + MCP server
AGENTS.md              → referencia rápida con gotchas
.opencode/rules/
  SOLID-RULES.md       → principios SOLID con ejemplos del código
  TILE-RULES.md        → motor de tiles TMJ (ambas ramas)
  MULTIPLAYER-RULES.md → arquitectura server-authoritative
.opencode/skills/
  build-test.md, gameplay-skills.md, tile-engine-development.md,
  code-quality.md, game-client-mcp.md, mcp-bridge.md,
  ai-agent.md, subagent-orchestration.md, session-checkpoint.md
```

## Dev credentials (hardcoded en `AuthService`)

```
player1 / pass1    player2 / pass2
```

## Licencia

[MIT](LICENSE)
