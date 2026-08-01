# 🎯 Luna Fetch — Roadmap

Plan de trabajo, objetivos y prioridades del proyecto.

## 🔴 Urgente / Normal (Pendientes Activos)

- [ ] **Selección fina de colecciones**: Permite seleccionar elementos específicos individuales dentro de una playlist de YouTube o TikTok antes de descargar.
- [ ] **Gestión de yt-dlp y FFmpeg**: Administración y actualización guiada de `yt-dlp` y FFmpeg desde el menú de Ajustes en Escritorio y Android.
- [ ] **Cola de Descargas Simultáneas**: Soporte para descargas en paralelo con límite configurable en la interfaz de usuario.
- [ ] **Pruebas Físicas Android**: Verificar descargas reales en dispositivos Android físicos para cada ABI publicada (ARM64, ARM32, x86_64).
- [ ] **Empaquetado Linux**: Completar pruebas de distribución de paquetes DEB/RPM en un host Linux.

## 🟡 Intermedio (Prioridad Media/Baja)

- [ ] **Estrategia de Agrupación de Versiones**: Mantener publicaciones estables y evitar incrementar versiones menores de parche por correcciones diminutas individuales.

## ⚪ Descartado / En Pausa

- ⏸️ **Filtros e Historial persistente avanzado**: Búsqueda por nombre y ordenación en historial descartados por baja utilidad práctica.
- ⏸️ **Sincronización LAN**: Sincronización entre dispositivos en red local puesta en pausa por complejidad/utilidad no prioritaria.
- ⏸️ **Licenciamiento GPL 3.0 / Tiendas**: Eliminado en favor de distribución libre open-source sin requisitos de tiendas oficiales.
- ⏸️ **Empaquetado MSIX y Microsoft Store**: Descartado en favor de la distribución directa en EXE/MSI con manifiesto Winget.
- ⏸️ **APK Universal Android y AAB**: Descartado en favor de APKs optimizados y firmados por ABI.

## 🟢 Completado

- [x] **Versión 1.1.2**: Unificación de Ajustes y Acerca de en un solo modal responsivo, rediseño de engranaje mecánico, panel de estado e instalador de controladores del motor (`yt-dlp`), refactorización modular por dominio en `feature/` (`header`, `download`, `history`, `logs`, `update`, `components`), temporizador de 3s con animación para estado de la app y guía paso a paso `browser-extension/README.md`.
- [x] **Corrección del Modal "Acerca de"**: Comprobación interactiva de actualizaciones desde el diálogo "Acerca de". Si no hay versión nueva disponible, el modal permanece abierto mostrando el mensaje informativo en pantalla y solo se cierra automáticamente cuando se detecta una nueva actualización para dar paso al modal central.
- [x] **Versión 1.1.1**: Modal central de actualización al 80% de ancho en móviles, descarga resiliente HTTP 302/307, transición limpia entre modales y corrección de parpadeo de modo oscuro en Android.
- [x] **Versión 1.1.0**: Modal Central de Actualización e Instalación Nativa In-App con progreso 0-100% y visualización de notas de versión en Compose.
- [x] **Versión 1.0.9**: Estabilidad en TikTok (canal NIGHTLY auto-reintento), historial de descargas persistente con refresco en tiempo real, rediseño de modal de historial al 88% de ancho en móviles, tema del sistema en tiempo real en Windows vía JNA y consola técnica continua.
- [x] **Migración a Material Expressive**: Actualización del sistema de diseño a Material 3 Expressive (nuevas micro-interacciones, animaciones fluidas, tipografía y componentes expresivos).
- [x] **Versión 1.0.8**: Optimización de la interfaz en dispositivos móviles (ancho del 92% para modales), rediseño del diálogo 'Acerca de' alineado al sistema visual de Lienzo Gallery con botones de píldora a ancho completo y botón directo 'Buscar actualizaciones'.
- [x] **Versión 1.0.7**: Auto-actualización nativa para Windows con descarga e instalación directa del ejecutable, sanitización estricta de Release Notes en plantillas y publicaciones oficiales.
- [x] **Versión 1.0.6**: Compatibilidad con TikTok sin marca de agua y desinfección de URL, auto-actualización desde GitHub Releases (`UpdateChecker`), notificaciones de sistema en Android al completar/fallar descargas, adopción completa de **Material 3 Expressive System** (Windows & Android) y modal de donaciones oficiales (`biglexj.com/donaciones`).
- [x] **Versión 1.0.5**: Refactorización modular de UI (`LunaFetchApp.kt` de 1,108 a 98 líneas), eliminación de deuda técnica activa, creación de 9 sub-componentes UI en `feature/components/`, estandarización de `.agents/rules/folder_structure.md` y estructura del repositorio.
- [x] **Versión 1.0.4**: Extensión de navegador Chrome / Edge, descargas silenciosas, historial unificado y miniaturas 16:9.
- [x] **Versiones 1.0.0 – 1.0.3**: Migración de WPF/.NET a Kotlin Multiplatform, interfaz Compose Material 3, soporte Windows/Linux/Android, motor local SAF, empaquetado EXE/MSI/DEB/RPM/APK.
