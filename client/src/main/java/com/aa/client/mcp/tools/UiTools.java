package com.aa.client.mcp.tools;

import com.aa.client.game.GameClient;
import com.aa.client.mcp.McpToolRegistry;
import com.aa.client.ui.ScreenManager;

import java.util.Map;

import static com.aa.client.mcp.McpToolRegistry.success;
import static com.aa.client.mcp.McpToolRegistry.error;

import javafx.application.Platform;
import reactor.core.publisher.Mono;

public class UiTools {

    private final GameClient gameClient;
    private final ScreenManager screenManager;

    public UiTools(GameClient gameClient, ScreenManager screenManager) {
        this.gameClient = gameClient;
        this.screenManager = screenManager;
    }

    public void register(McpToolRegistry registry) {
        Map<String, Object> loginProps = Map.of(
            "username", Map.of("type", "string", "description", "Username to register or login with", "required", true),
            "password", Map.of("type", "string", "description", "Password for the account", "required", true),
            "register", Map.of("type", "boolean", "description", "Set true to create a new account, false to login to existing", "default", false)
        );

        registry.registerTool("ui_login",
            "Connect to the server and login or register a new account. Use this from the login screen.",
            loginProps,
            (exchange, args) -> {
                if (!"login".equals(gameClient.getCurrentScreen())) {
                    return Mono.just(error("Must be on login screen. Current screen: " + gameClient.getCurrentScreen() + ". Use ui_logout first."));
                }
                String username = (String) args.get("username");
                String password = (String) args.get("password");
                boolean register = Boolean.TRUE.equals(args.get("register"));

                new Thread(() -> {
                    gameClient.setLastError(null);
                    if (gameClient.isConnected()) {
                        Platform.runLater(() -> gameClient.sendLogin(username, password, register));
                    } else if (gameClient.connect()) {
                        Platform.runLater(() -> gameClient.sendLogin(username, password, register));
                    } else {
                        gameClient.setLastError("Failed to connect to server");
                    }
                }).start();

                return Mono.just(success("Login request sent. Use get_screen_info to check result."));
            });

        registry.registerTool("ui_create_room",
            "Create a new game room. Must be in the lobby screen.",
            Map.of("map_id", Map.of("type", "string", "description", "Map ID (default: map_01)")),
            (exchange, args) -> {
                if (!"lobby".equals(gameClient.getCurrentScreen())) {
                    return Mono.just(error("Must be in lobby. Current screen: " + gameClient.getCurrentScreen()));
                }
                String mapId = args.containsKey("map_id") ? (String) args.get("map_id") : "map_01";
                gameClient.setLastError(null);
                gameClient.createRoom(mapId);
                return Mono.just(success("Room creation requested for map: " + mapId));
            });

        registry.registerTool("ui_join_room",
            "Join an existing room by ID. Must be in the lobby screen.",
            Map.of("room_id", Map.of("type", "string", "description", "Room ID to join", "required", true)),
            (exchange, args) -> {
                if (!"lobby".equals(gameClient.getCurrentScreen())) {
                    return Mono.just(error("Must be in lobby. Current screen: " + gameClient.getCurrentScreen()));
                }
                gameClient.setLastError(null);
                gameClient.joinRoom((String) args.get("room_id"));
                return Mono.just(success("Join room request sent: " + args.get("room_id")));
            });

        registry.registerTool("ui_start_game",
            "Start the game. Only the room host can do this. Must have at least 2 players in the room.",
            (exchange, args) -> {
                if (!"lobby".equals(gameClient.getCurrentScreen())) {
                    return Mono.just(error("Must be in lobby. Current screen: " + gameClient.getCurrentScreen()));
                }
                if (gameClient.getCurrentRoomId() == null) {
                    return Mono.just(error("Not in a room. Create or join a room first."));
                }
                gameClient.setLastError(null);
                gameClient.startGame();
                return Mono.just(success("Game start requested."));
            });

        registry.registerTool("ui_leave_room",
            "Leave the current room and return to the lobby.",
            (exchange, args) -> {
                if (!"lobby".equals(gameClient.getCurrentScreen())) {
                    return Mono.just(error("Must be in lobby. Current screen: " + gameClient.getCurrentScreen()));
                }
                if (gameClient.getCurrentRoomId() == null) {
                    return Mono.just(error("Not in a room."));
                }
                gameClient.leaveRoom();
                return Mono.just(success("Left room."));
            });

        registry.registerTool("ui_request_room_list",
            "Request the list of available rooms from the server.",
            (exchange, args) -> {
                if (!"lobby".equals(gameClient.getCurrentScreen())) {
                    return Mono.just(error("Must be in lobby. Current screen: " + gameClient.getCurrentScreen()));
                }
                gameClient.requestRoomList();
                return Mono.just(success("Room list requested."));
            });

        registry.registerTool("ui_back_to_lobby",
            "Return to the lobby from any screen (gameover, game, etc.). Equivalent to clicking 'Volver al Lobby' on the game over screen.",
            (exchange, args) -> {
                Platform.runLater(() -> gameClient.getScreenManager().showLobby());
                return Mono.just(success("Returning to lobby... Use wait_for_screen lobby to confirm."));
            });

        registry.registerTool("ui_logout",
            "Disconnect from the server and return to the login screen. Only works from the lobby screen.",
            (exchange, args) -> {
                if (!"lobby".equals(gameClient.getCurrentScreen())) {
                    return Mono.just(error("Must be in lobby to logout. Current screen: " + gameClient.getCurrentScreen() + ". Use ui_back_to_lobby first."));
                }
                Platform.runLater(() -> gameClient.logout());
                return Mono.just(success("Logged out."));
            });
    }
}
