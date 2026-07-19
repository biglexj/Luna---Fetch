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
    fun openUrl(url: String) {}
}
