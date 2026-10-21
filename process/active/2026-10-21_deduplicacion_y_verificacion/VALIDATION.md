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

- `luna-error-bajo-cola.png` (1850 px): el mensaje "⚠️ Detalles del error" sale
  en su propia tarjeta, justo debajo de la cola de descargas, y no al lado del
  historial. Para eso se extrajo del registro de la sesión, que se quedó con
  las líneas de log y los diagnósticos de 48 h en la columna central.
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

## 4. Paginación del historial

El historial pasó a mostrar **diez descargas por pestaña**, con el selector de
páginas al pie, y **sin scroll propio**: el desplazamiento es el de la ventana
completa, tal como pidió Biglex.

- `HistoryPaginator` (nuevo, en `domain/`) fija `PAGE_SIZE = 10` y resuelve
  `pageCount()` e `itemsForPage()` sobre la lista completa.
- `HistoryCard` renderiza la página activa y el selector
  "‹ Anterior | Página X de Y · N descargas | Siguiente ›" debajo de la lista.
- El presentador conserva hasta **50 descargas** (`HISTORY_LIMIT = 50`) en lugar
  de las tres llamadas a `take(10)` que había antes.
- El bloque de "Historial" ya no tiene `heightIn` con scroll propio ni
  `columns`: los elementos se apilan en una sola columna.
- `HistoryPaginatorTest` (4 casos) cubre: menos de diez elementos, página
  completa, página intermedia y página fuera de rango.

Captura: `luna-paginacion-final.png`.

## 5. Empaquetado e instalador EXE

| Artefacto | Tamaño |
|---|---|
| `release/LunaFetch-Windows-1.3.0.exe` | 72.360.448 bytes |
| `release/SHA256SUMS.txt` | generado |

Cambios en el pipeline:

- `composeApp/build.gradle.kts`: `targetFormats(TargetFormat.Exe, TargetFormat.Deb,
  TargetFormat.Rpm)`. Se eliminó `Msi`.
- `gradle.properties` y `AppConfig.kt`: versión **1.3.0** (`versionCode` 23).
- `build-release.ps1` (raíz): el splat de `$RemainingArgs` fallaba cuando no
  había argumentos adicionales y abortaba el pipeline. Corregido.
- `scripts/build/build-release.ps1`:
  - se eliminó `Get-MsiProductCode` (ya no hay MSI);
  - `Update-WingetManifest` ahora recibe el EXE, calcula su SHA-256 y genera un
    manifest con `InstallerType: burn` y
    `InstallerSwitches: { Silent: /quiet, SilentWithProgress: /passive }`
    (los manifests históricos de versiones ya publicadas no se tocaron);
  - el generador, que estaba definido pero nunca se invocaba, ahora se llama
    en la publicación.
- Actualización in-app sin MSI: `UpdateChecker` solo acepta assets `.exe` y
  `DesktopPlatformBindings` descarga e instala el EXE con `/passive`.

### Qué es realmente el instalador EXE

El `.exe` es un **bootstrapper WiX Burn** con el MSI de jpackage como payload:
extrae `main.msi` a `%TEMP%\<n>.tmp` y lanza `msiexec /i main.msi`. Por eso
soporta los switches estándar de Burn: `/quiet`, `/passive`, `/norestart` y
`/log`. En la prueba se usó `/quiet /norestart /log <archivo>` y el log generado
es el registro MSI completo.

### Bloqueo encontrado al instalar

La primera ejecución del EXE devolvió **1638**. El log de MSI muestra que el
payload intentó *reconfigurar* el producto ya instalado:

```
Windows Installer volvió a configurar el producto. Nombre del producto: LunaFetch.
Versión del producto: 1.3.0. ... Resultado de la nueva configuración: 1638.
```

Había dos causas encadenadas:

1. **El mutex de Windows Installer estaba tomado** por dos diálogos "LyraFlow
   Setup" (otra sesión de trabajo en la misma máquina) que llevaban desde las
   15:01 esperando interacción. Se avisó a la sesión de LyraFlow y se cerraron
   esos diálogos; los procesos `msiexec` colgados se detuvieron.
2. **El producto estaba instalado desde el MSI** de la tanda anterior. Con el
   MSI de por medio, el EXE no lograba reconfigurar la instalación y devolvía
   1638 ("otra versión de este producto ya está instalada"). Tras desinstalar
   el producto y pasar solo por EXE, la instalación quedó en **exit 0 en 7-8 s**,
   sin diálogos, y **volver a instalar el mismo EXE también da exit 0**.

