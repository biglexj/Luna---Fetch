# 🎯 Luna Fetch — Roadmap

Plan de trabajo, objetivos de producto y hoja de ruta estratégica del proyecto.

> **Regla del roadmap:** El Roadmap reúne la visión general del producto y las ideas por estado. Las tareas activas de compilación, pruebas y fases técnicas se gestionan en [TASKS.md](file:///d:/Proyectos/biglexj/Luna---Fetch/TASKS.md). Una vez validada y completada una tarea en TASKS.md, pasa a ROADMAP.md dentro de la sección **Completado** (`- [x] **vX.X.X**`).

---

## 🔴 Pendientes activos

- [ ] **Selección Fina de Colecciones**: Interfaz para seleccionar elementos específicos individuales dentro de una playlist de YouTube o TikTok antes de descargar.
- [ ] **Gestión de yt-dlp y FFmpeg**: Panel visual de actualización y control de canal (Stable/Nightly) en Ajustes.
- [ ] **Cola de Descargas Simultáneas**: Gestión de descargas en paralelo con límite configurable en la interfaz.
- [ ] **Pruebas Físicas Android**: Validación de descargas reales en dispositivos Android físicos por ABI (ARM64, ARM32, x86_64).
- [ ] **Empaquetado Linux**: Pruebas de instalación y ejecución en paquetes DEB/RPM.

---

## 🟡 Intermedio (Prioridad Media/Baja)

- [ ] **Estrategia de Agrupación de Versiones**: Mantener publicaciones estables y evitar incrementar versiones menores de parche por correcciones diminutas individuales.

---

## ⚪ Descartado / En Pausa

- ⏸️ **Filtros e Historial persistente avanzado**: Búsqueda por nombre y ordenación en historial descartados por baja utilidad práctica.
- ⏸️ **Sincronización LAN**: Sincronización entre dispositivos en red local puesta en pausa por complejidad/utilidad no prioritaria.
- ⏸️ **Licenciamiento GPL 3.0 / Tiendas**: Eliminado en favor de distribución libre open-source sin requisitos de tiendas oficiales.
- ⏸️ **Empaquetado MSIX y Microsoft Store**: Descartado en favor de la distribución directa en EXE/MSI con manifiesto Winget.
- ⏸️ **APK Universal Android y AAB**: Descartado en favor de APKs optimizados y firmados por ABI.

---

## 🟢 Completado

- [x] **v1.1.6**
  - Flujo de actualización in-app de fricción cero: liberación de socket lock (`SingleInstanceLock`), ejecución pasiva de instalador y auto-reinicio automático de Luna Fetch.
- [x] **v1.1.5**
  - Centralización dinámica de versión mediante `AppConfig.APP_VERSION`, corrección del bug de string hardcodeado "1.1.3" en la modal de Ajustes y en el comprobador de actualizaciones, y automatización síncrona en `build-release.ps1`.
- [x] **v1.1.4**
  - Adopción de estándares de escritorio (`desktop_app_standards.md`), Garantía de Instancia Única (Single-Instance Lock) con bypass `isDev`, memoria persistente del estado de ventana (ancho, alto, posición, `isMaximized`), Toast de 4s y centro de feedback.

- [x] **v1.1.3**
  - Corrección del lanzador del instalador en PC (`ProcessBuilder`), unificación de Ajustes y Acerca de responsivo al 80% en móvil, refactorización por dominio y guía `browser-extension/README.md`.
- [x] **v1.1.2**
  - Unificación del modal de Ajustes/Acerca de, engranaje mecánico de 6 dientes, temporizador de 3s para comprobador de versiones y extensión web para Chromium.
- [x] **v1.1.1**
  - Modal central de actualización al 80% de ancho en móvil, descarga HTTP resiliente 302/307, transición limpia entre modales y corrección de tema oscuro en Android.
- [x] **v1.1.0**
  - Modal Central de Actualización e Instalación Nativa In-App con progreso 0-100% y visualización de notas de versión en Compose.
- [x] **v1.0.9**
  - Estabilidad en TikTok (canal NIGHTLY auto-reintento), historial de descargas persistente en tiempo real, modal de historial al 88% en móvil y tema del sistema en tiempo real en Windows vía JNA.
- [x] **v1.0.8**
  - Diseño híbrido del panel "Acerca de Luna Fetch" para PC y móvil con píldoras de donación y enlace directo a comprobación de actualizaciones.
- [x] **v1.0.7**
  - Auto-actualización nativa para Windows con descarga ejecutable directa y reglas estrictas de sanitización de notas de lanzamiento.
- [x] **v1.0.6**
  - Descargas de TikTok sin marca de agua, desinfección de URL, adopción del sistema de diseño Material 3 Expressive y auto-actualizador desde GitHub Releases.
- [x] **v1.0.5**
  - Refactorización modular de UI (`LunaFetchApp.kt` dividida en 9 sub-componentes atomizados), eliminación de deuda técnica activa y estandarización de estructura.
- [x] **v1.0.4**
  - Extensión de navegador Chrome/Edge con descarga silenciosa, historial unificado y miniaturas 16:9.
- [x] **v1.0.0 – v1.0.3**
  - Migración de WPF/.NET a Kotlin Multiplatform, interfaz Compose Material 3, soporte Windows/Linux/Android, motor local SAF y empaquetado multisistema.
