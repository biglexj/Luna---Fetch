// Luna Fetch — YouTube content script
// Sincroniza las cookies de sesión de YouTube/Google con la app de
// escritorio para que yt-dlp supere el anti-bot ("Sign in to confirm").
// El envío de descargas desde el navegador se hace desde el popup de la
// extensión (popup.html), que auto-rellena la URL de la pestaña activa.
(function () {
  'use strict';
  try {
    chrome.runtime.sendMessage({ action: 'syncCookies' }, () => void chrome.runtime.lastError);
  } catch (e) { /* extension context invalidated — ignore */ }
})();