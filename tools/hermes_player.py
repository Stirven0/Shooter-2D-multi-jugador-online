#!/usr/bin/env python3
"""Hermes plays Shooter via client MCP over TCP."""
import socket
import json
import time
import sys

HOST = "localhost"
PORT = 4567

def send_msg(sock, msg):
    sock.sendall((json.dumps(msg) + "\n").encode())

def recv_msg(sock):
    data = b""
    while True:
        chunk = sock.recv(4096)
        if not chunk:
            break
        data += chunk
        if b"\n" in data:
            line, rest = data.split(b"\n", 1)
            return json.loads(line)
    return None

def rpc_call(sock, method, params=None, rid=None):
    if rid is None:
        rid = int(time.time() * 1000) % 100000
    msg = {"jsonrpc": "2.0", "id": rid, "method": method}
    if params:
        msg["params"] = params
    send_msg(sock, msg)
    return recv_msg(sock)

sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
sock.connect((HOST, PORT))
sock.settimeout(10)

# Initialize
r = rpc_call(sock, "initialize", {
    "protocolVersion": "2024-11-05",
    "capabilities": {},
    "clientInfo": {"name": "hermes", "version": "1.0"}
})
print(f"[INIT] {r['result']['serverInfo']}")

# Send initialized notification
send_msg(sock, {"jsonrpc":"2.0","method":"notifications/initialized"})
time.sleep(0.1)

# List tools
r = rpc_call(sock, "tools/list")
tools = {t["name"]: t for t in r["result"]["tools"]}
print(f"[TOOLS] {len(tools)} disponibles: {list(tools.keys())}")

# Step 1: Check screen
r = rpc_call(sock, "tools/call", {"name": "get_screen_info"})
info = json.loads(r["result"]["content"][0]["text"])
print(f"[SCREEN] {info.get('screen')}, connected={info.get('connected')}")

# Step 2: Login / Register
username = "hermes_bot"
password = "hermes123"
r = rpc_call(sock, "tools/call", {"name": "ui_login", "arguments": {
    "username": username, "password": password, "register": True
}})
print(f"[LOGIN] Sent register for {username}")

# Wait for lobby
r = rpc_call(sock, "tools/call", {"name": "wait_for_screen", "arguments": {
    "screen": "lobby", "timeout_ms": 15000
}})
result = json.loads(r["result"]["content"][0]["text"])
print(f"[WAIT] Now on screen: {result.get('screen')}")

# Step 3: Check screen info
r = rpc_call(sock, "tools/call", {"name": "get_screen_info"})
info = json.loads(r["result"]["content"][0]["text"])
print(f"[SCREEN] {info.get('screen')}, username={info.get('username')}, connected={info.get('connected')}")

# Step 4: Create room
r = rpc_call(sock, "tools/call", {"name": "ui_create_room", "arguments": {"map_id": "map_01"}})
print("[CREATE] Room creation requested")

time.sleep(2)

# Check if we have a room
r = rpc_call(sock, "tools/call", {"name": "get_screen_info"})
info = json.loads(r["result"]["content"][0]["text"])
print(f"[SCREEN] room_id={info.get('room_id')}, screen={info.get('screen')}")
print(f"[WAITING] Esperando que otro jugador se una a la sala {info.get('room_id')}...")

# Step 5: Wait for another player to join and start
# Poll until room has 2+ players or game starts
started = False
for attempt in range(60):  # ~2 min max
    r = rpc_call(sock, "tools/call", {"name": "get_screen_info"})
    info = json.loads(r["result"]["content"][0]["text"])
    screen = info.get("screen")
    
    if screen == "game":
        print(f"[GAME] Partida iniciada!")
        started = True
        break
    
    rooms = info.get("available_rooms", [])
    for room in rooms:
        if room.get("id") == info.get("room_id"):
            pcount = room.get("players", "0/0")
            print(f"[WAIT] Jugadores en sala: {pcount}")
    
    # If we're the host and someone joined, start the game
    if info.get("room_id") and not started:
        for room in rooms:
            if room.get("id") == info.get("room_id"):
                pcount_str = room.get("players", "0/0")
                pcount = int(pcount_str.split("/")[0])
                if pcount >= 2:
                    print(f"[START] {pcount} jugadores, iniciando partida...")
                    r = rpc_call(sock, "tools/call", {"name": "ui_start_game"})
                    print(f"[START] {r['result']['content'][0]['text']}")
                    break
    
    time.sleep(2)

if not started:
    print("[TIMEOUT] Nadie se unió. Saliendo.")
    sock.close()
    sys.exit(1)

# Step 6: Play!
print("\n=== JUGANDO ===")
time.sleep(1)

# Get initial state
r = rpc_call(sock, "tools/call", {"name": "get_hud_info"})
hud = json.loads(r["result"]["content"][0]["text"])
print(f"[HUD] health={hud.get('health')}, weapon={hud.get('current_weapon')}")

r = rpc_call(sock, "tools/call", {"name": "get_player_position"})
pos = json.loads(r["result"]["content"][0]["text"])
print(f"[POS] x={pos.get('x'):.0f}, y={pos.get('y'):.0f}")

# Movement + shooting loop
import random
actions = ["W", "A", "S", "D"]
print("\nMovimiento aleatorio + disparos. 30 segundos...")
deadline = time.time() + 30

while time.time() < deadline:
    # Move in random direction
    key = random.choice(actions)
    rpc_call(sock, "tools/call", {"name": "send_key", "arguments": {"key": key, "action": "press"}})
    time.sleep(0.3)
    rpc_call(sock, "tools/call", {"name": "send_key", "arguments": {"key": key, "action": "release"}})
    
    # Check for other players and aim at them
    try:
        r = rpc_call(sock, "tools/call", {"name": "get_other_players"})
        other = json.loads(r["result"]["content"][0]["text"])
        if isinstance(other, list) and other:
            # Pick closest alive player
            r_pos = rpc_call(sock, "tools/call", {"name": "get_player_position"})
            my_pos = json.loads(r_pos["result"]["content"][0]["text"])
            
            closest = None
            closest_dist = float('inf')
            for p in other:
                if p.get("alive"):
                    dist = ((p["position"]["x"] - my_pos["x"])**2 + (p["position"]["y"] - my_pos["y"])**2)**0.5
                    if dist < closest_dist:
                        closest_dist = dist
                        closest = p
            
            if closest:
                # Aim at enemy and shoot
                rpc_call(sock, "tools/call", {"name": "aim_at", "arguments": {
                    "x": closest["position"]["x"], 
                    "y": closest["position"]["y"]
                }})
                rpc_call(sock, "tools/call", {"name": "send_key", "arguments": {"key": "CLICK"}})
                
                # Sometimes swap weapon or use skill
                if random.random() < 0.15:
                    rpc_call(sock, "tools/call", {"name": "send_key", "arguments": {"key": "Q"}})
                if random.random() < 0.08:
                    rpc_call(sock, "tools/call", {"name": "send_key", "arguments": {"key": random.choice(["E", "F"])}})
    except:
        pass  # Not in game anymore
    
    time.sleep(0.5)

# Final state
r = rpc_call(sock, "tools/call", {"name": "get_hud_info"})
try:
    hud = json.loads(r["result"]["content"][0]["text"])
    print(f"\n[FINAL] health={hud.get('health')}, kills={hud.get('kills')}, deaths={hud.get('deaths')}")
except:
    pass

sock.close()
print("\n¡Listo! Hermes terminó de jugar.")
