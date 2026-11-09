// Luna Fetch — background service worker
const LUNA_BASE = 'http://127.0.0.1:51234';

// Tiempo máximo de espera de la app de escritorio. Si no responde en este
// plazo, el motivo se le dice al usuario en vez de un genérico "no está
// abierto" que no distinguía entre app cerrada, error de red o error del
// propio servicio en segundo plano.
const LUNA_TIMEOUT_MS = 20000;
const HEALTH_TIMEOUT_MS = 5000;

/** Explica un fallo de red o de respuesta en lenguaje comprensible. */
function describeError (error, fallback) {
  const detail = error && error.message ? error.message : String(error || '');
  if (!detail) return fallback;
  return fallback + ' (' + detail + ')';
}

/** Traduce un fallo de conexión con la app de escritorio. */
function connectionError (error) {
  return describeError(
    error,
    'No se pudo conectar con Luna Fetch en ' + LUNA_BASE + '. ¿Está la aplicación abierta?'
  );
}

// ── Cookie sync: read YouTube/Google session cookies and push them
//    to the desktop app as a Netscape cookie file. The app persists them
//    to %TEMP%/luna_session_cookies.txt so yt-dlp can pass YouTube's
//    "Sign in to confirm you're not a bot" challenge.

function sanitizeValue (v) {
  return String(v == null ? '' : v).replace(/[\t\r\n]+/g, '');
}

function toNetscapeCookies (cookies) {
  const lines = [
    '# Netscape HTTP Cookie File',
    '# Generado por la extensión de Luna Fetch (no editar).',
    ''
  ];
  for (const c of cookies) {
    const domain = c.hostOnly ? c.domain : '.' + c.domain;
    const flag = c.hostOnly ? 'FALSE' : 'TRUE';
    const path = c.path || '/';
    const secure = c.secure ? 'TRUE' : 'FALSE';
    const expiry = Math.floor(c.expirationDate || 0);
    lines.push(
      [domain, flag, path, secure, expiry, c.name, sanitizeValue(c.value)].join('\t')
    );
  }
  return lines.join('\n');
}

// ── Suno: importación de listas
//    El importador corre en la pestaña de Suno ya autenticada; el service
//    worker solo inyecta los scripts y encola los enlaces públicos capturados.

// ── Cookies de la sesión, para que el escritorio lea la ficha real ────────
// Se envían en el propio POST de la descarga: el servidor las guarda en un
// fichero Netscape temporal y las usa tanto para `yt-dlp` como para leer la
// ficha de las canciones privadas de Suno.

async function cookiesForUrl (url) {
  let host = '';
  try { host = new URL(url).hostname.toLowerCase(); } catch (e) { return ''; }
  const domains = host.endsWith('suno.com')
    ? ['suno.com']
    : ['youtube.com', 'google.com'];
  const all = [];
  for (const domain of domains) {
    try {
      const list = await chrome.cookies.getAll({ domain });
      if (Array.isArray(list)) all.push(...list);
    } catch (e) {
      // permiso o dominio no disponible — se sigue sin cookies
    }
  }
  return all.length ? toNetscapeCookies(all) : '';
}

/**
 * Habla con la app de escritorio y devuelve el JSON de la respuesta.
 * Si la respuesta no es JSON válido, el error lo dice con el cuerpo que
 * llegó: antes ese fallo se traducía a "no está abierto" y no había forma
 * de saber qué pasaba.
 */
async function fetchJson (path, options) {
  const res = await fetch(LUNA_BASE + path, Object.assign({
    signal: AbortSignal.timeout(LUNA_TIMEOUT_MS)
  }, options || {}));
  const text = await res.text();
  let data;
  try {
    data = JSON.parse(text);
  } catch (e) {
    throw new Error('Luna Fetch respondió ' + res.status + ' con un cuerpo no válido: ' + text.slice(0, 120));
  }
  return data;
}

