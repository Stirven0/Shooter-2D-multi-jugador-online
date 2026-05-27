package com.aa.server.room;

import com.aa.server.game.GameInstanceManager;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class RoomManager {
    private final ConcurrentHashMap<String, Room> rooms = new ConcurrentHashMap<>();
    private final GameInstanceManager gameInstanceManager;
    private final AtomicInteger counter = new AtomicInteger(0);

    public RoomManager(GameInstanceManager gameInstanceManager) {
        this.gameInstanceManager = gameInstanceManager;
    }

    public Room createRoom(String hostId, String mapId) {
        String roomId = "room_" + counter.incrementAndGet();
        Room room = new Room(roomId, hostId, mapId);
        rooms.put(roomId, room);
        return room;
    }

    public Room getRoom(String roomId) {
        return rooms.get(roomId);
    }

    public boolean joinRoom(String roomId, String playerId) {
        Room room = rooms.get(roomId);
        return room != null && room.addPlayer(playerId);
    }

    public void leaveRoom(String roomId, String playerId) {
        Room room = rooms.get(roomId);
        if (room != null) {
            room.removePlayer(playerId);
            if (room.getPlayerCount() == 0) {
                rooms.remove(roomId);
            } else if (room.isHost(playerId)) {
                room.transferHostIfEmpty();
            }
        }
    }

    public void startGame(String roomId) {
        Room room = rooms.get(roomId);
        if (room == null) return;
        if (room.getPlayerCount() < com.aa.server.util.ServerConfig.MIN_PLAYERS_TO_START) return;

        room.setStatus(RoomStatus.STARTING);
        gameInstanceManager.createAndStart(room);
        room.setStatus(RoomStatus.PLAYING);
    }

    public void removeRoom(String roomId) {
        rooms.remove(roomId);
    }

    public Collection<Room> listOpenRooms() {
        return rooms.values().stream()
                .filter(r -> r.getStatus() == RoomStatus.WAITING)
                .toList();
    }
}
