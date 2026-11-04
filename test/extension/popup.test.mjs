import { readFileSync } from 'node:fs';
import vm from 'node:vm';
import test from 'node:test';
import assert from 'node:assert/strict';

const source = readFileSync(new URL('../../browser-extension/popup.js', import.meta.url), 'utf8');

function popup(reply = { ok: true }) {
  function element(dataset = {}) {
    return { dataset, value: '', style: {}, disabled: false, textContent: '',
      classList: { add() {}, remove() {} }, focus() {}, select() {},
      handlers: {}, addEventListener(type, fn) { this.handlers[type] = fn; } };
  }
  const ids = Object.fromEntries(['send', 'url', 'status', 'suno-import', 'suno-copy', 'suno-auto']
    .map(id => [id, element()]));
  const formats = [element({ fmt: 'mp4' }), element({ fmt: 'mp3' })];
  const sunoFormats = [element({ sfmt: 'mp3' }), element({ sfmt: 'mp4' })];
  const messages = [];
  let closed = 0;
  const context = {
    document: {
      getElementById: id => ids[id],
      querySelectorAll: selector => selector === '[data-sfmt]' ? sunoFormats
        : selector === '[data-fmt]' ? formats : [...formats, ...sunoFormats]
    },
    window: { close() { closed++; }, SunoLinks: { extract: text => text } },
    navigator: { clipboard: { readText: async () => 'https://suno.com/song/example' } },
    chrome: {
      runtime: { async sendMessage(message) { messages.push(message); return typeof reply === 'function' ? reply() : reply; } },
      storage: { local: { get(defaults, cb) { cb(defaults); }, set() {} } },
      tabs: { query(_options, cb) { cb([]); } }
    }
  };
  vm.runInNewContext(source, context);
  ids.url.value = 'https://example.com/video';
  return { ids, formats, sunoFormats, messages, closed: () => closed };
}

test('cierra al aceptar el envío y conserva MP3 general al elegir video de Suno', async () => {
  const p = popup();
  p.formats[1].handlers.click();
  p.sunoFormats[1].handlers.click();
  await p.ids.send.handlers.click();
  assert.equal(p.messages[0].format, 'mp3');
  assert.equal(p.closed(), 1);
});

test('el envío desde el portapapeles usa el formato de Suno y cierra', async () => {
  const p = popup();
  p.sunoFormats[1].handlers.click();
  await p.ids['suno-copy'].handlers.click();
  assert.equal(p.messages[0].format, 'mp4');
  assert.equal(p.messages[0].action, 'sunoQueue');
  assert.equal(p.closed(), 1);
});

test('muestra los errores sin cerrar, tanto en descarga general como en Suno', async () => {
  const p = popup({ ok: false, error: 'Sin conexión' });
  await p.ids.send.handlers.click();
  assert.equal(p.closed(), 0);
  assert.equal(p.ids.status.textContent, 'Sin conexión');
  await p.ids['suno-copy'].handlers.click();
  assert.equal(p.closed(), 0);
  assert.equal(p.ids.status.textContent, 'Sin conexión');
});

test('Enter repetido no duplica el envío y espera la respuesta antes de cerrar', async () => {
  let respond;
  const p = popup(() => new Promise(resolve => { respond = resolve; }));
  const pending = p.ids.send.handlers.click();
  await p.ids.url.handlers.keydown({ key: 'Enter' });
  assert.equal(p.messages.length, 1);
  assert.equal(p.closed(), 0);
  respond({ ok: true });
  await pending;
  assert.equal(p.closed(), 1);
});

test('sin enlace no envía ni cierra', async () => {
  const p = popup();
  p.ids.url.value = '  ';
  await p.ids.send.handlers.click();
  assert.equal(p.messages.length, 0);
  assert.equal(p.closed(), 0);
});
