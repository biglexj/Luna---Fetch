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

## 9. Paginación del historial
- [x] `HistoryPaginator` en `domain/`: `PAGE_SIZE = 10`, `pageCount()` e
      `itemsForPage()`.
- [x] `HistoryCard`: diez descargas por página y selector de páginas al pie
      ("‹ Anterior | Página X de Y · N descargas | Siguiente ›").
- [x] **Sin scroll propio**: el historial se desplaza con el scroll de la
      ventana completa, como el resto de la interfaz.
- [x] Historial siempre en una sola columna de elementos (se eliminó el
      parámetro `columns` y el troceado `chunked`).
- [x] `HISTORY_LIMIT = 50` en el presentador (antes había tres `take(10)`).
- [x] `HistoryPaginatorTest` con 4 casos.

## 10. Empaquetado solo con EXE
- [x] `targetFormats` sin `Msi`: solo `Exe`, `Deb` y `Rpm`.
- [x] Versión 1.3.0 (`versionName` 1.3.0, `versionCode` 23, `APP_VERSION` 1.3.0).
- [x] `build-release.ps1` raíz: corregido el splat de `$RemainingArgs`, que
      fallaba cuando no había argumentos adicionales.
- [x] Manifest de winget en modo EXE: `InstallerType: burn` (el instalador es un
      bundle WiX) con `Silent: /quiet` y `SilentWithProgress: /passive`; se
      eliminó `Get-MsiProductCode` y la URL del `.msi`.
- [x] El generador de manifest, que no se invocaba, ahora se llama en la
      publicación y usa el EXE publicado.
- [x] Actualización in-app sin MSI: `UpdateChecker` solo acepta assets `.exe` y
      `DesktopPlatformBindings` instala el EXE con `/passive`.

## 11. Verificación del instalador
- [x] Build local del EXE: `release/LunaFetch-Windows-1.3.0.exe`
      (72.360.448 bytes) y `SHA256SUMS.txt`.
- [x] El EXE es un bootstrapper **WiX Burn** con payload MSI: acepta `/quiet`,
      `/passive`, `/norestart` y `/log`.
- [x] Instalación con el EXE (`/quiet /norestart`): exit 0 en 8 s, sin diálogo.
- [x] Aplicación instalada: título "Luna Fetch" sin distintivo DEV, tres columnas,
      paginación del historial y regla de duplicados por HTTP.

## 12. El error vive en su tarjeta, no en el registro
- [x] Se eliminó la línea `ERROR: ...` del "Registro de la sesión actual": el
      error ya se mostraba en la tarjeta "Detalles del error" y se duplicaba.
- [x] El registro de la sesión conserva la traza técnica de la operación.
- [x] El informe de fallos de 48 h sigue guardando el mensaje completo del
      error (`technicalDetails()`), aunque el registro no lo muestre.
- [x] Se corrigió el prefijo duplicado (`ERROR: ERROR: ...`) en el detalle
      técnico guardado.

## 13. El informe de 48 h dejó de guardarse (Java Preferences)
- [x] Causa raíz: `java.util.prefs.Preferences` rechaza valores de más de
      **8192 caracteres** con `IllegalArgumentException`, y el guardado en
      `DesktopPlatformBindings.saveDiagnostics` usa `runCatching`, así que el
      fallo quedaba en silencio. Con el JSON del informe en 8047 caracteres, el
      siguiente incidente (≈380 caracteres) ya no entraba y **el informe
      dejaba de persistirse** (la tarjeta seguía contando incidentes en
      memoria, pero al reiniciar se perdían).
- [x] `DiagnosticStore.fitToBudget()`: recorta la lista, quitando primero los
      incidentes más antiguos, hasta que el JSON quepa con holgura.
- [x] Se aplica en escritorio (Java Preferences) y en Android (SharedPreferences).
- [x] `technicalDetails` se acota a 1000 caracteres por incidente.
- [x] `DiagnosticStoreTest`: dos casos nuevos para el recorte por presupuesto.

## 14. Prueba en teléfono físico (Android 16, motorola edge 60 fusion)
- [x] Instalación: el teléfono se conecta por **ADB inalámbrico**; el registro
      fantasma `emulador-5562` (offline) impedía que Gradle instalara
      ("No online devices found"). Con `adb -s <serie>` el APK instala bien.
- [x] La app arranca en el dispositivo y el historial conserva las 10 entradas
      reales, en una sola columna.
- [x] Los estados por archivo se detectan en el dispositivo: "⚠️ Archivo movido
      o eliminado" con la acción de re-descargar, y el distintivo del equipo
      (`💻 BIGLEXJ`) para los archivos de otro equipo.
- [x] La tarjeta "Detalles del error" funciona en Android: el error del análisis
      aparece ahí y no en el registro de la sesión.
- [x] El informe de fallos de 48 h se guarda en Android (SharedPreferences):
      1 incidente con el mensaje y el detalle técnico.
- [x] Re-descarga real en el teléfono: el MP3 de YouTube se descargó en
      `Movies/YouTube` **sin crear un "(1)" duplicado** (el arreglo de
      `copyToTree`).
- [x] **Bug encontrado y corregido en esta prueba**: al re-descargar un ítem
      del historial cuyo enlace trae `list=`/`start_radio=` (enlace de radio),
      la app analizaba la colección completa y ofrecía 399 elementos en vez de
      ese archivo. Ahora `LinkCanonicalizer.singleItemUrl()` deja el enlace
      apuntando al contenido exacto, y la re-descarga vuelve a reparar en el
      sitio, sin renombrar el archivo.
- [x] `LinkCanonicalizerTest`: dos casos nuevos para `singleItemUrl`.
- [x] Versión **1.3.1** (`versionName` 1.3.1, `versionCode` 24, `APP_VERSION`
      1.3.1): el 1.3.0 ya estaba instalado, y reinstalar la *misma* versión
      con otro build devuelve 1638 ("otra versión ya instalada"). Subir la
      versión permite además probar la ruta de actualización real.
- [x] Actualización verificada: `LunaFetch-Windows-1.3.1.exe /quiet` sobre la
      1.3.0 instalada → **exit 0 en 7 s**, con `RemoveExistingProducts` en el log
      y el producto registrado como 1.3.1.
