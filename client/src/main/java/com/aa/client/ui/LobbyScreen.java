package com.aa.client.ui;

import com.aa.client.game.GameClient;
import com.aa.client.util.ClientConfig;
import com.aa.shared.message.RoomListResponseMessage;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import java.util.List;
import java.util.Map;
import javafx.stage.Stage;

public class LobbyScreen implements IScreen {

    private static final Map<String, String> MAP_NAMES = Map.of(
        "map_01", "Warehouse",
        "map_02", "Desert Outpost",
        "map_03", "Frost Arena",
        "map_04", "Fortress"
    );

    private final GameClient gameClient;
    private Label roomInfoLabel;
    private Label roomStatusLabel;
    private ComboBox<String> mapSelector;
    private ListView<String> playerList;
    private volatile String currentHostId;
    private ListView<String> roomListView;
    private List<RoomListResponseMessage.RoomInfo> cachedRooms;
    private Label errorLabel;
    private VBox myRoomPanel;
    private SettingsOverlay settingsOverlay;
    private Timeline refreshTimer;

    private static final String LIST_STYLE = "-fx-control-inner-background: #161b22; -fx-control-inner-background-alt: #1c2128; -fx-text-fill: #f0f6fc; -fx-background-radius: 6; -fx-border-color: #30363d; -fx-border-radius: 6; -fx-border-width: 1; -fx-selection-bar: #1f6feb; -fx-selection-bar-non-focused: #21262d;";
    private static final String LABEL_ACCENT = "-fx-text-fill: #58a6ff; -fx-font-size: 14px; -fx-font-weight: bold;";
    private static final String LABEL_GREEN = "-fx-text-fill: #2ea043; -fx-font-size: 14px; -fx-font-weight: bold;";
    private static final String LABEL_GOLD = "-fx-text-fill: #d29922; -fx-font-size: 14px; -fx-font-weight: bold;";

    public LobbyScreen(GameClient gameClient) {
        this.gameClient = gameClient;
    }

