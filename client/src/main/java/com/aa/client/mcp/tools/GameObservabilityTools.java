package com.aa.client.mcp.tools;

import com.aa.client.mcp.McpGameContext;
import com.aa.client.mcp.McpToolRegistry;
import com.aa.shared.model.Bullet;
import com.aa.shared.model.Player;
import com.aa.shared.model.PowerUpPickup;
import com.aa.shared.model.WeaponPickup;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import static com.aa.client.mcp.McpToolRegistry.success;
import static com.aa.client.mcp.McpToolRegistry.error;

import reactor.core.publisher.Mono;

public class GameObservabilityTools {

    private final McpGameContext ctx;
    private final Gson gson;

    public GameObservabilityTools(McpGameContext ctx) {
        this.ctx = ctx;
        this.gson = ctx.getGson();
    }

    public void register(McpToolRegistry registry) {
        registry.registerTool("get_other_players",
            "Get information about all other players in the game: username, position, health, weapon, shield, kills, alive, direction.",
            (exchange, args) -> {
                if (ctx.getGameState() == null) return Mono.just(error("No game state"));
                JsonArray playersArr = new JsonArray();
                for (Player p : ctx.getOtherPlayers()) {
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
                    if (p.getCurrentWeapon() != null) po.addProperty("weapon", p.getCurrentWeapon().getDisplayName());
                    playersArr.add(po);
                }
                JsonObject result = new JsonObject();
                result.add("players", playersArr);
                result.addProperty("count", playersArr.size());
                return Mono.just(success(gson.toJson(result)));
            });

        registry.registerTool("get_bullets",
            "Get all active bullets in the game: position, direction, speed, damage, owner.",
            (exchange, args) -> {
                if (ctx.getGameState() == null) return Mono.just(error("No game state"));
                JsonArray bulletsArr = new JsonArray();
                for (Bullet b : ctx.getBullets()) {
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
                return Mono.just(success(gson.toJson(result)));
            });

        registry.registerTool("get_map_pickups",
            "Get all pickups on the map: weapon pickups (with weapon type) and power-up pickups (with power-up type), each with position.",
            (exchange, args) -> {
                if (ctx.getGameState() == null) return Mono.just(error("No game state"));
                JsonObject result = new JsonObject();
                JsonArray weaponsArr = new JsonArray();
                for (WeaponPickup wp : ctx.getWeaponPickups()) {
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
                for (PowerUpPickup pp : ctx.getPowerUpPickups()) {
                    JsonObject po = new JsonObject();
                    po.addProperty("id", pp.getId());
                    po.addProperty("x", pp.getPosition().x());
                    po.addProperty("y", pp.getPosition().y());
                    po.addProperty("power_up_type", pp.getType().name());
                    powerupsArr.add(po);
                }
                result.add("power_up_pickups", powerupsArr);
                result.addProperty("total", weaponsArr.size() + powerupsArr.size());
                return Mono.just(success(gson.toJson(result)));
            });
    }
}
