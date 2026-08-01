# 🌙 Luna Fetch 1.1.3

Corrección en la ejecución del auto-instalador ejecutable en Windows y estabilización completa de las actualizaciones in-app.

- 🛠️ **Lanzador Nativo en Windows**: Corrección del invocador del instalador en PC (`ProcessBuilder`) para ejecutar directamente el archivo descargado (`.exe` / `.msi`) al pulsar "Instalar", eliminando bloqueos en hilos de AWT.
- ⚙️ **Modal de Ajustes Unificado**: Integración completa del diálogo "Acerca de" dentro de Ajustes con comprobación de versión y temporizador suave de 3 segundos (`AnimatedVisibility`).
- 🏎️ **Controladores del Motor**: Panel de control e instalador/actualizador en tiempo real de `yt-dlp` con sanitización de registros a texto limpio de interfaz.
- 📂 **Arquitectura Modular por Dominio**: Reorganización estructural de la capa UI en sub-carpetas especializadas (`header`, `download`, `history`, `logs`, `update` y `components`).
- 🧩 **Extensión de Navegador**: Guía interactiva de instalación manual en Chrome, Edge, Brave y Opera (`browser-extension/README.md`) y registro en 1 clic del Native Host en Windows.
