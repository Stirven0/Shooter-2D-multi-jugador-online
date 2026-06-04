#!/usr/bin/env python3
"""Direct WebSocket test - bypass MCP, test server movement directly"""
import websocket, json, time, threading, sys

SERVER = "ws://localhost:8080"

class Bot:
    def __init__(self, name):
        self.name = name
        self.ws = None
        self.player_id = None
        self.running = False
        self.last_pos = None
        self.last_state = None

    def connect(self):
        self.ws = websocket.create_connection(SERVER, timeout=10)
        self.running = True
        threading.Thread(target=self._recv, daemon=True).start()
        return True

    def _recv(self):
        self.ws.settimeout(1)
        while self.running:
            try:
                msg = self.ws.recv()
                if msg:
                    data = json.loads(msg)
                    if data.get("type") == "LOGIN_RESPONSE":
                        self.player_id = data.get("userId")
                        print(f"[{self.name}] Logged in: {self.player_id[:8]}")
                    elif data.get("type") == "GAME_STATE":
                        self.last_state = data.get("gameState", {})
                        players = self.last_state.get("players", [])
                        for p in players:
                            if p.get("id") == self.player_id:
                                pos = p.get("position", {})
                                self.last_pos = (pos.get("x"), pos.get("y"))
            except:
                pass

    def send(self, msg):
        if self.ws and self.running:
            try:
                self.ws.send(json.dumps(msg))
            except:
                pass

    def login(self, username, password, register=False):
        self.send({"type":"LOGIN_REQUEST","username":username,"password":password,"register":register})

    def move(self, dx, dy):
        self.send({"type":"MOVE_INPUT","dx":dx,"dy":dy,"sprinting":False})

    def close(self):
        self.running = False
        if self.ws: self.ws.close()

p1 = Bot("P1")
p2 = Bot("P2")
p1.connect()
p2.connect()
time.sleep(0.5)

p1.login("bot1", "pass1", True)
p2.login("bot2", "pass2", True)
time.sleep(2)

# Check positions
print(f"P1 pos: {p1.last_pos}")
print(f"P2 pos: {p2.last_pos}")

# Move P2 down for 5 seconds
print("\nMoving P2 DOWN (dy=1.0) for 5 seconds...")
start = time.time()
while time.time() - start < 5:
    p2.move(0, 1.0)
    time.sleep(0.05)

print(f"P2 final pos: {p2.last_pos}")
print(f"P1 final pos: {p1.last_pos}")

p1.close()
p2.close()
print("Done")
