package com.aa.client.mcp;

import com.aa.client.game.GameClient;
import com.aa.client.game.GameClientState;
import com.aa.client.input.InputHandler;
import com.aa.client.render.Renderer;
import com.aa.client.ui.LobbyScreen;
import com.aa.client.ui.ScreenManager;
import com.aa.shared.message.BuffUpdateMessage;
import com.aa.shared.message.RoomListResponseMessage;
import com.aa.shared.model.Player;
import com.aa.shared.state.GameState;
import com.aa.shared.util.JsonUtil;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

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
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.robot.Robot;
import javafx.stage.Stage;

import reactor.core.publisher.Mono;

import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

        Thread thread = new Thread(() -> {
            try {
                McpJsonMapper jsonMapper = McpJsonMapper.getDefault();

                server = McpServer.async(new StdioServerTransportProvider(jsonMapper))
                    .serverInfo("multiplayer-client", "2.0.0")
                    .capabilities(McpSchema.ServerCapabilities.builder()
                        .tools(true)
                        .build())
                    .build();

                registerTools();
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

    public void stop() {
        running = false;
        if (server != null) {
            try { server.close(); } catch (Exception ignored) {}
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
        server.addTool(new McpServerFeatures.AsyncToolSpecification(tool, handler)).subscribe();
    }

    private void registerTools() {
        registerStatusTools();
        registerUiTools();
        registerGameTools();
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
            "Create a new game room. Must be in the lobby screen. Returns room ID once created.",
            Map.of("map_id", Map.of("type", "string", "description", "Map ID (default: map_01)")),
            (exchange, args) -> {
                if (!gameClient.isConnected()) {
                    return Mono.just(errorResult("Not connected. Login first."));
                }
                String mapId = args.containsKey("map_id") ? (String) args.get("map_id") : "map_01";
                gameClient.setLastError(null);
                gameClient.createRoom(mapId);
                return Mono.just(successResult("Room creation requested for map: " + mapId + ". Use get_screen_info to check result."));
            });

        addTool("ui_join_room",
            "Join an existing room by ID. Must be in the lobby screen.",
            Map.of("room_id", Map.of("type", "string", "description", "Room ID to join", "required", true)),
            (exchange, args) -> {
                if (!gameClient.isConnected()) {
                    return Mono.just(errorResult("Not connected. Login first."));
                }
                String roomId = (String) args.get("room_id");
                gameClient.setLastError(null);
                gameClient.joinRoom(roomId);
                return Mono.just(successResult("Join room request sent: " + roomId + ". Use get_screen_info to check result."));
            });

        addTool("ui_start_game",
            "Start the game. Only the room host can do this. Must have at least 2 players in the room.",
            Map.of(),
            (exchange, args) -> {
                if (!gameClient.isConnected()) {
                    return Mono.just(errorResult("Not connected."));
                }
                if (gameClient.getCurrentRoomId() == null) {
                    return Mono.just(errorResult("Not in a room. Create or join a room first."));
                }
                gameClient.setLastError(null);
                gameClient.startGame();
                return Mono.just(successResult("Game start requested. Use get_screen_info to check if game began."));
            });

        addTool("ui_leave_room",
            "Leave the current room and return to the lobby.",
            Map.of(),
            (exchange, args) -> {
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
                if (!gameClient.isConnected()) {
                    return Mono.just(errorResult("Not connected."));
                }
                gameClient.requestRoomList();
                return Mono.just(successResult("Room list requested. Use get_screen_info to see results."));
            });

        addTool("ui_logout",
            "Disconnect from the server and return to the login screen.",
            Map.of(),
            (exchange, args) -> {
                gameClient.logout();
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
            "Send a key press for game controls. Use press/release for movement (WASD), click for shooting. Q=swap weapon, E/F=use skills.",
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
