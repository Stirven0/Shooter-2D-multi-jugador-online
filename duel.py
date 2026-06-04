#!/usr/bin/env python3
"""Dual fight loop - controls both player1 (port 4568) and player2 (port 4567)"""
import json, socket, time, threading

HOST = "localhost"
P1_PORT = 4568
P2_PORT = 4567

game_over = False
result = {"winner": None}

def mcp_call(port, method, args=None):
    if args is None: args = {}
    payload = {"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":method,"arguments":args}}
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        s.settimeout(2)
        s.connect((HOST, port))
        s.sendall((json.dumps(payload) + "\n").encode())
        data = s.recv(65535)
        s.close()
        resp = json.loads(data.decode())
        if "result" in resp and "content" in resp["result"]:
            texts = [c["text"] for c in resp["result"]["content"] if c.get("type") == "text"]
            return "\n".join(texts)
        return None
    except:
        return None

def parse_json(text):
    try: return json.loads(text)
    except: return None

def send_key(port, key, action="click"):
    mcp_call(port, "send_key", {"key": key, "action": action})

def fight_loop(port, name):
    global game_over
    iterations = 0
    while not game_over and iterations < 500:
        iterations += 1

        # Get position
        pos_text = mcp_call(port, "get_player_position")
        if not pos_text: time.sleep(0.3); continue
        pos = parse_json(pos_text)
        if not pos or "x" not in pos: time.sleep(0.3); continue
        my_x, my_y = pos["x"], pos["y"]

        # Get enemy
        enemy_text = mcp_call(port, "get_other_players")
        if not enemy_text: time.sleep(0.3); continue
        enemy_data = parse_json(enemy_text)
        if not enemy_data or "players" not in enemy_data or len(enemy_data["players"]) == 0:
            time.sleep(0.3); continue

        enemy = enemy_data["players"][0]
        enemy_x = enemy["x"]; enemy_y = enemy["y"]
        enemy_health = enemy.get("health", "?")
        enemy_alive = enemy.get("alive", True)

        # Get my HUD
        hud_text = mcp_call(port, "get_hud_info")
        my_health = "?"; my_alive = True
        if hud_text:
            hud = parse_json(hud_text)
            if hud:
                my_health = hud.get("health", "?")
                my_alive = hud.get("alive", True)

        if not my_alive:
            print(f"[{name}] DIED at iter {iterations}!")
            result["winner"] = "player2" if name == "player1" else "player1"
            game_over = True
            break
        if not enemy_alive:
            print(f"[{name}] Enemy died! We win!")
            result["winner"] = name
            game_over = True
            break

        dx = enemy_x - my_x; dy = enemy_y - my_y

        # Movement toward enemy
        if dx > 50:
            send_key(port, "D", "press"); send_key(port, "A", "release")
        elif dx < -50:
            send_key(port, "A", "press"); send_key(port, "D", "release")
        else:
            send_key(port, "A", "release"); send_key(port, "D", "release")

        if dy > 50:
            send_key(port, "S", "press"); send_key(port, "W", "release")
        elif dy < -50:
            send_key(port, "W", "press"); send_key(port, "S", "release")
        else:
            send_key(port, "W", "release"); send_key(port, "S", "release")

        # Aim and shoot
        mcp_call(port, "aim_at", {"x": enemy_x, "y": enemy_y})
        send_key(port, "CLICK", "click")

        # Heal if low
        if isinstance(my_health, (int, float)) and my_health < 40:
            print(f"[{name}] HEALING! hp={my_health}")
            send_key(port, "E", "click")

        if iterations % 10 == 0:
            print(f"[{name}] iter {iterations}: pos=({my_x:.0f},{my_y:.0f}) hp={my_health} enemy=({enemy_x:.0f},{enemy_y:.0f}) hp={enemy_health}")

        time.sleep(0.3)

    if not game_over:
        game_over = True
    print(f"[{name}] Loop ended after {iterations} iterations")

print("=== DUAL BATTLE STARTED ===")

t1 = threading.Thread(target=fight_loop, args=(P1_PORT, "player1"), daemon=True)
t2 = threading.Thread(target=fight_loop, args=(P2_PORT, "player2"), daemon=True)
t1.start()
t2.start()

# Wait for game to end
try:
    t1.join(timeout=180)
    t2.join(timeout=180)
except:
    pass

print(f"\n=== BATTLE OVER ===")
if result["winner"]:
    print(f"Winner: {result['winner']}!")
else:
    print("Draw or timeout")
