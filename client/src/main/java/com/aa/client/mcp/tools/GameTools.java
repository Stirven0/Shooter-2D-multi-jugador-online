package com.aa.client.mcp.tools;

import com.aa.client.game.GameClient;
import com.aa.client.game.GameClientState;
import com.aa.client.input.InputHandler;
import com.aa.client.mcp.McpGameContext;
import com.aa.client.mcp.McpToolRegistry;
import com.aa.shared.message.BuffUpdateMessage;
import com.aa.shared.model.Player;
import com.aa.shared.state.GameState;
import com.aa.shared.util.JsonUtil;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import static com.aa.client.mcp.McpToolRegistry.success;
import static com.aa.client.mcp.McpToolRegistry.error;

import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;

import reactor.core.publisher.Mono;

import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.List;
import java.util.Map;

public class GameTools {

    private final GameClient gameClient;
    private final InputHandler inputHandler;
    private final Stage stage;
    private final McpGameContext ctx;
    private final Gson gson;
    private Canvas canvas;

    public GameTools(GameClient gameClient, InputHandler inputHandler, Stage stage, McpGameContext ctx) {
        this.gameClient = gameClient;
        this.inputHandler = inputHandler;
        this.stage = stage;
        this.ctx = ctx;
        this.gson = ctx.getGson();
    }

    public void setCanvas(Canvas canvas) {
        this.canvas = canvas;
    }

    public void register(McpToolRegistry registry) {
        registry.registerTool("screenshot",
            "Capture the entire game window as a base64-encoded PNG image. Useful to see what the player sees.",
            (exchange, args) -> {
                if (!stage.isShowing()) return Mono.just(error("Stage not showing yet."));
                String result = captureWindowScreenshot();
                return result != null
                    ? Mono.just(success(result))
                    : Mono.just(error("Screenshot failed."));
            });

        registry.registerTool("get_hud_info",
            "Get HUD information: health, weapon, ammo, kills, shield, upgrades, active buffs.",
            (exchange, args) -> {
                JsonObject info = new JsonObject();
                Player me = ctx.getLocalPlayer();
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
                return Mono.just(success(gson.toJson(info)));
            });

        registry.registerTool("get_player_position",
            "Get the player's current world position coordinates.",
            (exchange, args) -> {
                Player me = ctx.getLocalPlayer();
                if (me == null) return Mono.just(error("Player not found"));
                JsonObject pos = new JsonObject();
                pos.addProperty("x", me.getPosition().x());
                pos.addProperty("y", me.getPosition().y());
                return Mono.just(success(gson.toJson(pos)));
            });

        registry.registerTool("get_game_state",
            "Get the complete game state as seen by the client: all players, bullets, pickups, obstacles, map dimensions.",
            (exchange, args) -> {
                GameState gs = ctx.getGameState();
                if (gs == null) return Mono.just(error("No game state"));
                return Mono.just(success(JsonUtil.toJson(gs)));
            });

        Map<String, Object> keyProps = Map.of(
            "key", Map.of("type", "string", "description", "Key: W, A, S, D, Q, E, F, SPACE, SHIFT, CLICK, ARROW_UP, ARROW_DOWN, ARROW_LEFT, ARROW_RIGHT", "required", true),
            "action", Map.of("type", "string", "description", "Action: press (hold down), release (let go), or click (press+release instantly)", "default", "click")
        );

        registry.registerTool("send_key",
            "Send a key press for game controls. Use press/release for movement (WASD), click for shooting. Q=swap weapon, E/F=use skills. CLICK shoots toward where the mouse is pointing.",
            keyProps,
            (exchange, args) -> {
                String key = ((String) args.get("key")).toUpperCase();
                String action = args.containsKey("action") ? ((String) args.get("action")).toLowerCase() : "click";

                boolean handled = dispatchKeyEvent(key, action);
                if (!handled) {
                    return Mono.just(error("Unknown key: " + key + ". Use W/A/S/D/Q/E/F/SPACE/SHIFT/CLICK/ARROW_*"));
                }

                if (canvas != null) Platform.runLater(() -> gameClient.update(inputHandler, canvas.getGraphicsContext2D()));
                return Mono.just(success("Key " + key + " " + action));
            });
    }

    private boolean dispatchKeyEvent(String key, String action) {
        switch (key) {
            case "W" -> { simulateKey(KeyCode.W, action); return true; }
            case "A" -> { simulateKey(KeyCode.A, action); return true; }
            case "S" -> { simulateKey(KeyCode.S, action); return true; }
            case "D" -> { simulateKey(KeyCode.D, action); return true; }
            case "ARROW_UP" -> { simulateKey(KeyCode.UP, action); return true; }
            case "ARROW_DOWN" -> { simulateKey(KeyCode.DOWN, action); return true; }
            case "ARROW_LEFT" -> { simulateKey(KeyCode.LEFT, action); return true; }
            case "ARROW_RIGHT" -> { simulateKey(KeyCode.RIGHT, action); return true; }
            case "SPACE" -> { simulateKey(KeyCode.SPACE, action); return true; }
            case "SHIFT" -> { simulateKey(KeyCode.SHIFT, action); return true; }
            case "Q" -> { inputHandler.triggerSwapWeapon(); return true; }
            case "E" -> { inputHandler.triggerSkillSlot0(); return true; }
            case "F" -> { inputHandler.triggerSkillSlot1(); return true; }
            case "CLICK" -> { inputHandler.triggerShoot(); return true; }
            default -> { return false; }
        }
    }

    private void simulateKey(KeyCode code, String action) {
        switch (action) {
            case "press" -> inputHandler.addKey(code);
            case "release" -> inputHandler.removeKey(code);
            default -> {
                inputHandler.addKey(code);
                inputHandler.removeKey(code);
            }
        }
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
}
