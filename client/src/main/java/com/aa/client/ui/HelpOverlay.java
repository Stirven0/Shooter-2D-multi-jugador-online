package com.aa.client.ui;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

public final class HelpOverlay {

    private static final String OVERLAY_STYLE = "-fx-background-color: rgba(13, 17, 23, 0.92); -fx-padding: 30; -fx-background-radius: 8;";
    private static final String TITLE_STYLE = "-fx-text-fill: #f0f6fc; -fx-font-size: 20px; -fx-font-weight: bold;";
    private static final String TEXT_STYLE = "-fx-text-fill: #8b949e; -fx-font-size: 14px;";

    private HelpOverlay() {}

    public static VBox create(boolean includeGameControls) {
        VBox overlay = new VBox(12);
        overlay.setAlignment(Pos.CENTER);
        overlay.setStyle(OVERLAY_STYLE);
        overlay.setVisible(false);
        overlay.setManaged(false);

        Label title = new Label("AYUDA - CONTROLES");
        title.setStyle(TITLE_STYLE);

        String[] lines = includeGameControls
            ? new String[] {
                "WASD / Flechas ................. Moverse",
                "Mouse ......................... Apuntar",
                "Click izquierdo ............... Disparar",
                "Q ............................. Cambiar arma",
                "E ............................. Skill 1 (ej. Dash, EMP)",
                "F ............................. Skill 2 (ej. Shield, Heal)",
                "SHIFT ......................... Correr",
                "ESC ........................... Pausa / Ajustes",
                "F11 ........................... Pantalla completa",
                "F3 ............................ Debug",
                "",
                "Objetivo: Sé el último jugador en pie.",
                "Elimina a tus oponentes para ganar."}
            : new String[] {
                "WASD / Flechas ................. Moverse",
                "Mouse ......................... Apuntar",
                "Click izquierdo ............... Disparar",
                "SHIFT ......................... Correr",
                "",
                "Objetivo: Sé el último jugador en pie.",
                "Elimina a tus oponentes para ganar."};

        VBox textBox = new VBox(3);
        textBox.setAlignment(Pos.CENTER);
        for (String line : lines) {
            Label l = new Label(line);
            l.setStyle(TEXT_STYLE);
            textBox.getChildren().add(l);
        }

        Button closeBtn = new Button("Volver");
        Styles.setBtnStyle(closeBtn, Styles.ACCENT, Styles.ACCENT_HOVER);
        closeBtn.setOnAction(e -> {
            overlay.setVisible(false);
            overlay.setManaged(false);
        });

        overlay.getChildren().addAll(title, textBox, closeBtn);
        return overlay;
    }
}
