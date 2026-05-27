package com.aa.client.mcp.tools;

import com.aa.client.game.GameClient;
import com.aa.client.mcp.McpToolRegistry;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.util.Map;

import static com.aa.client.mcp.McpToolRegistry.success;
import static com.aa.client.mcp.McpToolRegistry.error;

import reactor.core.publisher.Mono;

public class UiSyncTools {

    private final GameClient gameClient;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public UiSyncTools(GameClient gameClient) {
        this.gameClient = gameClient;
    }

    public void register(McpToolRegistry registry) {
        registry.registerTool("wait_for_screen",
            "Block until the client reaches a specific screen (login/lobby/game/gameover). Useful for synchronizing UI flow after login, room join, or game start. Returns immediately if already on the target screen.",
            Map.of(
                "screen", Map.of("type", "string", "description", "Target screen: login, lobby, game, or gameover", "required", true),
                "timeout_ms", Map.of("type", "number", "description", "Maximum time to wait in milliseconds (default 10000)", "default", 10000)
            ),
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
                        return Mono.just(success(gson.toJson(result)));
                    }
                    try { Thread.sleep(200); } catch (InterruptedException e) {
                        return Mono.just(error("Interrupted"));
                    }
                }
                return Mono.just(error("Timeout waiting for screen: " + targetScreen
                    + ". Current screen: " + gameClient.getCurrentScreen()));
            });
    }
}
