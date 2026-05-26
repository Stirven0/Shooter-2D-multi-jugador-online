package com.aa.server.game.engine;

import com.aa.server.game.PlayerInput;
import com.aa.server.game.map.GameMap;
import com.aa.server.game.system.*;
import com.aa.shared.state.GameState;

import java.util.ArrayList;
import java.util.List;

public class GameEngine {
    private final List<GameSystem> systems = new ArrayList<>();
    private final PowerUpSystem powerUpSystem;
    private final UpgradeSystem upgradeSystem;
    private final MovementSystem movementSystem;
    private final DamageSystem damageSystem;

    public GameEngine() {
        this.upgradeSystem = new UpgradeSystem();
        this.powerUpSystem = new PowerUpSystem();
        this.movementSystem = new MovementSystem();
        this.damageSystem = new DamageSystem();

        movementSystem.setPowerUpSystem(powerUpSystem);
        movementSystem.setUpgradeSystem(upgradeSystem);
        damageSystem.setUpgradeSystem(upgradeSystem);

        systems.add(movementSystem);
        systems.add(new ShootingSystem());
        systems.add(new CollisionSystem());
        systems.add(damageSystem);
        systems.add(powerUpSystem);
        systems.add(new SkillSystem());
        systems.add(upgradeSystem);
    }

    public void update(GameState state, float deltaTime, List<PlayerInput> inputs, GameMap map) {
        for (GameSystem system : systems) {
            system.update(state, deltaTime, inputs, map);
        }
    }

    public PowerUpSystem getPowerUpSystem() { return powerUpSystem; }
    public UpgradeSystem getUpgradeSystem() { return upgradeSystem; }
    public DamageSystem getDamageSystem() { return damageSystem; }
}
