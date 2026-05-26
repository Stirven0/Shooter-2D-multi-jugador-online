package com.aa.client.mcp;

import com.aa.client.game.GameClient;
import com.aa.client.game.GameClientState;
import com.aa.client.input.InputHandler;
import com.aa.client.render.Camera;
import com.aa.client.render.Renderer;
import com.aa.client.ui.LobbyScreen;
import com.aa.client.ui.ScreenManager;
import com.aa.client.util.ClientConfig;
import com.aa.shared.message.BuffUpdateMessage;
import com.aa.shared.message.RoomListResponseMessage;
import com.aa.shared.model.Bullet;
import com.aa.shared.model.Player;
import com.aa.shared.model.PowerUpPickup;
import com.aa.shared.model.WeaponPickup;
import com.aa.shared.state.GameState;
import com.aa.shared.util.JsonUtil;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpAsyncServer;
import io.modelcontextprotocol.server.McpAsyncServerExchange;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;

import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.robot.Robot;
import javafx.stage.Stage;

import reactor.core.publisher.Mono;

import javax.imageio.ImageIO;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ClientMcpServer {

    private final GameClient gameClient;
    private final InputHandler inputHandler;
    private final Renderer renderer;
    private Canvas canvas;
    private final Stage stage;
    private final ScreenManager screenManager;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private volatile boolean running = false;
    private McpAsyncServer server;
    private Robot robot;
    private ServerSocket tcpServerSocket;
    private final Map<String, McpSchema.Tool> toolDefs = new ConcurrentHashMap<>();
    private final Map<String, java.util.function.BiFunction<McpAsyncServerExchange, Map<String, Object>, Mono<McpSchema.CallToolResult>>> toolHandlers = new ConcurrentHashMap<>();

    public ClientMcpServer(GameClient gameClient, InputHandler inputHandler, Renderer renderer, Canvas canvas, Stage stage) {
        this.gameClient = gameClient;
        this.inputHandler = inputHandler;
        this.renderer = renderer;
        this.canvas = canvas;
        this.stage = stage;
        this.screenManager = gameClient.getScreenManager();
    }

    public void setCanvas(Canvas canvas) {
        this.canvas = canvas;
    }

    public void start() {
        if (running) return;
        running = true;

        registerTools();

        if (ClientConfig.isMcpTcpEnabled()) {
            startTcp(ClientConfig.getMcpHost(), ClientConfig.getMcpPort());
        } else {
            startStdio();
        }
    }

    private void startStdio() {
        Thread thread = new Thread(() -> {
            try {
                McpJsonMapper jsonMapper = McpJsonMapper.getDefault();

                server = McpServer.async(new StdioServerTransportProvider(jsonMapper))
                    .serverInfo("multiplayer-client", "2.0.0")
                    .capabilities(McpSchema.ServerCapabilities.builder()
                        .tools(true)
                        .build())
                    .build();

                for (var spec : buildToolSpecs()) {
                    server.addTool(spec).subscribe();
                }

                System.err.println("[CLIENT-MCP] MCP Server ready on stdio");
                while (running) {
                    try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
                }
            } catch (Exception e) {
                System.err.println("[CLIENT-MCP] Error: " + e.getMessage());
                e.printStackTrace();
            }
        }, "client-mcp-thread");
        thread.setDaemon(true);
        thread.start();
    }

    public void startTcp(String host, int port) {
        Thread thread = new Thread(() -> {
            try (ServerSocket srv = new ServerSocket(port, 50, InetAddress.getByName(host))) {
                tcpServerSocket = srv;
                System.err.println("[CLIENT-MCP] MCP Server ready on TCP " + host + ":" + port);
                while (running) {
                    Socket client = srv.accept();
                    new Thread(() -> handleTcpClient(client), "mcp-tcp-handler").start();
                }
            } catch (Exception e) {
                if (running) {
                    System.err.println("[CLIENT-MCP] TCP error: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }, "mcp-tcp-thread");
        thread.setDaemon(true);
        thread.start();
    }

    private void handleTcpClient(Socket socket) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter writer = new PrintWriter(socket.getOutputStream(), true)) {
            String line;
            while (running && (line = reader.readLine()) != null) {
                String response = handleJsonRpc(line);
                if (!response.isEmpty()) {
                    writer.println(response);
                }
            }
        } catch (Exception e) {
            if (running) System.err.println("[CLIENT-MCP] TCP client error: " + e.getMessage());
        }
    }

    private String handleJsonRpc(String message) {
        try {
            JsonObject req = gson.fromJson(message, JsonObject.class);
            if (req == null || !req.has("method")) {
                return jsonRpcError(null, -32600, "Invalid Request");
            }

            String method = req.get("method").getAsString();
            JsonElement id = req.has("id") ? req.get("id") : null;

            switch (method) {
                case "initialize" -> {
                    JsonObject caps = new JsonObject();
                    caps.addProperty("protocolVersion", "2024-11-05");
                    JsonObject capsInner = new JsonObject();
                    JsonObject toolsCaps = new JsonObject();
                    toolsCaps.addProperty("listChanged", false);
                    capsInner.add("tools", toolsCaps);
                    caps.add("capabilities", capsInner);
                    JsonObject serverInfo = new JsonObject();
                    serverInfo.addProperty("name", "multiplayer-client");
                    serverInfo.addProperty("version", "2.0.0");
                    caps.add("serverInfo", serverInfo);
                    return jsonRpcResult(id, caps);
                }
                case "notifications/initialized" -> { return ""; }
                case "ping" -> { return jsonRpcResult(id, new JsonObject()); }
                case "tools/list" -> {
                    JsonObject result = new JsonObject();
                    JsonArray toolsArr = new JsonArray();
                    for (McpSchema.Tool tool : toolDefs.values()) {
                        JsonObject t = new JsonObject();
                        t.addProperty("name", tool.name());
                        t.addProperty("description", tool.description() != null ? tool.description() : "");
                        if (tool.inputSchema() != null) {
                            JsonObject schema = new JsonObject();
                            schema.addProperty("type", "object");
                            if (tool.inputSchema().properties() != null) {
                                JsonObject props = new JsonObject();
                                for (var entry : tool.inputSchema().properties().entrySet()) {
                                    @SuppressWarnings("unchecked")
                                    Map<String, Object> propDef = (Map<String, Object>) entry.getValue();
                                    JsonObject pd = new JsonObject();
                                    pd.addProperty("type", (String) propDef.get("type"));
                                    if (propDef.containsKey("description"))
                                        pd.addProperty("description", (String) propDef.get("description"));
                                    if (propDef.containsKey("default"))
                                        pd.add("default", gson.toJsonTree(propDef.get("default")));
                                    props.add(entry.getKey(), pd);
                                }
                                schema.add("properties", props);
                            }
                            if (tool.inputSchema().required() != null) {
                                JsonArray reqArr = new JsonArray();
                                for (String r : tool.inputSchema().required()) reqArr.add(r);
                                schema.add("required", reqArr);
                            }
                            t.add("inputSchema", schema);
                        }
                        toolsArr.add(t);
                    }
                    result.add("tools", toolsArr);
                    return jsonRpcResult(id, result);
                }
                case "tools/call" -> {
                    JsonObject p = req.getAsJsonObject("params");
                    String toolName = p.get("name").getAsString();
                    JsonObject arguments = p.has("arguments") ? p.getAsJsonObject("arguments") : new JsonObject();
                    Map<String, Object> argsMap = new HashMap<>();
                    for (var entry : arguments.entrySet()) {
                        JsonElement val = entry.getValue();
                        if (val.isJsonPrimitive()) {
                            JsonPrimitive prim = val.getAsJsonPrimitive();
                            if (prim.isString()) argsMap.put(entry.getKey(), prim.getAsString());
                            else if (prim.isNumber()) argsMap.put(entry.getKey(), prim.getAsDouble());
                            else if (prim.isBoolean()) argsMap.put(entry.getKey(), prim.getAsBoolean());
                        } else {
                            argsMap.put(entry.getKey(), val.toString());
                        }
                    }
                    var handler = toolHandlers.get(toolName);
                    if (handler == null) {
                        return jsonRpcError(id, -32601, "Tool not found: " + toolName);
                    }
                    McpSchema.CallToolResult result = handler.apply(null, argsMap).block(Duration.ofSeconds(30));
                    return jsonRpcResult(id, callToolResultToJson(result));
                }
                default -> {
                    return jsonRpcError(id, -32601, "Method not found: " + method);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return jsonRpcError(null, -32700, "Parse error: " + e.getMessage());
        }
    }

    private static JsonObject callToolResultToJson(McpSchema.CallToolResult result) {
        JsonObject json = new JsonObject();
        JsonArray content = new JsonArray();
        if (result.content() != null) {
            for (McpSchema.Content c : result.content()) {
                if (c instanceof McpSchema.TextContent tc) {
                    JsonObject item = new JsonObject();
                    item.addProperty("type", "text");
                    item.addProperty("text", tc.text());
                    content.add(item);
                }
            }
        }
        json.add("content", content);
        if (result.isError()) json.addProperty("isError", true);
        return json;
    }

    private static String jsonRpcResult(JsonElement id, JsonObject result) {
        JsonObject resp = new JsonObject();
        resp.addProperty("jsonrpc", "2.0");
        resp.add("id", id != null ? id : JsonNull.INSTANCE);
        resp.add("result", result);
        return resp.toString();
    }

    private static String jsonRpcError(JsonElement id, int code, String msg) {
        JsonObject resp = new JsonObject();
        resp.addProperty("jsonrpc", "2.0");
        resp.add("id", id != null ? id : JsonNull.INSTANCE);
        JsonObject error = new JsonObject();
        error.addProperty("code", code);
        error.addProperty("message", msg);
        resp.add("error", error);
        return resp.toString();
    }

    public void stop() {
        running = false;
        if (server != null) {
            try { server.close(); } catch (Exception ignored) {}
        }
        if (tcpServerSocket != null) {
            try { tcpServerSocket.close(); } catch (Exception ignored) {}
        }
    }

    private Robot getRobot() {
        if (robot == null) {
            robot = new Robot();
        }
        return robot;
    }

    private McpSchema.Tool toolDef(String name, String description) {
        return toolDef(name, description, Map.of());
    }

    private McpSchema.Tool toolDef(String name, String description, Map<String, Object> properties) {
        Map<String, Object> schemaProps = new HashMap<>();
        List<String> required = new java.util.ArrayList<>();

        for (var entry : properties.entrySet()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> propDef = (Map<String, Object>) entry.getValue();
            schemaProps.put(entry.getKey(), propDef);
            if (propDef.get("required") == Boolean.TRUE) {
                required.add(entry.getKey());
            }
        }

        return McpSchema.Tool.builder()
            .name(name)
            .description(description)
            .inputSchema(new McpSchema.JsonSchema("object", schemaProps, required, false, Map.of(), Map.of()))
            .build();
    }

    private void addTool(String name, String description,
                         Map<String, Object> properties,
                         java.util.function.BiFunction<McpAsyncServerExchange, Map<String, Object>, Mono<McpSchema.CallToolResult>> handler) {
        McpSchema.Tool tool = toolDef(name, description, properties);
        toolDefs.put(name, tool);
        toolHandlers.put(name, handler);
    }

    private List<McpServerFeatures.AsyncToolSpecification> buildToolSpecs() {
        List<McpServerFeatures.AsyncToolSpecification> specs = new java.util.ArrayList<>();
        for (var entry : toolDefs.entrySet()) {
            specs.add(new McpServerFeatures.AsyncToolSpecification(
                entry.getValue(), toolHandlers.get(entry.getKey())));
        }
        return specs;
    }

    private void registerTools() {
        registerStatusTools();
        registerUiTools();
        registerGameTools();
        registerGameControlTools();
        registerGameObservabilityTools();
        registerUiSyncTools();
    }

    private void registerStatusTools() {
        addTool("get_screen_info",
            "Get the current client state: which screen is visible (login/lobby/game/gameover), connection status, username, room ID, and player count.",
            Map.of(),
            (exchange, args) -> Mono.just(successResult(buildScreenInfo())));

        addTool("get_last_error",
            "Get the last error message shown to the user. Returns null if no error.",
            Map.of(),
            (exchange, args) -> {
                String err = gameClient.getLastError();
                if (err == null || err.isEmpty()) {
                    err = screenManager.getLastErrorMessage();
                }
                JsonObject result = new JsonObject();
                result.addProperty("error", err);
                result.addProperty("has_error", err != null && !err.isEmpty());
                return Mono.just(successResult(gson.toJson(result)));
            });
    }

    private void registerUiTools() {
        Map<String, Object> usernameProp = new HashMap<>();
        usernameProp.put("type", "string");
        usernameProp.put("description", "Username to register or login with");
        usernameProp.put("required", true);

        Map<String, Object> passwordProp = new HashMap<>();
        passwordProp.put("type", "string");
        passwordProp.put("description", "Password for the account");
        passwordProp.put("required", true);

        Map<String, Object> registerProp = new HashMap<>();
        registerProp.put("type", "boolean");
        registerProp.put("description", "Set true to create a new account, false to login to existing");
        registerProp.put("default", false);

        addTool("ui_login",
            "Connect to the server and login or register a new account. Use this from the login screen.",
            Map.of("username", usernameProp, "password", passwordProp, "register", registerProp),
            (exchange, args) -> {
                String username = (String) args.get("username");
                String password = (String) args.get("password");
                boolean register = Boolean.TRUE.equals(args.get("register"));

                if (!"login".equals(gameClient.getCurrentScreen())) {
                    return Mono.just(errorResult("Must be on login screen. Current screen: " + gameClient.getCurrentScreen() + ". Use ui_logout first."));
                }
                if (gameClient.isConnected()) {
                    return Mono.just(errorResult("Already connected. Use ui_logout first."));
                }

                new Thread(() -> {
                    gameClient.setLastError(null);
                    boolean ok = gameClient.connect();
                    if (ok) {
                        Platform.runLater(() -> {
                            gameClient.sendLogin(username, password, register);
                        });
                    } else {
                        gameClient.setLastError("Failed to connect to server");
                    }
                }).start();

                return Mono.just(successResult("Login request sent. Use get_screen_info to check result."));
            });

        addTool("ui_create_room",
            "Create a new game room. Must be in the lobby screen.",
            Map.of("map_id", Map.of("type", "string", "description", "Map ID (default: map_01)")),
            (exchange, args) -> {
                if (!"lobby".equals(gameClient.getCurrentScreen())) {
                    return Mono.just(errorResult("Must be in lobby. Current screen: " + gameClient.getCurrentScreen()));
                }
                String mapId = args.containsKey("map_id") ? (String) args.get("map_id") : "map_01";
                gameClient.setLastError(null);
                gameClient.createRoom(mapId);
                return Mono.just(successResult("Room creation requested for map: " + mapId));
            });

        addTool("ui_join_room",
            "Join an existing room by ID. Must be in the lobby screen.",
            Map.of("room_id", Map.of("type", "string", "description", "Room ID to join", "required", true)),
            (exchange, args) -> {
                if (!"lobby".equals(gameClient.getCurrentScreen())) {
                    return Mono.just(errorResult("Must be in lobby. Current screen: " + gameClient.getCurrentScreen()));
                }
                String roomId = (String) args.get("room_id");
                gameClient.setLastError(null);
                gameClient.joinRoom(roomId);
                return Mono.just(successResult("Join room request sent: " + roomId));
            });

        addTool("ui_start_game",
            "Start the game. Only the room host can do this. Must have at least 2 players in the room.",
            Map.of(),
            (exchange, args) -> {
                if (!"lobby".equals(gameClient.getCurrentScreen())) {
                    return Mono.just(errorResult("Must be in lobby. Current screen: " + gameClient.getCurrentScreen()));
                }
                if (gameClient.getCurrentRoomId() == null) {
                    return Mono.just(errorResult("Not in a room. Create or join a room first."));
                }
                gameClient.setLastError(null);
                gameClient.startGame();
                return Mono.just(successResult("Game start requested."));
            });

        addTool("ui_leave_room",
            "Leave the current room and return to the lobby.",
            Map.of(),
            (exchange, args) -> {
                if (!"lobby".equals(gameClient.getCurrentScreen())) {
                    return Mono.just(errorResult("Must be in lobby. Current screen: " + gameClient.getCurrentScreen()));
                }
                if (gameClient.getCurrentRoomId() == null) {
                    return Mono.just(errorResult("Not in a room."));
                }
                gameClient.leaveRoom();
                return Mono.just(successResult("Left room."));
            });

        addTool("ui_request_room_list",
            "Request the list of available rooms from the server.",
            Map.of(),
            (exchange, args) -> {
                if (!"lobby".equals(gameClient.getCurrentScreen())) {
                    return Mono.just(errorResult("Must be in lobby. Current screen: " + gameClient.getCurrentScreen()));
                }
                gameClient.requestRoomList();
                return Mono.just(successResult("Room list requested."));
            });

        addTool("ui_back_to_lobby",
            "Return to the lobby from any screen (gameover, game, etc.). Equivalent to clicking 'Volver al Lobby' on the game over screen.",
            Map.of(),
            (exchange, args) -> {
                Platform.runLater(() -> gameClient.getScreenManager().showLobby());
                return Mono.just(successResult("Returning to lobby... Use wait_for_screen lobby to confirm."));
            });

        addTool("ui_logout",
            "Disconnect from the server and return to the login screen. Only works from the lobby screen — use ui_back_to_lobby first if you are in game or gameover.",
            Map.of(),
            (exchange, args) -> {
                String screen = gameClient.getCurrentScreen();
                if (!"lobby".equals(screen)) {
                    return Mono.just(errorResult("Must be in lobby to logout. Current screen: " + screen + ". Use ui_back_to_lobby first."));
                }
                Platform.runLater(() -> gameClient.logout());
                return Mono.just(successResult("Logged out."));
            });
    }

    private void registerGameTools() {
        addTool("screenshot",
            "Capture the entire game window as a base64-encoded PNG image. Useful to see what the player sees.",
            Map.of(),
            (exchange, args) -> {
                if (!stage.isShowing()) {
                    return Mono.just(errorResult("Stage not showing yet."));
                }
                String result = captureWindowScreenshot();
                if (result == null) {
                    return Mono.just(errorResult("Screenshot failed."));
                }
                return Mono.just(successResult(result));
            });

        addTool("get_hud_info",
            "Get HUD information: health, weapon, ammo, kills, shield, upgrades, active buffs.",
            Map.of(),
            (exchange, args) -> {
                JsonObject info = new JsonObject();
                GameClientState clientState = gameClient.getClientState();
                GameState gs = clientState.getCurrentState();
                Player me = gs != null ? gs.getPlayer(clientState.getLocalPlayerId()) : null;

                if (me != null) {
                    info.addProperty("health", me.getHealth());
                    info.addProperty("max_health", me.getMaxHealth());
                    info.addProperty("shield", me.getShield());
                    info.addProperty("alive", me.isAlive());
                    info.addProperty("kills", me.getKills());
                    info.addProperty("deaths", me.getDeaths());
                    info.addProperty("current_weapon", me.getCurrentWeapon().getDisplayName());
                    info.addProperty("weapon_slot", me.getCurrentWeaponSlot() == 0 ? "primary" : "secondary");
                    if (me.getPrimaryWeapon() != null) info.addProperty("primary_weapon", me.getPrimaryWeapon().getDisplayName());
                    if (me.getSecondaryWeapon() != null) info.addProperty("secondary_weapon", me.getSecondaryWeapon().getDisplayName());
                    info.addProperty("upgrade_points", me.getUpgradePoints());

                    List<BuffUpdateMessage.ActiveBuff> buffs = gameClient.getActiveBuffs();
                    JsonArray buffsArr = new JsonArray();
                    if (buffs != null) {
                        for (BuffUpdateMessage.ActiveBuff b : buffs) {
                            JsonObject bo = new JsonObject();
                            bo.addProperty("type", b.getType());
                            bo.addProperty("remaining_ms", (int) b.getRemainingMs());
                            buffsArr.add(bo);
                        }
                    }
                    info.add("active_buffs", buffsArr);
                } else {
                    info.addProperty("status", "not_in_game");
                }

                return Mono.just(successResult(gson.toJson(info)));
            });

        addTool("get_player_position",
            "Get the player's current world position coordinates.",
            Map.of(),
            (exchange, args) -> {
                GameClientState clientState = gameClient.getClientState();
                GameState gs = clientState.getCurrentState();
                Player me = gs != null ? gs.getPlayer(clientState.getLocalPlayerId()) : null;

                if (me == null) {
                    return Mono.just(errorResult("Player not found"));
                }
                JsonObject pos = new JsonObject();
                pos.addProperty("x", me.getPosition().x());
                pos.addProperty("y", me.getPosition().y());
                return Mono.just(successResult(gson.toJson(pos)));
            });

        addTool("get_game_state",
            "Get the complete game state as seen by the client: all players, bullets, pickups, obstacles, map dimensions.",
            Map.of(),
            (exchange, args) -> {
                GameClientState clientState = gameClient.getClientState();
                GameState gs = clientState.getCurrentState();
                if (gs == null) {
                    return Mono.just(errorResult("No game state"));
                }
                return Mono.just(successResult(JsonUtil.toJson(gs)));
            });

        Map<String, Object> keyProp = new HashMap<>();
        keyProp.put("type", "string");
        keyProp.put("description", "Key: W, A, S, D, Q, E, F, SPACE, SHIFT, CLICK, ARROW_UP, ARROW_DOWN, ARROW_LEFT, ARROW_RIGHT");
        keyProp.put("required", true);

        Map<String, Object> actionProp = new HashMap<>();
        actionProp.put("type", "string");
        actionProp.put("description", "Action: press (hold down), release (let go), or click (press+release instantly)");
        actionProp.put("default", "click");

        addTool("send_key",
            "Send a key press for game controls. Use press/release for movement (WASD), click for shooting. Q=swap weapon, E/F=use skills. CLICK shoots toward where the mouse is pointing — use aim_at or mouse_move first to set aim direction.",
            Map.of("key", keyProp, "action", actionProp),
            (exchange, args) -> {
                String key = ((String) args.get("key")).toUpperCase();
                String action = args.containsKey("action") ? ((String) args.get("action")).toLowerCase() : "click";

                switch (key) {
                    case "W" -> simulateKey(KeyCode.W, action);
                    case "A" -> simulateKey(KeyCode.A, action);
                    case "S" -> simulateKey(KeyCode.S, action);
                    case "D" -> simulateKey(KeyCode.D, action);
                    case "ARROW_UP" -> simulateKey(KeyCode.UP, action);
                    case "ARROW_DOWN" -> simulateKey(KeyCode.DOWN, action);
                    case "ARROW_LEFT" -> simulateKey(KeyCode.LEFT, action);
                    case "ARROW_RIGHT" -> simulateKey(KeyCode.RIGHT, action);
                    case "SPACE" -> simulateKey(KeyCode.SPACE, action);
                    case "SHIFT" -> simulateKey(KeyCode.SHIFT, action);
                    case "Q" -> {
                        inputHandler.triggerSwapWeapon();
                        if (canvas != null) Platform.runLater(() ->
                            gameClient.update(inputHandler, canvas.getGraphicsContext2D()));
                    }
                    case "E" -> {
                        inputHandler.triggerSkillSlot0();
                        if (canvas != null) Platform.runLater(() ->
                            gameClient.update(inputHandler, canvas.getGraphicsContext2D()));
                    }
                    case "F" -> {
                        inputHandler.triggerSkillSlot1();
                        if (canvas != null) Platform.runLater(() ->
                            gameClient.update(inputHandler, canvas.getGraphicsContext2D()));
                    }
                    case "CLICK" -> {
                        inputHandler.triggerShoot();
                        if (canvas != null) Platform.runLater(() ->
                            gameClient.update(inputHandler, canvas.getGraphicsContext2D()));
                    }
                    default -> {
                        return Mono.just(errorResult("Unknown key: " + key + ". Use W/A/S/D/Q/E/F/SPACE/SHIFT/CLICK/ARROW_*"));
                    }
                }

                return Mono.just(successResult("Key " + key + " " + action));
            });
    }

    private void registerGameControlTools() {
        Map<String, Object> xProp = new HashMap<>();
        xProp.put("type", "number");
        xProp.put("description", "World X coordinate to aim at");
        xProp.put("required", true);

        Map<String, Object> yProp = new HashMap<>();
        yProp.put("type", "number");
        yProp.put("description", "World Y coordinate to aim at");
        yProp.put("required", true);

        addTool("aim_at",
            "Aim the player's weapon toward a world coordinate. Calculates the screen position using the camera and moves the virtual mouse there. Follow with send_key CLICK to shoot.",
            Map.of("x", xProp, "y", yProp),
            (exchange, args) -> {
                double targetX = ((Number) args.get("x")).doubleValue();
                double targetY = ((Number) args.get("y")).doubleValue();
                GameClientState clientState = gameClient.getClientState();
                GameState gs = clientState.getCurrentState();
                if (gs == null) return Mono.just(errorResult("No game state"));
                Player me = gs.getPlayer(clientState.getLocalPlayerId());
                if (me == null) return Mono.just(errorResult("Player not found"));
                Camera camera = gameClient.getCamera();
                double screenX = camera.worldToScreenX(targetX);
                double screenY = camera.worldToScreenY(targetY);
                inputHandler.setMousePosition(screenX, screenY);
                JsonObject result = new JsonObject();
                result.addProperty("aimed_at_x", targetX);
                result.addProperty("aimed_at_y", targetY);
                result.addProperty("screen_x", screenX);
                result.addProperty("screen_y", screenY);
                return Mono.just(successResult(gson.toJson(result)));
            });

        Map<String, Object> screenXProp = new HashMap<>();
        screenXProp.put("type", "number");
        screenXProp.put("description", "Screen X pixel position");
        screenXProp.put("required", true);

        Map<String, Object> screenYProp = new HashMap<>();
        screenYProp.put("type", "number");
        screenYProp.put("description", "Screen Y pixel position");
        screenYProp.put("required", true);

        addTool("mouse_move",
            "Move the virtual mouse to a specific screen pixel position. Use this to precisely control where the player aims.",
            Map.of("screen_x", screenXProp, "screen_y", screenYProp),
            (exchange, args) -> {
                double sx = ((Number) args.get("screen_x")).doubleValue();
                double sy = ((Number) args.get("screen_y")).doubleValue();
                inputHandler.setMousePosition(sx, sy);
                JsonObject result = new JsonObject();
                result.addProperty("screen_x", sx);
                result.addProperty("screen_y", sy);
                return Mono.just(successResult(gson.toJson(result)));
            });

        Map<String, Object> angleProp = new HashMap<>();
        angleProp.put("type", "number");
        angleProp.put("description", "Angle in degrees (0=right, 90=down, 180=left, 270=up)");
        angleProp.put("required", true);

        addTool("aim_direction",
            "Aim the player's weapon in a specific direction by angle in degrees. 0=right, 90=down, 180=left, 270=up. Useful for aiming in cardinal or diagonal directions.",
            Map.of("angle_degrees", angleProp),
            (exchange, args) -> {
                double angleDeg = ((Number) args.get("angle_degrees")).doubleValue();
                double angleRad = Math.toRadians(angleDeg);
                GameClientState clientState = gameClient.getClientState();
                GameState gs = clientState.getCurrentState();
                if (gs == null) return Mono.just(errorResult("No game state"));
                Player me = gs.getPlayer(clientState.getLocalPlayerId());
                if (me == null) return Mono.just(errorResult("Player not found"));
                Camera camera = gameClient.getCamera();
                double playerScreenX = camera.worldToScreenX(me.getPosition().x());
                double playerScreenY = camera.worldToScreenY(me.getPosition().y());
                double offset = 100;
                double mouseX = playerScreenX + Math.cos(angleRad) * offset;
                double mouseY = playerScreenY + Math.sin(angleRad) * offset;
                inputHandler.setMousePosition(mouseX, mouseY);
                JsonObject result = new JsonObject();
                result.addProperty("angle_degrees", angleDeg);
                result.addProperty("screen_x", mouseX);
                result.addProperty("screen_y", mouseY);
                return Mono.just(successResult(gson.toJson(result)));
            });
    }

    private void registerGameObservabilityTools() {
        addTool("get_other_players",
            "Get information about all other players in the game: username, position, health, weapon, shield, kills, alive, direction.",
            Map.of(),
            (exchange, args) -> {
                GameClientState clientState = gameClient.getClientState();
                GameState gs = clientState.getCurrentState();
                if (gs == null) return Mono.just(errorResult("No game state"));
                String localId = clientState.getLocalPlayerId();
                JsonArray playersArr = new JsonArray();
                for (Player p : gs.getAllPlayers()) {
                    if (p.getId().equals(localId)) continue;
                    JsonObject po = new JsonObject();
                    po.addProperty("username", p.getUsername());
                    po.addProperty("x", p.getPosition().x());
                    po.addProperty("y", p.getPosition().y());
                    po.addProperty("health", p.getHealth());
                    po.addProperty("max_health", p.getMaxHealth());
                    po.addProperty("shield", p.getShield());
                    po.addProperty("kills", p.getKills());
                    po.addProperty("alive", p.isAlive());
                    po.addProperty("direction_x", p.getDirection().x());
                    po.addProperty("direction_y", p.getDirection().y());
                    if (p.getCurrentWeapon() != null)
                        po.addProperty("weapon", p.getCurrentWeapon().getDisplayName());
                    playersArr.add(po);
                }
                JsonObject result = new JsonObject();
                result.add("players", playersArr);
                result.addProperty("count", playersArr.size());
                return Mono.just(successResult(gson.toJson(result)));
            });

        addTool("get_bullets",
            "Get all active bullets in the game: position, direction, speed, damage, owner.",
            Map.of(),
            (exchange, args) -> {
                GameClientState clientState = gameClient.getClientState();
                GameState gs = clientState.getCurrentState();
                if (gs == null) return Mono.just(errorResult("No game state"));
                JsonArray bulletsArr = new JsonArray();
                for (Bullet b : gs.getAllBullets()) {
                    JsonObject bo = new JsonObject();
                    bo.addProperty("id", b.getId());
                    bo.addProperty("x", b.getPosition().x());
                    bo.addProperty("y", b.getPosition().y());
                    bo.addProperty("direction_x", b.getDirection().x());
                    bo.addProperty("direction_y", b.getDirection().y());
                    bo.addProperty("speed", b.getSpeed());
                    bo.addProperty("damage", b.getDamage());
                    bo.addProperty("owner_id", b.getOwnerId());
                    bulletsArr.add(bo);
                }
                JsonObject result = new JsonObject();
                result.add("bullets", bulletsArr);
                result.addProperty("count", bulletsArr.size());
                return Mono.just(successResult(gson.toJson(result)));
            });

        addTool("get_map_pickups",
            "Get all pickups on the map: weapon pickups (with weapon type) and power-up pickups (with power-up type), each with position.",
            Map.of(),
            (exchange, args) -> {
                GameClientState clientState = gameClient.getClientState();
                GameState gs = clientState.getCurrentState();
                if (gs == null) return Mono.just(errorResult("No game state"));
                JsonObject result = new JsonObject();
                JsonArray weaponsArr = new JsonArray();
                for (WeaponPickup wp : gs.getWeaponPickups()) {
                    JsonObject wo = new JsonObject();
                    wo.addProperty("id", wp.getId());
                    wo.addProperty("x", wp.getPosition().x());
                    wo.addProperty("y", wp.getPosition().y());
                    wo.addProperty("weapon_type", wp.getWeaponType().name());
                    wo.addProperty("display_name", wp.getWeaponType().getDisplayName());
                    weaponsArr.add(wo);
                }
                result.add("weapon_pickups", weaponsArr);
                JsonArray powerupsArr = new JsonArray();
                for (PowerUpPickup pp : gs.getPowerUpPickups()) {
                    JsonObject po = new JsonObject();
                    po.addProperty("id", pp.getId());
                    po.addProperty("x", pp.getPosition().x());
                    po.addProperty("y", pp.getPosition().y());
                    po.addProperty("power_up_type", pp.getType().name());
                    powerupsArr.add(po);
                }
                result.add("power_up_pickups", powerupsArr);
                result.addProperty("total", weaponsArr.size() + powerupsArr.size());
                return Mono.just(successResult(gson.toJson(result)));
            });
    }

    private void registerUiSyncTools() {
        Map<String, Object> screenProp = new HashMap<>();
        screenProp.put("type", "string");
        screenProp.put("description", "Target screen: login, lobby, game, or gameover");
        screenProp.put("required", true);

        Map<String, Object> timeoutProp = new HashMap<>();
        timeoutProp.put("type", "number");
        timeoutProp.put("description", "Maximum time to wait in milliseconds (default 10000)");
        timeoutProp.put("default", 10000);

        addTool("wait_for_screen",
            "Block until the client reaches a specific screen (login/lobby/game/gameover). Useful for synchronizing UI flow after login, room join, or game start. Returns immediately if already on the target screen.",
            Map.of("screen", screenProp, "timeout_ms", timeoutProp),
            (exchange, args) -> {
                String targetScreen = (String) args.get("screen");
                long timeoutMs = args.containsKey("timeout_ms")
                    ? ((Number) args.get("timeout_ms")).longValue() : 10000;
                long deadline = System.currentTimeMillis() + timeoutMs;
                while (System.currentTimeMillis() < deadline) {
                    if (targetScreen.equals(gameClient.getCurrentScreen())) {
                        JsonObject result = new JsonObject();
                        result.addProperty("screen", targetScreen);
                        result.addProperty("elapsed_ms", timeoutMs - (deadline - System.currentTimeMillis()));
                        return Mono.just(successResult(gson.toJson(result)));
                    }
                    try { Thread.sleep(200); } catch (InterruptedException e) {
                        return Mono.just(errorResult("Interrupted"));
                    }
                }
                return Mono.just(errorResult("Timeout waiting for screen: " + targetScreen
                    + ". Current screen: " + gameClient.getCurrentScreen()));
            });
    }

    private void simulateKey(KeyCode code, String action) {
        javafx.event.EventType<javafx.scene.input.KeyEvent> type;

        switch (action) {
            case "press" -> type = javafx.scene.input.KeyEvent.KEY_PRESSED;
            case "release" -> type = javafx.scene.input.KeyEvent.KEY_RELEASED;
            default -> {
                simulateKey(code, "press");
                simulateKey(code, "release");
                return;
            }
        }

        if (canvas == null) return;
        javafx.scene.input.KeyEvent event = new javafx.scene.input.KeyEvent(
            type, code.getChar(), code.getName(), code, false, false, false, false);
        if (canvas.getScene() != null) {
            Platform.runLater(() ->
                canvas.getScene().getRoot().fireEvent(event));
        }
    }

    private String buildScreenInfo() {
        JsonObject info = new JsonObject();
        info.addProperty("screen", gameClient.getCurrentScreen());
        info.addProperty("connected", gameClient.isConnected());
        info.addProperty("username", gameClient.getCurrentUsername());
        info.addProperty("in_game", gameClient.isInGame());
        info.addProperty("room_id", gameClient.getCurrentRoomId());

        GameClientState clientState = gameClient.getClientState();
        GameState gs = clientState.getCurrentState();
        Player me = gs != null ? gs.getPlayer(clientState.getLocalPlayerId()) : null;
        if (me != null) {
            info.addProperty("health", me.getHealth());
            info.addProperty("alive", me.isAlive());
        }

        LobbyScreen lobby = screenManager.getLobbyScreen();
        if (lobby != null) {
            String lobbyRoomId = lobby.getCurrentRoomId();
            if (lobbyRoomId != null) {
                info.addProperty("lobby_room_id", lobbyRoomId);
            }
            List<RoomListResponseMessage.RoomInfo> rooms = lobby.getCachedRooms();
            JsonArray roomsArr = new JsonArray();
            if (rooms != null) {
                for (RoomListResponseMessage.RoomInfo r : rooms) {
                    JsonObject ro = new JsonObject();
                    ro.addProperty("id", r.getRoomId());
                    ro.addProperty("map", r.getMapId());
                    ro.addProperty("players", r.getPlayerCount() + "/" + r.getMaxPlayers());
                    ro.addProperty("status", r.getStatus());
                    roomsArr.add(ro);
                }
            }
            info.add("available_rooms", roomsArr);
        }

        String err = gameClient.getLastError();
        if (err == null || err.isEmpty()) {
            err = screenManager.getLastErrorMessage();
        }
        if (err != null && !err.isEmpty()) {
            info.addProperty("last_error", err);
        }

        return gson.toJson(info);
    }

    private String captureWindowScreenshot() {
        try {
            Scene scene = stage.getScene();
            if (scene == null) return null;

            WritableImage image = scene.snapshot(null);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            java.awt.image.BufferedImage bImage = SwingFXUtils.fromFXImage(image, null);
            ImageIO.write(bImage, "png", baos);

            JsonObject imgInfo = new JsonObject();
            imgInfo.addProperty("width", (int) image.getWidth());
            imgInfo.addProperty("height", (int) image.getHeight());
            imgInfo.addProperty("image_base64", Base64.getEncoder().encodeToString(baos.toByteArray()));
            imgInfo.addProperty("format", "PNG");
            return gson.toJson(imgInfo);
        } catch (Exception e) {
            System.err.println("[CLIENT-MCP] Screenshot error: " + e.getMessage());
            return null;
        }
    }

    private static McpSchema.CallToolResult successResult(String text) {
        return new McpSchema.CallToolResult(
            List.of(new McpSchema.TextContent(text)), false);
    }

    private static McpSchema.CallToolResult errorResult(String text) {
        return new McpSchema.CallToolResult(
            List.of(new McpSchema.TextContent("ERROR: " + text)), true);
    }
}
