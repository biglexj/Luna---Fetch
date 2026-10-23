// Luna Fetch — utilidades compartidas de enlaces de Suno.
//
// Este módulo es la única fuente de verdad sobre la forma de un enlace de
// Suno. Se carga en los tres contextos donde hace falta:
//
//   · el popup (ventana propia de la extensión),
//   · el content script del importador (mundo aislado),
//   · el hook de página (mundo MAIN, junto al hook).
//
// La forma canónica es `https://suno.com/song/<uuid>`: es la única que el
// extractor del escritorio puede leer sin seguir redirecciones. El enlace
// corto `https://suno.com/s/<slug>` se resuelve con la sesión del navegador.
(function (root) {
  'use strict';

  if (root.SunoLinks) return;

  /** Enlaces de canción de Suno: corto (`/s/`), reparto (`/share/`) y directo (`/song/`). */
  const SUNO_LINK_RE = /https?:\/\/(?:www\.)?suno\.com\/(?:s|share|song)\/([A-Za-z0-9._-]+)/i;
  /** Identificador de canción (UUID). */
  const SUNO_UUID_RE = /[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/i;

  /**
   * Extrae el primer enlace de Suno de un texto cualquiera.
   * @param {string} text
   * @returns {string|null}
   */
  function extract(text) {
    if (typeof text !== 'string' || !text) return null;
    const match = text.match(SUNO_LINK_RE);
    return match ? match[0] : null;
  }

  /**
   * Extrae el identificador de canción de un texto (enlace, HTML, JSON…).
   * @param {string} text
   * @returns {string|null}
   */
  function uuidOf(text) {
    if (typeof text !== 'string' || !text) return null;
    const match = text.match(SUNO_UUID_RE);
    return match ? match[0].toLowerCase() : null;
  }

  /**
   * Normaliza un enlace de Suno.
   *
   * - Un enlace `/song/<uuid>` se devuelve tal cual, en minúsculas.
   * - Un enlace corto `/s/<slug>` conserva el slug (sirve para mostrarlo) pero
   *   no puede saber el UUID: por eso `id` queda en `null` y quien lo use debe
   *   resolverlo con la sesión del navegador.
   *
   * @param {string} link
   * @returns {{url:string,id:string|null,slug:string|null}|null}
   */
  function canonicalize(link) {
    if (typeof link !== 'string' || !link) return null;
    const match = link.match(SUNO_LINK_RE);
    if (!match) return null;

    const kindMatch = link.match(/suno\.com\/(s|share|song)\//i);
    const kind = kindMatch ? kindMatch[1].toLowerCase() : 's';
    const value = match[1];
    const id = uuidOf(link);
    const base = 'https://suno.com/';

    if (kind === 'song') {
      const uuid = id || value.toLowerCase();
      return { url: base + 'song/' + uuid, id: id, slug: null };
    }
    return { url: base + 's/' + value, id: id, slug: value };
  }

  /**
   * Indica si dos enlaces apuntan a la misma canción.
   * @param {string} a
   * @param {string} b
   * @returns {boolean}
   */
  function sameSong(a, b) {
    const left = canonicalize(a);
    const right = canonicalize(b);
    if (!left || !right) return a === b;
    if (left.id && right.id) return left.id === right.id;
    return left.url === right.url;
  }

  root.SunoLinks = {
    SUNO_LINK_RE: SUNO_LINK_RE,
    SUNO_UUID_RE: SUNO_UUID_RE,
    extract: extract,
    uuidOf: uuidOf,
    canonicalize: canonicalize,
    sameSong: sameSong
  };
})(typeof window !== 'undefined' ? window : globalThis);
