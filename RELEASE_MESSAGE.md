# 🚀 ¡Nuevo Lanzamiento de Luna Fetch! — v1.2.0

Nos complace anunciar la versión **v1.2.0** de **Luna Fetch**, centrada en la integración total con el navegador, la propagación de actualizaciones por la red local y una experiencia de uso más robusta.

## 📝 Resumen

Esta versión reemplaza el botón inyectado en YouTube por un **popup nativo de la extensión del navegador** con auto-rellenado de URL, formato MP4/MP3 y envío directo al escritorio. Se añade la **propagación de releases por Aurora Synapse LAN** para que todos los nodos vean el modal de actualización cuando un equipo detecta una nueva versión. Se corrige el bypass anti-bot de YouTube sincronizando cookies reales desde el navegador y se moderniza el manejo de errores y la ergonomía de los diálogos.

## 🌟 Novedades Destacadas

### 🧩 Extensión de navegador reescrita (v1.2.0)
- **Popup nativo completo** al hacer clic en el ícono de la extensión: input de URL auto-rellenado con la pestaña activa, selector de formato 🎬 Video MP4 / 🎵 Audio MP3 y botón **Enviar a Luna Fetch**.
- **Botón flotante eliminado**: tras múltiples intentos contra el shadow DOM de YouTube (Polymer components, grid layout, re-renders), se descartó el botón inyectado en favor del popup, que es 100% fiable.
- **Página `popup.html` funcional** (soluciona el antiguo `ERR_FILE_NOT_FOUND` del ícono de la extensión).

### 🍪 Sync de cookies para superar el anti-bot de YouTube
- La extensión ahora lee `chrome.cookies.getAll` para `youtube.com` y `` con la API `chrome.cookies`, las serializa como **Netscape cookie file** y las envía al endpoint local `/cookies` de Luna Fetch.
- El archivo `%TEMP%/luna_session_cookies.txt` es leído por `yt-dlp` antes que el fallback `--cookies-from-browser`, eliminando el "Sign in to confirm you're not a bot" sin depender del navegador cerrado.

### 📡 Propagación de release por Aurora Synapse LAN (regla auto_updater #11)
- Cuando un nodo Luna Fetch detecta una nueva versión en GitHub, **anuncia el release a todos los peers descubiertos en la misma Wi-Fi** mediante `POST /api/v1/synapse/announce-release`.
- Los nodos receptores abren el `UpdateModalDialog` con las mismas notas (`sanitizeReleaseNotes`) y muestran el toast `🛰️ Nueva versión vX.Y.Z disponible (recibida desde {device})`.
- **Aurora SOLO transporta metadatos** (version, body markdown, URLs a GitHub). Los binarios (`.exe`/`.msi`/`.apk`) **nunca** se suben ni distribuyen por Aurora; viven exclusivamente en GitHub Releases y se descargan directo desde ahí.

### 🎨 Mejoras de UI/UX
- **Iconos diferenciados por plataforma**: `icon-transparent.png` para Windows (transparente), `icon.png` con fondo para Android (launcher opaco).
- **AboutUpdatesDialog y SettingsDialog**: botón flotante `✕`, cierre tocando el espacio en blanco (scrim con `pointerInput`/`detectTapGestures` — fiable en táctil), versión 1.1.7 visible, selector de canal del motor (Estable / Nocturno) con persistencia.
- **Historial de descargas**: el botón "Abrir resultado" ahora abre la **carpeta** de destino (`📂 Abrir carpeta`) en vez de reproducir el archivo, evitando duplicar la acción de "▶▶ Reproducir en Prisma".

### 🛡️ Robustez y diagnósticos
- Mensaje de error amigable cuando `yt-dlp` devuelve **HTTP 403** (anti-bot) sugiriendo el canal Nocturno o la sincronización de cookies.
- `LunaSocketServer` reestructurado con endpoint dedicado `/cookies` para la sincronización de la extensión, guardado fiable de cookies y reintento ordenado por navegador.

## 🔧 Detalles técnicos

- Extensión de navegador (MV3): `browser-extension/{manifest.json, background.js, content.js, popup.html, popup.css, popup.js}` en **v1.2.0**.
- Nuevos modelos serializables: `LanAnnounceReleaseRequest` y endpoint `POST /api/v1/synapse/announce-release` en `SynapseLanServer`.
- `LunaFetchPresenter.checkForUpdates()` ahora hace `broadcastReleaseToAurora(release)` y `handleAuroraReleaseAnnouncement(req)` tras la verificación en GitHub.
- `DesktopDownloadEngine.friendlyError()` clasifica los errores de `yt-dlp` (403, sign-in, unable to download video data) y los traduce a mensajes accionables para el usuario.

¡Gracias por usar Luna Fetch! 🌙