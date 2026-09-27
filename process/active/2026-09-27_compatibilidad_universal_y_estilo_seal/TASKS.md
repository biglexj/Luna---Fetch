# Tareas: Compatibilidad Universal y Formatos Seal

- [x] **Tarea 1**: Actualizar `DownloadModels.kt` (MediaFormat con FLAC y WAV, selector universal de video con soporte de streams pre-muxed).
- [x] **Tarea 2**: Actualizar `LinkCard.kt` para remover la lista blanca cerrada de dominios y permitir detección universal del portapapeles.
- [x] **Tarea 3**: Actualizar `AndroidDownloadEngine.kt` para condicionar banderas exclusivas de YouTube (`--remote-components`) y mejorar resiliencia SSL/extractor.
- [x] **Tarea 4**: Verificar o ajustar `YtdlpProtocol.kt` y `DesktopDownloadEngine.kt` para el flujo universal (`--no-check-certificates`).
- [x] **Tarea 5**: Ejecutar pruebas unitarias en `composeApp` (`./gradlew test`) y validar compilación en Android y Desktop.
- [x] **Tarea 6**: Resolver error HTTP 403 Forbidden en Android (Pornhub/Cloudflare WAF) mediante inyección dinámica de cabeceras de origen (`--referer`, `--add-header Origin`, `--user-agent`), fallback a actualización forzada a canal Nightly de `yt-dlp` en caso de 403, e instalación en dispositivo físico.
