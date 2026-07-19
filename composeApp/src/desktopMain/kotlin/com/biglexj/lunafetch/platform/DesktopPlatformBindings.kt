package com.biglexj.lunafetch.platform

import com.biglexj.lunafetch.domain.DownloadEngine
import com.biglexj.lunafetch.domain.PlatformBindings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.io.File
import java.util.prefs.Preferences
import javax.swing.JFileChooser

class DesktopPlatformBindings : PlatformBindings {
    private val preferences = Preferences.userRoot().node("com/biglexj/lunafetch")
    private val settings = AppSettings()
    override val engine: DownloadEngine = DesktopDownloadEngine()
    override val isAutoStartEnabled: Boolean get() = settings.autoStart
    override val isMinimizeToTrayEnabled: Boolean get() = settings.minimizeToTray
    override val isNativeHostInstalled: Boolean get() = settings.isNativeHostInstalled
    override fun setAutoStart(enabled: Boolean) { settings.autoStart = enabled }
    override fun setMinimizeToTray(enabled: Boolean) { settings.minimizeToTray = enabled }
    override fun installNativeHost() {
        val exe = ProcessHandle.current().info().command().orElse(null) ?: return
        settings.installNativeHost(exe)
    }
    override fun uninstallNativeHost() { settings.uninstallNativeHost() }
    override val defaultDestination: String
        get() = preferences.get("downloadDirectory", systemDownloadsDirectory())

    override suspend fun chooseDestination(current: String): String? = withContext(Dispatchers.IO) {
        val chooser = JFileChooser(current.takeIf { it.isNotBlank() } ?: systemDownloadsDirectory()).apply {
            dialogTitle = "Selecciona la carpeta de destino"
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            isAcceptAllFileFilterUsed = false
        }
        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile.absolutePath else null
    }

    override fun destinationLabel(destination: String): String = destination.ifBlank { "Seleccionar carpeta" }

    override fun rememberDestination(destination: String) {
        preferences.put("downloadDirectory", destination)
    }

    override fun openOutput(path: String) {
        val target = File(path)
        val openTarget = if (target.exists()) target else target.parentFile
        if (openTarget != null && openTarget.exists() && Desktop.isDesktopSupported()) {
            Desktop.getDesktop().open(openTarget)
        }
    }

    override fun openUrl(url: String) {
        if (url.isNotBlank() && Desktop.isDesktopSupported()) {
            runCatching { Desktop.getDesktop().browse(java.net.URI(url)) }
        }
    }

    private fun systemDownloadsDirectory(): String {
        val home = System.getProperty("user.home") ?: "."
        return File(home, "Downloads").absolutePath
    }
}
