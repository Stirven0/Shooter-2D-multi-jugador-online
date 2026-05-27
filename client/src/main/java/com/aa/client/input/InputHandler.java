package com.aa.client.input;

import com.aa.client.render.Camera;
import com.aa.shared.message.MoveMessage;
import com.aa.shared.model.Vector2;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class InputHandler {
    private final Set<KeyCode> keys = ConcurrentHashMap.newKeySet();
    private double mouseScreenX;
    private double mouseScreenY;
    private volatile boolean mousePressed;
    private volatile boolean swapWeaponPressed;
    private volatile boolean useSkill0Pressed;
    private volatile boolean useSkill1Pressed;

    public void attach(Scene scene) {
        scene.setOnKeyPressed(e -> {
            keys.add(e.getCode());
            if (e.getCode() == KeyCode.Q) swapWeaponPressed = true;
            if (e.getCode() == KeyCode.E) useSkill0Pressed = true;
            if (e.getCode() == KeyCode.F) useSkill1Pressed = true;
        });
        scene.setOnKeyReleased(e -> {
            keys.remove(e.getCode());
            if (e.getCode() == KeyCode.E) useSkill0Pressed = false;
            if (e.getCode() == KeyCode.F) useSkill1Pressed = false;
        });
        scene.setOnMouseMoved(e -> {
            mouseScreenX = e.getX();
            mouseScreenY = e.getY();
        });
        scene.setOnMousePressed(e -> {
            if (e.getButton() == MouseButton.PRIMARY) mousePressed = true;
        });
        scene.setOnMouseReleased(e -> {
            if (e.getButton() == MouseButton.PRIMARY) mousePressed = false;
        });
    }

    public boolean isMoving() {
        return keys.contains(KeyCode.W) || keys.contains(KeyCode.A)
            || keys.contains(KeyCode.S) || keys.contains(KeyCode.D)
            || keys.contains(KeyCode.UP) || keys.contains(KeyCode.LEFT)
            || keys.contains(KeyCode.DOWN) || keys.contains(KeyCode.RIGHT);
    }

    public MoveMessage getMoveMessage() {
        double dx = 0, dy = 0;
        if (keys.contains(KeyCode.W) || keys.contains(KeyCode.UP)) dy -= 1;
        if (keys.contains(KeyCode.S) || keys.contains(KeyCode.DOWN)) dy += 1;
        if (keys.contains(KeyCode.A) || keys.contains(KeyCode.LEFT)) dx -= 1;
        if (keys.contains(KeyCode.D) || keys.contains(KeyCode.RIGHT)) dx += 1;

        double mag = Math.hypot(dx, dy);
        if (mag > 0) {
            dx /= mag;
            dy /= mag;
        }

        boolean sprint = keys.contains(KeyCode.SHIFT);
        return new MoveMessage(dx, dy, sprint);
    }

    public boolean isShooting() {
        return mousePressed;
    }

    public void clearShoot() {
        mousePressed = false;
    }

    public boolean consumeSwapWeapon() {
        if (swapWeaponPressed) {
            swapWeaponPressed = false;
            return true;
        }
        return false;
    }

    public void triggerSwapWeapon() { this.swapWeaponPressed = true; }
    public void triggerSkillSlot0() { this.useSkill0Pressed = true; }
    public void triggerSkillSlot1() { this.useSkill1Pressed = true; }
    public void triggerShoot() { this.mousePressed = true; }

    public void addKey(KeyCode code) { keys.add(code); }
    public void removeKey(KeyCode code) { keys.remove(code); }

    public void setMousePosition(double x, double y) {
        this.mouseScreenX = x;
        this.mouseScreenY = y;
    }

    public boolean consumeSkillSlot0() {
        if (useSkill0Pressed) {
            useSkill0Pressed = false;
            return true;
        }
        return false;
    }

    public boolean consumeSkillSlot1() {
        if (useSkill1Pressed) {
            useSkill1Pressed = false;
            return true;
        }
        return false;
    }

    public double getMouseScreenX() { return mouseScreenX; }
    public double getMouseScreenY() { return mouseScreenY; }

    public double getShootAngle(Camera camera, Vector2 playerWorldPos) {
        double screenPlayerX = camera.worldToScreenX(playerWorldPos.x());
        double screenPlayerY = camera.worldToScreenY(playerWorldPos.y());
        return Math.atan2(mouseScreenY - screenPlayerY, mouseScreenX - screenPlayerX);
    }
}
