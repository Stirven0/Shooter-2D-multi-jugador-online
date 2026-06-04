package com.aa.client.render;

import com.aa.client.asset.SpriteManager;
import com.aa.shared.model.Bullet;
import com.aa.shared.model.Obstacle;
import com.aa.shared.model.Player;
import com.aa.shared.model.TileMap;
import com.aa.shared.model.PowerUpPickup;
import com.aa.shared.model.PowerUpType;
import com.aa.shared.model.WeaponPickup;
import com.aa.shared.state.GameState;
import java.util.List;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

public class Renderer {
    private final Camera camera;
    private final TileRenderer tileRenderer;
    private final HudRenderer hudRenderer;
    private TileMap cachedTileMap;

    public Renderer(Camera camera) {
        this.camera = camera;
        this.tileRenderer = new TileRenderer(camera);
        this.hudRenderer = new HudRenderer();
    }

    public void setCachedTileMap(TileMap tileMap) { this.cachedTileMap = tileMap; }

    public void setShowDebug(boolean v) { hudRenderer.setShowDebug(v); }
    public void setFps(double v) { hudRenderer.setFps(v); }

    public void render(GraphicsContext gc, GameState state, String localPlayerId, double mouseScreenX, double mouseScreenY) {
        double cw = gc.getCanvas().getWidth();
        double ch = gc.getCanvas().getHeight();

        gc.setFill(Color.rgb(13, 17, 23));
        gc.fillRect(0, 0, cw, ch);

        if (state == null) {
            gc.setFill(Color.rgb(248, 81, 73));
            gc.setFont(Font.font("Monospace", 24));
            gc.setTextAlign(TextAlignment.CENTER);
            gc.fillText("NO STATE", cw/2, ch/2);
            return;
        }

        drawGrid(gc);
        if (cachedTileMap != null) {
            tileRenderer.render(gc, cachedTileMap, cw, ch);
        } else {
            drawObstacles(gc, state.getObstacles());
        }
        drawWeaponPickups(gc, state.getWeaponPickups());
        drawPowerUpPickups(gc, state.getPowerUpPickups());

        for (Player p : state.getAllPlayers()) {
            boolean isLocal = p.getId().equals(localPlayerId);
            drawPlayer(gc, p, isLocal);
            if (hudRenderer.isShowDebug()) drawPlayerHitbox(gc, p);
        }

        for (Bullet b : state.getAllBullets()) {
            drawBullet(gc, b);
            if (hudRenderer.isShowDebug()) drawBulletHitbox(gc, b);
        }

        drawCrosshair(gc, mouseScreenX, mouseScreenY);
        hudRenderer.render(gc, state, localPlayerId);
        hudRenderer.drawDebugOverlay(gc, state, localPlayerId);
    }

    private void drawGrid(GraphicsContext gc) {
        gc.setStroke(Color.rgb(48, 54, 61, 0.4));
        gc.setLineWidth(1);
        double startX = -camera.getX() % 100;
        double startY = -camera.getY() % 100;
        double w = gc.getCanvas().getWidth();
        double h = gc.getCanvas().getHeight();

        for (double x = startX; x < w; x += 100) {
            gc.strokeLine(x, 0, x, h);
        }
        for (double y = startY; y < h; y += 100) {
            gc.strokeLine(0, y, w, y);
        }
    }

    private void drawObstacles(GraphicsContext gc, List<Obstacle> obstacles) {
        if (obstacles == null) return;
        gc.setFill(Color.rgb(33, 38, 45));
        gc.setStroke(Color.rgb(48, 54, 61));
        gc.setLineWidth(2);
        for (Obstacle o : obstacles) {
            double sx = camera.worldToScreenX(o.x());
            double sy = camera.worldToScreenY(o.y());
            double sw = o.width();
            double sh = o.height();
            gc.fillRect(sx, sy, sw, sh);
            gc.strokeRect(sx, sy, sw, sh);
            gc.setStroke(Color.rgb(88, 166, 255, 0.08));
            gc.setLineWidth(1);
            gc.strokeRect(sx + 3, sy + 3, sw - 6, sh - 6);
            gc.setStroke(Color.rgb(48, 54, 61));
            gc.setLineWidth(2);
        }
    }

    private void drawWeaponPickups(GraphicsContext gc, List<WeaponPickup> pickups) {
        if (pickups == null) return;
        for (WeaponPickup wp : pickups) {
            double sx = camera.worldToScreenX(wp.getPosition().x());
            double sy = camera.worldToScreenY(wp.getPosition().y());
            double size = 20;

            gc.setFill(Color.rgb(88, 166, 255, 0.25));
            gc.fillOval(sx - size/2 - 2, sy - size/2 - 2, size + 4, size + 4);
            gc.setFill(Color.rgb(88, 166, 255));
            gc.setFont(Font.font("Monospace", 11));
            gc.setTextAlign(TextAlignment.CENTER);
            String label = switch (wp.getWeaponType()) {
                case SHOTGUN -> "W";
                case RIFLE -> "R";
                case SNIPER -> "S";
                case SMG -> "M";
                default -> "?";
            };
            gc.fillText(label, sx, sy + 4);

            gc.setFont(Font.font("Monospace", 9));
            gc.setFill(Color.rgb(139, 148, 158));
            gc.fillText(wp.getWeaponType().getDisplayName(), sx, sy + size/2 + 12);
        }
    }

