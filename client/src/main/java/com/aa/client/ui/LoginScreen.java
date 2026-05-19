package com.aa.client.ui;

import com.aa.client.asset.AudioManager;
import com.aa.client.game.GameClient;
import com.aa.client.util.ClientConfig;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class LoginScreen {
    private final GameClient gameClient;
    private Label status;
    private TextField user;
    private PasswordField pass;
    private Button btn;
    private boolean registerMode = false;
    private Hyperlink toggleLink;
    private VBox helpOverlay;
    private VBox settingsOverlay;
    private Button helpBtn;
    private Button exitBtn;
    private TextField hostField;
    private TextField portField;
    private Button mcpToggleBtn;
    private Label mcpStatusLabel;

    private static final String INPUT_STYLE = "-fx-background-color: #21262d; -fx-text-fill: #f0f6fc; -fx-prompt-text-fill: #484f58; -fx-font-size: 14px; -fx-padding: 10 14; -fx-background-radius: 6; -fx-border-color: #30363d; -fx-border-radius: 6; -fx-border-width: 1; -fx-max-width: 260;";

    public LoginScreen(GameClient gameClient) {
        this.gameClient = gameClient;
    }

    public void setError(String msg) {
        Platform.runLater(() -> {
            if (status != null) {
                status.setText(msg);
                status.setStyle("-fx-text-fill: #f85149; -fx-font-size: 13px;");
            }
        });
    }

    public String getErrorMessage() {
        if (status != null) {
            String text = status.getText();
            String style = status.getStyle();
            if (text != null && !text.isEmpty() && style != null && style.contains("#f85149")) {
                return text;
            }
        }
        return null;
    }

    public String getUsername() { return user != null ? user.getText() : null; }
    public String getPassword() { return pass != null ? pass.getText() : null; }

    public Scene createScene(Stage stage) {
        VBox form = new VBox(12);
        form.setAlignment(Pos.CENTER);
        form.setMaxWidth(320);
        form.setStyle(Styles.PANEL);

        Label title = new Label("MULTIPLAYER\nSHOOTER");
        title.setStyle("-fx-text-fill: #58a6ff; -fx-font-size: 26px; -fx-font-weight: bold; -fx-text-alignment: center; -fx-line-spacing: 2;");

        Label subtitle = new Label("Inicia sesión para jugar");
        subtitle.setStyle("-fx-text-fill: #8b949e; -fx-font-size: 13px;");

        user = new TextField("player1");
        user.setPromptText("Usuario");
        user.setStyle(INPUT_STYLE);

        pass = new PasswordField();
        pass.setPromptText("Contraseña");
        pass.setText("pass1");
        pass.setStyle(INPUT_STYLE);

        status = new Label();
        status.setStyle("-fx-text-fill: #d29922; -fx-font-size: 13px;");

        btn = new Button("Connect & Login");
        Styles.setBtnStyle(btn, Styles.ACCENT, Styles.ACCENT_HOVER);
        btn.setMaxWidth(260);
        btn.setOnAction(e -> doLogin());

        toggleLink = new Hyperlink("¿No tienes cuenta? Regístrate");
        toggleLink.setId("toggleLink");
        toggleLink.setStyle("-fx-text-fill: #58a6ff; -fx-font-size: 12px; -fx-border-color: transparent; -fx-underline: false;");
        toggleLink.setOnAction(e -> toggleMode());

        HBox bottomRow = new HBox(10);
        bottomRow.setAlignment(Pos.CENTER);

        helpBtn = new Button("Ayuda");
        Styles.setBtnStyle(helpBtn, Styles.BG_INPUT, Styles.BORDER);
        helpBtn.setOnAction(e -> {
            helpOverlay.setVisible(true);
            helpOverlay.setManaged(true);
        });

        Button settingsBtn = new Button("Ajustes");
        Styles.setBtnStyle(settingsBtn, Styles.BG_INPUT, Styles.BORDER);
        settingsBtn.setOnAction(e -> {
            settingsOverlay.setVisible(true);
            settingsOverlay.setManaged(true);
        });

        exitBtn = new Button("Salir");
        Styles.setBtnStyle(exitBtn, Styles.DANGER, Styles.DANGER_HOVER);
        exitBtn.setOnAction(e -> Platform.exit());

        bottomRow.getChildren().addAll(helpBtn, settingsBtn, exitBtn);

        form.getChildren().addAll(title, subtitle, user, pass, btn, toggleLink, status, bottomRow);

        helpOverlay = createHelpOverlay();
        helpOverlay.setVisible(false);
        helpOverlay.setManaged(false);

        settingsOverlay = createSettingsOverlay();
        settingsOverlay.setVisible(false);
        settingsOverlay.setManaged(false);

        StackPane centerStack = new StackPane(form, helpOverlay, settingsOverlay);
        centerStack.setStyle("-fx-background-color: linear-gradient(to bottom, #0d1117, #161b22);");

        BorderPane root = new BorderPane();
        root.setTop(TitleBar.create("Shooter Game", stage));
        root.setCenter(centerStack);

        Scene scene = new Scene(root, ClientConfig.WIDTH, ClientConfig.HEIGHT + TitleBar.HEIGHT);
        return scene;
    }

    private VBox createHelpOverlay() {
        VBox overlay = new VBox(12);
        overlay.setAlignment(Pos.CENTER);
        overlay.setStyle("-fx-background-color: rgba(13, 17, 23, 0.92); -fx-padding: 30; -fx-background-radius: 8;");

        Label helpTitle = new Label("AYUDA - CONTROLES");
        helpTitle.setStyle("-fx-text-fill: #f0f6fc; -fx-font-size: 20px; -fx-font-weight: bold;");

        String[] lines = {
            "WASD / Flechas ................. Moverse",
            "Mouse ......................... Apuntar",
            "Click izquierdo ............... Disparar",
            "SHIFT ......................... Correr",
            "",
            "Objetivo: Sé el último jugador en pie.",
            "Elimina a tus oponentes para ganar."
        };
        VBox textBox = new VBox(3);
        textBox.setAlignment(Pos.CENTER);
        for (String line : lines) {
            Label l = new Label(line);
            l.setStyle("-fx-text-fill: #8b949e; -fx-font-size: 14px;");
            textBox.getChildren().add(l);
        }

        Button closeBtn = new Button("Volver");
        Styles.setBtnStyle(closeBtn, Styles.ACCENT, Styles.ACCENT_HOVER);
        closeBtn.setOnAction(e -> {
            overlay.setVisible(false);
            overlay.setManaged(false);
        });

        overlay.getChildren().addAll(helpTitle, textBox, closeBtn);
        return overlay;
    }

    private VBox createSettingsOverlay() {
        VBox overlay = new VBox(12);
        overlay.setAlignment(Pos.CENTER);
        overlay.setStyle("-fx-background-color: rgba(13, 17, 23, 0.92); -fx-padding: 30; -fx-background-radius: 8;");
        overlay.setMaxWidth(360);

        Label settingsTitle = new Label("AJUSTES - MCP");
        settingsTitle.setStyle("-fx-text-fill: #f0f6fc; -fx-font-size: 20px; -fx-font-weight: bold;");

        VBox fields = new VBox(8);
        fields.setAlignment(Pos.CENTER);

        hostField = new TextField(ClientConfig.getServerHost());
        hostField.setPromptText("Servidor host");
        hostField.setStyle("-fx-background-color: #21262d; -fx-text-fill: #f0f6fc; -fx-prompt-text-fill: #484f58; -fx-font-size: 13px; -fx-padding: 8 12; -fx-background-radius: 6; -fx-border-color: #30363d; -fx-border-radius: 6; -fx-border-width: 1; -fx-max-width: 280;");

        portField = new TextField(String.valueOf(ClientConfig.getServerPort()));
        portField.setPromptText("Puerto");
        portField.setStyle(hostField.getStyle());

        Label mcpLabel = new Label("Conexión MCP (stdio):");
        mcpLabel.setStyle("-fx-text-fill: #8b949e; -fx-font-size: 13px;");

        mcpStatusLabel = new Label("MCP: desactivado");
        mcpStatusLabel.setStyle("-fx-text-fill: #8b949e; -fx-font-size: 12px;");

        mcpToggleBtn = new Button("Activar MCP");
        Styles.setBtnStyle(mcpToggleBtn, Styles.BG_INPUT, Styles.BORDER);
        mcpToggleBtn.setMaxWidth(200);
        mcpToggleBtn.setOnAction(e -> {
            ScreenManager sm = gameClient.getScreenManager();
            boolean on = sm.toggleMcpMode();
            mcpToggleBtn.setText(on ? "Desactivar MCP" : "Activar MCP");
            mcpStatusLabel.setText(on ? "MCP: activado" : "MCP: desactivado");
            mcpStatusLabel.setStyle(on ?
                "-fx-text-fill: #3fb950; -fx-font-size: 12px;" :
                "-fx-text-fill: #8b949e; -fx-font-size: 12px;");
        });

        Button saveBtn = new Button("Guardar y aplicar");
        Styles.setBtnStyle(saveBtn, Styles.ACCENT, Styles.ACCENT_HOVER);
        saveBtn.setMaxWidth(280);
        saveBtn.setOnAction(e -> {
            String host = hostField.getText().trim();
            String port = portField.getText().trim();
            if (!host.isEmpty() && !port.isEmpty()) {
                ClientConfig.setServerUrl(host, Integer.parseInt(port));
            }
            settingsOverlay.setVisible(false);
            settingsOverlay.setManaged(false);
        });

        Button closeBtn = new Button("Volver");
        Styles.setBtnStyle(closeBtn, Styles.ACCENT, Styles.ACCENT_HOVER);
        closeBtn.setOnAction(e -> {
            settingsOverlay.setVisible(false);
            settingsOverlay.setManaged(false);
        });

        HBox btnRow = new HBox(10);
        btnRow.setAlignment(Pos.CENTER);
        btnRow.getChildren().addAll(saveBtn, closeBtn);

        fields.getChildren().addAll(
            new Label("Servidor:") {{
                setStyle("-fx-text-fill: #f0f6fc; -fx-font-size: 13px; -fx-font-weight: bold;");
            }},
            hostField, portField,
            new Separator() {{ setStyle("-fx-max-width: 280;"); }},
            mcpLabel, mcpStatusLabel, mcpToggleBtn
        );

        overlay.getChildren().addAll(settingsTitle, fields, btnRow);
        return overlay;
    }

    private void toggleMode() {
        registerMode = !registerMode;
        if (registerMode) {
            btn.setText("Register & Login");
            toggleLink.setText("Ya tienes cuenta? Inicia sesión");
            status.setText("Modo registro");
            status.setStyle("-fx-text-fill: #d29922; -fx-font-size: 13px;");
        } else {
            btn.setText("Connect & Login");
            toggleLink.setText("¿No tienes cuenta? Regístrate");
            status.setText("");
        }
    }

    private void doLogin() {
        btn.setDisable(true);
        AudioManager.playClick();
        status.setText("Conectando...");
        status.setStyle("-fx-text-fill: #d29922; -fx-font-size: 13px;");

        String username = user.getText().trim();
        String password = pass.getText().trim();

        if (username.isEmpty() || password.isEmpty()) {
            status.setText("Usuario y contraseña requeridos");
            status.setStyle("-fx-text-fill: #f85149; -fx-font-size: 13px;");
            btn.setDisable(false);
            return;
        }

        new Thread(() -> {
            boolean ok = gameClient.connect();
            Platform.runLater(() -> {
                if (ok) {
                    status.setText("Conectado. Enviando login...");
                    status.setStyle("-fx-text-fill: #3fb950; -fx-font-size: 13px;");
                    gameClient.sendLogin(username, password, registerMode);
                } else {
                    status.setText("Error de conexión");
                    status.setStyle("-fx-text-fill: #f85149; -fx-font-size: 13px;");
                    btn.setDisable(false);
                }
            });
        }).start();
    }
}
