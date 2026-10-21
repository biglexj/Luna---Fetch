package com.biglexj.lunafetch.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel

data class LunaFetchState(
    val url: String = "",
    val destination: String = "",
    val video: VideoInfo? = null,
    val selectedFormat: MediaFormat = MediaFormat.Mp4,
    val qualities: List<QualityOption> = FormatCatalog.qualities(MediaFormat.Mp4, 1080),
    val selectedQuality: QualityOption = qualities.first(),
    val downloadCollection: Boolean = false,
    val isAnalyzing: Boolean = false,
    val isDownloading: Boolean = false,
    val progress: DownloadProgress? = null,
    val logs: List<String> = emptyList(),
    val error: String? = null,
    val completedOutput: String? = null,
    val history: List<DownloadHistoryItem> = emptyList(),
    val availableUpdate: UpdateRelease? = null,
    val updateMessage: String? = null,
    val showUpdateModal: Boolean = false,
    val isUpdateDownloading: Boolean = false,
    val updateDownloadProgress: Float = 0f,
    val updateDownloadedFilePath: String? = null,
    val updateError: String? = null,
    val toastMessage: String? = null,
    val discoveredPeers: List<com.biglexj.lunafetch.domain.synapse.lan.SynapseDevice> = emptyList(),
    val selectedTargetPeer: com.biglexj.lunafetch.domain.synapse.lan.SynapseDevice? = null,
    val isSendingToPeer: Boolean = false,
    val diagnostics: List<DiagnosticEntry> = emptyList(),
    val queue: List<QueuedDownload> = emptyList(),
    val activeQueueItemId: String? = null,
)

