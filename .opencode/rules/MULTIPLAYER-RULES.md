# Reglas de arquitectura multijugador — Shooter 2D

## Modelo server-authoritative

- El cliente **NUNCA** envía posiciones. Solo inputs normalizados (-1..1).
- `MovementSystem.java:34-35` clampa dx/dy a `[-1.0, 1.0]` y luego normaliza magnitud a `1.0` (`MovementSystem.java:49-53`).
- El servidor computa toda la física (movimiento, colisión, daño) y envía snapshots a 30 Hz (`ServerConfig.java:11`: `TICK_RATE=30`).
- El cliente es "terminal tonta" para lógica de juego: renderiza lo que el servidor dicta.
- No hay client-side prediction, interpolación, ni lag compensation.

## Flujo de datos por tick

```
Cliente                    Red                           Servidor (GameLoop)
  |                         |                                |
  |-- MOVE_INPUT --------->|-- ConcurrentLinkedQueue ------->| (MovementSystem)
  |-- SHOOT_INPUT -------->|                                |-> (ShootingSystem)
  |-- SWAP_WEAPON -------->| (procesado inline)             |
  |-- USE_SKILL ---------->|                                |-> (SkillSystem)
  |                         |                                |
  |<-- GAME_STATE ---------|<-- state.copy() ---------------|
  |<-- MAP_DATA (una vez) -|<-- al iniciar partida ---------|
```

- `GameInstance.java:40-42`: tres colas concurrentes separadas (`inputQueue`, `disconnectQueue`, `reconnectQueue`).
- `GameInstance.java:139-190` (`processTick`): drena disconnect/reconnect, luego procesa inputs (SWAP_WEAPON inline, resto al engine).
- `GameLoop.java:48-82`: bucle a `TICK_DURATION_MS` (~33ms), corrige deriva reseteando `nextTick` (`GameLoop.java:70-72`).

## Thread safety (CRÍTICO)

- **Solo `GameLoop` muta `GameState`.** Sin excepciones.
- `GameInstance.java:40`: `ConcurrentLinkedQueue<PlayerInput>` recibe inputs desde threads de red.
- `GameInstance.java:277`: `state.copy()` antes de serializar para broadcast. **Nunca** enviar referencia mutable.
- `GameState.java:54`: `ConcurrentHashMap` para players/bullets (defensivo; en práctica solo game loop toca).
- `GameLoop.java:70-72`: si hay deriva (`nextTick < now`), reinicia `nextTick = now + tickDuration` para evitar espiral de muerte.

## Orden de ejecución de sistemas (`GameEngine.java:28-34`)

1. `MovementSystem` — aplica MOVE_INPUT
2. `ShootingSystem` — genera balas desde SHOOT_INPUT
3. `CollisionSystem` — mueve balas, detecta impactos
4. `DamageSystem` — aplica daño, marca jugadores muertos
5. `PowerUpSystem` — colisión con pickups, timers de efectos, respawn
6. `SkillSystem` — procesa USE_SKILL, cooldowns
7. `UpgradeSystem` — calcula puntos de mejora por kills

## Reglas de mensajes

- **Nunca** hacer parse manual de JSON excepto para mensajes de lobby en `MessageHandler`.
- Usar `JsonUtil.toJson()` / `parseMessage()` exclusivamente.
- `JsonUtil.java:16-36`: dos instancias Gson — `gson` (con `MessageAdapter` para polimorfismo) y `gsonPlain` (sin adapter, para tipos concretos como `MoveMessage`).
- `JsonUtil.java:133`: `CREATE_ROOM -> LoginMessage.class` en `getTargetClass()` — hack intencional. `CREATE_ROOM` se parsea manual desde `JsonObject` en `MessageHandler.java:171-173`. **NO "ARREGLAR"**.
- `NetworkClient.java:59-62`: `sendMessage()` usa `JsonUtil.toJson()`. `NetworkClient.java:64-72`: `sendJson()` envía `JsonObject` directo.

## Checklist para nuevos tipos de mensaje

1. Agregar valor a `MessageType` en `shared/src/main/java/.../message/MessageType.java`
2. Crear clase mensaje extendiendo `Message` en shared
3. Agregar `case TYPE -> NuevaClase.class` a `JsonUtil.MessageAdapter.getTargetClass()` (`JsonUtil.java:129-156`)
4. Agregar case en `MessageHandler.handleAuthenticated()` (`MessageHandler.java:98-114`)
5. Si aplica al cliente, agregar case en `GameClient.handleMessage()` (`GameClient.java:248-358`)

## MessageType no usados (NO USAR)

`ROTATE_INPUT`, `DELTA_STATE`, `ENTITY_SPAWN`, `ENTITY_DESTROY`, `PLAYER_DEATH` están definidos en `MessageType.java:30-40` pero **no** cableados en `MessageAdapter.getTargetClass()` ni en handlers. `USE_ABILITY` fue renombrado a `USE_SKILL` (`MessageType.java:31`).

## Salas y host

- `Room.java:9`: `hostId` es `volatile`, mutable.
- `RoomManager.java:34-43` (`leaveRoom`): si el que sale es host y quedan jugadores, llama a `Room.transferHostIfEmpty()`.
- `Room.java:42-50` (`transferHostIfEmpty`): asigna el primer `playerId` restante como nuevo host.
- `RoomUpdatedMessage` (`MessageHandler.java:187-196`) incluye `hostId` para que el cliente actualice la UI (corona 👑).
- Solo el host puede iniciar partida: `MessageHandler.handleStartGame()` verifica `client.getPlayerId().equals(room.getHostId())`.

## Reconexión (solo servidor)

