# Plan: Deduplicación de Enlaces y Verificación de Archivos

## 1. Contexto y Problema

Luna Fetch no distinguía entre tres situaciones que Biglex anticipaba:

1. **Enlace duplicado**: el mismo video enviado dos veces se descargaba dos veces.
   `startDirectDownload()` encolaba de forma incondicional, sin comparar contra la
   cola ni contra el historial.
2. **Video ya descargado**: no existía ninguna verificación. El único control
   estaba en la tarjeta de historial, y era manual: `isLocalPathAccessible()`
   solo se consultaba al pintar la lista.
3. **Archivo dañado**: la comprobación de existencia no separa un archivo vacío
   (descarga truncada o dañada) de uno que fue movido o eliminado. En escritorio
   `isLocalPathAccessible()` era `File(path).exists()`, de modo que un archivo de
   cero bytes pasaba por válido.

Además, `yt-dlp` no sobrescribe archivos existentes por defecto: los marca como
"ya descargado" y los omite. Sin `--force-overwrites` una reparación no
reemplaza nada.

## 2. Objetivo

Implementar una regla única y verificable para los enlaces duplicados y los
archivos ya descargados:

| Situación | Resultado esperado |
|---|---|
| El enlace ya está pendiente o descargándose | Se ignora |
| El enlace ya fue descargado y el archivo está en pie | Se ignora |
| El archivo existe pero está vacío (dañado) | Se rehace y se reemplaza en su sitio |
| El archivo ya no existe (movido o eliminado) | Se descarga de nuevo con normalidad |

## 3. Decisiones tomadas

- **Alcance de la deduplicación**: duplicados en la cola **y** en el historial.
- **Criterio de daño**: el archivo existe y su tamaño es mayor que cero.
- **Reemplazo**: en el mismo sitio, con `--force-overwrites` de yt-dlp.

## 4. Solución Arquitectónica

### A. Clave canónica (`domain/LinkCanonicalizer.kt`)

`canonicalKey(url)` reduce un enlace a una clave estable: `watch?v=abc`,
`youtu.be/abc`, `shorts/abc` y `embed/abc` producen `yt:abc`. Se descartan los
parámetros de seguimiento (`utm_*`, `fbclid`, `si`, `is_from_webapp`...). TikTok
usa `tt:<ruta>` y el resto `web:<host><ruta>?<parámetros>`.

### B. Estado del archivo (`domain/DownloadRegistry.kt`)

`LocalFileState` distingue `Missing` (movido o eliminado), `Empty` (dañado) y
`Present` (en pie). `PlatformBindings.localFileState()` lo implementa por
plataforma: en escritorio con `File.exists()` + `length()`; en Android con
`openFileDescriptor().statSize` para URI `content://` y `File.length()` para
rutas locales.

### C. Registro de descargas (`DownloadRegistryEntry`)

El historial visible está limitado a los últimos diez elementos, así que no
sirve como registro de "ya descargado". El registro aparte, persistente
(`downloadRegistry` en Java Preferences y SharedPreferences), conserva hasta
200 entradas con el mapa enlace → archivo.

### D. Presentador (`LunaFetchPresenter`)

`startDirectDownload()` devuelve `EnqueueResult` con `EnqueueOutcome`:

- `Queued`: el enlace se encoló.
- `InvalidUrl`: el enlace no es válido.
- `DuplicateInQueue`: ya estaba pendiente o descargándose.
- `AlreadyDownloaded`: ya estaba descargado y el archivo está en pie.

El botón de re-descarga del historial y la reparación usan `force = true` para
saltar la comprobación del registro, porque son acciones explícitas del usuario.

### E. Reemplazo en el sitio

`DownloadRequest.replaceExisting` añade `--force-overwrites` a los argumentos de
yt-dlp (`YtdlpProtocol.buildDownloadArguments`). Una reparación se escribe en la
carpeta original del archivo (`parentFolderOf`). Si la descarga reparadora
produce un nombre distinto, el archivo dañado se elimina.

En Android, `copyToTree()` busca un documento con el mismo nombre en el árbol de
destino y lo sobrescribe en lugar de crear una copia "(1)".

## 5. Interfaz

- La tarjeta de historial distingue los tres estados: "Archivo dañado (vacío)"
  con botón ♻️ de reparación, "Archivo movido o eliminado" con botón 🔄.
- El popup de la extensión distingue "⏭️ ya está en la cola" de "✅ ya está
  descargado".
- El servidor local responde `{"ok":true,"queued":false,"reason":"..."}`.

## 6. Verificación

- `LinkCanonicalizerTest`: equivalencia de variantes de YouTube, TikTok y
  parámetros de seguimiento.
- `DownloadRegistryTest`: búsqueda, actualización, límite y carpeta madre.
- `DownloadQueueTest`: duplicado en la cola, equivalencia por formato, archivo
  en pie, archivo dañado (se reemplaza) y archivo movido (se descarga normal).
- `LunaSocketServerQueueTest`: el servidor informa el duplicado.
