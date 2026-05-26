package com.aa.mcp;

import com.aa.shared.message.*;
import com.aa.shared.state.GameState;
import com.aa.shared.util.JsonUtil;
import com.google.gson.JsonObject;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BridgeGameClient extends WebSocketClient {

    private final McpBridge bridge;
    private volatile GameState gameState;
    private volatile String playerId;
    private volatile String roomId;
    private volatile boolean loggedIn = false;
    private volatile boolean inGame = false;
    private final Map<String, Object> pendingResponses = new ConcurrentHashMap<>();

    private MoveMessage lastMoveMessage = new MoveMessage(0, 0, false);
    private long lastMoveSend = 0;
    private static final long MOVE_INTERVAL_MS = 50;

    public BridgeGameClient(URI uri, McpBridge bridge) {
        super(uri);
        this.bridge = bridge;
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        System.out.println("[BRIDGE] Connected to server");
        bridge.setConnected(true);
    }

    @Override
    public void onMessage(String message) {
        try {
            String type = JsonUtil.extractField(message, "type");
            if (type == null) return;

            MessageType mt = MessageType.valueOf(type);
            switch (mt) {
                case LOGIN_RESPONSE -> {
                    LoginResponseMessage resp = JsonUtil.fromJson(message, LoginResponseMessage.class);
                    if (resp.isSuccess()) {
                        playerId = resp.getUserId();
                        loggedIn = true;
                        System.out.println("[BRIDGE] Logged in as: " + resp.getUsername() + " (id=" + playerId + ")");
                    } else {
                        System.err.println("[BRIDGE] Login failed: " + resp.getErrorMessage());
                    }
                }
                case ROOM_CREATED -> {
                    RoomCreatedMessage rcm = JsonUtil.fromJson(message, RoomCreatedMessage.class);
                    roomId = rcm.getRoomId();
                    System.out.println("[BRIDGE] Room created: " + roomId);
                }
                case ROOM_UPDATED -> {
                    // Room updated - ignore
                }
                case GAME_STATE -> {
                    GameStateMessage gsm = JsonUtil.fromJson(message, GameStateMessage.class);
                    gameState = gsm.getGameState();
                    if (!inGame) {
                        inGame = true;
                        System.out.println("[BRIDGE] Game started!");
                    }
                }
                case GAME_END -> {
                    GameEndMessage gem = JsonUtil.fromJson(message, GameEndMessage.class);
                    System.out.println("[BRIDGE] Game ended. Winner: " + gem.getWinnerUsername());
                    inGame = false;
                    gameState = null;
                }
                case ERROR -> {
                    ErrorMessage err = JsonUtil.fromJson(message, ErrorMessage.class);
                    System.err.println("[BRIDGE] Server error: " + err.getMessage());
                }
                default -> {}
            }
        } catch (Exception e) {
            System.err.println("[BRIDGE] Error parsing message: " + e.getMessage());
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        System.out.println("[BRIDGE] Disconnected: " + reason);
        bridge.setConnected(false);
        loggedIn = false;
        inGame = false;
    }

    @Override
    public void onError(Exception ex) {
        System.err.println("[BRIDGE] Error: " + ex.getMessage());
    }

    public void sendLogin(String username, String password) {
        send(JsonUtil.toJson(new LoginMessage(username, password, true)));
    }

    public void createAndJoinRoom() {
        JsonObject createRoom = new JsonObject();
        createRoom.addProperty("type", "CREATE_ROOM");
        createRoom.addProperty("mapId", "map_01");
        send(createRoom.toString());
    }

    public void sendMove(double dx, double dy) {
        long now = System.currentTimeMillis();
        if (now - lastMoveSend < MOVE_INTERVAL_MS) return;
        lastMoveSend = now;
        lastMoveMessage.setDx(dx);
        lastMoveMessage.setDy(dy);
        send(JsonUtil.toJson(lastMoveMessage));
    }

    public void sendShoot(double angle) {
        send(JsonUtil.toJson(new ShootMessage(angle)));
    }

    public void sendSwapWeapon() {
        send(JsonUtil.toJson(new SwapWeaponMessage()));
    }

    public void sendUseSkill(int slot) {
        send(JsonUtil.toJson(new UseSkillMessage(slot)));
    }

    public GameState getGameState() { return gameState; }
    public String getPlayerId() { return playerId; }
    public boolean isLoggedIn() { return loggedIn; }
    public boolean isInGame() { return inGame; }
}
