# Game Client MCP Skill

## Description
Working with the JavaFX client's `--mcp` mode, which exposes an embedded MCP server (TCP on localhost:4567) for AI agent control.

## When to use
- Debugging the client MCP server
- Adding new client-side MCP tools
- Testing screenshot capture or key simulation
- Modifying how the AI agent interacts with the client

## Architecture
The client MCP server runs alongside the JavaFX game loop. It exposes 22 tools that let an AI see the game viewport, press keys, and interact with UI screens as if it were a human player. All UI tools validate the current screen before executing.

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
1. Add tool registration in `ClientMcpServer.java` using `addTool()` helper
2. Screen validation: add screen check at the start of UI tool handlers
3. Rebuild and test with `--mcp`
