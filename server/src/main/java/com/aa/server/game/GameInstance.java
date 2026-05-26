package com.aa.server.game;

import com.aa.server.game.engine.GameEngine;
import com.aa.server.game.engine.GameLoop;
import com.aa.server.game.system.SkillSystem;
import com.aa.server.game.map.GameMap;
import com.aa.server.network.ConnectionManager;
import com.aa.server.room.Room;
import com.aa.server.util.ServerConfig;
import com.aa.shared.message.GameEndMessage;
import com.aa.shared.message.GameStateMessage;
import com.aa.shared.message.IdleWarningMessage;
import com.aa.shared.message.KickedIdleMessage;
import com.aa.shared.model.Player;
import com.aa.shared.model.PowerUpPickup;
import com.aa.shared.model.PowerUpType;
import com.aa.shared.model.Vector2;
import com.aa.shared.model.WeaponPickup;
import com.aa.shared.model.WeaponType;
import com.aa.shared.state.GameState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

public class GameInstance {
    private final String gameId;
    private final Room room;
    private final GameState state;
    private final GameEngine engine;
    private final GameLoop loop;
    private final GameMap map;
    private final ConnectionManager connectionManager;
    private final ConcurrentLinkedQueue<PlayerInput> inputQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<String> disconnectQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<String> reconnectQueue = new ConcurrentLinkedQueue<>();
    private volatile int sequence = 0;
    private Runnable onGameEndCallback;
    private BiConsumer<String, com.aa.shared.message.Message> messageSender;

    private final AtomicInteger pickupIdCounter = new AtomicInteger(0);

    // Idle tracking
    private final Map<String, Long> lastInputTime = new HashMap<>();
    private final Map<String, Long> idleWarningStart = new HashMap<>();
    private final Map<String, Long> lastWarningSend = new HashMap<>();

    public GameInstance(String gameId, Room room, GameMap map, ConnectionManager connectionManager) {
        this.gameId = gameId;
        this.room = room;
        this.map = map;
        this.connectionManager = connectionManager;
        this.state = new GameState(gameId, room.getMapId());
        this.state.setObstacles(map.obstacles());
        this.state.setMapWidth(map.width());
        this.state.setMapHeight(map.height());
        this.engine = new GameEngine();

        for (String playerId : room.getPlayerIds()) {
            Player p = new Player(playerId, playerId, randomSpawn());
            p.setSkillSlots(SkillSystem.createDefaultSkills());
            state.addPlayer(p);
        }

        spawnInitialPickups();

        this.loop = new GameLoop(this);
    }

    private void spawnInitialPickups() {
        List<WeaponPickup> weaponPickups = new ArrayList<>();
        int numWeapons = 4;
        for (int i = 0; i < numWeapons; i++) {
            String wid = "wp_" + pickupIdCounter.incrementAndGet();
            WeaponType wt = WeaponType.randomSpawnable();
            Vector2 pos = randomSpawn();
            weaponPickups.add(new WeaponPickup(wid, pos, wt));
        }
        state.setWeaponPickups(weaponPickups);

        List<PowerUpPickup> powerUpPickups = new ArrayList<>();
        int numPowerUps = 3;
        for (int i = 0; i < numPowerUps; i++) {
            String pid = "pp_" + pickupIdCounter.incrementAndGet();
            PowerUpType type = PowerUpType.values()[(int)(Math.random() * 5)];
            Vector2 pos = randomSpawn();
            powerUpPickups.add(new PowerUpPickup(pid, pos, type));
        }
        state.setPowerUpPickups(powerUpPickups);
    }

    public void start() {
        state.setStatus(GameState.GameStatus.PLAYING);
        state.setStartTime(System.currentTimeMillis());
        loop.start();
    }

    public void stop() {
        loop.stop();
    }

    public void setOnGameEndCallback(Runnable callback) {
        this.onGameEndCallback = callback;
    }

    public Runnable getOnGameEndCallback() {
        return onGameEndCallback;
    }

    public void setMessageSender(BiConsumer<String, com.aa.shared.message.Message> sender) {
        this.messageSender = sender;
    }

    public void queueInput(PlayerInput input) {
        inputQueue.offer(input);
    }

    public boolean hasPendingInputs() {
        return !inputQueue.isEmpty();
    }

    public ConnectionManager getConnectionManager() { return connectionManager; }
    public GameState getState() { return state; }
    public boolean hasPlayer(String playerId) {
        return room.getPlayerIds().contains(playerId);
    }
    public java.util.Set<String> getRoomPlayerIds() {
        return room.getPlayerIds();
    }

    public void processTick(float deltaTime) {
        if (state.getStatus() == GameState.GameStatus.FINISHED) return;
        long now = System.currentTimeMillis();

        String disconnectedId;
        while ((disconnectedId = disconnectQueue.poll()) != null) {
            Player p = state.getPlayer(disconnectedId);
            if (p != null && p.isAlive()) {
                p.setAlive(false);
                p.setHealth(0);
            }
        }

        String reconnectedId;
        while ((reconnectedId = reconnectQueue.poll()) != null) {
            Player p = state.getPlayer(reconnectedId);
            if (p != null && !p.isAlive()) {
                p.setAlive(true);
                p.setHealth(100);
                p.setPosition(randomSpawn());
            }
        }

        // Process inputs: handle SWAP_WEAPON inline, pass rest to engine
        List<PlayerInput> engineInputs = new ArrayList<>();
        while (!inputQueue.isEmpty()) {
            PlayerInput input = inputQueue.poll();
            if (input.type() == com.aa.shared.message.MessageType.SWAP_WEAPON) {
                handleSwapWeapon(input.playerId());
            } else {
                engineInputs.add(input);
            }
            lastInputTime.put(input.playerId(), now);
        }

        // Handle weapon pickup collisions
        checkWeaponPickups();

        state.incrementTick();
        state.setTimestamp(now);
        engine.update(state, deltaTime, engineInputs, map);

        long aliveCount = state.getAllPlayers().stream().filter(Player::isAlive).count();
        if (aliveCount <= 1) {
            state.setStatus(GameState.GameStatus.FINISHED);
            state.setEndTime(now);
        }

        if (state.getStatus() == GameState.GameStatus.PLAYING) {
            checkIdlePlayers(now);
        }
    }

