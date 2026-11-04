---
trigger: always_on
---

# Lenguaje de diseño

> [!NOTE]
> Este archivo es un **puente** a la Documentación Core. La norma vive en Core; aquí solo se registran las decisiones propias de Luna Fetch.

Fuentes canónicas:

- [Docs/global/design/design_system.md](../../../Docs/global/design/design_system.md): lenguaje de diseño compartido y criterios comunes de composición.
- [Docs/features/design-system/design-system-baseline.md](../../../Docs/features/design-system/design-system-baseline.md): base del sistema de diseño.
- [Docs/global/design/interface-writing.md](../../../Docs/global/design/interface-writing.md): textos de botones, ajustes, avisos y descripciones.

## Decisiones de Luna Fetch

- Luna Fetch adopta **Material 3 Expressive** en sus superficies Compose (colores tonales, contenedores elevados, micro-movimiento).
- Los diálogos y modales en Android y móvil se configuran con `DialogProperties(usePlatformDefaultWidth = false)` y `fillMaxWidth(0.80f)`.