/** Comprobación de vida: ¿responde la app de escritorio? */
async function checkHealth () {
  try {
    const res = await fetch(LUNA_BASE + '/health', { signal: AbortSignal.timeout(HEALTH_TIMEOUT_MS) });
    if (!res.ok) {
      // La app contesta pero esta versión no tiene /health: está abierta.
      return { ok: true, legacy: true, response: { status: res.status } };
    }
    const data = await res.json();
    return { ok: true, response: data };
  } catch (e) {
    return { ok: false, error: connectionError(e) };
  }
}

async function postDownload (url, format, quality, downloadLyrics) {
  const body = { url: url, format: format || 'mp4' };
  if (quality) body.quality = quality;
  if (downloadLyrics) body.downloadLyrics = true;
  const cookies = await cookiesForUrl(url);
  if (cookies) body.cookies = cookies;
  return fetchJson('/download', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body)
  });
}

async function queueSunoUrl (url, format, downloadLyrics) {
  try {
    const data = await postDownload(url, format || 'mp3', '', downloadLyrics);
    return { ok: data.ok !== false, response: data };
  } catch (e) {
    return { ok: false, error: connectionError(e) };
  }
}

/**
 * Encola varios enlaces de una vez (Fase 3 del importador de Suno).
 * Una sola ida y vuelta al servidor local en lugar de N.
 */
async function queueSunoBatch (urls, format, downloadLyrics) {
  const list = (urls || []).filter((url) => typeof url === 'string' && url.trim());
  if (!list.length) return { ok: false, error: 'No hay enlaces para encolar.' };
  try {
    const body = { urls: list, format: format || 'mp3' };
    if (downloadLyrics) body.downloadLyrics = true;
    const cookies = await cookiesForUrl(list[0]);
    if (cookies) body.cookies = cookies;
    const res = await fetch(`${LUNA_BASE}/download/batch`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
      signal: AbortSignal.timeout(LUNA_TIMEOUT_MS)
    });
    const data = await res.json();
    return { ok: data.ok !== false, response: data };
  } catch (e) {
    return { ok: false, error: connectionError(e) };
  }
}

/**
 * Conserva lo que el hook descubrió en la web de Suno (endpoint real de
 * «compartir», forma de la respuesta). Sirve para el acelerador por API y
 * para calibrar los selectores; nunca sale del equipo.
 */
function saveDiscovery (payload) {
  return new Promise((resolve) => {
    try {
      chrome.storage.local.get({ sunoDiscoveries: [] }, (result) => {
        const previous = Array.isArray(result.sunoDiscoveries) ? result.sunoDiscoveries : [];
        const entry = Object.assign({ at: Date.now() }, payload || {});
        const key = entry.method + ' ' + entry.url;
        const filtered = previous.filter((item) => (item.method + ' ' + item.url) !== key);
        filtered.unshift(entry);
        chrome.storage.local.set({ sunoDiscoveries: filtered.slice(0, 30) }, () => resolve({ ok: true }));
      });
    } catch (e) {
      resolve({ ok: false, error: String(e && e.message ? e.message : e) });
    }
  });
}

async function injectSunoScripts (tabId) {
  // El orquestador vive en el mundo aislado; el hook del portapapeles y de la
  // red necesita el mundo de la página para poder observar lo que copia Suno.
  // `suno-links.js` acompaña a cada uno: define la forma de los enlaces.
  await chrome.scripting.executeScript({ target: { tabId }, files: ['suno-links.js', 'suno.js'] });
  await chrome.scripting.executeScript({ target: { tabId }, world: 'MAIN', files: ['suno-links.js', 'suno-hook.js'] });
}

async function sunoTab () {
  const tabs = await chrome.tabs.query({});
  return tabs.find((tab) => tab.url && /^https?:\/\/([a-z0-9-]+\.)*suno\.com\//i.test(tab.url)) || null;
}

async function syncCookies () {
  try {
    const domains = ['youtube.com', 'google.com', 'suno.com'];
    const all = [];
    for (const d of domains) {
      try {
        const list = await chrome.cookies.getAll({ domain: d });
        if (Array.isArray(list)) all.push(...list);
      } catch (e) {
        // permission or domain not available — skip silently
      }
    }
    if (all.length === 0) return { ok: false, reason: 'no-cookies' };

    const text = toNetscapeCookies(all);
    const res = await fetch(`${LUNA_BASE}/cookies`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ cookies: text }),
      signal: AbortSignal.timeout(LUNA_TIMEOUT_MS)
    });
    return { ok: res.ok, count: all.length };
  } catch (e) {
    // Luna Fetch app is not running — silently ignore.
    return { ok: false, reason: 'app-offline' };
  }
}

