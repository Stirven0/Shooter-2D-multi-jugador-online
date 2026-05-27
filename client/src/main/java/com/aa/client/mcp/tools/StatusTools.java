package com.aa.client.mcp.tools;

import com.aa.client.game.GameClient;
import com.aa.client.game.GameClientState;
import com.aa.client.mcp.McpGameContext;
import com.aa.client.mcp.McpToolRegistry;
import com.aa.client.ui.LobbyScreen;
import com.aa.client.ui.ScreenManager;
import com.aa.shared.message.RoomListResponseMessage;
import com.aa.shared.model.Player;
import com.aa.shared.state.GameState;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.List;

import static com.aa.client.mcp.McpToolRegistry.success;

import reactor.core.publisher.Mono;

public class StatusTools {

    private final GameClient gameClient;
    private final ScreenManager screenManager;
    private final McpGameContext ctx;
    private final Gson gson;

    public StatusTools(GameClient gameClient, ScreenManager screenManager, McpGameContext ctx) {
        this.gameClient = gameClient;
        this.screenManager = screenManager;
        this.ctx = ctx;
        this.gson = ctx.getGson();
    }

    public void register(McpToolRegistry registry) {
        registry.registerTool("get_screen_info",
            "Get the current client state: which screen is visible (login/lobby/game/gameover), connection status, username, room ID, and player count.",
            (exchange, args) -> Mono.just(success(buildScreenInfo())));

        registry.registerTool("get_last_error",
            "Get the last error message shown to the user. Returns null if no error.",
            (exchange, args) -> {
                String err = gameClient.getLastError();
                if (err == null || err.isEmpty()) err = screenManager.getLastErrorMessage();
                JsonObject result = new JsonObject();
                result.addProperty("error", err);
                result.addProperty("has_error", err != null && !err.isEmpty());
                return Mono.just(success(gson.toJson(result)));
            });
    }

    private String buildScreenInfo() {
        JsonObject info = new JsonObject();
        info.addProperty("screen", gameClient.getCurrentScreen());
        info.addProperty("connected", gameClient.isConnected());
        info.addProperty("username", gameClient.getCurrentUsername());
        info.addProperty("in_game", ctx.isInGame());
        info.addProperty("room_id", gameClient.getCurrentRoomId());

        Player me = ctx.getLocalPlayer();
        if (me != null) {
            info.addProperty("health", me.getHealth());
            info.addProperty("alive", me.isAlive());
        }

        LobbyScreen lobby = screenManager.getLobbyScreen();
        if (lobby != null) {
            String lobbyRoomId = lobby.getCurrentRoomId();
            if (lobbyRoomId != null) info.addProperty("lobby_room_id", lobbyRoomId);
            List<RoomListResponseMessage.RoomInfo> rooms = lobby.getCachedRooms();
            if (rooms != null) {
                JsonArray roomsArr = new JsonArray();
                for (RoomListResponseMessage.RoomInfo r : rooms) {
                    JsonObject ro = new JsonObject();
                    ro.addProperty("id", r.getRoomId());
                    ro.addProperty("map", r.getMapId());
                    ro.addProperty("players", r.getPlayerCount() + "/" + r.getMaxPlayers());
                    ro.addProperty("status", r.getStatus());
                    roomsArr.add(ro);
                }
                info.add("available_rooms", roomsArr);
            }
        }

        String err = gameClient.getLastError();
        if (err == null || err.isEmpty()) err = screenManager.getLastErrorMessage();
        if (err != null && !err.isEmpty()) info.addProperty("last_error", err);

        return gson.toJson(info);
    }
}
