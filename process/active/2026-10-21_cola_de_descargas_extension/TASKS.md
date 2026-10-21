# Tareas: Cola de Descargas para Envíos Externos

- [x] **Tarea 1**: Crear el modelo `QueuedDownload` / `QueueItemStatus` en `domain/DownloadQueue.kt`.
- [x] **Tarea 2**: Convertir `startDirectDownload()` en encolado y añadir el worker secuencial en `LunaFetchPresenter`.
- [x] **Tarea 3**: Extraer `runDownload()` para compartir la descarga entre el botón manual y la cola.
- [x] **Tarea 4**: Gestionar la pausa por falta de destino y las acciones de cola (quitar, vaciar, limpiar).
- [x] **Tarea 5**: Atender cada conexión del servidor local en su propia corrutina y responder con el estado de la cola.
- [x] **Tarea 6**: Crear la tarjeta "Cola de descargas" y conectarla en los dos layouts de la interfaz.
- [x] **Tarea 7**: Deshabilitar la descarga manual mientras la cola procesa un enlace.
- [x] **Tarea 8**: Actualizar la extensión (popup encolado, manifiesto 1.4.2).
- [x] **Tarea 9**: Pruebas unitarias de la cola y prueba de integración del servidor local.
- [x] **Tarea 10**: Validación en la aplicación de escritorio en ejecución y actualización de `ROADMAP.md`.

