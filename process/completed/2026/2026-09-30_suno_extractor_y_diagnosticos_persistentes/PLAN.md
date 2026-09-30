# Plan: Suno Extractor Nativo, Diagnósticos Persistentes (48h) y Resiliencia TikTok

## 1. Contexto y Objetivos
- **Problema 1 (Suno AI)**: `yt-dlp` carece de extractor nativo para Suno (`suno.com`), descargando archivos vacíos o fallando por completo. Es necesario dotar a Luna Fetch de un motor extractor nativo para Suno que obtenga audio (MP3/M4A/WAV), video (MP4), letras y portadas con etiquetas ID3 completas.
- **Problema 2 (Pérdida de Diagnósticos y Errores)**: Actualmente, cuando una descarga o análisis falla, el error solo vive en memoria volátil de `state.logs`/`state.error`. Si la app se reinicia o se inicia otra descarga, el diagnóstico desaparece. El usuario necesita que los registros de errores y diagnósticos técnicos se conserven como mínimo **48 horas** de forma persistente para poder inspeccionarlos y reportarlos sin perderlos.
- **Problema 3 (Falla de Extracción en TikTok)**: TikTok está activando mitigaciones anti-scraping estrictas en las últimas versiones de `yt-dlp` (`The extractor is attempting impersonation, but no impersonate target is available. Video not available, status code 0`). El mecanismo de fallback de cookies de Luna Fetch no detectaba este mensaje de error específico para activar las cookies de navegador o estrategias alternativas de extracción.

## 2. Solución Arquitectónica

### A. Diagnósticos Técnicos Persistentes (Ventana de 48 Horas)
1. **Modelo de Diagnóstico (`DiagnosticLogEntry`)**:
   - `id`: UUID único.
   - `timestampMs`: Momento exacto del fallo.
   - `url`: Enlace que falló.
   - `operation`: "ANALYZE" o "DOWNLOAD".
   - `errorMessage`: Mensaje amigable para el usuario.
   - `technicalDetails`: Volcado completo de stderr/stdout o stacktrace.
   - `platform`: Sistema operativo y versión de la app.
2. **Almacén de Diagnósticos (`DiagnosticStore`)**:
   - Persiste en almacenamiento local (`.lunafetch/diagnostics.json` en Desktop y `diagnostics.json` en Android).
   - Auto-purgado inteligente: al guardar o cargar, purga automáticamente todo registro con antigüedad superior a 48 horas (`timestampMs < now - 48.hours`).
3. **Visualización en UI (`LogsCard.kt`)**:
   - Mostrar no solo los logs volátiles de la sesión actual, sino un selector / historial de diagnósticos recientes de las últimas 48 horas con botón directo "Copiar diagnóstico completo".

### B. Módulo de Extracción Nativa `SunoExtractor`
1. **Detección y Normalización de URLs**:
   - Reconocer URLs de `suno.com/song/...`, `suno.com/s/...`, `suno.com/hook/...`, `usesuno.com/...`.
   - Extracción del UUID canónico.
2. **Análisis de Metadatos (`analyzeSuno`)**:
   - Consulta a la API pública / CDN de Suno para obtener: Título, Autor (@handle), Duración, Tags/Estilo, Letra lírica (prompt), Portada JPEG y existencia de Video MP4 (`cdn1.suno.ai/{id}.mp4`).
   - Retorna un `VideoInfo` completo para que la UI de Luna Fetch lo renderice como cualquier otra canción/video.
3. **Descarga y Empaquetado (`downloadSuno`)**:
   - Descarga directa con seguimiento de progreso (0% a 100%).
   - Si se selecciona formato de video (MP4): descarga directa del videoclip animado de Suno.
   - Si se selecciona formato de audio (MP3/M4A/WAV/FLAC): extracción/descarga con incrustación de metadatos (Título, Artista, Álbum "Suno AI", Carátula oficial).
   - Exportación de letras líricas (`.txt` opcional o incrustadas en ID3).

### C. Resiliencia de TikTok en `DesktopDownloadEngine`
1. Extender `executeWithCookieFallback` para capturar errores de `impersonation`, `status code 0`, `Video not available` y `TikTok` y activar fallback inmediato de cookies desde navegadores (Chrome, Edge, Firefox, Brave, Opera) y argumentos de extractor (`--extractor-args "tiktok:api_hostname=..."`).

## 3. Plan de Verificación
- Pruebas unitarias en `commonTest`: `SunoExtractorTest`, `DiagnosticStoreTest`.
- Validación de persistencia de 48 horas (creación, lectura, auto-purga de registros viejos).
- Compilación y verificación general `./gradlew test`.
