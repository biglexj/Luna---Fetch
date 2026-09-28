# Tareas: Compatibilidad Universal y Formatos Seal

- [x] **Tarea 1**: Actualizar `DownloadModels.kt` (MediaFormat con FLAC y WAV, selector universal de video con soporte de streams pre-muxed).
- [x] **Tarea 2**: Actualizar `LinkCard.kt` para remover la lista blanca cerrada de dominios y permitir detección universal del portapapeles.
- [x] **Tarea 3**: Actualizar `AndroidDownloadEngine.kt` para condicionar banderas exclusivas de YouTube (`--remote-components`) y mejorar resiliencia SSL/extractor.
- [x] **Tarea 4**: Verificar o ajustar `YtdlpProtocol.kt` y `DesktopDownloadEngine.kt` para el flujo universal (`--no-check-certificates`).
- [x] **Tarea 5**: Ejecutar pruebas unitarias en `composeApp` (`./gradlew test`) y validar compilación en Android y Desktop.
- [x] **Tarea 6**: Diagnosticar causa raíz de HTTP 403 Forbidden en Android (Python urllib carece de `curl_cffi` para impersonation y Pornhub valida el hash del User-Agent `ua` junto con cookies de age gate).
- [x] **Tarea 7**: Implementar `NetscapeCookieJar` universal y `AndroidCookieJar` con clearance cookies de age gate (`platform=pc`, `age_verified=1`, `accessAgeDisclaimerPH=1`), resolución automática en segundo plano mediante headless WebView (`CookieManager`), e integración en `AndroidDownloadEngine` y `DesktopDownloadEngine`.
- [x] **Tarea 8**: Reverse engineering de Seal (`JunkFood02/Seal`): añadir `android:extractNativeLibs="true"` en `AndroidManifest.xml`, eliminar hash `ua` nocivo que causaba HTTP 403, adoptar arquitectura de requests limpios y sin sobreescritura de cabeceras de extractores nativos en `AndroidDownloadEngine.kt` y `YtdlpProtocol.kt`. Verificación completa con `./gradlew test` y `./gradlew assembleDebug`.
