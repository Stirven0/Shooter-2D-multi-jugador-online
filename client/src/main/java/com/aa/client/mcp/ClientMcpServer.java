package com.aa.client.mcp;

import com.aa.client.game.GameClient;
import com.aa.client.input.InputHandler;
import com.aa.client.render.Renderer;
import com.aa.client.ui.ScreenManager;
import com.aa.client.mcp.tools.*;
import com.aa.client.util.ClientConfig;

import javafx.scene.canvas.Canvas;
import javafx.stage.Stage;

public class ClientMcpServer {

    private final McpToolRegistry toolRegistry;
    private final McpGameContext gameContext;
    private final GameTools gameTools;
    private final McpTransport transport;
    private volatile boolean running;

    public ClientMcpServer(GameClient gameClient, InputHandler inputHandler,
                           Renderer renderer, Canvas canvas, Stage stage) {
        this.toolRegistry = new McpToolRegistry();
        this.gameContext = new McpGameContext(gameClient);
        ScreenManager screenManager = gameClient.getScreenManager();

        this.gameTools = new GameTools(gameClient, inputHandler, stage, gameContext);
        if (canvas != null) gameTools.setCanvas(canvas);

        new StatusTools(gameClient, screenManager, gameContext).register(toolRegistry);
        new UiTools(gameClient, screenManager).register(toolRegistry);
        gameTools.register(toolRegistry);
        new GameControlTools(gameClient, inputHandler, gameContext).register(toolRegistry);
        new GameObservabilityTools(gameContext).register(toolRegistry);
        new UiSyncTools(gameClient).register(toolRegistry);

        if (ClientConfig.isMcpTcpEnabled()) {
            McpJsonRpcHandler jsonRpcHandler = new McpJsonRpcHandler(toolRegistry);
            transport = new McpTcpTransport(ClientConfig.getMcpHost(), ClientConfig.getMcpPort(), jsonRpcHandler);
        } else {
            transport = new McpStdioTransport(toolRegistry);
        }
    }

    public void setCanvas(Canvas canvas) {
        gameTools.setCanvas(canvas);
    }

    public void start() {
        if (running) return;
        running = true;
        transport.start();
    }

    public void stop() {
        running = false;
        transport.stop();
    }

    public boolean isRunning() {
        return running;
    }
}
