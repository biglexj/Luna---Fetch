# Plan: Carpetas por Tipo de Medio, Soporte Suno y Hover con Radio

## 1. Contexto y Problema

Biglex solicitó tres cosas en la misma sesión:

1. **Carpetas por tipo de archivo**: el material descargado se mezclaba en la
   carpeta de destino. Se pidió separar el audio y el video en subcarpetas
   propias (`audio` y `video`).
2. **Revisión del soporte de Suno**: «de Suno descargo pero sin título ni
   carátula». La descarga funcionaba, pero la ficha se leía mal.
3. **Bug visual**: «el hover no tiene bordes radius». Al pasar el cursor sobre
   la tarjeta de resultado aparecía un marco rectangular alrededor de una
   tarjeta con esquinas redondeadas.

### 2.1 Diagnóstico del fallo de Suno

Suno dejó de servir la ficha como un JSON plano y ahora la entrega dentro del
payload de **React Server Components**. Las cadenas viajan escapadas dentro de
un literal de JavaScript (`\"title\":\"Hello!\"`) y la página incluye además los
textos de su propia interfaz, que reutilizan las mismas claves (`title`,
`tags`, `prompt`, `display_name`).

El analizador anterior buscaba `\"clip\"` y aplicaba regex sobre el documento
completo, de modo que:

| Campo | Antes | Realidad de hoy |
|---|---|---|
| Título | `Get the full app experience` (texto de un modal) | `\"title\":\"Hello!\"` junto al id de la canción |
| Autor | `Tony` (a veces `V6`, una «persona» de Suno) | `\"display_name\":\"Biglex J\",\"handle\":\"biglexj\"` |
| Duración | Correcta | Correcta |
| Estilo | Vacío | `\"tags\":\"Latin pop with layered synth…\"` |
| Letra | Vacía | `\"prompt\":\"$c9\"` → fragmento `c9:Tc08,<letra>` |
| Portada | Regex sin coincidencias → URL por defecto | `\"image_large_url\":\"https://cdn2.suno.ai/image_large_<id>.jpeg\"` |

De ahí el síntoma: sin título real y con la portada genérica.

### 2.2 Diagnóstico del bloqueo de FFmpeg

`convertAudioWithFfmpeg()` arrancaba FFmpeg sin leer su salida estándar. FFmpeg
escribe mucho en `stderr`; al llenarse el canal el proceso **se queda
bloqueado** y la descarga se cuelga (se reproductionó: 600 s sin terminar).
Además, `-vn` descartaba la pista de la imagen, por lo que la carátula nunca
llegaba a incrustarse aunque el comando la pidiera.

## 2. Objetivo

- Separar cada descarga en la subcarpeta `audio` o `video` de la carpeta elegida,
  en escritorio y Android, sin romper las reparaciones.
- Leer correctamente la ficha de Suno (título, autor, alias, duración, estilo,
  letra y carátula) e incrustar la portada real en el archivo de audio.
- Corregir la indicación de hover de las tarjetas para que respete el radio.

## 3. Solución Arquitectónica

### A. Modelo de carpetas (`domain/MediaFolders.kt`)

Objeto único con la regla de nombres y la resolución de la ruta final:

- `folderFor(format)` → `audio` para los formatos sonoros, `video` para el resto.
- `pointsToFolder(destination, folder)` → detecta si la ruta ya termina en esa
  subcarpeta (evita `audio/audio` al reparar un archivo que ya vive en `audio`).
- `resolve(destination, format)` → ruta final de la descarga.

### B. Motor de escritorio (`DesktopDownloadEngine`)

Antes de cualquier descarga se resuelve el destino con `MediaFolders.resolve()`
y se usa esa ruta tanto para `yt-dlp` como para el extractor de Suno. La
carpeta se crea si no existe (`mkdirs`).

### C. Motor de Android (`AndroidDownloadEngine`)

En Android el destino es un árbol de Storage Access Framework, no una ruta. Se
añade `findOrCreateFolder()`: busca un documento con el nombre de la subcarpeta
(`audio`/`video`) entre los hijos de la raíz y, si no existe, lo crea con
`DocumentsContract.createDocument(..., MIME_TYPE_DIR, ...)`. Los archivos se
copian dentro de ese documento y la búsqueda de duplicados pasa a listar los
hijos de la subcarpeta.

### D. Analizador de Suno (`domain/SunoExtractor`)

Se reescribe el lector de metadatos en torno a tres ideas:

1. **Ancla en el identificador de la canción**: se localiza `\"id\":\"<uuid>\"` y
   se trabaja en una ventana alrededor; el título se busca antes del ancla y el
   resto después.
2. **Patrones compuestos**: el título se acepta cuando precede a `play_count`
   y el autor cuando aparece la pareja `display_name` + `handle` (las «personas»
   de Suno usan `display_name` sin alias).
3. **Dos niveles de escapado**: el valor se decodifica dos veces (literal de
   JavaScript y JSON de la ficha).

Para la letra se resuelven los fragmentos del payload (`"prompt":"$c9"` →
`c9:Tc08,<letra>`), con el `<textarea>` de la página como red de seguridad.

La portada se descarga a un archivo temporal y se incrusta en el audio con
`-map 1:v -c:v mjpeg -disposition:v:0 attached_pic` (sin `-vn`, que la
descartaría). Si el formato no admite carátula (WAV) o la incrustación falla,
se reintenta sin ella.

### E. FFmpeg sin bloqueo

`runFfmpeg()` drena la salida del proceso en un hilo aparte, de modo que el
canal nunca se llena y la conversión termina. El borrado de los temporales
(`deleteQuietly`) reintenta, porque en Windows el archivo puede estar tomado
un instante después del proceso.

### F. Hover con radio (`LunaCard` / `VideoCard`)

`LunaCard` acepta ahora `onClick`. Cuando la tarjeta es pulsable se usa la
variante `Card(onClick = …)` de Material 3, que dibuja la indicación sobre la
superficie de la tarjeta: el resplandor hereda el radio de 24 dp. Antes, el
`Modifier.clickable` se aplicaba **fuera** de la tarjeta y pintaba un marco
rectangular.

### G. Interfaz

La tarjeta de destino indica la subcarpeta que se usará
(`Se guardará en la subcarpeta «audio»…`) tanto en la vista de descarga como en
la hoja de descarga rápida.

## 4. Riesgo y Medidas

| Riesgo | Medida |
|---|---|
| Doble anidación `audio/audio` | `MediaFolders.pointsToFolder()` y prueba unitaria |
| Reparaciones que escriben fuera de su carpeta | La reparación sigue usando la carpeta padre del archivo dañado |
| Proveedores SAF que no admiten carpetas | Si la creación falla, se propaga un mensaje explícito |
| Cambio futuro del payload de Suno | Tres redes de seguridad: payload → cabecera HTML → `<textarea>`; la cabecera HTML cubre título y autor |
| Regresión en el nombre de los archivos | El nombre pasa a ser `Título - Autor.ext` con el título real |

## 5. Fuera de Alcance

- En Android el audio de Suno no se transcodifica: el motor no expone un
  ejecutable de FFmpeg, así que se conserva el MP4 original con el mensaje
  «Transcodificación no completada».
- No se migran automáticamente archivos ya descargados a las nuevas subcarpetas.
