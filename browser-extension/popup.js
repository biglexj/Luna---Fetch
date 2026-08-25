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
      setStatus('✅ Enviado a Luna Fetch.', '#2ec4a3');
      urlInput.value = '';
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