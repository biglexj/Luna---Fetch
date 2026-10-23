# Plan: Importador de Listas de Suno (publicación en lote + captura del enlace público)

## 1. Contexto y petición

Biglex trabaja sobre la lista abierta de un espacio de trabajo de Suno
(«Espacio Vtuber» en la captura). El flujo manual actual es, canción a canción:

1. Pulsar `…` en la fila.
2. Abrir **Compartir** y activar **Pública**.
3. Copiar el enlace público.
4. Pegarlo en el popup de Luna Fetch y encolarlo.

La petición: **publicar en lote todas las canciones de la lista abierta, copiar
automáticamente el enlace público de cada una y mandarla a descargar**,
aprovechando la extensión para que, si es posible, **intercepte el enlace
público** y lo envíe sola.

Decisiones ya tomadas con Biglex:

- **Captura híbrida**: la interfaz (clic a clic) es el camino principal y
  garantizado; la API interna de Suno, si se descubre, actúa como acelerador
  opcional en una segunda fase.
- **Cookies de Suno sincronizadas** hacia el escritorio, para que la ficha real
  se lea también en canciones privadas (red de seguridad del flujo público).

## 2. Diagnóstico del estado actual

| Pieza | Estado | Nota |
|---|---|---|
| `domain/SunoExtractor.kt` | Funcional | Resuelve el UUID, lee la ficha del payload RSC y baja `cdn1.suno.ai/<id>.mp4`; transcodifica con FFmpeg, incrusta portada y guarda la letra |
| Extensión → servidor local | Funcional | `GET /download?url=…&format=…` encola de a uno, con deduplicación por clave canónica |
| Cola de descargas | Funcional | Un enlace detrás de otro, con estado visible en la interfaz |
| **Publicación en lote** | ❌ Ausente | No existe ninguna automatización de la interfaz de Suno |
| **Captura del enlace público** | ❌ Ausente | El popup solo acepta URLs escritas a mano |
| **Cookies en la descarga** | ⚠️ Latente | `LunaSocketServer` recibe `cookieFile` en `/download`, pero `Main.kt` lo descarta: hoy las cookies solo llegan a `/analyze` |

**Limitación real que motiva el flujo público**: el extractor lee la ficha de
`https://suno.com/song/<uuid>` **sin sesión de autenticación**. En una canción
privada el payload no trae metadatos, y el resultado es el título genérico con
la portada por defecto. Publicar resuelve el caso; las cookies lo resuelven sin
necesidad de exponer el material.

## 2.1 Escenario detonante: el límite de 20 canciones

Suno limita ahora el número de canciones de la biblioteca/espacio de trabajo.
Publicar la canción era la **ruta** que Biglex tenía en mente: el enlace
público es el único identificador que el escritorio de Luna Fetch puede leer
sin sesión, de modo que la canción se puede descargar, archivar y liberar la
ranura.

De ahí se derivan tres exigencias que el diseño debe satisfacer:

| Exigencia | Cómo se resuelve |
|---|---|
| **No iniciar sesión otra vez** | El importador se ejecuta dentro de la pestaña de Suno ya autenticada: no gestiona contraseñas, no pega cookies y todas las acciones quedan autorizadas por la sesión del navegador |
| **El enlace debe sobrevivir a la canción en la biblioteca** | Al ser público, el enlace `suno.com/s/…` funciona desde el escritorio sin sesión; además, con la sincronización de cookies (4.6) la ficha de las privadas también es legible |
| **No perder nada al liberar ranuras** | Luna Fetch guarda MP3 con portada incrustada, letra `.lyrics.txt` y metadatos ID3; el archivo local es la copia permanente |

> **Fuera de alcance por defecto**: la eliminación de las canciones en Suno para
> liberar las ranuras. Es una acción destructiva e irreversible en el servicio.
> Si Biglex lo pide, se implementa como un paso **explícito y separado**, con
> confirmación por canción y solo después de verificar que el archivo local
> existe y pesa lo esperado (el motor ya tiene verificación de archivos).

## 3. Objetivo

- Convertir la lista abierta de Suno en un lote de enlaces públicos con **un
  solo gesto**.
- Capturar el enlace de cada canción **sin copiarlo a mano** (ni depender del
  portapapeles del sistema).
- Encolar cada canción en Luna Fetch con el formato elegido (MP3 por defecto
  para canciones) y recibir el recuento de pendientes.
- Permitir que la extensión **intercepte cualquier enlace público de Suno
  copiado** y lo envíe sola.
- Mantener intactos los flujos actuales de YouTube, TikTok y Synapse.

## 4. Arquitectura

