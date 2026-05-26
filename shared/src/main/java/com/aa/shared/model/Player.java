package com.aa.shared.model;

/**
 * Representa el estado de un jugador.
 * Mutable: la posición y salud cambian durante el juego.
 */
public class Player {
    private String id;
    private Vector2 position;
    private Vector2 direction; // Vector unitario de dirección
    private double health;
    private double maxHealth;
    private boolean alive;
    private String username;
    private int kills;
    private int deaths;
    private WeaponType primaryWeapon;
    private WeaponType secondaryWeapon;
    private int currentWeaponSlot;
    private double shield;
    private int upgradePoints;
    private SkillSlot[] skillSlots;
    
    // Constructor vacío necesario para deserialización JSON
    public Player() {
        this.position = new Vector2(0, 0);
        this.direction = new Vector2(1, 0);
        this.health = 100;
        this.maxHealth = 100;
        this.alive = true;
        this.primaryWeapon = WeaponType.PISTOL;
        this.currentWeaponSlot = 0;
        this.skillSlots = new SkillSlot[2];
    }
    
    public Player(String id, String username, Vector2 position) {
        this.id = id;
        this.username = username;
        this.position = position;
        this.direction = new Vector2(1, 0);
        this.health = 100;
        this.maxHealth = 100;
        this.alive = true;
        this.primaryWeapon = WeaponType.PISTOL;
        this.currentWeaponSlot = 0;
        this.skillSlots = new SkillSlot[2];
    }
    
    // Getters y Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public Vector2 getPosition() { return position; }
    public void setPosition(Vector2 position) { this.position = position; }
    
    public Vector2 getDirection() { return direction; }
    public void setDirection(Vector2 direction) { this.direction = direction; }
    
    public double getHealth() { return health; }
    public void setHealth(double health) { 
        this.health = Math.max(0, Math.min(health, maxHealth));
        this.alive = this.health > 0;
    }
    
    public double getMaxHealth() { return maxHealth; }
    public void setMaxHealth(double maxHealth) { this.maxHealth = maxHealth; }
    
    public boolean isAlive() { return alive; }
    public void setAlive(boolean alive) { this.alive = alive; }
    
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    
    public int getKills() { return kills; }
    public void setKills(int kills) { this.kills = kills; }
    
    public int getDeaths() { return deaths; }
    public void setDeaths(int deaths) { this.deaths = deaths; }
    
    public WeaponType getPrimaryWeapon() { return primaryWeapon; }
    public void setPrimaryWeapon(WeaponType primaryWeapon) { this.primaryWeapon = primaryWeapon; }
    
    public WeaponType getSecondaryWeapon() { return secondaryWeapon; }
    public void setSecondaryWeapon(WeaponType secondaryWeapon) { this.secondaryWeapon = secondaryWeapon; }
    
    public int getCurrentWeaponSlot() { return currentWeaponSlot; }
    public void setCurrentWeaponSlot(int currentWeaponSlot) { this.currentWeaponSlot = currentWeaponSlot; }
    
    public WeaponType getCurrentWeapon() {
        return currentWeaponSlot == 0 ? primaryWeapon : (secondaryWeapon != null ? secondaryWeapon : primaryWeapon);
    }
    
    public double getShield() { return shield; }
    public void setShield(double shield) { this.shield = Math.max(0, shield); }
    
    public int getUpgradePoints() { return upgradePoints; }
    public void setUpgradePoints(int upgradePoints) { this.upgradePoints = upgradePoints; }
    
    public SkillSlot[] getSkillSlots() { return skillSlots; }
    public void setSkillSlots(SkillSlot[] skillSlots) { this.skillSlots = skillSlots; }
    
    public void takeDamage(double damage) {
        if (shield > 0) {
            double absorbed = Math.min(shield, damage);
            shield -= absorbed;
            damage -= absorbed;
        }
        setHealth(this.health - damage);
    }
    
    @Override
    public String toString() {
        return String.format("Player[%s: %s @ (%.1f, %.1f) HP:%.0f]", 
            id, username, position.x(), position.y(), health);
    }
}
