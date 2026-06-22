#!/bin/bash
# Lanzador del servidor Shooter 2D
# Requiere: Java 25+
#
# Uso:
#   ./run-server.sh                                    # localhost:8080, SQLite
#   DB_URL=jdbc:postgresql://... ./run-server.sh       # con PostgreSQL

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SERVER_JAR="$SCRIPT_DIR/server/target/server.jar"

if [ ! -f "$SERVER_JAR" ]; then
    echo "[!] No se encuentra $SERVER_JAR"
    echo "[!] Ejecuta primero: mvn package -pl server -am -DskipTests"
    exit 1
fi

exec java -jar "$SERVER_JAR" "$@"
