// Luna Fetch — importador de listas de Suno (content script)
//
// Recorre la lista abierta de un espacio de trabajo de Suno, publica cada
// canción que aún no lo esté, captura el enlace público (sin leer el
// portapapeles del sistema: se intercepta lo que la propia web copia) y lo
// encola en Luna Fetch.
//
// La sesión es la del navegador: no se gestiona ninguna credencial.
(function () {
  'use strict';

  if (window.__lunaSunoInjected) return;
  window.__lunaSunoInjected = true;

  const CHANNEL = 'luna-suno-hook';

  // Forma de los enlaces de Suno: la define `suno-links.js`, que se carga
  // antes que este script (manifiesto y popup). Una sola fuente de verdad.
  const Links = window.SunoLinks;
  if (!Links) {
    console.error('[Luna Fetch][Suno] falta suno-links.js: el importador no puede arrancar.');
    return;
  }

  // Los menús de Suno están en español o inglés según el idioma de la cuenta.
  const TEXT_PUBLISH = /hacer pública|hacer publico|make public|publicar|publish|público|public/i;
  const TEXT_SHARE = /compartir|share/i;
  const TEXT_COPY = /copiar enlace|copiar link|copiar url|copiar|copy link|copy url|copy/i;
  const TEXT_PUBLIC_BADGE = /publicada|published/i;

  /** Enlaces que se envían al servidor en una sola petición. */
  const BATCH_SIZE = 20;

  const state = {
    running: false,
    stopRequested: false,
    mode: 'publish', // 'publish' | 'dry' | 'links'
    format: 'mp3',
    diagnostics: false,
    seenIds: new Set(),
    processed: new WeakSet(),
    links: [],
    pendingQueue: [],
    pendingLink: null,
    apiLink: null
  };

  // ── Utilidades de DOM ───────────────────────────────────────────────────

  function isVisible(element) {
    if (!element || !element.isConnected) return false;
    if (element.hidden) return false;
    const style = window.getComputedStyle(element);
    if (!style) return false;
    if (style.display === 'none' || style.visibility === 'hidden') return false;
    if (parseFloat(style.opacity || '1') === 0) return false;
    return element.getClientRects().length > 0;
  }

  function ownText(element) {
    return (element.textContent || '').replace(/\s+/g, ' ').trim();
  }

  function click(element) {
    if (!element) return false;
    try {
      element.scrollIntoView({ block: 'center', inline: 'center' });
    } catch (e) { /* ignorado */ }
    try {
      element.click();
      return true;
    } catch (e) {
      return false;
    }
  }

  function isAfter(element, reference) {
    if (!reference || !reference.isConnected) return true;
    try {
      const position = reference.compareDocumentPosition(element);
      return !!(position & Node.DOCUMENT_POSITION_FOLLOWING);
    } catch (e) {
      return true;
    }
  }

  const CANDIDATE_SELECTOR = 'button, [role="button"], [role="menuitem"], [role="option"], [role="radio"], [role="switch"], a, li, label, span, div';

  /** Elementos visibles cuyo texto (o etiqueta) coincide con los patrones. */
  function collectCandidates(patterns) {
    const found = [];
    document.querySelectorAll(CANDIDATE_SELECTOR).forEach(function (element) {
      if (element.closest('[aria-hidden="true"]')) return;
      if (!isVisible(element)) return;
      const text = ownText(element);
      if (!text || text.length > 60) return;
      const aria = [
        element.getAttribute('aria-label') || '',
        element.getAttribute('title') || '',
        element.getAttribute('data-testid') || ''
      ].join(' ');
      const haystack = text + ' ' + aria;
      const matched = patterns.some(function (pattern) { return pattern.test(haystack); });
      if (!matched) return;
      found.push({ element: element, text: text, aria: aria });
    });
    return found;
  }

  function rank(candidate) {
    const tag = candidate.element.tagName;
    const role = candidate.element.getAttribute('role') || '';
    if (tag === 'BUTTON') return 0;
    if (role === 'menuitem' || role === 'button' || role === 'option' || role === 'radio' || role === 'switch') return 1;
    if (tag === 'A') return 2;
    if (tag === 'LI') return 3;
    return 4;
  }

  /**
   * Busca un elemento pulsable por texto.
   *
   * `exclude` descarta la propia fila (para no pulsar su insignia «Publicada»)
   * y `after` exige que el candidato aparezca después en el documento, que es
   * donde Suno monta el menú y el diálogo (portales al final del `body`).
   */
  function findItem(patterns, options) {
    const opts = options || {};
    const exclude = opts.exclude || null;
    const after = opts.after || null;
    const within = opts.within || null;

    let pool = collectCandidates(patterns);
    if (within) pool = pool.filter(function (candidate) { return within.contains(candidate.element); });
    if (exclude) pool = pool.filter(function (candidate) { return !exclude.contains(candidate.element); });

    if (after) {
      const filtered = pool.filter(function (candidate) { return isAfter(candidate.element, after); });
      if (filtered.length) pool = filtered;
    }

    if (!pool.length) return null;
    return pickBest(pool, opts);
  }

  /** Gana el candidato más interno (el botón, no el contenedor que lo envuelve). */
  function pickBest(pool, opts) {
    if (!pool.length) return null;
    const options = opts || {};
    const innermost = pool.filter(function (candidate) {
      return !pool.some(function (other) {
        return other !== candidate && candidate.element.contains(other.element);
      });
    });
    const usable = innermost.length ? innermost : pool;

    if (options.last) {
      return usable[usable.length - 1].element;
    }
    return usable.slice().sort(function (a, b) { return rank(a) - rank(b); })[0].element;
  }

  // ── Detección por diferencia del DOM ─────────────────────────────────────
  //
  // Pulsar `…` o «Compartir» hace aparecer nodos nuevos. En lugar de adivinar
  // la estructura de la interfaz, se compara el DOM antes y después: los nodos
  // nuevos son, por definición, el menú o el diálogo recién abiertos. El
  // método es agnóstico al framework que use Suno.

  function snapshotElements() {
    return new Set(Array.prototype.slice.call(document.querySelectorAll('*')));
  }

  function freshMatching(before, patterns) {
    const found = [];
    document.querySelectorAll(CANDIDATE_SELECTOR).forEach(function (element) {
      if (before.has(element)) return;
      if (!isVisible(element)) return;
      const text = ownText(element);
      if (!text || text.length > 60) return;
      const aria = [
        element.getAttribute('aria-label') || '',
        element.getAttribute('title') || '',
        element.getAttribute('data-testid') || ''
      ].join(' ');
      const haystack = text + ' ' + aria;
      if (!patterns.some(function (pattern) { return pattern.test(haystack); })) return;
      found.push({ element: element, text: text, aria: aria });
    });
    return found;
  }

  /** Elementos del menú o del diálogo recién abiertos que coinciden con los patrones. */
  function freshItem(before, patterns, options) {
    return pickBest(freshMatching(before, patterns), options);
  }

  /** Contenedor de overlay visible (menú, diálogo o lista). */
  function overlayScope(exclude) {
    const scopes = Array.prototype.slice.call(
      document.querySelectorAll('[role="menu"],[role="dialog"],[role="listbox"],[role="listmenu"],[data-radix-popper-content-wrapper]')
    );
    const visible = scopes.filter(function (scope) {
      if (!isVisible(scope)) return false;
      if (exclude && exclude.contains(scope)) return false;
      return true;
    });
    return visible.length ? visible[visible.length - 1] : null;
  }

  function waitFor(predicate, timeoutMs, intervalMs) {
    const timeout = timeoutMs || 2000;
    const interval = intervalMs || 80;
    const deadline = Date.now() + timeout;
    return new Promise(function (resolve) {
      (function tick() {
        let value = null;
        try { value = predicate(); } catch (e) { value = null; }
        if (value) return resolve(value);
        if (Date.now() >= deadline) return resolve(null);
        setTimeout(tick, interval);
      })();
    });
  }

  function sleep(ms) {
    return new Promise(function (resolve) { setTimeout(resolve, ms); });
  }

  // ── Enlaces ─────────────────────────────────────────────────────────────

  function extractSunoLink(text) {
    return Links.extract(text);
  }

  function canonicalize(link) {
    return Links.canonicalize(link);
  }

  /**
   * Resuelve un enlace corto `suno.com/s/<slug>` a su URL canónica.
   *
   * Se hace desde la propia página (con la sesión del navegador) para que el
   * escritorio reciba siempre `suno.com/song/<uuid>`, que es parseable sin
   * seguir redirecciones.
   */
  async function resolveCanonical(entry) {
    if (!entry || entry.id || !entry.slug) return entry;
    try {
      const controller = new AbortController();
      const timer = setTimeout(function () { controller.abort(); }, 8000);
      const response = await fetch(entry.url, { credentials: 'include', redirect: 'follow', signal: controller.signal });
      clearTimeout(timer);
      const canonical = canonicalize(response.url || '');
      if (canonical && canonical.id) return canonical;
    } catch (e) { /* se conserva el enlace corto */ }
    return entry;
  }

  // ── Puente con el service worker ────────────────────────────────────────

  function sendMessage(message) {
    return new Promise(function (resolve) {
      try {
        chrome.runtime.sendMessage(message, function (response) {
          void chrome.runtime.lastError;
          resolve(response || { ok: false, error: 'sin-respuesta' });
        });
      } catch (e) {
        resolve({ ok: false, error: String(e && e.message ? e.message : e) });
      }
    });
  }

  function queueUrl(url) {
    return sendMessage({ action: 'sunoQueue', url: url, format: state.format });
  }

  /** Lote de enlaces: una sola ida y vuelta al servidor en vez de N. */
  function queueUrls(urls) {
    return sendMessage({ action: 'sunoQueueBatch', urls: urls, format: state.format });
  }

  /**
   * Vacía el búfer de enlaces capturados hacia Luna Fetch.
   *
   * Se prefiere el envío por lotes (Fase 3). Si el servidor no lo soporta,
   * se degrada al envío enlace a enlace para no perder nada.
   */
  async function flushQueue() {
    if (!state.pendingQueue.length) return null;
    const urls = state.pendingQueue.splice(0, state.pendingQueue.length);

    const batch = await queueUrls(urls);
    if (batch && batch.ok !== false && batch.response) {
      const data = batch.response;
      const queued = typeof data.queued === 'number' ? data.queued : urls.length;
      const skipped = typeof data.skipped === 'number' ? data.skipped : 0;
      addLog('Lote enviado', queued + ' encolado(s)' + (skipped ? ', ' + skipped + ' ya estaban' : ''), 'ok');
      return data;
    }

    let sent = 0;
    for (const url of urls) {
      const response = await queueUrl(url);
      if (response && response.ok) sent += 1;
    }
    addLog('Lote enviado (uno a uno)', sent + '/' + urls.length + ' encolado(s)', sent === urls.length ? 'ok' : 'warn');
    return { ok: sent > 0, queued: sent };
  }

  function storageGet(key, fallback) {
    return new Promise(function (resolve) {
      try {
        chrome.storage.local.get({ [key]: fallback }, function (result) {
          void chrome.runtime.lastError;
          resolve((result && result[key]) !== undefined ? result[key] : fallback);
        });
      } catch (e) {
        resolve(fallback);
      }
    });
  }

  // ── Panel de control ────────────────────────────────────────────────────

  const ui = { root: null, list: null, status: null, button: null };

  function ensureUi() {
    if (ui.root && ui.root.isConnected) return ui.root;

    const root = document.createElement('div');
    root.id = 'luna-suno-panel';
    root.style.cssText = [
      'position:fixed', 'right:18px', 'bottom:70px', 'z-index:2147483000',
      'width:330px', 'max-width:92vw', 'font-family:system-ui,-apple-system,"Segoe UI",sans-serif',
      'background:#0f1214', 'color:#e6edf3', 'border:1px solid #263238', 'border-radius:14px',
      'box-shadow:0 12px 40px rgba(0,0,0,.55)', 'overflow:hidden'
    ].join(';');

    root.innerHTML = [
      '<div style="padding:12px 14px;display:flex;align-items:center;gap:8px;border-bottom:1px solid #263238">',
      '  <span style="font-weight:600;font-size:13px">Luna Fetch · Importar lista</span>',
      '  <button id="luna-suno-close" style="margin-left:auto;background:transparent;border:0;color:#8b949e;cursor:pointer;font-size:16px">×</button>',
      '</div>',
      '<div style="padding:12px 14px;display:flex;flex-direction:column;gap:8px">',
      '  <label style="display:flex;gap:6px;align-items:center;font-size:12px">',
      '    Modo ',
      '    <select id="luna-suno-mode" style="flex:1;background:#171b1f;color:#e6edf3;border:1px solid #30363d;border-radius:8px;padding:4px 6px">',
      '      <option value="publish">Publicar y enviar</option>',
      '      <option value="api">Publicar por API (acelerado)</option>',
      '      <option value="dry">Simulación (no publica)</option>',
      '      <option value="links">Solo enlaces (sin publicar)</option>',
      '    </select>',
      '  </label>',
      '  <label style="display:flex;gap:6px;align-items:center;font-size:12px">',
      '    Formato ',
      '    <select id="luna-suno-format" style="flex:1;background:#171b1f;color:#e6edf3;border:1px solid #30363d;border-radius:8px;padding:4px 6px">',
      '      <option value="mp3">Audio MP3</option>',
      '      <option value="mp4">Video MP4</option>',
      '    </select>',
      '  </label>',
      '  <label style="display:flex;gap:6px;align-items:center;font-size:12px">',
      '    <input type="checkbox" id="luna-suno-diagnostics"> Diagnóstico (volcar menús)',
      '  </label>',
      '  <button id="luna-suno-run" style="background:#06b6d4;color:#04252b;border:0;border-radius:10px;padding:8px 10px;font-weight:600;cursor:pointer">Importar lista abierta</button>',
      '  <button id="luna-suno-stop" style="display:none;background:#30363d;color:#e6edf3;border:0;border-radius:10px;padding:8px 10px;cursor:pointer">Detener</button>',
      '  <div id="luna-suno-status" style="font-size:12px;color:#8b949e;min-height:16px"></div>',
      '  <div id="luna-suno-list" style="max-height:220px;overflow:auto;font-size:11px;line-height:1.45"></div>',
      '</div>'
    ].join('');

    document.documentElement.appendChild(root);

    ui.root = root;
    ui.list = root.querySelector('#luna-suno-list');
    ui.status = root.querySelector('#luna-suno-status');

    root.querySelector('#luna-suno-close').addEventListener('click', function () {
      root.style.display = 'none';
    });
    root.querySelector('#luna-suno-mode').addEventListener('change', function (event) {
      state.mode = event.target.value;
    });
    root.querySelector('#luna-suno-format').addEventListener('change', function (event) {
      state.format = event.target.value;
    });
    root.querySelector('#luna-suno-diagnostics').addEventListener('change', function (event) {
      state.diagnostics = event.target.checked;
    });
    root.querySelector('#luna-suno-run').addEventListener('click', function () { runImport(); });
    root.querySelector('#luna-suno-stop').addEventListener('click', function () {
      state.stopRequested = true;
    });

    return root;
  }

  function setStatus(text) {
    if (ui.status) ui.status.textContent = text;
  }

  function addLog(title, detail, tone) {
    if (!ui.list) return;
    const row = document.createElement('div');
    row.style.cssText = 'padding:3px 0;border-bottom:1px solid #1c2125;color:' +
      (tone === 'ok' ? '#2ec4a3' : tone === 'error' ? '#ff6b6b' : tone === 'warn' ? '#f4a261' : '#c9d1d9') +
      ';word-break:break-all';
    row.textContent = title + (detail ? ' — ' + detail : '');
    ui.list.appendChild(row);
    ui.list.scrollTop = ui.list.scrollHeight;
  }

  // ── Botón flotante ──────────────────────────────────────────────────────

  function ensureLauncher() {
    if (ui.button && ui.button.isConnected) return;
    const button = document.createElement('button');
    button.id = 'luna-suno-launcher';
    button.textContent = '☀️ Luna Fetch';
    button.style.cssText = [
      'position:fixed', 'right:18px', 'bottom:18px', 'z-index:2147482999',
      'background:#06b6d4', 'color:#04252b', 'border:0', 'border-radius:999px',
      'padding:10px 16px', 'font-weight:700', 'font-size:13px', 'cursor:pointer',
      'box-shadow:0 8px 24px rgba(0,0,0,.45)', 'font-family:system-ui,sans-serif'
    ].join(';');
    button.addEventListener('click', function () {
      const root = ensureUi();
      root.style.display = root.style.display === 'none' ? 'block' : 'none';
    });
    document.documentElement.appendChild(button);
    ui.button = button;
  }

  // ── Descubrimiento de la lista ──────────────────────────────────────────

  function isOptionsButton(element) {
    if (!element || element.tagName !== 'BUTTON') return false;
    const aria = [
      element.getAttribute('aria-label') || '',
      element.getAttribute('title') || '',
      element.getAttribute('data-testid') || ''
    ].join(' ');
    const text = ownText(element);
    if (/^(more|options|more options|opciones|más|mas|\.{3}|…|⋯)$/i.test(text.trim())) return true;
    if (/more|options|opciones/i.test(aria) && text.length <= 4) return true;
    return false;
  }

  function optionButtons() {
    return Array.prototype.slice.call(document.querySelectorAll('button')).filter(function (button) {
      if (!isVisible(button)) return false;
      if (state.processed.has(button)) return false;
      return isOptionsButton(button);
    });
  }

  /** Contenedor con scroll donde vive la lista. */
  function scrollContainer() {
    let best = null;
    let bestScore = 0;
    const elements = Array.prototype.slice.call(document.querySelectorAll('div,section,main,ul'));
    elements.forEach(function (element) {
      if (!isVisible(element)) return;
      const overflow = getComputedStyle(element).overflowY;
      if (overflow !== 'auto' && overflow !== 'scroll') return;
      const score = element.scrollHeight;
      if (score > bestScore) { bestScore = score; best = element; }
    });
    return best;
  }

  /**
   * Contenedor de la fila a la que pertenece un botón de opciones.
   *
   * Devuelve la fila real (el contenedor que identifica la canción) y el
   * botón «Compartir» de esa fila, si lo hay.
   */
  function rowContainer(button) {
    let node = button.parentElement;
    for (let depth = 0; depth < 6 && node; depth += 1) {
      const buttons = node.querySelectorAll('button');
      let shareCount = 0;
      let shareButton = null;
      Array.prototype.forEach.call(buttons, function (candidate) {
        const text = ownText(candidate);
        if (TEXT_SHARE.test(text) && text.length <= 30) {
          shareCount += 1;
          if (!shareButton) shareButton = candidate;
        }
      });
      if (shareCount === 1) {
        return { row: songRowOf(node), shareButton: shareButton };
      }
      node = node.parentElement;
    }
    return { row: songRowOf(button.parentElement), shareButton: null };
  }

  /**
   * Sube desde un nodo hasta el contenedor que identifica la canción.
   *
   * La fila real es la que lleva el identificador (o el enlace a la
   * canción): es donde vive la insignia «Publicada» y el título.
   */
  function songRowOf(node) {
    let current = node;
    for (let depth = 0; depth < 6 && current; depth += 1) {
      if (current.hasAttribute && current.hasAttribute('data-id')) return current;
      if (current.getAttribute && current.getAttribute('data-song-id')) return current;
      if (current.querySelector && current.querySelector('[data-id]')) return current;
      if (current.querySelector && current.querySelector('a[href*="/song/"]')) return current;
      current = current.parentElement;
    }
    return node;
  }

  function rowTitle(row) {
    if (!row) return '';
    const text = ownText(row);
    return text.length > 70 ? text.slice(0, 70) + '…' : text;
  }

  function alreadyPublic(row) {
    if (!row) return false;
    return TEXT_PUBLIC_BADGE.test(ownText(row));
  }

  function optionIsChecked(element) {
    const marker = [
      element.getAttribute('aria-checked') || '',
      element.getAttribute('aria-pressed') || '',
      element.getAttribute('aria-selected') || '',
      element.getAttribute('data-state') || ''
    ].join(' ');
    return /true|checked|on|selected/i.test(marker);
  }

  // ── Acciones sobre el menú y el diálogo ────────────────────────────────

  function openMenu(button, exclude) {
    const before = snapshotElements();
    click(button);
    return waitFor(function () {
      const fresh = freshMatching(before, [TEXT_SHARE, TEXT_PUBLISH]);
      const scope = overlayScope(exclude);

      const share = pickBest(fresh.filter(function (candidate) {
        return TEXT_SHARE.test(candidate.text + ' ' + candidate.aria);
      }), { last: true })
        || (scope ? findItem([TEXT_SHARE], { within: scope }) : null)
        || findItem([TEXT_SHARE], { after: button, exclude: exclude });

      const publish = pickBest(fresh.filter(function (candidate) {
        return TEXT_PUBLISH.test(candidate.text + ' ' + candidate.aria);
      }), { last: true })
        || (scope ? findItem([TEXT_PUBLISH], { within: scope }) : null);

      if (share || publish) return { share: share, publish: publish };
      return null;
    }, 2000, 100);
  }

  function closeOverlays() {
    try {
      document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', keyCode: 27, bubbles: true }));
    } catch (e) { /* ignorado */ }
  }

  /** Descripción breve de un elemento, para el modo diagnóstico. */
  function describeElement(element) {
    if (!element) return 'null';
    return element.tagName.toLowerCase() + '["' + ownText(element).slice(0, 40) + '"]';
  }

  function dumpDiagnostics(label, element) {
    if (!state.diagnostics) return;
    const html = element ? element.outerHTML : '(elemento no encontrado)';
    addLog('Diagnóstico: ' + label, html.slice(0, 500), 'warn');
    console.log('[Luna Fetch][Suno] ' + label, html);
  }

  /** Busca el enlace público dentro del diálogo abierto. */
  function linkFromDialog() {
    const inputs = Array.prototype.slice.call(document.querySelectorAll('input[value], textarea'));
    for (const input of inputs) {
      const link = extractSunoLink(input.value || '');
      if (link) return link;
    }
    const anchors = Array.prototype.slice.call(document.querySelectorAll('a[href]'));
    for (const anchor of anchors) {
      const link = extractSunoLink(anchor.getAttribute('href') || '');
      if (link) return link;
    }
    return extractSunoLink(ownText(document.body));
  }

  // ── Acelerador por API ──────────────────────────────────────────────────
  //
  // Suno publica por la interfaz, pero por debajo usa llamadas HTTP. El hook
  // las observa en tiempo real: cuando detecta una llamada de publicación
  // (una URL con «share», «publish» o «visibility») guarda una *receta*: la
  // misma llamada con el identificador de la canción sustituido por un
  // marcador. Con esa receta, el importador puede publicar el lote sin
  // clics — mucho más rápido — y si la llamada falla, se degrada al método
  // de interfaz sin perder nada.

  const API_PUBLISH_RE = /(share|publish|visibility|public)/i;
  const RECIPE_KEY = 'sunoApiRecipe';

  const api = {
    recipe: null,

    /** Carga la receta guardada en la sesión anterior del navegador. */
    async load() {
      const stored = await storageGet(RECIPE_KEY, null);
      if (stored && stored.url && stored.idToken) {
        api.recipe = stored;
      }
      return api.recipe;
    },

    /**
     * Aprende de una llamada observada por el hook.
     * Solo guarda la receta si el identificador de la canción aparece en la
     * llamada: sin él no se puede generalizar a las demás canciones.
     */
    learn(payload) {
      if (!payload || !payload.method || !payload.url) return null;
      if (String(payload.method).toUpperCase() === 'GET') return null;
      if (!API_PUBLISH_RE.test(payload.url)) return null;

      const haystack = (payload.url || '') + ' ' + (payload.body || '');
      const token = Links.uuidOf(haystack);
      if (!token) return null;

      const recipe = {
        method: String(payload.method).toUpperCase(),
        url: String(payload.url).split(token).join('{id}'),
        body: payload.body ? String(payload.body).split(token).join('{id}') : '',
        idToken: token,
        learnedAt: Date.now()
      };
      api.recipe = recipe;
      sendMessage({ action: 'sunoCapture', payload: { kind: 'publish-recipe', recipe: recipe } });
      addLog('Receta de API aprendida', recipe.method + ' ' + recipe.url, 'ok');
      return recipe;
    },

    /**
     * Publica una canción por API.
     * @returns {Promise<boolean>} true si la publicación se confirmó.
     */
    async publish(songId) {
      const recipe = api.recipe;
      if (!recipe || !songId) return false;
      try {
        const response = await fetch(recipe.url.split('{id}').join(songId), {
          method: recipe.method,
          credentials: 'include',
          headers: recipe.body ? { 'Content-Type': 'application/json' } : undefined,
          body: recipe.body ? recipe.body.split('{id}').join(songId) : undefined
        });
        if (!response.ok) return false;
        const text = await response.text();
        const link = Links.extract(text);
        if (link) {
          const entry = canonicalize(link);
          if (entry && entry.id) state.seenIds.add(entry.id);
          state.apiLink = link;
        }
        return true;
      } catch (e) {
        return false;
      }
    }
  };

  /** Identificador de la canción de una fila (si la fila lo expone). */
  function rowSongId(row) {
    if (!row) return null;
    const attributes = ['data-id', 'data-song-id', 'data-uuid', 'href'];
    const own = row.getAttribute ? row.getAttribute('data-id') : '';
    const ownId = Links.uuidOf(own || '');
    if (ownId) return ownId;
    const nodes = row.querySelectorAll('a[href], [data-id], [data-song-id], [data-uuid]');
    for (const node of nodes) {
      for (const attribute of attributes) {
        const id = Links.uuidOf(node.getAttribute(attribute) || '');
        if (id) return id;
      }
    }
    return Links.uuidOf(ownText(row));
  }

  // ── Intercepción del portapapeles ───────────────────────────────────────

  window.addEventListener('message', function (event) {
    if (event.source !== window) return;
    const data = event.data;
    if (!data || data.source !== CHANNEL) return;

    if (data.type === 'suno-link') {
      const link = extractSunoLink(data.payload && data.payload.url);
      if (link) {
        state.pendingLink = link;
        maybeAutoQueue(link);
      }
    }
    if (data.type === 'suno-api') {
      // La llamada real de publicación se aprende aunque el diagnóstico esté
      // apagado: es lo que permite acelerar el lote.
      api.learn(data.payload);
      if (state.diagnostics) {
        console.log('[Luna Fetch][Suno][api]', data.payload);
      }
    }
  });

  async function maybeAutoQueue(link) {
    const enabled = await storageGet('sunoAutoLinks', false);
    if (!enabled) return;
    const entry = await resolveCanonical(canonicalize(link));
    if (!entry) return;
    if (entry.id && state.seenIds.has(entry.id)) return;
    if (entry.id) state.seenIds.add(entry.id);
    const response = await queueUrl(entry.url);
    if (response && response.ok) addLog('Autoenviado', entry.url, 'ok');
    else addLog('Autoenvío fallido', link, 'error');
  }

  // ── Bucle de importación ────────────────────────────────────────────────

  async function processButton(button) {
    const context = rowContainer(button);
    const title = rowTitle(context.row);
    const link = await shareAndCapture(button, context);
    state.processed.add(button);

    if (!link) {
      addLog('Sin enlace', title || '(fila sin título)', 'warn');
      return null;
    }
    const entry = await resolveCanonical(canonicalize(link));
    if (!entry) {
      addLog('Enlace no recognized', link, 'error');
      return null;
    }
    if (entry.id) state.seenIds.add(entry.id);
    state.links.push(entry.url);
    addLog('Enlace capturado', entry.url, 'ok');

    if (state.mode === 'dry') return entry;

    // El enlace queda en el búfer: se envía por lotes al terminar (o al
    // llenarse el lote), para no hacer una ida y vuelta por canción.
    state.pendingQueue.push(entry.url);
    if (state.pendingQueue.length >= BATCH_SIZE) await flushQueue();
    return entry;
  }

  async function shareAndCapture(button, context) {
    closeOverlays();
    await sleep(80);

    // 1) Menú contextual de la fila.
    let menu = await openMenu(button, context.row);
    if (menu) {
      dumpDiagnostics(
        'menú de la fila · compartir=' + describeElement(menu.share) +
        ' · publicar=' + describeElement(menu.publish),
        (menu.share || menu.publish || button).parentElement
      );

      // 2) Publicar (si el menú lo ofrece y la fila aún no es pública).
      const rowIsPublic = alreadyPublic(context.row);
      let publishedByApi = false;

      // Modo API: se intenta publicar sin clics con la receta aprendida.
      if (state.mode === 'api' && !rowIsPublic && api.recipe) {
        const songId = rowSongId(context.row);
        publishedByApi = songId ? await api.publish(songId) : false;
        addLog(
          publishedByApi ? 'Publicada por API' : 'La API no respondió',
          publishedByApi ? (rowTitle(context.row) || songId) : 'se publica desde la interfaz',
          publishedByApi ? 'ok' : 'warn'
        );
      }

      const shouldPublishHere = state.mode === 'publish' ||
        (state.mode === 'api' && !publishedByApi);

      if (shouldPublishHere && menu.publish && !rowIsPublic && !optionIsChecked(menu.publish)) {
        click(menu.publish);
        await sleep(700);
        menu = (await openMenu(button, context.row)) || menu; // el menú se cierra tras publicar
      }

      // 3) Compartir.
      if (menu.share) {
        const shareItem = menu.share;
        const beforeDialog = snapshotElements();
        click(shareItem);
        const dialog = await waitFor(function () {
          return freshItem(beforeDialog, [TEXT_COPY], { last: true }) ||
                 findItem([TEXT_COPY], { last: true }) || null;
        }, 2500, 120);
        dumpDiagnostics('diálogo de compartir', dialog ? dialog.parentElement : null);
        if (dialog) {
          const link = await captureFromDialog(dialog, beforeDialog);
          if (link) { closeOverlays(); return link; }
        }
      }
    }

    // 4) Reserva: el botón «Compartir» de la propia fila.
    if (context.shareButton) {
      const beforeDialog = snapshotElements();
      click(context.shareButton);
      const dialog = await waitFor(function () {
        return freshItem(beforeDialog, [TEXT_COPY], { last: true }) ||
               findItem([TEXT_COPY], { last: true }) || null;
      }, 2500, 120);
      if (dialog) {
        const link = await captureFromDialog(dialog, beforeDialog);
        if (link) { closeOverlays(); return link; }
      }
    }

    closeOverlays();
    return null;
  }

  async function captureFromDialog(dialog, beforeDialog) {
    // La web puede ofrecer la visibilidad pública dentro del propio diálogo.
    if (state.mode === 'publish') {
      const publicOption = freshItem(beforeDialog, [TEXT_PUBLISH], { last: true });
      if (publicOption && !optionIsChecked(publicOption)) {
        click(publicOption);
        await sleep(500);
      }
    }

    // Preferimos lo que la web copia (evento del hook); si no llega, leemos el DOM.
    state.pendingLink = null;
    const copyButton = freshItem(beforeDialog, [TEXT_COPY], { last: true }) || dialog;
    click(copyButton);

    const captured = await waitFor(function () { return state.pendingLink; }, 2500, 100);
    if (captured) return captured;

    await sleep(300);
    return linkFromDialog();
  }

  async function runImport() {
    if (state.running) return;
    state.running = true;
    state.stopRequested = false;
    state.links = [];
    state.pendingQueue = [];
    ensureUi();
    const runButton = ui.root.querySelector('#luna-suno-run');
    const stopButton = ui.root.querySelector('#luna-suno-stop');
    if (runButton) runButton.style.display = 'none';
    if (stopButton) stopButton.style.display = 'block';

    setStatus('Analizando la lista…');

    // El acelerador por API necesita la receta aprendida en sesiones previas.
    if (state.mode === 'api') {
      const recipe = await api.load();
      setStatus(recipe
        ? 'Analizando la lista… (API acelerada lista)'
        : 'Analizando la lista… (sin receta de API: se publicará desde la interfaz)');
    }

    try {
      let rounds = 0;
      let stagnant = 0;
      const MAX_ROUNDS = 80;

      while (rounds < MAX_ROUNDS && !state.stopRequested) {
        rounds += 1;
        const buttons = optionButtons();

        if (!buttons.length) {
          const container = scrollContainer();
          if (!container) break;
          const before = container.scrollTop;
          container.scrollTop = Math.min(container.scrollTop + Math.max(container.clientHeight * 0.8, 300), container.scrollHeight);
          await sleep(700);
          if (container.scrollTop === before) { stagnant += 1; if (stagnant > 2) break; } else stagnant = 0;
          if (!optionButtons().length && stagnant > 2) break;
          continue;
        }

        setStatus('Procesando… (' + buttons.length + ' filas visibles)');

        for (const button of buttons) {
          if (state.stopRequested) break;
          try {
            await processButton(button);
          } catch (error) {
            addLog('Error', String(error && error.message ? error.message : error), 'error');
          }
          await sleep(300);
        }

        const container = scrollContainer();
        if (container) {
          const before = container.scrollTop;
          container.scrollTop = Math.min(container.scrollTop + Math.max(container.clientHeight * 0.9, 320), container.scrollHeight);
          await sleep(600);
          if (before === container.scrollTop && !optionButtons().length) { stagnant += 1; if (stagnant > 2) break; } else stagnant = 0;
        }
      }

      const captured = state.links.length;
      const batch = state.mode === 'dry' ? null : await flushQueue();
      const queued = batch && typeof batch.queued === 'number' ? batch.queued : captured;
      const skipped = batch && typeof batch.skipped === 'number' ? batch.skipped : 0;

      if (state.mode === 'dry') {
        setStatus('Simulación: ' + captured + ' enlace(s) capturados, nada enviado.');
        addLog('Simulación finalizada', captured + ' enlace(s) sin enviar', 'warn');
      } else {
        setStatus('✅ ' + queued + ' encolada(s)' + (skipped ? ' · ' + skipped + ' ya estaban' : '') + '.');
        addLog('Importación finalizada', queued + ' encolada(s)' + (skipped ? ', ' + skipped + ' ya estaban' : ''), queued ? 'ok' : 'warn');
      }
    } finally {
      state.running = false;
      if (runButton) runButton.style.display = 'block';
      if (stopButton) stopButton.style.display = 'none';
    }
  }

  // ── Arranque ────────────────────────────────────────────────────────────

  ensureLauncher();

  try {
    chrome.runtime.onMessage.addListener(function (message, sender, sendResponse) {
      if (message && message.action === 'startSunoImport') {
        if (message.format) state.format = message.format;
        if (message.mode) state.mode = message.mode;
        ensureUi();
        ui.root.style.display = 'block';
        runImport();
        sendResponse({ ok: true });
        return true;
      }
      if (message && message.action === 'showSunoPanel') {
        ensureUi();
        ui.root.style.display = 'block';
        sendResponse({ ok: true });
        return true;
      }
      return false;
    });
  } catch (e) { /* contexto invalidado */ }

  window.__lunaSuno = {
    run: runImport,
    state: state,
    canonicalize: canonicalize,
    resolveCanonical: resolveCanonical,
    api: api
  };
})();
