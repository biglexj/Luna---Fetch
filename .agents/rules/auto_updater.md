---
trigger: always_on
---

# Auto-actualización desde GitHub Releases

> [!NOTE]
> Este archivo es un **puente** a la Documentación Core. La norma vive en Core; aquí solo se registra lo propio de Luna Fetch.

La regla compartida está en [Docs/features/auto-updater/auto_updater.md](../../../Docs/features/auto-updater/auto_updater.md) y su flujo de escritorio en [desktop-update-flow.md](../../../Docs/features/auto-updater/desktop-update-flow.md).

## particularidades de Luna Fetch

- El comprobador consulta `https://api.github.com/repos/biglexj/Luna---Fetch/releases/latest`.
- La verificación de arranque es silenciosa; la comprobación manual desde el diálogo «Acerca de» cierra ese diálogo en la misma acción y abre el modal de actualización al 80 % de ancho.
- El diálogo «Acerca de» de Luna Fetch es una página completa: el cierre sincrónico del diálogo flotante no aplica.
