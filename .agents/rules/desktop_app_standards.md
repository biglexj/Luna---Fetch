---
trigger: always_on
---

# Estándares de aplicaciones de escritorio

> [!NOTE]
> Este archivo es un **puente** a la Documentación Core. La norma vive en Core; aquí solo se registran las decisiones propias de Luna Fetch.

Fuentes canónicas:

- [Docs/types/desktop/desktop_app_standards.md](../../../Docs/types/desktop/desktop_app_standards.md): índice de compatibilidad y ubicación actual de cada concepto.
- [Docs/types/desktop/README.md](../../../Docs/types/desktop/README.md): base del tipo de proyecto de escritorio.
- [Docs/features/single-instance/README.md](../../../Docs/features/single-instance/README.md): instancia única y reactivación.
- [Docs/features/system-tray/README.md](../../../Docs/features/system-tray/README.md): bandeja del sistema.
- [Docs/types/desktop/window-state.md](../../../Docs/types/desktop/window-state.md): estado, tamaño y posición de la ventana.

## Decisiones de Luna Fetch

- Luna Fetch **sí** aplica instancia única: es una aplicación residente en la bandeja del sistema con atajos globales.
- El bloqueo de instancia tiene bypass en desarrollo mediante la propiedad de sistema `-Dlunafetch.dev=true`, que inyecta la tarea `:composeApp:run`.
