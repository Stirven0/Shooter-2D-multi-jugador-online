package com.aa.shared.message;

import java.util.List;

public class MapListMessage extends Message {
    private List<MapInfo> maps;

    public MapListMessage() {
        super(MessageType.MAP_LIST);
    }

    public static class MapInfo {
        private String id;
        private String name;
        private int maxPlayers;
        public String getId() {
            return id;
        }
        public void setId(String id) {
            this.id = id;
        }
        public String getName() {
            return name;
        }
        public void setName(String name) {
            this.name = name;
        }
        public int getMaxPlayers() {
            return maxPlayers;
        }
        public void setMaxPlayers(int maxPlayers) {
            this.maxPlayers = maxPlayers;
        }
    }
    // getters/setters

    public List<MapInfo> getMaps() {
        return maps;
    }

    public void setMaps(List<MapInfo> maps) {
        this.maps = maps;
    }
}