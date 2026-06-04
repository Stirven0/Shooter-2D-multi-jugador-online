# Sub-Agent Orchestration Skill

## Description
Uso eficiente de sub-agentes de OpenCode en este proyecto. Guía rápida para delegar tareas sin perder contexto ni tiempo.

## Tipos de agente y cuándo usarlos

### Explore Agent (solo lectura)
Busca archivos por patrón, busca código por keywords, responde preguntas de arquitectura.
Nivel de exhaustividad: **"quick"** (búsquedas básicas), **"medium"** (moderado), **"very thorough"** (integral, revisa convenciones, tests, docs).
Usar para: encontrar dónde se define una clase, buscar todos los usos de un método, entender el flujo de mensajes.

### General Agent (lectura/escritura)
Tareas complejas multi-paso: escribir código, investigar, operaciones de archivos.
Usar para: crear archivos nuevos desde plantillas, depurar bugs complejos, analizar arquitectura.

## Paralelismo
Lanza múltiples agentes simultáneamente cuando las tareas sean independientes.
Ejemplo: explorar motor del juego + explorar infraestructura de tests + explorar motor de tiles en paralelo.

## Paso de datos a sub-agentes
Pasa prompts detallados con rutas verificadas, ubicaciones de archivos y nombres de clases.
Los sub-agentes **pierden el contexto de la conversación** — sé explícito y no asumas que saben lo que tú sabes.

## Cuándo NO usar sub-agentes
- Lectura simple de archivos → usa la herramienta **Read**
- Búsqueda única con grep → usa la herramienta **Grep**
- Encontrar una definición de clase específica → usa **Grep** directamente

## Verificación post-cambios
Siempre ejecutar tests/lint después de cambios de código:
```bash
mvn test -pl server -Dtest="!*IntegrationTest"   # tests unitarios servidor
DISPLAY=:99 mvn test -pl client                    # tests UI cliente
```
