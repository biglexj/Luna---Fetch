# 🚀 Luna Fetch v1.3.0 — Rediseño de Navegación, Ajustes Integrados y Suno AI

Luna Fetch 1.3.0 renueva por completo la experiencia visual y funcional en escritorio y móvil, introduciendo una barra lateral de navegación adaptable inspirada en Material 3 Expressive, un panel de Ajustes integrado en pantalla completa, un instalador nativo para Windows ligero y sin elevación, e integración avanzada con Suno AI para la importación por lotes de canciones y playlists con metadatos reales.

---

### Novedades destacadas

- **Navegación adaptable y panel de Ajustes integrado**: Nueva barra lateral (NavigationRail) que centraliza Descargas e Historial y ancla Ajustes. Se sustituyen los diálogos flotantes por una pantalla completa con pestañas temáticas (General con selector visual de tema, Sincronización Synapse y Acerca de / Actualizaciones), mejorando la legibilidad y el aprovechamiento de la pantalla.
- **Instalador de Windows modernizado (Inno Setup)**: Empaquetado nativo rápido con Biglex Modern UI 2.0. Instalación directa por usuario en `%LOCALAPPDATA%\Programs\LunaFetch` sin solicitar permisos de administrador, con soporte automático para temas claro y oscuro, esquinas redondeadas en Windows 11 y actualización limpia en el mismo directorio.
- **Integración con Suno AI y extracción de alta fidelidad**: Captura de canciones y playlists de Suno AI desde la extensión de navegador en un solo clic. El extractor nativo procesa las cookies de sesión para obtener título, artista, estilo, carátula incrustada y letra sincronizada, indicando de forma explícita las pistas instrumentales.
- **Organización automática por tipo de medio**: Cada descarga se clasifica automáticamente en las carpetas estándar «Música» o «Videos» dentro de tu directorio de destino, tanto en Windows como en Android.
- **Conexión robusta con la extensión de navegador**: El servidor local resuelve de forma precisa la longitud de peticiones por bytes UTF-8, garantizando transferencias estables sin desconexiones e incorporando verificación de estado en tiempo real.
- **Compilación multiplataforma optimizada**: Pipeline de lanzamiento independiente que genera y firma por separado el instalador de Windows y el paquete APK de Android.

---

### Descargas e instalación

- **Windows (x64)**: [LunaFetch-Windows-1.3.0.exe](https://github.com/biglexj/Luna---Fetch/releases/download/v1.3.0/LunaFetch-Windows-1.3.0.exe)
- **Extensión de navegador (v1.4.4)**: Disponible en la carpeta `browser-extension` del repositorio (carga descomprimida en Chrome, Edge, Brave u Opera).

---

### Soporte oficial

- **Sitio oficial**: https://www.biglexj.com/
- **GitHub**: https://github.com/biglexj/Luna---Fetch
- **Reportar un problema**: https://github.com/biglexj/Luna---Fetch/issues

¡Gracias por usar Luna Fetch! 🌙