    private void handleSwapWeapon(String playerId) {
        Player player = state.getPlayer(playerId);
        if (player == null) return;
        if (player.getSecondaryWeapon() == null) return;
        int slot = player.getCurrentWeaponSlot();
        player.setCurrentWeaponSlot(slot == 0 ? 1 : 0);
    }

    private void checkWeaponPickups() {
        List<WeaponPickup> pickups = state.getWeaponPickups();
        List<WeaponPickup> collected = new ArrayList<>();

        for (WeaponPickup wp : pickups) {
            for (Player player : state.getAllPlayers()) {
                if (!player.isAlive()) continue;
                double dist = player.getPosition().distanceTo(wp.getPosition());
                if (dist < ServerConfig.PLAYER_RADIUS + 20) {
                    if (player.getSecondaryWeapon() == null) {
                        player.setSecondaryWeapon(wp.getWeaponType());
                    } else {
                        player.setPrimaryWeapon(wp.getWeaponType());
                        player.setCurrentWeaponSlot(0);
                    }
                    collected.add(wp);
                    break;
                }
            }
        }

        if (!collected.isEmpty()) {
            List<WeaponPickup> remaining = new ArrayList<>(pickups);
            remaining.removeAll(collected);
            state.setWeaponPickups(remaining);
        }
    }

    private void checkIdlePlayers(long now) {
        for (Player p : state.getAllPlayers()) {
            if (!p.isAlive()) continue;
            String pid = p.getId();

            Long lastTime = lastInputTime.get(pid);
            long idleTime = now - (lastTime != null ? lastTime : state.getStartTime());

            if (idleTime < ServerConfig.IDLE_THRESHOLD_MS) {
                if (idleWarningStart.remove(pid) != null) {
                    lastWarningSend.remove(pid);
                    if (messageSender != null) {
                        messageSender.accept(pid, new IdleWarningMessage(0));
                    }
                }
                continue;
            }

            Long warningStart = idleWarningStart.get(pid);
            if (warningStart != null) {
                long warningElapsed = now - warningStart;
                if (warningElapsed >= ServerConfig.IDLE_WARNING_DURATION_MS) {
                    idleWarningStart.remove(pid);
                    lastWarningSend.remove(pid);
                    if (messageSender != null) {
                        messageSender.accept(pid, new KickedIdleMessage());
                    }
                    markPlayerDisconnected(pid);
                } else {
                    long lastSend = lastWarningSend.getOrDefault(pid, 0L);
                    if (now - lastSend >= 1000) {
                        lastWarningSend.put(pid, now);
                        int remaining = (int)((ServerConfig.IDLE_WARNING_DURATION_MS - warningElapsed) / 1000) + 1;
                        if (messageSender != null) {
                            messageSender.accept(pid, new IdleWarningMessage(remaining));
                        }
                    }
                }
            } else {
                idleWarningStart.put(pid, now);
                lastWarningSend.put(pid, now);
                if (messageSender != null) {
                    messageSender.accept(pid, new IdleWarningMessage(ServerConfig.IDLE_WARNING_DURATION_SECONDS));
                }
            }
        }
    }

    public void broadcastState() {
        GameState snapshot = state.copy();
        GameStateMessage msg = new GameStateMessage(snapshot, sequence++);
        connectionManager.broadcastToPlayers(room.getPlayerIds(), msg);
    }

    public boolean isFinished() {
        return state.getStatus() == GameState.GameStatus.FINISHED;
    }

    public GameEndMessage createGameEndMessage() {
        Player winner = null;
        for (Player p : state.getAllPlayers()) {
            if (p.isAlive()) {
                winner = p;
                break;
            }
        }

        String finalWinnerId = winner != null ? winner.getId() : "";
        String finalWinnerUsername = winner != null ? winner.getUsername() : "Empate";

        List<GameEndMessage.PlayerScore> scores = state.getAllPlayers().stream()
            .sorted(Comparator.comparingInt(Player::getKills).reversed())
            .map(p -> new GameEndMessage.PlayerScore(
                p.getId(), p.getUsername(), p.getKills(), p.getDeaths(),
                p.getId().equals(finalWinnerId)))
            .collect(Collectors.toList());

        long duration = state.getEndTime() - state.getStartTime();
        return new GameEndMessage(gameId, finalWinnerId, finalWinnerUsername, scores, duration);
    }

    public void markPlayerDisconnected(String playerId) {
        inputQueue.removeIf(in -> in.playerId().equals(playerId));
        disconnectQueue.offer(playerId);
    }

    public void queueReconnect(String playerId) {
        reconnectQueue.offer(playerId);
    }

    public Vector2 randomSpawn() {
        double x = Math.random() * (map.width() - 100) + 50;
        double y = Math.random() * (map.height() - 100) + 50;
        return new Vector2(x, y);
    }
}
