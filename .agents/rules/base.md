---
trigger: always_on
---

# Instrucciones del proyecto — Luna Fetch

## Documentación Core — entrada compartida

Consultar primero [Documentación Core](../../../Docs/README.md) y seleccionar solo los módulos pertinentes. Los estándares compartidos se mantienen allí; este archivo conserva el alcance y los contratos específicos del proyecto.

- [Calidad y entrega proporcionales](../../../Docs/global/quality/quality-baseline.md): build para ajustes visuales pequeños; pruebas pertinentes para elementos, lógica o páginas nuevos y cambios grandes; revisión acotada para documentación.
- [Procesos según alcance](../../../Docs/global/documentation/process-workflow.md): no añadir fases, archivos ni nuevas aprobaciones por rutina a ajustes pequeños ya autorizados.
- [Diseño común](../../../Docs/global/design/design_system.md) y [textos de interfaz](../../../Docs/global/design/interface-writing.md): contenido preciso, espacio útil, mínimo scroll innecesario, conservando la identidad del producto.
- [Estilo de comunicación](../../../Docs/global/agents/communication-style.md): registro «Scientific Elegant» en resúmenes y cierres; no aplica a los textos de interfaz.
- [Reglas de trabajo del agente](../../../Docs/global/documentation/agent-working-rules.md): entrada única, límite de 12.000 caracteres por archivo de reglas y commits de resguardo.
- [Comandos mediante Mise](../../../Docs/global/architecture/mise-command-standard.md) y [scripts y alias](../../../Docs/global/architecture/command-scripts-standard.md): Mise es la interfaz de comandos; las tareas de Gradle son implementación.
- [Base de lanzamientos](../../../Docs/global/releases/release-baseline.md), [protocolo de lanzamiento](../../../Docs/global/releases/protocolo_lanzamiento_actualizacion.md) y [mensaje de release web](../../../Docs/global/releases/web-release-message-standard.md): versionado, inmutabilidad de lo publicado y notas públicas.
- [Soporte oficial y diálogo «Acerca de»](../../../Docs/features/official-support/official-support-baseline.md): autoría, licencia y enlaces oficiales del ecosistema.
- [Modelos de IA](../../../Docs/features/ai-models/README.md): criterio de selección, migración y validación. Luna Fetch no mantiene un registro local de modelos; su configuración ejecutable es la fuente de verdad.
- [Diagnósticos reutilizables](../../../Docs/troubleshooting/README.md) y [promoción a Core](../../../Docs/global/documentation/core-promotion-workflow.md): documentar en Core los fallos confirmados o aprendizajes generalizables y enlazarlos desde el proyecto; mantener locales los detalles exclusivos.

En estos criterios comunes, aplicar las bases vigentes enlazadas en lugar de copias históricas de este archivo. Respetar la dirección actual de Biglex y los contratos específicos del producto; ante una contradicción, comprobar la implementación vigente y resolverla explícitamente. Las skills generales importadas son apoyo técnico, no una autoridad para redefinir estas reglas.

Entrada única de instrucciones: `.agents/rules/base.md`. No se crean `AGENTS.md` ni `agent.md` equivalentes en la raíz.

## Contratos locales de Luna Fetch

- Aplicación **Kotlin Multiplatform / Compose** (Windows y Android) centrada en la lectura y el asisten del contenido. Licencia MIT; autoría `biglexj` (2026).
- Alcance y módulos Core adoptados: [perfil Core del proyecto](core_profile.md).
- Estructura real del repositorio: [estructura de carpetas](folder_structure.md).

### Instancia única y bandeja

- Luna Fetch es una aplicación **residente en la bandeja del sistema** con atajos globales: aplica la política de instancia única de Core ([instancia única y reactivación](../../../Docs/features/single-instance/README.md)).
- El bloqueo tiene **bypass en desarrollo** mediante la propiedad `-Dlunafetch.dev=true`, que inyecta la tarea `:composeApp:run`. Detalle en [estándares de escritorio](desktop_app_standards.md).

### Auto-actualización y feedback

- Comprobación de actualizaciones desde GitHub Releases: [regla de auto-actualización](auto_updater.md) (puente a Core).
- Canal de feedback vigente: [centro de feedback](feedback_center.md) (puente a Core).

### Diseño

- Material 3 Expressive en las superficies Compose: [lenguaje de diseño](design_system.md) (puente a Core).

### Scripts y lanzadores

- Los scripts de implementación viven en `scripts/`. El lanzador `build-release.ps1` de la raíz es una **envoltura de conveniencia** que redirige a `scripts/build/build-release.ps1` y admite tanto `local` como `-LocalOnly`; `dev.bat` arranca la aplicación de escritorio con doble clic.
- `gradlew` y `gradlew.bat` son wrappers técnicos del stack y se conservan.

### Flujo de trabajo

- Rama de trabajo `preview`; el merge a `main` ocurre solo con la instrucción explícita de lanzar versión, conforme al [protocolo de Core](../../../Docs/global/releases/protocolo_lanzamiento_actualizacion.md).
- El trabajo planificado vive en `process/active/YYYY-MM-DD_objetivo/` con `PLAN.md`, `TASKS.md`, `VALIDATION.md` y `APPROVAL.md`; `ROADMAP.md` conserva los pendientes generales.

## Referencias locales especializadas

- [core_profile.md](core_profile.md) — alcance Core declarado del proyecto.
- [folder_structure.md](folder_structure.md) — estructura real del repositorio.
- [auto_updater.md](auto_updater.md) — puente a la base de auto-actualización de Core.
- [desktop_app_standards.md](desktop_app_standards.md) — puente a los estándares de escritorio de Core.
- [design_system.md](design_system.md) — puente al lenguaje de diseño de Core.
- [feedback_center.md](feedback_center.md) — puente al estándar de feedback de Core.
