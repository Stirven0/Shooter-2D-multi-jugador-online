package com.aa.client.render;

import com.aa.shared.model.Player;
import com.aa.shared.model.SkillSlot;
import com.aa.shared.model.WeaponType;
import com.aa.shared.state.GameState;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

public class HudRenderer {

    private double fps;
    private boolean showDebug;

    public void setFps(double fps) { this.fps = fps; }
    public void setShowDebug(boolean v) { this.showDebug = v; }
    public boolean isShowDebug() { return showDebug; }

    public void render(GraphicsContext gc, GameState state, String localPlayerId) {
        drawHud(gc, state, localPlayerId);
    }

    private void drawHud(GraphicsContext gc, GameState state, String localPlayerId) {
        Player local = state.getPlayer(localPlayerId);
        if (local == null) return;

        double cw = gc.getCanvas().getWidth();
        double ch = gc.getCanvas().getHeight();
        drawHpBar(gc, local, 16, ch - 40, 180, 16);
        drawShieldBar(gc, local, 16, ch - 20, 180, 6);
        drawWeaponInfo(gc, local, cw - 200, ch - 60);
        drawUpgradePoints(gc, local, cw, ch);
        drawSkills(gc, local, cw, ch);
        drawScoreboard(gc, state, localPlayerId, cw);
    }

