# 📋 Luna Fetch — Registro de Tareas y Verificación (TASKS)

Documento dinámico de seguimiento técnico, listas de tareas activas, fases de desarrollo y checklist de verificación.

---

## 🔴 Pendientes Activos de Desarrollo

- [ ] **Selección Fina de Colecciones**: Interfaz para pre-seleccionar ítems individuales dentro de playlists de YouTube/TikTok antes de descargar.
- [ ] **Gestión de yt-dlp y FFmpeg**: Panel visual de actualización y control de canal (Stable/Nightly) en Ajustes.
- [ ] **Cola de Descargas Simultáneas**: Gestión de descargas en paralelo con límite configurable.
- [ ] **Pruebas Físicas Android**: Validación de descargas reales en dispositivos Android físicos por ABI (ARM64, ARM32, x86_64).
- [ ] **Empaquetado Linux**: Pruebas de instalación y ejecución en paquetes DEB/RPM.

---

## ⏳ Checklist de Verificación y Validaciones

- [ ] **Single-Instance Lock en Windows Desktop JVM**:
  - *Cómo probar:* Intentar iniciar una segunda instancia de Luna Fetch con la primera en ejecución.
  - *Resultado esperado:* La primera ventana sale al frente y la segunda se cierra inmediatamente con código 0.
- [ ] **Verificación de Auto-Actualización y Sanitización**:
  - *Cómo probar:* Pulsar "Buscar actualizaciones" en Ajustes / Acerca de estando en v1.1.3.
  - *Resultado esperado:* Se cierra la ventana modal anterior de forma síncrona y aparece un Toast flotante centrado en la parte superior ("✅ Estás en la última versión") durante 4 segundos.

---

## 🧭 Fases Técnicas de Desarrollo

### Fase 1 — Estandarización y Mantenimiento de Reglas (En Progreso)
- [x] Sincronizar instrucciones del agente (`agent.md`) con las plantillas maestras (`D:\Proyectos\biglexj\Scripts\templates`).
- [x] Crear regla de estándar de escritorio `desktop_app_standards.md` (Single-Instance Lock & In-App AutoDownloader).
- [x] Implementar bypass obligatorio en modo desarrollo (`isDev` via `-Dlunafetch.dev=true` / `idea.active`) para `SingleInstanceLock`.
- [x] Implementar persistencia y restauración obligatoria del estado de ventana (ancho, alto, posición, `isMaximized`) en `AppSettings.kt` y `Main.kt`.
- [x] Crear regla de estándar de centro de feedback `feedback_center.md` (GitHub Issues & portal web).
- [x] Migrar `TASK.md` a `TASKS.md` y estructurar `ROADMAP.md` según plantilla canónica.

### Fase 2 — Selección Fina & Control de Motor
- [ ] Implementación de pre-selección de ítems de colecciones/playlists.
- [ ] Selector de canal Stable/Nightly para `yt-dlp` en el panel de Ajustes.
