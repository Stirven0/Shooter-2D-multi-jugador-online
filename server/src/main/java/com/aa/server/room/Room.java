package com.aa.server.room;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class Room {
    private final String roomId;
    private volatile String hostId;
    private final String mapId;
    private final Set<String> playerIds = ConcurrentHashMap.newKeySet();
    private volatile RoomStatus status = RoomStatus.WAITING;

    public Room(String roomId, String hostId, String mapId) {
        this.roomId = roomId;
        this.hostId = hostId;
        this.mapId = mapId;
        this.playerIds.add(hostId);
    }

    public boolean addPlayer(String playerId) {
        if (isFull() || status != RoomStatus.WAITING) return false;
        return playerIds.add(playerId);
    }

    public boolean removePlayer(String playerId) {
        return playerIds.remove(playerId);
    }

    public boolean isFull() {
        return playerIds.size() >= com.aa.server.util.ServerConfig.MAX_PLAYERS_PER_ROOM;
    }

    public int getPlayerCount() {
        return playerIds.size();
    }

    public boolean isHost(String playerId) {
        return hostId.equals(playerId);
    }

    public boolean transferHostIfEmpty() {
        if (!playerIds.isEmpty() && !playerIds.contains(hostId)) {
            String newHost = playerIds.iterator().next();
            hostId = newHost;
            System.out.println("[ROOM] Host transferido: " + roomId + " nuevo host=" + newHost);
            return true;
        }
        return false;
    }

    // Getters
    public String getRoomId() { return roomId; }
    public String getHostId() { return hostId; }
    public String getMapId() { return mapId; }
    public Set<String> getPlayerIds() { return Collections.unmodifiableSet(playerIds); }
    public RoomStatus getStatus() { return status; }
    public void setStatus(RoomStatus status) { this.status = status; }
}
