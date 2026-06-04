#!/usr/bin/env python3
"""Definitive dual AI fight controlling both players via raw JSON-RPC."""
import subprocess, time, json, sys, math, random

P1_PORT = 4568
P2_PORT = 4567

def rpc(port, method, args=None, timeout=5):
    payload = {"jsonrpc":"2.0","id":1,"method":"tools/call",
               "params":{"name":method,"arguments":args or {}}}
    try:
        data = subprocess.run(
            ["timeout", str(timeout), "nc", "localhost", str(port)],
            input=json.dumps(payload).encode(), capture_output=True, timeout=timeout+1
        ).stdout
        if data:
            result = json.loads(data)
            texts = [c["text"] for c in result.get("result",{}).get("content",[]) if "text" in c]
            return texts[0] if texts else None
    except Exception as e:
        return None

def parse_json_field(text, key):
    try:
        d = json.loads(text)
        return d.get(key)
    except:
        return None

def get_screen(port):
    t = rpc(port, "get_screen_info")
    return parse_json_field(t, "screen") if t else None

def wait_for_game(port, timeout=30):
    start = time.time()
    while time.time() - start < timeout:
        s = get_screen(port)
        if s == "game":
            return True
        time.sleep(0.5)
    return False

def get_pos(port):
    t = rpc(port, "get_player_position")
    if t:
        return (parse_json_field(t, "x"), parse_json_field(t, "y"))
    return None

def get_enemy(port):
    t = rpc(port, "get_other_players")
    if t:
        d = json.loads(t)
        players = d.get("players", [])
        if players:
            p = players[0]
            return (p.get("x"), p.get("y"), p.get("health"))
    return None

def get_hud(port):
    t = rpc(port, "get_hud_info")
    if t:
        d = json.loads(t)
        # Find player's own health
        return {"health": d.get("health")}
    return {"health": 100}

def move(port, key, action="press"):
    rpc(port, "send_key", {"key": key, "action": action})

def aim(port, x, y):
    rpc(port, "aim_at", {"x": x, "y": y})

def shoot(port):
    rpc(port, "send_key", {"key": "CLICK"})

def use_skill(port, slot):
    rpc(port, "send_key", {"key": "E" if slot == 0 else "F"})

def rpc_ignore(port, method, args=None):
    """Fire and forget - no wait for response."""
    payload = {"jsonrpc":"2.0","id":1,"method":"tools/call",
               "params":{"name":method,"arguments":args or {}}}
    try:
        subprocess.Popen(
            ["timeout", "1", "nc", "localhost", str(port)],
            stdin=subprocess.PIPE, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL
        ).communicate(json.dumps(payload).encode())
    except:
        pass

def move_fast(port, key, action="press"):
    """Fire-and-forget movement to avoid blocking."""
    rpc_ignore(port, "send_key", {"key": key, "action": action})

def aim_and_shoot(port, ex, ey, px, py):
    """Aim at enemy and shoot."""
    aim(port, ex, ey)
    time.sleep(0.05)
    shoot(port)

# ==== MAIN ====
print("=== DUAL AI FIGHT ===")

# 1. Create room and start game
rpc(P1_PORT, "ui_create_room", {"map_id": "map_01"})
time.sleep(2)

# Get room id
t = rpc(P1_PORT, "get_screen_info")
room_id = parse_json_field(t, "room_id")
if not room_id:
    room_id = parse_json_field(t, "lobby_room_id")
print(f"Room: {room_id}")

rpc(P2_PORT, "ui_join_room", {"room_id": room_id})
time.sleep(2)

rpc(P1_PORT, "ui_start_game")
time.sleep(3)

# Wait for game screen
if not wait_for_game(P2_PORT) or not wait_for_game(P1_PORT):
    print("FAILED to get both in game")
    sys.exit(1)

print("Both in game! Fighting...")

# Get initial positions
p2_pos = get_pos(P2_PORT)
p1_pos = get_pos(P1_PORT)
print(f"P2 spawn: {p2_pos}")
print(f"P1 spawn: {p1_pos}")

