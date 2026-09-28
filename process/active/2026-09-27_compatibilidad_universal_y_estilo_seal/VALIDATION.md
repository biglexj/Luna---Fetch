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