    private void drawHpBar(GraphicsContext gc, Player local, double x, double y, double w, double h) {
        gc.setFill(Color.rgb(13, 17, 23, 0.8));
        gc.fillRoundRect(x, y, w, h, 4, 4);
        gc.setStroke(Color.rgb(48, 54, 61));
        gc.setLineWidth(1);
        gc.strokeRoundRect(x, y, w, h, 4, 4);

        double ratio = local.getHealth() / 100.0;
        Color hpColor = ratio > 0.6 ? Color.rgb(46, 160, 67) : ratio > 0.3 ? Color.rgb(210, 153, 34) : Color.rgb(248, 81, 73);
        double fillW = (w - 4) * ratio;
        if (fillW > 0) {
            gc.setFill(hpColor);
            gc.fillRoundRect(x + 2, y + 2, fillW, h - 4, 3, 3);
        }

        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("Monospace", 11));
        gc.setTextAlign(TextAlignment.RIGHT);
        gc.fillText((int) local.getHealth() + " HP", x + w - 6, y + 12);
    }

    private void drawShieldBar(GraphicsContext gc, Player local, double x, double y, double w, double h) {
        if (local.getShield() <= 0) return;
        gc.setFill(Color.rgb(13, 17, 23, 0.8));
        gc.fillRoundRect(x, y, w, h, 3, 3);
        double ratio = Math.min(local.getShield() / 40.0, 1.0);
        gc.setFill(Color.rgb(88, 166, 255));
        gc.fillRoundRect(x + 1, y + 1, (w - 2) * ratio, h - 2, 2, 2);
    }

    private void drawWeaponInfo(GraphicsContext gc, Player local, double x, double y) {
        gc.setFill(Color.rgb(13, 17, 23, 0.8));
        gc.fillRoundRect(x, y, 190, 50, 6, 6);
        gc.setStroke(Color.rgb(48, 54, 61));
        gc.setLineWidth(1);
        gc.strokeRoundRect(x, y, 190, 50, 6, 6);

        WeaponType current = local.getCurrentWeapon();
        gc.setFill(Color.rgb(88, 166, 255));
        gc.setFont(Font.font("Monospace", 14));
        gc.setTextAlign(TextAlignment.LEFT);
        gc.fillText(current.getDisplayName(), x + 10, y + 20);

        gc.setFill(Color.rgb(139, 148, 158));
        gc.setFont(Font.font("Monospace", 10));
        gc.fillText("Slot: " + (local.getCurrentWeaponSlot() == 0 ? "1" : "2") + "  [Q]", x + 10, y + 40);

        if (local.getSecondaryWeapon() != null) {
            gc.setFill(Color.rgb(48, 54, 61));
            gc.fillText("Slot " + (local.getCurrentWeaponSlot() == 0 ? "2" : "1") + ": " + local.getSecondaryWeapon().getDisplayName(), x + 10, y + 55);
        }
    }

    private void drawUpgradePoints(GraphicsContext gc, Player local, double cw, double ch) {
        if (local.getUpgradePoints() <= 0) return;
        gc.setFill(Color.rgb(210, 153, 34));
        gc.setFont(Font.font("Monospace", 11));
        gc.setTextAlign(TextAlignment.RIGHT);
        gc.fillText("Mejora x" + local.getUpgradePoints(), cw - 16, ch - 80);
    }

    private void drawSkills(GraphicsContext gc, Player local, double cw, double ch) {
        double skillY = ch - 40;
        for (int i = 0; i < 2; i++) {
            SkillSlot slot = local.getSkillSlots() != null && i < local.getSkillSlots().length ? local.getSkillSlots()[i] : null;
            if (slot == null || slot.getSkill() == null) continue;
            double skX = cw / 2 + (i - 1) * 80;

            gc.setFill(Color.rgb(13, 17, 23, 0.8));
            gc.fillRoundRect(skX - 30, skillY - 12, 60, 24, 4, 4);
            gc.setStroke(Color.rgb(48, 54, 61));
            gc.setLineWidth(1);
            gc.strokeRoundRect(skX - 30, skillY - 12, 60, 24, 4, 4);

            String keyLabel = i == 0 ? "[E]" : "[F]";
            gc.setFill(Color.rgb(139, 148, 158));
            gc.setFont(Font.font("Monospace", 9));
            gc.setTextAlign(TextAlignment.CENTER);
            gc.fillText(keyLabel, skX, skillY + 4);

            if (slot.getCooldownRemaining() > 0) {
                gc.setFill(Color.rgb(248, 81, 73, 0.5));
                gc.fillText(String.format("%.1f", slot.getCooldownRemaining()), skX, skillY + 20);
            } else {
                gc.setFill(Color.rgb(88, 166, 255));
                gc.setFont(Font.font("Monospace", 10));
                gc.fillText(slot.getSkill().getDisplayName(), skX, skillY + 20);
            }
        }
    }

    private void drawScoreboard(GraphicsContext gc, GameState state, String localPlayerId, double cw) {
        double sbX = cw - 200;
        double sbY = 10;
        int rows = state.getAllPlayers().size();
        double sbH = 28 + rows * 20;
        gc.setFill(Color.rgb(13, 17, 23, 0.8));
        gc.fillRoundRect(sbX - 6, sbY - 4, 196, sbH, 6, 6);
        gc.setStroke(Color.rgb(48, 54, 61));
        gc.setLineWidth(1);
        gc.strokeRoundRect(sbX - 6, sbY - 4, 196, sbH, 6, 6);

        gc.setFill(Color.rgb(139, 148, 158));
        gc.setFont(Font.font("Monospace", 10));
        gc.setTextAlign(TextAlignment.LEFT);
        gc.fillText("JUGADOR", sbX, sbY + 10);
        gc.setTextAlign(TextAlignment.RIGHT);
        gc.fillText("K  D", sbX + 180, sbY + 10);

        gc.setStroke(Color.rgb(48, 54, 61));
        gc.setLineWidth(1);
        gc.strokeLine(sbX, sbY + 16, sbX + 180, sbY + 16);

        int i = 1;
        List<Player> sorted = new ArrayList<>(state.getAllPlayers());
        sorted.sort(Comparator.comparingInt(Player::getKills).reversed());
        for (Player p : sorted) {
            double y = sbY + 14 + i * 20;
            boolean isLocal = p.getId().equals(localPlayerId);
            gc.setFill(isLocal ? Color.rgb(88, 166, 255) : Color.rgb(240, 246, 252));
            gc.setFont(Font.font("Monospace", 12));
            gc.setTextAlign(TextAlignment.LEFT);
            gc.fillText(p.getUsername(), sbX, y);
            gc.setTextAlign(TextAlignment.RIGHT);
            gc.fillText(p.getKills() + "  " + p.getDeaths(), sbX + 180, y);
            i++;
        }
    }

    public void drawDebugOverlay(GraphicsContext gc, GameState state, String localPlayerId) {
        if (!showDebug) return;
        gc.setFill(Color.rgb(13, 17, 23, 0.75));
        gc.fillRoundRect(5, 5, 240, 210, 6, 6);
        gc.setStroke(Color.rgb(48, 54, 61));
        gc.setLineWidth(1);
        gc.strokeRoundRect(5, 5, 240, 210, 6, 6);

        gc.setFill(Color.rgb(88, 166, 255));
        gc.setFont(Font.font("Monospace", 12));
        gc.setTextAlign(TextAlignment.LEFT);

        Player local = state.getPlayer(localPlayerId);
        int alive = (int) state.getAllPlayers().stream().filter(Player::isAlive).count();
        int total = state.getAllPlayers().size();

        int y = 22;
        int lh = 16;
        gc.fillText(String.format("FPS: %.0f", fps), 12, y); y += lh;
        gc.fillText("Tick: " + state.getTick(), 12, y); y += lh;
        gc.fillText("Jugadores: " + alive + "/" + total + " vivos", 12, y); y += lh;
        gc.fillText("Balas: " + state.getAllBullets().size(), 12, y); y += lh;
        if (local != null) {
            gc.fillText("Pos: %.0f, %.0f".formatted(local.getPosition().x(), local.getPosition().y()), 12, y); y += lh;
            gc.fillText("HP: %.0f".formatted(local.getHealth()), 12, y); y += lh;
            gc.fillText("Arma: " + local.getCurrentWeapon().getDisplayName(), 12, y); y += lh;
            gc.fillText("Puntos mejora: " + local.getUpgradePoints(), 12, y); y += lh;
            String status = state.getStatus() != null ? state.getStatus().name() : "?";
            gc.fillText("Estado: " + status, 12, y); y += lh;
        }
        gc.setFill(Color.rgb(139, 148, 158));
        gc.fillText("F3: ocultar debug", 12, y);
    }
}
