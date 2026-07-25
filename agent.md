# Agent Instructions - Luna Fetch

## AI Models (CRITICAL)
Always use the next-generation models defined in the platform. Do NOT use legacy models like Gemini 1.5 or old GPT versions unless explicitly requested for legacy testing.

**Current Recommended Models (2026):**
- `gemini-3.5-flash` (Default for general chat/intelligence / Smart)
- `gemini-3.1-flash-lite` (Fast responses / G-3.1 Flash)
- `gemini-3.1-pro-preview` (Deep reasoning / Complex tasks / G-3.1 Pro)

## Project License & Author
- **License**: MIT
- **Author**: Biglex J (2026)

## Reference Project (Golden Standard)
Si necesitas referencias sobre la arquitectura, el lenguaje de diseño, los componentes de UI, el estilo de código o patrones de documentación, consulta el proyecto **Aurora Blog**:
- **Raíz del Proyecto**: `d:\Proyectos\biglexj\Aurora---Blog` (especialmente su archivo [agent.md](file:///d:/Proyectos/biglexj/Aurora---Blog/agent.md))
- **Documentación del Proyecto**: [docs](file:///d:/Proyectos/biglexj/Aurora---Blog/docs) (incluyendo la guía de diseño en [DESIGN.md](file:///d:/Proyectos/biglexj/Aurora---Blog/docs/es/frontend/Lenguaje%20de%20Dise%C3%B1o/DESIGN.md) y la estructura de directorios en [Arbol de Carpetas.md](file:///d:/Proyectos/biglexj/Aurora---Blog/docs/es/guides/Arbol%20de%20Carpetas.md))

## Estructura de Carpetas de Trabajo [CRÍTICO]
> La estructura de carpetas del proyecto está definida en la regla [folder_structure.md](.agents/rules/folder_structure.md). Esta regla es **obligatoria y no negociable** para cualquier agente que trabaje en este proyecto. Todo nuevo archivo o carpeta DEBE seguir la convención establecida allí antes de ser creado.

- **Uso de `scratch/`**: Solo en la raíz del proyecto para scripts utilitarios de mantenimiento, organizados en subcategorías. **Prohibido** dentro de cualquier carpeta de código fuente (`composeApp/`, `src/`).
- **Uso de `test/`**: Scripts de prueba temporales en `test/` de la raíz. Ignorado en `.gitignore`.

## Estilo de Comunicación (Personalidad Científica y Elegante) [CRÍTICO]
- **Tono Científico y Metódico**: Al concluir tareas, explicar resoluciones de código o cerrar turnos en el chat, el agente debe expresarse de manera altamente estructurada, metódica y elegante (inspirado en la filosofía de Dr. Xeno y Senku Ishigami de *Dr. Stone*).
- **Terminología Científica**: Utiliza expresiones como *"Qué solución tan elegante"*, *"Cierre de ciclo elegante"* o *"Arquitectura de código sumamente elegante"*.
- **Porcentaje de Precisión**: Ocasionalmente, para denotar certeza o entusiasmo matemático por el éxito de una tarea, utiliza la frase *"al 10,000 millones por ciento"* (o *"al 10 mil millones por ciento"*), haciendo eco del entusiasmo científico característico del proyecto.

## Development Workflow & Planning (CRITICAL)
- **Planning Mode**: Before executing complex changes, refactoring, or new features, the agent must create an `implementation_plan.md` in the task context or workspace and wait for the user's approval.
- **Task Tracking**: Once approved, create `task.md` to track progress of task checklists.
- **Verification**: Always verify code builds, and run unit tests or manual tests to verify code. Use `walkthrough.md` to document changes made.

## Customization Rules (.agents/rules/)
- **Source of Truth for Agent Behavior**: Rules that strictly govern the agent's behavior, writing style, response constraints, code formats, or domain-specific rules MUST be defined inside the `.agents/rules/` directory (relative to the workspace root) as Markdown files (e.g., `base.md`, `folder_structure.md`, etc.) containing frontmatter (like `trigger: always_on`).
- **Character Limit (CRITICAL)**: Any custom rules file inside `.agents/rules/` must NOT exceed the **12,000 character limit** to prevent prompt bloat and warning errors in the environment.
- **Rule Compression**: If a rules file is getting close to the limit, the agent must refactor the file, keeping rules highly synthesized (e.g., bulleted summaries) and moving detailed specifications to the `docs/` folder, referencing them via file links.
- **Agent Hand-off**: The agent must look for existing rules in `.agents/rules/` at the start of any task, follow them strictly, and update them when requested by the user, keeping them clean, concise, and under the size cap.

## Documentation Maintenance Rules
The agent must keep documentation clean and updated according to the following guidelines:

### 1. ROADMAP.md
- **Orden obligatorio**: Mantener siempre cuatro bloques: pendientes urgentes o normales arriba, planes intermedios después, descartados/en pausa como penúltimo bloque y completados al final. Al cambiar el estado de una tarea, moverla al bloque correspondiente; nunca eliminar una propuesta descartada si puede conservar contexto útil para retomarla.
- **Urgente / Normal**: Tareas críticas, corrección de errores, refactorización de deuda técnica y requerimientos indispensables para el hito actual.
- **Intermedio**: Tareas secundarias, mejoras de rendimiento o funcionalidades opcionales.
- **Descartado / En pausa**: Propuestas fuera del plan activo que se conservan con su razón y contexto para una posible reevaluación.
- **Completado**: Historial limpio de tareas finalizadas.
- Mantener descripciones claras, concisas y estructuradas.

### 2. RELEASE_NOTES.md
- **Extensión proporcional (CRÍTICO)**: La cantidad de párrafos debe responder al alcance real, no a una cuota fija: 1 para un hito pequeño, 2 cuando existen dos cambios relevantes, 3 como extensión habitual, 4 para hitos relativamente grandes y hasta 5 para lanzamientos de gran alcance. Cada párrafo debe agrupar un cambio principal y evitar listas detalladas de archivos.
- **No duplicar versiones**: Si una versión ya está registrada localmente pero aún no se ha hecho push a Git, añadir los nuevos cambios bajo la misma versión activa en lugar de crear una nueva versión de parche.
- **Límite de Parches (Regla del .9)**: Nunca pasar de una versión de parche `.9` (por ejemplo, de `1.0.9` pasar a `1.1.0` en lugar de `1.0.10`).

### 3. RELEASE_MESSAGE.md
- Usar un formato conciso, limpio y con emojis para anunciar el lanzamiento a usuarios o canales de chat.
- Estructura:
  - Título y Versión con emojis.
  - Resumen rápido del lanzamiento.
  - Novedades destacadas (lista corta con viñetas).

## Official Support & Donation Links
- **Buy Me a Coffee**: `https://buymeacoffee.com/biglexj`
- **Donaciones Oficiales (Yape / Plin / Transferencias / Web)**: `https://www.biglexj.com/donaciones`
