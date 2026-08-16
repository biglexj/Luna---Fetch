# Tareas — Aurora Synapse LAN Link (Descarga Remota y Sincronización)

- **Proceso:** `2026-08-16_aurora_synapse_lan_sync`
- **Estado:** En ejecución

---

## Tareas Técnicas

### 1. Modelos de Datos y Protocolo LAN (`domain/synapse/lan/`)
- [x] `T1.1`: Crear `SynapseDevice.kt` (identificación de nodo en la red: ID, nombre, tipo, IP, puerto, OS).
- [x] `T1.2`: Crear `SynapseLanModels.kt` con los contratos de paquetes UDP y TCP (Beacon, PushDownload, HistorySync).

### 2. Motor de Red Local (`domain/synapse/lan/` & `platform/`)
- [x] `T2.1`: Implementar `SynapseLanDiscovery.kt` (emisor periódico y receptor de beacons UDP en `49289`).
- [x] `T2.2`: Implementar `SynapseLanServer.kt` (servidor de red local en puerto `49288` para recepción de enlaces y sincronización).
- [x] `T2.3`: Implementar `SynapseLanClient.kt` (cliente de despacho remoto hacia IPs de la red local).

### 3. Integración en Presenter y UI
- [x] `T3.1`: Añadir lista de peers reactiva (`discoveredPeers`) en `LunaFetchState.kt` y lógica de envío remoto en `LunaFetchPresenter.kt`.
- [x] `T3.2`: Integrar selector de dispositivo destino **«💻 Descargar en PC»** en `DownloadOptionsCard.kt` y `QuickDownloadSheet.kt`.
- [x] `T3.3`: Integrar indicador de dispositivos LAN conectados (`LanMeshBadge`) en la cabecera (`AppHeader.kt`) y sección en Ajustes.

### 4. Verificación y Documentación
- [x] `T4.1`: Validar con tests unitarios el flujo de paquetes y parseo LAN (`SynapseLanTest.kt`).
- [x] `T4.2`: Compilar Desktop JVM y Android (`compileKotlinDesktop`, `desktopTest`, `compileDebugKotlinAndroid`) con éxito total.
- [x] `T4.3`: Actualizar documentación en `Core-Docs/features/aurora-synapse/apps-adaptadas/luna-fetch.md`.
