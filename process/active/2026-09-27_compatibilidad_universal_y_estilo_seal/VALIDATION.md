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
5. **Resolución de HTTP 403 Forbidden y Protección Cloudflare/PornHub**:
   - Agregada inyección dinámica de cabeceras de origen (`--referer`, `--add-header Origin`) y User-Agent moderno de escritorio en `YtdlpProtocol.kt`.
   - Excluidos dominios de TikTok/Douyin para preservar la integridad de su extractor nativo.
   - Añadida prueba `YtdlpProtocolTest.adultAndGenericUrlIncludesOriginHeaders`: Exitosa.
   - Sincronización de preferencias del motor (`lunafetch-prefs`) en Android y fallback a canal `NIGHTLY` ante errores 403/Forbidden.
   - Compilación e instalación en dispositivo físico: `.\gradlew installDebug` (`BUILD SUCCESSFUL in 33s`, instalado en Motorola Edge 60 Fusion).
