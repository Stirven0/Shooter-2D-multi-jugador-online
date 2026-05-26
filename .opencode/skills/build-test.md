# Build & Test Skill

## Description
Building, testing, and running all modules of the multiplayer game project.

## When to use
- Initial project setup
- Running tests before committing
- Building specific modules
- Debugging compilation errors

## Commands

### Full build (skip tests)
```bash
mvn clean install -DskipTests
```

### Build specific module with dependencies
```bash
mvn clean install -DskipTests -pl mcp-bridge -am
```

### Server tests
```bash
mvn test -pl server
mvn test -pl server -Dtest="!*IntegrationTest"
```

### Client UI tests (requires Xvfb)
```bash
Xvfb :99 -ac -screen 0 1280x720x24 &
DISPLAY=:99 mvn test -pl client
```

### Run server
```bash
java -jar server/target/server-1.0-SNAPSHOT.jar
```

### Run client
```bash
mvn javafx:run -pl client
```

### Run MCP bridge
```bash
java -jar mcp-bridge/target/mcp-bridge-1.0-SNAPSHOT.jar --username ai_player
```

### Clean everything
```bash
mvn clean
```
