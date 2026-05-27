package com.aa.client.mcp;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

public class McpTcpTransport implements McpTransport {

    private final String host;
    private final int port;
    private final McpJsonRpcHandler jsonRpcHandler;
    private volatile boolean running;
    private ServerSocket serverSocket;

    public McpTcpTransport(String host, int port, McpJsonRpcHandler jsonRpcHandler) {
        this.host = host;
        this.port = port;
        this.jsonRpcHandler = jsonRpcHandler;
    }

    @Override
    public void start() {
        Thread thread = new Thread(() -> {
            try (ServerSocket srv = new ServerSocket(port, 50, InetAddress.getByName(host))) {
                serverSocket = srv;
                running = true;
                System.err.println("[CLIENT-MCP] TCP transport ready on " + host + ":" + port);
                while (running) {
                    Socket client = srv.accept();
                    new Thread(() -> handleClient(client), "mcp-tcp-handler").start();
                }
            } catch (Exception e) {
                if (running) {
                    System.err.println("[CLIENT-MCP] TCP error: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }, "mcp-tcp-thread");
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    public void stop() {
        running = false;
        if (serverSocket != null) {
            try { serverSocket.close(); } catch (Exception ignored) {}
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private void handleClient(Socket socket) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter writer = new PrintWriter(socket.getOutputStream(), true)) {
            String line;
            while (running && (line = reader.readLine()) != null) {
                String response = jsonRpcHandler.handle(line);
                if (!response.isEmpty()) {
                    writer.println(response);
                }
            }
        } catch (Exception e) {
            if (running) System.err.println("[CLIENT-MCP] TCP client error: " + e.getMessage());
        }
    }
}