chrome.runtime.onMessage.addListener((message, _sender, sendResponse) => {
  if (message.action === 'syncCookies') {
    syncCookies().then((r) => sendResponse(r));
    return true;
  }

  // Comprobación de vida: la extensión pregunta si la app está en marcha
  // antes de enviar nada, y así distingue "app cerrada" de otros fallos.
  if (message.action === 'health') {
    checkHealth().then((r) => sendResponse(r));
    return true;
  }

  if (message.action === 'analyze') {
    fetchJson('/analyze?url=' + encodeURIComponent(message.url), { method: 'GET' })
      .then((data) => sendResponse(data))
      .catch((e) => sendResponse({ ok: false, error: connectionError(e) }));
    return true;
  }

  if (message.action === 'download') {
    const format = message.format || 'mp4';
    const quality = message.quality || '';
    postDownload(message.url, format, quality)
      .then((data) => sendResponse({ ok: data.ok !== false, response: data }))
      .catch((e) => sendResponse({ ok: false, error: connectionError(e) }));
    return true;
  }

  // ── Suno ────────────────────────────────────────────────────────────────

  if (message.action === 'sunoQueue') {
    queueSunoUrl(message.url, message.format, message.downloadLyrics).then((r) => sendResponse(r));
    return true;
  }

  if (message.action === 'sunoQueueBatch') {
    queueSunoBatch(message.urls, message.format, message.downloadLyrics).then((r) => sendResponse(r));
    return true;
  }

  if (message.action === 'sunoCapture') {
    saveDiscovery(message.payload).then((r) => sendResponse(r));
    return true;
  }

  if (message.action === 'sunoStartImport') {
    (async () => {
      try {
        const tab = message.tabId
          ? await chrome.tabs.get(message.tabId)
          : await sunoTab();
        if (!tab || !tab.id) {
          sendResponse({ ok: false, error: 'No hay ninguna pestaña de Suno abierta.' });
          return;
        }
        await chrome.tabs.update(tab.id, { active: true });
        await injectSunoScripts(tab.id);
        await chrome.tabs.sendMessage(tab.id, {
          action: 'startSunoImport',
          format: message.format || 'mp3',
          mode: message.mode || 'links',
          downloadLyrics: message.downloadLyrics === true
        });
        sendResponse({ ok: true });
      } catch (e) {
        sendResponse({ ok: false, error: 'No se pudo iniciar en la pestaña de Suno: ' + (e && e.message ? e.message : e) });
      }
    })();
    return true;
  }

  if (message.action === 'sunoStopImport') {
    (async () => {
      try {
        const tab = await sunoTab();
        if (tab && tab.id) {
          await chrome.tabs.sendMessage(tab.id, { action: 'stopSunoImport' });
        }
        sendResponse({ ok: true });
      } catch (e) {
        sendResponse({ ok: false, error: String(e && e.message ? e.message : e) });
      }
    })();
    return true;
  }

  if (message.action === 'sunoShowPanel') {
    (async () => {
      try {
        const tab = await sunoTab();
        if (!tab || !tab.id) {
          sendResponse({ ok: false, error: 'No hay ninguna pestaña de Suno abierta.' });
          return;
        }
        await injectSunoScripts(tab.id);
        await chrome.tabs.sendMessage(tab.id, { action: 'showSunoPanel' });
        sendResponse({ ok: true });
      } catch (e) {
        sendResponse({ ok: false, error: String(e && e.message ? e.message : e) });
      }
    })();
    return true;
  }

  return false;
});