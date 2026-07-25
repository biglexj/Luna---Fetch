# 🌌 Release Notes — Luna Fetch

> [!IMPORTANT]
> **Regla del .9 para Versionado:**
> - Nunca se debe pasar de una versión de parche `.9` (ej. de `1.0.9` no se pasa a `1.0.10`). Al alcanzar el límite del parche `.9`, se incrementa el número menor/secundario (ej. pasando a `1.1.0`).
> - De igual manera, al alcanzar el límite de la versión menor `1.9.9` (o ante hitos de arquitectura significativos), se debe saltar obligatoriamente al siguiente número mayor completo (`2.0.0`).
> - **Extensión proporcional en Release Notes:** La cantidad de párrafos depende del alcance: 1 para un hito pequeño, 2 cuando hay dos cambios relevantes, 3 como extensión habitual, 4 para hitos relativamente grandes y hasta 5 para lanzamientos de gran alcance. Cada párrafo debe concentrarse en un cambio principal y evitar descripciones excesivamente largas o listas detalladas de archivos.
> - **No duplicar versiones**: Si una versión ya está registrada localmente pero aún no se ha subido a GitHub (no se ha hecho push), no crees una nueva versión de parche. Simplemente añade los nuevos cambios dentro de la misma versión activa.

Registro histórico de cambios y versiones del proyecto.

## [1.0.6] — TikTok sin Marca de Agua, Material Expressive & Auto-Updater — 2026-07-25

Luna Fetch 1.0.6 introduce compatibilidad completa para la descarga de videos de TikTok sin marca de agua, desinfección de enlaces con parámetros de seguimiento e inclusión de cabeceras HTTP de navegador.

Se adopta el sistema de diseño Material 3 Expressive para Windows y Android con colores tonales vibrantes, botones en forma de píldora, tarjetas elevadas y menús emergentes sin tintes desalineados.

Se integra la comprobación y descarga directa de actualizaciones desde GitHub Releases, notificaciones del sistema de progreso en Android y la sección oficial de "Acerca de" con accesos a donaciones (Yape, Plin y Buy Me a Coffee).

## [1.0.5] — Refactorización y Estandarización Modular — 2026-07-24

Estandarización de la estructura del repositorio bajo las reglas del agente y refactorización modular de la interfaz de usuario en Compose. Se eliminó la deuda técnica de `LunaFetchApp.kt` dividiéndolo en 9 sub-componentes atomizados, mejorando la mantenibilidad y organización del proyecto.

## [1.0.4] — Extensión Web e Historial Unificado — 2026-07-24

Integración de la extensión oficial para navegadores Chromium (Chrome / Edge) con botón directo de descarga y sincronización silenciosa en segundo plano. Se unifica el historial de descargas entre cliente de escritorio y móvil, optimizando además las miniaturas a relación de aspecto 16:9 y mejorando la resiliencia contra verificaciones anti-bot.

## [1.0.3] — Audio con contexto — 2026-07-18

MP3 y M4A conservan ahora todos los metadatos y portada que entregue la fuente, sin asumir que cada audio es una canción. Las playlists y álbumes se detectan como colecciones, pueden descargarse completas y numeran sus pistas; cuando existen, su título e índice se incorporan como álbum y pista.

## [1.0.2] — APK por arquitectura — 2026-07-18

Android se distribuye ahora en APK firmados y separados para ARM64, ARM32 y x86_64: se eliminan el APK universal, x86 y AAB para reducir drásticamente las descargas. El selector de tema se simplifica a un único icono que rota entre Sistema, Claro y Oscuro.

## [1.0.1] — Migración Kotlin Multiplatform — 2026-07-16

Luna YT-DLP Downloader adopta el nombre **Luna Fetch** y migra de WPF/.NET a Kotlin Multiplatform con una interfaz Compose compartida para Windows, Linux y Android. La versión conserva análisis, formatos, calidades, miniaturas, progreso y logs, añade cancelación real y permite abrir el archivo descargado pulsando su tarjeta.

Android incorpora Material 3, color dinámico, almacenamiento mediante el selector del sistema y un motor local con Python, `yt-dlp` y FFmpeg. La distribución de escritorio adopta una cadena reproducible para EXE, MSI, DEB/RPM, firma y hashes.

## [1.0.0] — Lollipop — 2026-07-14

Primera versión WPF para Windows con análisis y descarga mediante `yt-dlp`, conversión con FFmpeg, selección de formato/calidad, tema claro/oscuro, progreso y consola técnica.
