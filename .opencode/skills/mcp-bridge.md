# MCP Bridge Skill

## Description
Development and debugging of the MCP Bridge module (`mcp-bridge/`), which connects AI agents to the game server via the Model Context Protocol.

## When to use
- Adding or modifying MCP tools exposed to the AI agent
- Debugging WebSocket connection between bridge and game server
- Testing AI agent gameplay via Claude Desktop
- Modifying tool schemas or descriptions

## Steps

### 1. Build the MCP bridge
```bash
mvn clean install -DskipTests -pl mcp-bridge -am
```

### 2. Start server + bridge
```bash
# Terminal 1: game server
java -jar server/target/server-1.0-SNAPSHOT.jar

# Terminal 2: MCP bridge (stdio mode)
java -jar mcp-bridge/target/mcp-bridge-1.0-SNAPSHOT.jar --host localhost --port 8080 --username ai_player --password ai_pass
```

The bridge includes an autonomous AI loop that moves toward the nearest enemy and shoots when in range.

### 3. Test with Claude Desktop
Add to `claude_desktop_config.json`:
```json
{
  "mcpServers": {
    "multiplayer-bridge": {
      "command": "java",
      "args": [
        "-jar", "/path/to/mcp-bridge/target/mcp-bridge-1.0-SNAPSHOT.jar",
        "--host", "localhost",
        "--port", "8080",
        "--username", "ai_player",
        "--password", "ai_pass"
      ]
    }
  }
}
```

### 4. Adding a new tool
1. Add tool logic in `McpBridge.java` `registerTools()` method
2. Define input schema using `Map.of(...)` with parameter descriptions
3. Implement the executor lambda
4. Rebuild and test
