# Sincronización de Documentación Core — Tareas

- Estado: COMPLETED

## Ejecución

- [x] T01 — Inicializar `.agents/rules/core_profile.md` para Luna Fetch.
- [x] T02 — Actualizar `agent.md` en la raíz con el template de `Core-Docs/templates/agents/agent.md`.
- [x] T03 — Actualizar `.agents/rules/folder_structure.md` con el template de `Core-Docs/templates/project/folder_structure.md` y adaptarlo al stack de Compose Multiplatform.
- [x] T04 — Sincronizar `.agents/rules/auto_updater.md` y `.agents/rules/feedback_center.md` con las nuevas reglas de `Core-Docs/features`.
- [x] T05 — Migrar las tareas activas pendientes del antiguo `TASKS.md` a este archivo (sección de Tareas del Proyecto / Backlog) y el historial de fases.
- [x] T06 — Eliminar el `TASKS.md` de la raíz del proyecto y la carpeta `plan/`.
- [x] T07 — Verificar límites de caracteres de las reglas locales (<12,000 caracteres por archivo).
- [x] T08 — Preparar y ejecutar la validación en `VALIDATION.md`.

## Tareas del Proyecto / Backlog Migrado

### 🔴 Pendientes Activos de Desarrollo (Migrados de TASKS.md)
- [ ] **Selección Fina de Colecciones**: Interfaz para pre-seleccionar ítems individuales dentro de playlists de YouTube/TikTok antes de descargar.
- [ ] **Gestión de yt-dlp y FFmpeg**: Panel visual de actualización y control de canal (Stable/Nightly) en Ajustes.
- [ ] **Cola de Descargas Simultáneas**: Gestión de descargas en paralelo con límite configurable.
- [ ] **Pruebas Físicas Android**: Validación de descargas reales en dispositivos Android físicos por ABI (ARM64, ARM32, x86_64).
- [ ] **Empaquetado Linux**: Pruebas de instalación y ejecución en paquetes DEB/RPM.

### ⏳ Checklist de Verificación y Validaciones del Proyecto (Migrado)
- [ ] **Single-Instance Lock en Windows Desktop JVM**:
  - *Cómo probar:* Intentar iniciar una segunda instancia de Luna Fetch con la primera en ejecución.
  - *Resultado esperado:* La primera ventana sale al frente y la segunda se cierra inmediatamente con código 0.
- [ ] **Verificación de Auto-Actualización y Sanitización**:
  - *Cómo probar:* Pulsar "Buscar actualizaciones" en Ajustes / Acerca de estando en v1.1.3.
  - *Resultado esperado:* Se cierra la ventana modal anterior de forma síncrona y aparece un Toast flotante centrado en la parte superior ("✅ Estás en la última versión") durante 4 segundos.

### 🧭 Fases Técnicas del Proyecto (Historial)
- [x] Fase 1 — Estandarización y Mantenimiento de Reglas (Completado)
