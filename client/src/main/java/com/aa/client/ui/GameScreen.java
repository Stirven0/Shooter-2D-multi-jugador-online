package com.aa.client.ui;

import com.aa.client.game.GameClient;
import com.aa.client.input.InputHandler;
import com.aa.client.mcp.ClientMcpServer;
import com.aa.client.util.ClientConfig;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class GameScreen {
    private final GameClient gameClient;
    private final Canvas canvas;
    private final GraphicsContext gc;
    private final InputHandler inputHandler;
    private boolean paused = false;
    private VBox pauseOverlay;
    private VBox helpOverlay;
    private SettingsOverlay settingsOverlay;
    private Label idleWarningLabel;
    private AnimationTimer gameLoop;

    private long lastFPSTime = 0;
    private int fpsFrameCount = 0;

    private static final String OVERLAY = "-fx-background-color: rgba(13, 17, 23, 0.85); -fx-background-radius: 12; -fx-padding: 30;";
    private static final String OVERLAY_TITLE = "-fx-text-fill: #f0f6fc; -fx-font-size: 24px; -fx-font-weight: bold;";
    private static final String OVERLAY_TEXT = "-fx-text-fill: #8b949e; -fx-font-size: 14px;";

    public GameScreen(GameClient gameClient) {
        this.gameClient = gameClient;
        this.canvas = new Canvas(ClientConfig.WIDTH, ClientConfig.HEIGHT);
        this.gc = canvas.getGraphicsContext2D();
        this.inputHandler = gameClient.getInputHandler();
    }

    public Scene createScene(Stage stage) {
        StackPane gameArea = new StackPane(canvas);

        pauseOverlay = buildPauseOverlay(stage);
        helpOverlay = buildHelpOverlay();
        settingsOverlay = new SettingsOverlay(gameClient, stage, () -> {}, false);

        idleWarningLabel = new Label();
        idleWarningLabel.setStyle("-fx-text-fill: #f85149; -fx-font-size: 16px; -fx-font-weight: bold; -fx-background-color: rgba(13, 17, 23, 0.8); -fx-padding: 8 16; -fx-background-radius: 6; -fx-border-color: #f85149; -fx-border-radius: 6; -fx-border-width: 1;");
        idleWarningLabel.setVisible(false);
        StackPane.setAlignment(idleWarningLabel, Pos.TOP_CENTER);
        StackPane.setMargin(idleWarningLabel, new Insets(10, 0, 0, 0));

        StackPane centerStack = new StackPane(gameArea, pauseOverlay, helpOverlay, settingsOverlay.getRoot(), idleWarningLabel);

        BorderPane root = new BorderPane();
        root.setTop(TitleBar.create("Partida en curso", stage, true));
        root.setCenter(centerStack);

        Scene scene = new Scene(root, ClientConfig.WIDTH, ClientConfig.HEIGHT + TitleBar.HEIGHT);

        inputHandler.attach(scene);

        scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                if (helpOverlay.isVisible()) {
                    toggleHelp();
                } else if (settingsOverlay.getRoot().isVisible()) {
                    toggleSettings();
                } else {
                    togglePause();
                }
                e.consume();
            } else if (e.getCode() == KeyCode.F3) {
                gameClient.setShowDebug(!gameClient.isShowDebug());
                e.consume();
            }
        });

        stage.fullScreenProperty().addListener((obs, was, is) ->
            Platform.runLater(() -> resizeCanvas(stage))
        );
        resizeCanvas(stage);

        gameLoop = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (!gameClient.isInGame()) {
                    gameLoop.stop();
                    return;
                }
                if (lastFPSTime == 0) lastFPSTime = now;
                fpsFrameCount++;
                if (now - lastFPSTime >= 1_000_000_000) {
                    gameClient.setFps(fpsFrameCount);
                    fpsFrameCount = 0;
                    lastFPSTime = now;
                }
                gameClient.update(inputHandler, gc);
                updateIdleWarning();
            }
        };
        gameLoop.start();

        ClientMcpServer mcpSrv = gameClient.getScreenManager().getMcpServer();
        if (mcpSrv != null) {
            mcpSrv.setCanvas(canvas);
        } else {
            ClientMcpServer mcpNew = new ClientMcpServer(
                gameClient, gameClient.getInputHandler(),
                gameClient.getRenderer(), canvas, stage);
            gameClient.getScreenManager().setMcpServer(mcpNew);
            mcpNew.start();
        }

        return scene;
    }

    private VBox buildPauseOverlay(Stage stage) {
        VBox overlay = new VBox(14);
        overlay.setAlignment(Pos.CENTER);
        overlay.setStyle(OVERLAY);
        overlay.setVisible(false);
        overlay.setMaxWidth(280);

        Label title = new Label("PAUSA");
        title.setStyle(OVERLAY_TITLE);

        Runnable resumeFn = () -> {
            paused = false;
            helpOverlay.setVisible(false);
            settingsOverlay.getRoot().setVisible(false);
            settingsOverlay.getRoot().setManaged(false);
            pauseOverlay.setVisible(false);
            gameClient.setPaused(false);
        };

        Button resumeBtn = btn("Reanudar", Styles.ACCENT, Styles.ACCENT_HOVER, e -> resumeFn.run());
        Button helpBtn = btn("Ayuda", Styles.BG_INPUT, Styles.BORDER, e -> toggleHelp());
        Button settingsBtn = btn("Ajustes", Styles.BG_INPUT, Styles.BORDER, e -> toggleSettings());
        Button leaveBtn = btn("Abandonar partida", Styles.DANGER, Styles.DANGER_HOVER, e -> {
            paused = false;
            overlay.setVisible(false);
            gameClient.leaveRoom();
            gameClient.getScreenManager().showLobby();
        });

        overlay.getChildren().addAll(title, resumeBtn, helpBtn, settingsBtn, leaveBtn);
        return overlay;
    }

    private VBox buildHelpOverlay() {
        VBox overlay = new VBox(12);
        overlay.setAlignment(Pos.CENTER);
        overlay.setStyle(OVERLAY);
        overlay.setVisible(false);
        overlay.setMaxWidth(360);

        Label title = new Label("AYUDA - CONTROLES");
        title.setStyle(OVERLAY_TITLE);

        String[] lines = {
            "WASD / Flechas ................. Moverse",
            "Mouse ......................... Apuntar",
            "Click izquierdo ............... Disparar",
            "SHIFT ......................... Correr",
            "F3 ............................ Debug",
            "",
            "Objetivo: Sé el último jugador en pie.",
            "Elimina a tus oponentes para ganar."
        };
        VBox textBox = new VBox(3);
        textBox.setAlignment(Pos.CENTER);
        for (String line : lines) {
            Label l = new Label(line);
            l.setStyle(OVERLAY_TEXT);
            textBox.getChildren().add(l);
        }

        Button closeBtn = btn("Volver", Styles.ACCENT, Styles.ACCENT_HOVER, e -> toggleHelp());
        overlay.getChildren().addAll(title, textBox, closeBtn);
        return overlay;
    }

    private void togglePause() {
        paused = !paused;
        if (paused) {
            helpOverlay.setVisible(false);
            settingsOverlay.getRoot().setVisible(false);
            settingsOverlay.getRoot().setManaged(false);
        }
        pauseOverlay.setVisible(paused);
        gameClient.setPaused(paused);
    }

    private void toggleHelp() {
        boolean showing = !helpOverlay.isVisible();
        helpOverlay.setVisible(showing);
        pauseOverlay.setVisible(!showing);
        settingsOverlay.getRoot().setVisible(false);
        settingsOverlay.getRoot().setManaged(false);
    }

    private void toggleSettings() {
        if (settingsOverlay.getRoot().isVisible()) {
            settingsOverlay.hide();
            pauseOverlay.setVisible(true);
        } else {
            settingsOverlay.show();
            pauseOverlay.setVisible(false);
            helpOverlay.setVisible(false);
        }
    }

    private void updateIdleWarning() {
        int secs = gameClient.getIdleWarningSeconds();
        if (secs > 0) {
            idleWarningLabel.setText("⚠  Si no te mueves serás expulsado en " + secs + "s  ⚠");
            idleWarningLabel.setVisible(true);
        } else {
            idleWarningLabel.setVisible(false);
        }
    }

    private void resizeCanvas(Stage stage) {
        double w = stage.getWidth();
        double h = stage.getHeight() - TitleBar.HEIGHT;
        if (w > 0 && h > 0) {
            canvas.setWidth(w);
            canvas.setHeight(h);
            gameClient.getCamera().setViewportSize(w, h);
        }
    }

    private static Button btn(String text, String color, String hoverColor, EventHandler<ActionEvent> action) {
        Button b = new Button(text);
        Styles.setBtnStyle(b, color, hoverColor);
        b.setOnAction(action);
        return b;
    }
}
