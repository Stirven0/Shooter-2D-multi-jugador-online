package com.aa.server.game.map;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MapManager {
    private final Map<String, GameMap> maps = new ConcurrentHashMap<>();

    public MapManager() {
        loadDefaults();
    }

    private void loadDefaults() {
        // Try loading TMJ maps first, fall back to legacy JSON
        String[] mapIds = {"map_01", "map_02", "map_03", "map_04"};
        for (String mapId : mapIds) {
            try {
                register(TiledMapLoader.loadFromTmj("/maps/" + mapId + ".tmj"));
            } catch (Exception e) {
                System.out.println("[MAP] No TMJ for " + mapId + ", loading legacy JSON: " + e.getMessage());
                register(MapLoader.loadFromJson("/maps/" + mapId + ".json"));
            }
        }
    }

    private void register(GameMap map) {
        maps.put(map.mapId(), map);
    }

    public GameMap getMap(String mapId) {
        GameMap m = maps.get(mapId);
        if (m == null) {
            System.err.println("[MAP] Map not found: " + mapId + ", using default");
            return getDefaultMap();
        }
        return m;
    }

    public GameMap getDefaultMap() {
        GameMap m = maps.get("map_01");
        if (m == null) {
            // fallback: pick first available
            m = maps.values().stream().findFirst().orElse(null);
        }
        return m;
    }

    public Map<String, GameMap> getAllMaps() {
        return Map.copyOf(maps);
    }
}