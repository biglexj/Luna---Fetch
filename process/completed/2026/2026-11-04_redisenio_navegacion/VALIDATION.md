# Validación

- `mise test`: BUILD SUCCESSFUL; 104 pruebas sin fallos, incluidas cuatro
  regresiones del registro de inicio automático.
- Inicio instalado con `--autostart`: proceso residente y respuesta HTTP del
  servidor local en 127.0.0.1:51234. La ruta de Run apuntaba a una instalación
  inexistente; ahora apunta al ejecutable en LocalAppData/Programs/LunaFetch.
- Detectada y corregida la declaración de `isWindows` posterior a `init`, que
  tomaba la rama de Linux durante la reparación inicial del registro.
- Interfaz DEV revisada con capturas: Descargas, General, cambio al tema oscuro,
  Synapse y Acerca de; ventana normal y maximizada.
- Las correcciones posteriores a esa revisión retiran la cabecera global,
  usan Ajustes como título, amplían Acerca de y reubican los accesos laterales.
- Pendientes: revisión visual final de estas correcciones, prueba en Android,
  aceptación tras reiniciar Windows y actualización de la instalación estable.
- No se han ejecutado descargas ni actualizaciones del motor durante la revisión.
