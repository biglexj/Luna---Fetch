# Tareas: Importador de Listas de Suno

## Fase 0 — Cimientos de la extensión

- [x] **Tarea 1**: Actualizar `browser-extension/manifest.json` con los permisos `scripting` y `clipboardRead`, `host_permissions` de `*://*.suno.com/*` y el content script `suno.js`.
- [x] **Tarea 2**: Crear `browser-extension/suno-hook.js` (mundo MAIN): parchear `navigator.clipboard.writeText` y `document.execCommand('copy')`, escuchar el evento `copy` y registrar las respuestas JSON de `fetch`/`XHR`. Canal `postMessage` con validación de origen.
- [x] **Tarea 3**: Crear `browser-extension/suno.js` (content script): botón flotante «Luna Fetch · Importar lista», panel de progreso y núcleo del importador (descubrimiento de filas, scroll incremental, deduplicación por UUID en `chrome.storage.session`).
- [x] **Tarea 4**: Implementar la máquina de estados de publicación (abrir menú → publicar → compartir → copiar → capturar → encolar → cerrar) con coincidencia de textos multilingüe y reintentos.
- [x] **Tarea 5**: Añadir en `background.js` las acciones `sunoQueue` (encola en el servidor local), `sunoQueueBatch` (envío por lotes) y `sunoCapture` (persiste lo descubierto por el hook en `chrome.storage.local`).
- [x] **Tarea 6**: Añadir el **modo diagnóstico**: vuelca al portapapeles el HTML del menú y del diálogo para calibrar los selectores en una sola iteración.

## Fase 1 — Integración con el popup

- [x] **Tarea 7**: Sección «Suno» en `popup.html`/`popup.js`/`popup.css`: «Importar lista abierta», interruptor «Autoenviar enlaces públicos de Suno» y «Enviar enlace copiado».
- [x] **Tarea 8**: Normalizador de enlaces de Suno compartido (`browser-extension/suno-links.js`) con `suno.com/s/…` → `https://suno.com/song/<uuid>`, usado por el popup, el importador y el hook, con pruebas en el banco headless.

## Fase 2 — Cookies de Suno hacia el escritorio

- [x] **Tarea 9**: Añadir el campo `cookieFile` a `DownloadRequest` y propagarlo en `LunaFetchPresenter.startDirectDownload` y en `LunaSocketServer` (hoy se descarta en `Main.kt`).
- [x] **Tarea 10**: `DesktopDownloadEngine`: pasar `--cookies` a `yt-dlp` y las cookies a `SunoExtractor`.
- [x] **Tarea 11**: `SunoExtractor.fetchMetadata`/`download` con cabecera `Cookie`: la ficha real se lee también en canciones privadas. Pruebas unitarias.
- [x] **Tarea 12**: `background.js`: `syncCookies` incluye el dominio `suno.com`.

## Fase 3 — Lote y acelerador

- [x] **Tarea 13**: Endpoint `POST /download/batch` en `LunaSocketServer` (encola N enlaces y devuelve el recuento con duplicados excluidos). El importador lo usa en bloques de 20 y degrada al envío uno a uno.
- [x] **Tarea 14**: Modo API: el hook aprende la llamada real de publicación y el importador la reproduce sin clics; si la llamada falla, vuelve al método de interfaz sin perder enlaces.
- [ ] **Tarea 15**: Endpoint de limpieza opcional (borrar la canción en Suno tras verificar el archivo local) — **solo si Biglex lo pide**, con confirmación explícita. *No implementado por decisión propia: es destructivo e irreversible en el servicio.*

## Fase 4 — Cierre

- [x] **Tarea 16**: Banco de pruebas headless completo (19/19): publicación, captura de enlaces, envío por lotes, intercepción del enlace copiado, modo API y degradación.
- [ ] **Tarea 16b**: Prueba manual con la lista real «Espacio Vtuber»: publicación, captura de enlaces, recuento de cola y verificación de los MP3 en `<destino>/audio/` con portada y letra. *Requiere la sesión autenticada de Suno.*
- [x] **Tarea 17**: Regresión de la extensión con YouTube y TikTok; suite completa de pruebas (`gradlew :composeApp:desktopTest`, 0 fallos).
- [x] **Tarea 18**: Actualizar `browser-extension/README.md`, `ROADMAP.md` y `RELEASE_NOTES.md`; commit de resguardo (`checkpoint: session 2026-10-23 - importador de listas de Suno`).

## Fase 5 — Diagnóstico de la letra

- [x] **Tarea 19**: Reproducir la queja «de Suno las letras no descarga» con el disco del usuario: `D:\Vídeos\Descargas\audio\Weightless Sleep - Biglex J.flac` sin su `.lyrics.txt`.
- [x] **Tarea 20**: Capturar la página real y completa de Suno (`suno_song_page_live.html`) y comprobar que el fragmento de la letra (`c7:T1033,…`) sí se resuelve: la letra **se lee bien**.
- [x] **Tarea 21**: Comprobar el final de la cadena con una descarga real de una canción *con* letra: se generan el MP3 y el `.lyrics.txt` (4.648 bytes) — el motor funciona.
- [x] **Tarea 22**: Verificar el origen real: la canción descargada es **instrumental** (`make_instrumental: true`, `prompt: ""`); Suno no tiene letra que entregar.
- [x] **Tarea 23**: Distinguir en el registro del caso esperado del anómalo: «🎼 La canción es instrumental» frente a «⚠️ La ficha no incluye la letra» (`SunoSongMetadata.isInstrumental`).
- [x] **Tarea 24**: Pruebas con capturas reales (`SunoLivePageParsingTest`): canción con letra y canción instrumental.

## Fase 6 — Carpetas por tipo de medio

- [x] **Tarea 25**: Renombrar las subcarpetas de medio a `Música` y `Videos` (los nombres de la biblioteca del usuario) en escritorio y Android; el texto de la interfaz y las pruebas siguen la misma regla.
- [x] **Tarea 26**: Reconocer los nombres antiguos (`audio`, `video`) como carpeta de medio, para que la reparación de un archivo descargado antes del cambio no anide `audio/Música`.
- [x] **Tarea 27**: Migrar el material ya descargado: archivo trasladado a `Música` y rutas del registro de descargas actualizadas con la misma API de preferencias que usa la aplicación.
