package com.aa.client.ui;

import javafx.scene.Scene;
import javafx.stage.Stage;

public interface IScreen {
    Scene createScene(Stage stage);
    default String getErrorMessage() { return null; }
    default void setError(String msg) {}
}
