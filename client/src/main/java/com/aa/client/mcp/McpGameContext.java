package com.aa.client.mcp;

import com.aa.client.game.GameClient;
import com.aa.client.game.GameClientState;
import com.aa.shared.model.Bullet;
import com.aa.shared.model.Player;
import com.aa.shared.model.PowerUpPickup;
import com.aa.shared.model.WeaponPickup;
import com.aa.shared.state.GameState;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class McpGameContext {

    private final GameClient gameClient;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public McpGameContext(GameClient gameClient) {
        this.gameClient = gameClient;
    }

    public GameClientState getClientState() {
        return gameClient.getClientState();
    }

    public GameState getGameState() {
        return getClientState().getCurrentState();
    }

    public String getLocalPlayerId() {
        return getClientState().getLocalPlayerId();
    }

    public Player getLocalPlayer() {
        GameState gs = getGameState();
        String id = getLocalPlayerId();
        return (gs != null && id != null) ? gs.getPlayer(id) : null;
    }

    public java.util.List<Player> getOtherPlayers() {
        GameState gs = getGameState();
        if (gs == null) return java.util.List.of();
        String localId = getLocalPlayerId();
        return gs.getAllPlayers().stream()
            .filter(p -> !p.getId().equals(localId))
            .toList();
    }

    public java.util.Collection<Bullet> getBullets() {
        GameState gs = getGameState();
        return gs != null ? gs.getAllBullets() : java.util.List.of();
    }

    public java.util.Collection<WeaponPickup> getWeaponPickups() {
        GameState gs = getGameState();
        return gs != null ? gs.getWeaponPickups() : java.util.List.of();
    }

    public java.util.Collection<PowerUpPickup> getPowerUpPickups() {
        GameState gs = getGameState();
        return gs != null ? gs.getPowerUpPickups() : java.util.List.of();
    }

    public boolean isInGame() {
        return gameClient.isInGame();
    }

    public String getCurrentScreen() {
        return gameClient.getCurrentScreen();
    }

    public Gson getGson() {
        return gson;
    }
}
