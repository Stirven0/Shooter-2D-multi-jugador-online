package com.aa.client.mcp;

import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpAsyncServer;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;

public class McpStdioTransport implements McpTransport {

    private final McpToolRegistry toolRegistry;
    private volatile boolean running;
    private McpAsyncServer server;

    public McpStdioTransport(McpToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    @Override
    public void start() {
        Thread thread = new Thread(() -> {
            try {
                McpJsonMapper jsonMapper = McpJsonMapper.getDefault();
                server = McpServer.async(new StdioServerTransportProvider(jsonMapper))
                    .serverInfo("multiplayer-client", "2.0.0")
                    .capabilities(McpSchema.ServerCapabilities.builder()
                        .tools(true)
                        .build())
                    .build();

                for (var spec : toolRegistry.buildAsyncSpecs()) {
                    server.addTool(spec).subscribe();
                }

                running = true;
                System.err.println("[CLIENT-MCP] STDIO transport ready");
                while (running) {
                    try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
                }
            } catch (Exception e) {
                System.err.println("[CLIENT-MCP] STDIO error: " + e.getMessage());
                e.printStackTrace();
            }
        }, "client-mcp-thread");
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    public void stop() {
        running = false;
        if (server != null) {
            try { server.close(); } catch (Exception ignored) {}
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
