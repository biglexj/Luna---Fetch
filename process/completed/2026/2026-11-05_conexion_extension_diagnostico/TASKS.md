# Tareas

- [x] Reproducir el fallo: la app respondía bien por consola y por `curl`, y la
      extensión funcionaba en un perfil limpio de Edge.
- [x] Confirmar el fallo del servidor: con un carácter multibyte en el cuerpo,
      la respuesta tardaba 8 s y terminaba sin responder (error de red en el
      navegador).
- [x] Leer el cuerpo de la petición en bytes y no en caracteres.
- [x] Responder con un error 500 cuando el manejador falla, en vez de dejar la
      conexión muerta.
- [x] Añadir `GET /health` para saber si la aplicación responde.
- [x] Registrar cada petición en `%TEMP%\luna_fetch_server.log`.
- [x] Cabeceras CORS con permiso de red local (`Access-Control-Allow-Private-Network`).
- [x] Mostrar el motivo real del fallo en la extensión en lugar del mensaje
      genérico «no está abierto».
- [x] Mostrar el estado de la conexión al abrir el popup.
- [x] Pruebas de regresión: cuerpo multibyte, endpoint de estado y manejador que falla.
- [x] Comprobación de extremo a extremo con Edge y la extensión real.
- [x] Actualizar la instalación estable del equipo con la app corregida.
- [x] Cola de descargas: lo ya terminado (completado, fallido o cancelado) al
      final, y lo que sigue vivo primero y en su orden de encolado.
- [x] Vista de Descargas: la descarga completada y el error al final de la
      columna, debajo del enlace y de la cola.
- [ ] Aceptación de Biglex sobre la instancia actualizada.
