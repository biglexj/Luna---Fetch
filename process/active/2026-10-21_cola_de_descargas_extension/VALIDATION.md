# Validación: Cola de Descargas para Envíos Externos

## Fecha
2026-10-21

## 1. Compilación

| Tarea | Resultado |
|---|---|
| `:composeApp:compileKotlinDesktop` | OK |
| `:composeApp:compileDebugKotlinAndroid` | OK |

## 2. Pruebas automatizadas

```
:composeApp:desktopTest
53 tests completed, 2 failed
```

- ✅ `DownloadQueueTest` (4 pruebas nuevas): orden de la cola, enlaces que no se
  cancelan entre sí, continuidad tras un enlace fallido y conteo de pendientes.
- ✅ `LunaSocketServerQueueTest` (1 prueba nueva, servidor real): tres
  peticiones `/download` seguidas → las tres encoladas, descargadas en orden y
  con una sola descarga activa (`maxConcurrent == 1`).
- ✅ `DownloadNotificationTest` (4 pruebas existentes) sin cambios de
  comportamiento.
- ⚠️ `SunoExtractorTest.testShortUrlFetch` y `testSongAnalyzeLive`: fallan por
  datos en vivo de `suno.com` (los títulos esperados ya no coinciden con la
  respuesta real). Son fallos preexistentes y ambientales; no tocan el código
  modificado en este proceso.

## 3. Ejecución real (app de escritorio)

Instancia de desarrollo con puerto alterno (`-Plunafetch.port=51244`) para no
interferir con la app instalada del usuario, que se dejó intacta.

```
https://youtu.be/queueDemoA -> {"ok":true,"queued":true,"pending":1}
https://youtu.be/queueDemoB -> {"ok":true,"queued":true,"pending":2}
https://youtu.be/queueDemoC -> {"ok":true,"queued":true,"pending":3}
```

- Las tres peticiones fueron aceptadas y encoladas (contador creciente).
- La tarjeta "Cola de descargas" mostró los tres enlaces en orden de llegada,
  con su estado final y el motivo del fallo (URLs de prueba inexistentes).
- Verificación visual: `temp/luna-queue3.png`.

## 4. Lo que queda sin verificar

- No se probó con un teléfono físico: no hay dispositivo Android conectado
  (`adb devices` solo muestra un emulador en estado *offline*). El código
  afectado (`commonMain`) sí compila para Android.
- No se probó el popup de la extensión en un navegador real: la extensión se
  carga descomprimida y requiere recargarla en `chrome://extensions` para
  tomar el cambio.
- No se Lanzó una descarga real de YouTube desde la cola en la app de
  escritorio (evita escribir en la carpeta de descargas del usuario); la
  secuencialidad está cubierta por las pruebas automatizadas.

