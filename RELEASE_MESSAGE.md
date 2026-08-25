# 🚀 Luna Fetch — v1.1.8

Correcciones y mejoras de estabilidad sobre v1.1.7.

## 📝 Resumen

Esta versión de parche corrige el flujo de descarga desde el navegador, el bypass anti-bot de YouTube y varios detalles de UI. Se reemplaza el botón inyectado en YouTube por un popup nativo de la extensión del navegador, se sincronizan cookies reales del navegador para `yt-dlp`, y se propaga el aviso de nueva versión por Aurora Synapse LAN.

## 🔧 Correcciones y cambios

- **Extensión de navegador**: popup nativo al hacer clic en el ícono (URL de la pestaña activa auto-rellenada, formato 🎬 MP4 / 🎵 MP3, envío directo al escritorio). Se retira el botón inyectado en la página de YouTube. Corrige el `ERR_FILE_NOT_FOUND` del ícono de la extensión.
- **Anti-bot de YouTube**: la extensión sincroniza cookies reales (`chrome.cookies` de youtube.com/google.com) al endpoint local `/cookies`; `yt-dlp` las usa antes que el fallback `--cookies-from-browser`, evitando el "Sign in to confirm you're not a bot" y los HTTP 403.
- **Aurora Synapse LAN**: los nodos de la misma Wi-Fi reciben el aviso de nueva versión (`/api/v1/synapse/announce-release`) y abren el modal de actualización con las mismas notas. Solo se transportan metadatos; los binarios se descargan siempre desde GitHub Releases.
- **UI**: botón flotante de cierre y cierre tocando el espacio en blanco en los diálogos (fiable en táctil), selector de canal del motor (Estable/Nocturno) con persistencia, y el botón de descarga completada ahora abre la **carpeta** de destino (`📂 Abrir carpeta`).
- **Diagnóstico**: mensajes de error accionables cuando `yt-dlp` devuelve HTTP 403 o pide inicio de sesión.
- **Iconos**: versión con fondo para el launcher de Android y transparente para Windows.

¡Gracias por usar Luna Fetch! 🌙