### 4.1 Pieza A — Hook de página (`browser-extension/suno-hook.js`, mundo MAIN)

Script inyectado en el **mundo de la página** (no en el mundo aislado del
content script), porque es el único punto donde se puede observar
`navigator.clipboard.writeText` y el tráfico `fetch`/`XHR` de la propia web.

Responsabilidades, todas de **solo lectura**:

1. **Portapapeles**: parchear `navigator.clipboard.writeText`, parchear
   `document.execCommand('copy')` y escuchar el evento `copy` del documento.
   Cualquier texto que coincida con un enlace de Suno se emite como evento.
2. **Red**: envolver `window.fetch` y `XMLHttpRequest` para registrar método,
   URL, cuerpo y un resumen de la respuesta JSON. Sirve para **descubrir el
   endpoint real de «compartir»** y la forma de la ficha en el payload.
3. **Canal**: `window.postMessage({source:'luna-suno-hook', …})` hacia el
   content script, que valida el origen antes de recibir nada.

> Nota de aislamiento: los content scripts viven en un mundo aislado, así que
> el parche del portapapeles se inyecta con
> `chrome.scripting.executeScript({world:'MAIN'})` (Chrome/Edge 111+), sin
> necesidad de `web_accessible_resources`.

### 4.2 Pieza B — Orquestador (`browser-extension/suno.js`, content script)

- Botón flotante **«Luna Fetch · Importar lista»** y panel de progreso
  (por canción: título, estado, enlace capturado).
- **Descubrimiento de filas** con doble heurística:
  1. atributos `data-*` / `href` que contengan el UUID de la canción;
  2. botón de opciones (`…`) localizado por fila y扫 con paginación infinita
     (scroll incremental con espera de carga).
- Ejecuta la máquina de estados de 4.3 y encola cada resultado.
- Tres modos seleccionables: **dry-run** (solo informa, no publica),
  **publicar + encolar** y **solo enlaces** (no publica; captura lo ya público).

### 4.3 Máquina de estados por canción

| # | Estado | Acción | Tolerancia |
|---|---|---|---|
| 1 | `LOCALIZAR` | Fila *n* de la lista | Scroll incremental; re-consulta el DOM en cada paso (la lista puede virtualizarse) |
| 2 | `ABRIR_MENU` | Clic en `…` | Varios candidatos: `aria-label`/`title` con «more/options/más», nodo de texto `…` |
| 3 | `PUBLICAR` | Clic en el ítem público del menú, si existe | Coincidencia por texto multilingüe (`público`, `public`, `publicar`, `hacer pública`, `compartir con el público`) |
| 4 | `COMPARTIR` | Clic en `compartir`/`share` | Ítem del menú o botón del diálogo |
| 5 | `ENLACE` | Activar la opción pública del diálogo (si existe) y pulsar **copiar** | Varios candidatos de botón y de `input[value]` |
| 6 | `CAPTURAR` | El hook entrega el texto copiado | Se normaliza a `https://suno.com/song/<uuid>` (preferida: siempre parseable) y se conserva el slug público para mostrarlo |
| 7 | `ENCOLAR` | `chrome.runtime.sendMessage({action:'sunoQueue'})` | La cola deduplica por clave canónica; además se recuerda lo ya capturado en la sesión |
| 8 | `CERRAR` | `Escape` / clic fuera | Cierre idempotente; si el diálogo persiste, se fuerza por DOM |

Si el badge **«Publicada»** ya está presente, se omite el paso 3 (no se repite
la publicación) y se pasa directamente a capturar el enlace.

### 4.4 Pieza C — Acelerador por API (fase 2, opcional)

Con lo que el hook registra en una compartición manual (endpoint + cuerpo +
forma de la respuesta), el importador puede **reproducir la llamada para las N
canciones** sin clics: segundos en lugar de minutos. Se valida contra el método
de interfaz (mismo conjunto de enlaces) antes de activarse. Si el endpoint
cambia o deja de responder, se degrada al método de interfaz sin romper nada.

### 4.5 Pieza D — Popup (`popup.html` / `popup.js` / `popup.css`)

Sección **Suno** en el popup:

- **«Importar lista abierta»**: inyecta el content script en la pestaña activa
  de `suno.com` y arranca el importador.
- **Interruptor «Autoenviar enlaces públicos de Suno»** (persistido en
  `chrome.storage.local`): cualquier enlace `suno.com/s/…` o `suno.com/song/…`
  copiado dentro de Suno se encola automáticamente.
- **«Enviar enlace copiado»**: lee el portapapeles del sistema y, si contiene
  un enlace de Suno, lo encola sin abrir la pestaña.

