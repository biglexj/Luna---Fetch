# Validación — Importador de Listas de Suno

Fecha: 2026-10-23
Alcance: fases 0 a 4 del plan (extensión, cadena de cookies en el escritorio, lote y acelerador).

## 1. Banco de pruebas del importador (Chromium sin cabeza)

Réplica local de la interfaz de Suno (`test/suno-import/mock-suno.html`) con
10 canciones (8 privadas, 2 públicas) y scroll incremental. Se inyectan los
ficheros reales de la extensión (`suno-links.js`, `suno-hook.js`, `suno.js`) y
se comprueba el resultado.

**Resultado: 19 de 19 comprobaciones en verde.**

| Comprobación | Resultado |
|---|---|
| El orquestador se inyecta en la página | ✅ |
| Se capturan los 10 enlaces de la lista | ✅ |
| Se copia el enlace en las 10 canciones | ✅ |
| Se publica solo lo que estaba en privado (8) | ✅ |
| Todas las canciones quedan públicas | ✅ |
| Todos los enlaces son canónicos (`/song/<uuid>`) | ✅ |
| No hay enlaces duplicados | ✅ |
| El formato enviado es el elegido (MP3) | ✅ |
| El enlace copiado dentro de Suno se encola solo | ✅ |
| El enlace interceptado llega en forma canónica | ✅ |
| El enlace interceptado conserva el formato elegido | ✅ |
| El hook aprende la receta de publicación | ✅ |
| La receta conserva una plantilla reutilizable (`{id}`) | ✅ |
| El modo API captura también los 10 enlaces | ✅ |
| El modo API no pulsa «Hacer pública» en la interfaz | ✅ |
| El modo API sigue copiando el enlace de cada canción | ✅ |
| La API publica las 8 canciones privadas | ✅ |
| Con la API caída se recurre a la interfaz | ✅ |
| La degradación no pierde ningún enlace | ✅ |

## 2. Suite de pruebas del escritorio

`gradlew :composeApp:desktopTest` → **BUILD SUCCESSFUL**, 0 fallos, 0 errores.

| Suite | Pruebas | Fallos |
|---|---|---|
| `LunaSocketServerQueueTest` | 5 | 0 |
| `NetscapeCookieJarTest` | 6 | 0 |
| `SunoExtractorTest` | 5 | 0 |
| `SunoMetadataParsingTest` | 3 | 0 |
| `SunoRealPageParsingTest` | 1 | 0 |
| `LinkCanonicalizerTest` | 9 | 0 |
| `DownloadQueueTest` | 11 | 0 |
| Resto de suites | 88 | 0 |

Las dos pruebas nuevas del endpoint de lote (`/download/batch`) están dentro de
`LunaSocketServerQueueTest`: encolado en bloque con recuento de duplicados y
rechazo del lote vacío.

## 3. Regresión de los flujos existentes

- La extensión de YouTube/TikTok no se modificó en su camino de envío: el
  servicio worker conserva las acciones `download` y `analyze` sin cambios, y
  `cookiesForUrl` sigue devolviendo las cookies de YouTube y Google.
- La cola del escritorio sigue siendo secuencial: `LunaSocketServerQueueTest`
  comprueba que dos envíos seguidos se descargan uno detrás de otro y sin
  solaparse.
- La sincronización de cookies ahora incluye `suno.com`; es una ampliación del
  dominio, no un cambio de comportamiento para el resto.

## 4. Defectos encontrados y corregidos durante la validación

| Defecto | Causa | Corrección |
|---|---|---|
| El hook nunca observaba llamadas de red | `fetch` devuelve una promesa, no la respuesta: se leía `response.headers` sobre la promesa | Se inspecciona la respuesta dentro de `promise.then(...)` |
| El importador nunca usaba el menú contextual | `openMenu` devuelve una promesa y no se esperaba: `menu.share` era `undefined` | `await openMenu(...)` en los dos puntos de uso |
| La insignia «Publicada» no se detectaba | La fila devuelta era el contenedor de botones, no la fila de la canción | `songRowOf()` sube hasta el contenedor que identifica la canción |
| El acelerador no encontraba la canción | El identificador se buscaba en el contenedor equivocado | `rowSongId()` busca `data-id` y enlaces a `/song/<uuid>`, subiendo por los ancestros |
| Los enlaces salían como `/s/undefined` | Al unificar el normalizador se perdió el grupo de captura de la expresión regular | Se restituyó el grupo en `suno-links.js` |

## 5. Comprobaciones pendientes de la sesión real

Estas requieren la sesión autenticada de Suno en el navegador de Biglex y no
pueden ejecutarse desde el entorno de pruebas:

- [ ] Importar la lista «Espacio Vtuber» real y comprobar el recuento de cola.
- [ ] Confirmar que los MP3 llegan a `<destino>/audio/` con la portada incrustada
      y la letra `.txt` acompañante.
- [ ] Verificar que la receta aprendida por el hook corresponde al endpoint real
      de publicación de Suno antes de usar el modo API en producción.
- [ ] Medir cuánto tarda el modo API frente al método de interfaz sobre la lista
      real.

Para la calibración de selectores está el **modo diagnóstico** del panel: vuelca
el HTML del menú y del diálogo con el elemento exacto que se está pulsando.
