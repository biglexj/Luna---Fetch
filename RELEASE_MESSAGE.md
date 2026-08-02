# 🚀 ¡Nuevo Lanzamiento de Luna Fetch! — v1.1.5

¡Nos complace anunciar la versión **v1.1.5** de **Luna Fetch**! 🎉

### 📝 Resumen
Esta actualización corrige de forma definitiva el desplegado y reporte interno de versión en la interfaz gráfica, centralizando el número de versión a través de `AppConfig.APP_VERSION`.

### 🌟 Novedades Destacadas
- 🏷️ **Versión Dinámica Centralizada**: Eliminados los textos estáticos hardcodeados en la interfaz de Ajustes y en el motor de comprobación de actualizaciones (`AppConfig.APP_VERSION`).
- ⚡ **Verificación In-App Exacta**: Elimina los falsos positivos donde la app notificaba "Nueva actualización disponible" aun estando en el último lanzamiento.
- ⚙️ **Automatización de Builds**: `build-release.ps1` sincroniza automáticamente la versión en el código fuente Kotlin, artefactos ejecutable (.exe / .msi), paquetes Android y manifiestos de Winget.
