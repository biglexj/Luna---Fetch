# Tareas: Compatibilidad Universal y Formatos Seal

- [x] **Tarea 1**: Actualizar `DownloadModels.kt` (MediaFormat con FLAC y WAV, selector universal de video con soporte de streams pre-muxed).
- [x] **Tarea 2**: Actualizar `LinkCard.kt` para remover la lista blanca cerrada de dominios y permitir detección universal del portapapeles.
- [x] **Tarea 3**: Actualizar `AndroidDownloadEngine.kt` para condicionar banderas exclusivas de YouTube (`--remote-components`) y mejorar resiliencia SSL/extractor.
- [x] **Tarea 4**: Verificar o ajustar `YtdlpProtocol.kt` y `DesktopDownloadEngine.kt` para el flujo universal (`--no-check-certificates`).
- [x] **Tarea 5**: Ejecutar pruebas unitarias en `composeApp` (`./gradlew test`) y validar compilación en Android y Desktop.
- [x] **Tarea 6**: Diagnosticar causa raíz de HTTP 403 Forbidden en Android (Python urllib carece de `curl_cffi` para impersonation y Pornhub valida el hash del User-Agent `ua` junto con cookies de age gate).
- [x] **Tarea 7**: Implementar `NetscapeCookieJar` universal y `AndroidCookieJar` con pre-sembrado determinista de clearance cookies (`platform=pc`, `age_verified=1`, `accessAgeDisclaimerPH=1`, `ua=7675d59b5e84e0a878ee6f0a97f9056f`), resolución automática en segundo plano mediante headless WebView (`CookieManager`), e integración en `AndroidDownloadEngine` y `DesktopDownloadEngine`.
