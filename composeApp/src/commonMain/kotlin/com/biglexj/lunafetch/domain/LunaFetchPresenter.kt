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
            showToast("📥 Descarga remota desde ${req.sourceDevice}...")
            startDirectDownload(req.url, req.mediaType, req.quality)
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
        }
    )

    init {
        refreshHistory()
        checkForUpdates()
        startLanServices()
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
                    }
                }
        }
    }

    /**
     * Triggered by browser extension / external requests.
     * Analyzes the URL and starts downloading automatically in the background
     * without showing popups or asking for user confirmation.
     */
    fun startDirectDownload(rawUrl: String, formatName: String = "mp4", requestedQuality: String? = null) {
        val url = TikTokUtils.sanitizeUrl(rawUrl)
        if (!isSupportedUrl(url)) return
        val format = if (formatName.contains("audio", ignoreCase = true) || formatName.contains("mp3", ignoreCase = true) || formatName.contains("m4a", ignoreCase = true)) MediaFormat.Mp3 else MediaFormat.Mp4
        
        setUrl(url)
        selectFormat(format)

        operation?.cancel()
        operation = scope.launch {
            _state.update { it.copy(isAnalyzing = true, video = null, error = null, completedOutput = null) }
            runCatching { platform.engine.analyze(url) }
                .onSuccess { video ->
                    val qualities = FormatCatalog.qualities(format, video.maxHeight)
                    val matchedQuality = if (!requestedQuality.isNullOrBlank()) {
                        qualities.firstOrNull { 
                            it.displayName.contains(requestedQuality, ignoreCase = true) ||
                            it.formatSelector.contains(requestedQuality, ignoreCase = true)
                        } ?: qualities.first()
                    } else {
                        qualities.first()
                    }
                    val hasSingleVideoId = url.contains("watch?v=") || url.contains("v=") || url.contains("youtu.be/")
                    val shouldDefaultToCollection = video.isCollection && !hasSingleVideoId
                    _state.update { current ->
                        current.copy(
                            video = video,
                            selectedFormat = format,
                            qualities = qualities,
                            selectedQuality = matchedQuality,
                            downloadCollection = shouldDefaultToCollection,
                            isAnalyzing = false,
                        )
                    }
                    download()
                }
                .onFailure { error ->
                    if (error !is CancellationException) {
                        val msg = error.userMessage("No se pudo analizar el enlace.")
                        appendLog("ERROR: $msg")
                        _state.update { it.copy(isAnalyzing = false, error = msg) }
                    }
                }
        }
    }

    fun chooseDestination() {
        operation = scope.launch {
            platform.chooseDestination(state.value.destination)?.let { destination ->
                platform.rememberDestination(destination)
                _state.update { it.copy(destination = destination, error = null) }
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
            _state.update { it.copy(error = "Selecciona una carpeta de destino.") }
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
            _state.update {
                it.copy(
                    isDownloading = true,
                    progress = DownloadProgress(0.0, phase = DownloadPhase.Preparing),
                    logs = emptyList(),
                    error = null,
                    completedOutput = null,
                )
            }
            try {
                val result = platform.engine.download(
                    request = request,
                    onProgress = { progress -> _state.update { it.copy(progress = progress) } },
                    onLog = ::appendLog,
                )
                val newItem = DownloadHistoryItem(
                    id = "${System.currentTimeMillis()}_${(1000..9999).random()}",
                    title = video.collectionTitle ?: video.title,
                    formatLabel = "${current.selectedFormat.displayName} · ${current.selectedQuality.displayName}",
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
            } catch (cancelled: CancellationException) {
                _state.update {
                    it.copy(isDownloading = false, progress = DownloadProgress(0.0, phase = DownloadPhase.Cancelled))
                }
            } catch (error: Throwable) {
                val msg = error.userMessage("La descarga no pudo completarse.")
                appendLog("ERROR: $msg")
                _state.update {
                    it.copy(
                        isDownloading = false,
                        error = msg,
                    )
                }
            }
        }
    }

    fun cancel() {
        platform.engine.cancel()
        operation?.cancel()
    }

    fun openCompletedOutput() = state.value.completedOutput?.let(platform::openOutput)

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

    fun redownloadHistoryItem(item: DownloadHistoryItem) {
        if (item.url.isBlank()) return
        showToast("🔄 Iniciando descarga de nuevo: ${item.title}")
        val formatName = if (item.formatLabel.contains("Audio", ignoreCase = true) || item.formatLabel.contains("MP3", ignoreCase = true) || item.formatLabel.contains("M4A", ignoreCase = true)) "mp3" else "mp4"
        val qualityName = item.formatLabel.substringAfter("·", "").trim().takeIf { it.isNotBlank() }
        startDirectDownload(item.url, formatName = formatName, requestedQuality = qualityName)
    }

    fun handleSynapseAction(action: com.biglexj.lunafetch.domain.synapse.SynapseAction) {
        when (action) {
            is com.biglexj.lunafetch.domain.synapse.SynapseAction.EnqueueDownload -> {
                if (action.silent) {
                    showToast("📥 Descargando desde Aurora Synapse...")
                }
                val format = if (action.mediaType.equals("audio", ignoreCase = true)) MediaFormat.Mp3 else MediaFormat.Mp4
                startDirectDownload(action.url, format.extension, action.quality)
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
