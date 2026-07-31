# 🌙 Luna Fetch 1.1.1

Optimización visual para modales en móviles (ancho al 80%), transición fluida sin parpadeos, renderizado limpio de Markdown y corrección del tema oscuro del sistema en Android.

- 📐 **Ancho de Modal al 80%**: Contenedor del diálogo optimizado al 80% de la pantalla en móviles (`usePlatformDefaultWidth = false`) con botones "Descargar" e "Instalar" adaptativos en una sola línea.
- ⚡ **Transición Fluida entre Modales**: Cierre inmediato del cuadro "Acerca de" y sustitución en el mismo fotograma por el Modal Central sin pantallas vacías ni parpadeos.
- 📜 **Renderizado Limpio en Markdown**: Parseador de notas de versión que elimina caracteres de formato plano (`#`, `**`, `-`) para presentar las novedades estilizadas con viñetas.
- 🌙 **Estabilidad de Tema Oscuro en Android**: Corrección del bucle de verificación de tema en Android para seguir fielmente la configuración del sistema sin regresiones a modo claro.
- 🛡️ **Ocultamiento de Banners Duplicados**: Desactivación del `UpdateBanner` superior mientras el Modal Central de actualización permanece activo.
