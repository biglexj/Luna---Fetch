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
   - `./gradlew :composeApp:desktopTest :composeApp:compileKotlinDesktop --no-daemon`: `BUILD SUCCESSFUL in 51s` (17 tareas ejecutadas / up-to-date sin errores).
