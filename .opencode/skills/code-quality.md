# Code Quality Skill

## Description
Principios SOLID, testing, thread safety y refactorización del proyecto multiplayer shooter.

## When to use
- Aplicar refactors SOLID en el código del juego
- Escribir tests nuevos (unitarios, integración, UI)
- Depurar problemas de concurrencia (thread safety)
- Agregar nuevos sistemas de juego o tipos de mensaje
- Revisar código en busca de violaciones de arquitectura

## Principios SOLID

### SRP — Single Responsibility
- `GameEngine.java:28-34`: Compone `GameSystem`s — cada sistema tiene exactamente UNA responsabilidad (`MovementSystem`, `ShootingSystem`, `CollisionSystem`, `SkillSystem`, etc.)
- `Renderer` separado de lógica de HUD (interfaces `IScreen` con `createScene()`, `getErrorMessage()`, `setError()`)

### OCP — Open/Closed
- `GameSystem` interface (`GameSystem.java:9`): Implementa la interfaz, registra en `GameEngine.systems`. Nunca editar `GameEngine.update()`.
- `PlayerSkill.java`: Agregar valor al enum + case en `SkillSystem.activateSkill()` + opcionalmente actualizar HUD
- `mcp/tools/XxxTools.java`: Agregar tools sin tocar `ClientMcpServer` ni `McpToolRegistry`

### LSP — Liskov Substitution
- `McpTransport.java` (3 métodos: `start()`, `stop()`, `isRunning()`): `McpTcpTransport` y `McpStdioTransport` son intercambiables
- `IScreen` interface: `LoginScreen`, `LobbyScreen`, `GameScreen` — usables indistintamente via `ScreenManager`

### ISP — Interface Segregation
- `GameSystem` tiene 1 solo método (`update`). `McpTransport` tiene 3 métodos. Mantener interfaces pequeñas — no "God interfaces".

### DIP — Dependency Inversion
- `GameEngine.java:24-26`: Inyección por setter — `MovementSystem` recibe `PowerUpSystem` y `UpgradeSystem`, `DamageSystem` recibe `UpgradeSystem`
- `GameEngine.update()` itera sobre `List<GameSystem>`, no clases concretas
- `McpToolRegistry` recibe dependencias vía constructor, no las crea

## Patrones de Testing

### Tests unitarios
- Crear `GameState` + `Player` directamente en `@BeforeEach` de cada test (ver `MovementSystemTest.java:27-32`)
- Sin fixtures compartidos, sin clases base de test — cada archivo auto-contenido
- Naming: `XxxTest.java` (surefire captura `*Test.java`), métodos con `@DisplayName` en español
- `@MockitoSettings(strictness = Strictness.LENIENT)` para setups complejos de mocks

### Tests de integración
- Ubicar en `integration/` sub-paquete (ej: `GameFlowIntegrationTest.java`)
- Excluir con: `mvn test -pl server -Dtest="!*IntegrationTest"`
- Usar instancias reales de servicios, mockear solo network (`ClientConnection`)
- `DatabaseManager.initForTest()` en `@BeforeAll` para tests con BD
- `Thread.sleep()` para esperas asíncronas (sin Awaitility)

### Tests UI (cliente)
- TestFX 4.0.18 + `@ExtendWith(ApplicationExtension.class)`, mock de `GameClient` y `ScreenManager`
- Linux: `Xvfb :99 -ac -screen 0 1280x720x24 &` + `DISPLAY=:99 mvn test -pl client`
- Headless sin Xvfb: `prism.order=sw` en `argLine` del surefire

### Tests de concurrencia
- Ver `GameInstanceConcurrencyTest.java`: Múltiples threads encolando inputs + un thread consumidor con `processTick()`
- Usar `CountDownLatch` para sincronizar threads, `ExecutorService` con pool fijo
- Validar `ConcurrentLinkedQueue` de `GameInstance.queueInput()`

## Thread Safety (CRÍTICO)

1. **SOLO el hilo `GameLoop` muta `GameState`** — el hilo de red encola inputs en `GameInstance` via `ConcurrentLinkedQueue`
2. **Siempre `state.copy()` antes de broadcast** — nunca enviar referencia mutable de `GameState`
3. `GameLoop.java:69-72`: Corrección de deriva — si `nextTick < now`, reinicia `nextTick` para evitar espiral de muerte
4. `RateLimiter` tiene su propio test de thread safety en `RateLimiterTest.java`

## Refactoring Guidelines

### Cuándo extraer una clase nueva:
- Archivo >200 líneas con múltiples responsabilidades
- Mismo patrón de acceso aparece 3+ veces
- Una clase tiene "y" en su descripción

### Cuándo crear una interfaz:
- Existen o se planean 2+ implementaciones
- Se inyecta una dependencia que podría cambiar
- Se necesita mockear en tests

### Vector2 (CRÍTICO — record inmutable):
```java
// CORRECTO:
player.setPosition(player.getPosition().add(movement));
velocidad.multiply(scalar);
new Vector2(x, y);

// INCORRECTO (Vector2.add devuelve un nuevo Vector2, no modifica el original):
player.getPosition().add(dx, dy);  // NO hace nada — el resultado se descarta
```

## Violaciones conocidas (arreglar con cuidado)
1. `DamageSystem.applyDamageReduction()` existe pero `CollisionSystem` NO lo invoca — daño aplicado sin reducción de upgrades
2. `ADRENALINE` en `SkillSystem.java:73-76` activa timer pero `MovementSystem`/`ShootingSystem` no lo verifican — sin efecto real
3. `GameLoop.java:11` dice "20 Hz" pero `ServerConfig.TICK_RATE=30` → corregir comentarios, no cambiar tick rate sin testear
