# Aprobación: Cola de Descargas para Envíos Externos

## Estado

Implementado y validado. **Pendiente de confirmación de Biglex** (revisión del
comportamiento en su flujo real con la extensión y decisión sobre publicar una
nueva versión).

## Decisiones tomadas

1. La cola es **secuencial** (un enlace a la vez). Es lo que pidió Biglex y
   evita competir por el motor de descarga.
2. La cola vive en el presentador (`commonMain`), por lo que beneficia a
   escritorio y Android por igual, y a todos los orígenes de envío
   (extensión, Aurora Synapse LAN, Synapse dispatcher, historial).
3. Los enlaces fallidos no detienen la cola: se marcan como fallidos y se
   continúa con el siguiente.
4. Sin carpeta de destino, la cola se pausa (en vez de marcar todo como fallido)
   y se reanuda al elegir la carpeta.

## Pendiente de decisión

- Publicación de una nueva versión (la app instalada del usuario sigue en
  `1.2.0`; este cambio es local y no está empaquetado).
- Recarga de la extensión en el navegador para tomar el popup actualizado.

