package com.aa.shared.message;

import com.aa.shared.model.TileMap;

public class MapDataMessage extends Message {
    private String mapId;
    private TileMap tileMap;

    public MapDataMessage() {
        super(MessageType.MAP_DATA);
    }

    public MapDataMessage(String mapId, TileMap tileMap) {
        this();
        this.mapId = mapId;
        this.tileMap = tileMap;
    }

    public String getMapId() { return mapId; }
    public void setMapId(String mapId) { this.mapId = mapId; }
    public TileMap getTileMap() { return tileMap; }
    public void setTileMap(TileMap tileMap) { this.tileMap = tileMap; }
}
