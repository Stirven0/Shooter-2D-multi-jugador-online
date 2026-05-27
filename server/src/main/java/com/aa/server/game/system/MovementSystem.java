package com.aa.server.game.system;

import com.aa.server.game.PlayerInput;
import com.aa.server.game.map.GameMap;
import com.aa.server.util.ServerConfig;
import com.aa.shared.message.MessageType;
import com.aa.shared.message.MoveMessage;
import com.aa.shared.model.Player;
import com.aa.shared.model.Vector2;
import com.aa.shared.state.GameState;

import java.util.List;

public class MovementSystem implements GameSystem {

    private PowerUpSystem powerUpSystem;
    private UpgradeSystem upgradeSystem;

    public void setPowerUpSystem(PowerUpSystem p) { this.powerUpSystem = p; }
    public void setUpgradeSystem(UpgradeSystem u) { this.upgradeSystem = u; }

    @Override
    public void update(GameState state, float deltaTime, List<PlayerInput> inputs, GameMap map) {
        for (PlayerInput input : inputs) {
            if (input.type() != MessageType.MOVE_INPUT) continue;

            MoveMessage msg = (MoveMessage) input.message();
            Player player = state.getPlayer(input.playerId());
            if (player == null || !player.isAlive()) {
                System.out.println("[MOVE] Player null or dead: " + input.playerId() + " alive=" + (player != null ? player.isAlive() : "N/A"));
                continue;
            }

            double dx = clamp(msg.getDx(), -1.0, 1.0);
            double dy = clamp(msg.getDy(), -1.0, 1.0);
            
            System.out.println("[MOVE] " + player.getUsername() + " dx=" + dx + " dy=" + dy + " from=(" + player.getPosition().x() + "," + player.getPosition().y() + ")");


            double baseSpeed = msg.isSprinting() ? ServerConfig.PLAYER_SPRINT_SPEED : ServerConfig.PLAYER_SPEED;

            double speedMult = 1.0;
            if (powerUpSystem != null) speedMult *= powerUpSystem.getSpeedMultiplier(player.getId());
            if (upgradeSystem != null) speedMult *= upgradeSystem.getSpeedMultiplier(player);

            double speed = baseSpeed * speedMult;
            double dist = speed * deltaTime;

            double mag = Math.sqrt(dx * dx + dy * dy);
            if (mag > 1.0) {
                dx /= mag;
                dy /= mag;
            }

            if (mag < 0.01) continue;

            double newX = player.getPosition().x() + dx * dist;
            double newY = player.getPosition().y() + dy * dist;

            if (map != null) {
                newX = clamp(newX, 0, map.width());
                newY = clamp(newY, 0, map.height());

                Vector2 newPos = new Vector2(newX, newY);

                if (map.collides(newPos, ServerConfig.PLAYER_RADIUS)) {
                    System.out.println("[MOVE] " + player.getUsername() + " COLLIDED at (" + newX + "," + newY + ")");
                    continue;
                }

                player.setPosition(newPos);
                System.out.println("[MOVE] " + player.getUsername() + " MOVED to (" + newX + "," + newY + ")");
            } else {
                System.out.println("[MOVE] MAP IS NULL for " + player.getUsername());
                player.setPosition(new Vector2(newX, newY));
            }
            player.setDirection(new Vector2(dx, dy).normalize());
        }
    }

    private double clamp(double val, double min, double max) {
        return Math.max(min, Math.min(max, val));
    }
}
