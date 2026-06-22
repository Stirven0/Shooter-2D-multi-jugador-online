# Shooter 2D — Multiplayer Game

Juego de disparos multijugador en 2D con arquitectura servidor-autoritario.
Cliente JavaFX, servidor WebSocket en Java 25. Integración con
**Model Context Protocol (MCP)** para agentes de IA.

## 📦 Descargas (v0.2)

| Plataforma | Server | Client |
|------------|--------|--------|
| **Windows** | `Shooter-Server.exe` | `Shooter-Client.exe` |
| **Linux** | `shooter-server_1.0_amd64.deb` | `shooter-client_1.0_amd64.deb` |
| **Multiplataforma (ZIP)** | [`Shooter-Server-v0.2.zip`](https://github.com/Stirven0/Shooter-2D-multi-jugador-online/releases/download/v0.2/Shooter-Server-v0.2.zip) | — |

### Quick Start (instaladores)

```bash
# Linux — instalar paquete .deb
sudo dpkg -i shooter-server_1.0_amd64.deb
sudo dpkg -i shooter-client_1.0_amd64.deb

# Servidor con Docker + Tailscale
docker compose up -d

# Windows — ejecutar instalador .exe
# Shooter-Server.exe → servidor
# Shooter-Client.exe → cliente
```

### Quick Start (desde código fuente)

```bash
# Compilar todo
mvn package -DskipTests

# Servidor
java -jar server/target/server.jar

# Cliente (requiere JavaFX SDK en module-path o Liberica JDK Full)
./run-client.sh
./run-client.sh --host shooter.tail642e6a.ts.net --port 443   # WSS vía Tailscale funnel
```

## Requisitos (desarrollo)

- Java 25 (JDK)
- Maven 3.9+
- Python 3 (para scripts de test y herramientas)
- Xvfb (opcional, para tests UI en Linux)
- dpkg-dev (opcional, para empaquetar .deb en Linux)
- WiX Toolset (opcional, para empaquetar .exe en Windows)

## Quick start (desarrollo)

```bash
# Compilar e instalar todo (4 módulos)
mvn clean install -DskipTests

# Terminal 1: Servidor
java -jar server/target/server.jar

# Terminal 2: Cliente (JavaFX)
./run-client.sh

# Terminal 3: Cliente en modo MCP (para agente IA)
./run-client.sh --mcp

# Terminal 4: MCP Bridge standalone (bot IA)
java -jar mcp-bridge/target/mcp-bridge.jar --username ai_player

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
- **GameLoop a 30 Hz**: Timestep fijo en `ServerConfig.TICK_RATE`, deriva corregida tick a tick
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
        "-jar", "/ruta/a/mcp-bridge/target/mcp-bridge.jar",
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

## Empaquetado nativo

El proyecto incluye scripts para crear instaladores con JRE+JavaFX embebidos (no requieren JDK):

```bash
# Linux → .deb (requiere dpkg-dev)
python3 tools/package-native.py

# Windows → .exe (requiere WiX Toolset, ejecutar en Windows)
package-native.bat

# Solo servidor o solo cliente
python3 tools/package-native.py --server-only
python3 tools/package-native.py --client-only
```

El script resuelve JavaFX automáticamente desde el caché de Maven o `~/.javafx-sdk/25/`.

## Changelog v0.2

- **Fat JARs unificados**: `server.jar` (15 MB), `client.jar` (28 MB, sin JavaFX embebido), `mcp-bridge.jar` (6 MB)
- **Empaquetado nativo**: Scripts para crear `.deb`/`.exe` con JRE + JavaFX incluidos
- **WSS automático**: `ClientConfig.getServerUrl()` detecta puerto 443/8443 y cambia a `wss://`
- **Flag `--ssl`**: Forzar WSS manualmente en cualquier puerto
- **Scripts lanzadores**: `run-server.sh`, `run-client.sh`, `run-client.bat` con module-path para JavaFX
- **SOLID MCP refactoring**: `ClientMcpServer` dividido en 11 clases (Facade, Transport strategy, ToolRegistry, 6 providers)
- **Corrección WSS funnel**: Cliente ahora usa `wss://shooter.tail642e6a.ts.net` contra Tailscale funnel
- **Dockerfile actualizado**: Copia `server.jar` directamente
- **Opencode shortcuts**: `package`, `package-win` para empaquetado

## Dev credentials (hardcoded en `AuthService`)

```
player1 / pass1    player2 / pass2
```

## Licencia

[MIT](LICENSE)
