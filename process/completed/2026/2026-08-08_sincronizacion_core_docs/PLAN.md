# Sincronización de Documentación Core — Plan

- Estado: APPROVED
- Fecha: 2026-08-08
- Proyecto: Luna Fetch

## Objetivo

Sincronizar la estructura de documentación, instrucciones del agente y reglas locales de Luna Fetch con la nueva arquitectura oficial de `Core-Docs`.

## Alcance

- Incluye:
  - Actualización de `agent.md` en la raíz del proyecto basándose en el template oficial `Core-Docs/templates/agents/agent.md`.
  - Actualización de `.agents/rules/folder_structure.md` adaptándola al nuevo template de estructura de carpetas (incluyendo las nuevas reglas de crecimiento de archivos y la estructura de procesos).
  - Creación del perfil del proyecto `.agents/rules/core_profile.md` indicando tipo, plataformas, stack y funciones.
  - Sincronización de `.agents/rules/auto_updater.md` y `.agents/rules/feedback_center.md` con las nuevas definiciones de `Core-Docs/features`.
  - Migración del flujo de trabajo: Mover las tareas técnicas activas e historial de fases desde el `TASKS.md` de la raíz al proceso activo y eliminar `TASKS.md` y la carpeta obsoleta `plan/` de la raíz.
- No incluye:
  - Cambios en el código fuente de la aplicación (salvo actualizaciones de variables/propiedades en scripts del sistema de empaquetado si correspondiera).

## Enfoque

1. **Creación del Proceso Activo**: Crear la estructura del proceso en `process/active/2026-08-08_sincronizacion_core_docs/` con las plantillas de `PLAN.md`, `TASKS.md`, `VALIDATION.md` y `APPROVAL.md`.
2. **Creación del Perfil del Proyecto**: Crear `.agents/rules/core_profile.md` rellenando el perfil específico de Luna Fetch.
3. **Actualización de Reglas**: Sincronizar y actualizar `agent.md`, `folder_structure.md`, `auto_updater.md` y `feedback_center.md` con las especificaciones de Core-Docs.
4. **Migración de Tareas**: Migrar las tareas activas y la historia de fases del actual `TASKS.md` de la raíz.
5. **Limpieza del Repositorio**: Eliminar `TASKS.md` y `plan/` obsoletos de la raíz.
6. **Validación y Aprobación**: Confirmar que todas las reglas de agente respetan los límites de caracteres (<12,000) y que la estructura sea 100% compatible.

## Criterios de finalización

- [x] Todas las reglas de agente actualizadas a las plantillas de `Core-Docs`.
- [x] Archivo `.agents/rules/core_profile.md` creado y configurado.
- [x] `TASKS.md` de la raíz eliminado y sus tareas migradas al proceso activo actual.
- [x] Carpeta `plan/` de la raíz eliminada.
- [x] No existen archivos de reglas en `.agents/rules/` que excedan los 12,000 caracteres.
- [x] El proyecto compila y funciona correctamente tras la limpieza.

## Autorización

- [x] Plan aprobado para ejecución.
