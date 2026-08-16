package com.biglexj.lunafetch.domain

interface DownloadEngine {
    suspend fun analyze(url: String): VideoInfo

    suspend fun download(
        request: DownloadRequest,
        onProgress: (DownloadProgress) -> Unit,
        onLog: (String) -> Unit,
    ): DownloadResult

    fun cancel()
}

interface PlatformBindings {
    val engine: DownloadEngine
    val defaultDestination: String

    val deviceName: String get() = "Dispositivo Luna"
    val deviceType: String get() = "desktop"
    val deviceOs: String get() = "windows"

    // Desktop-only settings; null means "not applicable on this platform"
    val isAutoStartEnabled: Boolean? get() = null
    val isMinimizeToTrayEnabled: Boolean? get() = null
    val isNativeHostInstalled: Boolean? get() = null
    fun setAutoStart(enabled: Boolean) {}
    fun setMinimizeToTray(enabled: Boolean) {}
    fun installNativeHost() {}
    fun uninstallNativeHost() {}

    suspend fun chooseDestination(current: String): String?
    fun destinationLabel(destination: String): String
    fun rememberDestination(destination: String)
    fun openOutput(path: String)
    fun openDestinationFolder(destination: String) { openOutput(destination) }
    fun isLocalPathAccessible(path: String): Boolean = false
    fun openInPrisma(filePath: String): Boolean = false
    fun openUrl(url: String) {}
    fun readClipboardText(): String? = null

    suspend fun checkUpdate(): UpdateRelease? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            val url = java.net.URL("https://api.github.com/repos/biglexj/Luna---Fetch/releases/latest")
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("User-Agent", "LunaFetch-Updater")
            if (connection.responseCode == 200) {
                val json = connection.inputStream.bufferedReader().use { it.readText() }
                UpdateChecker.parseUpdateRelease(json)
            } else null
        }.getOrNull()
    }

    fun downloadAndInstallUpdate(release: UpdateRelease) {}

    suspend fun downloadUpdateFile(release: UpdateRelease, onProgress: (Float) -> Unit): String? = null
    fun installDownloadedApk(filePath: String) {}

    fun loadHistory(): List<DownloadHistoryItem> = emptyList()
    fun saveHistory(history: List<DownloadHistoryItem>) {}

    suspend fun getEngineComponentStatus(): String = "Componentes activos"
    suspend fun updateEngineComponents(): Result<String> = Result.success("Los componentes del motor están actualizados.")
}
