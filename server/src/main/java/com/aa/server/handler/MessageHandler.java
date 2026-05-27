package com.aa.server.handler;

import com.aa.server.auth.AuthService;
import com.aa.server.game.GameInstance;
import com.aa.server.game.GameInstanceManager;
import com.aa.server.game.PlayerInput;
import com.aa.server.network.ClientConnection;
import com.aa.server.network.ConnectionManager;
import com.aa.shared.message.UseSkillMessage;
import com.aa.server.room.Room;
import com.aa.server.room.RoomManager;
import com.aa.server.util.ServerConfig;
import com.aa.shared.message.*;
import com.aa.shared.util.JsonUtil;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Router central de mensajes del servidor.
 * Deserializa los mensajes JSON entrantes y delega en los métodos
 * de manejo correspondientes según el tipo de mensaje.
 * No contiene lógica de negocio, solo enrutado y validación básica.
 */
public class MessageHandler {

    private final AuthService authService;
    private final RoomManager roomManager;
    private final GameInstanceManager gameInstanceManager;
    private final ConnectionManager connectionManager;

    /**
     * Construye el manejador con las dependencias necesarias.
     * @param authService servicio de autenticación
     * @param roomManager gestor de salas
     * @param gameInstanceManager gestor de instancias de partida
     * @param connectionManager gestor de conexiones de clientes
     */
    public MessageHandler(
        AuthService authService,
        RoomManager roomManager,
        GameInstanceManager gameInstanceManager,
        ConnectionManager connectionManager
    ) {
        this.authService = authService;
        this.roomManager = roomManager;
        this.gameInstanceManager = gameInstanceManager;
        this.connectionManager = connectionManager;
    }

    /**
     * Procesa un mensaje JSON entrante de un cliente.
     * Extrae el campo "type" y enruta al manejador adecuado.
     * Las rutas públicas (login, reconexión) no requieren autenticación.
     * @param client conexión del cliente que envió el mensaje
     * @param json cuerpo del mensaje en JSON
     */
    public void handle(ClientConnection client, String json) {
        try {
            String typeStr = JsonUtil.extractField(json, "type");
            if (typeStr == null) {
                client.sendError(
                    "MISSING_TYPE",
                    "Field 'type' is required",
                    false
                );
                return;
            }

            MessageType type = MessageType.valueOf(typeStr);

            // Rutas públicas
            switch (type) {
                case LOGIN_REQUEST -> handleLogin(client, json);
                case RECONNECT -> handleReconnect(client, json);
                default -> handleAuthenticated(client, json, type);
            }
        } catch (IllegalArgumentException e) {
            client.sendError("INVALID_TYPE", "Unknown message type", false);
        } catch (Exception e) {
            client.sendError("PROCESSING_ERROR", e.getMessage(), false);
            e.printStackTrace();
        }
    }

    /**
     * Enruta mensajes que requieren autenticación.
     * Si el cliente no está autenticado, envía un error.
     */
    private void handleAuthenticated(
        ClientConnection client,
        String json,
        MessageType type
    ) {
        if (!client.isAuthenticated()) {
            client.sendError("NOT_AUTHENTICATED", "Login required", false);
            return;
        }

        switch (type) {
            case CREATE_ROOM -> handleCreateRoom(client, json);
            case JOIN_ROOM -> handleJoinRoom(client, json);
            case LEAVE_ROOM -> handleLeaveRoom(client, json);
            case ROOM_LIST -> handleRoomList(client);
            case GAME_START -> handleStartGame(client, json);
            case MOVE_INPUT -> handleMove(client, json);
            case SHOOT_INPUT -> handleShoot(client, json);
            case SWAP_WEAPON -> handleSwapWeapon(client);
            case USE_SKILL -> handleUseSkill(client, json);
            case PING, PONG -> {
                /* heartbeat, no hacer nada */
            }
            default -> {
                System.out.println("[HANDLER] Mensaje no manejado: " + type);
            }
        }
    }

