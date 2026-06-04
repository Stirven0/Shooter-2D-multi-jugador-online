#!/usr/bin/env python3
"""Fight loop for player2 (port 4567) vs player1 (port 4568)"""
import json, socket, time

HOST = "localhost"
P2_PORT = 4567  # player2 (MCP client)
P1_PORT = 4568  # player1 (MCP client)

def mcp_call(port, method, args=None):
    if args is None: args = {}
    payload = {"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":method,"arguments":args}}
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        s.settimeout(3)
        s.connect((HOST, port))
        s.sendall((json.dumps(payload) + "\n").encode())
        data = s.recv(65535)
        s.close()
        resp = json.loads(data.decode())
        if "result" in resp and "content" in resp["result"]:
            texts = [c["text"] for c in resp["result"]["content"] if c.get("type") == "text"]
            return "\n".join(texts)
        return None
    except Exception as e:
        return None

def parse_json(text):
    try: return json.loads(text)
    except: return None

def send_key(key, action="click"):
    mcp_call(P2_PORT, "send_key", {"key": key, "action": action})

iterations = 0
max_iter = 600

print("=== FIGHT LOOP STARTED ===")

while iterations < max_iter:
    iterations += 1

    # Get player2 position
    pos_text = mcp_call(P2_PORT, "get_player_position")
    if not pos_text: time.sleep(0.3); continue
    pos = parse_json(pos_text)
    if not pos or "x" not in pos: time.sleep(0.3); continue
    my_x, my_y = pos["x"], pos["y"]

    # Get enemy info
    enemy_text = mcp_call(P2_PORT, "get_other_players")
    if not enemy_text: time.sleep(0.3); continue
    enemy_data = parse_json(enemy_text)
    if not enemy_data or "players" not in enemy_data or len(enemy_data["players"]) == 0:
        time.sleep(0.3); continue
    
    enemy = enemy_data["players"][0]
    enemy_x = enemy["x"]; enemy_y = enemy["y"]
    enemy_health = enemy.get("health", "?")
    enemy_alive = enemy.get("alive", True)

    # Get player2 HUD
    hud_text = mcp_call(P2_PORT, "get_hud_info")
    my_health = "?"; my_alive = True
    if hud_text:
        hud = parse_json(hud_text)
        if hud: my_health = hud.get("health", "?"); my_alive = hud.get("alive", True)

    if not my_alive:
        print(f"Iter {iterations}: PLAYER2 DIED!")
        break
    if not enemy_alive:
        print(f"Iter {iterations}: PLAYER1 DIED! We win!")
        break

    dx = enemy_x - my_x; dy = enemy_y - my_y

    # Movement - press/release based on dx, dy
    if dx > 50:
        send_key("D", "press"); send_key("A", "release")
    elif dx < -50:
        send_key("A", "press"); send_key("D", "release")
    else:
        send_key("A", "release"); send_key("D", "release")

    if dy > 50:
        send_key("S", "press"); send_key("W", "release")
    elif dy < -50:
        send_key("W", "press"); send_key("S", "release")
    else:
        send_key("W", "release"); send_key("S", "release")

    # Aim and shoot
    mcp_call(P2_PORT, "aim_at", {"x": enemy_x, "y": enemy_y})
    send_key("CLICK", "click")

    # Heal if low
    if isinstance(my_health, (int, float)) and my_health < 40:
        print(f"  HEALING! hp={my_health}")
        send_key("E", "click")

    print(f"Iter {iterations}: me=({my_x:.0f},{my_y:.0f}) hp={my_health} enemy=({enemy_x:.0f},{enemy_y:.0f}) hp={enemy_health}")

    time.sleep(0.3)

print("\n=== LOOP ENDED ===")

# Wait for gameover
print("Waiting for gameover...")
mcp_call(P2_PORT, "wait_for_screen", {"screen": "gameover", "timeout_ms": 60000})
print("Game over detected!")

# Logout
mcp_call(P2_PORT, "ui_logout")
print("Logged out.")
