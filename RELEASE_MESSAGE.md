# 🚀 Luna Fetch — v1.2.1

Instalador de Windows modernizado con Inno Setup e importador de listas de Suno desde el navegador.

## 📝 Resumen

Luna Fetch 1.2.1 es el parche sobre la 1.2.0 publicada en GitHub. El instalador EXE de Windows deja de generarse con el bootstrapper de WiX y pasa a generarse con la plantilla propia de Biglex en Inno Setup. Además, la extensión de navegador aprende a importar listas de Suno de una sola vez.

## 🔔 Novedades destacadas

- 🛠️ **Instalador de Windows modernizado (Inno Setup)**: Pantalla única de entrada con presentación, ruta editable y botón principal, progreso y éxito nativos, tema claro/oscuro automático según Windows, esquinas nativas de Windows 11 e ícono centrado. La instalación es por usuario y sin elevación, y la actualización se aplica en el mismo directorio sin instalar en paralelo. La coexistencia con el instalador anterior queda documentada en `packaging\windows\MIGRATION.md`.

- ☀️ **Importador de listas de Suno**: La extensión publica en lote las canciones de la lista abierta de Suno, captura el enlace público de cada una sin copiarlo a mano y las envía a Luna Fetch en un solo envío. Todo ocurre dentro de la pestaña de Suno ya autenticada. Incluye autoenvío de los enlaces públicos que copies dentro de Suno, un modo acelerado por API que degrada al método de interfaz si Suno deja de responder, y un modo diagnóstico para recalibrar los selectores si la web cambia.

- 🎵 **Ficha real también en canciones privadas**: Las cookies de la sesión de Suno llegan al extractor, de modo que el título, el autor, el estilo, la letra y la portada son los auténticos aunque la canción no sea pública.

## 📦 Windows x64

[Descargar Instalador (Windows x64)](https://github.com/biglexj/Luna---Fetch/releases/download/v1.2.1/LunaFetch-Windows-1.2.1.exe)

¡Gracias por usar Luna Fetch! 🌙