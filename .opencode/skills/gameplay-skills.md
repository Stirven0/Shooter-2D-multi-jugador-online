# Gameplay Skills Skill

## Description
Adding and modifying in-game player active abilities (skills), including their effects, cooldowns, and HUD display.

## When to use
- Adding a new player skill type
- Modifying skill cooldowns, duration, or effects
- Debugging skill activation or HUD display
- Adding skill pickups to the map

## Skill System Architecture

- Skills defined in `shared/.../model/PlayerSkill.java` enum: cooldown, duration, category, displayName, description
- `server/.../game/system/SkillSystem.java` handles activation, cooldowns, duration tracking, EMP dispel
- `USE_SKILL` message type in protocol (`MessageType.USE_SKILL`)
- `UseSkillMessage(slotIndex)` sent from client
- Client binds E (slot 0) and F (slot 1) in `InputHandler.java`
- 2 random skills assigned at spawn via `SkillSystem.createDefaultSkills()`
- Known issue: ADRENALINE sets timer but MovementSystem/ShootingSystem don't check it — sin efecto real

## Existing skills reference

| Skill | CD | Duration | Category | Effect |
|-------|----|---------|----------|--------|
| DASH | 5s | 0 | MOVEMENT | 150px lunge forward in direction vector |
| SHIELD_BURST | 15s | 3s | DEFENSE | Shield = 9999, removed after duration |
| HEAL | 20s | 0 | UTILITY | Instant +50 HP |
| ADRENALINE | 18s | 5s | BUFF | Timer only — multipliers not wired |
| EMP | 25s | 0 | OFFENSE | Remove all buffs from enemies within 300px |
| STEALTH | 20s | 4s | UTILITY | Visual only — no mechanical effect server-side |

## Steps for adding a new skill

### 1. Define skill type in `PlayerSkill.java`
```java
DASH(5.0, 0, SkillCategory.MOVEMENT, "Dash", "Lunge forward"),
```

### 2. Implement effect in `SkillSystem.activateSkill()`

**CRÍTICO**: `Vector2` es un record inmutable. `getPosition()` devuelve el objeto, no una referencia mutable. Para mover al jugador:
```java
case DASH -> {
    Vector2 dir = player.getDirection();
    // CORRECTO: setPosition con nuevo Vector2
    player.setPosition(player.getPosition().add(dir.multiply(150)));
}
```

### 3. Add deactivation logic in `SkillSystem.deactivateEffect()` if needed
For duration-based skills that need cleanup (e.g., removing shield):
```java
case SHIELD_BURST -> player.setShield(0);
```

### 4. Update HUD in `Renderer.java` / `HudRenderer.java` (tile-engine)
Add icon and cooldown display for the new skill.

### 5. If skill has a pickup, add to `GameInstance.spawnInitialPickups()`

### 6. Test
```bash
mvn clean install -DskipTests && java -jar server/target/server-1.0-SNAPSHOT.jar
```
