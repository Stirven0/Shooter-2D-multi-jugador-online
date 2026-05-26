package com.aa.server.game.system;

import com.aa.server.game.PlayerInput;
import com.aa.server.game.map.GameMap;
import com.aa.shared.message.MessageType;
import com.aa.shared.message.UseSkillMessage;
import com.aa.shared.model.Player;
import com.aa.shared.model.PlayerSkill;
import com.aa.shared.model.SkillSlot;
import com.aa.shared.model.Vector2;
import com.aa.shared.state.GameState;
import java.util.List;

public class SkillSystem implements GameSystem {

    @Override
    public void update(GameState state, float deltaTime, List<PlayerInput> inputs, GameMap map) {
        // Process skill activations
        for (PlayerInput input : inputs) {
            if (input.type() == MessageType.USE_SKILL && input.message() instanceof UseSkillMessage msg) {
                Player player = state.getPlayer(input.playerId());
                if (player != null && player.isAlive()) {
                    activateSkill(state, player, msg.getSkillSlot());
                }
            }
        }

        // Update cooldowns and active skill timers
        double dtSeconds = deltaTime;
        for (Player p : state.getAllPlayers()) {
            if (!p.isAlive()) continue;
            SkillSlot[] slots = p.getSkillSlots();
            for (int i = 0; i < slots.length; i++) {
                if (slots[i] == null || slots[i].getSkill() == null) continue;
                if (slots[i].isActive()) {
                    double remaining = slots[i].getCooldownRemaining() - dtSeconds;
                    slots[i].setCooldownRemaining(remaining);
                    if (remaining <= 0) {
                        slots[i].setActive(false);
                        deactivateEffect(p, slots[i].getSkill());
                    }
                }
                if (slots[i].getCooldownRemaining() > 0 && !slots[i].isActive()) {
                    slots[i].setCooldownRemaining(slots[i].getCooldownRemaining() - dtSeconds);
                }
            }
        }
    }

    private void activateSkill(GameState state, Player player, int slotIndex) {
        SkillSlot[] slots = player.getSkillSlots();
        if (slotIndex < 0 || slotIndex >= slots.length) return;
        SkillSlot slot = slots[slotIndex];
        if (slot == null || slot.getSkill() == null) return;
        if (slot.getCooldownRemaining() > 0) return;

        PlayerSkill skill = slot.getSkill();
        switch (skill) {
            case DASH -> {
                Vector2 dir = player.getDirection();
                double dashDist = 150;
                player.setPosition(player.getPosition().add(dir.multiply(dashDist)));
                clampToMap(player, state);
            }
            case SHIELD_BURST -> {
                player.setShield(player.getShield() + 9999);
                slot.setActive(true);
                slot.setCooldownRemaining(skill.getDurationSeconds());
            }
            case HEAL -> {
                player.setHealth(player.getHealth() + 50);
            }
            case ADRENALINE -> {
                slot.setActive(true);
                slot.setCooldownRemaining(skill.getDurationSeconds());
            }
            case EMP -> {
                double empRadius = 300;
                for (Player other : state.getAllPlayers()) {
                    if (other.getId().equals(player.getId())) continue;
                    if (!other.isAlive()) continue;
                    double dist = player.getPosition().distanceTo(other.getPosition());
                    if (dist <= empRadius) {
                        for (SkillSlot otherSlot : other.getSkillSlots()) {
                            if (otherSlot != null && otherSlot.isActive()) {
                                otherSlot.setActive(false);
                                otherSlot.setCooldownRemaining(otherSlot.getSkill().getCooldownSeconds());
                            }
                        }
                    }
                }
            }
            case STEALTH -> {
                slot.setActive(true);
                slot.setCooldownRemaining(skill.getDurationSeconds());
            }
        }

        // Set cooldown after activation
        if (skill.getDurationSeconds() <= 0) {
            slot.setCooldownRemaining(skill.getCooldownSeconds());
        } else {
            if (!slot.isActive()) {
                slot.setCooldownRemaining(skill.getCooldownSeconds());
            }
        }
    }

    private void deactivateEffect(Player player, PlayerSkill skill) {
        switch (skill) {
            case SHIELD_BURST -> player.setShield(0);
            default -> {}
        }
    }

    private void clampToMap(Player player, GameState state) {
        double x = Math.max(50, Math.min(state.getMapWidth() - 50, player.getPosition().x()));
        double y = Math.max(50, Math.min(state.getMapHeight() - 50, player.getPosition().y()));
        player.setPosition(new Vector2(x, y));
    }

    public static SkillSlot[] createDefaultSkills() {
        SkillSlot[] slots = new SkillSlot[2];
        PlayerSkill[] all = PlayerSkill.values();
        // Assign 2 random skills on spawn
        slots[0] = new SkillSlot(all[(int)(Math.random() * all.length)]);
        slots[1] = new SkillSlot(all[(int)(Math.random() * all.length)]);
        return slots;
    }
}
