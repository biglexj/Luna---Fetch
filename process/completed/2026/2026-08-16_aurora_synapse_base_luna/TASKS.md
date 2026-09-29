# Tareas — Integración Base de Aurora Synapse Protocol en Luna Fetch

- **Proceso:** `2026-08-16_aurora_synapse_base_luna`
- **Estado:** Pendiente de Aprobación de Plan

---

## Tareas Técnicas

### 1. Modelos de Dominio y Parser (`domain/synapse/`)
- [x] `T1.1`: Crear `SynapseEnvelope.kt` con soporte de serialización JSON Nivel 4 (`synapse_version`, `source_app`, `target_app`, `action`, `payload`).
- [x] `T1.2`: Crear `SynapseAction.kt` con las acciones soportadas: `EnqueueDownload`, `AnalyzeUrl`, `OpenFolder`, `OpenMediaInPrisma`.
- [x] `T1.3`: Crear `SynapseUriParser.kt` capaz de procesar `luna://...` y `aurora-synapse://luna/...`, decodificando query params y aplicando reglas de seguridad `PathGuard`.

### 2. Motor IPC y Despacho en Plataforma (`platform/synapse/` & `Main.kt`)
- [x] `T2.1`: Implementar `SynapseServer.kt` en Desktop JVM escuchando en `127.0.0.1:49282` y Named Pipe `\\.\pipe\luna-synapse-ipc`.
- [x] `T2.2`: Implementar `SynapseClient.kt` para emisión de acciones hacia Prisma (`127.0.0.1:49280` / `prisma://open`).
- [x] `T2.3`: Actualizar `SingleInstanceLock.kt` y `Main.kt` para transferir URIs y comandos CLI (`--synapse-action`, `--download-url`) a la instancia viva activa y cerrarse en código 0.

### 3. Integración en Presenter y UI
- [x] `T3.1`: Agregar método `handleSynapseAction(action)` en `LunaFetchPresenter.kt`.
- [x] `T3.2`: Integrar acción *"Reproducir en Prisma"* en las tarjetas de descarga completada (`DownloadStatusCard.kt`) y en el historial (`HistoryCard.kt`).
- [x] `T3.3`: Configurar Toast informativo de 4 segundos al procesar una orden remota de Synapse.

### 4. Verificación y Pruebas
- [x] `T4.1`: Validar parseo de URIs válidas y rechazo de URIs maliciosas con pruebas unitarias (`SynapseUriParserTest.kt`).
- [x] `T4.2`: Compilar el módulo de escritorio (`.\gradlew :composeApp:compileKotlinDesktop`).
- [x] `T4.3`: Documentar evidencia en `VALIDATION.md`.
