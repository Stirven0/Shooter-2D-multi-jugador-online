package com.aa.shared.model;

import java.util.Collections;
import java.util.Map;

public class TileSet {
    private int firstGid;
    private String name;
    private int tileWidth;
    private int tileHeight;
    private int tileCount;
    private int columns;
    private String image;
    private int imageWidth;
    private int imageHeight;
    private Map<Integer, Map<String, String>> tileProperties;

    public TileSet() {
        this.tileProperties = Collections.emptyMap();
    }

    public TileSet(int firstGid, String name, int tileWidth, int tileHeight, int tileCount, int columns) {
        this.firstGid = firstGid;
        this.name = name;
        this.tileWidth = tileWidth;
        this.tileHeight = tileHeight;
        this.tileCount = tileCount;
        this.columns = columns;
        this.tileProperties = Collections.emptyMap();
    }

    public int getFirstGid() { return firstGid; }
    public void setFirstGid(int firstGid) { this.firstGid = firstGid; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getTileWidth() { return tileWidth; }
    public void setTileWidth(int tileWidth) { this.tileWidth = tileWidth; }
    public int getTileHeight() { return tileHeight; }
    public void setTileHeight(int tileHeight) { this.tileHeight = tileHeight; }
    public int getTileCount() { return tileCount; }
    public void setTileCount(int tileCount) { this.tileCount = tileCount; }
    public int getColumns() { return columns; }
    public void setColumns(int columns) { this.columns = columns; }
    public String getImage() { return image; }
    public void setImage(String image) { this.image = image; }
    public int getImageWidth() { return imageWidth; }
    public void setImageWidth(int imageWidth) { this.imageWidth = imageWidth; }
    public int getImageHeight() { return imageHeight; }
    public void setImageHeight(int imageHeight) { this.imageHeight = imageHeight; }
    public Map<Integer, Map<String, String>> getTileProperties() { return tileProperties; }
    public void setTileProperties(Map<Integer, Map<String, String>> tileProperties) { this.tileProperties = tileProperties; }

    public boolean isSolid(int tileId) {
        if (tileProperties == null) return false;
        Map<String, String> props = tileProperties.get(tileId - firstGid);
        return props != null && "true".equals(props.get("solid"));
    }
}
