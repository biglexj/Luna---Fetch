# Luna YT-DLP Downloader 🌙

Una interfaz gráfica moderna, rápida y premium para descargar videos y música de YouTube y miles de sitios más, utilizando la potencia de `yt-dlp` y `ffmpeg`. Diseñada bajo las directrices cromáticas y tipográficas del **Sistema de Diseño Aurora**.

---

## 🎨 Características Principales
*   **Diseño Aurora Integrado**: Estética premium con el color de acento *Ely Green* (turquesa), esquinas redondeadas y contraste optimizado.
*   **Detección Automática de Tema**: Se adapta al tema de tu sistema Windows (Modo Oscuro / Modo Claro) al iniciar y permite alternarlo manualmente mediante un botón con iconos vectoriales SVG.
*   **Selector Avanzado de Formatos**:
    *   **Video**: Descarga y remuxa en formatos `.mp4` o `.webm`.
    *   **Audio / Música**: Extrae y convierte a formatos `.mp3`, `.m4a` o `.flac` utilizando `ffmpeg`.
*   **Calidad Dinámica**:
    *   Para video: Elige la resolución deseada (1080p, 720p, 480p, 360p) según las disponibles en la fuente original.
    *   Para audio: Elige el bitrate deseado (320 kbps, 192 kbps, 128 kbps).
*   **Barra de Progreso Premium**: Muestra velocidad de descarga en vivo, tamaño de archivo, porcentaje y tiempo estimado (ETA).
*   **Consola de Logs Técnica**: Desplegable interactivo para ver los registros del proceso en tiempo real si ocurren errores.
*   **Portable**: Diseñado para compilarse como un ejecutable único y portátil de Windows sin necesidad de instalador.

---

## 🚀 Requisitos Previos

Asegúrate de tener instalados en tu sistema:
1.  **yt-dlp**: El motor de descarga.
2.  **ffmpeg**: Necesario para combinar audio y video de alta calidad o realizar conversiones de música.

### Instalación rápida mediante Winget (PowerShell):
```powershell
winget install yt-dlp.yt-dlp
winget install Gyan.FFmpeg
```

---

## ⚙️ Desarrollo y Compilación

La aplicación está desarrollada con **C# WPF** en **.NET 10**.

### Ejecución en desarrollo:
```powershell
dotnet run
```

### Generación del Ejecutable Portable (Single File):
Hemos incluido un script en la carpeta de scripts para generar una compilación autocontenida de un solo archivo. Abre PowerShell en la raíz del proyecto y ejecuta:

```powershell
.\scripts\build-exe.ps1
```

Esto generará el archivo portable ejecutable en la carpeta `publish\exe\LunaYtdlp.exe`.

---

## 📄 Licencia y Privacidad
Este proyecto está licenciado bajo la licencia **MIT** (Ver [LICENSE](LICENSE)).
Consulta la [Política de Privacidad](PRIVACY.md) para más información sobre cómo manejamos tus datos locales.
