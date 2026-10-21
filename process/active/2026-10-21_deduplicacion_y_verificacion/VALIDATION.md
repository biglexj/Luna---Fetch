# Validación: Deduplicación y Verificación de Archivos

Fecha: 2026-10-21

## 1. Compilación

| Módulo | Tarea | Resultado |
|---|---|---|
| commonMain | `:composeApp:compileKotlinDesktop` | BUILD SUCCESSFUL |
| androidMain | `:composeApp:compileDebugKotlinAndroid` | BUILD SUCCESSFUL |

## 2. Pruebas automáticas

`:composeApp:desktopTest` → **74 pruebas, 2 fallos**.

Los dos fallos son `SunoExtractorTest.testShortUrlFetch` y
`SunoExtractorTest.testSongAnalyzeLive`: pruebas contra el servicio en línea de
Suno, que ya fallaban antes de este trabajo (no dependen de los cambios).

| Clase | Pruebas | Fallos |
|---|---|---|
| DownloadQueueTest | 11 | 0 |
| DownloadRegistryTest | 6 | 0 |
| LinkCanonicalizerTest | 7 | 0 |
| LunaSocketServerQueueTest | 2 | 0 |
| YtdlpProtocolTest | 7 | 0 |
| Las demás (sin cambios) | 41 | 2 (Suno, datos en línea) |

### Pruebas añadidas

- `LinkCanonicalizerTest`: `watch?v=`, `youtu.be/`, `shorts/` y `embed/` producen
  la misma clave; los parámetros de seguimiento se descartan; los
  significativos se conservan; TikTok normaliza la ruta.
- `DownloadRegistryTest`: búsqueda por contenido y formato, actualización sin
  duplicar, límite de 200 entradas y cálculo de la carpeta madre.
- `DownloadQueueTest`: duplicado en la cola ignorado; formatos distintos del
  mismo video no son duplicados; archivo en pie ignorado; archivo vacío se
  rehace y se reemplaza en su carpeta; archivo ausente se descarga normal; la
  descarga completada queda registrada; **ocho envíos simultáneos del mismo
  enlace solo encolan uno**.
- `LunaSocketServerQueueTest`: el servidor responde `queued:false` con
  `reason:duplicate_queue` ante un enlace repetido.

### Defectos encontrados y corregidos durante la validación

1. **Import faltante en el layout de tres columnas**: `LunaFetchApp.kt` no
   compilaba por `Unresolved reference 'fillMaxWidth'`. El working tree
   heredado de la sesión anterior estaba roto. Corregido.
2. **Barra de la ruta perdida en la clave canónica**: `web:example.comvideo?id=7`
   en lugar de `web:example.com/video?id=7`. Detectado por
   `meaningfulParametersAreKept`. Corregido.
3. **Condición de carrera en la deduplicación**: la comprobación de duplicados y
   el encolado se hacían en dos bloques separados. Con tres peticiones
   simultáneas (verificado contra la aplicación real) las tres pasaban el filtro
   y se encolaban. Corregido: comprobación y encolado en un solo bloque
   sincronizado.

## 3. Verificación contra la aplicación real

Aplicación de escritorio ejecutada desde Gradle en el puerto alterno 51244
(`--project-prop=lunafetch.port=51244`) para no interferir con la instancia
instalada.

### Duplicados concurrentes (tres enlaces equivalentes al mismo video)

```
https://youtu.be/lunafetchDemoFalso
https://www.youtube.com/watch?v=lunafetchDemoFalso&utm_source=lunafetch-test
https://m.youtube.com/shorts/lunafetchDemoFalso
```

Respuestas:

```
{"ok":true,"queued":true,"pending":1}
{"ok":true,"queued":false,"reason":"duplicate_queue","pending":1}
{"ok":true,"queued":false,"reason":"duplicate_queue","pending":1}
```

Un enlace distinto sí se encola: `{"ok":true,"queued":true,"pending":2}`.

### Estados del archivo

| Estado del archivo | Respuesta del servidor | Resultado |
|---|---|---|
| En pie (2048 bytes) | `{"ok":true,"queued":false,"reason":"already_downloaded"}` | Ignorado |
| Vacío (0 bytes) | `{"ok":true,"queued":true,"pending":1}` | Se rehace y se reemplaza |
| Movido (ruta inexistente) | `{"ok":true,"queued":true,"pending":1}` | Se descarga normal |
| URL inválida | HTTP 400 `{"ok":false,"error":"URL no válida"}` | Rechazada |

El mismo video en otro formato (MP3) sí se encola: el formato distingue el
video de su audio.

### Verificación visual

Capturas de la aplicación real en `temp/verificacion/`:

- `luna-dos-columnas.png` (1047 px, ventana normal): **dos columnas**. Izquierda,
  enlace de descarga y cola de descargas; derecha, la tarjeta de descarga con
  el selector de calidad y, justo debajo, el historial. Con tres columnas en
  una ventana mediana todo quedaba muy aplastado.
- `luna-tres-columnas-ancha.png` (1850 px, pantalla grande): **tres columnas**.
  Izquierda, enlace y cola; centro, descarga, detalles del error y registro;
  derecha, el historial.
- `luna-layout-final.png` (1850 px): el detalle de los distintivos y de los
  dispositivos de descarga.
  - Distintivo **DEV** junto al título "Luna Fetch" y en el título de la
    ventana ("Luna Fetch [DEV]"). La instancia instalada se titula "Luna Fetch"
    sin distintivo.
  - Los dispositivos de descarga ocupan dos columnas y usan todo el ancho:
    "Este equipo" y "Motorola motorola edge 60 fusion" se leen enteros.
- `luna-cola-izquierda.png`: la cola de descargas vive en la columna izquierda,
  debajo del enlace, con dos elementos encolados. El botón "Descargar MP4"
  conserva su sitio en la tarjeta de descarga: antes, la cola iba en la columna
  central y lo desplazaba hacia abajo.
  - La versión compacta mantiene el mismo orden: enlace, cola, vista previa,
    opciones, historial, estado y registro.
- `luna-tres-columnas-dev.png`: la primera versión con el historial repartido
  en varias columnas internas, que se descartó porque los títulos quedaban
  ilegibles y los avisos se apilaban letra por letra.
- `luna-historial.png`: los tres estados del archivo (en pie, dañado, movido).
- `luna-window.png`: la cola marca las reparaciones con "♻️ reparación".

## 4. Pendiente

- **Re-empaquetar `browser-extension.crx`**: el código fuente de la extensión
  está actualizado (versión 1.4.3) y el popup distingue los duplicados, pero el
  `.crx` versionado no se pudo regenerar en esta máquina porque no hay Chrome
  instalado y Edge no admite `--pack-extension`. Se empaqueta desde Chrome.
- **Prueba en teléfono físico**: la regla de reparación en Android se compila y
  está cubierta por pruebas de plataforma, pero falta la verificación en
  dispositivo real (regla del proyecto).

## 5. Restauración del entorno

Durante la verificación se escribieron datos de prueba en las preferencias de la
aplicación. Se restauraron al terminar:

- Historial: las 10 entradas reales del usuario intactas, sin las de prueba.
- Registro de descargas: vacío (la capacidad es nueva).
- Tamaño de ventana: 1047x793, maximizado (como estaba).
- Diagnósticos de 48 h: sin tocar.