    /** Procesa solicitudes de login y registro de usuarios. */
    private void handleLogin(ClientConnection client, String json) {
        LoginMessage msg = JsonUtil.fromJson(json, LoginMessage.class);
        LoginResponseMessage response = new LoginResponseMessage();

        if (msg.isRegister()) {
            boolean registered = authService.register(msg.getUsername(), msg.getPassword());
            if (!registered) {
                response.setSuccess(false);
                response.setErrorMessage("El usuario ya existe");
                client.send(response);
                return;
            }
            System.out.println("[AUTH] Registrado: " + msg.getUsername());
        }

        // Verificar si el usuario ya está conectado desde otro cliente
        boolean alreadyConnected = false;
        for (ClientConnection c : connectionManager.getAll()) {
            if (c.isAuthenticated() && msg.getUsername().equals(c.getUsername()) && c != client) {
                alreadyConnected = true;
                break;
            }
        }
        if (alreadyConnected) {
            response.setSuccess(false);
            response.setErrorMessage("El usuario ya está conectado desde otro cliente");
            client.send(response);
            return;
        }

        String token = authService.login(msg.getUsername(), msg.getPassword());
        if (token != null) {
            String userId = authService.getUserId(token);
            client.setUserId(userId);
            client.setUsername(msg.getUsername());
            connectionManager.authenticate(client.getConnectionId(), userId);

            response.setSuccess(true);
            response.setToken(token);
            response.setUserId(userId);
            response.setUsername(msg.getUsername());

            System.out.println(
                "[AUTH] Login exitoso: " + msg.getUsername() + " -> " + userId
            );
        } else {
            response.setSuccess(false);
            response.setErrorMessage("Usuario o contraseña incorrectos");
        }
        client.send(response);
    }

    /** Crea una nueva sala y notifica a los miembros. */
    private void handleCreateRoom(ClientConnection client, String json) {
        String mapId = extractStringField(json, "mapId", "map_01");
        Room room = roomManager.createRoom(client.getPlayerId(), mapId);
        client.setCurrentRoomId(room.getRoomId());

        RoomCreatedMessage response = new RoomCreatedMessage();
        response.setRoomId(room.getRoomId());
        response.setHostId(room.getHostId());
        response.setMapId(room.getMapId());
        client.send(response);

        broadcastRoomUpdate(room);
    }

    /** Notifica a todos los jugadores de una sala sobre cambios en la misma. */
    private void broadcastRoomUpdate(Room room) {
        RoomUpdatedMessage update = new RoomUpdatedMessage();
        update.setRoomId(room.getRoomId());
        update.setPlayerIds(new ArrayList<>(room.getPlayerIds()));
        update.setStatus(room.getStatus().name());
        update.setHostId(room.getHostId());
        Map<String, String> usernames = new HashMap<>();
        for (String pid : room.getPlayerIds()) {
            ClientConnection c = connectionManager.getByPlayerId(pid);
            usernames.put(pid, c != null ? c.getUsername() : pid);
        }
        update.setPlayerUsernames(usernames);

        for (String pid : room.getPlayerIds()) {
            ClientConnection c = connectionManager.getByPlayerId(pid);
            if (c != null) c.send(update);
        }
    }

    /** Procesa solicitudes de unión a una sala. */
    private void handleJoinRoom(ClientConnection client, String json) {
        String roomId = extractStringField(json, "roomId", null);
        if (roomId == null) {
            client.sendError("BAD_REQUEST", "roomId required", false);
            return;
        }
        boolean ok = roomManager.joinRoom(roomId, client.getPlayerId());
        if (ok) {
            client.setCurrentRoomId(roomId);
            Room room = roomManager.getRoom(roomId);
            if (room != null) broadcastRoomUpdate(room);
        } else {
            client.sendError("JOIN_FAILED", "Room full or not found", false);
        }
    }

    /** Envía al cliente la lista de salas disponibles. */
    private void handleRoomList(ClientConnection client) {
        java.util.List<RoomListResponseMessage.RoomInfo> infos = new java.util.ArrayList<>();
        for (Room r : roomManager.listOpenRooms()) {
            infos.add(new RoomListResponseMessage.RoomInfo(
                r.getRoomId(), r.getHostId(), r.getMapId(),
                r.getPlayerCount(), com.aa.server.util.ServerConfig.MAX_PLAYERS_PER_ROOM,
                r.getStatus().name()
            ));
        }
        client.send(new RoomListResponseMessage(infos));
    }

    /** Procesa solicitudes de abandono de sala. */
    private void handleLeaveRoom(ClientConnection client, String json) {
        String roomId = client.getCurrentRoomId();
        if (roomId == null) return;
        String playerId = client.getPlayerId();

        GameInstance gi = gameInstanceManager.getGameByPlayer(playerId);
        if (gi != null && !gi.isFinished()) {
            gi.markPlayerDisconnected(playerId);
        }

        roomManager.leaveRoom(roomId, playerId);
        client.setCurrentRoomId(null);
        Room room = roomManager.getRoom(roomId);
        if (room != null) broadcastRoomUpdate(room);
    }

