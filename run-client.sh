#!/bin/bash
# Lanzador del cliente Shooter 2D
# Requiere: Java 25+, JavaFX SDK 25
#
# Descargar JavaFX SDK desde: https://gluonhq.com/products/javafx/
# Descomprimir y apuntar JAVAFX_HOME al directorio lib/
#
# Uso:
#   ./run-client.sh                          # default
#   ./run-client.sh --mcp                    # con MCP TCP
#   ./run-client.sh --host 10.0.0.5 --port 8080 --ssl

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
CLIENT_JAR="$SCRIPT_DIR/client/target/client.jar"

if [ ! -f "$CLIENT_JAR" ]; then
    echo "[!] No se encuentra $CLIENT_JAR"
    echo "[!] Ejecuta primero: mvn package -pl client -am -DskipTests"
    exit 1
fi

JAVAFX_HOME="${JAVAFX_HOME:-/usr/share/java/javafx}"
JAVAFX_LIB="${JAVAFX_HOME}"

if [ ! -d "$JAVAFX_LIB" ]; then
    echo "[!] JAVAFX_HOME no configurado o no encontrado en $JAVAFX_LIB"
    echo "[!] Descargar de: https://gluonhq.com/products/javafx/"
    echo "    export JAVAFX_HOME=/ruta/a/javafx-sdk-25/lib"
    exit 1
fi

exec java \
    --module-path "$JAVAFX_LIB" \
    --add-modules javafx.controls,javafx.fxml,javafx.media,javafx.swing \
    -jar "$CLIENT_JAR" "$@"
