# MCP Full Flow Test

## Prereqs
- Server running on :8080
- opencode restarted (so it spawns the 4 MCP clients)
- Each client opens in a 2x2 grid (alpha top-left, bravo top-right, charlie bottom-left, delta bottom-right)

## Flow (paste these one at a time)

### 1. Verify all clients are connected
```
> Use MCP tool: client-alpha get_screen_info
```
Expected: `currentScreen: "lobby"`, no `lastError`

### 2. Check what rooms exist
```
> Use MCP tool: client-alpha ui_request_room_list
> Use MCP tool: client-alpha get_last_error
```

### 3. Alpha creates a room
```
> Use MCP tool: client-alpha ui_create_room("map_01")
> Use MCP tool: client-alpha get_screen_info
```
Expected: `currentScreen: "lobby"`, `roomId` present

### 4. Bravo joins via room list
```
> Use MCP tool: client-bravo ui_request_room_list
> Use MCP tool: client-bravo get_screen_info
> Use MCP tool: client-bravo ui_join_room(room_index=0)
> Use MCP tool: client-bravo get_screen_info
```
Expected: `roomId` matches alpha's room

### 5. Charlie joins
```
> Use MCP tool: client-charlie ui_join_room(room_index=0)
> Use MCP tool: client-charlie get_screen_info
```

### 6. Delta joins
```
> Use MCP tool: client-delta ui_join_room(room_index=0)
> Use MCP tool: client-delta get_screen_info
```

### 7. Alpha starts the game
```
> Use MCP tool: client-alpha ui_start_game
> Use MCP tool: client-alpha get_screen_info
```
Expected: `currentScreen: "game"` (all clients should auto-transition)

### 8. Gameplay — move and shoot
```
> Use MCP tool: client-alpha send_key("W", "press")
> Use MCP tool: client-alpha send_key("A", "press")
> Use MCP tool: client-alpha send_key("CLICK")
> Use MCP tool: client-alpha send_key("W", "release")
> Use MCP tool: client-alpha send_key("A", "release")
> Use MCP tool: client-alpha get_game_state
```

### 9. Screenshot
```
> Use MCP tool: client-alpha screenshot
```

### 10. Use skills
```
> Use MCP tool: client-alpha send_key("E")       # skill slot 1
> Use MCP tool: client-alpha send_key("F")       # skill slot 2
> Use MCP tool: client-alpha send_key("Q")       # swap weapon
```