    /** Inicia la partida si el cliente es el anfitrión y hay suficientes jugadores. */
    private void handleStartGame(ClientConnection client, String json) {
        String roomId = client.getCurrentRoomId();
        if (roomId == null) {
            client.sendError("NOT_IN_ROOM", "Join a room first", false);
            return;
        }
        Room room = roomManager.getRoom(roomId);
        if (room == null || !room.isHost(client.getPlayerId())) {
            client.sendError("FORBIDDEN", "Only host can start", false);
            return;
        }
        if (room.getPlayerCount() < ServerConfig.MIN_PLAYERS_TO_START) {
            client.sendError("NOT_ENOUGH_PLAYERS",
                "Need at least " + ServerConfig.MIN_PLAYERS_TO_START + " players to start", false);
            return;
        }
        roomManager.startGame(roomId);
    }

    /** Procesa entrada de movimiento de un jugador y la encola en su partida. */
    private void handleMove(ClientConnection client, String json) {
        GameInstance game = gameInstanceManager.getGameByPlayer(
            client.getPlayerId()
        );
        if (game == null) {
            client.sendError("NOT_IN_GAME", "Not in an active game", false);
            return;
        }
        MoveMessage msg = JsonUtil.fromJson(json, MoveMessage.class);
        msg.normalize();
        game.queueInput(
            new PlayerInput(client.getPlayerId(), MessageType.MOVE_INPUT, msg)
        );
    }

    /** Procesa entrada de disparo con rate limiting y la encola en la partida. */
    private void handleShoot(ClientConnection client, String json) {
        GameInstance game = gameInstanceManager.getGameByPlayer(
            client.getPlayerId()
        );
        if (game == null) {
            client.sendError("NOT_IN_GAME", "Not in an active game", false);
            return;
        }
        if (!client.tryShoot()) {
            client.sendError("RATE_LIMIT", "Shooting too fast", false);
            return;
        }
        ShootMessage msg = JsonUtil.fromJson(json, ShootMessage.class);
        game.queueInput(
            new PlayerInput(client.getPlayerId(), MessageType.SHOOT_INPUT, msg)
        );
    }

    private void handleUseSkill(ClientConnection client, String json) {
        GameInstance game = gameInstanceManager.getGameByPlayer(client.getPlayerId());
        if (game == null) return;
        UseSkillMessage msg = JsonUtil.fromJson(json, UseSkillMessage.class);
        game.queueInput(new PlayerInput(client.getPlayerId(), MessageType.USE_SKILL, msg));
    }

    /** Cambia el slot de arma activo del jugador (primaria ↔ secundaria). */
    private void handleSwapWeapon(ClientConnection client) {
        GameInstance game = gameInstanceManager.getGameByPlayer(client.getPlayerId());
        if (game == null) return;
        game.queueInput(new PlayerInput(client.getPlayerId(), MessageType.SWAP_WEAPON, null));
    }

    /** Procesa una solicitud de reconexión validando el token y re-asignando la conexión. */
    private void handleReconnect(ClientConnection client, String json) {
        ReconnectMessage msg = JsonUtil.fromJson(json, ReconnectMessage.class);
        if (!authService.validateToken(msg.getToken())) {
            client.sendError("RECONNECT_FAILED", "Token invalido o expirado", false);
            return;
        }

        String userId = msg.getUserId();
        String playerId = userId;

        client.setPlayerId(playerId);
        client.setUserId(userId);
        client.setAuthenticated(true);
        connectionManager.authenticate(client.getConnectionId(), playerId);

        GameInstance game = gameInstanceManager.getGameByPlayer(playerId);
        if (game != null && !game.isFinished()) {
            game.queueReconnect(playerId);
            System.out.println("[HANDLER] Reconnect exitoso: " + playerId);
            LoginResponseMessage resp = new LoginResponseMessage();
            resp.setSuccess(true);
            resp.setToken(msg.getToken());
            resp.setUserId(userId);
            resp.setUsername(userId);
            client.send(resp);
        } else {
            client.sendError("RECONNECT_FAILED", "No hay partida activa", false);
        }
    }

    /** Extrae un campo string de un JSON con valor por defecto. */
    private String extractStringField(
        String json,
        String field,
        String defaultValue
    ) {
        try {
            com.google.gson.JsonObject obj = JsonUtil.parseToObject(json);
            return obj.has(field) ? obj.get(field).getAsString() : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }
}
