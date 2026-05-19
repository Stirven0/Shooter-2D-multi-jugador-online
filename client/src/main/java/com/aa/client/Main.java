package com.aa.client;

import com.aa.client.ui.AutoLoginConfig;
import com.aa.client.ui.ScreenManager;
import com.aa.client.util.ClientConfig;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;

public class Main extends Application {

    private static boolean enableMcp = false;
    private static AutoLoginConfig autoLogin;
    private ScreenManager screens;

    @Override
    public void start(Stage stage) {
        screens = new ScreenManager(autoLogin);
        screens.init(stage);

        stage.setOnCloseRequest(e -> {
            cleanup();
            Platform.exit();
        });

        if (enableMcp) {
            screens.enableMcpMode();
        }
    }

    @Override
    public void stop() {
        cleanup();
    }

    private void cleanup() {
        if (screens != null) {
            screens.cleanup();
            screens = null;
        }
    }

    public static void main(String[] args) {
        String username = null;
        String password = null;
        boolean autoRegister = false;
        boolean autoCreate = false;
        boolean autoJoin = false;
        int x = -1, y = -1;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--mcp" -> enableMcp = true;
                case "--username" -> username = args[++i];
                case "--password" -> password = args[++i];
                case "--auto-register" -> autoRegister = true;
                case "--auto-create" -> autoCreate = true;
                case "--auto-join" -> autoJoin = true;
                case "--x" -> x = Integer.parseInt(args[++i]);
                case "--y" -> y = Integer.parseInt(args[++i]);
                case "--host" -> {
                    String h = args[++i];
                    ClientConfig.setServerHost(h);
                }
                case "--port" -> {
                    String p = args[++i];
                    ClientConfig.setServerPort(p);
                }
            }
        }

        if (username != null && password != null) {
            autoLogin = new AutoLoginConfig(username, password, autoRegister, autoCreate, autoJoin, x, y);
        }

        launch(args);
    }
}
