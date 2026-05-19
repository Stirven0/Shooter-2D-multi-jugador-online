package com.aa.client.ui;

import com.aa.client.game.GameClient;
import com.aa.client.mcp.ClientMcpServer;
import com.aa.shared.message.GameEndMessage;
import javafx.stage.StageStyle;
import javafx.stage.Stage;

public class ScreenManager {

    private Stage stage;
    private GameClient gameClient;
    private LobbyScreen lobbyScreen;
    private LoginScreen loginScreen;
    private volatile ClientMcpServer mcpServer;
    private final AutoLoginConfig autoLogin;

    public ScreenManager(AutoLoginConfig autoLogin) {
        this.autoLogin = autoLogin;
        this.gameClient = new GameClient(this, autoLogin);
    }

    public void init(Stage stage) {
        this.stage = stage;
        stage.initStyle(StageStyle.UNDECORATED);
        stage.setTitle(com.aa.client.util.ClientConfig.TITLE);

        if (autoLogin != null && autoLogin.hasPosition()) {
            stage.setX(autoLogin.getX());
            stage.setY(autoLogin.getY());
        }

        if (autoLogin != null) {
            startAutoLogin();
        } else {
            showLogin();
        }
        stage.show();
    }

    private void startAutoLogin() {
        System.out.println("[AUTO] Auto-login as " + autoLogin.getUsername() + " register=" + autoLogin.isAutoRegister());
        Thread t = new Thread(() -> {
            boolean ok = gameClient.connect();
            if (ok) {
                javafx.application.Platform.runLater(() -> {
                    gameClient.sendLogin(autoLogin.getUsername(), autoLogin.getPassword(), autoLogin.isAutoRegister());
                });
            } else {
                System.err.println("[AUTO] Failed to connect to server");
            }
        }, "auto-login-thread");
        t.setDaemon(true);
        t.start();
    }

    public void showLobby() {
        gameClient.setCurrentRoomId(null);
        gameClient.setCurrentScreen("lobby");
        this.lobbyScreen = new LobbyScreen(gameClient);
        stage.setScene(lobbyScreen.createScene(stage));

        if (autoLogin != null) {
            handleAutoLobbyAction();
        }
    }

    private void handleAutoLobbyAction() {
        if (autoLogin.isAutoCreate()) {
            System.out.println("[AUTO] Creating room...");
            gameClient.createRoom("map_01");
        } else if (autoLogin.isAutoJoin()) {
            System.out.println("[AUTO] Requesting room list...");
            gameClient.requestRoomList();
        }
    }

    public LobbyScreen getLobbyScreen() {
        return lobbyScreen;
    }

    public void showLogin() {
        gameClient.setCurrentScreen("login");
        this.loginScreen = new LoginScreen(gameClient);
        stage.setScene(loginScreen.createScene(stage));
    }

    public void showLoginError(String msg) {
        if (loginScreen != null) loginScreen.setError(msg);
    }

    public void showGame() {
        gameClient.setCurrentScreen("game");
        stage.setScene(new GameScreen(gameClient).createScene(stage));
    }

    public void showGameOver(GameEndMessage endMsg) {
        gameClient.setCurrentScreen("gameover");
        stage.setScene(new GameOverScreen(gameClient, endMsg).createScene(stage));
    }

    public GameClient getGameClient() {
        return gameClient;
    }

    public String getLastErrorMessage() {
        String clientError = gameClient.getLastError();
        if (clientError != null && !clientError.isEmpty()) return clientError;
        if (lobbyScreen != null) {
            String lobbyErr = lobbyScreen.getErrorMessage();
            if (lobbyErr != null && !lobbyErr.isEmpty()) return lobbyErr;
        }
        if (loginScreen != null) {
            String loginErr = loginScreen.getErrorMessage();
            if (loginErr != null && !loginErr.isEmpty()) return loginErr;
        }
        return null;
    }

    public void cleanup() {
        if (mcpServer != null) {
            mcpServer.stop();
            mcpServer = null;
        }
        if (gameClient != null) {
            gameClient.logout();
        }
    }

    public String getCurrentScreenName() {
        return gameClient.getCurrentScreen();
    }

    public void enableMcpMode() {
        System.out.println("[SCREEN] MCP mode enabled - starting MCP server");
        if (mcpServer != null) return;
        mcpServer = new ClientMcpServer(gameClient, gameClient.getInputHandler(),
            gameClient.getRenderer(), null, stage);
        mcpServer.start();
    }

    public boolean toggleMcpMode() {
        if (mcpServer != null) {
            mcpServer.stop();
            mcpServer = null;
            System.out.println("[SCREEN] MCP mode disabled");
            return false;
        } else {
            enableMcpMode();
            return true;
        }
    }

    public boolean isMcpEnabled() {
        return mcpServer != null;
    }

    public ClientMcpServer getMcpServer() { return mcpServer; }
    public void setMcpServer(ClientMcpServer mcpServer) { this.mcpServer = mcpServer; }

    public Stage getStage() { return stage; }

    public void toggleFullScreen() {
        stage.setFullScreen(!stage.isFullScreen());
    }

    public boolean isFullScreen() {
        return stage.isFullScreen();
    }
}
