package com.aa.shared.model;

import java.util.Collections;
import java.util.Map;

public class TileObject {
    private int id;
    private String name;
    private String type;
    private double x;
    private double y;
    private double width;
    private double height;
    private double rotation;
    private boolean visible;
    private Map<String, String> properties;

    public TileObject() {
        this.visible = true;
        this.properties = Collections.emptyMap();
    }

    public TileObject(int id, String name, String type, double x, double y, double width, double height) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.visible = true;
        this.properties = Collections.emptyMap();
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public double getX() { return x; }
    public void setX(double x) { this.x = x; }
    public double getY() { return y; }
    public void setY(double y) { this.y = y; }
    public double getWidth() { return width; }
    public void setWidth(double width) { this.width = width; }
    public double getHeight() { return height; }
    public void setHeight(double height) { this.height = height; }
    public double getRotation() { return rotation; }
    public void setRotation(double rotation) { this.rotation = rotation; }
    public boolean isVisible() { return visible; }
    public void setVisible(boolean visible) { this.visible = visible; }
    public Map<String, String> getProperties() { return properties; }
    public void setProperties(Map<String, String> properties) { this.properties = properties; }

    public Vector2 getPosition() {
        return new Vector2(x, y);
    }

    public String getProperty(String key) {
        return properties != null ? properties.get(key) : null;
    }
}
