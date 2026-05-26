package com.aa.client.ui;

import javafx.scene.control.Button;

public final class Styles {

    private Styles() {}

    // Palette
    public static final String BG_DARK = "#0d1117";
    public static final String BG_PANEL = "#161b22";
    public static final String BG_INPUT = "#21262d";
    public static final String BORDER = "#30363d";
    public static final String TEXT_PRIMARY = "#f0f6fc";
    public static final String TEXT_SECONDARY = "#8b949e";
    public static final String ACCENT = "#58a6ff";
    public static final String ACCENT_HOVER = "#79b8ff";
    public static final String SUCCESS = "#2ea043";
    public static final String SUCCESS_HOVER = "#3fb950";
    public static final String DANGER = "#da3633";
    public static final String DANGER_HOVER = "#f85149";
    public static final String WARNING = "#d29922";
    public static final String GOLD = "#ffd700";

    // Panels
    public static final String PANEL = "-fx-background-color: " + BG_PANEL + "; -fx-background-radius: 8; -fx-border-color: " + BORDER + "; -fx-border-radius: 8; -fx-border-width: 1; -fx-padding: 16;";
    public static final String PANEL_NO_BORDER = "-fx-background-color: " + BG_PANEL + "; -fx-background-radius: 8; -fx-padding: 16;";

    public static String button(String bg, String hoverBg) {
        return "-fx-background-color: " + bg + "; -fx-text-fill: white; -fx-font-size: 13px; -fx-padding: 8 18; -fx-background-radius: 6; -fx-cursor: hand; -fx-border-color: " + BORDER + "; -fx-border-radius: 6; -fx-border-width: 1;";
    }

    public static String btnDanger() { return button(DANGER, DANGER_HOVER); }
    public static String btnPrimary() { return button(ACCENT, ACCENT_HOVER); }
    public static String btnSuccess() { return button(SUCCESS, SUCCESS_HOVER); }
    public static String btnDefault() { return button(BG_INPUT, BORDER); }

    public static String input() {
        return "-fx-background-color: " + BG_INPUT + "; -fx-text-fill: " + TEXT_PRIMARY + "; -fx-font-size: 13px; -fx-padding: 6 10; -fx-background-radius: 4; -fx-border-color: " + BORDER + "; -fx-border-radius: 4; -fx-border-width: 1;";
    }

    public static void setBtnStyle(Button btn, String bg, String hoverBg) {
        String normal = button(bg, hoverBg);
        String hover = button(hoverBg, hoverBg);
        btn.setStyle(normal);
        btn.setOnMouseEntered(e -> btn.setStyle(hover));
        btn.setOnMouseExited(e -> btn.setStyle(normal));
    }
}
