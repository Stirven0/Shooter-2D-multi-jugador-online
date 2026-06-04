#!/usr/bin/env python3
"""Fast-start dual AI fight with persistent connections and immediate movement."""
import socket, time, json, math, threading, sys

P1_PORT = 4568
P2_PORT = 4567

def make_sock(port):
    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    s.settimeout(5)
    s.connect(("127.0.0.1", port))
    return s

def rpc_sock(sock, method, args=None):
    payload = json.dumps({
        "jsonrpc":"2.0","id":1,"method":"tools/call",
        "params":{"name":method,"arguments":args or {}}
    }) + "\n"
    try:
        sock.sendall(payload.encode())
        data = b""
        while True:
            chunk = sock.recv(4096)
            if not chunk:
                break
            data += chunk
            if chunk.endswith(b"\n"):
                break
        if data:
            result = json.loads(data.decode())
            texts = [c["text"] for c in result.get("result",{}).get("content",[]) if "text" in c]
            return texts[0] if texts else None
    except Exception as e:
        return None

def get_screen(sock):
    t = rpc_sock(sock, "get_screen_info")
    if t:
        try:
            return json.loads(t).get("screen")
        except:
            pass
    return None

def wait_for_screen(sock, target, timeout=30):
    start = time.time()
    while time.time() - start < timeout:
        s = get_screen(sock)
        if s == target:
            return True
        time.sleep(0.2)
    return False

print("Connecting...")
p1 = make_sock(P1_PORT)
p2 = make_sock(P2_PORT)
print("Connected!")

# === SETUP ===
print("Logging in...")
rpc_sock(p1, "ui_login", {"username":"player1","password":"pass1"})
rpc_sock(p2, "ui_login", {"username":"player2","password":"pass2"})
time.sleep(2)

# Return to lobby if needed
for s in [p1, p2]:
    scr = get_screen(s)
    if scr != "lobby":
        rpc_sock(s, "ui_back_to_lobby")
time.sleep(1)

print("Creating room...")
rpc_sock(p1, "ui_create_room", {"map_id":"map_01"})
time.sleep(2)

t = rpc_sock(p1, "get_screen_info")
room_id = None
if t:
    d = json.loads(t)
    room_id = d.get("room_id") or d.get("lobby_room_id")
print(f"Room: {room_id}")

print("Joining...")
rpc_sock(p2, "ui_join_room", {"room_id": room_id})
time.sleep(2)

print("Starting game...")
rpc_sock(p1, "ui_start_game")
time.sleep(3)

if not wait_for_screen(p2, "game") or not wait_for_screen(p1, "game"):
    print("FAILED to get both in game")
    # Debug
    print("P2 screen:", get_screen(p2))
    print("P1 screen:", get_screen(p1))
    sys.exit(1)

print("BOTH IN GAME! Starting fight...")

# === HELPERS ===
def get_pos(sock):
    t = rpc_sock(sock, "get_player_position")
    if t:
        try:
            d = json.loads(t)
            return (d["x"], d["y"])
        except:
            pass
    return None

def get_other_players(sock):
    t = rpc_sock(sock, "get_other_players")
    if t:
        try:
            d = json.loads(t)
            return d.get("players", [])
        except:
            pass
    return []

def send_key(sock, key, action="click"):
    rpc_sock(sock, "send_key", {"key": key, "action": action})

def aim_at(sock, x, y):
    rpc_sock(sock, "aim_at", {"x": x, "y": y})

# Global direction state
direction_p1 = ""
direction_p2 = ""
release_needed_p1 = False
release_needed_p2 = False

def move_towards(sock, my_x, my_y, target_x, target_y, player_num):
    global direction_p1, direction_p2, release_needed_p1, release_needed_p2
    dx = target_x - my_x
    dy = target_y - my_y
    
    if abs(dx) > abs(dy):
        new_dir = "D" if dx > 0 else "A"
    else:
        new_dir = "S" if dy > 0 else "W"
    
    if player_num == 1:
        if new_dir != direction_p1:
            if direction_p1 and release_needed_p1:
                send_key(sock, direction_p1, "release")
            direction_p1 = new_dir
            send_key(sock, direction_p1, "press")
            release_needed_p1 = True
    else:
        if new_dir != direction_p2:
            if direction_p2 and release_needed_p2:
                send_key(sock, direction_p2, "release")
            direction_p2 = new_dir
            send_key(sock, direction_p2, "press")
            release_needed_p2 = True

# === FIGHT LOOP ===
last_pos_p1 = None
last_pos_p2 = None
stuck_count_p1 = 0
stuck_count_p2 = 0

for i in range(3000):
    # Get P2's view
    p2_pos = get_pos(p2)
    p2_enemies = get_other_players(p2)
    p2_target = p2_enemies[0] if p2_enemies else None
    
    # Get P1's view  
    p1_pos = get_pos(p1)
    p1_enemies = get_other_players(p1)
    p1_target = p1_enemies[0] if p1_enemies else None
    
    if i % 20 == 0:
        p1_hp = p1_target["health"] if p1_target else "?"
        p2_hp = p2_target["health"] if p2_target else "?"
        print(f"[{i}] P2=({p2_pos[0]:.0f},{p2_pos[1]:.0f} P1={p1_target['x']:.0f},{p1_target['y']:.0f}) HP:P1={p1_hp} P2={p2_hp}" if p2_pos and p2_target and p1_pos and p1_target else f"[{i}] data incomplete")
    
    if p2_pos and p2_target:
        # Move towards target
        move_towards(p2, p2_pos[0], p2_pos[1], p2_target["x"], p2_target["y"], 2)
        # Stuck detection: alternate direction if not moving
        if last_pos_p2 and p2_pos == last_pos_p2:
            stuck_count_p2 += 1
        else:
            stuck_count_p2 = 0
        if stuck_count_p2 > 5:
            send_key(p2, direction_p2, "release")
            direction_p2 = ""
            stuck_count_p2 = 0
        
        # Aim and shoot
        aim_at(p2, p2_target["x"], p2_target["y"])
        send_key(p2, "CLICK")
    
    if p1_pos and p1_target:
        move_towards(p1, p1_pos[0], p1_pos[1], p1_target["x"], p1_target["y"], 1)
        if last_pos_p1 and p1_pos == last_pos_p1:
            stuck_count_p1 += 1
        else:
            stuck_count_p1 = 0
        if stuck_count_p1 > 5:
            send_key(p1, direction_p1, "release")
            direction_p1 = ""
            stuck_count_p1 = 0
        
        aim_at(p1, p1_target["x"], p1_target["y"])
        send_key(p1, "CLICK")
    
    last_pos_p2 = p2_pos
    last_pos_p1 = p1_pos
    
    # Check if game ended
    s2 = get_screen(p2)
    if s2 != "game":
        print(f"P2 screen changed to: {s2}")
        break
    
    time.sleep(0.25)

# Release all keys
for s in [p1, p2]:
    for k in ["W","A","S","D"]:
        send_key(s, k, "release")

print("Fight ended!")
for i, s in enumerate([p1, p2], 1):
    print(f"P{i} screen: {get_screen(s)}")
p1.close()
p2.close()
