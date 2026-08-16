# Plan — Integración Base de Aurora Synapse Protocol en Luna Fetch

- **Fecha de inicio:** 2026-08-16
- **Estado:** Planificación
- **Objetivo:** Implementar la capa fundacional del protocolo de interoperabilidad *Aurora Synapse* (v1.0.0) en Luna Fetch, habilitando Deep Linking (`luna://`), despacho de instancia única (IPC port `49282`), contratos JSON y emisión hacia Prisma.

---

## 1. Contexto y Motivación
Luna Fetch es la primera aplicación del ecosistema en abrir sus puertas al **Aurora Synapse Protocol**. Esto permitirá que:
1. Otras aplicaciones (Prisma, Ely Intelligence, Mouzi, scripts CLI) puedan ordenar descargas y análisis a Luna Fetch sin duplicar procesos ni colapsar recursos.
2. Luna Fetch pueda delegar la reproducción de audio y video directamente a **Prisma** (`prisma://open`).
3. La aplicación soporte argumentos CLI directos (`luna://...` o `--synapse-action`) y enlaces web nativos.

---

## 2. Alcance y Entregables

### Capa de Dominio (`com.biglexj.lunafetch.domain.synapse`)
- **`SynapseEnvelope.kt`**: Modelo de datos serializable del envoltorio universal Nivel 4 (`synapse_version`, `source_app`, `target_app`, `action`, `timestamp_utc`, `idempotency_key`, `payload`).
- **`SynapseAction.kt`**: Jerarquía sellada de acciones admitidas (`EnqueueDownload`, `AnalyzeUrl`, `OpenFolder`, `OpenMediaInPrisma`).
- **`SynapseUriParser.kt`**: Parser seguro para esquemas canónicos (`aurora-synapse://luna/...`) y directos (`luna://...`), con validación `PathGuard` y decodificación URL.

### Capa de Plataforma e IPC (`com.biglexj.lunafetch.platform.synapse`)
- **`SynapseServer.kt`**: Listener en segundo plano en puerto localhost `49282` y Named Pipe en Windows, procesando invocaciones en caliente.
- **`SynapseClient.kt`**: Despachador emisor hacia otras aplicaciones (ej. Prisma en puerto `49280` / `prisma://`).
- **Actualización de `SingleInstanceLock.kt` y `Main.kt`**: Integración con el parser CLI y despacho en caliente si ya existe una instancia viva.

### Capa de Presentación y UI (Material 3 Expressive)
- **`LunaFetchPresenter.kt`**: Manejo de eventos `handleSynapseAction(action)` para encolar descargas silenciosas o análisis con feedback visual.
- **`VideoCard.kt` / `HistoryCard.kt`**: Botón de acción rápida *"Reproducir en Prisma"* al finalizar una descarga o desde el historial.
- **Toasts y Notificaciones flotantes**: Feedback discreto de 4 segundos al recibir peticiones externas remotas.

---

## 3. Criterios de Finalización
- [ ] Modelos de contrato y parser de URI implementados con tests/validaciones.
- [ ] Servidor de despacho Synapse escuchando en `127.0.0.1:49282` y canalizando acciones a la UI/Presenter.
- [ ] SingleInstanceLock transferiendo argumentos URI a la instancia primaria en ejecución.
- [ ] Emisión de acciones salientes (`prisma://open`) estructurada.
- [ ] Compilación exitosa en Desktop JVM (`.\gradlew :composeApp:compileKotlinDesktop`).