- `GameInstance.java:42`: `reconnectQueue` (servidor valida token y encola reactivación).
- `GameInstance.java:152-159`: reactiva jugador con 100 HP y posición aleatoria.
- El cliente **NUNCA** inicia reconexión: `GameClient.java:119-128` (`logout`) limpia estado y vuelve a login.
- `GameClient.java:58-77` (`connect`): siempre crea un **nuevo** `NetworkClient`. Java-WebSocket no reutiliza objetos cerrados; `connectBlocking()` lanza `IllegalStateException`.

## PING/PONG

- `MessageHandler.java:108-110`: `case PING, PONG -> { /* heartbeat, no hacer nada */ }`. Sin tracking de latencia.

## Filtrado GAME_STATE

- `GameClient.java:308-309`: el cliente ignora `GAME_STATE` si `gameId != currentRoomId`. Evita interferencia entre salas.

## Reglas de conexión del cliente

- `Main.java:19-23`: `System.exit(0)` al cerrar ventana para matar threads non-daemon.
- `GameClient.java:119-128` (`logout`): limpia estado, cierra WebSocket, navega a login.
- `InputHandler.java:53-67`: inputs WASD/Flechas normalizados a magnitud 1.0.
- `InputHandler.java:70-76`: disparo es single-shot (se limpia `mousePressed` tras enviar).

## Broadcast

- `GameInstance.java:276-279` (`broadcastState`): `state.copy()` → nuevo `GameStateMessage(snapshot, sequence++)`.
- `GameState.java:143-196` (`copy`): copia profunda de players, bullets, skill slots y pickups. **Nunca** enviar referencia mutable.
- `MapDataMessage` se envía **una vez** al inicio de partida, no en cada tick.

## Armas (`WeaponType.java:3-8`)

| Tipo    | Daño | FireRate | Pellets | Spread |
|---------|------|----------|---------|--------|
| PISTOL  | 25   | 250ms    | 1       | 0°     |
| SHOTGUN | 15   | 800ms    | 3       | 15°    |
| RIFLE   | 20   | 150ms    | 1       | 1°     |
| SNIPER  | 75   | 1500ms   | 1       | 0°     |
| SMG     | 12   | 100ms    | 1       | 4°     |

- 2 slots (primaria/secundaria). Q para swap. `GameInstance.java:192-198` (`handleSwapWeapon`).
- `WeaponType.java:41-54` (`randomSpawnable`): PISTOL excluido de spawns aleatorios.

## Power-ups (`PowerUpType.java:3-10`)

- 7 tipos: SPEED, DAMAGE_BOOST, FIRE_RATE, SHIELD, HEALTH_PACK, SLOW, WEAKNESS.
- `PowerUpSystem.java:18-21`: radio de colisión = 25px, respawn = 15s, buffs = 8s, debuffs = 4s.
- `PowerUpSystem.java:84-89`: HEALTH_PACK es instantáneo (+30 HP). El resto son efectos temporizados.
- `PowerUpSystem.java:95-115`: multiplicadores de speed (1.5/0.6), damage (1.5/0.6), fire rate (0.6).

## Habilidades de jugador (`PlayerSkill.java:3-9`)

| Habilidad      | CD  | Duración | Tecla |
|----------------|-----|----------|-------|
| DASH           | 5s  | 0        | E     |
| SHIELD_BURST   | 15s | 3s       | E/F   |
| HEAL           | 20s | 0        | E/F   |
| ADRENALINE     | 18s | 5s       | E/F   |
| EMP            | 25s | 0        | E/F   |
| STEALTH        | 20s | 4s       | E/F   |

- 2 slots por jugador, teclas E y F. `InputHandler.java:26-27`.
- `SkillSystem.java:122-129` (`createDefaultSkills`): 2 habilidades aleatorias al spawn.
- `SkillSystem.java:50-107` (`activateSkill`): lógica por tipo de habilidad.

## Sistema de mejoras (`UpgradeSystem.java:12-17`)

- 5 niveles por kills: 2 / 5 / 9 / 14 / 20.
- Bonificaciones pasivas: daño (+5%→30%), cadencia (+5%→30%), velocidad (+5%→25%), HP max (+5→30), reducción daño (+5%→25%).
- `UpgradeSystem.java:20-30` (`update`): recalcula puntos cada tick. Persiste al morir (no se resetea).

## Persistencia en BD (`DatabaseManager.java`)

- Tabla `player_stats` (`DatabaseManager.java:78-87`): `total_kills, total_deaths, total_wins, total_games, upgrade_points`.
- `DatabaseManager.java:93-119` (`savePlayerStats`): guarda al terminar partida. SQLite por defecto (`shooter.db`), HikariCP pool (`DatabaseManager.java:19`).
- `DatabaseManager.java:49-53` (`initForTest`): SQLite en memoria para tests.

## Gotchas verificados

- `.env.example:26` dice `TICK_RATE=20` pero `ServerConfig.java:11` hardcodea `30`. Editar `ServerConfig.java` para cambiar.
- `GameLoop.java:11`: comentario dice "20 Hz" pero usa `ServerConfig.TICK_RATE` (30 Hz real).
- `Vector2.java:7`: es un **record** — sin setters. Usar `add(Vector2)`, `multiply(double)`, y `new Vector2(...)` (`SkillSystem.java:119`).
- `AuthService.java:17-18`: cuentas dev hardcoded: `player1/pass1`, `player2/pass2`. Usa BCrypt.
- `JsonUtil.java:59-63`: `parseMessage(String json)` usa `gson.fromJson(json, Message.class)` con `MessageAdapter` para resolver el tipo correcto.
- `NetworkClient.java:14-17`: extiende `WebSocketClient`. No reutilizar — `connectBlocking()` en objeto cerrado lanza `IllegalStateException`.
