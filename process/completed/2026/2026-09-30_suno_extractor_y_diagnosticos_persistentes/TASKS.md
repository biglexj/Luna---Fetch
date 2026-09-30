# Tareas: Suno Extractor, Diagnósticos Persistentes (48h) y Resiliencia TikTok

- [x] **Tarea 1**: Implementar `DiagnosticStore` y modelo `DiagnosticEntry` con ventana de retención estricta de 48 horas en `domain/` y enlace en `PlatformBindings`.
- [x] **Tarea 2**: Integrar guardado automático de errores en `DiagnosticStore` desde `LunaFetchPresenter.kt` al fallar cualquier análisis o descarga.
- [x] **Tarea 3**: Actualizar `LogsCard.kt` para mostrar el historial de diagnósticos recientes de las últimas 48 horas con botón para copiar al portapapeles.
- [x] **Tarea 4**: Implementar `SunoExtractor.kt` en `domain/` (normalización de URLs de Suno, análisis de metadatos, letras, carátulas y descarga directa HTTP con reporte de progreso y metadatos ID3).
- [x] **Tarea 5**: Cablear `SunoExtractor` en `DesktopDownloadEngine.kt` y `AndroidDownloadEngine.kt` para desviar enlaces de Suno de forma transparente sin depender de `yt-dlp`.
- [x] **Tarea 6**: Mejorar la resiliencia de TikTok en `DesktopDownloadEngine.kt` para detectar errores de `impersonation` y activar cookies de navegador y fallback de hostname.
- [x] **Tarea 7**: Crear pruebas unitarias para `DiagnosticStoreTest` y `SunoExtractorTest`.
- [x] **Tarea 8**: Ejecutar `./gradlew test` y validar compilación limpia.
