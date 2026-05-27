# Game Client MCP Skill

## Description
Working with the JavaFX client's `--mcp` mode, which exposes an embedded MCP server (TCP on localhost:4567) for AI agent control.

## When to use
- Debugging the client MCP server
- Adding new client-side MCP tools
- Testing screenshot capture or key simulation
- Modifying how the AI agent interacts with the client

## Architecture
The client MCP server (`ClientMcpServer`) orchestrates 11 classes:
- **Transport**: `McpTransport` interface → `McpTcpTransport` (TCP localhost:4567, JSON-RPC 2.0 via `McpJsonRpcHandler`) o `McpStdioTransport` (MCP SDK nativo)
- **Registry**: `McpToolRegistry` centraliza tool definitions y handler mappings
- **Game context**: `McpGameContext` provee acceso thread-safe a `GameState`, jugadores, balas, pickups, estado de pantalla
- **Tool providers** (6 clases en `mcp/tools/`): `StatusTools` (2), `UiTools` (8), `UiSyncTools` (1), `GameTools` (5), `GameControlTools` (3), `GameObservabilityTools` (3)
- **JSON-RPC**: `McpJsonRpcHandler` maneja protocolo JSON-RPC 2.0 para TCP transport

Expone 22 tools que permiten a una IA ver el viewport, presionar teclas e interactuar con pantallas UI. Todos los tools UI validan la pantalla actual antes de ejecutarse.

## Steps

### 1. Run client in MCP mode
```bash
mvn javafx:run -pl client -Djavafx.args="--mcp"
```

### 2. Client MCP tools (22 total)
| Category | Tools |
|----------|-------|
| Status | `get_screen_info`, `get_last_error` |
| UI | `ui_login`, `ui_create_room`, `ui_join_room`, `ui_start_game`, `ui_leave_room`, `ui_request_room_list`, `ui_back_to_lobby`, `ui_logout` |
| UI Sync | `wait_for_screen` |
| Game Observability | `screenshot`, `get_hud_info`, `get_player_position`, `get_game_state`, `get_other_players`, `get_bullets`, `get_map_pickups` |
| Game Control | `send_key`, `aim_at`, `mouse_move`, `aim_direction` |

### 3. Adding a new client MCP tool
1. Identificar la categoria: `StatusTools` / `UiTools` / `UiSyncTools` / `GameTools` / `GameControlTools` / `GameObservabilityTools`
2. Agregar tool en la clase provider correspondiente (`mcp/tools/XxxTools.java`) usando `registry.registerTool(name, desc, props, handler)`
3. Si la tool necesita GameState, usar los metodos de `McpGameContext` (`getLocalPlayer()`, `getOtherPlayers()`, etc.)
4. UI tools: validar pantalla actual al inicio del handler (`if (!"lobby".equals(ctx.getCurrentScreen())) → error`)
5. El provider se auto-wirea en el constructor de `ClientMcpServer` — no se necesita boilerplate de registro adicional
6. Rebuild y probar con `--mcp`
