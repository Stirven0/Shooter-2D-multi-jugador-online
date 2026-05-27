package com.aa.client.render;

import com.aa.client.render.Camera;
import com.aa.shared.model.TileLayer;
import com.aa.shared.model.TileMap;
import com.aa.shared.model.TileSet;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TileRenderer {

    private final Camera camera;
    private final Map<String, Image> spriteSheetCache = new HashMap<>();

    public TileRenderer(Camera camera) {
        this.camera = camera;
    }

    public void render(GraphicsContext gc, TileMap tileMap, double canvasWidth, double canvasHeight) {
        if (tileMap == null) return;

        int tileW = tileMap.getTileWidth();
        int tileH = tileMap.getTileHeight();

        int startCol = (int) (camera.getX() / tileW);
        int startRow = (int) (camera.getY() / tileH);
        int endCol = startCol + (int) (canvasWidth / tileW) + 2;
        int endRow = startRow + (int) (canvasHeight / tileH) + 2;

        for (TileLayer layer : tileMap.getLayers()) {
            if (!layer.isVisible()) continue;
            if (!"tilelayer".equals(layer.getType())) continue;

            int[][] data = layer.getData();
            if (data == null) continue;

            int rows = Math.min(data.length, tileMap.getHeight());
            int cols = data.length > 0 ? Math.min(data[0].length, tileMap.getWidth()) : 0;

            for (int row = Math.max(0, startRow); row <= Math.min(endRow, rows - 1); row++) {
                for (int col = Math.max(0, startCol); col <= Math.min(endCol, cols - 1); col++) {
                    int tileId = data[row][col];
                    if (tileId == 0) continue;

                    double sx = camera.worldToScreenX(col * tileW);
                    double sy = camera.worldToScreenY(row * tileH);

                    TileSet ts = findTileset(tileId, tileMap.getTilesets());
                    if (ts != null && ts.getImage() != null) {
                        Image sheet = getSpriteSheet(ts.getImage());
                        if (sheet != null) {
                            int localId = tileId - ts.getFirstGid();
                            int srcCol = localId % ts.getColumns();
                            int srcRow = localId / ts.getColumns();
                            double srcX = srcCol * tileW;
                            double srcY = srcRow * tileH;
                            gc.drawImage(sheet, srcX, srcY, tileW, tileH, sx, sy, tileW, tileH);
                            continue;
                        }
                    }

                    gc.setFill(TileColors.getColor(tileId));
                    gc.fillRect(sx, sy, tileW + 1, tileH + 1);

                    if (isSolidTile(tileId, tileMap.getTilesets())) {
                        gc.setStroke(javafx.scene.paint.Color.rgb(60, 68, 76));
                        gc.setLineWidth(0.5);
                        gc.strokeRect(sx, sy, tileW, tileH);
                    }
                }
            }
        }
    }

    private TileSet findTileset(int tileId, List<TileSet> tilesets) {
        for (TileSet ts : tilesets) {
            int localId = tileId - ts.getFirstGid();
            if (localId >= 0 && localId < ts.getTileCount()) return ts;
        }
        return null;
    }

    private Image getSpriteSheet(String imagePath) {
        if (spriteSheetCache.containsKey(imagePath)) {
            Image cached = spriteSheetCache.get(imagePath);
            if (cached != null && !cached.isError()) return cached;
        }
        try {
            Image img = new Image(getClass().getClassLoader().getResourceAsStream(imagePath));
            if (img.isError()) {
                spriteSheetCache.put(imagePath, null);
                return null;
            }
            spriteSheetCache.put(imagePath, img);
            return img;
        } catch (Exception e) {
            spriteSheetCache.put(imagePath, null);
            return null;
        }
    }

    private boolean isSolidTile(int tileId, List<TileSet> tilesets) {
        for (TileSet ts : tilesets) {
            if (ts.isSolid(tileId)) return true;
        }
        return false;
    }
}
