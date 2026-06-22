# 1. Instalar dependencia
pip install websocket-client

# 2. Asegurar que el servidor corre
cd multiplayer-game
mvn clean package -DskipTests
java -jar server/target/server.jar

# 3. En otra terminal, ejecutar test client
python tools/test_client.py

# Ejemplo de sesión interactiva:
> connect
> login player1 pass1
> create
> start
> move 0.5 0.0      # Mover derecha
> move 0.0 0.5      # Mover abajo
> shoot 0           # Disparar derecha
> shoot 1.57        # Disparar abajo (90°)
> auto              # Bot automático

# 4. En otra terminal, ejecuta load test

python tools/load_test.py 5    # 5 jugadores
python tools/load_test.py 20   # 20 jugadores (stress test)


# Solo Shell

import websocket, json

ws = websocket.create_connection("ws://localhost:8080")
print("Conectado")   # → dispara onOpen en el servidor

ws.send(json.dumps({"type": "PING"}))
print("Mensaje enviado")  # → dispara onMessage en el servidor

ws.close()
print("Conexión cerrada") # → dispara onClose en el servidor

# 5. Empaquetar instaladores nativos

# Linux → .deb
python3 tools/package-native.py

# Windows → .exe (ejecutar en Windows con WiX Toolset)
python3 tools/package-native.py --type exe
