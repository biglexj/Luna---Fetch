# Roadmap — Luna Fetch

## Versión 1.0.5 (Próximo Release)

- **Filtros e Historial persistente avanzado**: Búsqueda por nombre, ordenación y persistencia en almacenamiento local para el historial de descargas (Escritorio y Teléfono/Android).
- **Selección fina de colecciones**: Permite seleccionar elementos específicos individuales dentro de una playlist de YouTube antes de descargar.
- **Gestión de yt-dlp**: Administración y actualización guiada de `yt-dlp` y FFmpeg desde el menú de Ajustes en Escritorio.

## Urgente / Importante

- Verificar descargas reales en un dispositivo Android físico para cada ABI publicada.
- Completar pruebas de paquetes DEB/RPM en un host Linux.
- Resolver y documentar el modelo definitivo de licencia para la distribución Android GPL-3.0.

## Completado

### Versión 1.0.4
- **Extensión de Navegador Chrome / Edge**: Botón nativo `⬇ Luna` integrado en YouTube con menú desplegable para elegir formato (MP4 / MP3) y calidades analizadas en tiempo real.
- **Descargas Silenciosas y Sincronización**: Descarga directa en segundo plano desde el navegador sin abrir popups ni robar foco a la ventana principal, con sincronización de estado e historial en tiempo real entre la extensión y la app.
- **Historial Unificado (Escritorio y Teléfono / Android)**: Registro automático de todas las descargas realizadas tanto en PC como en el teléfono, con botón `🌐 Web` para abrir el video original en el navegador o app de YouTube.
- **Comportamiento Intuitivo de Playlists / Mixes**: Para URLs con `v=` y `list=` (Mixes/Radio), descarga por defecto la canción individual sin saturar el sistema con los 700+ elementos a menos que el usuario lo active.
- **Botón e Historial de Descargas**: Acceso directo al historial desde el header principal (reloj 🕒) con opciones para abrir archivos/carpetas, ir al video en la web y limpiar registros.
- **Miniaturas 16:9 y Bucle Anti-Bot**: Miniaturas recortadas dinámicamente en 16:9 y soporte de cookies de sesión para evitar bloqueos por bot verification en YouTube.

### Versión 1.0.0 – 1.0.3
- Migración de WPF/.NET a Kotlin Multiplatform.
- Interfaz Compose responsive con Material 3 y radios controlados.
- Targets Windows, Linux y Android.
- Motor Android local, almacenamiento SAF y servicio en primer plano.
- Empaquetado EXE, MSI, DEB/RPM y APK ARM64/ARM32/x86_64 con hashes SHA-256; MSIX descartado.
- División de APK Android por ABI; APK universal, x86 y AAB descartados.
- Metadatos, portada y descarga de colecciones de audio.
