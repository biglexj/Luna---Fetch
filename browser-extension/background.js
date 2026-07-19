// Luna Fetch — background service worker
const LUNA_BASE = 'http://127.0.0.1:51234';

chrome.runtime.onMessage.addListener((message, _sender, sendResponse) => {
  if (message.action === 'analyze') {
    fetch(`${LUNA_BASE}/analyze?url=${encodeURIComponent(message.url)}`)
      .then((res) => res.json())
      .then((data) => sendResponse(data))
      .catch((err) => sendResponse({ ok: false, error: 'Luna Fetch no está abierto en tu PC.' }));
    return true;
  }

  if (message.action === 'download') {
    const format = message.format || 'mp4';
    const quality = message.quality || '';
    fetch(`${LUNA_BASE}/download?url=${encodeURIComponent(message.url)}&format=${encodeURIComponent(format)}&quality=${encodeURIComponent(quality)}`)
      .then((res) => res.json())
      .then((data) => sendResponse({ ok: data.ok !== false, response: data }))
      .catch((err) => sendResponse({ ok: false, error: 'Luna Fetch no está abierto en tu PC.' }));
    return true;
  }

  return false;
});
