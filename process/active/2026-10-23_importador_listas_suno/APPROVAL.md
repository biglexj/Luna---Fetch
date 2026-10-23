# Aprobación — Importador de Listas de Suno

Fecha: 2026-10-23
Estado: **implementado y validado en laboratorio**; pendiente la comprobación
final sobre la sesión real de Suno.

## Decisiones tomadas

| Decisión | Motivo |
|---|---|
| La publicación se hace siempre desde la interfaz real del navegador | No se falsea ni se replica la API de Suno en el camino principal; el modo API es un acelerador opcional que degrada sin romperse |
| El enlace canónico es `https://suno.com/song/<uuid>` | Es la única forma que el extractor del escritorio puede leer sin seguir redirecciones |
| La forma de los enlaces vive en `suno-links.js` | Una sola fuente de verdad para el popup, el importador y el hook |
| El envío de un lote es una sola petición (`POST /download/batch`) | Evita N idas y venidas y devuelve un recuento honesto |
| Las cookies de la sesión viajan en el propio envío | La sincronización ya existía para YouTube; se amplía a `suno.com` y se propaga a la descarga |
| **No** se implementa el borrado de canciones en Suno | Es destructivo e irreversible en el servicio. Queda como paso explícito y separado, solo si Biglex lo pide, y solo después de verificar que el archivo local existe y pesa lo esperado |

## Alcance entregado

- Extensión: hook de portapapeles y de red, importador con cuatro modos,
  panel de progreso, modo diagnóstico y normalizador compartido.
- Popup: sección Suno con importación de lista, autoenvío de enlaces copiados y
  envío desde el portapapeles del sistema.
- Escritorio: endpoint de lote, propagación de `cookieFile` en toda la cadena
  y lectura de la ficha real de Suno con cabecera `Cookie`.

## Qué falta para darlo por cerrado

1. Biglex ejecuta el importador sobre la lista real «Espacio Vtuber» con la
   extensión cargada en el navegador.
2. Se comprueba la llegada de los MP3 a `<destino>/audio/` con portada y letra.
3. Se calibra la receta del modo API contra el endpoint real (o se deja el modo
   API desactivado y se usa el método de interfaz, que ya es suficiente).

## Riesgo residual

- **Selectores de la interfaz de Suno.** El importador no depende de clases CSS
  concretas: localiza el menú y el diálogo por diferencia del DOM y por texto
  multilingüe. Si Suno renombra los menús, el modo diagnóstico permite
  recalibrar en una sola iteración.
- **Modo API sin verificar en producción.** La receta se aprende de la sesión
  real y solo se usa en modo explícito; si falla, el importador vuelve al
  método de interfaz sin perder enlaces (verificado en el banco de pruebas).
- **Límite de 20 canciones de Suno.** El plan no elimina canciones del
  servicio: la liberación de ranuras sigue siendo una decisión explícita de
  Biglex.