# Start both constantly moving towards each other
# Player2 press W to advance, Player1 press S to retreat
# We'll dynamically adjust in the loop

last_move = {}
for p in [P1_PORT, P2_PORT]:
    last_move[p] = ""

ITERATIONS = 2000
for i in range(ITERATIONS):
    p2_pos = get_pos(P2_PORT)
    p1_enemy = get_enemy(P2_PORT)  # P2 sees P1 as enemy
    
    p1_pos = get_pos(P1_PORT)
    p2_enemy = get_enemy(P1_PORT)  # P1 sees P2 as enemy
    
    if not all([p2_pos, p1_enemy, p1_pos, p2_enemy]):
        print(f"iter {i}: missing data, checking screens")
        s2 = get_screen(P2_PORT)
        s1 = get_screen(P1_PORT)
        print(f"  P2 screen={s2}, P1 screen={s1}")
        if s2 == "gameover" or s1 == "gameover":
            print("Game ended!")
            break
        time.sleep(0.3)
        continue
    
    p2x, p2y = p2_pos
    p1_enemy_x, p1_enemy_y, p1_enemy_hp = p1_enemy
    
    p1x, p1y = p1_pos
    p2_enemy_x, p2_enemy_y, p2_enemy_hp = p2_enemy
    
    if i % 30 == 0:
        print(f"[{i}] P2=({p2x:.0f},{p2y:.0f}) P1=({p1x:.0f},{p1y:.0f}) "
              f"P1hp={p1_enemy_hp} P2hp={p2_enemy_hp}")
    
    # === P2 moves towards P1 ===
    dx = p1_enemy_x - p2x
    dy = p1_enemy_y - p2y
    mag = math.hypot(dx, dy)
    
    if mag > 10:
        # Release old direction
        move_fast(P2_PORT, "W", "release")
        move_fast(P2_PORT, "A", "release")
        move_fast(P2_PORT, "S", "release")
        move_fast(P2_PORT, "D", "release")
        
        # Set new direction
        if abs(dx) > abs(dy):
            if dx > 0:
                move_fast(P2_PORT, "D", "press")
                last_move[P2_PORT] = "D"
            else:
                move_fast(P2_PORT, "A", "press")
                last_move[P2_PORT] = "A"
        else:
            if dy > 0:
                move_fast(P2_PORT, "S", "press")
                last_move[P2_PORT] = "S"
            else:
                move_fast(P2_PORT, "W", "press")
                last_move[P2_PORT] = "W"
    
    # === P1 moves towards P2 ===
    dx = p2_enemy_x - p1x
    dy = p2_enemy_y - p1y
    mag = math.hypot(dx, dy)
    
    if mag > 10:
        move_fast(P1_PORT, "W", "release")
        move_fast(P1_PORT, "A", "release")
        move_fast(P1_PORT, "S", "release")
        move_fast(P1_PORT, "D", "release")
        
        if abs(dx) > abs(dy):
            if dx > 0:
                move_fast(P1_PORT, "D", "press")
                last_move[P1_PORT] = "D"
            else:
                move_fast(P1_PORT, "A", "press")
                last_move[P1_PORT] = "A"
        else:
            if dy > 0:
                move_fast(P1_PORT, "S", "press")
                last_move[P1_PORT] = "S"
            else:
                move_fast(P1_PORT, "W", "press")
                last_move[P1_PORT] = "W"
    
    # === Both aim and shoot ===
    aim_and_shoot(P2_PORT, p1_enemy_x, p1_enemy_y, p2x, p2y)
    aim_and_shoot(P1_PORT, p2_enemy_x, p2_enemy_y, p1x, p1y)
    
    time.sleep(0.2)

# Release all keys
for p in [P1_PORT, P2_PORT]:
    for k in ["W","A","S","D"]:
        move_fast(p, k, "release")

print("Fight complete!")
s2 = get_screen(P2_PORT)
s1 = get_screen(P1_PORT)
print(f"Final screens: P2={s2}, P1={s1}")
