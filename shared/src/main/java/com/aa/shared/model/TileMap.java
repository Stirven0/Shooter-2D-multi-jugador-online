package com.aa.shared.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Tile map data loaded from a .tmj file.
 * After loading and initialization, TileMap is treated as effectively immutable.
 * The same instance is shared by GameMap and sent once via MapDataMessage.
 * No code path mutates tile data after game start.
 */
public class TileMap {
    private int width;
    private int height;
    private int tileWidth;
    private int tileHeight;
    private List<TileLayer> layers;
    private List<TileSet> tilesets;

    public TileMap() {
        this.layers = new ArrayList<>();
        this.tilesets = new ArrayList<>();
        this.tileWidth = 32;
        this.tileHeight = 32;
    }

    public TileMap(int width, int height, int tileWidth, int tileHeight) {
        this.width = width;
        this.height = height;
        this.tileWidth = tileWidth;
        this.tileHeight = tileHeight;
        this.layers = new ArrayList<>();
        this.tilesets = new ArrayList<>();
    }

    public int getWidth() { return width; }
    public void setWidth(int width) { this.width = width; }
    public int getHeight() { return height; }
    public void setHeight(int height) { this.height = height; }
    public int getTileWidth() { return tileWidth; }
    public void setTileWidth(int tileWidth) { this.tileWidth = tileWidth; }
    public int getTileHeight() { return tileHeight; }
    public void setTileHeight(int tileHeight) { this.tileHeight = tileHeight; }
    public List<TileLayer> getLayers() { return layers; }
    public void setLayers(List<TileLayer> layers) { this.layers = layers; }
    public List<TileSet> getTilesets() { return tilesets; }
    public void setTilesets(List<TileSet> tilesets) { this.tilesets = tilesets; }

    public double getMapWidthPixels() { return width * tileWidth; }
    public double getMapHeightPixels() { return height * tileHeight; }

    public TileLayer getLayer(String name) {
        for (TileLayer layer : layers) {
            if (layer.getName().equals(name)) return layer;
        }
        return null;
    }

    public int getTileId(String layerName, int col, int row) {
        TileLayer layer = getLayer(layerName);
        return layer != null ? layer.getTile(col, row) : 0;
    }

    public boolean isSolid(int col, int row) {
        for (TileSet ts : tilesets) {
            for (TileLayer layer : layers) {
                if (!"tilelayer".equals(layer.getType())) continue;
                int tileId = layer.getTile(col, row);
                if (tileId > 0 && ts.isSolid(tileId)) return true;
            }
        }
        return false;
    }

    public List<TileObject> getObjectGroup(String groupName) {
        for (TileLayer layer : layers) {
            if ("objectgroup".equals(layer.getType()) && layer.getName().equals(groupName)) {
                return layer.getObjects() != null ? layer.getObjects() : Collections.emptyList();
            }
        }
        return Collections.emptyList();
    }
}
