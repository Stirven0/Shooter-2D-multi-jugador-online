package com.aa.server.network;

import com.aa.server.auth.AuthService;
import com.aa.server.game.GameInstanceManager;
import com.aa.server.util.ServerConfig;
import com.aa.server.handler.MessageHandler;
import com.aa.server.room.Room;
import com.aa.server.room.RoomManager;
import com.aa.shared.message.Message;
import com.aa.shared.message.PingMessage;
import com.aa.shared.message.RoomUpdatedMessage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import com.aa.server.game.map.MapManager;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;

/**
 * Punto de entrada WebSocket. Delega todo a MessageHandler.
 */
public class GameServer extends WebSocketServer {
    private final ConnectionManager connectionManager;
    private final MessageHandler messageHandler;
    private final GameInstanceManager gameInstanceManager;
    private final RoomManager roomManager;

    private static final Message PING_MSG = new PingMessage();


    public GameServer(InetSocketAddress address) {
        super(address);
        setReuseAddr(true);
        this.connectionManager = new ConnectionManager();

        AuthService authService = new AuthService();
        MapManager mapManager = new MapManager();
        this.gameInstanceManager = new GameInstanceManager(connectionManager, mapManager);
        this.roomManager = new RoomManager(gameInstanceManager);
        gameInstanceManager.setRoomManager(this.roomManager);

        this.messageHandler = new MessageHandler(authService, this.roomManager, gameInstanceManager, connectionManager);
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        System.out.println("[NET] Connected: " + conn.getRemoteSocketAddress());
        connectionManager.register(conn);
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        String connId = conn.getAttachment();
        if (connId != null) {
            ClientConnection client = connectionManager.getByConnectionId(connId);
            String playerId = client != null ? client.getPlayerId() : null;
            String roomId = client != null ? client.getCurrentRoomId() : null;
            if (playerId != null && roomId != null) {
                roomManager.leaveRoom(roomId, playerId);
                Room room = roomManager.getRoom(roomId);
                if (room != null) {
                    RoomUpdatedMessage update = new RoomUpdatedMessage();
                    update.setRoomId(room.getRoomId());
                    update.setPlayerIds(new ArrayList<>(room.getPlayerIds()));
                    update.setStatus(room.getStatus().name());
                    update.setHostId(room.getHostId());
                    Map<String, String> usernames = new HashMap<>();
                    for (String pid : room.getPlayerIds()) {
                        ClientConnection c2 = connectionManager.getByPlayerId(pid);
                        usernames.put(pid, c2 != null ? c2.getUsername() : pid);
                    }
                    update.setPlayerUsernames(usernames);
                    for (String pid : room.getPlayerIds()) {
                        ClientConnection c = connectionManager.getByPlayerId(pid);
                        if (c != null) c.send(update);
                    }
                }
            }
            boolean wasPlaying = playerId != null && gameInstanceManager.getGameByPlayer(playerId) != null;
            connectionManager.remove(connId);
            if (wasPlaying) {
                gameInstanceManager.handleDisconnect(playerId);
            }
            System.out.println("[NET] Disconnected: " + connId + " player=" + playerId);
        }
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        String connId = conn.getAttachment();
        ClientConnection client = connectionManager.getByConnectionId(connId);
        if (client != null) {
            client.updateActivity();
            messageHandler.handle(client, message);
        }
        System.out.println("[NET] Mensaje recibido: " + "{" + connId + " : " + message + "}");
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        ex.printStackTrace();
    }

    @Override
    public void onStart() {
        System.out.println("[NET] Server a la escucha en: " + getPort());
        startHeartbeat();
        startTimeoutChecker();
    }

    private void startHeartbeat() {
        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(3000);

                    for (ClientConnection c : connectionManager.getAll()) {
                        if (c.isOpen()) {
                            c.send(PING_MSG);
                        }
                    }

                } catch (InterruptedException e) {
                    System.out.println("[NET] Heartbeat detenido");
                    break;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }, "Heartbeat-Thread").start();
    }

    private void startTimeoutChecker() {
        long timeoutMs = ServerConfig.CONNECTION_TIMEOUT_MS;
        if (timeoutMs <= 0) {
            System.out.println("[NET] Timeout checker desactivado (usa idle de juego)");
            return;
        }
        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(ServerConfig.TIMEOUT_CHECK_INTERVAL_MS);
                    for (ClientConnection c : connectionManager.getAll()) {
                        if (c.isOpen() && c.isTimedOut(timeoutMs)) {
                            String pid = c.getPlayerId();
                            String rid = c.getCurrentRoomId();
                            System.out.println("[NET] Timeout: " + c.getConnectionId() + " player=" + pid);
                            if (pid != null && rid != null) {
                                roomManager.leaveRoom(rid, pid);
                                Room r = roomManager.getRoom(rid);
                                if (r != null) {
                                    RoomUpdatedMessage up = new RoomUpdatedMessage();
                                    up.setRoomId(r.getRoomId());
                                    up.setPlayerIds(new ArrayList<>(r.getPlayerIds()));
                                    up.setStatus(r.getStatus().name());
                                    up.setHostId(r.getHostId());
                                    for (String p : r.getPlayerIds()) {
                                        ClientConnection cc = connectionManager.getByPlayerId(p);
                                        if (cc != null) cc.send(up);
                                    }
                                }
                            }
                            if (pid != null && gameInstanceManager.getGameByPlayer(pid) != null) {
                                gameInstanceManager.handleDisconnect(pid);
                            }
                            c.close();
                            connectionManager.remove(c.getConnectionId());
                        }
                    }
                } catch (InterruptedException e) {
                    System.out.println("[NET] Timeout checker detenido");
                    break;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }, "Timeout-Checker").start();
    }
}
