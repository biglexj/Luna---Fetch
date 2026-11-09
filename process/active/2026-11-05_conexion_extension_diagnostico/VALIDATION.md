# Validación

## Diagnóstico

- La aplicación instalada estaba en marcha y respondía en 127.0.0.1:51234: se
  comprobó con `curl` y con el registro de cookies de sesión, que la extensión
  actualiza cada vez que se carga una página de YouTube.
- La extensión estaba cargada desde `browser-extension` con el permiso de host
  `http://127.0.0.1:51234/*` concedido y sin motivos de desactivación.
- Con un perfil limpio de Edge 156 la extensión encolaba la descarga sin
  problema: el fallo no era de CORS, ni de permisos, ni del puerto.

## Causa

- `LunaSocketServer` leía el cuerpo de la petición con `BufferedReader`, es
  decir, por caracteres, mientras que `Content-Length` cuenta bytes. Con un
  carácter multibyte en el cuerpo (una cookie con tilde, por ejemplo) el
  servidor esperaba de más, agotaba el tiempo de espera de 8 s y moría sin
  responder. En el navegador eso es un error de red, y la extensión lo traducía
  al mensaje «Luna Fetch no está abierto en tu PC».
- Reproducido contra la app instalada: 8006 ms y `fetch failed` con cuerpo
  multibyte, frente a 39 ms y respuesta correcta con cuerpo ASCII.
- El fallo de red y el de comunicación con el servicio en segundo plano
  compartían el mismo mensaje, sin dejar forma de saber cuál había ocurrido.

## Correcciones

- El cuerpo se lee en bytes; el caso multibyte responde en 10 ms.
- Un manejador que lanza una excepción responde 500 con el motivo, en vez de
  dejar la conexión muerta.
- Nuevo `GET /health` con la aplicación, la versión, el PID y la cola pendiente.
- Registro de peticiones en `%TEMP%\luna_fetch_server.log`.
- La extensión muestra el motivo real del fallo y el estado de la conexión al
  abrir el popup; la extensión pasa a la versión 1.4.6.

## Comprobaciones

- `mise test` (`:composeApp:desktopTest`): BUILD SUCCESSFUL, 122 pruebas sin
  fallos, incluidas tres regresiones nuevas (cuerpo multibyte, `/health` y
  manejador que falla).
- Extremo a extremo con Edge y la extensión real contra la app nueva:
  `GET /health -> 200`, `OPTIONS /download -> 204` y `POST /download -> 200`
  en el registro del servidor, con el enlace encolado.
- Popup: muestra «✅ Conectado con Luna Fetch» al abrirlo.
- No se han ejecutado descargas reales ni actualizaciones del motor: solo
  encolados de prueba.

## Instalación en el equipo

- Con autorización de Biglex se cerró la aplicación instalada y se copió la
  imagen de la app recién compilada sobre `%LOCALAPPDATA%\Programs\LunaFetch`
  (`app/` y `runtime/` reflejados con `robocopy /MIR`, que también retira los
  jars viejos acumulados de versiones anteriores). El desinstalador
  (`unins000.exe`) se conservó intacto.
- La ruta de inicio con Windows sigue apuntando a
  `%LOCALAPPDATA%\Programs\LunaFetch\LunaFetch.exe --autostart`.
- La aplicación actualizada está en marcha y responde en 127.0.0.1:51234:
  `GET /health` devuelve `{"ok":true,"app":"Luna Fetch","pid":…,"pending":0}`.
- Cuerpo multibyte contra la app instalada: 58 ms y respuesta 200, frente a
  8006 ms y conexión muerta antes del arreglo.
- Prueba final con la extensión del repositorio sin modificar contra la app
  instalada: `GET /health -> 200` y `POST /download -> 200 (Queued)`.
- Pendiente: recargar la extensión en `edge://extensions` para que Edge tome
  el código nuevo del popup y del servicio en segundo plano, y comprobar el
  envío desde YouTube Music.

## Orden de la cola y de la vista de Descargas

- La cola de descargas se muestra con lo que sigue vivo primero (descargando o
  en espera, en su orden de encolado) y lo ya terminado al final
  (completado, fallido o cancelado). Dos pruebas cubren el reparto y la
  conservación del orden dentro de cada grupo.
- En la vista de Descargas, la tarjeta de descarga completada y la de detalles
  del error quedan al final de la columna, debajo del enlace y de la cola.
- Comprobado en la app instalada con un enlace encolado a propósito que falla:
  la cola muestra el elemento con ❌ y la tarjeta «Detalles del error» queda
  debajo, en el último lugar. Tras reiniciar la app, la cola de prueba quedó
  vacía (la cola vive en memoria).
- `mise test`: 124 pruebas sin fallos.
