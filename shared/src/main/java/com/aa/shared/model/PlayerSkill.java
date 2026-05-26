package com.aa.shared.model;

public enum PlayerSkill {
    DASH(5.0, 0, SkillCategory.MOVEMENT, "Dash", "Lunge forward quickly"),
    SHIELD_BURST(15.0, 3.0, SkillCategory.DEFENSE, "Shield Burst", "Immune to damage for 3s"),
    HEAL(20.0, 0, SkillCategory.UTILITY, "Heal", "Restore 50 HP instantly"),
    ADRENALINE(18.0, 5.0, SkillCategory.BUFF, "Adrenaline", "+50% speed, +30% fire rate for 5s"),
    EMP(25.0, 0, SkillCategory.OFFENSE, "EMP", "Remove all buffs from nearby enemies (radius 300px)"),
    STEALTH(20.0, 4.0, SkillCategory.UTILITY, "Stealth", "Invisible for 4s");

    private final double cooldownSeconds;
    private final double durationSeconds;
    private final SkillCategory category;
    private final String displayName;
    private final String description;

    PlayerSkill(double cooldownSeconds, double durationSeconds, SkillCategory category, String displayName, String description) {
        this.cooldownSeconds = cooldownSeconds;
        this.durationSeconds = durationSeconds;
        this.category = category;
        this.displayName = displayName;
        this.description = description;
    }

    public double getCooldownSeconds() { return cooldownSeconds; }
    public double getDurationSeconds() { return durationSeconds; }
    public SkillCategory getCategory() { return category; }
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }

    public enum SkillCategory {
        MOVEMENT, DEFENSE, OFFENSE, UTILITY, BUFF
    }
}
