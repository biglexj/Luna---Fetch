// Luna Fetch — popup script
const sendBtn   = document.getElementById('send');
const urlInput  = document.getElementById('url');
const statusEl  = document.getElementById('status');
const fmtBtns   = document.querySelectorAll('.fmt-btn');

let selectedFmt = 'mp4';

function setStatus (text, color) {
  statusEl.style.color = color;
  statusEl.textContent = text;
}

async function send () {
  const url = urlInput.value.trim();
  if (!url) {
    setStatus('Pega una URL primero.', '#ff6b6b');
    urlInput.focus();
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
      format: selectedFmt,
      quality: ''
    });
    if (resp && resp.ok !== false) {
      const data = (resp.response && typeof resp.response === 'object') ? resp.response : {};
      const pending = typeof data.pending === 'number' ? data.pending : 0;
      if (data.queued === false) {
        const duplicate = data.reason === 'duplicate_queue'
          ? '⏭️ Ese enlace ya está en la cola de Luna Fetch.'
          : '✅ Ese video ya está descargado.';
        setStatus(duplicate, '#f4a261');
      } else {
        setStatus(
          pending > 1
            ? `✅ Encolado · ${pending} en espera. Sigue pegando el siguiente enlace.`
            : '✅ Encolado en Luna Fetch. Puedes pegar el siguiente enlace.',
          '#2ec4a3'
        );
      }
      urlInput.value = '';
      urlInput.focus();
    } else {
      setStatus(resp && resp.error ? resp.error : 'No se pudo enviar.', '#ff6b6b');
    }
  } catch (e) {
    setStatus('Luna Fetch no está abierto en tu PC.', '#ff6b6b');
  } finally {
    sendBtn.disabled = false;
    sendBtn.textContent = prevLabel;
  }
}

sendBtn.addEventListener('click', send);
urlInput.addEventListener('keydown', (e) => { if (e.key === 'Enter') send(); });

// ── Suno ────────────────────────────────────────────────────────────────
// La importación publica en lote las canciones de la lista abierta de Suno y
// encola sus enlaces públicos. El autoenvío captura cualquier enlace público
// de Suno que se copie dentro de la web.

const sunoImportBtn = document.getElementById('suno-import');
const sunoCopyBtn   = document.getElementById('suno-copy');
const sunoAutoBox   = document.getElementById('suno-auto');
const sunoFmtBtns   = document.querySelectorAll('[data-sfmt]');

/**
 * Extrae el enlace de Suno del portapapeles.
 * La forma del enlace la define `suno-links.js`, cargado antes que este
 * script en el popup: una sola fuente de verdad para popup e importador.
 */
function extractSunoLink(text) {
  if (!window.SunoLinks) return null;
  return window.SunoLinks.extract(text);
}

let sunoFmt = 'mp3';

sunoFmtBtns.forEach((b) => {
  b.addEventListener('click', () => {
    sunoFmtBtns.forEach((x) => x.classList.remove('selected'));
    b.classList.add('selected');
    sunoFmt = b.dataset.sfmt;
  });
});

try {
  chrome.storage.local.get({ sunoAutoLinks: false }, (result) => {
    sunoAutoBox.checked = !!result.sunoAutoLinks;
  });
} catch (e) { /* sin almacenamiento */ }

sunoAutoBox.addEventListener('change', () => {
  try {
    chrome.storage.local.set({ sunoAutoLinks: sunoAutoBox.checked });
  } catch (e) { /* sin almacenamiento */ }
});

sunoImportBtn.addEventListener('click', async () => {
  sunoImportBtn.disabled = true;
  setStatus('Abriendo el importador de Suno…', '#aaa');
  try {
    const resp = await chrome.runtime.sendMessage({ action: 'sunoStartImport', format: sunoFmt });
    if (resp && resp.ok !== false) {
      setStatus('✅ Importador abierto en la pestaña de Suno.', '#2ec4a3');
      window.close();
    } else {
      setStatus((resp && resp.error) || 'No se pudo abrir el importador.', '#ff6b6b');
      sunoImportBtn.disabled = false;
    }
  } catch (e) {
    setStatus('No se pudo abrir el importador.', '#ff6b6b');
    sunoImportBtn.disabled = false;
  }
});

sunoCopyBtn.addEventListener('click', async () => {
  setStatus('Leyendo el portapapeles…', '#aaa');
  let text = '';
  try {
    text = await navigator.clipboard.readText();
  } catch (e) {
    setStatus('El navegador no dejó leer el portapapeles.', '#ff6b6b');
    return;
  }
  const link = extractSunoLink(text || '');
  if (!link) {
    setStatus('El portapapeles no contiene un enlace de Suno.', '#f4a261');
    return;
  }
  sunoCopyBtn.disabled = true;
  try {
    const resp = await chrome.runtime.sendMessage({
      action: 'sunoQueue',
      url: link,
      format: sunoFmt
    });
    if (resp && resp.ok !== false) {
      setStatus('✅ Enlace de Suno encolado.', '#2ec4a3');
    } else {
      setStatus((resp && resp.error) || 'No se pudo encolar.', '#ff6b6b');
    }
  } catch (e) {
    setStatus('Luna Fetch no está abierto en tu PC.', '#ff6b6b');
  } finally {
    sunoCopyBtn.disabled = false;
  }
});

fmtBtns.forEach((b) => {
  b.addEventListener('click', () => {
    fmtBtns.forEach((x) => x.classList.remove('selected'));
    b.classList.add('selected');
    selectedFmt = b.dataset.fmt;
  });
});

// Auto-rellenar con la URL de la pestaña activa al abrir el popup.
try {
  chrome.tabs.query({ active: true, currentWindow: true }, (tabs) => {
    const tab = tabs && tabs[0];
    if (tab && tab.url && /^https?:\/\//.test(tab.url)) {
      urlInput.value = tab.url;
      urlInput.select();
    }
  });
} catch (e) { /* tabs API no disponible */ }

urlInput.focus();
