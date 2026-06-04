package com.aa.client.game;

import com.aa.shared.model.TileMap;
import com.aa.shared.state.GameState;
import javafx.application.Platform;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Estado thread-safe del cliente de juego.
 * Escrito desde el hilo de red y leído desde el hilo JavaFX.
 * Utiliza AtomicReference para el estado compartido y variables volátiles
 * para las banderas de control.
 */
public class GameClientState {
    private final AtomicReference<GameState> currentState = new AtomicReference<>();
    private volatile String localPlayerId;
    private volatile boolean inGame = false;

    /**
     * Reemplaza el estado actual con una nueva instantánea.
     * @param state nuevo estado del juego recibido del servidor
     */
    public void updateState(GameState state) {
        currentState.set(state);
    }

    /**
     * Reemplaza el estado actual (alias de updateState).
     * @param state nuevo estado del juego
     */
    public void setCurrentState(GameState state) {
        currentState.set(state);
    }

    /**
     * @return el estado actual del juego, o null si aún no se ha recibido
     */
    public GameState getCurrentState() {
        return currentState.get();
    }

    /**
     * @return ID del jugador local asignado por el servidor
     */
    public String getLocalPlayerId() {
        return localPlayerId;
    }

    /**
     * Establece el ID del jugador local.
     * @param id ID del jugador
     */
    public void setLocalPlayerId(String id) {
        this.localPlayerId = id;
    }

    /**
     * @return true si el jugador está actualmente en una partida
     */
    public boolean isInGame() {
        return inGame;
    }

    /**
     * Marca si el jugador está en una partida.
     * @param inGame true si está en partida
     */
    public void setInGame(boolean inGame) {
        this.inGame = inGame;
    }

    /**
     * Ejecuta una tarea en el hilo JavaFX de forma segura.
     * Si ya se está en el FX thread, ejecuta directamente; si no, lo encola.
     * @param r tarea a ejecutar
     */
    // Tile map cache (received once via MAP_DATA, not on every tick)
    private TileMap cachedTileMap;

    public TileMap getCachedTileMap() { return cachedTileMap; }
    public void setCachedTileMap(TileMap tileMap) { this.cachedTileMap = tileMap; }

    public static void runLater(Runnable r) {
        if (Platform.isFxApplicationThread()) {
            r.run();
        } else {
            Platform.runLater(r);
        }
    }
}