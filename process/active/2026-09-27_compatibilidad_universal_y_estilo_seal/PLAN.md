# Plan: Compatibilidad Universal (Páginas +18, Streaming y Soporte Extendido) y Mejoras de Formatos estilo Seal

## 1. Contexto y Objetivos
- **Problema**: Luna Fetch fallaba o no admitía páginas para adultos (+18) y ciertos sitios de streaming debido a filtros restrictivos en el portapapeles (`isYouTubeOrMediaUrl`), selectores de formato rígidos (`bestvideo+bestaudio`) que rompen en streams pre-muxed (donde no hay audio separado), y parámetros específicos de YouTube inyectados universalmente en Android.
- **Objetivo**:
  1. Universalizar la detección de URLs en portapapeles y compartidos (sin listas blancas arbitrarias).
  2. Implementar un selector de formatos resiliente que soporte tanto streams separados (YouTube DASH) como streams pre-muxed (Pornhub, XVideos, SpankBang, etc.).
  3. Inyectar banderas de YouTube (`--remote-components`) solo para YouTube, y añadir banderas de resiliencia (`--no-check-certificates`).
  4. Agregar formatos de audio sin pérdidas (**FLAC** y **WAV**) inspirados en Superflac y Seal.
  5. Mejorar la visualización de la plataforma y el enlace en el historial (estilo Seal).

## 2. Cambios Arquitectónicos
- `composeApp/.../domain/DownloadModels.kt`:
  - Agregar `Flac` y `Wav` a `MediaFormat`.
  - Refactorizar `FormatCatalog.videoSelector` con patrón universal `bestvideo*+bestaudio/best` con fallbacks robustos.
- `composeApp/.../feature/download/LinkCard.kt`:
  - Reemplazar la lista blanca rígida de 10 dominios por validación universal HTTP/HTTPS (`LunaFetchPresenter.isSupportedUrl`).
- `composeApp/.../platform/AndroidDownloadEngine.kt`:
  - Condicionar `--remote-components` y `--js-runtimes` solo para YouTube.
  - Asegurar flags de resiliencia SSL para extractores de terceros.
- `composeApp/.../platform/DesktopDownloadEngine.kt` y `YtdlpProtocol.kt`:
  - Compatibilidad de post-procesamiento para FLAC/WAV.
  - Verificación de análisis y descarga.
- Tests unitarios: Actualizar y agregar pruebas en `FormatCatalogTest` y `YtdlpProtocolTest`.
