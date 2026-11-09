# Validación: Carpetas por Tipo de Medio, Soporte Suno y Hover con Radio

## Fecha

2026-10-23

## 1. Compilación

| Tarea | Resultado |
|---|---|
| `:composeApp:compileKotlinDesktop` | OK |
| `:composeApp:compileDebugKotlinAndroid` | OK |

Aviso resuelto durante la validación: `ProcessBuilder.Redirect.DISCARD` no
existe en Android, por lo que la salida de FFmpeg se drena en un hilo (solución
válida en ambas plataformas).

## 2. Pruebas automatizadas

```
:composeApp:desktopTest
BUILD SUCCESSFUL — 92 pruebas completadas, 0 fallidas
```

- ✅ `MediaFoldersTest` (6 pruebas nuevas): separación audio/video, carpeta
  añadida al destino, tolerancia con separadores finales, ausencia de doble
  anidación y destino vacío intacto.
- ✅ `SunoMetadataParsingTest` (3 pruebas nuevas): ficha completa desde el
  payload con las «personas» de Suno como distractores, recurso a la cabecera
  HTML y título genérico cuando no hay nada.
- ✅ `SunoRealPageParsingTest` (1 prueba nueva): análisis de una **captura real**
  del payload de Suno (`composeApp/src/desktopTest/resources/suno_song_page.html`):
  título «Y Que Pasó», autor «Biglex J», alias `biglexj`, duración, estilo,
  letra completa y portada exacta.
- ✅ Pruebas existentes de Suno contra el sitio en vivo: análisis del enlace
  corto, análisis de la ficha y descarga FLAC con letra adjunta.

## 3. Comprobación en el sitio real

Descarga real de `https://suno.com/song/6a011fc0-5d07-4810-b422-b3f8af4032fc`:

```
LOG: Iniciando extracción de Suno AI para: Y Que Pasó
LOG: Audio transcodificado con metadatos ID3 y portada incrustada.
LOG: Letra lírica guardada en: Y Que Pasó - Biglex J.lyrics.txt
ARCHIVOS: [Y Que Pasó - Biglex J.lyrics.txt, Y Que Pasó - Biglex J.mp3]
```

- Etiquetas del MP3 (`ffprobe`): `title=Y Que Pasó`, `artist=Biglex J`,
  `album=Suno AI`, `genre=Latin pop with layered synth stereo widening…`.
- Carátula incrustada: marco `APIC` presente en la etiqueta ID3.
- Sin temporales:left over (`.suno_cover.jpg` ni `_suno_source.mp4`).
- Tiempo de conversión: ~20 s. Antes el proceso se bloqueaba (600 s sin
  terminar) por el canal de salida de FFmpeg sin leer.

## 4. Comprobación de las subcarpetas

Descarga real con `yt-dlp` en escritorio (`https://www.youtube.com/watch?v=jNQXAC9IVRw`):

```
temp/folder_check/
├── audio/Me at the zoo.mp3
└── video/Me at the zoo.mp4
```

## 5. Partes verificadas por inspección

- La reparación escribe en la carpeta padre del archivo dañado, que ya
  vive en `…/audio` o `…/video`: `MediaFolders.resolve()` detecta el sufijo y
  no genera `audio/audio`.
- El hover de la tarjeta de resultado usa ahora `Card(onClick = …)`, cuya
  indicación se dibuja sobre la superficie con el radio de 24 dp.
