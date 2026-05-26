# Gameplay Skills Skill

## Description
Adding and modifying in-game player active abilities (skills), including their effects, cooldowns, and HUD display.

## When to use
- Adding a new player skill type
- Modifying skill cooldowns or effects
- Debugging skill activation or HUD display
- Adding skill pickups to the map

## Skill System Architecture

- Skills are defined in `PlayerSkill.java` enum (cooldown, duration, type)
- `SkillSystem.java` handles activation, cooldowns, and effects
- `USE_SKILL` message type in the protocol
- Client sends skill activation via E (slot 0) and F (slot 1)

## Steps for adding a new skill

### 1. Define skill type in `PlayerSkill.java`
```java
DASH(5.0, 0, SkillCategory.MOVEMENT, "Dash", "Lunge forward"),
```

### 2. Implement effect in `SkillSystem.java`
Add a case in the `activateSkill()` method:
```java
case DASH -> {
    Vector2 dir = player.getDirection();
    player.getPosition().add(dir.x() * 150, dir.y() * 150);
}
```

### 3. Update HUD in `Renderer.java`
Add icon/cooldown display for the new skill.

### 4. Test
Build and run with `mvn clean install -DskipTests && java -jar server/...`
