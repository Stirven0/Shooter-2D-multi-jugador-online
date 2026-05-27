package com.aa.client.ui;

import com.aa.client.game.GameClient;
import com.aa.client.util.ClientConfig;
import com.aa.shared.message.GameEndMessage;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class GameOverScreen implements IScreen {

    private final GameClient gameClient;
    private final GameEndMessage endMsg;

    public GameOverScreen(GameClient gameClient, GameEndMessage endMsg) {
        this.gameClient = gameClient;
        this.endMsg = endMsg;
    }

    public Scene createScene(Stage stage) {
        VBox content = new VBox(12);
        content.setAlignment(Pos.CENTER);
        content.setStyle("-fx-background-color: linear-gradient(to bottom, #0d1117, #161b22);");

        Label title = new Label("GAME OVER");
        title.setStyle("-fx-text-fill: #f85149; -fx-font-size: 40px; -fx-font-weight: bold; -fx-letter-spacing: 4;");

        boolean isDraw = endMsg.getWinnerUsername() == null || endMsg.getWinnerUsername().isEmpty() || "Empate".equals(endMsg.getWinnerUsername());
        Label winner = new Label(isDraw ? "Empate!" : "Ganador: " + endMsg.getWinnerUsername());
        String winStyle = isDraw
            ? "-fx-text-fill: #8b949e; -fx-font-size: 22px; -fx-font-weight: bold;"
            : "-fx-text-fill: #ffd700; -fx-font-size: 22px; -fx-font-weight: bold;";
        winner.setStyle(winStyle);

        long seconds = endMsg.getDuration() / 1000;
        Label duration = new Label("⏱  Duración: " + seconds + "s");
        duration.setStyle("-fx-text-fill: #484f58; -fx-font-size: 13px;");

        VBox scoresBox = new VBox(6);
        scoresBox.setAlignment(Pos.CENTER);
        scoresBox.setMaxWidth(360);
        scoresBox.setStyle("-fx-background-color: #161b22; -fx-background-radius: 8; -fx-border-color: #30363d; -fx-border-radius: 8; -fx-border-width: 1; -fx-padding: 20;");

        Label scoresTitle = new Label("SCOREBOARD");
        scoresTitle.setStyle("-fx-text-fill: #58a6ff; -fx-font-size: 16px; -fx-font-weight: bold;");

        HBox headerRow = new HBox();
        headerRow.setPrefWidth(320);
        Label hPos = new Label("#");
        hPos.setStyle("-fx-text-fill: #484f58; -fx-font-size: 12px; -fx-min-width: 24;");
        Label hName = new Label("Jugador");
        hName.setStyle("-fx-text-fill: #484f58; -fx-font-size: 12px; -fx-min-width: 120;");
        Region hSpacer = new Region();
        HBox.setHgrow(hSpacer, Priority.ALWAYS);
        Label hKills = new Label("K");
        hKills.setStyle("-fx-text-fill: #484f58; -fx-font-size: 12px; -fx-min-width: 28; -fx-alignment: center-right;");
        Label hDeaths = new Label("D");
        hDeaths.setStyle("-fx-text-fill: #484f58; -fx-font-size: 12px; -fx-min-width: 28; -fx-alignment: center-right;");
        headerRow.getChildren().addAll(hPos, hName, hSpacer, hKills, hDeaths);
        scoresBox.getChildren().add(scoresTitle);
        scoresBox.getChildren().add(headerRow);

        if (endMsg.getScores() != null) {
            int rank = 1;
            var sorted = new java.util.ArrayList<>(endMsg.getScores());
            sorted.sort((a, b) -> Integer.compare(b.getKills(), a.getKills()));
            for (GameEndMessage.PlayerScore score : sorted) {
                HBox row = new HBox();
                row.setPrefWidth(320);

                String posStyle = score.isWinner()
                    ? "-fx-text-fill: #ffd700; -fx-font-size: 14px; -fx-font-weight: bold; -fx-min-width: 24;"
                    : "-fx-text-fill: #8b949e; -fx-font-size: 14px; -fx-min-width: 24;";
                Label posLabel = new Label(rank == 1 ? "🥇" : rank == 2 ? "🥈" : rank == 3 ? "🥉" : rank + ".");
                posLabel.setStyle(posStyle);

                String nameStyle = score.isWinner()
                    ? "-fx-text-fill: #ffd700; -fx-font-size: 14px; -fx-font-weight: bold; -fx-min-width: 120;"
                    : "-fx-text-fill: #f0f6fc; -fx-font-size: 14px; -fx-min-width: 120;";
                Label nameLabel = new Label(score.getUsername());
                nameLabel.setStyle(nameStyle);

                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);

                String statStyle = "-fx-text-fill: #f0f6fc; -fx-font-size: 14px; -fx-min-width: 28; -fx-alignment: center-right;";
                Label killsLabel = new Label(String.valueOf(score.getKills()));
                killsLabel.setStyle(statStyle);
                Label deathsLabel = new Label(String.valueOf(score.getDeaths()));
                deathsLabel.setStyle(statStyle);

                row.getChildren().addAll(posLabel, nameLabel, spacer, killsLabel, deathsLabel);
                scoresBox.getChildren().add(row);
                rank++;
            }
        }

        Button backBtn = new Button("Volver al Lobby");
        Styles.setBtnStyle(backBtn, Styles.ACCENT, Styles.ACCENT_HOVER);
        backBtn.setOnAction(e -> gameClient.getScreenManager().showLobby());

        content.getChildren().addAll(title, winner, duration, scoresBox, backBtn);

        BorderPane root = new BorderPane();
        root.setTop(TitleBar.create("Game Over", stage));
        root.setCenter(content);

        Scene scene = new Scene(root);
        ScreenManager.addFullScreenHandler(scene, gameClient.getScreenManager());
        return scene;
    }
}
