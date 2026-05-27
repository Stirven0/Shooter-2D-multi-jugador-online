package com.aa.server.game.engine;

import com.aa.server.game.GameInstance;
import com.aa.server.network.ClientConnection;
import com.aa.server.network.ConnectionManager;
import com.aa.server.util.ServerConfig;
import com.aa.shared.message.GameEndMessage;

/**
 * Bucle principal de la partida.
 * Ejecuta un ciclo fijo a la frecuencia definida en ServerConfig.TICK_RATE
 * (default 20 Hz). Procesa ticks, detecta fin de partida y transmite estado.
 * Implementa corrección de deriva temporal para mantener la frecuencia.
 */
public class GameLoop implements Runnable {
    private final GameInstance instance;
    private volatile boolean running = false;
    private Thread thread;

    /** Construye el bucle asociado a una instancia de partida. */
    public GameLoop(GameInstance instance) {
        this.instance = instance;
    }

    /** @return true si el bucle está en ejecución */
    public boolean isRunning() { return running; }

    /** Inicia el bucle en un hilo nuevo. */
    public void start() {
        running = true;
        thread = new Thread(this);
        thread.setName("GameLoop-" + instance.hashCode());
        thread.start();
    }

    /** Detiene el bucle y el hilo asociado. */
    public void stop() {
        running = false;
        if (thread != null) thread.interrupt();
    }

    /**
     * Ejecuta el bucle principal: espera hasta el próximo tick,
     * procesa el tick, detecta fin de partida y transmite el estado.
     * Si hay deriva, reinicia nextTick para evitar espiral de muerte.
     */
    @Override
    public void run() {
        long tickDuration = ServerConfig.TICK_DURATION_MS;
        long nextTick = System.currentTimeMillis();

        while (running) {
            long now = System.currentTimeMillis();

            if (now >= nextTick) {
                instance.processTick(ServerConfig.TICK_DURATION_SECONDS);

                if (instance.isFinished()) {
                    instance.broadcastState();
                    broadcastGameEnd();
                    instance.stop();
                    Runnable cb = instance.getOnGameEndCallback();
                    if (cb != null) cb.run();
                    return;
                }

                instance.broadcastState();
                nextTick += tickDuration;

                if (nextTick < now) {
                    nextTick = now + tickDuration;
                }
            } else {
                try {
                    Thread.sleep(Math.max(1, nextTick - now - 1));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    /** Envía el mensaje de fin de partida a cada jugador de la sala. */
    private void broadcastGameEnd() {
        GameEndMessage endMsg = instance.createGameEndMessage();
        ConnectionManager cm = instance.getConnectionManager();
        for (String pid : instance.getRoomPlayerIds()) {
            ClientConnection c = cm.getByPlayerId(pid);
            if (c != null) c.send(endMsg);
        }
    }
}