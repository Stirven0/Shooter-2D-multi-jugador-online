package com.aa.server.util;

/**
 * Constantes centralizadas del servidor.
 * Lee valores de system properties (-Dkey=value) con fallback a defaults.
 * Sincronizar con .env.example.
 */
public final class ServerConfig {

    private ServerConfig() {}

    /** Ticks del bucle de juego por segundo (default 20 Hz). */
    public static final int TICK_RATE = getInt("TICK_RATE", 20);
    public static final float TICK_DURATION_SECONDS = 1.0f / TICK_RATE;
    public static final long TICK_DURATION_MS = 1000L / TICK_RATE;

    /** Velocidad de movimiento normal del jugador (px/s). */
    public static final double PLAYER_SPEED = getDouble("PLAYER_SPEED", 200.0);
    /** Velocidad de movimiento al correr (px/s). */
    public static final double PLAYER_SPRINT_SPEED = getDouble("PLAYER_SPRINT_SPEED", 300.0);
    public static final double BULLET_SPEED = getDouble("BULLET_SPEED", 600.0);
    public static final double FIRE_RATE_MS = getDouble("FIRE_RATE_MS", 250.0);
    public static final double BULLET_DAMAGE = getDouble("BULLET_DAMAGE", 25.0);
    public static final double BULLET_LIFETIME_MS = getDouble("BULLET_LIFETIME_MS", 5000.0);

    public static final double PLAYER_RADIUS = getDouble("PLAYER_RADIUS", 15.0);
    public static final double BULLET_RADIUS = getDouble("BULLET_RADIUS", 3.0);

    public static final int MAX_PLAYERS_PER_ROOM = getInt("MAX_PLAYERS_PER_ROOM", 10);
    public static final int MIN_PLAYERS_TO_START = getInt("MIN_PLAYERS_TO_START", 2);

    public static final long CONNECTION_TIMEOUT_MS = getLong("CONNECTION_TIMEOUT_MS", 0);
    public static final long TIMEOUT_CHECK_INTERVAL_MS = getLong("TIMEOUT_CHECK_INTERVAL_MS", 10_000);

    public static final long IDLE_THRESHOLD_MS = getLong("IDLE_THRESHOLD_MS", 30_000);
    public static final long IDLE_WARNING_DURATION_MS = getLong("IDLE_WARNING_DURATION_MS", 10_000);
    public static final int IDLE_WARNING_DURATION_SECONDS = getInt("IDLE_WARNING_DURATION_SECONDS", 10);

    private static int getInt(String key, int def) {
        String v = System.getProperty(key);
        return v != null ? Integer.parseInt(v) : def;
    }

    private static long getLong(String key, long def) {
        String v = System.getProperty(key);
        return v != null ? Long.parseLong(v) : def;
    }

    private static double getDouble(String key, double def) {
        String v = System.getProperty(key);
        return v != null ? Double.parseDouble(v) : def;
    }
}
