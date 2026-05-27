package com.aa.server.game;

import com.aa.server.db.DatabaseManager;
import com.aa.server.game.map.GameMap;
import com.aa.server.game.map.MapManager;
import com.aa.server.network.ClientConnection;
import com.aa.server.network.ConnectionManager;
import com.aa.server.room.Room;
import com.aa.server.room.RoomManager;
import com.aa.shared.model.Player;

import java.util.concurrent.ConcurrentHashMap;


public class GameInstanceManager {
    private final ConcurrentHashMap<String, GameInstance> instances = new ConcurrentHashMap<>();
    private final ConnectionManager connectionManager;
    private final MapManager mapManager;
    private RoomManager roomManager;

    public GameInstanceManager(ConnectionManager connectionManager, MapManager mapManager) {
        this.connectionManager = connectionManager;
        this.mapManager = mapManager;
    }

    public void setRoomManager(RoomManager roomManager) {
        this.roomManager = roomManager;
    }

    public void createAndStart(Room room) {
        System.out.println("[GAME] createAndStart called for room " + room.getRoomId() + " map=" + room.getMapId());
        GameMap map = mapManager.getMap(room.getMapId());
        if (map == null) {
            System.out.println("[GAME] Map not found for " + room.getMapId() + ", using default");
            map = mapManager.getDefaultMap();
        }

        GameInstance instance = new GameInstance(room.getRoomId(), room, map, connectionManager);
        instance.setOnGameEndCallback(() -> {
            persistStats(instance);
            cleanupGame(room.getRoomId());
        });
        instance.setMessageSender((playerId, msg) -> {
            ClientConnection c = connectionManager.getByPlayerId(playerId);
            if (c != null && c.isOpen()) {
                c.send(msg);
            }
            if (msg instanceof com.aa.shared.message.KickedIdleMessage) {
                String roomId = instance.getState().getGameId();
                if (roomManager != null) {
                    roomManager.leaveRoom(roomId, playerId);
                }
                if (c != null) {
                    c.setCurrentRoomId(null);
                }
            }
        });
        instances.put(room.getRoomId(), instance);
        instance.start();
    }

    public GameInstance getGameByPlayer(String playerId) {
        return instances.values().stream()
                .filter(g -> g.hasPlayer(playerId))
                .findFirst()
                .orElse(null);
    }

    public void cleanupGame(String roomId) {
        GameInstance gi = instances.remove(roomId);
        if (gi != null) {
            gi.stop();
            // Limpiar referencias a la sala de todos los jugadores
            for (String pid : gi.getState().getAllPlayers().stream().map(p -> p.getId()).toList()) {
                ClientConnection c = connectionManager.getByPlayerId(pid);
                if (c != null) c.setCurrentRoomId(null);
            }
        }
        if (roomManager != null) roomManager.removeRoom(roomId);
        System.out.println("[GAME] Sala y partida limpiadas: " + roomId);
    }

    private void persistStats(GameInstance instance) {
        String winnerId = "";
        for (Player p : instance.getState().getAllPlayers()) {
            if (p.isAlive()) {
                winnerId = p.getId();
                break;
            }
        }
        for (Player p : instance.getState().getAllPlayers()) {
            boolean won = p.getId().equals(winnerId);
            DatabaseManager.savePlayerStats(p.getId(), p.getKills(), p.getDeaths(), won);
        }
    }

    public void handleDisconnect(String playerId) {
        GameInstance gi = getGameByPlayer(playerId);
        if (gi != null) {
            gi.markPlayerDisconnected(playerId);
            System.out.println("[GAME] Player disconnected, marked as dead: " + playerId);
        }
    }

}
