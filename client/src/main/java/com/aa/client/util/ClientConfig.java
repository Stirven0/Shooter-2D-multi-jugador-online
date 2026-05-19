package com.aa.client.util;

/**
 * Constantes de configuración del cliente.
 * Contiene URL del servidor, dimensiones de ventana y parámetros de cámara.
 */
public final class ClientConfig {

    private ClientConfig() {}

    /** URL del servidor WebSocket al que se conecta el cliente. */
    private static String serverHost = "localhost";
    private static int serverPort = 8080;

    public static String getServerUrl() { return "ws://" + serverHost + ":" + serverPort; }
    public static String getServerHost() { return serverHost; }
    public static int getServerPort() { return serverPort; }
    public static void setServerHost(String host) { serverHost = host; }
    public static void setServerPort(String port) { serverPort = Integer.parseInt(port); }
    public static void setServerUrl(String host, int port) {
        serverHost = host;
        serverPort = port;
    }
    /** Título de la ventana de la aplicación. */
    public static final String TITLE = "Shooter Client";
    /** Ancho por defecto de la ventana en píxeles. */
    public static final int WIDTH = 800;
    /** Alto por defecto de la ventana en píxeles. */
    public static final int HEIGHT = 450;

    /** Factor de suavizado para la interpolación de la cámara (0..1). */
    public static final double CAMERA_SMOOTH = 0.15;
}
