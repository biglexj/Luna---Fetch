# Validación: Suno Extractor, Diagnósticos Persistentes (48h) y Resiliencia TikTok

## 1. Evidencia de Pruebas Automatizadas
- [x] **Tests de retención de 48 horas en `DiagnosticStoreTest`**:
  - `retentionWindowRespects48HoursCutoff`: Validado que solo se conservan entradas dentro de las 48 horas (`RETENTION_MS = 172_800_000L`).
  - `addEntryInsertsAndPurgesAutomatically`: Validada la purga transparente al agregar nuevos errores.
  - `formattingGeneratesStructuredReport`: Validado el formato estructurado con metadatos para reporte técnico.
- [x] **Tests de `SunoExtractorTest`**:
  - `detectsSunoUrls`: Validada la detección de `suno.com`, `suno.ai`, `usesuno.com` y descarte de dominios externos.
  - `extractUuidFromUrls`: Validada la extracción exacta de UUIDs canónicos.
- [x] **Ejecución de suite completa Gradle**:
  - `./gradlew test`: **BUILD SUCCESSFUL in 1m 37s** (58 actionable tasks: 11 executed, 47 up-to-date). Pruebas en `:composeApp:testDebugUnitTest`, `:composeApp:testReleaseUnitTest` y `:composeApp:test` ejecutadas al 100% exitosamente.

## 2. Evidencia de UI y Funcionalidad
- [x] **Visualización en `LogsCard.kt`**:
  - Sección persistente de diagnósticos técnicos de las últimas 48 horas.
  - Botón de "📋 Copiar reporte 48h" para exportar con un solo clic todos los incidentes ocurridos en los últimos dos días.
  - Botón de "Limpiar" para purga voluntaria.
- [x] **Resiliencia de TikTok en `DesktopDownloadEngine.kt`**:
  - Detección de errores de `impersonation` y `status code 0`.
  - Fallback automático mediante inyección de `--extractor-args "tiktok:api_hostname=..."` y extracción de cookies de navegadores (Chrome, Edge, Firefox, Brave, Opera).
- [x] **Suno Extractor Multiplataforma**:
  - Integrado de forma no invasiva en `DesktopDownloadEngine` y `AndroidDownloadEngine`.
  - Descarga de Video MP4 y extracción de Audio (MP3/M4A/WAV/FLAC) con metadatos ID3 y archivo de letras líricas `.lyrics.txt`.
