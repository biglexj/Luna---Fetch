// Luna Fetch — popup script
const sendBtn        = document.getElementById('send');
const urlInput       = document.getElementById('url');
const statusEl       = document.getElementById('status');
const fmtSelect      = document.getElementById('fmt');
const brandTitle     = document.getElementById('brand-title');
const brandSubtitle  = document.getElementById('brand-subtitle');
const generalSection = document.getElementById('general-section');
const generalActions = document.getElementById('general-actions');
const sunoSection    = document.getElementById('suno-section');
const sunoActions    = document.getElementById('suno-actions');
const sunoModeSelect = document.getElementById('suno-mode');
const sunoLyricsCheck = document.getElementById('suno-lyrics');
const sunoImportBtn  = document.getElementById('suno-import');
const sunoStopBtn    = document.getElementById('suno-stop');
const sunoCopyBtn    = document.getElementById('suno-copy');

function setStatus(text, color) {
  if (!statusEl) return;
  statusEl.style.color = color || '#cdd6f4';
  statusEl.textContent = text;
}

// ── Envío general (YouTube, etc.) ─────────────────────────────────────────

async function send() {
  if (!sendBtn || sendBtn.disabled) return;
  const url = urlInput ? urlInput.value.trim() : '';
  if (!url) {
    setStatus('Pega una URL primero.', '#ff6b6b');
    if (urlInput) urlInput.focus();
    return;
  }

  sendBtn.disabled = true;
  const prevLabel = sendBtn.textContent;
  sendBtn.textContent = 'Enviando…';
  setStatus('', '#aaa');

  try {
    const resp = await chrome.runtime.sendMessage({
      action: 'download',
      url,
      format: fmtSelect ? fmtSelect.value : 'mp4',
      quality: ''
    });
    if (resp && resp.ok !== false) {
      const data = (resp.response && typeof resp.response === 'object') ? resp.response : {};
      const pending = typeof data.pending === 'number' ? data.pending : 0;
      if (data.queued === false) {
        const duplicate = data.reason === 'duplicate_queue'
          ? '⏭️ Ese enlace ya está en la cola de Luna Fetch.'
          : '✅ Ese archivo ya está descargado.';
        setStatus(duplicate, '#f4a261');
      } else {
        setStatus(
          pending > 1
            ? `✅ Encolado · ${pending} en espera.`
            : '✅ Encolado en Luna Fetch.',
          '#2ec4a3'
        );
      }
      setTimeout(() => { window.close(); }, 900);
    } else {
      setStatus((resp && resp.error) || 'No se pudo enviar.', '#ff6b6b');
    }
  } catch (e) {
    setStatus(
      'La extensión no pudo contactar con su servicio en segundo plano: ' +
      (e && e.message ? e.message : e),
      '#ff6b6b'
    );
  } finally {
    sendBtn.disabled = false;
    sendBtn.textContent = prevLabel;
  }
}

if (sendBtn) sendBtn.addEventListener('click', send);
if (urlInput) urlInput.addEventListener('keydown', (e) => { if (e.key === 'Enter') send(); });

// ── Acciones Suno ────────────────────────────────────────────────────────

if (sunoImportBtn) {
  sunoImportBtn.addEventListener('click', async () => {
    sunoImportBtn.disabled = true;
    setStatus('⏳ Iniciando importación en la lista de Suno…', '#2ec4a3');
    try {
      const mode = (sunoModeSelect && sunoModeSelect.value) || 'links';
      const format = (fmtSelect && fmtSelect.value) || 'mp3';
      const downloadLyrics = !!(sunoLyricsCheck && sunoLyricsCheck.checked);
      const resp = await chrome.runtime.sendMessage({
        action: 'sunoStartImport',
        format,
        mode,
        downloadLyrics
      });
      if (resp && resp.ok !== false) {
        setStatus('📥 Capturando lista abierta y enviando a la cola…', '#2ec4a3');
      } else {
        setStatus((resp && resp.error) || 'No se pudo iniciar la importación.', '#ff6b6b');
        sunoImportBtn.disabled = false;
      }
    } catch (e) {
      setStatus('No se pudo iniciar la importación.', '#ff6b6b');
      sunoImportBtn.disabled = false;
    }
  });
}

