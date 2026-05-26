package com.aa.mcp;

import com.aa.shared.message.*;
import com.aa.shared.model.Player;
import com.aa.shared.model.PowerUpPickup;
import com.aa.shared.model.SkillSlot;
import com.aa.shared.model.Vector2;
import com.aa.shared.model.WeaponPickup;
import com.aa.shared.state.GameState;
import com.aa.shared.util.JsonUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpAsyncServer;
import io.modelcontextprotocol.server.McpAsyncServerExchange;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import reactor.core.publisher.Mono;

public class McpBridge {

    private BridgeGameClient gameClient;
    private volatile boolean connected = false;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public static void main(String[] args) throws Exception {
        String host = "localhost";
        int port = 8080;
        String username = "ai_player";
        String password = "ai_pass";

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--host" -> host = args[++i];
                case "--port" -> port = Integer.parseInt(args[++i]);
                case "--username" -> username = args[++i];
                case "--password" -> password = args[++i];
            }
        }

        McpBridge bridge = new McpBridge();
        bridge.start(host, port, username, password);
    }

    public void start(String host, int port, String username, String password) throws Exception {
        System.out.println("[MCP] Starting MCP Bridge...");

        gameClient = new BridgeGameClient(URI.create("ws://" + host + ":" + port), this);
        gameClient.connectBlocking();
        gameClient.sendLogin(username, password);

        Thread.sleep(2000);

        if (!connected) {
            System.err.println("[MCP] Failed to connect/login to game server");
            System.exit(1);
        }

        System.out.println("[MCP] Connected and logged in as: " + username);
        gameClient.createAndJoinRoom();
        startAiLoop();

        var jsonMapper = new JacksonMcpJsonMapper(new ObjectMapper());
        var transport = new StdioServerTransportProvider(jsonMapper);

        McpAsyncServer server = McpServer.async(transport)
            .serverInfo("multiplayer-bridge", "1.0.0")
            .capabilities(McpSchema.ServerCapabilities.builder().tools(false).build())
            .build();

        registerTools(server);

        System.out.println("[MCP] MCP Server ready on stdio");
        Thread.currentThread().join();
    }

    private void startAiLoop() {
        Thread ai = new Thread(() -> {
            while (true) {
                try { Thread.sleep(50); } catch (InterruptedException e) { break; }
                if (!gameClient.isInGame()) continue;
                GameState state = gameClient.getGameState();
                if (state == null) continue;
                Player me = state.getPlayer(gameClient.getPlayerId());
                if (me == null || !me.isAlive()) continue;

                Player target = null;
                double minDist = Double.MAX_VALUE;
                for (Player p : state.getAllPlayers()) {
                    if (p.getId().equals(gameClient.getPlayerId()) || !p.isAlive()) continue;
                    double d = me.getPosition().distanceTo(p.getPosition());
                    if (d < minDist) { minDist = d; target = p; }
                }
                if (target == null) continue;

                double dx = target.getPosition().x() - me.getPosition().x();
                double dy = target.getPosition().y() - me.getPosition().y();
                double len = Math.sqrt(dx * dx + dy * dy);
                if (len > 0) {
                    gameClient.sendMove(dx / len, dy / len);
                    if (len < 350) {
                        gameClient.sendShoot(Math.atan2(dy, dx));
                    }
                }
            }
        }, "ai-loop");
        ai.setDaemon(true);
        ai.start();
    }

    @SuppressWarnings("unchecked")
    private void registerTools(McpAsyncServer server) {
        tool(server, "get_state", "Get the complete game state including all players, positions, health, weapons, bullets, and pickups",
            (exchange, args) -> {
                GameState state = gameClient.getGameState();
                if (state == null) return Mono.just(error("Not connected to game"));
                return Mono.just(success(gson.toJson(stateToMap(state))));
            });

        tool(server, "get_map", "Get map information including dimensions and pickup locations",
            (exchange, args) -> {
                GameState state = gameClient.getGameState();
                if (state == null) return Mono.just(error("Not connected"));
                JsonObject mapInfo = new JsonObject();
                mapInfo.addProperty("width", state.getMapWidth());
                mapInfo.addProperty("height", state.getMapHeight());
                JsonArray weapons = new JsonArray();
                if (state.getWeaponPickups() != null) {
                    for (WeaponPickup wp : state.getWeaponPickups()) {
                        JsonObject w = new JsonObject();
                        w.addProperty("id", wp.getId());
                        w.add("position", posToJson(wp.getPosition()));
                        w.addProperty("weapon", wp.getWeaponType().name());
                        weapons.add(w);
                    }
                }
                mapInfo.add("weapon_pickups", weapons);
                JsonArray powerups = new JsonArray();
                if (state.getPowerUpPickups() != null) {
                    for (PowerUpPickup pp : state.getPowerUpPickups()) {
                        JsonObject p = new JsonObject();
                        p.addProperty("id", pp.getId());
                        p.add("position", posToJson(pp.getPosition()));
                        p.addProperty("type", pp.getType().name());
                        powerups.add(p);
                    }
                }
                mapInfo.add("powerup_pickups", powerups);
                return Mono.just(success(gson.toJson(mapInfo)));
            });

        tool(server, "move", "Move the player in a direction. dx/dy range -1 to 1",
            Map.of("dx", "number", "dy", "number"), List.of("dx", "dy"),
            (exchange, args) -> {
                double dx = ((Number) args.get("dx")).doubleValue();
                double dy = ((Number) args.get("dy")).doubleValue();
                dx = Math.max(-1, Math.min(1, dx));
                dy = Math.max(-1, Math.min(1, dy));
                gameClient.sendMove(dx, dy);
                return Mono.just(success("Moved to (" + dx + ", " + dy + ")"));
            });

        tool(server, "shoot", "Shoot in a direction. Angle in degrees (0=right, 90=down, 180=left, 270=up)",
            Map.of("angle", "number"), List.of("angle"),
            (exchange, args) -> {
                double angle = ((Number) args.get("angle")).doubleValue();
                gameClient.sendShoot(Math.toRadians(angle));
                return Mono.just(success("Shot at angle " + angle));
            });

        tool(server, "swap_weapon", "Switch between primary and secondary weapon slots",
            (exchange, args) -> {
                gameClient.sendSwapWeapon();
                return Mono.just(success("Swapped weapon"));
            });

        tool(server, "use_skill", "Activate a player skill. Slot 0 = [E] key, Slot 1 = [F] key",
            Map.of("slot", "number"), List.of("slot"),
            (exchange, args) -> {
                int slot = ((Number) args.get("slot")).intValue();
                if (slot < 0 || slot > 1) return Mono.just(error("Slot must be 0 or 1"));
                gameClient.sendUseSkill(slot);
                return Mono.just(success("Activated skill slot " + slot));
            });

        tool(server, "get_inventory", "Get current weapons, active skills, health, kills, and buffs",
            (exchange, args) -> {
                GameState state = gameClient.getGameState();
                Player me = state != null ? state.getPlayer(gameClient.getPlayerId()) : null;
                if (me == null) return Mono.just(error("Player not found"));
                JsonObject inv = new JsonObject();
                inv.addProperty("primary", me.getPrimaryWeapon().getDisplayName());
                if (me.getSecondaryWeapon() != null) inv.addProperty("secondary", me.getSecondaryWeapon().getDisplayName());
                inv.addProperty("current_slot", me.getCurrentWeaponSlot() == 0 ? "primary" : "secondary");
                inv.addProperty("health", me.getHealth());
                inv.addProperty("shield", me.getShield());
                inv.addProperty("kills", me.getKills());
                inv.addProperty("upgrade_points", me.getUpgradePoints());
                JsonArray skills = new JsonArray();
                if (me.getSkillSlots() != null) {
                    for (int i = 0; i < me.getSkillSlots().length; i++) {
                        SkillSlot slot = me.getSkillSlots()[i];
                        if (slot != null && slot.getSkill() != null) {
                            JsonObject s = new JsonObject();
                            s.addProperty("slot", i);
                            s.addProperty("name", slot.getSkill().getDisplayName());
                            s.addProperty("cooldown", String.format("%.1f", slot.getCooldownRemaining()));
                            s.addProperty("active", slot.isActive());
                            skills.add(s);
                        }
                    }
                }
                inv.add("skills", skills);
                return Mono.just(success(gson.toJson(inv)));
            });
    }

    private void tool(McpAsyncServer server, String name, String description,
                      BiFunction<McpAsyncServerExchange, Map<String, Object>, Mono<McpSchema.CallToolResult>> handler) {
        McpSchema.Tool schema = McpSchema.Tool.builder()
            .name(name).description(description)
            .inputSchema(new McpSchema.JsonSchema("object", Map.of(), List.of(), false, Map.of(), Map.of()))
            .build();
        server.addTool(new McpServerFeatures.AsyncToolSpecification(schema, handler));
    }

    private void tool(McpAsyncServer server, String name, String description,
                      Map<String, String> paramTypes, List<String> required,
                      BiFunction<McpAsyncServerExchange, Map<String, Object>, Mono<McpSchema.CallToolResult>> handler) {
        Map<String, Object> properties = new java.util.HashMap<>();
        for (var entry : paramTypes.entrySet()) {
            Map<String, Object> prop = new java.util.HashMap<>();
            prop.put("type", entry.getValue());
            properties.put(entry.getKey(), prop);
        }
        McpSchema.Tool schema = McpSchema.Tool.builder()
            .name(name).description(description)
            .inputSchema(new McpSchema.JsonSchema("object", properties, required, false, Map.of(), Map.of()))
            .build();
        server.addTool(new McpServerFeatures.AsyncToolSpecification(schema, handler));
    }

    private McpSchema.CallToolResult success(String text) {
        return new McpSchema.CallToolResult(List.of(new McpSchema.TextContent(text)), false);
    }

    private McpSchema.CallToolResult error(String text) {
        return new McpSchema.CallToolResult(List.of(new McpSchema.TextContent("ERROR: " + text)), true);
    }

    private Map<String, Object> stateToMap(GameState state) {
        JsonObject obj = new JsonObject();
        obj.addProperty("tick", state.getTick());
        obj.addProperty("status", state.getStatus() != null ? state.getStatus().name() : "?");
        obj.addProperty("map_width", state.getMapWidth());
        obj.addProperty("map_height", state.getMapHeight());
        JsonArray playersArray = new JsonArray();
        if (state.getAllPlayers() != null) {
            for (Player p : state.getAllPlayers()) {
                JsonObject po = new JsonObject();
                po.addProperty("id", p.getId());
                po.addProperty("username", p.getUsername());
                po.add("position", posToJson(p.getPosition()));
                po.addProperty("health", p.getHealth());
                po.addProperty("alive", p.isAlive());
                po.addProperty("kills", p.getKills());
                po.addProperty("weapon", p.getCurrentWeapon().getDisplayName());
                playersArray.add(po);
            }
        }
        obj.add("players", playersArray);
        return Map.of("state", gson.toJson(obj));
    }

    private static JsonArray posToJson(Vector2 pos) {
        JsonArray arr = new JsonArray();
        arr.add(pos.x());
        arr.add(pos.y());
        return arr;
    }

    public void setConnected(boolean c) { this.connected = c; }
    public boolean isConnected() { return connected; }
    public BridgeGameClient getGameClient() { return gameClient; }
}
