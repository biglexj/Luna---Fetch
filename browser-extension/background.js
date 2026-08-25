// Luna Fetch — background service worker
const LUNA_BASE = 'http://127.0.0.1:51234';

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

async function syncCookies () {
  try {
    const domains = ['youtube.com', 'google.com'];
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
      body: JSON.stringify({ cookies: text })
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

  if (message.action === 'analyze') {
    fetch(`${LUNA_BASE}/analyze?url=${encodeURIComponent(message.url)}`)
      .then((res) => res.json())
      .then((data) => sendResponse(data))
      .catch(() => sendResponse({ ok: false, error: 'Luna Fetch no está abierto en tu PC.' }));
    return true;
  }

  if (message.action === 'download') {
    const format = message.format || 'mp4';
    const quality = message.quality || '';
    fetch(`${LUNA_BASE}/download?url=${encodeURIComponent(message.url)}&format=${encodeURIComponent(format)}&quality=${encodeURIComponent(quality)}`)
      .then((res) => res.json())
      .then((data) => sendResponse({ ok: data.ok !== false, response: data }))
      .catch(() => sendResponse({ ok: false, error: 'Luna Fetch no está abierto en tu PC.' }));
    return true;
  }

  return false;
});