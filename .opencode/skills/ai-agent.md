# AI Agent Skill

## Description
Configuring and using the AI agent (via Claude Desktop) to play the game as a bot.

## When to use
- Setting up Claude Desktop for the first time
- Debugging AI agent behavior
- Testing game balance against AI
- Adding new capabilities for the agent

## Setup

### 1. Build all modules
```bash
mvn clean install -DskipTests
```

### 2. Start the game server
```bash
java -jar server/target/server-1.0-SNAPSHOT.jar
```

### 3. Configure Claude Desktop
Add to your `claude_desktop_config.json`:
```json
{
  "mcpServers": {
    "multiplayer-bridge": {
      "command": "java",
      "args": [
        "-jar", "/ABSOLUTE/PATH/TO/mcp-bridge/target/mcp-bridge-1.0-SNAPSHOT.jar",
        "--host", "localhost",
        "--port", "8080",
        "--username", "ai_player",
        "--password", "ai_pass"
      ]
    }
  }
}
```

### 4. Start playing
Ask Claude: "Connect to the multiplayer game and play as ai_player. Move around, pick up weapons, and shoot enemies."

## Available tools for the agent
- `get_state` - See all players, positions, health
- `get_map` - Map layout with pickups
- `move(dx, dy)` - Move in direction
- `shoot(angle)` - Fire weapon
- `swap_weapon()` - Switch weapon slot
- `use_skill(slot)` - Activate skill
- `get_inventory` - Check weapons and buffs

## Notes

- The bridge has an autonomous AI loop that moves toward enemies and shoots. To disable it, stop the bridge process.
- All game interactions (login, room creation, game start) must go through MCP tools. No auto-login CLI flags exist.`