### 4.6 Pieza E — Cookies de Suno hacia el escritorio (Kotlin)

Cadena a completar (hoy truncada en `Main.kt`):

```
LunaSocketServer(/download) → cookieFile
  → LunaFetchPresenter.startDirectDownload(…, cookieFile)
  → DownloadRequest.cookieFile
  → DesktopDownloadEngine  → yt-dlp --cookies (hoy ignorado)
  → SunoExtractor.fetchMetadata(id, cookieFile) → cabecera Cookie
```

- `SunoExtractor` pasa a aceptar el fichero de cookies y añade la cabecera
  `Cookie` al pedir la ficha: **la ficha real llega también en canciones
  privadas** (título, autor, estilo, letra y portada reales).
- La extensión amplía `syncCookies()` con el dominio `suno.com`.
- Efecto colateral favorable: la sincronización de cookies de YouTube también
  empieza a aplicarse a las **descargas** (hoy solo a `/analyze`).

### 4.7 Pieza F — Endpoint de lote (opcional)

`POST /download/batch` con `{urls:[…], format}` → encola todo y responde
`{ok, queued, pending, results[]}` con los duplicados excluidos. Evita N
idencias y vueltas y ofrece un único recuento honesto («37 encoladas, 3 ya
descargadas»).

## 5. Cambios por archivo

| Fichero | Cambio |
|---|---|
| `browser-extension/suno-hook.js` | **Nuevo**: hook de portapapeles y de red en mundo MAIN |
| `browser-extension/suno.js` | **Nuevo**: orquestador del importador y panel de progreso |
| `browser-extension/background.js` | Nuevas acciones `sunoQueue` y `sunoCapture`; `syncCookies` incluye `suno.com` |
| `browser-extension/popup.html/js/css` | Sección Suno: importar, autoenvío, enviar enlace copiado |
| `browser-extension/manifest.json` | Permisos `scripting` y `clipboardRead`; `host_permissions` de `*.suno.com`; content script de Suno |
| `domain/SunoExtractor.kt` | `fetchMetadata`/`download` aceptan cookies de sesión |
| `domain/DownloadRequest.kt` | Campo `cookieFile` |
| `domain/LunaFetchPresenter.kt` | `startDirectDownload` propaga `cookieFile` |
| `platform/LunaSocketServer.kt` | Propaga `cookieFile`; endpoint `/download/batch` (Fase 2) |
| `platform/DesktopDownloadEngine.kt` | Pasa `--cookies` a yt-dlp y las cookies a Suno |
| `Main.kt` | Encadena el `cookieFile` |

## 6. Riesgos y medidas

| Riesgo | Medida |
|---|---|
| Suno renombra los menús o cambia el diálogo | Coincidencia por texto multilingüe + **modo diagnóstico** que vuelca el HTML del menú al portapapeles → calibración en una sola iteración |
| Publicar algo que el usuario no pidió | Modo *dry-run* por defecto en la primera ejecución, contador de filas y **una única confirmación** antes de publicar en lote |
| Encolar dos veces la misma canción | Deduplicación de la cola (clave canónica) + registro de UUID ya capturados en `chrome.storage.session` |
| La lista se recarga a mitad del proceso | Reanudación por índice; lo ya capturado persiste en `chrome.storage.session` |
| Filtración de material que no debería ser público | El panel muestra título y duración de cada fila **antes** de publicar |
| La API privada cambia o desaparece | Es un acelerador opcional: el método de interfaz es el camino principal y el sistema degrada sin romperse |
| Cookies de sesión sensibles | Solo viajan de forma local (`127.0.0.1`), a un fichero temporal ya existente (`%TEMP%/luna_session_cookies.txt`), nunca salen del equipo |

## 7. Fuera de alcance

- No se falsea ni se replica la API de Suno en la fase principal: la
  publicación se hace **siempre** desde la interfaz real del navegador.
- No se automatiza el inicio de sesión en Suno.
- No se toca el motor de Android en esta iteración (el importador es una
  herramienta de escritorio; en Android se conserva el flujo manual por Synapse).
- No se migran ni renombran archivos ya descargados.

## 8. Verificación prevista

- Prueba manual con la lista «Espacio Vtuber» (≥8 canciones): publicación,
  captura de enlaces, recuento de cola.
- Comprobación de que los MP3 llegan a `<destino>/audio/` con título real,
  portada incrustada y letra `.txt` acompañante.
- Pruebas unitarias del normalizador de enlaces (`suno.com/s/…` → canónico) y
  del reconocedor de URLs de Suno.
- Regresión: la extensión sigue descargando de YouTube igual que antes.
