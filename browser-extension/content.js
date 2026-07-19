// Luna Fetch — YouTube content script
// Injects the Luna Fetch download button into YouTube's action bar.

(function () {
  'use strict';

  const LUNA_BASE = 'http://127.0.0.1:51234';

  // ── Styles ──────────────────────────────────────────────────────────────────
  const STYLE = `
    #luna-fetch-btn {
      display: inline-flex; align-items: center; gap: 6px;
      padding: 0 16px; height: 36px; border: none; border-radius: 18px;
      background: rgba(255, 255, 255, 0.1);
      color: #f1f1f1; font-size: 14px; font-weight: 500; letter-spacing: .2px;
      cursor: pointer; margin-left: 8px; flex-shrink: 0;
      font-family: "Roboto","YouTube Sans",sans-serif;
      transition: background-color 0.2s ease, color 0.2s ease, transform 0.15s ease;
    }
    #luna-fetch-btn:hover {
      background: rgba(26, 138, 110, 0.85);
      color: #ffffff;
      transform: translateY(-1px);
    }
    #luna-fetch-btn.luna-active-btn {
      background: #1a8a6e;
      color: #ffffff;
    }
    #luna-fetch-btn svg { width: 16px; height: 16px; flex-shrink: 0; fill: currentColor; }

    #luna-popup {
      position: fixed; z-index: 999999;
      background: #1a1b26; border: 1px solid rgba(255, 255, 255, 0.12);
      border-radius: 16px; box-shadow: 0 10px 30px rgba(0, 0, 0, 0.6);
      width: 250px; overflow: hidden;
      font-family: "Roboto", sans-serif;
      animation: lunaFadeIn 0.18s ease-out;
    }
    @keyframes lunaFadeIn {
      from { opacity: 0; transform: translateY(-4px) scale(0.98); }
      to   { opacity: 1; transform: translateY(0) scale(1); }
    }
    .luna-popup-inner { padding: 14px 16px; }
    .luna-popup-title {
      color: #ffffff; font-weight: 600; font-size: 14px; margin-bottom: 12px;
      display: flex; align-items: center; gap: 8px;
    }
    .luna-popup-row { display: flex; gap: 8px; margin-bottom: 12px; }
    .luna-fmt-btn {
      flex: 1; padding: 8px 6px; border: 1px solid rgba(255, 255, 255, 0.15); border-radius: 10px;
      background: rgba(255, 255, 255, 0.05); color: #aaa; cursor: pointer; font-size: 13px;
      font-family: inherit; font-weight: 500; transition: all 0.15s ease;
      display: flex; align-items: center; justify-content: center; gap: 4px;
    }
    .luna-fmt-btn:hover { background: rgba(255, 255, 255, 0.12); color: #fff; }
    .luna-fmt-btn.luna-selected {
      border-color: #2ec4a3; background: rgba(26, 138, 110, 0.25); color: #2ec4a3; font-weight: 600;
    }
    .luna-quality-box { margin-bottom: 12px; }
    .luna-quality-label { display: block; font-size: 12px; color: #888; margin-bottom: 4px; font-weight: 500; }
    .luna-quality-select {
      width: 100%; padding: 8px; border: 1px solid rgba(255, 255, 255, 0.15); border-radius: 10px;
      background: #222336; color: #fff; font-size: 13px; font-family: inherit; outline: none;
      cursor: pointer;
    }
    .luna-send-btn {
      width: 100%; padding: 10px; border: none; border-radius: 10px;
      background: #1a8a6e; color: #ffffff; font-weight: 600; font-size: 14px; cursor: pointer;
      font-family: inherit; transition: background 0.18s ease, transform 0.1s ease;
      box-shadow: 0 4px 12px rgba(26, 138, 110, 0.3);
    }
    .luna-send-btn:hover  { background: #22a383; }
    .luna-send-btn:active { transform: scale(0.98); }
    .luna-status {
      text-align: center; margin-top: 8px; font-size: 12px;
      color: #aaa; min-height: 16px; word-break: break-word;
    }
  `;

  function injectStyles () {
    if (document.getElementById('luna-styles')) return;
    const el = document.createElement('style');
    el.id = 'luna-styles';
    el.textContent = STYLE;
    document.head.appendChild(el);
  }

  // ── SVG Icon (Luna Fetch Logo) ───────────────────────────────────────────────
  const ICON_SVG = `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
    <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
    <polyline points="7 10 12 15 17 10"/>
    <line x1="12" y1="15" x2="12" y2="3"/>
  </svg>`;

  // ── Popup management ─────────────────────────────────────────────────────────
  let activePopup = null;
  let currentAnchorBtn = null;
  let scrollHandler = null;

  function closePopup () {
    if (activePopup) {
      activePopup.remove();
      activePopup = null;
    }
    if (currentAnchorBtn) {
      currentAnchorBtn.classList.remove('luna-active-btn');
      currentAnchorBtn = null;
    }
    if (scrollHandler) {
      window.removeEventListener('scroll', scrollHandler, true);
      window.removeEventListener('resize', scrollHandler);
      scrollHandler = null;
    }
  }

  function updatePopupPosition (anchorBtn, popup) {
    if (!anchorBtn || !popup) return;
    const rect = anchorBtn.getBoundingClientRect();
    const popupWidth = 250;
    
    let left = rect.left;
    if (left + popupWidth > window.innerWidth - 16) {
      left = window.innerWidth - popupWidth - 16;
    }

    let top = rect.bottom + 8;
    if (top + 220 > window.innerHeight) {
      top = rect.top - 220 - 8;
    }

    popup.style.top = `${Math.max(8, top)}px`;
    popup.style.left = `${Math.max(8, left)}px`;
  }

  function showPopup (anchorBtn, url) {
    if (activePopup) {
      closePopup();
      return;
    }

    currentAnchorBtn = anchorBtn;
    anchorBtn.classList.add('luna-active-btn');

    const popup = document.createElement('div');
    popup.id = 'luna-popup';
    popup.innerHTML = `
      <div class="luna-popup-inner">
        <div class="luna-popup-title">${ICON_SVG} <span>Luna Fetch</span></div>
        <div class="luna-popup-row">
          <button class="luna-fmt-btn luna-selected" data-fmt="mp4">🎬 Video MP4</button>
          <button class="luna-fmt-btn"          data-fmt="mp3">🎵 Audio MP3</button>
        </div>
        <div class="luna-quality-box">
          <label class="luna-quality-label">Calidad</label>
          <select class="luna-quality-select" disabled>
            <option value="">Analizando enlace…</option>
          </select>
        </div>
        <button class="luna-send-btn" disabled style="opacity: 0.6; cursor: not-allowed;">Analizando…</button>
        <div class="luna-status"></div>
      </div>`;

    document.body.appendChild(popup);
    activePopup = popup;

    updatePopupPosition(anchorBtn, popup);

    scrollHandler = () => updatePopupPosition(anchorBtn, popup);
    window.addEventListener('scroll', scrollHandler, true);
    window.addEventListener('resize', scrollHandler);

    let selectedFmt = 'mp4';
    let videoQualities = [];
    let audioQualities = [];

    const selectEl = popup.querySelector('.luna-quality-select');
    const sendBtn = popup.querySelector('.luna-send-btn');
    const statusEl = popup.querySelector('.luna-status');

    function populateQualities () {
      selectEl.innerHTML = '';
      const list = selectedFmt === 'mp4' ? videoQualities : audioQualities;
      if (!list || list.length === 0) {
        selectEl.innerHTML = '<option value="">Máxima disponible</option>';
      } else {
        list.forEach((q) => {
          const opt = document.createElement('option');
          opt.value = q.id;
          opt.textContent = q.label;
          selectEl.appendChild(opt);
        });
      }
      selectEl.disabled = false;
      sendBtn.disabled = false;
      sendBtn.textContent = 'Descargar';
      sendBtn.style.opacity = '1';
      sendBtn.style.cursor = 'pointer';
    }

    // Direct fetch from content script to Luna Fetch server
    fetch(`${LUNA_BASE}/analyze?url=${encodeURIComponent(url)}`)
      .then((res) => res.json())
      .then((resp) => {
        if (!activePopup) return;
        if (resp && resp.ok) {
          videoQualities = resp.videoQualities || [];
          audioQualities = resp.audioQualities || [];
        } else {
          selectEl.innerHTML = '<option value="">Máxima disponible</option>';
        }
        populateQualities();
      })
      .catch(() => {
        if (!activePopup) return;
        selectEl.innerHTML = '<option value="">Máxima disponible</option>';
        populateQualities();
      });

    popup.querySelectorAll('.luna-fmt-btn').forEach((b) => {
      b.addEventListener('click', () => {
        popup.querySelectorAll('.luna-fmt-btn').forEach((x) => x.classList.remove('luna-selected'));
        b.classList.add('luna-selected');
        selectedFmt = b.dataset.fmt;
        populateQualities();
      });
    });

    popup.querySelector('.luna-send-btn').addEventListener('click', () => {
      const qualityVal = selectEl.value;
      statusEl.style.color = '#2ec4a3';
      statusEl.textContent = 'Enviando a Luna Fetch…';

      fetch(`${LUNA_BASE}/download?url=${encodeURIComponent(url)}&format=${encodeURIComponent(selectedFmt)}&quality=${encodeURIComponent(qualityVal)}`)
        .then((res) => res.json())
        .then((resp) => {
          if (resp && resp.ok !== false) {
            statusEl.style.color = '#2ec4a3';
            statusEl.textContent = '✅ ¡Descarga iniciada!';
            setTimeout(closePopup, 1600);
          } else {
            statusEl.style.color = '#ff6b6b';
            statusEl.textContent = '❌ Error al iniciar la descarga';
          }
        })
        .catch(() => {
          statusEl.style.color = '#ff6b6b';
          statusEl.textContent = '❌ Luna Fetch no está abierto en tu PC.';
        });
    });

    // Dismiss on outside click
    setTimeout(() => {
      document.addEventListener('click', function outside (e) {
        if (activePopup && !activePopup.contains(e.target) && !anchorBtn.contains(e.target)) {
          closePopup();
          document.removeEventListener('click', outside);
        }
      });
    }, 100);
  }

  // ── Button injection ─────────────────────────────────────────────────────────
  function findActionsRow () {
    return (
      document.querySelector('#actions-inner #top-level-buttons-computed') ||
      document.querySelector('#top-level-buttons-computed') ||
      document.querySelector('#flexible-item-buttons') ||
      document.querySelector('#menu-container #buttons')
    );
  }

  function injectLunaButton () {
    const actionsRow = findActionsRow();
    if (!actionsRow) return;

    if (document.getElementById('luna-fetch-btn')) return;

    injectStyles();

    const btn = document.createElement('button');
    btn.id = 'luna-fetch-btn';
    btn.title = 'Descargar con Luna Fetch';
    btn.innerHTML = `${ICON_SVG} <span>Luna</span>`;

    btn.addEventListener('click', (e) => {
      e.stopPropagation();
      const currentUrl = window.location.href;
      showPopup(btn, currentUrl);
    });

    actionsRow.appendChild(btn);
  }

  // Observe YouTube SPA navigation
  const observer = new MutationObserver(() => injectLunaButton());
  observer.observe(document.body, { childList: true, subtree: true });

  injectLunaButton();
})();
