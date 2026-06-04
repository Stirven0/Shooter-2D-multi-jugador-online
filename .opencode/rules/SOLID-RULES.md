# SOLID en el Shooter 2D Multijugador

> Principios aplicados con ejemplos reales del código. Sin teoría genérica.

---

## S — Single Responsibility (Una sola razón para cambiar)

| Clase | Responsabilidad única |
|---|---|
| `McpGameContext` | Acceso centralizado y thread-safe al estado del juego (jugadores, balas, pickups, info de pantalla). Eliminó 6+ patrones de acceso repetidos que estaban dispersos en los tool providers. |
| `HudRenderer` (rama `tile-engine`) | Todo el renderizado de HUD extraído de `Renderer`: barra de HP, escudo, armas, skills, scoreboard, kill feed, overlay de debug. `Renderer` ahora solo orquesta la escena. |
| `GameLoop` | Solo ejecuta el tick loop a 30Hz fijo. Delega la lógica de actualización a `GameEngine`, que a su vez delega en 7 `GameSystem`. |
| `GameEngine` | Solo compone y ejecuta la lista de sistemas en orden. No contiene lógica de juego propia. |

---

## O — Open/Closed (Abierto a extensión, cerrado a modificación)

| Mecanismo | Cómo se extiende sin tocar el núcleo |
|---|---|
| `GameSystem` (interfaz) | 7 implementaciones: `MovementSystem`, `ShootingSystem`, `CollisionSystem`, `DamageSystem`, `PowerUpSystem`, `SkillSystem`, `UpgradeSystem`. Agregar un sistema nuevo = crear una clase que implemente `update(GameState, float, List<PlayerInput>, GameMap)` y registrarla en `GameEngine`. Nunca se edita el bucle interno de `GameEngine.update()`. |
| `McpToolRegistry` | Centraliza 22 tools en 6 providers (`mcp/tools/XxxTools.java`). Agregar una tool = editar 1 archivo provider. `ClientMcpServer` no se toca. |
| `PlayerSkill` (enum) | Agregar una skill = editar el enum + 1-2 cases en `SkillSystem.activateSkill()` / `deactivateEffect()`. Opcionalmente HUD. |

---

## L — Liskov Substitution (Los subtipos deben ser intercambiables)

| Contrato | Implementaciones intercambiables |
|---|---|
| `McpTransport` (interfaz) | `McpTcpTransport` (TCP JSON-RPC) y `McpStdioTransport` (MCP SDK nativo). `ClientMcpServer` depende de la interfaz — cualquiera de las dos implementaciones funciona sin modificar el servidor. |
| `IScreen` (rama `tile-engine`) | `LoginScreen`, `LobbyScreen`, `GameScreen` — todas implementan `createScene(Stage)`, `getErrorMessage()`, `setError(String)`. `ScreenManager` las trata indistintamente. |

---

## I — Interface Segregation (Interfaces pequeñas y enfocadas)

| Interfaz | Métodos |
|---|---|
| `McpTransport` | Solo `start()`, `stop()`, `isRunning()` (3 métodos). Sin acoplar transporte con tools, registro ni JSON-RPC. |
| `ClientMessageListener` | Callback mínimo de WebSocket. Sin mezclar con lógica de juego. |
| `GameSystem` | Un solo método: `update(GameState, float, List<PlayerInput>, GameMap)`. No es una interfaz Dios. |

---

## D — Dependency Inversion (Depender de abstracciones, no concreciones)

| Dependencia | Mecanismo |
|---|---|
| `MovementSystem → PowerUpSystem, UpgradeSystem` | Setter injection (`setPowerUpSystem()`, `setUpgradeSystem()` en `MovementSystem.java:19-20`). `MovementSystem` no instancia sus dependencias — las recibe desde `GameEngine`. |
| `GameEngine → List<GameSystem>` | Compone una lista de la abstracción `GameSystem`, no de implementaciones concretas. Itera con `system.update(...)` sin saber qué sistema ejecuta. |
| `McpToolRegistry` | Recibe `McpGameContext`, `ScreenManager`, etc. por constructor. No crea ni busca dependencias internamente. |

---

## Patrones de prueba

- **Tests unitarios autosuficientes**: Cada test (ej. `MovementSystemTest`) crea `GameState` + `Player` directamente, sin herencia ni fixtures compartidos.
- **`@MockitoSettings(strictness = Strictness.LENIENT)`**: Para setups complejos con stubs no usados en todos los tests.
- **Sin clases base**: Cada clase de test es independiente, con su propio `@BeforeEach`.
- **Integración segregada**: Tests de integración en sub-paquete `integration/`, excluidos con `-Dtest="!*IntegrationTest"`.
- **DB en tests**: `DatabaseManager.initForTest()` en `@BeforeAll` para tests que dependen de base de datos.

---

## Thread safety (server-authoritative)

- Solo el hilo de `GameLoop` muta `GameState`.
- El hilo de red encola inputs vía `ConcurrentLinkedQueue<PlayerInput>` en `GameInstance`.
- `state.copy()` antes de cada broadcast — patrón snapshot inmutable.
- `ConcurrentHashMap` como medida defensiva (aunque en práctica el acceso es single-thread).

---

## Inmutabilidad de Vector2

- `Vector2` es un `record` — sin setters, sin `add(double, double)`.
- Operaciones: `add(Vector2)`, `multiply(double)`, `setPosition(new Vector2(...))`.
- Cada operación devuelve una instancia nueva.

---

## Violaciones conocidas (no arreglar sin plan)

1. **`DamageSystem`**: Tiene lógica de reducción de daño no cableada en `CollisionSystem` — el daño aplicado no pasa por la reducción configurada.
2. **`ADRENALINE` skill**: `SkillSystem` activa un timer pero `MovementSystem` y `ShootingSystem` no lo consultan — la skill no tiene efecto real.
