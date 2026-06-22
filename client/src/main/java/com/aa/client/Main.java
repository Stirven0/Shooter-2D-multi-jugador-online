package com.aa.client;

import com.aa.client.ui.ScreenManager;
import com.aa.client.util.ClientConfig;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;

public class Main extends Application {

    private static boolean enableMcp = false;
    private ScreenManager screens;

    @Override
    public void start(Stage stage) {
        screens = new ScreenManager();
        screens.init(stage);

        stage.setOnCloseRequest(e -> {
            cleanup();
            Platform.exit();
            System.exit(0);
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
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--mcp" -> {
                    enableMcp = true;
                    ClientConfig.setMcpTcpEnabled(true);
                }
                case "--host" -> {
                    String h = args[++i];
                    ClientConfig.setServerHost(h);
                }
                case "--port" -> {
                    String p = args[++i];
                    ClientConfig.setServerPort(p);
                }
                case "--ssl" -> ClientConfig.setServerUseSsl(true);
                case "--hostmcp" -> ClientConfig.setMcpHost(args[++i]);
                case "--portmcp" -> {
                    ClientConfig.setMcpPort(Integer.parseInt(args[++i]));
                    ClientConfig.setMcpTcpEnabled(true);
                    enableMcp = true;
                }
            }
        }

        launch(args);
    }
}