class LunaFetchPresenter(
    private val platform: PlatformBindings,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
) {
    private val _state = MutableStateFlow(LunaFetchState(destination = platform.defaultDestination))
    val state: StateFlow<LunaFetchState> = _state.asStateFlow()
    private var operation: Job? = null
    private var updateJob: Job? = null
    private var autoClearUpdateMessageJob: Job? = null

    // ── Cola de descargas ───────────────────────────────────────────────
    // Los envíos externos (extensión, Synapse, Launcher nativo) se encolan aquí
    // y se procesan de uno en uno: cada enlace se analiza y descarga por
    // completo antes de tomar el siguiente.
    private val queueLock = Any()
    private val queueItems = mutableListOf<QueuedDownload>()
    private val queueSignal = Channel<Unit>(Channel.CONFLATED)
    private var queuePaused = false
    private var cancelRequested = false

    private val localDeviceId = "luna_${(platform.deviceName + platform.deviceOs).hashCode().toString(16)}"

    private val lanDiscovery = com.biglexj.lunafetch.domain.synapse.lan.SynapseLanDiscovery(
        localDevice = com.biglexj.lunafetch.domain.synapse.lan.SynapseDevice(
            id = localDeviceId,
            name = platform.deviceName,
            type = platform.deviceType,
            ip = "127.0.0.1",
            port = com.biglexj.lunafetch.domain.synapse.lan.SynapseLanServer.LAN_PORT,
            os = platform.deviceOs,
        )
    )

    private val lanServer = com.biglexj.lunafetch.domain.synapse.lan.SynapseLanServer(
        onRemoteDownloadReceived = { req ->
            if (state.value.destination.isBlank()) {
                showToast("⚠️ Descarga remota recibida, pero no hay carpeta de destino configurada.")
                platform.notifyDownloadFailed(
                    "Descarga desde ${req.sourceDevice}",
                    "Configura una carpeta de destino en Luna Fetch para completar la descarga.",
                )
            } else {
                showToast("📥 Descarga remota desde ${req.sourceDevice}...")
            }
            startDirectDownload(req.url, req.mediaType, req.quality, source = "Aurora LAN")
        },
        onHistorySyncReceived = { remoteItems ->
            val local = state.value.history
            val merged = (remoteItems + local)
                .distinctBy { if (it.url.isNotBlank()) "${it.url}_${it.formatLabel}" else it.id }
                .sortedByDescending { it.timestampMs }
                .take(10)
            platform.saveHistory(merged)
            _state.update { it.copy(history = merged) }
            merged
        },
        onPeerDiscovered = { peer ->
            lanDiscovery.registerDirectPeer(peer)
        },
        onReleaseAnnounced = { req ->
            handleAuroraReleaseAnnouncement(req)
        }
    )

    init {
        refreshHistory()
        refreshDiagnostics()
        checkForUpdates()
        startLanServices()
        startQueueWorker()
    }

    private fun startLanServices() {
        lanServer.start()
        lanDiscovery.start()
        scope.launch {
            lanDiscovery.discoveredDevices.collect { peers ->
                val prevPeers = state.value.discoveredPeers
                _state.update { it.copy(discoveredPeers = peers) }
                val newPeers = peers.filter { p -> prevPeers.none { it.id == p.id } }
                for (peer in newPeers) {
                    syncHistoryWithPeer(peer, silent = true)
                }
            }
        }
    }

    fun refreshHistory() {
        val loaded = platform.loadHistory()
        val migrated = loaded.map { item ->
            if (item.originDevice.isBlank() && platform.isLocalPathAccessible(item.path)) {
                item.copy(originDevice = platform.deviceName)
            } else {
                item
            }
        }
        if (migrated != loaded) {
            platform.saveHistory(migrated)
        }
        _state.update { it.copy(history = migrated) }
    }

    fun refreshDiagnostics() {
        val loaded = platform.loadDiagnostics()
        val purged = DiagnosticStore.purgeExpired(loaded)
        if (purged.size != loaded.size) {
            platform.saveDiagnostics(purged)
        }
        _state.update { it.copy(diagnostics = purged) }
    }

    fun recordDiagnostic(
        url: String,
        operation: String,
        errorMessage: String,
        technicalDetails: String,
    ) {
        val newEntry = DiagnosticEntry(
            id = "${System.currentTimeMillis()}_${(1000..9999).random()}",
            timestampMs = System.currentTimeMillis(),
            url = url,
            operation = operation,
            errorMessage = errorMessage,
            technicalDetails = technicalDetails,
            originDevice = platform.deviceName,
        )
        val current = platform.loadDiagnostics()
        val updated = DiagnosticStore.addEntry(current, newEntry)
        platform.saveDiagnostics(updated)
        _state.update { it.copy(diagnostics = updated) }
    }

    fun clearDiagnostics() {
        platform.saveDiagnostics(emptyList())
        _state.update { it.copy(diagnostics = emptyList()) }
        showToast("🧹 Diagnósticos eliminados.")
    }

    fun showToast(message: String) {
        _state.update { it.copy(toastMessage = message) }
    }

    fun clearToast() {
        _state.update { it.copy(toastMessage = null) }
    }

    fun checkForUpdates(manual: Boolean = false) {
        scope.launch {
            val release = platform.checkUpdate()
            val currentVersion = AppConfig.APP_VERSION
            if (release != null && UpdateChecker.isNewerVersion(currentVersion, release.version)) {
                _state.update {
                    it.copy(
                        availableUpdate = release,
                        showUpdateModal = manual || it.showUpdateModal,
                        updateMessage = null,
                        isUpdateDownloading = false,
                        updateDownloadProgress = 0f,
                        updateDownloadedFilePath = null,
                        updateError = null,
                    )
                }
                // Propagar el release a Aurora Synapse LAN (auto_updater.md #11).
                broadcastReleaseToAurora(release)
            } else if (manual) {
                autoClearUpdateMessageJob?.cancel()
                val msg = if (release != null) "✅ ¡Estás en la última versión!" else "⚠️ No se pudo comprobar las actualizaciones."
                _state.update {
                    it.copy(
                        availableUpdate = null,
                        showUpdateModal = false,
                        updateMessage = msg,
                        toastMessage = null,
                    )
                }
                autoClearUpdateMessageJob = scope.launch {
                    kotlinx.coroutines.delay(4000L)
                    _state.update { it.copy(updateMessage = null) }
                }
            }
        }
    }

    private fun broadcastReleaseToAurora(release: com.biglexj.lunafetch.domain.UpdateRelease) {
        val peers = state.value.discoveredPeers
        if (peers.isEmpty()) return
        val sourceName = platform.deviceName
        scope.launch {
            peers.forEach { peer ->
                runCatching {
                    com.biglexj.lunafetch.domain.synapse.lan.SynapseLanClient.broadcastRelease(
                        peer = peer,
                        sourceDeviceName = sourceName,
                        version = release.version,
                        downloadUrl = release.exeDownloadUrl.ifBlank { release.downloadUrl },
                        releasePageUrl = release.releasePageUrl,
                        body = release.body,
                    )
                }
            }
        }
    }

    private fun handleAuroraReleaseAnnouncement(req: com.biglexj.lunafetch.domain.synapse.lan.LanAnnounceReleaseRequest) {
        val currentVersion = AppConfig.APP_VERSION
        if (!UpdateChecker.isNewerVersion(currentVersion, req.version)) return
        val release = com.biglexj.lunafetch.domain.UpdateRelease(
            version = req.version,
            downloadUrl = req.downloadUrl,
            exeDownloadUrl = req.downloadUrl,
            releasePageUrl = req.releasePageUrl,
            body = req.body,
        )
        _state.update {
            it.copy(
                availableUpdate = release,
                showUpdateModal = true,
                updateMessage = null,
                updateError = null,
            )
        }
        showToast("🛰️ Nueva versión v${req.version} disponible (recibida desde ${req.sourceDevice})")
    }

    fun clearUpdateMessage() {
        autoClearUpdateMessageJob?.cancel()
        _state.update { it.copy(updateMessage = null) }
    }

    fun openUpdateModal() {
        _state.update { it.copy(showUpdateModal = true) }
    }

    fun dismissUpdateModal() {
        updateJob?.cancel()
        updateJob = null
        _state.update {
            it.copy(
                showUpdateModal = false,
                isUpdateDownloading = false,
                updateDownloadProgress = 0f,
                updateDownloadedFilePath = null,
                updateError = null,
            )
        }
    }

    fun startUpdateDownload() {
        val release = state.value.availableUpdate ?: return
        if (state.value.isUpdateDownloading) return

        updateJob?.cancel()
        updateJob = scope.launch {
            _state.update {
                it.copy(
                    isUpdateDownloading = true,
                    updateDownloadProgress = 0f,
                    updateDownloadedFilePath = null,
                    updateError = null,
                )
            }
            try {
                val filePath = platform.downloadUpdateFile(release) { progress ->
                    _state.update { it.copy(updateDownloadProgress = progress) }
                }
                if (filePath != null) {
                    _state.update {
                        it.copy(
                            isUpdateDownloading = false,
                            updateDownloadProgress = 1f,
                            updateDownloadedFilePath = filePath,
                            updateError = null,
                        )
                    }
                } else {
                    _state.update {
                        it.copy(
                            isUpdateDownloading = false,
                            updateError = "No se pudo descargar la actualización.",
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                _state.update {
                    it.copy(
                        isUpdateDownloading = false,
                        updateDownloadProgress = 0f,
                    )
                }
            } catch (error: Throwable) {
                _state.update {
                    it.copy(
                        isUpdateDownloading = false,
                        updateError = error.message ?: "Ocurrió un error al descargar la actualización.",
                    )
                }
            }
        }
    }

    fun installDownloadedUpdate() {
        val path = state.value.updateDownloadedFilePath
        if (!path.isNullOrBlank()) {
            platform.installDownloadedApk(path)
        } else {
            state.value.availableUpdate?.let(platform::downloadAndInstallUpdate)
        }
    }

    fun installUpdate() {
        openUpdateModal()
    }

    fun dismissUpdate() {
        _state.update { it.copy(availableUpdate = null, showUpdateModal = false) }
    }

    fun setUrl(value: String) {
        val sanitized = TikTokUtils.sanitizeUrl(value)
        _state.update { it.copy(url = sanitized, error = null) }
    }

    fun selectFormat(format: MediaFormat) {
        _state.update { current ->
            val qualities = FormatCatalog.qualities(format, current.video?.maxHeight ?: 1080)
            current.copy(selectedFormat = format, qualities = qualities, selectedQuality = qualities.first())
        }
    }

    fun selectQuality(quality: QualityOption) = _state.update { it.copy(selectedQuality = quality) }

    fun setDownloadCollection(value: Boolean) = _state.update { current ->
        current.copy(downloadCollection = value && current.video?.isCollection == true)
    }

    fun removeFromHistory(id: String) {
        _state.update { current ->
            val updated = current.history.filter { it.id != id }
            platform.saveHistory(updated)
            current.copy(history = updated)
        }
    }

    fun clearHistory() {
        platform.saveHistory(emptyList())
        _state.update { current -> current.copy(history = emptyList()) }
    }

    fun analyze() {
        val rawUrl = state.value.url.trim()
        val url = TikTokUtils.sanitizeUrl(rawUrl)
        if (url != rawUrl) {
            setUrl(url)
        }
        if (!isSupportedUrl(url)) {
            _state.update { it.copy(error = "Escribe una URL http o https válida.") }
            return
        }
        operation?.cancel()
        operation = scope.launch {
            _state.update { it.copy(isAnalyzing = true, video = null, error = null, completedOutput = null) }
            runCatching { platform.engine.analyze(url) }
                .onSuccess { video ->
                    _state.update { current ->
                        val qualities = FormatCatalog.qualities(current.selectedFormat, video.maxHeight)
                        val hasSingleVideoId = url.contains("watch?v=") || url.contains("v=") || url.contains("youtu.be/")
                        val shouldDefaultToCollection = video.isCollection && !hasSingleVideoId
                        current.copy(
                            video = video,
                            qualities = qualities,
                            selectedQuality = qualities.first(),
                            downloadCollection = shouldDefaultToCollection,
                            isAnalyzing = false,
                        )
                    }
                }
                .onFailure { error ->
                    if (error !is CancellationException) {
                        val msg = error.userMessage("No se pudo analizar el enlace.")
                        appendLog("ERROR: $msg")
                        _state.update { it.copy(isAnalyzing = false, error = msg) }
                        val techDetails = if (state.value.logs.isNotEmpty()) state.value.logs.joinToString("\n") else error.stackTraceToString()
                        recordDiagnostic(url = url, operation = "ANALYZE", errorMessage = msg, technicalDetails = techDetails)
                    }
                }
        }
    }

    /**
     * Triggered by browser extension / external requests.
     *
     * The link is appended to the download queue instead of being processed
 * immediately, so several links sent in a row are downloaded one after the
 * other instead of replacing each other.
     *
     * @return Number of links waiting in the queue, or -1 when the URL is invalid.
     */
    fun startDirectDownload(
        rawUrl: String,
        formatName: String = "mp4",
        requestedQuality: String? = null,
        source: String = "Extensión",
    ): Int {
        val url = TikTokUtils.sanitizeUrl(rawUrl)
        if (!isSupportedUrl(url)) return -1

        val item = QueuedDownload(
            id = "${System.currentTimeMillis()}_${(1000..9999).random()}",
            url = url,
            formatName = formatName,
            quality = requestedQuality,
            source = source,
        )
        val pending = synchronized(queueLock) {
            queueItems.add(item)
            trimQueueLocked()
            queueItems.count { !it.isFinished }
        }
        publishQueue()
        showToast(
            if (pending > 1) "📥 Encolado · $pending en espera"
            else "📥 Encolado en Luna Fetch"
        )
        queueSignal.trySend(Unit)
        return pending
    }

    // ── Queue worker ────────────────────────────────────────────────────

    private fun startQueueWorker() {
        scope.launch {
            for (signal in queueSignal) {
                drainQueue()
            }
        }
    }

    /**
     * Processes the pending links one by one. The worker is the only coroutine
     * that touches the engine for queued items, so two downloads never overlap.
     */
    private suspend fun drainQueue() {
        while (!queuePaused) {
            val item = synchronized(queueLock) {
                queueItems.firstOrNull { it.status == QueueItemStatus.Pending }
            } ?: return

            updateQueueItem(item.id) { it.copy(status = QueueItemStatus.Running) }
            _state.update { it.copy(activeQueueItemId = item.id) }
            try {
                processQueueItem(item)
            } catch (cancelled: CancellationException) {
                updateQueueItem(item.id) { it.copy(status = QueueItemStatus.Cancelled) }
                throw cancelled
            } finally {
                _state.update { it.copy(activeQueueItemId = null) }
            }
        }
    }

    private suspend fun processQueueItem(item: QueuedDownload) {
        val url = item.url
        val format = resolveMediaFormat(item.formatName)
        val destination = state.value.destination

        setUrl(url)
        selectFormat(format)
        platform.notifyDownloadStarted("Iniciando descarga de ${format.displayName}…")
        _state.update { it.copy(isAnalyzing = true, video = null, error = null, completedOutput = null) }

        val video = try {
            platform.engine.analyze(url)
        } catch (cancelled: CancellationException) {
            updateQueueItem(item.id) { it.copy(status = QueueItemStatus.Cancelled) }
            throw cancelled
        } catch (error: Throwable) {
            val msg = error.userMessage("No se pudo analizar el enlace.")
            appendLog("ERROR: $msg")
            _state.update { it.copy(isAnalyzing = false, error = msg) }
            showToast("❌ $msg")
            platform.notifyDownloadFailed("Error al analizar enlace", msg)
            val techDetails = if (state.value.logs.isNotEmpty()) state.value.logs.joinToString("\n") else error.stackTraceToString()
            recordDiagnostic(url = url, operation = "ANALYZE_DIRECT", errorMessage = msg, technicalDetails = techDetails)
            updateQueueItem(item.id) { it.copy(status = QueueItemStatus.Failed, errorMessage = msg) }
            return
        }

        if (destination.isBlank()) {
            val msg = "Selecciona una carpeta de destino para guardar las descargas."
            val title = video.collectionTitle ?: video.title
            _state.update { it.copy(isAnalyzing = false, error = msg) }
            showToast("⚠️ $msg")
            platform.notifyDownloadFailed(title.ifBlank { "Descarga detenida" }, msg)
            updateQueueItem(item.id) { it.copy(status = QueueItemStatus.Failed, errorMessage = msg, title = title) }
            // La cola queda en pausa hasta que el usuario elija una carpeta.
            queuePaused = true
            return
        }

        val qualities = FormatCatalog.qualities(format, video.maxHeight)
        val matchedQuality = item.quality?.let { requested ->
            qualities.firstOrNull {
                it.displayName.contains(requested, ignoreCase = true) ||
                    it.formatSelector.contains(requested, ignoreCase = true)
            }
        } ?: qualities.first()
        val hasSingleVideoId = url.contains("watch?v=") || url.contains("v=") || url.contains("youtu.be/")
        val shouldDownloadCollection = video.isCollection && !hasSingleVideoId

        _state.update { current ->
            current.copy(
                video = video,
                selectedFormat = format,
                qualities = qualities,
                selectedQuality = matchedQuality,
                downloadCollection = shouldDownloadCollection,
                isAnalyzing = false,
            )
        }
        updateQueueItem(item.id) { it.copy(title = video.collectionTitle ?: video.title) }

        val request = DownloadRequest(
            url = video.url,
            destination = destination,
            format = format,
            quality = matchedQuality,
            downloadCollection = shouldDownloadCollection,
        )

        when (runDownload(request = request, video = video, format = format, quality = matchedQuality)) {
            DownloadOutcome.Completed -> updateQueueItem(item.id) { it.copy(status = QueueItemStatus.Completed) }
            DownloadOutcome.Cancelled -> updateQueueItem(item.id) { it.copy(status = QueueItemStatus.Cancelled) }
            DownloadOutcome.Failed -> updateQueueItem(item.id) {
                it.copy(status = QueueItemStatus.Failed, errorMessage = state.value.error)
            }
        }
    }

    private fun resolveMediaFormat(formatName: String): MediaFormat = when {
        formatName.contains("audio", ignoreCase = true) ||
            formatName.contains("mp3", ignoreCase = true) ||
            formatName.contains("m4a", ignoreCase = true) ||
            formatName.contains("flac", ignoreCase = true) ||
            formatName.contains("wav", ignoreCase = true) -> MediaFormat.Mp3
        else -> MediaFormat.Mp4
    }

    // ── Queue state helpers ─────────────────────────────────────────────

    private fun publishQueue() {
        val snapshot = synchronized(queueLock) { queueItems.toList() }
        _state.update { it.copy(queue = snapshot) }
    }

    private fun updateQueueItem(id: String, transform: (QueuedDownload) -> QueuedDownload) {
        synchronized(queueLock) {
            val index = queueItems.indexOfFirst { it.id == id }
            if (index >= 0) {
                queueItems[index] = transform(queueItems[index])
            }
        }
        publishQueue()
    }

    /** Keeps the queue bounded by dropping the oldest finished links. */
    private fun trimQueueLocked() {
        val maxItems = 30
        var excess = queueItems.size - maxItems
        if (excess <= 0) return
        val iterator = queueItems.iterator()
        while (iterator.hasNext() && excess > 0) {
            val candidate = iterator.next()
            if (candidate.isFinished) {
                iterator.remove()
                excess--
            }
        }
    }

    /** Removes a pending link from the queue. Running links are kept. */
    fun removeQueueItem(id: String) {
        synchronized(queueLock) {
            val target = queueItems.firstOrNull { it.id == id } ?: return
            if (target.status == QueueItemStatus.Running) return
            queueItems.removeAll { it.id == id }
        }
        publishQueue()
    }

    /** Removes the links that already finished (completed, failed or cancelled). */
    fun clearFinishedQueueItems() {
        synchronized(queueLock) {
            queueItems.removeAll { it.isFinished }
        }
        publishQueue()
    }

    /** Removes every pending link, keeping the one that is currently running. */
    fun clearPendingQueueItems() {
        synchronized(queueLock) {
            queueItems.removeAll { it.status == QueueItemStatus.Pending }
        }
        publishQueue()
    }

    /** Reactivates a paused queue (for example after choosing the destination). */
    private fun resumeQueue() {
        queuePaused = false
        queueSignal.trySend(Unit)
    }

    fun chooseDestination() {
        operation = scope.launch {
            platform.chooseDestination(state.value.destination)?.let { destination ->
                platform.rememberDestination(destination)
                _state.update { it.copy(destination = destination, error = null) }
                resumeQueue()
            }
        }
    }

    fun download() {
        val current = state.value
        val video = current.video ?: run {
            _state.update { it.copy(error = "Analiza un enlace antes de descargar.") }
            return
        }
        if (current.destination.isBlank()) {
            val errorMsg = "Selecciona una carpeta de destino para guardar las descargas."
            _state.update { it.copy(error = errorMsg) }
            showToast("⚠️ $errorMsg")
            val title = video.collectionTitle ?: video.title
            platform.notifyDownloadFailed(title.ifBlank { "Descarga detenida" }, errorMsg)
            chooseDestination()
            return
        }

        val request = DownloadRequest(
            url = video.url,
            destination = current.destination,
            format = current.selectedFormat,
            quality = current.selectedQuality,
            downloadCollection = current.downloadCollection,
        )
        operation?.cancel()
        operation = scope.launch {
            runDownload(
                request = request,
                video = video,
                format = current.selectedFormat,
                quality = current.selectedQuality,
            )
        }
    }

    /**
     * Runs the download itself and reports the outcome. Shared by the manual
     * download button and by the queue worker so both behave identically.
     */
    private suspend fun runDownload(
        request: DownloadRequest,
        video: VideoInfo,
        format: MediaFormat,
        quality: QualityOption,
    ): DownloadOutcome {
        _state.update {
            it.copy(
                isDownloading = true,
                progress = DownloadProgress(0.0, phase = DownloadPhase.Preparing),
                logs = emptyList(),
                error = null,
                completedOutput = null,
            )
        }
        return try {
            val result = platform.engine.download(
                request = request,
                onProgress = { progress -> _state.update { it.copy(progress = progress) } },
                onLog = ::appendLog,
            )
            val downloadTitle = video.collectionTitle ?: video.title
            val newItem = DownloadHistoryItem(
                id = "${System.currentTimeMillis()}_${(1000..9999).random()}",
                title = downloadTitle,
                formatLabel = "${format.displayName} · ${quality.displayName}",
                path = result.openPath ?: "",
                url = video.url,
                originDevice = platform.deviceName,
            )
            _state.update {
                val updatedHistory = (listOf(newItem) + it.history)
                    .distinctBy { h -> if (h.url.isNotBlank()) "${h.url}_${h.formatLabel}" else h.id }
                    .take(10)
                platform.saveHistory(updatedHistory)
                it.copy(
                    isDownloading = false,
                    progress = DownloadProgress(100.0, phase = DownloadPhase.Completed),
                    completedOutput = result.openPath,
                    history = updatedHistory,
                )
            }
            autoBroadcastHistorySync()
            showToast("✅ Descarga completada: $downloadTitle")
            platform.notifyDownloadCompleted(downloadTitle, result.openPath ?: "")
            DownloadOutcome.Completed
        } catch (cancelled: CancellationException) {
            _state.update {
                it.copy(isDownloading = false, progress = DownloadProgress(0.0, phase = DownloadPhase.Cancelled))
            }
            DownloadOutcome.Cancelled
        } catch (error: Throwable) {
            if (cancelRequested) {
                cancelRequested = false
                _state.update {
                    it.copy(isDownloading = false, progress = DownloadProgress(0.0, phase = DownloadPhase.Cancelled))
                }
                return DownloadOutcome.Cancelled
            }
            val msg = error.userMessage("La descarga no pudo completarse.")
            appendLog("ERROR: $msg")
            _state.update {
                it.copy(
                    isDownloading = false,
                    error = msg,
                )
            }
            val downloadTitle = video.collectionTitle ?: video.title.ifBlank { "Descarga" }
            showToast("❌ $msg")
            platform.notifyDownloadFailed(downloadTitle, msg)
            val techDetails = if (state.value.logs.isNotEmpty()) state.value.logs.joinToString("\n") else error.stackTraceToString()
            recordDiagnostic(url = video.url, operation = "DOWNLOAD", errorMessage = msg, technicalDetails = techDetails)
            DownloadOutcome.Failed
        }
    }

    fun cancel() {
        cancelRequested = true
        platform.engine.cancel()
        operation?.cancel()
    }

    fun openCompletedOutput() = state.value.completedOutput?.let(platform::openOutput)

    fun openCompletedFolder() = state.value.completedOutput?.let(platform::openDestinationFolder)

    fun playInPrisma(filePath: String) {
        if (filePath.isBlank()) return
        if (!platform.isLocalPathAccessible(filePath)) {
            showToast("Este archivo fue descargado en otro equipo (${platform.deviceName}) y no está disponible localmente.")
            return
        }
        val success = platform.openInPrisma(filePath)
        if (!success) {
            platform.openOutput(filePath)
        }
    }

    fun playCompletedInPrisma() {
        state.value.completedOutput?.let(::playInPrisma)
    }

    fun playCompletedInSuperGaleria() {
        val path = state.value.completedOutput ?: return
        if (!platform.isLocalPathAccessible(path)) {
            showToast("Este archivo no está disponible localmente.")
            return
        }
        val success = platform.openInSuperGaleria(path)
        if (!success) platform.openOutput(path)
    }

    fun openCompletedInSuperGaleria() {
        val path = state.value.completedOutput ?: return
        val success = platform.openFolderInSuperGaleria(path)
        if (!success) platform.openDestinationFolder(path)
    }

    fun redownloadHistoryItem(item: DownloadHistoryItem) {
        if (item.url.isBlank()) return
        showToast("🔄 Iniciando descarga de nuevo: ${item.title}")
        val formatName = if (item.formatLabel.contains("Audio", ignoreCase = true) || item.formatLabel.contains("MP3", ignoreCase = true) || item.formatLabel.contains("M4A", ignoreCase = true)) "mp3" else "mp4"
        val qualityName = item.formatLabel.substringAfter("·", "").trim().takeIf { it.isNotBlank() }
        startDirectDownload(item.url, formatName = formatName, requestedQuality = qualityName, source = "Historial")
    }

    fun handleSynapseAction(action: com.biglexj.lunafetch.domain.synapse.SynapseAction) {
        when (action) {
            is com.biglexj.lunafetch.domain.synapse.SynapseAction.EnqueueDownload -> {
                if (action.silent) {
                    showToast("📥 Descargando desde Aurora Synapse...")
                }
                val format = if (action.mediaType.equals("audio", ignoreCase = true)) MediaFormat.Mp3 else MediaFormat.Mp4
                startDirectDownload(action.url, format.extension, action.quality, source = "Aurora Synapse")
            }
            is com.biglexj.lunafetch.domain.synapse.SynapseAction.AnalyzeUrl -> {
                setUrl(action.url)
                analyze()
                showToast("🔍 Analizando enlace con Synapse...")
            }
            is com.biglexj.lunafetch.domain.synapse.SynapseAction.OpenFolder -> {
                platform.openOutput(action.path ?: state.value.destination)
            }
            is com.biglexj.lunafetch.domain.synapse.SynapseAction.PlayInPrisma -> {
                playInPrisma(action.filePath)
            }
            is com.biglexj.lunafetch.domain.synapse.SynapseAction.Focus -> {
                // Gestionado en la capa de ventana
            }
        }
    }

    fun selectTargetPeer(peer: com.biglexj.lunafetch.domain.synapse.lan.SynapseDevice?) {
        _state.update { it.copy(selectedTargetPeer = peer) }
    }

    fun pushDownloadToPeer(peer: com.biglexj.lunafetch.domain.synapse.lan.SynapseDevice) {
        val url = state.value.url.trim()
        if (!isSupportedUrl(url)) {
            _state.update { it.copy(error = "Escribe una URL válida antes de enviar.") }
            return
        }
        scope.launch {
            _state.update { it.copy(isSendingToPeer = true) }
            val format = state.value.selectedFormat
            val quality = state.value.selectedQuality.displayName
            val res = com.biglexj.lunafetch.domain.synapse.lan.SynapseLanClient.pushDownload(
                peer = peer,
                url = url,
                mediaType = if (format.isAudio) "audio" else "video",
                quality = quality,
                sourceDeviceName = platform.deviceName,
            )
            _state.update { it.copy(isSendingToPeer = false) }
            res.onSuccess {
                showToast("🚀 Enlace enviado a ${peer.name} para descargar.")
            }.onFailure { err ->
                showToast("❌ Error al enviar a ${peer.name}: ${err.message}")
            }
        }
    }

    fun syncHistoryWithPeer(peer: com.biglexj.lunafetch.domain.synapse.lan.SynapseDevice, silent: Boolean = false) {
        scope.launch {
            if (!silent) showToast("🔄 Sincronizando historial con ${peer.name}...")
            val res = com.biglexj.lunafetch.domain.synapse.lan.SynapseLanClient.syncHistory(
                peer = peer,
                localHistory = state.value.history,
                sourceDeviceName = platform.deviceName,
            )
            res.onSuccess { remoteMerged ->
                val combined = (remoteMerged + state.value.history)
                    .distinctBy { if (it.url.isNotBlank()) "${it.url}_${it.formatLabel}" else it.id }
                    .sortedByDescending { it.timestampMs }
                    .take(10)
                platform.saveHistory(combined)
                _state.update { it.copy(history = combined) }
                if (!silent) showToast("✅ Historial sincronizado con ${peer.name} (${combined.size} elementos).")
            }.onFailure { err ->
                if (!silent) showToast("❌ Error en sincronización con ${peer.name}: ${err.message}")
            }
        }
    }

    private fun autoBroadcastHistorySync() {
        val activePeers = state.value.discoveredPeers
        for (peer in activePeers) {
            syncHistoryWithPeer(peer, silent = true)
        }
    }

    private fun appendLog(line: String) {
        if (line.isBlank()) return
        _state.update { current -> current.copy(logs = (current.logs + line).takeLast(400)) }
    }

    companion object {
        fun isSupportedUrl(value: String): Boolean {
            val normalized = value.trim().lowercase()
            return (normalized.startsWith("https://") || normalized.startsWith("http://")) &&
                normalized.length > 10 && !normalized.any(Char::isWhitespace)
        }
    }
}

private fun Throwable.userMessage(fallback: String): String {
    val raw = message.orEmpty()
    return when {
        raw.contains("comfortable for some audiences", ignoreCase = true) ||
        raw.contains("Log in for access", ignoreCase = true) ->
            "Este video de TikTok tiene restricción de edad o contenido sensible impuesta por TikTok y requiere sesión iniciada (cookies)."
        raw.contains("Sign in to confirm", ignoreCase = true) ->
            "Este contenido requiere iniciar sesión."
        raw.isNotBlank() -> raw
        else -> fallback
    }
}

/** Resultado interno de una descarga, compartido por el botón manual y la cola. */
private enum class DownloadOutcome {
    Completed,
    Failed,
    Cancelled,
}
