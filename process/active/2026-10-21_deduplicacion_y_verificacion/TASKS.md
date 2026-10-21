# Tareas: Deduplicación y Verificación de Archivos

## 1. Núcleo de dominio
- [x] `LinkCanonicalizer`: clave canónica por contenido (YouTube, TikTok, genérico).
- [x] `LocalFileState`: `Missing` / `Empty` / `Present`.
- [x] `DownloadRegistryEntry` + `DownloadRegistry`: registro persistente y búsqueda.
- [x] `parentFolderOf`: carpeta que contiene al archivo.

## 2. Plataformas
- [x] `PlatformBindings.localFileState()` con valor por defecto.
- [x] `PlatformBindings.loadDownloadRegistry()` / `saveDownloadRegistry()`.
- [x] `PlatformBindings.deleteOutput()`.
- [x] Escritorio: estado por `exists()` + `length()`, borrado por `File.delete()`.
- [x] Android: estado por `openFileDescriptor().statSize` y `File.length()`,
      borrado por `ContentResolver.delete()`.
- [x] Android: `copyToTree()` sobrescribe el documento con el mismo nombre.

## 3. Descarga
- [x] `DownloadRequest.replaceExisting`.
- [x] `YtdlpProtocol`: `--force-overwrites` solo cuando se repara.
- [x] Cola: `QueuedDownload.canonicalKey` y `QueuedDownload.repairPath`.
- [x] `EnqueueOutcome` / `EnqueueResult` en `domain/DownloadQueue.kt`.

## 4. Presentador
- [x] `startDirectDownload()`: duplicado en la cola → ignorado.
- [x] `startDirectDownload()`: ya descargado y en pie → ignorado.
- [x] `startDirectDownload()`: archivo vacío → se rehace y se reemplaza.
- [x] `startDirectDownload()`: archivo ausente → se descarga con normalidad.
- [x] `processQueueItem()`: destino y sobrescritura en la reparación.
- [x] `runDownload()`: alta en el registro al completar la descarga.
- [x] `redownloadHistoryItem()` con `force` y `repairHistoryItem()` nuevo.

## 5. Interfaz y servidor local
- [x] Historial: distingue dañado, movido y en pie.
- [x] Servidor local: `queued:false` con `reason` en los duplicados.
- [x] Popup de la extensión: mensajes de duplicado.
- [x] `Main.kt` y prueba de integración adapted al nuevo contrato.

## 6. Pruebas
- [x] `LinkCanonicalizerTest`.
- [x] `DownloadRegistryTest`.
- [x] `DownloadQueueTest`: duplicados, formato, archivo en pie, dañado y movido.
- [x] `LunaSocketServerQueueTest`: el servidor informa el duplicado.

## 7. Interfaz
- [x] Dos columnas en ventanas normales y tres solo en pantalla grande o
      expandida (a partir de 1500 dp de ancho).
- [x] Cola de descargas en la columna izquierda, junto al enlace, para que no
      desplace hacia abajo el botón de descarga.
- [x] Mensaje de fallos en su propia tarjeta, debajo de la cola y no al lado
      del historial.
- [x] Historial debajo del selector de calidad en las dos versiones anchas, y
      en una sola columna de elementos.
- [x] Distintivo "DEV" en el encabezado y en el título de la ventana.
- [x] Dispositivos de descarga en dos columnas que usan todo el ancho, sin
      cortar el nombre del dispositivo.
- [x] Historial: distingue dañado, movido y en pie.
- [x] Servidor local: `queued:false` con `reason` en los duplicados.
- [x] Popup de la extensión: mensajes de duplicado.
- [x] `Main.kt` y prueba de integración adaptados al nuevo contrato.

## 8. Verificación
- [x] Compilación de escritorio y Android.
- [x] Ejecución de la suite de pruebas.
- [x] Verificación visual en la aplicación de escritorio.
- [ ] Prueba en teléfono físico (regla del proyecto).
