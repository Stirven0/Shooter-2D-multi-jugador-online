# Session Checkpoint Skill

## Description
Punto de control de sesión. Se activa con frases como "terminamos por hoy", "hasta mañana", "cerramos sesión", "fin del día", "guardar progreso".

## When to use
Al finalizar una sesión de desarrollo, para guardar el estado del proyecto en `MEMORY.md` con datos frescos de tests.

## Procedimiento

### 1. Obtener estado del repositorio
```bash
git branch --show-current
git log --oneline -5
```

### 2. Ejecutar tests y capturar conteos frescos
```bash
mvn test -pl server -Dtest="!*IntegrationTest" 2>&1 | grep -E "Tests run:"
```
Para tests de cliente, verificar si Xvfb está disponible:
```bash
pgrep Xvfb || echo "Xvfb no disponible"
```
Si Xvfb está corriendo (o tras iniciarlo con `Xvfb :99 -ac -screen 0 1280x720x24 &`):
```bash
DISPLAY=:99 mvn test -pl client 2>&1 | grep -E "Tests run:"
```
Si Xvfb no está disponible, anotar "requiere Xvfb" en el reporte.

### 3. Actualizar MEMORY.md
Abrir `MEMORY.md` (raíz del proyecto) y añadir la nueva sesión al inicio, preservando el historial previo.

Formato de entrada:
```markdown
## Sesión actual: {resumen corto} ({fecha ISO})

## Estado
- **Rama**: `{branch}`
- **Tests servidor**: {pasaron}/{total} pasan
- **Tests UI cliente**: {pasaron}/{total} pasan
- **Build**: mvn clean install -DskipTests → BUILD SUCCESS

## Cambios aplicados
### {Categoría}
- {cambio realizado}

## Pendiente
- {tarea pendiente para próxima sesión}
```

### 4. Revisar cambios de arquitectura
Si la arquitectura cambió significativamente (nuevos módulos, patrones, refactors grandes), sugerir actualizar `AGENTS.md` o los archivos de reglas en `.opencode/`.

### 5. Reportar resumen
Formato: "MEMORY.md actualizado. Tests: {server} server, {client} client. Rama: {branch}."
