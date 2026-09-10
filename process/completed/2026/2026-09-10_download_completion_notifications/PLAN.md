# Plan — Sistema de Notificaciones de Descarga en Segundo Plano

- **Fecha de inicio:** 2026-09-10
- **Estado:** En ejecución
- **Objetivo:** Implementar un sistema de notificaciones del sistema operativo (Windows Desktop & Android) y feedback visual en UI para informar al usuario cuando una descarga termine exitosamente o falle, especialmente al dispararla desde la extensión de navegador web en segundo plano.

---

## 1. Contexto y Justificación

Al enviar enlaces a descargar desde la extensión del navegador (Chrome / Edge), Luna Fetch recibe la petición vía socket HTTP en segundo plano mientras el usuario continúa navegando. Actualmente, Luna Fetch procesa la descarga pero no emite ninguna notificación del sistema operativo al finalizar ni al fallar. El usuario no tiene forma de saber si la descarga concluyó o sigue en progreso sin abrir manualmente la aplicación.

---

## 2. Arquitectura de Notificaciones

```mermaid
flowchart TD
    EXT["🌐 Extensión Chromium"] -->|POST /download| SOCK["🔌 LunaSocketServer (51234)"]
    SOCK --> PRES["🧠 LunaFetchPresenter"]
    PRES -->|download()| ENG["⚙️ DownloadEngine"]
    
    ENG -->|Success / Error| PRES
    PRES --> TOAST["💬 Floating Toast UI (si visible)"]
    PRES --> PLAT["📱/💻 PlatformBindings"]
    
    subgraph Desktop["💻 Windows Desktop"]
        PLAT --> TRAY["ModernTrayManager (SystemTray / TrayIcon)"]
        TRAY --> WIN_NOTIF["🔔 Notificación Nativa de Windows (Toast / Balloon)"]
        WIN_NOTIF -->|Click en notificación| OPEN["📂 Abrir archivo / carpeta descargada"]
    end

    subgraph Android["📱 Android"]
        PLAT --> SERV["DownloadForegroundService.notifyCompleted / Failed"]
        SERV --> AND_NOTIF["🔔 Notificación de Sistema Android"]
    end
```

---

## 3. Componentes a Modificar

1. **`PlatformBindings` (`domain/DownloadEngine.kt`)**:
   - `val isNotificationsEnabled: Boolean? get() = null`
   - `fun setNotificationsEnabled(enabled: Boolean) {}`
   - `fun notifyDownloadCompleted(title: String, filePath: String) {}`
   - `fun notifyDownloadFailed(title: String, error: String) {}`

2. **`AppSettings` (`platform/AppSettings.kt`)**:
   - Persistencia de preferencia `showNotifications: Boolean` (por defecto `true`).

3. **`ModernTrayManager` (`platform/ModernTrayManager.kt`)**:
   - Asignar `currentTrayIcon = trayIcon` en `setupTray`.
   - Método `showNotification(title, message, isError, onClick)`.
   - Listener de clic en notificación para abrir el archivo descargado.

4. **`DesktopPlatformBindings` (`platform/DesktopPlatformBindings.kt`)**:
   - Implementar `notifyDownloadCompleted` y `notifyDownloadFailed` invocando `ModernTrayManager.showNotification`.

5. **`AndroidPlatformBindings` & `AndroidDownloadEngine`**:
   - Implementar `notifyDownloadCompleted` y `notifyDownloadFailed` delegando en `DownloadForegroundService`.
   - Limpieza de llamadas duplicadas en `AndroidDownloadEngine`.

6. **`LunaFetchPresenter` (`domain/LunaFetchPresenter.kt`)**:
   - Invocar `platform.notifyDownloadCompleted` y `showToast` al finalizar con éxito.
   - Invocar `platform.notifyDownloadFailed` y `showToast` en caso de error en descarga o análisis directo.

7. **`SettingsDialog` (`feature/header/SettingsDialog.kt`)**:
   - Fila de ajuste para activar/desactivar notificaciones del sistema.
   - Corrección de etiqueta de versión dinámica `AppConfig.APP_VERSION`.

---

## 4. Criterios de Aceptación
- [ ] Al completarse una descarga en segundo plano desde la extensión de navegador, Windows muestra una notificación nativa indicando el título del contenido descargado.
- [ ] Si el usuario hace clic en la notificación, se abre el archivo descargado o la carpeta contenedora.
- [ ] En caso de fallo de descarga o análisis, se muestra una notificación de error descriptiva.
- [ ] La opción es configurable en el panel de Ajustes (activada por defecto).
- [ ] Compilación y pruebas unitarias exitosas en Desktop JVM y Android.
