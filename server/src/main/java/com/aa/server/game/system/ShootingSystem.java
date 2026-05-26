package com.aa.server.game.system;

import com.aa.server.game.PlayerInput;
import com.aa.server.game.map.GameMap;
import com.aa.shared.message.MessageType;
import com.aa.shared.message.ShootMessage;
import com.aa.shared.model.Bullet;
import com.aa.shared.model.Player;
import com.aa.shared.model.Vector2;
import com.aa.shared.model.WeaponType;
import com.aa.shared.state.GameState;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class ShootingSystem implements GameSystem {
    private final Map<String, Long> lastShotTime = new HashMap<>();
    private final AtomicInteger bulletCounter = new AtomicInteger(0);

    @Override
    public void update(GameState state, float deltaTime, List<PlayerInput> inputs, GameMap map) {
        long now = System.currentTimeMillis();

        for (PlayerInput input : inputs) {
            if (input.type() != MessageType.SHOOT_INPUT) continue;

            String pid = input.playerId();
            Player player = state.getPlayer(pid);
            if (player == null || !player.isAlive()) continue;

            WeaponType weapon = player.getCurrentWeapon();
            double fireRate = weapon.getFireRateMs();

            Long last = lastShotTime.get(pid);
            if (last != null && (now - last) < fireRate) continue;

            ShootMessage msg = (ShootMessage) input.message();
            double baseAngle = msg.getAngle();

            for (int i = 0; i < weapon.getPellets(); i++) {
                double angle = baseAngle;
                if (weapon.getSpreadDeg() > 0) {
                    double spreadRad = Math.toRadians(weapon.getSpreadDeg());
                    angle += (Math.random() - 0.5) * 2 * spreadRad;
                }

                Vector2 dir = new Vector2(Math.cos(angle), Math.sin(angle));
                Vector2 spawn = player.getPosition().add(dir.multiply(20));

                String bulletId = pid + "_b_" + bulletCounter.incrementAndGet();
                Bullet bullet = new Bullet(
                    bulletId, spawn, dir, weapon.getBulletSpeed(),
                    pid, weapon.getDamage()
                );
                bullet.setMaxLifetime((long) weapon.getBulletLifetimeMs());
                state.addBullet(bullet);
            }

            lastShotTime.put(pid, now);
        }
    }
}
