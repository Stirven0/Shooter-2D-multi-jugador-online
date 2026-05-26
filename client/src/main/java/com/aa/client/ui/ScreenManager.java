package com.aa.client.ui;

import com.aa.client.game.GameClient;
import com.aa.client.mcp.ClientMcpServer;
import com.aa.shared.message.GameEndMessage;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.stage.StageStyle;
import javafx.stage.Stage;

public class ScreenManager {

    private Stage stage;
    private GameClient gameClient;
    private LobbyScreen lobbyScreen;
    private LoginScreen loginScreen;
    private volatile ClientMcpServer mcpServer;

    public ScreenManager() {
        this.gameClient = new GameClient(this);
    }

    public void init(Stage stage) {
        this.stage = stage;
        stage.initStyle(StageStyle.UNDECORATED);
        stage.setTitle(com.aa.client.util.ClientConfig.TITLE);

        showLogin();
        stage.show();
    }

    public void showLobby() {
        gameClient.setCurrentRoomId(null);
        gameClient.setCurrentScreen("lobby");
        this.lobbyScreen = new LobbyScreen(gameClient);
        stage.setScene(lobbyScreen.createScene(stage));
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

    public void showNotification(String msg, boolean isError) {
        Platform.runLater(() -> {
            var scene = stage.getScene();
            if (scene == null || !(scene.getRoot() instanceof BorderPane bp)) return;
            if (!(bp.getCenter() instanceof StackPane sp)) return;

            var label = new Label(msg);
            label.setStyle((isError
                ? "-fx-background-color: #f85149;"
                : "-fx-background-color: #3fb950;")
                + "-fx-text-fill: #ffffff; -fx-font-size: 13px; -fx-padding: 8 16;"
                + "-fx-background-radius: 6; -fx-font-weight: bold; -fx-opacity: 0.95;"
                + "-fx-border-color: rgba(255,255,255,0.15); -fx-border-radius: 6;");
            label.setMaxWidth(Double.MAX_VALUE);
            label.setAlignment(Pos.CENTER);
            StackPane.setAlignment(label, Pos.TOP_CENTER);
            StackPane.setMargin(label, new Insets(10, 40, 0, 40));
            sp.getChildren().add(label);

            new Thread(() -> {
                try { Thread.sleep(3000); } catch (InterruptedException ignored) {}
                Platform.runLater(() -> sp.getChildren().remove(label));
            }, "notif-clear").start();
        });
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

    public void restartMcpServer(String host, int port) {
        if (mcpServer != null) {
            mcpServer.stop();
            mcpServer = null;
        }
        com.aa.client.util.ClientConfig.setMcpHost(host);
        com.aa.client.util.ClientConfig.setMcpPort(port);
        com.aa.client.util.ClientConfig.setMcpTcpEnabled(true);
        mcpServer = new ClientMcpServer(gameClient, gameClient.getInputHandler(),
            gameClient.getRenderer(), null, stage);
        mcpServer.start();
    }

    public Stage getStage() { return stage; }

    public void toggleFullScreen() {
        stage.setFullScreen(!stage.isFullScreen());
    }

    public boolean isFullScreen() {
        return stage.isFullScreen();
    }
}
