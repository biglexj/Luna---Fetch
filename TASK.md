# 📋 Task List — Luna Fetch

Lista de tareas activas para seguimiento continuo del desarrollo.

## 🔴 Pendientes Activos

- [ ] **Selección Fina de Colecciones**: Interfaz para pre-seleccionar ítems individuales dentro de playlists de YouTube/TikTok antes de descargar.
- [ ] **Gestión de yt-dlp y FFmpeg**: Panel visual de actualización y control de canal (Stable/Nightly) en Ajustes.
- [ ] **Cola de Descargas Simultáneas**: Gestión de descargas en paralelo con límite configurable.
- [ ] **Pruebas Físicas Android**: Validación de descargas reales en dispositivos Android físicos por ABI (ARM64, ARM32, x86_64).
- [ ] **Empaquetado Linux**: Pruebas de instalación y ejecución en paquetes DEB/RPM.

## 🟢 Completados Recientemente

- [x] **Versión 1.1.3 — Lanzador Nativo en Windows**: Corrección del invocador del instalador `.exe`/`.msi` en PC con `ProcessBuilder` nativo.
- [x] **Versión 1.1.2 — Unificación de Ajustes y Refactorización por Dominio**: Modal de Ajustes unificado con controladores del motor (`yt-dlp`), rediseño de engranaje mecánico, temporizador de 3s para comprobador de versión, guía `browser-extension/README.md` y estructura modular por dominio en `feature/`.
- [x] **Corrección del Modal "Acerca de"**: Comprobación interactiva de actualizaciones sin cerrar el modal prematuramente si no hay versiones nuevas disponibles.
- [x] **Modal Central de Actualizaciones (1.1.1)**: Transición fluida entre modales, progreso 0-100% y auto-instalador.
