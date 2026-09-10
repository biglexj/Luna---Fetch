# Tareas — Sistema de Notificaciones de Descarga en Segundo Plano

- **Proceso:** `2026-09-10_download_completion_notifications`
- **Estado:** En ejecución

---

## Tareas Técnicas

### 1. Capa de Contratos y Preferencias (`domain/` & `platform/`)
- [x] `T1.1`: Extender `PlatformBindings` con `notifyDownloadCompleted`, `notifyDownloadFailed`, `isNotificationsEnabled` y `setNotificationsEnabled`.
- [x] `T1.2`: Agregar `showNotifications` persistente en `AppSettings.kt`.

### 2. Implementación en Plataforma de Escritorio (`desktopMain/`)
- [x] `T2.1`: Corregir `currentTrayIcon` en `ModernTrayManager.kt` y agregar `showNotification(title, message, isError, onClick)`.
- [x] `T2.2`: Configurar `ActionListener` en `TrayIcon` para responder al clic en el globo/notificación abriendo el archivo/carpeta.
- [x] `T2.3`: Implementar los métodos de notificación en `DesktopPlatformBindings.kt`.

### 3. Implementación en Plataforma Android (`androidMain/`)
- [x] `T3.1`: Implementar `notifyDownloadCompleted` y `notifyDownloadFailed` en `AndroidPlatformBindings.kt`.
- [x] `T3.2`: Estandarizar `AndroidDownloadEngine.kt` para evitar doble notificación.

### 4. Orquestación en Presenter y UI (`commonMain/`)
- [x] `T4.1`: Invocar notificaciones y toast en `LunaFetchPresenter.kt` al concluir la descarga y ante errores.
- [x] `T4.2`: Añadir fila de ajuste de notificaciones en `SettingsDialog.kt` y corregir versión dinámica.

### 5. Pruebas y Validación
- [x] `T5.1`: Crear prueba unitaria `DownloadNotificationTest.kt` validando emisión de notificaciones.
- [x] `T5.2`: Compilar y ejecutar pruebas de Desktop (`compileKotlinDesktop`, `desktopTest`) y Android (`compileDebugKotlinAndroid`).
- [x] `T5.3`: Registrar evidencia de validación en `VALIDATION.md` y aprobar en `APPROVAL.md`.
