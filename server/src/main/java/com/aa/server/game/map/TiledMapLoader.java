package com.aa.server.game.map;

import com.aa.shared.model.Obstacle;
import com.aa.shared.model.TileLayer;
import com.aa.shared.model.TileMap;
import com.aa.shared.model.TileObject;
import com.aa.shared.model.TileSet;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TiledMapLoader {

    public static GameMap loadFromTmj(String resourcePath) {
        try (InputStreamReader reader = new InputStreamReader(
                TiledMapLoader.class.getResourceAsStream(resourcePath))) {

            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();

            int mapWidth = json.get("width").getAsInt();
            int mapHeight = json.get("height").getAsInt();
            int tileW = json.get("tilewidth").getAsInt();
            int tileH = json.get("tileheight").getAsInt();

            TileMap tileMap = new TileMap(mapWidth, mapHeight, tileW, tileH);

            List<TileSet> tilesets = new ArrayList<>();
            JsonArray tsArray = json.getAsJsonArray("tilesets");
            if (tsArray != null) {
                for (var elem : tsArray) {
                    tilesets.add(parseTileSet(elem.getAsJsonObject()));
                }
            }
            tileMap.setTilesets(tilesets);

            List<TileLayer> layers = new ArrayList<>();
            JsonArray layersArray = json.getAsJsonArray("layers");
            if (layersArray != null) {
                for (var elem : layersArray) {
                    layers.add(parseLayer(elem.getAsJsonObject(), mapWidth, mapHeight));
                }
            }
            tileMap.setLayers(layers);

            List<Obstacle> obstacles = new ArrayList<>();
            TileLayer wallsLayer = tileMap.getLayer("walls");
            if (wallsLayer != null && wallsLayer.getData() != null) {
                int[][] data = wallsLayer.getData();
                boolean[][] visited = new boolean[mapHeight][mapWidth];
                for (int row = 0; row < mapHeight; row++) {
                    for (int col = 0; col < mapWidth; col++) {
                        if (visited[row][col]) continue;
                        visited[row][col] = true;
                        int tileId = data[row][col];
                        if (tileId == 0) continue;
                        if (isSolidTile(tileId, tilesets)) {
                            int runEnd = col;
                            while (runEnd + 1 < mapWidth && data[row][runEnd + 1] > 0
                                    && isSolidTile(data[row][runEnd + 1], tilesets) && !visited[row][runEnd + 1]) {
                                runEnd++;
                                visited[row][runEnd] = true;
                            }
                            double ox = col * tileW;
                            double oy = row * tileH;
                            double ow = (runEnd - col + 1) * tileW;
                            double oh = tileH;
                            obstacles.add(new Obstacle(ox, oy, ow, oh));
                        }
                    }
                }
            }

            String id = json.has("id") ? json.get("id").getAsString() : resourcePath.replaceAll(".*/(\\w+)\\.tmj", "$1");
            String name = json.has("name") ? json.get("name").getAsString() : id;
            String mapId = id;
            double pixelWidth = mapWidth * tileW;
            double pixelHeight = mapHeight * tileH;

            return new GameMap(mapId, name, pixelWidth, pixelHeight, obstacles, tileMap);

        } catch (Exception e) {
            throw new RuntimeException("Failed to load TMJ map: " + resourcePath, e);
        }
    }

    private static TileLayer parseLayer(JsonObject json, int mapWidth, int mapHeight) {
        TileLayer layer = new TileLayer();
        layer.setId(json.get("id").getAsInt());
        layer.setName(json.get("name").getAsString());
        layer.setType(json.get("type").getAsString());
        layer.setWidth(json.has("width") ? json.get("width").getAsInt() : mapWidth);
        layer.setHeight(json.has("height") ? json.get("height").getAsInt() : mapHeight);
        if (json.has("visible")) layer.setVisible(json.get("visible").getAsBoolean());
        if (json.has("opacity")) layer.setOpacity(json.get("opacity").getAsDouble());

        if ("tilelayer".equals(layer.getType()) && json.has("data")) {
            JsonArray dataArr = json.getAsJsonArray("data");
            int[][] data = new int[layer.getHeight()][layer.getWidth()];
            for (int row = 0; row < layer.getHeight(); row++) {
                for (int col = 0; col < layer.getWidth(); col++) {
                    int idx = row * layer.getWidth() + col;
                    data[row][col] = dataArr.get(idx).getAsInt();
                }
            }
            layer.setData(data);
        }

        if ("objectgroup".equals(layer.getType()) && json.has("objects")) {
            JsonArray objArr = json.getAsJsonArray("objects");
            List<TileObject> objects = new ArrayList<>();
            for (var elem : objArr) {
                objects.add(parseTileObject(elem.getAsJsonObject()));
            }
            layer.setObjects(objects);
        }

        return layer;
    }

    private static TileObject parseTileObject(JsonObject json) {
        TileObject obj = new TileObject();
        obj.setId(json.get("id").getAsInt());
        obj.setName(json.has("name") ? json.get("name").getAsString() : "");
        obj.setType(json.has("type") ? json.get("type").getAsString() : "");
        obj.setX(json.get("x").getAsDouble());
        obj.setY(json.get("y").getAsDouble());
        obj.setWidth(json.has("width") ? json.get("width").getAsDouble() : 0);
        obj.setHeight(json.has("height") ? json.get("height").getAsDouble() : 0);
        if (json.has("visible")) obj.setVisible(json.get("visible").getAsBoolean());
        if (json.has("rotation")) obj.setRotation(json.get("rotation").getAsDouble());

        if (json.has("properties")) {
            Map<String, String> props = new HashMap<>();
            JsonArray propArr = json.getAsJsonArray("properties");
            for (var pe : propArr) {
                JsonObject p = pe.getAsJsonObject();
                props.put(p.get("name").getAsString(), p.get("value").getAsString());
            }
            obj.setProperties(props);
        }

        return obj;
    }

    private static TileSet parseTileSet(JsonObject json) {
        TileSet ts = new TileSet();
        ts.setFirstGid(json.get("firstgid").getAsInt());
        ts.setName(json.get("name").getAsString());
        ts.setTileWidth(json.has("tilewidth") ? json.get("tilewidth").getAsInt() : 32);
        ts.setTileHeight(json.has("tileheight") ? json.get("tileheight").getAsInt() : 32);
        ts.setTileCount(json.has("tilecount") ? json.get("tilecount").getAsInt() : 0);
        ts.setColumns(json.has("columns") ? json.get("columns").getAsInt() : 0);
        if (json.has("image")) ts.setImage(json.get("image").getAsString());
        if (json.has("imagewidth")) ts.setImageWidth(json.get("imagewidth").getAsInt());
        if (json.has("imageheight")) ts.setImageHeight(json.get("imageheight").getAsInt());

        if (json.has("tiles")) {
            Map<Integer, Map<String, String>> tileProps = new HashMap<>();
            JsonArray tilesArr = json.getAsJsonArray("tiles");
            for (var elem : tilesArr) {
                JsonObject t = elem.getAsJsonObject();
                int id = t.get("id").getAsInt();
                if (t.has("properties")) {
                    Map<String, String> props = new HashMap<>();
                    JsonArray propArr = t.getAsJsonArray("properties");
                    for (var pe : propArr) {
                        JsonObject p = pe.getAsJsonObject();
                        props.put(p.get("name").getAsString(), p.get("value").getAsString());
                    }
                    tileProps.put(id, props);
                }
            }
            ts.setTileProperties(tileProps);
        }

        return ts;
    }

    private static boolean isSolidTile(int tileId, List<TileSet> tilesets) {
        for (TileSet ts : tilesets) {
            if (ts.isSolid(tileId)) return true;
        }
        return false;
    }
}