if (sunoStopBtn) {
  sunoStopBtn.addEventListener('click', async () => {
    setStatus('⏹️ Deteniendo importación de Suno…', '#f4a261');
    try {
      await chrome.runtime.sendMessage({ action: 'sunoStopImport' });
      setStatus('⏹️ Importación detenida.', '#f4a261');
      if (sunoImportBtn) sunoImportBtn.disabled = false;
    } catch (e) {
      setStatus('No se pudo enviar la orden de detención.', '#ff6b6b');
    }
  });
}

if (sunoCopyBtn) {
  sunoCopyBtn.addEventListener('click', async () => {
    setStatus('Leyendo el portapapeles…', '#aaa');
    let text = '';
    try {
      text = await navigator.clipboard.readText();
    } catch (e) {
      setStatus('El navegador no dejó leer el portapapeles.', '#ff6b6b');
      return;
    }
    const link = window.SunoLinks ? window.SunoLinks.extract(text || '') : null;
    if (!link) {
      setStatus('El portapapeles no contiene un enlace de Suno.', '#f4a261');
      return;
    }
    sunoCopyBtn.disabled = true;
    try {
      const resp = await chrome.runtime.sendMessage({
        action: 'sunoQueue',
        url: link,
        format: fmtSelect ? fmtSelect.value : 'mp3',
        downloadLyrics: !!(sunoLyricsCheck && sunoLyricsCheck.checked)
      });
      if (resp && resp.ok !== false) {
        setStatus('✅ Enlace de Suno encolado.', '#2ec4a3');
      } else {
        setStatus((resp && resp.error) || 'No se pudo encolar.', '#ff6b6b');
      }
    } catch (e) {
      setStatus('Error al enviar enlace copiado.', '#ff6b6b');
    } finally {
      sunoCopyBtn.disabled = false;
    }
  });
}

// Escucha mensajes de progreso en vivo desde la pestaña de Suno
chrome.runtime.onMessage.addListener((message) => {
  if (message && message.action === 'sunoStatusUpdate') {
    setStatus(message.status, message.running ? '#2ec4a3' : '#cdd6f4');
    if (!message.running && sunoImportBtn) {
      sunoImportBtn.disabled = false;
    }
  }
});

// ── Detección contextual de la pestaña activa ─────────────────────────────

try {
  chrome.tabs.query({ active: true, currentWindow: true }, (tabs) => {
    const tab = tabs && tabs[0];
    const url = (tab && tab.url) || '';
    const isSuno = url.includes('suno.com');

    if (isSuno) {
      if (brandTitle) brandTitle.textContent = 'Luna Fetch · Suno';
      if (brandSubtitle) brandSubtitle.textContent = 'Importa y envía canciones a tu cola';
      if (generalSection) generalSection.style.display = 'none';
      if (generalActions) generalActions.style.display = 'none';
      if (sunoSection) sunoSection.style.display = 'block';
      if (sunoActions) sunoActions.style.display = 'block';
      // Por defecto sugerir MP3 para Suno
      if (fmtSelect && (fmtSelect.value === 'mp4' || !fmtSelect.value)) {
        fmtSelect.value = 'mp3';
      }
    } else {
      if (brandTitle) brandTitle.textContent = 'Luna Fetch';
      if (brandSubtitle) brandSubtitle.textContent = 'Envía un enlace a tu escritorio';
      if (generalSection) generalSection.style.display = 'block';
      if (generalActions) generalActions.style.display = 'block';
      if (sunoSection) sunoSection.style.display = 'none';
      if (sunoActions) sunoActions.style.display = 'none';
      if (url && /^https?:\/\//.test(url) && urlInput) {
        urlInput.value = url;
        urlInput.select();
      }
    }
  });
} catch (e) { /* tabs API no disponible */ }

// ── Estado de conexión y cola ─────────────────────────────────────────────

async function refreshConnection() {
  try {
    const resp = await chrome.runtime.sendMessage({ action: 'health' });
    if (resp && resp.ok) {
      const pending = resp.response && resp.response.pending;
      setStatus(
        '✅ Conectado con Luna Fetch' + (pending ? ` · ${pending} en cola` : ''),
        '#2ec4a3'
      );
    } else {
      setStatus('⚠️ ' + ((resp && resp.error) || 'Sin conexión con Luna Fetch.'), '#f4a261');
    }
  } catch (e) {
    setStatus(
      '⚠️ Sin conexión con Luna Fetch: asegúrate de tener la app abierta.',
      '#f4a261'
    );
  }
}

refreshConnection();
