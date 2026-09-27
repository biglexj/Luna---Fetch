# Tareas: Compatibilidad Universal y Formatos Seal

- [x] **Tarea 1**: Actualizar `DownloadModels.kt` (MediaFormat con FLAC y WAV, selector universal de video con soporte de streams pre-muxed).
- [x] **Tarea 2**: Actualizar `LinkCard.kt` para remover la lista blanca cerrada de dominios y permitir detección universal del portapapeles.
- [x] **Tarea 3**: Actualizar `AndroidDownloadEngine.kt` para condicionar banderas exclusivas de YouTube (`--remote-components`) y mejorar resiliencia SSL/extractor.
- [x] **Tarea 4**: Verificar o ajustar `YtdlpProtocol.kt` y `DesktopDownloadEngine.kt` para el flujo universal (`--no-check-certificates`).
- [x] **Tarea 5**: Ejecutar pruebas unitarias en `composeApp` (`./gradlew test`) y validar compilación en Android y Desktop.
