# Tareas: Carpetas por Tipo de Medio, Soporte Suno y Hover con Radio

- [x] **Tarea 1**: Crear `domain/MediaFolders.kt` con la regla `audio`/`video` y la resolución de ruta sin doble anidación.
- [x] **Tarea 2**: Resolver la subcarpeta en `DesktopDownloadEngine` (rama de `yt-dlp` y rama de Suno).
- [x] **Tarea 3**: Crear o reutilizar la subcarpeta dentro del árbol SAF en `AndroidDownloadEngine` (`findOrCreateFolder`, `listChildren`).
- [x] **Tarea 4**: Reescribir el analizador de metadatos de Suno con ancla en el identificador, patrones compuestos y decodificación de dos niveles.
- [x] **Tarea 5**: Resolver la letra desde los fragmentos del payload (`$c9`) con el `<textarea>` como red de seguridad.
- [x] **Tarea 6**: Descargar la portada e incrustarla en el archivo de audio (`attached_pic`), con reintento sin carátula.
- [x] **Tarea 7**: Corregir el bloqueo de FFmpeg drenando su salida y robustecer el borrado de temporales.
- [x] **Tarea 8**: Sustituir el `clickable` externo por `Card(onClick = …)` en `LunaCard` y `VideoCard` para que el hover respete el radio.
- [x] **Tarea 9**: Mostrar la subcarpeta de destino en la tarjeta de descarga y en la hoja de descarga rápida.
- [x] **Tarea 10**: Pruebas unitarias (`MediaFoldersTest`, `SunoMetadataParsingTest`), captura real del payload de Suno como fixture y prueba de extremo a extremo en escritorio.
- [x] **Tarea 11**: Compilación de escritorio y Android, suite completa de pruebas y actualización de `ROADMAP.md`.
