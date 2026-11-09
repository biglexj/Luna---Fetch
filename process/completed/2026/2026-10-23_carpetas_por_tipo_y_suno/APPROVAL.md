# Aprobación: Carpetas por Tipo de Medio, Soporte Suno y Hover con Radio

## Estado

Implementado y validado. **Pendiente de confirmación de Biglex** (prueba en su
flujo real y decisión sobre la publicación de una nueva versión).

## Decisiones tomadas

1. Las subcarpetas se llaman `audio` y `video`, en minúsculas, tal y como las
   pidió Biglex, y se crean solas dentro de la carpeta elegida.
2. El reparto se decide por el **formato** solicitado (MP3/M4A/FLAC/WAV →
   `audio`; MP4/WebM → `video`), no por la plataforma de origen.
3. Una reparación se queda en la carpeta del archivo dañado: si ya vive en
   `…/audio`, no se anida en `audio/audio`.
4. En Android las subcarpetas se crean dentro del árbol SAF como documentos de
   directorio; si el proveedor no admite la creación, la descarga falla con un
   mensaje explícito en vez de escribir en la raíz sin avisar.
5. El título, el autor, el alias, el estilo, la letra y la portada de Suno se
   leen del payload real de la página. Cuando el payload no está disponible se
   recurre a la cabecera HTML (`<title>` y meta descripción) y, para la letra,
   al `<textarea>` de la ficha.
6. La portada se incrusta en el audio siempre que el formato lo admita (WAV no
   admite carátula). Si la incrustación falla, el audio se genera igualmente
   sin carátula.
7. En Android el audio de Suno **no** se transcodifica: el motor de Android no
   expone un ejecutable de FFmpeg, así que se conserva el MP4 original con el
   aviso «Transcodificación no completada». Queda como mejora pendiente.

## Riesgo residual

- Si Suno vuelve a cambiar la estructura del payload, el título y el autor
  seguirán siendo correctos por la cabecera HTML; el estilo, la letra y la
  portada se reponen por los valores por defecto.
- El nombre de los archivos descargados cambia (antes «Canción Suno
  373502af», ahora el título real). Las reparaciones apuntan a los archivos
  dañados y siguen funcionando porque usan la ruta del historial.

## Verificaciones pendientes de Biglex

- [ ] Probar en escritorio: una descarga de video y una de audio, y comprobar
      las carpetas `audio` y `video`.
- [ ] Probar en el teléfono real: `.\gradlew installDebug` y una descarga, para
      validar la creación de las subcarpetas en Android.
- [ ] Probar el hover de la tarjeta de resultado y confirmar que el resplandor
      respeta las esquinas.
