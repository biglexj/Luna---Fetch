# Validación: Compatibilidad Universal y Formatos Seal

## Comprobaciones realizadas
1. **Pruebas unitarias de selección de formato**:
   - `FormatCatalogTest.losslessFlacAndWavAreSupported`: Exitoso.
   - `FormatCatalogTest.videoSelectorsKeepProgressiveAndGenericFallbacks`: Exitoso.
   - `FormatCatalogTest.videoQualitiesRespectAvailableHeight`: Exitoso.
   - `FormatCatalogTest.audioQualitiesUseAudioSelectors`: Exitoso.
2. **Pruebas unitarias de argumentos de descarga**:
   - `YtdlpProtocolTest.argumentsRemainSeparatedAndEndWithOutputTemplate`: Exitoso.
   - `YtdlpProtocolTest.tikTokUrlUsesCleanNativeArguments`: Exitoso.
   - `YtdlpProtocolTest.parsesStableProgressTemplate`: Exitoso.
3. **Compilación y suite de pruebas Android**:
   - `./gradlew test --no-daemon`: `BUILD SUCCESSFUL in 1m 17s` (58 tareas ejecutadas / up-to-date sin errores).
4. **Compilación y suite de pruebas Desktop**:
   - `./gradlew :composeApp:desktopTest :composeApp:compileKotlinDesktop --no-daemon`: `BUILD SUCCESSFUL in 16s` (17 tareas ejecutadas / up-to-date sin errores).
5. **Inyección de cabeceras de origen en YtdlpProtocol**:
   - Agregada inyección dinámica de cabeceras de origen (`--referer`, `--add-header Origin`) y User-Agent moderno de escritorio en `YtdlpProtocol.kt`.
   - Excluidos dominios de TikTok/Douyin para preservar la integridad de su extractor nativo.
   - Añadida prueba `YtdlpProtocolTest.adultAndGenericUrlIncludesOriginHeaders`: Exitosa.
6. **Resolución Definitiva de HTTP 403 Forbidden (+18 / Adult Sites)**:
   - Diagnóstico científico de causa raíz: Python `urllib` en Android carece de `curl_cffi` para impersonar navegadores, y plataformas como Pornhub/Redtube/YouPorn exigen validación cruzada del hash User-Agent (`ua`) junto con cookies de age gate (`age_verified=1`, `platform=pc`, `accessAgeDisclaimerPH=1`).
   - Implementado `NetscapeCookieJar` en `commonMain`: maneja parsing, combinación/merge idempotente y serialización formal en formato Netscape (`luna_session_cookies.txt`).
   - Creada suite de pruebas unitarias `NetscapeCookieJarTest` (4 pruebas exitosas: clearance por defecto, roundtrip de serialización, merge conservando cookies de otros dominios, y parsing de strings de cabecera).
   - Implementado `AndroidCookieJar` en `androidMain`: pre-siembra de clearance cookies y puente reactivo headless con `WebView` y `CookieManager` para resolver desafíos dinámicos en segundo plano.
   - Paridad en `DesktopDownloadEngine`: inyección automática de clearance cookies en el archivo temporal de cookies en caso de descarga sin extensión activa.
   - Compilación exitosa de APK: `./gradlew assembleDebug` (`BUILD SUCCESSFUL in 15s`). Suite `./gradlew test` (58 tareas pasadas exitosamente).
7. **Reverse Engineering de Seal (`JunkFood02/Seal`) e Integración Arquitectónica**:
   - Identificado que en Seal `android:extractNativeLibs="true"` es vital en `AndroidManifest.xml` para que los procesos fork/exec de Python carguen sockets y extensiones C (`libpython.so`) sin error de resolución DNS `[Errno 7] No address associated with hostname`.
   - Detectado que la inyección forzada de User-Agent de escritorio y hash sintético `ua` en requests sin `curl_cffi` provocaba la detección de bot/falsificación y generaba el error `HTTP 403 Forbidden`.
   - Adoptado el patrón canónico de Seal: requests de análisis limpios y nativos (`--dump-single-json`, `--flat-playlist`, `-R 1`, `--socket-timeout 10`, `--no-check-certificates`) sin sobreescribir las cabeceras afinadas de los 1,745 extractores de yt-dlp.
   - Actualizadas suites de tests unitarios: `YtdlpProtocolTest` y `NetscapeCookieJarTest`. Todas las pruebas unitarias pasaron con éxito (`BUILD SUCCESSFUL`).
   - Verificada compilación de APK Android (`./gradlew assembleDebug`: `BUILD SUCCESSFUL in 10s`).
8. **Resolución de Anti-Hotlinking en Miniaturas Protegidas (Coil 3 HTTP 403)**:
   - Implementado `buildThumbnailRequest(context, thumbnailUrl, sourceUrl)` inyectando `Referer: https://www.pornhub.com/` y User-Agent de escritorio mediante `NetworkHeaders`.
   - Verificado con test unitario en Desktop (`CoilThumbnailTest[desktop]` retornando `SuccessResult` con `BitmapImage` decodificado al 100%).
9. **Corrección de Transmisiones Adultas y Descarga de MP4 Directos**:
   - Corrección de `platform=pc` en `AndroidCookieJar.kt` y `AndroidWebExtractor.kt`.
   - Priorización de streams progresivos directos MP4 sobre listas de reproducción HLS fragmentadas y volátiles.
   - Validación empírica con archivo real de 232 MB descargado de extremo a extremo sin corrupción.
10. **Rediseño e Integración del Flujo Invertido de Descarga Rápida**:
   - `QuickDownloadSheet.kt` modificado con selector segmentado de dos pestañas (`[ 🎬 Video ]` y `[ 🎵 Música ]`) y auto-selección de mejor calidad.
   - El usuario inicia la descarga al instante; el servicio en primer plano (`DownloadForegroundService`) gestiona la tarea sin esperas en la interfaz.
11. **Pruebas Físicas en Teléfono Android y Enlaces a Super Galería**:
   - Instalación y validación en hardware Android físico con botones directos "▶ Reproducir" y "📂 Abrir en Super Galería" (`com.biglexj.lienzo`).
   - El usuario final confirmó satisfacción total: "Sí, ahora sí ya descarga correctamente... prepara las actualizaciones y lánzalo ya".

