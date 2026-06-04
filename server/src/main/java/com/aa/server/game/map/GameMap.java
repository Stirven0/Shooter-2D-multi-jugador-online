package com.aa.server.game.map;

import com.aa.shared.model.Obstacle;
import com.aa.shared.model.TileMap;
import com.aa.shared.model.Vector2;
import java.util.List;

public class GameMap {
    private final String mapId;
    private final String name;
    private final double width;
    private final double height;
    private final List<Obstacle> obstacles;
    private TileMap tileMap;

    public GameMap(String mapId, String name, double width, double height, List<Obstacle> obstacles, TileMap tileMap) {
        this.mapId = mapId;
        this.name = name;
        this.width = width;
        this.height = height;
        this.obstacles = List.copyOf(obstacles);
        this.tileMap = tileMap;
    }

    public GameMap(String mapId, String name, double width, double height, List<Obstacle> obstacles) {
        this(mapId, name, width, height, obstacles, null);
    }

    public boolean isInsideBounds(Vector2 pos) {
        return pos.x() >= 0 && pos.x() <= width && pos.y() >= 0 && pos.y() <= height;
    }

    public boolean collides(Vector2 pos, double radius) {
        for (Obstacle o : obstacles) {
            if (o.intersectsCircle(pos, radius)) return true;
        }
        return false;
    }

    public String mapId() { return mapId; }
    public String mapName() { return name; }
    public double width() { return width; }
    public double height() { return height; }
    public List<Obstacle> obstacles() { return obstacles; }
    public TileMap getTileMap() { return tileMap; }
    public void setTileMap(TileMap tileMap) { this.tileMap = tileMap; }
}
