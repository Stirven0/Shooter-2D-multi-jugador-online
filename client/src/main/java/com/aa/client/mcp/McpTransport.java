package com.aa.client.mcp;

public interface McpTransport {
    void start();
    void stop();
    boolean isRunning();
}