    public Scene createScene(Stage stage) {
        BorderPane content = new BorderPane();
        content.setStyle("-fx-background-color: linear-gradient(to bottom, #0d1117, #161b22);");
        content.setPadding(new Insets(20));

        HBox topBar = new HBox(10);
        topBar.setAlignment(Pos.CENTER_LEFT);

        String username = gameClient.getCurrentUsername();
        Label userLabel = new Label("👤 " + (username != null ? username : "?"));
        userLabel.setStyle("-fx-text-fill: #58a6ff; -fx-font-size: 14px; -fx-font-weight: bold;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label versionLabel = new Label("v1.0");
        versionLabel.setStyle("-fx-text-fill: #484f58; -fx-font-size: 11px; -fx-padding: 0 10;");

        Button settingsBtn = new Button("Ajustes");
        Styles.setBtnStyle(settingsBtn, Styles.BG_INPUT, Styles.BORDER);
        settingsBtn.setOnAction(e -> settingsOverlay.show());

        Button logoutBtn = new Button("Cerrar sesión");
        Styles.setBtnStyle(logoutBtn, Styles.DANGER, Styles.DANGER_HOVER);

        logoutBtn.setOnAction(e -> gameClient.logout());

        topBar.getChildren().addAll(userLabel, spacer, versionLabel, settingsBtn, logoutBtn);

        errorLabel = new Label();
        errorLabel.setStyle("-fx-text-fill: #f85149; -fx-font-size: 12px;");
        errorLabel.setVisible(false);

        HBox center = new HBox(20);
        center.setAlignment(Pos.TOP_CENTER);

        VBox left = new VBox(12);
        left.setPrefWidth(300);

        myRoomPanel = new VBox(10);
        myRoomPanel.setStyle(Styles.PANEL);

        Label myRoomTitle = new Label("MI SALA");
        myRoomTitle.setStyle(LABEL_GOLD);

        roomInfoLabel = new Label("No estás en ninguna sala");
        roomInfoLabel.setStyle("-fx-text-fill: #484f58; -fx-font-size: 12px;");

        roomStatusLabel = new Label();
        roomStatusLabel.setStyle("-fx-text-fill: #8b949e; -fx-font-size: 11px;");

        playerList = new ListView<>();
        playerList.setMaxHeight(140);
        playerList.setPrefHeight(140);
        playerList.setStyle(LIST_STYLE);

        HBox roomActions = new HBox(8);
        roomActions.setAlignment(Pos.CENTER);

        Button leaveBtn = new Button("Salir de sala");
        Styles.setBtnStyle(leaveBtn, Styles.BG_INPUT, Styles.BORDER);
        leaveBtn.setOnAction(e -> gameClient.leaveRoom());

        Button startBtn = new Button("Iniciar partida");
        Styles.setBtnStyle(startBtn, Styles.SUCCESS, Styles.SUCCESS_HOVER);
        startBtn.setOnAction(e -> gameClient.startGame());

        roomActions.getChildren().addAll(leaveBtn, startBtn);

        myRoomPanel.getChildren().addAll(myRoomTitle, roomInfoLabel, roomStatusLabel, playerList, roomActions);

        VBox createPanel = new VBox(10);
        createPanel.setStyle(Styles.PANEL);

        Label createTitle = new Label("CREAR SALA");
        createTitle.setStyle(LABEL_GREEN);

        mapSelector = new ComboBox<>();
        for (var entry : MAP_NAMES.entrySet()) {
            mapSelector.getItems().add(entry.getValue() + " (" + entry.getKey() + ")");
        }
        mapSelector.setValue("Warehouse (map_01)");
        mapSelector.setStyle("-fx-background-color: #21262d; -fx-text-fill: #f0f6fc; -fx-font-size: 13px; -fx-background-radius: 6; -fx-border-color: #30363d; -fx-border-radius: 6;");

        Button createBtn = new Button("Crear sala");
        Styles.setBtnStyle(createBtn, Styles.ACCENT, Styles.ACCENT_HOVER);
        createBtn.setMaxWidth(Double.MAX_VALUE);
        createBtn.setOnAction(e -> {
            String selected = mapSelector.getValue();
            String mapId = selected.substring(selected.lastIndexOf('(') + 1, selected.lastIndexOf(')'));
            gameClient.createRoom(mapId);
            createBtn.setDisable(true);
        });

        createPanel.getChildren().addAll(createTitle, mapSelector, createBtn);

        left.getChildren().addAll(myRoomPanel, createPanel);

        VBox right = new VBox(12);
        right.setPrefWidth(380);

        VBox roomsPanel = new VBox(10);
        roomsPanel.setStyle(Styles.PANEL);

        HBox roomsHeader = new HBox(8);
        roomsHeader.setAlignment(Pos.CENTER_LEFT);

        Label roomsTitle = new Label("SALAS DISPONIBLES");
        roomsTitle.setStyle(LABEL_ACCENT);

        Region roomsSpacer = new Region();
        HBox.setHgrow(roomsSpacer, Priority.ALWAYS);

        String btnStyleAux = "-fx-background-color: #21262d; -fx-text-fill: #f0f6fc; -fx-font-size: 12px; -fx-padding: 6 14; -fx-background-radius: 6; -fx-cursor: hand; -fx-border-color: #30363d; -fx-border-radius: 6; -fx-border-width: 1;";
        Button refreshBtn = new Button("↻ Refrescar");
        refreshBtn.setStyle(btnStyleAux);
        refreshBtn.setOnMouseEntered(e -> refreshBtn.setStyle(btnStyleAux.replace("#21262d", "#30363d")));
        refreshBtn.setOnMouseExited(e -> refreshBtn.setStyle(btnStyleAux));
        refreshBtn.setOnAction(e -> gameClient.requestRoomList());

        roomsHeader.getChildren().addAll(roomsTitle, roomsSpacer, refreshBtn);

        roomListView = new ListView<>();
        roomListView.setPrefHeight(220);
        roomListView.setStyle(LIST_STYLE);
        roomListView.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                int idx = roomListView.getSelectionModel().getSelectedIndex();
                if (idx >= 0 && idx < cachedRooms.size()) {
                    gameClient.joinRoom(cachedRooms.get(idx).getRoomId());
                }
            }
        });

        Button joinSelectedBtn = new Button("Unirse a sala seleccionada");
        Styles.setBtnStyle(joinSelectedBtn, Styles.SUCCESS, Styles.SUCCESS_HOVER);
        joinSelectedBtn.setMaxWidth(Double.MAX_VALUE);
        joinSelectedBtn.setOnAction(e -> {
            int idx = roomListView.getSelectionModel().getSelectedIndex();
            if (idx >= 0 && idx < cachedRooms.size()) {
                gameClient.joinRoom(cachedRooms.get(idx).getRoomId());
            } else {
                setError("Selecciona una sala de la lista");
            }
        });

        roomsPanel.getChildren().addAll(roomsHeader, roomListView, joinSelectedBtn);
        right.getChildren().add(roomsPanel);

        center.getChildren().addAll(left, right);

        VBox topSection = new VBox(8);
        topSection.getChildren().addAll(topBar, errorLabel);

        content.setTop(topSection);
        content.setCenter(center);

        settingsOverlay = new SettingsOverlay(gameClient, stage, () -> {});
        settingsOverlay.getRoot().setVisible(false);
        settingsOverlay.getRoot().setManaged(false);

        StackPane stack = new StackPane(content, settingsOverlay.getRoot());

        BorderPane root = new BorderPane();
        root.setTop(TitleBar.create("Lobby", stage, true));
        root.setCenter(stack);

        Scene scene = new Scene(root);

        gameClient.requestRoomList();

        refreshTimer = new Timeline(new KeyFrame(Duration.seconds(3), e -> gameClient.requestRoomList()));
        refreshTimer.setCycleCount(Timeline.INDEFINITE);
        refreshTimer.play();

        ScreenManager.addFullScreenHandler(scene, gameClient.getScreenManager());

        return scene;
    }

    public void setError(String msg) {
        Platform.runLater(() -> {
            if (errorLabel != null) {
                errorLabel.setText(msg);
                errorLabel.setVisible(true);
            }
        });
    }

    public String getErrorMessage() {
        if (errorLabel != null && errorLabel.isVisible()) {
            return errorLabel.getText();
        }
        return null;
    }

    public String getCurrentRoomId() {
        if (roomInfoLabel != null && roomInfoLabel.getText() != null && roomInfoLabel.getText().contains("🆔")) {
            String text = roomInfoLabel.getText();
            int idx = text.indexOf(": ");
            if (idx >= 0) return text.substring(idx + 2);
        }
        return null;
    }

    public List<RoomListResponseMessage.RoomInfo> getCachedRooms() {
        return cachedRooms;
    }

    public void updateRoomInfo(String roomId) {
        Platform.runLater(() -> {
            roomInfoLabel.setText("🆔 Sala: " + roomId);
            roomInfoLabel.setStyle("-fx-text-fill: #58a6ff; -fx-font-size: 13px; -fx-font-weight: bold;");
            roomStatusLabel.setText("⏳ Esperando jugadores...");
            roomStatusLabel.setStyle("-fx-text-fill: #d29922; -fx-font-size: 12px;");
        });
    }

    public void updatePlayerList(List<String> playerIds, String hostId, Map<String, String> usernames) {
        this.currentHostId = hostId;
        Platform.runLater(() -> {
            playerList.getItems().clear();
            String localId = gameClient.getClientState().getLocalPlayerId();
            for (String pid : playerIds) {
                boolean isHost = pid.equals(hostId);
                boolean isMe = pid.equals(localId);
                String name = usernames != null ? usernames.getOrDefault(pid, pid) : pid;
                String display = (isHost ? "👑 " : "   ") + name;
                if (isMe) display += " (tú)";
                playerList.getItems().add(display);
            }
            roomStatusLabel.setText("👥 " + playerIds.size() + " jugador(es) en sala");
            roomStatusLabel.setStyle("-fx-text-fill: #8b949e; -fx-font-size: 12px;");
        });
    }

    public void updateRoomList(List<RoomListResponseMessage.RoomInfo> rooms) {
        this.cachedRooms = rooms;
        Platform.runLater(() -> {
            roomListView.getItems().clear();
            if (rooms.isEmpty()) {
                roomListView.getItems().add("(No hay salas disponibles)");
            }
            for (RoomListResponseMessage.RoomInfo r : rooms) {
                String mapName = MAP_NAMES.getOrDefault(r.getMapId(), r.getMapId());
                roomListView.getItems().add(
                    r.getRoomId() + "  |  " + mapName + "  |  " +
                    r.getPlayerCount() + "/" + r.getMaxPlayers() + " jugs  |  " + r.getStatus()
                );
            }
        });
    }
}
