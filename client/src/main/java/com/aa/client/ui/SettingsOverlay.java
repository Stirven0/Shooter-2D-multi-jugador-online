package com.aa.client.ui;

import com.aa.client.asset.AudioManager;
import com.aa.client.game.GameClient;
import com.aa.client.util.ClientConfig;
import java.util.function.Consumer;
import javafx.beans.value.ChangeListener;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class SettingsOverlay {

    private static final String OVERLAY_STYLE = "-fx-background-color: rgba(13, 17, 23, 0.92); -fx-background-radius: 12; -fx-padding: 24;";
    private static final String FIELD_STYLE = "-fx-background-color: #21262d; -fx-text-fill: #f0f6fc; -fx-prompt-text-fill: #484f58; -fx-font-size: 13px; -fx-padding: 8 12; -fx-background-radius: 6; -fx-border-color: #30363d; -fx-border-radius: 6; -fx-border-width: 1;";
    private static final String SCROLL_STYLE = "-fx-background: transparent; -fx-background-color: transparent;";

    private final GameClient gameClient;
    private final Stage stage;
    private final Runnable onClose;
    private final boolean escEnabled;
    private final VBox root;

    private TextField serverHostField;
    private TextField serverPortField;
    private TextField mcpHostField;
    private TextField mcpPortField;
    private Label mcpStatusLabel;
    private Button mcpToggleBtn;
    private Button resetMcpBtn;
    private Button fullscreenBtn;
    private Button applyMcpBtn;

    private ChangeListener<? super Scene> sceneListener;
    private EventHandler<KeyEvent> escHandler;

    public SettingsOverlay(GameClient gameClient, Stage stage, Runnable onClose) {
        this(gameClient, stage, onClose, true);
    }

    public SettingsOverlay(GameClient gameClient, Stage stage, Runnable onClose, boolean escEnabled) {
        this.gameClient = gameClient;
        this.stage = stage;
        this.onClose = onClose;
        this.escEnabled = escEnabled;
        this.root = new VBox(12);
        root.setAlignment(Pos.TOP_CENTER);
        root.setStyle(OVERLAY_STYLE);
        root.setMaxWidth(600);
        root.setMaxHeight(480);
        root.setVisible(false);
        root.setManaged(false);
        buildUI();
    }

    public VBox getRoot() { return root; }

    public void show() {
        refresh();
        root.setVisible(true);
        root.setManaged(true);
        root.requestFocus();
        if (escEnabled) setupEscHandler();
    }

    public void hide() {
        root.setVisible(false);
        root.setManaged(false);
        removeEscHandler();
        if (onClose != null) onClose.run();
    }

    public void refresh() {
        boolean mcpOn = gameClient.getScreenManager().isMcpEnabled();
        mcpStatusLabel.setText(mcpOn ? "MCP: activado" : "MCP: desactivado");
        mcpStatusLabel.setStyle(mcpOn
            ? "-fx-text-fill: #3fb950; -fx-font-size: 12px;"
            : "-fx-text-fill: #8b949e; -fx-font-size: 12px;");
        mcpToggleBtn.setText(mcpOn ? "Desactivar MCP" : "Activar MCP");
        Styles.setBtnStyle(mcpToggleBtn, mcpOn ? "#1f6feb" : Styles.BG_INPUT, "#58a6ff");
        resetMcpBtn.setDisable(!mcpOn);
        applyMcpBtn.setDisable(mcpOn);

        mcpHostField.setText(ClientConfig.getMcpHost());
        mcpPortField.setText(String.valueOf(ClientConfig.getMcpPort()));
        serverHostField.setText(ClientConfig.getServerHost());
        serverPortField.setText(String.valueOf(ClientConfig.getServerPort()));

        boolean fs = stage.isFullScreen();
        fullscreenBtn.setText(fs ? "🗗  Modo ventana" : "⛶  Pantalla completa");
    }

    private void buildUI() {
        Label title = new Label("AJUSTES");
        title.setStyle("-fx-text-fill: #f0f6fc; -fx-font-size: 22px; -fx-font-weight: bold;");

        HBox columns = new HBox(20);
        columns.setAlignment(Pos.TOP_CENTER);
        columns.setFillHeight(true);

        VBox colLeft = new VBox(14);
        colLeft.setAlignment(Pos.TOP_CENTER);
        colLeft.setPrefWidth(240);
        colLeft.getChildren().add(buildServerSection());

        VBox colRight = new VBox(14);
        colRight.setAlignment(Pos.TOP_CENTER);
        colRight.setPrefWidth(240);
        colRight.getChildren().add(buildMcpSection());

        columns.getChildren().addAll(colLeft, colRight);

        VBox bottom = new VBox(12);
        bottom.setAlignment(Pos.CENTER);
        bottom.getChildren().addAll(buildDisplaySection(), buildAudioSection());

        Button closeBtn = new Button("Volver");
        Styles.setBtnStyle(closeBtn, Styles.ACCENT, Styles.ACCENT_HOVER);
        closeBtn.setOnAction(e -> hide());
        closeBtn.setMaxWidth(200);

        VBox content = new VBox(14);
        content.setAlignment(Pos.TOP_CENTER);
        content.getChildren().addAll(title, columns, bottom, closeBtn);

        ScrollPane scroll = new ScrollPane(content);
        scroll.getStylesheets().add(getClass().getClassLoader().getResource("style.css").toExternalForm());
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setMaxHeight(400);

        root.getChildren().add(scroll);
    }

    private VBox colSection(String titleText) {
        VBox box = new VBox(8);
        box.setAlignment(Pos.TOP_CENTER);
        Label lbl = new Label(titleText);
        lbl.setStyle("-fx-text-fill: #8b949e; -fx-font-size: 12px; -fx-padding: 6 0 2 0;");
        box.getChildren().add(lbl);
        return box;
    }

    private VBox buildServerSection() {
        VBox box = colSection("Servidor");

        serverHostField = new TextField(ClientConfig.getServerHost());
        serverHostField.setPromptText("Host del servidor");
        serverHostField.setStyle(FIELD_STYLE);
        serverHostField.setMaxWidth(220);
        box.getChildren().add(serverHostField);

        serverPortField = new TextField(String.valueOf(ClientConfig.getServerPort()));
        serverPortField.setPromptText("Puerto");
        serverPortField.setStyle(FIELD_STYLE);
        serverPortField.setMaxWidth(220);
        box.getChildren().add(serverPortField);

        Button saveBtn = new Button("Guardar");
        Styles.setBtnStyle(saveBtn, Styles.ACCENT, Styles.ACCENT_HOVER);
        saveBtn.setMaxWidth(160);
        saveBtn.setOnAction(e -> {
            String h = serverHostField.getText().trim();
            String p = serverPortField.getText().trim();
            if (!h.isEmpty() && !p.isEmpty()) {
                try {
                    ClientConfig.setServerUrl(h, Integer.parseInt(p));
                } catch (NumberFormatException ignored) {}
            }
        });
        box.getChildren().add(saveBtn);
        return box;
    }

    private VBox buildMcpSection() {
        VBox box = colSection("MCP Server");

        mcpHostField = new TextField(ClientConfig.getMcpHost());
        mcpHostField.setPromptText("MCP host");
        mcpHostField.setStyle(FIELD_STYLE);
        mcpHostField.setMaxWidth(220);
        box.getChildren().addAll(fieldLabel("Host:"), mcpHostField);

        mcpPortField = new TextField(String.valueOf(ClientConfig.getMcpPort()));
        mcpPortField.setPromptText("MCP puerto");
        mcpPortField.setStyle(FIELD_STYLE);
        mcpPortField.setMaxWidth(220);
        box.getChildren().addAll(fieldLabel("Puerto:"), mcpPortField);

        mcpStatusLabel = new Label("MCP: desactivado");
        mcpStatusLabel.setStyle("-fx-text-fill: #8b949e; -fx-font-size: 12px;");
        box.getChildren().add(mcpStatusLabel);

        mcpToggleBtn = new Button("Activar MCP");
        Styles.setBtnStyle(mcpToggleBtn, Styles.BG_INPUT, Styles.BORDER);
        mcpToggleBtn.setMaxWidth(220);
        mcpToggleBtn.setOnAction(e -> {
            var sm = gameClient.getScreenManager();
            if (sm.isMcpEnabled()) {
                sm.toggleMcpMode();
            } else {
                String h = mcpHostField.getText().trim();
                String p = mcpPortField.getText().trim();
                if (!h.isEmpty() && !p.isEmpty()) {
                    try {
                        int port = Integer.parseInt(p);
                        ClientConfig.setMcpHost(h);
                        ClientConfig.setMcpPort(port);
                        ClientConfig.setMcpTcpEnabled(true);
                        sm.restartMcpServer(h, port);
                    } catch (NumberFormatException ex) {
                        mcpStatusLabel.setText("Puerto inválido");
                        mcpStatusLabel.setStyle("-fx-text-fill: #f85149; -fx-font-size: 12px;");
                        return;
                    }
                } else {
                    sm.toggleMcpMode();
                }
            }
            refresh();
        });
        box.getChildren().add(mcpToggleBtn);

        HBox mcpActions = new HBox(8);
        mcpActions.setAlignment(Pos.CENTER);

        applyMcpBtn = new Button("Aplicar");
        Styles.setBtnStyle(applyMcpBtn, Styles.ACCENT, Styles.ACCENT_HOVER);
        applyMcpBtn.setOnAction(e -> {
            String h = mcpHostField.getText().trim();
            String p = mcpPortField.getText().trim();
            if (!h.isEmpty() && !p.isEmpty()) {
                try {
                    ClientConfig.setMcpHost(h);
                    ClientConfig.setMcpPort(Integer.parseInt(p));
                    ClientConfig.setMcpTcpEnabled(true);
                } catch (NumberFormatException ignored) {}
            }
        });
        mcpActions.getChildren().add(applyMcpBtn);

        resetMcpBtn = new Button("Reiniciar MCP");
        Styles.setBtnStyle(resetMcpBtn, Styles.BG_INPUT, Styles.BORDER);
        resetMcpBtn.setOnAction(e -> {
            String h = mcpHostField.getText().trim();
            String p = mcpPortField.getText().trim();
            try {
                int port = Integer.parseInt(p);
                ClientConfig.setMcpHost(h);
                ClientConfig.setMcpPort(port);
                ClientConfig.setMcpTcpEnabled(true);
                gameClient.getScreenManager().restartMcpServer(h, port);
                refresh();
            } catch (NumberFormatException ignored) {}
        });
        mcpActions.getChildren().add(resetMcpBtn);

        box.getChildren().add(mcpActions);
        return box;
    }

    private VBox buildDisplaySection() {
        VBox box = new VBox(8);
        box.setAlignment(Pos.CENTER);
        Label lbl = new Label("Pantalla");
        lbl.setStyle("-fx-text-fill: #8b949e; -fx-font-size: 12px; -fx-padding: 6 0 2 0;");
        box.getChildren().add(lbl);

        fullscreenBtn = new Button();
        fullscreenBtn.setMaxWidth(280);
        fullscreenBtn.setOnAction(e -> {
            gameClient.getScreenManager().toggleFullScreen();
            refresh();
        });
        box.getChildren().add(fullscreenBtn);
        return box;
    }

    private VBox buildAudioSection() {
        VBox box = new VBox(8);
        box.setAlignment(Pos.CENTER);
        Label lbl = new Label("Audio");
        lbl.setStyle("-fx-text-fill: #8b949e; -fx-font-size: 12px; -fx-padding: 6 0 2 0;");
        box.getChildren().add(lbl);

        HBox rows = new HBox(16);
        rows.setAlignment(Pos.CENTER);
        rows.getChildren().add(buildVolumeRow("General", AudioManager.getMasterVolume(), AudioManager::setMasterVolume));
        rows.getChildren().add(buildVolumeRow("Efectos", AudioManager.getSfxVolume(), AudioManager::setSfxVolume));
        rows.getChildren().add(buildVolumeRow("Música", AudioManager.getMusicVolume(), AudioManager::setMusicVolume));
        box.getChildren().add(rows);
        return box;
    }

    private VBox buildVolumeRow(String labelText, double initialValue, Consumer<Double> setter) {
        Label label = new Label(labelText);
        label.setStyle("-fx-text-fill: #f0f6fc; -fx-font-size: 12px;");

        Slider slider = new Slider(0, 100, initialValue * 100);
        slider.setStyle("-fx-control-inner-background: #21262d; -fx-accent: #58a6ff;");
        slider.setMaxWidth(120);
        slider.setMinWidth(120);

        Label valueLabel = new Label(String.format("%.0f%%", initialValue * 100));
        valueLabel.setStyle("-fx-text-fill: #58a6ff; -fx-font-size: 12px; -fx-min-width: 36px; -fx-font-weight: bold;");

        slider.valueProperty().addListener((obs, old, val) -> {
            double v = val.doubleValue() / 100.0;
            setter.accept(v);
            valueLabel.setText(String.format("%.0f%%", val.doubleValue()));
        });

        VBox col = new VBox(4);
        col.setAlignment(Pos.CENTER);
        col.getChildren().addAll(label, slider, valueLabel);
        return col;
    }

    private Label fieldLabel(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-text-fill: #f0f6fc; -fx-font-size: 13px;");
        return l;
    }

    private void setupEscHandler() {
        removeEscHandler();
        escHandler = e -> {
            if (e.getCode() == KeyCode.ESCAPE && root.isVisible()) {
                hide();
                e.consume();
            }
        };
        sceneListener = (obs, old, scene) -> {
            if (scene != null) {
                scene.addEventFilter(KeyEvent.KEY_PRESSED, escHandler);
            }
        };
        root.sceneProperty().addListener(sceneListener);
        if (root.getScene() != null) {
            root.getScene().addEventFilter(KeyEvent.KEY_PRESSED, escHandler);
        }
    }

    private void removeEscHandler() {
        if (escHandler != null) {
            if (root.getScene() != null) {
                root.getScene().removeEventFilter(KeyEvent.KEY_PRESSED, escHandler);
            }
            if (sceneListener != null) {
                root.sceneProperty().removeListener(sceneListener);
                sceneListener = null;
            }
            escHandler = null;
        }
    }
}
