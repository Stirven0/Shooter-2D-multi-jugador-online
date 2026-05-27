package com.aa.client.mcp.tools;

import com.aa.client.game.GameClient;
import com.aa.client.game.GameClientState;
import com.aa.client.input.InputHandler;
import com.aa.client.mcp.McpGameContext;
import com.aa.client.mcp.McpToolRegistry;
import com.aa.client.render.Camera;
import com.aa.shared.model.Player;
import com.aa.shared.state.GameState;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.util.Map;

import static com.aa.client.mcp.McpToolRegistry.success;
import static com.aa.client.mcp.McpToolRegistry.error;

import reactor.core.publisher.Mono;

public class GameControlTools {

    private final GameClient gameClient;
    private final InputHandler inputHandler;
    private final McpGameContext ctx;
    private final Gson gson;

    public GameControlTools(GameClient gameClient, InputHandler inputHandler, McpGameContext ctx) {
        this.gameClient = gameClient;
        this.inputHandler = inputHandler;
        this.ctx = ctx;
        this.gson = ctx.getGson();
    }

    public void register(McpToolRegistry registry) {
        Map<String, Object> xyProps = Map.of(
            "x", Map.of("type", "number", "description", "World X coordinate to aim at", "required", true),
            "y", Map.of("type", "number", "description", "World Y coordinate to aim at", "required", true)
        );

        registry.registerTool("aim_at",
            "Aim the player's weapon toward a world coordinate. Calculates the screen position using the camera and moves the virtual mouse there. Follow with send_key CLICK to shoot.",
            xyProps,
            (exchange, args) -> {
                double targetX = ((Number) args.get("x")).doubleValue();
                double targetY = ((Number) args.get("y")).doubleValue();
                Player me = ctx.getLocalPlayer();
                if (me == null) return Mono.just(error("Player not found"));
                Camera camera = gameClient.getCamera();
                double screenX = camera.worldToScreenX(targetX);
                double screenY = camera.worldToScreenY(targetY);
                inputHandler.setMousePosition(screenX, screenY);
                JsonObject result = new JsonObject();
                result.addProperty("aimed_at_x", targetX);
                result.addProperty("aimed_at_y", targetY);
                result.addProperty("screen_x", screenX);
                result.addProperty("screen_y", screenY);
                return Mono.just(success(gson.toJson(result)));
            });

        Map<String, Object> screenProps = Map.of(
            "screen_x", Map.of("type", "number", "description", "Screen X pixel position", "required", true),
            "screen_y", Map.of("type", "number", "description", "Screen Y pixel position", "required", true)
        );

        registry.registerTool("mouse_move",
            "Move the virtual mouse to a specific screen pixel position. Use this to precisely control where the player aims.",
            screenProps,
            (exchange, args) -> {
                double sx = ((Number) args.get("screen_x")).doubleValue();
                double sy = ((Number) args.get("screen_y")).doubleValue();
                inputHandler.setMousePosition(sx, sy);
                JsonObject result = new JsonObject();
                result.addProperty("screen_x", sx);
                result.addProperty("screen_y", sy);
                return Mono.just(success(gson.toJson(result)));
            });

        registry.registerTool("aim_direction",
            "Aim the player's weapon in a specific direction by angle in degrees. 0=right, 90=down, 180=left, 270=up.",
            Map.of("angle_degrees", Map.of("type", "number", "description", "Angle in degrees (0=right, 90=down, 180=left, 270=up)", "required", true)),
            (exchange, args) -> {
                double angleDeg = ((Number) args.get("angle_degrees")).doubleValue();
                double angleRad = Math.toRadians(angleDeg);
                Player me = ctx.getLocalPlayer();
                if (me == null) return Mono.just(error("Player not found"));
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
                return Mono.just(success(gson.toJson(result)));
            });
    }
}
