// Luna Fetch — hook de página para Suno (mundo MAIN)
//
// Este script se inyecta en el mundo de la página (no en el mundo aislado del
// content script) porque es el único punto donde se puede observar lo que la
// propia web de Suno copia y las peticiones que envía.
//
// Es estrictamente de SOLO LECTURA: no altera ninguna petición ni respuesta,
// solo observa y emite eventos por `window.postMessage`.
//
//  · Portapapeles: `navigator.clipboard.writeText`, `document.execCommand('copy')`
//    y el evento `copy` del documento.
//  · Red: `window.fetch` y `XMLHttpRequest` (modo diagnóstico: sirve para
//    descubrir el endpoint real de «compartir»).
(function () {
  'use strict';

  if (window.__lunaSunoHookInstalled) return;
  window.__lunaSunoHookInstalled = true;

  const CHANNEL = 'luna-suno-hook';

  // Forma de los enlaces: la define `suno-links.js`, inyectado en este mismo
  // mundo antes que el hook.
  const Links = window.SunoLinks;
  if (!Links) {
    console.error('[Luna Fetch][Suno] falta suno-links.js en el mundo de la página.');
    return;
  }

  /** Dominios de Suno reconocidos como enlaces de canción. */
  const SUNO_LINK_RE = Links.SUNO_LINK_RE;
  const SUNO_UUID_RE = Links.SUNO_UUID_RE;

  function emit(type, payload) {
    try {
      window.postMessage({ source: CHANNEL, type: type, payload: payload }, '*');
    } catch (e) { /* ignorado: la página pudo navegar */ }
  }

  function notifyLink(text, origin) {
    if (typeof text !== 'string' || !text) return;
    const match = text.match(SUNO_LINK_RE);
    if (!match) return;
    emit('suno-link', {
      url: match[0],
      origin: origin,
      uuid: (text.match(SUNO_UUID_RE) || [null])[0],
      raw: text.slice(0, 500)
    });
  }

  // ── Portapapeles ────────────────────────────────────────────────────────
  try {
    const clipboard = navigator.clipboard;
    if (clipboard && typeof clipboard.writeText === 'function') {
      const original = clipboard.writeText.bind(clipboard);
      clipboard.writeText = function (text) {
        try { notifyLink(text, 'clipboard.writeText'); } catch (e) { /* ignorado */ }
        return original(text);
      };
    }
  } catch (e) { /* sin permiso de portapapeles: se sigue con el evento copy */ }

  try {
    if (typeof document.execCommand === 'function') {
      const originalExec = document.execCommand.bind(document);
      document.execCommand = function (command) {
        try {
          if (String(command).toLowerCase() === 'copy') {
            const selection = window.getSelection();
            notifyLink(selection ? String(selection) : '', 'execCommand');
          }
        } catch (e) { /* ignorado */ }
        return originalExec.apply(document, arguments);
      };
    }
  } catch (e) { /* ignorado */ }

  document.addEventListener('copy', function (event) {
    try {
      const data = event.clipboardData;
      notifyLink(data ? data.getData('text/plain') : '', 'copy-event');
    } catch (e) { /* ignorado */ }
  }, true);

  // ── Red (diagnóstico) ───────────────────────────────────────────────────
  function summarizeJson(value, depth) {
    const level = depth || 0;
    if (value === null || value === undefined) return value;
    if (Array.isArray(value)) {
      return value.slice(0, 5).map(function (item) { return summarizeJson(item, level + 1); });
    }
    if (typeof value === 'object') {
      if (level >= 3) return '{…}';
      const out = {};
      let count = 0;
      for (const key in value) {
        if (!Object.prototype.hasOwnProperty.call(value, key)) continue;
        if (count++ >= 25) break;
        out[key] = summarizeJson(value[key], level + 1);
      }
      return out;
    }
    if (typeof value === 'string') return value.length > 300 ? value.slice(0, 300) + '…' : value;
    return value;
  }

  function describeBody(body) {
    if (body == null) return null;
    if (typeof body === 'string') return body.slice(0, 400);
    try {
      return JSON.stringify(body).slice(0, 400);
    } catch (e) {
      return null;
    }
  }

  function requestUrl(input) {
    try {
      if (typeof input === 'string') return input;
      if (input && typeof input.url === 'string') return input.url;
    } catch (e) { /* ignorado */ }
    return '';
  }

  function requestMethod(input, init) {
    try {
      if (init && init.method) return String(init.method).toUpperCase();
      if (input && typeof input.method === 'string') return input.method.toUpperCase();
    } catch (e) { /* ignorado */ }
    return 'GET';
  }

  try {
    const originalFetch = window.fetch;
    if (typeof originalFetch === 'function') {
      window.fetch = function (input, init) {
        const promise = originalFetch.apply(this, arguments);
        try {
          const url = requestUrl(input);
          const method = requestMethod(input, init);
          // `fetch` devuelve una promesa: la respuesta llega después y hay que
          // inspeccionarla en su momento.
          promise.then(function (response) {
            try {
              const contentType = (response.headers && response.headers.get('content-type')) || '';
              if (contentType.indexOf('json') === -1) return;
              response.clone().json().then(function (json) {
                emit('suno-api', {
                  transport: 'fetch',
                  method: method,
                  url: String(url).slice(0, 400),
                  body: describeBody(init && init.body),
                  json: summarizeJson(json, 0)
                });
              }).catch(function () { /* cuerpo no JSON */ });
            } catch (e) { /* ignorado */ }
          }).catch(function () { /* la petición falló */ });
        } catch (e) { /* ignorado */ }
        return promise;
      };
    }
  } catch (e) { /* ignorado */ }

  try {
    const originalOpen = XMLHttpRequest.prototype.open;
    const originalSend = XMLHttpRequest.prototype.send;
    XMLHttpRequest.prototype.open = function (method, url) {
      this.__lunaMethod = String(method || 'GET').toUpperCase();
      this.__lunaUrl = String(url || '');
      return originalOpen.apply(this, arguments);
    };
    XMLHttpRequest.prototype.send = function (body) {
      const xhr = this;
      try {
        xhr.addEventListener('load', function () {
          try {
            const contentType = xhr.getResponseHeader('content-type') || '';
            if (contentType.indexOf('json') === -1) return;
            const json = JSON.parse(xhr.responseText);
            emit('suno-api', {
              transport: 'xhr',
              method: xhr.__lunaMethod,
              url: String(xhr.__lunaUrl).slice(0, 400),
              body: describeBody(body),
              json: summarizeJson(json, 0)
            });
          } catch (e) { /* cuerpo no JSON */ }
        });
      } catch (e) { /* ignorado */ }
      return originalSend.apply(this, arguments);
    };
  } catch (e) { /* ignorado */ }

  emit('hook-ready', { href: String(location.href).slice(0, 300) });
})();
