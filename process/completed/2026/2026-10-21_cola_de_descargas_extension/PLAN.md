# Plan: Cola de Descargas para Envíos Externos (Extensión, Synapse, Launcher)

## 1. Contexto y Problema

Al enviar un enlace desde la extensión del navegador, Luna Fetch ejecutaba
`startDirectDownload()`, que:

1. Cancelaba la operación en curso (`operation?.cancel()`).
2. Lanzaba el análisis y la descarga del nuevo enlace.

Consecuencia: al mandar el video A y, dos segundos después, el video B, la
segunda llamada **cancelaba el análisis o la descarga del primero**. El
resultado era que solo se procesaba un enlace (normalmente el último en
llegar) y el resto se perdía en silencio.

Además, el servidor local (`LunaSocketServer`) atendía las conexiones de una
en una dentro del bucle de `accept()`, de modo que una petición lenta podía
bloquear la recepción de la siguiente.

## 2. Objetivo

Implementar una **cola de enlaces** en la que cada enlace recibido se procesa
por completo (análisis + descarga) antes de tomar el siguiente, sin que uno
pueda cancelar a otro, y con visibilidad del estado en la interfaz.

## 3. Solución Arquitectónica

### A. Modelo de cola (`domain/DownloadQueue.kt`)

- `QueuedDownload`: id, url, formato, calidad, origen, estado, título y error.
- `QueueItemStatus`: Pending, Running, Completed, Failed, Cancelled.

### B. Presentador (`LunaFetchPresenter`)

- `startDirectDownload()` deja de ejecutar de inmediato: **encola** el enlace y
  devuelve la cantidad de enlaces en la cola (-1 si la URL no es válida).
- Un único *worker* (`queueSignal` + `drainQueue()`) consume la cola de forma
  estrictamente secuencial: nunca hay dos descargas simultáneas.
- La lógica de descarga se extrajo a `runDownload()`, compartida por el botón
  manual y por la cola, para que ambos caminos se comporten igual.
- La cola se pausa si falta la carpeta de destino y se reanuda al elegirla.
- Acciones de usuario: quitar un pendiente, vaciar pendientes, limpiar terminados.

### C. Servidor local (`LunaSocketServer`)

- Cada conexión se atiende en su propia corrutina (el bucle de `accept()` ya no
  se bloquea) y `/analyze` deja de usar `runBlocking`.
- La respuesta de `/download` informa del estado de la cola:
  `{"ok":true,"queued":true,"pending":N}`.
- Puerto configurable (por defecto 51234) para poder validar en un entorno de
  pruebas sin interferir con la instancia en producción.

### D. Interfaz (`feature/download/QueueCard.kt`)

Tarjeta "Cola de descargas" con el contador de pendientes, el estado de cada
enlace (⏳ pendiente, ⬇️ descargando, ✅ completado, ❌ falló, ⛔ cancelado) y
las acciones de limpieza. El botón "Descargar" se deshabilita mientras la cola
está procesando un enlace para no intercalar dos descargas.

### E. Extensión del navegador

- El popup ya no se cierra al enviar: permite pegar el siguiente enlace y
  muestra "✅ Encolado · N en espera".
- Versión del manifiesto elevada a 1.4.2.

## 4. Verificación

- Pruebas unitarias (`DownloadQueueTest`): orden, no cancelación entre
  enlaces, continuidad tras un fallo y conteo de pendientes.
- Prueba de integración (`LunaSocketServerQueueTest`): tres peticiones
  `/download` consecutivas contra el servidor real → las tres se encolan y se
  descargan en orden, con una sola descarga activa.
- Ejecución real en la aplicación de escritorio con verificación visual del
  estado de la cola.