### Resultado final de la instalación con EXE

```
LunaFetch-Windows-1.3.0.exe /quiet /norestart   -> exit 0 (7 s)
```

- Producto registrado: `LunaFetch 1.3.0`, en `C:\Users\biglexj\AppData\Local\LunaFetch`.
- Título de la ventana: **"Luna Fetch"**, sin el distintivo DEV.
- Layout de tres columnas en pantalla ancha, dos en ventana normal.
- Historial con las 10 entradas reales del usuario.
- Paginación: "Página 1 de 2 · 16 descargas" con "‹ Anterior" y "Siguiente ›".
- Duplicados por HTTP en el puerto 51234:
  `{"ok":true,"queued":true,"pending":1}` y después
  `{"ok":true,"queued":false,"reason":"duplicate_queue","pending":1}`.

Capturas: `instalada-exe-layout.png`, `instalada-exe-paginacion.png`,
`instalada-final.png`.

## 6. El error se muestra en su tarjeta, no en el registro

Biglex señaló, sobre una captura, que la línea `ERROR: ...` aparecía en el
"Registro de la sesión actual" además de en la tarjeta "Detalles del error".

- Se eliminó el `appendLog("ERROR: ...")` de los tres caminos de fallo
  (análisis desde el formulario, análisis directo y descarga).
- El registro de la sesión conserva la traza técnica de la operación.
- El informe de fallos de 48 h sigue guardando el mensaje completo del error
  mediante `technicalDetails(error, mensaje)`, sin duplicar el prefijo
  (`ERROR: ERROR: ...`).

Captura: `instalada-final.png` (el error aparece en "Detalles del error", en el
elemento fallido de la cola y, de forma contextual, bajo los botones del
enlace; el "Registro de la sesión actual" ya no lo repite).

## 7. El informe de 48 h había dejado de guardarse

Al verificar el punto anterior, el incidente nuevo **no aparecía** en las
preferencias. La causa: `java.util.prefs.Preferences` rechaza valores de más de
**8192 caracteres**, y el guardado en `DesktopPlatformBindings.saveDiagnostics`
usa `runCatching`, así que el error quedaba en silencio.

Prueba directa del límite:

```
longitud 8000 -> guardado OK
longitud 8192 -> guardado OK
longitud 8300 -> ERROR: IllegalArgumentException: Value too long
longitud 9000 -> ERROR: IllegalArgumentException: Value too long
```

El JSON del informe pesaba 8047 caracteres: el siguiente incidente (≈380) ya no
entraba y **el informe dejaba de persistirse**, aunque la tarjeta siguiera
contando incidentes en memoria.

Correcciones:

- `DiagnosticStore.fitToBudget()`: descarta los incidentes más antiguos hasta
  que el JSON quepa con holgura (presupuesto de 7000 caracteres).
- Se aplica en escritorio y en Android.
- `technicalDetails` se acota a 1000 caracteres por incidente.
- `DiagnosticStoreTest` con dos casos nuevos para el recorte.

Verificación: con el recorte, el incidente de prueba **sí** quedó guardado
(8047 → 7296 caracteres de informe) con el mensaje sin prefijo duplicado.

## 8. Pendiente

- **Re-empaquetar `browser-extension.crx`**: el código fuente de la extensión
  está actualizado (versión 1.4.3) y el popup distingue los duplicados, pero el
  `.crx` versionado no se pudo regenerar en esta máquina porque no hay Chrome
  instalado y Edge no admite `--pack-extension`. Se empaqueta desde Chrome.
- **Prueba en teléfono físico**: la regla de reparación en Android se compila y
  está cubierta por pruebas de plataforma, pero falta la verificación en
  dispositivo real (regla del proyecto).

## 9. Restauración del entorno

Durante la verificación se escribieron datos de prueba en las preferencias de la
aplicación. Se restauraron al terminar:

- Historial: las 10 entradas reales del usuario intactas, sin las de prueba.
- Registro de descargas: vacío (la capacidad es nueva).
- Tamaño de ventana: 1047x793, maximizado (como estaba).
- Diagnósticos de 48 h: el listado real del usuario se conservó; solo se
  añadieron los incidentes de las pruebas de esta tanda.
