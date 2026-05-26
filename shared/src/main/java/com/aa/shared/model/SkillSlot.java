package com.aa.shared.model;

public class SkillSlot {
    private PlayerSkill skill;
    private double cooldownRemaining;
    private boolean active;

    public SkillSlot() {}

    public SkillSlot(PlayerSkill skill) {
        this.skill = skill;
        this.cooldownRemaining = 0;
        this.active = false;
    }

    public PlayerSkill getSkill() { return skill; }
    public void setSkill(PlayerSkill skill) { this.skill = skill; }
    public double getCooldownRemaining() { return cooldownRemaining; }
    public void setCooldownRemaining(double v) { this.cooldownRemaining = Math.max(0, v); }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