    private void drawPowerUpPickups(GraphicsContext gc, List<PowerUpPickup> pickups) {
        if (pickups == null) return;
        for (PowerUpPickup pp : pickups) {
            double sx = camera.worldToScreenX(pp.getPosition().x());
            double sy = camera.worldToScreenY(pp.getPosition().y());
            double size = 16;

            Color bg = pp.getType().isDebuff() ? Color.rgb(248, 81, 73, 0.3) : Color.rgb(46, 160, 67, 0.3);
            gc.setFill(bg);
            gc.fillRoundRect(sx - size/2, sy - size/2, size, size, 4, 4);

            gc.setFill(pp.getType().isDebuff() ? Color.rgb(248, 81, 73) : Color.rgb(46, 160, 67));
            gc.setFont(Font.font("Monospace", 10));
            gc.setTextAlign(TextAlignment.CENTER);
            String label = switch (pp.getType()) {
                case SPEED -> ">>";
                case DAMAGE_BOOST -> "+D";
                case FIRE_RATE -> ">>|";
                case SHIELD -> "[]";
                case HEALTH_PACK -> "+";
                case SLOW -> "<<";
                case WEAKNESS -> "-D";
            };
            gc.fillText(label, sx, sy + 4);
        }
    }

    private void drawPlayer(GraphicsContext gc, Player p, boolean isLocal) {
        double sx = camera.worldToScreenX(p.getPosition().x());
        double sy = camera.worldToScreenY(p.getPosition().y());
        double size = 32;

        Image sprite = SpriteManager.getPlayerSprite(isLocal, p.isAlive());
        if (sprite != null) {
            gc.drawImage(sprite, sx - size/2, sy - size/2, size, size);
        } else {
            double r = size / 2;
            if (!p.isAlive()) {
                gc.setFill(Color.rgb(48, 54, 61, 0.4));
                gc.fillOval(sx - r, sy - r, size, size);
                return;
            }
            Color fill = isLocal ? Color.rgb(88, 166, 255) : Color.rgb(248, 81, 73);
            gc.setFill(fill);
            gc.fillOval(sx - r, sy - r, size, size);
            gc.setStroke(fill.deriveColor(0, 1, 1, 0.5));
            gc.setLineWidth(2);
            gc.strokeOval(sx - r - 2, sy - r - 2, size + 4, size + 4);
        }

        double dirLen = 20;
        double dx = p.getDirection().x() * dirLen;
        double dy = p.getDirection().y() * dirLen;
        gc.setStroke(Color.rgb(240, 246, 252, 0.6));
        gc.setLineWidth(2);
        gc.strokeLine(sx, sy, sx + dx, sy + dy);

        gc.setFill(Color.rgb(240, 246, 252));
        gc.setFont(Font.font("Monospace", 12));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText(p.getUsername(), sx, sy - size - 4);

        if (p.isAlive()) {
            drawHealthBar(gc, sx, sy + size/2 + 4, 32, 4, p.getHealth());
        }
    }

    private void drawHealthBar(GraphicsContext gc, double cx, double y, double width, double height, double health) {
        double ratio = health / 100.0;
        gc.setFill(Color.rgb(48, 54, 61));
        gc.fillRoundRect(cx - width/2, y, width, height, 2, 2);
        Color barColor;
        if (ratio > 0.6) barColor = Color.rgb(46, 160, 67);
        else if (ratio > 0.3) barColor = Color.rgb(210, 153, 34);
        else barColor = Color.rgb(248, 81, 73);
        gc.setFill(barColor);
        gc.fillRoundRect(cx - width/2, y, width * ratio, height, 2, 2);
    }

    private void drawBullet(GraphicsContext gc, Bullet b) {
        double sx = camera.worldToScreenX(b.getPosition().x());
        double sy = camera.worldToScreenY(b.getPosition().y());

        Image sprite = SpriteManager.getBulletSprite();
        if (sprite != null) {
            gc.drawImage(sprite, sx - 4, sy - 4, 8, 8);
        } else {
            gc.setFill(Color.rgb(255, 166, 0, 0.3));
            gc.fillOval(sx - 5, sy - 5, 10, 10);
            gc.setFill(Color.rgb(255, 200, 50));
            gc.fillOval(sx - 2.5, sy - 2.5, 5, 5);
        }
    }

    private void drawCrosshair(GraphicsContext gc, double mx, double my) {
        Image crosshair = SpriteManager.getCrosshair();
        if (crosshair != null) {
            gc.drawImage(crosshair, mx - 16, my - 16, 32, 32);
        } else {
            double len = 10;
            double gap = 4;
            gc.setStroke(Color.rgb(240, 246, 252, 0.8));
            gc.setLineWidth(2);
            gc.strokeLine(mx - len - gap, my, mx - gap, my);
            gc.strokeLine(mx + gap, my, mx + len + gap, my);
            gc.strokeLine(mx, my - len - gap, mx, my - gap);
            gc.strokeLine(mx, my + gap, mx, my + len + gap);
            gc.setFill(Color.rgb(240, 246, 252, 0.4));
            gc.fillOval(mx - 1.5, my - 1.5, 3, 3);
        }
    }

    private void drawPlayerHitbox(GraphicsContext gc, Player p) {
        double sx = camera.worldToScreenX(p.getPosition().x());
        double sy = camera.worldToScreenY(p.getPosition().y());
        double r = 15;
        gc.setStroke(Color.rgb(88, 166, 255, 0.3));
        gc.setLineWidth(1);
        gc.setLineDashes(4);
        gc.strokeOval(sx - r, sy - r, r * 2, r * 2);
        gc.setLineDashes(null);
    }

    private void drawBulletHitbox(GraphicsContext gc, Bullet b) {
        double sx = camera.worldToScreenX(b.getPosition().x());
        double sy = camera.worldToScreenY(b.getPosition().y());
        double r = 3;
        gc.setStroke(Color.rgb(248, 81, 73, 0.4));
        gc.setLineWidth(1);
        gc.strokeOval(sx - r, sy - r, r * 2, r * 2);
    }
}
