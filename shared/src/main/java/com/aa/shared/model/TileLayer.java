package com.aa.shared.model;

import java.util.List;

public class TileLayer {
    private int id;
    private String name;
    private String type;
    private int width;
    private int height;
    private int[][] data;
    private boolean visible;
    private double opacity;
    private List<TileObject> objects;

    public TileLayer() {
        this.visible = true;
        this.opacity = 1.0;
    }

    public TileLayer(int id, String name, String type, int width, int height, int[][] data) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.width = width;
        this.height = height;
        this.data = data;
        this.visible = true;
        this.opacity = 1.0;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public int getWidth() { return width; }
    public void setWidth(int width) { this.width = width; }
    public int getHeight() { return height; }
    public void setHeight(int height) { this.height = height; }
    public int[][] getData() { return data; }
    public void setData(int[][] data) { this.data = data; }
    public boolean isVisible() { return visible; }
    public void setVisible(boolean visible) { this.visible = visible; }
    public double getOpacity() { return opacity; }
    public void setOpacity(double opacity) { this.opacity = opacity; }
    public List<TileObject> getObjects() { return objects; }
    public void setObjects(List<TileObject> objects) { this.objects = objects; }

    public int getTile(int col, int row) {
        if (data == null || row < 0 || row >= data.length || col < 0 || col >= data[row].length) return 0;
        return data[row][col];
    }
}
