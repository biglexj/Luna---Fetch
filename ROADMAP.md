# 🎯 Luna Fetch — Roadmap

Plan de trabajo, objetivos y prioridades del proyecto.

## 🔴 Urgente / Normal (Pendientes Activos)

- [ ] Correcion en el "acerca de" si doy click en buscara actuañlizaciones pero no hay ninguna debe solo salir un mensaje que diga que no hay actualizaciones y no cerrar automaticamente el modal de acerca de. Para eso debe comprobrar si realmente hay actualizaciones y si es asi ahi si puede cerrarse el modal de acerca de.

- [ ] **Filtros e Historial persistente avanzado**: Búsqueda por nombre, ordenación y persistencia en almacenamiento local para el historial de descargas (Escritorio y Teléfono/Android).
- [ ] **Selección fina de colecciones**: Permite seleccionar elementos específicos individuales dentro de una playlist de YouTube antes de descargar.
- [ ] **Gestión de yt-dlp**: Administración y actualización guiada de `yt-dlp` y FFmpeg desde el menú de Ajustes en Escritorio.
- [ ] **Pruebas Físicas Android**: Verificar descargas reales en un dispositivo Android físico para cada ABI publicada (ARM64, ARM32, x86_64).
- [ ] **Empaquetado Linux**: Completar pruebas de paquetes DEB/RPM en un host Linux.
- [ ] **Licenciamiento Android**: Resolver y documentar el modelo definitivo de licencia para la distribución Android GPL-3.0.

## 🟡 Intermedio (Prioridad Media/Baja)

- [ ] **Cola de Descargas Simultáneas**: Soporte para descargas en paralelo con límite configurable.
- [ ] **Sincronización LAN**: Sincronización rápida de descargas entre Escritorio y Android en la red local.

## ⚪ Descartado / En Pausa

- ⏸️ **Empaquetado MSIX y Microsoft Store**: Descartado en favor de la distribución directa en EXE/MSI con manifiesto Winget.
- ⏸️ **APK Universal Android y AAB**: Descartado en favor de APKs optimizados y firmados por ABI.

## 🟢 Completado

- [x] **Versión 1.0.9**: Estabilidad en TikTok (canal NIGHTLY auto-reintento), historial de descargas persistente con refresco en tiempo real, rediseño de modal de historial al 88% de ancho en móviles, tema del sistema en tiempo real en Windows vía JNA y consola técnica continua.
- [x] **Migración a Material Expressive**: Actualización del sistema de diseño a Material 3 Expressive (nuevas micro-interacciones, animaciones fluidas, tipografía y componentes expresivos).
- [x] **Versión 1.0.8**: Optimización de la interfaz en dispositivos móviles (ancho del 92% para modales), rediseño del diálogo 'Acerca de' alineado al sistema visual de Lienzo Gallery con botones de píldora a ancho completo y botón directo 'Buscar actualizaciones'.
- [x] **Versión 1.0.7**: Auto-actualización nativa para Windows con descarga e instalación directa del ejecutable, sanitización estricta de Release Notes en plantillas y publicaciones oficiales.
- [x] **Versión 1.0.6**: Compatibilidad con TikTok sin marca de agua y desinfección de URL, auto-actualización desde GitHub Releases (`UpdateChecker`), notificaciones de sistema en Android al completar/fallar descargas, adopción completa de **Material 3 Expressive System** (Windows & Android) y modal de donaciones oficiales (`biglexj.com/donaciones`).
- [x] **Versión 1.0.5**: Refactorización modular de UI (`LunaFetchApp.kt` de 1,108 a 98 líneas), eliminación de deuda técnica activa, creación de 9 sub-componentes UI en `feature/components/`, estandarización de `.agents/rules/folder_structure.md` y estructura del repositorio.
- [x] **Versión 1.0.4**: Extensión de navegador Chrome / Edge, descargas silenciosas, historial unificado y miniaturas 16:9.
- [x] **Versiones 1.0.0 – 1.0.3**: Migración de WPF/.NET a Kotlin Multiplatform, interfaz Compose Material 3, soporte Windows/Linux/Android, motor local SAF, empaquetado EXE/MSI/DEB/RPM/APK.
