# 🚀 ¡Nuevo Lanzamiento de Luna Fetch! — v1.1.4

¡Nos complace anunciar la versión **v1.1.4** de **Luna Fetch**! 🎉

### 📝 Resumen
Esta actualización adopta de forma integral los nuevos estándares de arquitectura para aplicaciones de escritorio: incluye la Garantía de Instancia Única (*Single-Instance Lock*) con bypass para desarrolladores, la memoria persistente del estado y tamaño de ventana entre sesiones y optimizaciones visuales en los avisos emergentes.

### 🌟 Novedades Destacadas
- 🔒 **Garantía de Instancia Única (Single-Instance Lock)**: Evita procesos duplicados e iconos repetidos en la bandeja de entrada (*System Tray*) al relanzar la app. Incluye bypass automático para entorno de desarrollo (`isDev`).
- 📐 **Memoria de Ventana Persistente**: La aplicación recuerda su tamaño (ancho/alto), posición en pantalla y estado de maximizado (`isMaximized`) entre sesiones.
- ⚡ **Auto-Actualización & Notificaciones**: Notificación flotante emergente Toast de 4 segundos centrada en la parte superior.
- 💬 **Centro de Feedback**: Canal oficial de soporte e incidencias vinculado a GitHub Issues.
