package com.aa.server.game.system;

import com.aa.server.game.PlayerInput;
import com.aa.server.game.map.GameMap;
import com.aa.shared.model.Player;
import com.aa.shared.model.PowerUpPickup;
import com.aa.shared.model.PowerUpType;
import com.aa.shared.state.GameState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class PowerUpSystem implements GameSystem {

    private static final double PICKUP_RADIUS = 25;
    private static final long RESPAWN_DELAY_MS = 15_000;
    private static final long BUFF_DURATION_MS = 8_000;
    private static final long DEBUFF_DURATION_MS = 4_000;

    private final Map<String, ActiveEffect> activeEffects = new HashMap<>();
    private final List<PowerUpPickup> pendingRespawns = new ArrayList<>();
    private long lastSpawnCheck = 0;

    public static class ActiveEffect {
        public final PowerUpType type;
        public final long expireTime;

        public ActiveEffect(PowerUpType type, long expireTime) {
            this.type = type;
            this.expireTime = expireTime;
        }
    }

    @Override
    public void update(GameState state, float deltaTime, List<PlayerInput> inputs, GameMap map) {
        long now = System.currentTimeMillis();

        // Clean expired effects
        activeEffects.entrySet().removeIf(e -> now >= e.getValue().expireTime);

        // Respawn power-ups
        if (now - lastSpawnCheck > 2000) {
            lastSpawnCheck = now;
            Iterator<PowerUpPickup> it = pendingRespawns.iterator();
            while (it.hasNext()) {
                PowerUpPickup p = it.next();
                if (now - p.getSpawnTime() >= RESPAWN_DELAY_MS) {
                    p.setSpawnTime(now);
                    List<PowerUpPickup> current = new ArrayList<>(state.getPowerUpPickups());
                    current.add(p);
                    state.setPowerUpPickups(current);
                    it.remove();
                }
            }
        }

        // Check player pickup collision
        List<PowerUpPickup> pickups = state.getPowerUpPickups();
        List<PowerUpPickup> collected = new ArrayList<>();

        for (PowerUpPickup pickup : pickups) {
            for (Player player : state.getAllPlayers()) {
                if (!player.isAlive()) continue;
                double dist = player.getPosition().distanceTo(pickup.getPosition());
                if (dist < PICKUP_RADIUS) {
                    applyEffect(player, pickup.getType(), now);
                    collected.add(pickup);
                    break;
                }
            }
        }

        if (!collected.isEmpty()) {
            List<PowerUpPickup> remaining = new ArrayList<>(pickups);
            remaining.removeAll(collected);
            state.setPowerUpPickups(remaining);
            pendingRespawns.addAll(collected);
        }
    }

    private void applyEffect(Player player, PowerUpType type, long now) {
        if (type == PowerUpType.HEALTH_PACK) {
            double newHp = Math.min(player.getHealth() + 30, player.getMaxHealth());
            player.setHealth(newHp);
            return;
        }

        long duration = type.isDebuff() ? DEBUFF_DURATION_MS : BUFF_DURATION_MS;
        activeEffects.put(player.getId() + ":" + type.name(), new ActiveEffect(type, now + duration));
    }

    public boolean hasEffect(String playerId, PowerUpType type) {
        ActiveEffect ef = activeEffects.get(playerId + ":" + type.name());
        return ef != null && System.currentTimeMillis() < ef.expireTime;
    }

    public double getSpeedMultiplier(String playerId) {
        if (hasEffect(playerId, PowerUpType.SPEED)) return 1.5;
        if (hasEffect(playerId, PowerUpType.SLOW)) return 0.6;
        return 1.0;
    }

    public double getDamageMultiplier(String playerId) {
        if (hasEffect(playerId, PowerUpType.DAMAGE_BOOST)) return 1.5;
        if (hasEffect(playerId, PowerUpType.WEAKNESS)) return 0.6;
        return 1.0;
    }

    public double getFireRateMultiplier(String playerId) {
        if (hasEffect(playerId, PowerUpType.FIRE_RATE)) return 0.6;
        return 1.0;
    }
}
