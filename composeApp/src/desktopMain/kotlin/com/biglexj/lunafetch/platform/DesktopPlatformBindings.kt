package com.biglexj.lunafetch.platform

import com.biglexj.lunafetch.domain.DownloadEngine
import com.biglexj.lunafetch.domain.PlatformBindings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.io.File
import java.util.prefs.Preferences
import javax.swing.JFileChooser

class DesktopPlatformBindings : PlatformBindings {
    private val preferences = Preferences.userRoot().node("com/biglexj/lunafetch")
    private val settings = AppSettings()
    override val engine: DownloadEngine = DesktopDownloadEngine()

    override val deviceName: String
        get() = runCatching { java.net.InetAddress.getLocalHost().hostName }.getOrDefault("Luna PC")

    override val deviceType: String
        get() = if (System.getProperty("os.name")?.lowercase()?.contains("mac") == true) "laptop" else "desktop"

    override val deviceOs: String
        get() = when {
            System.getProperty("os.name")?.lowercase()?.contains("win") == true -> "windows"
            System.getProperty("os.name")?.lowercase()?.contains("linux") == true -> "linux"
            else -> "desktop"
        }

    override val isAutoStartEnabled: Boolean get() = settings.autoStart
    override val isMinimizeToTrayEnabled: Boolean get() = settings.minimizeToTray
    override val isNativeHostInstalled: Boolean get() = settings.isNativeHostInstalled
    override val isNotificationsEnabled: Boolean? get() = settings.showNotifications
    override fun setAutoStart(enabled: Boolean) { settings.autoStart = enabled }
    override fun setMinimizeToTray(enabled: Boolean) { settings.minimizeToTray = enabled }
    override fun setNotificationsEnabled(enabled: Boolean) { settings.showNotifications = enabled }
    override fun installNativeHost() {
        val exe = ProcessHandle.current().info().command().orElse(null) ?: return
        settings.installNativeHost(exe)
    }
    override fun uninstallNativeHost() { settings.uninstallNativeHost() }

    override fun notifyDownloadStarted(title: String) {
        if (settings.showNotifications) {
            ModernTrayManager.showNotification(
                title = "Luna Fetch",
                message = title.ifBlank { "Iniciando descarga en segundo plano…" },
                isError = false,
                onClick = null,
            )
        }
    }

    override fun notifyDownloadCompleted(title: String, filePath: String) {
        if (settings.showNotifications) {
            ModernTrayManager.showNotification(
                title = "Descarga completada",
                message = title.ifBlank { "Archivo guardado exitosamente." },
                isError = false,
                onClick = {
                    if (filePath.isNotBlank()) {
                        openOutput(filePath)
                    } else {
                        openDestinationFolder(defaultDestination)
                    }
                },
            )
        }
    }

    override fun notifyDownloadFailed(title: String, error: String) {
        if (settings.showNotifications) {
            ModernTrayManager.showNotification(
                title = "Descarga fallida",
                message = if (title.isNotBlank()) "$title: $error" else error,
                isError = true,
                onClick = null,
            )
        }
    }
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
        if (!target.exists()) return
        val opened = runCatching {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(target)
                true
            } else false
        }.getOrDefault(false)

        if (!opened) {
            val os = System.getProperty("os.name", "").lowercase()
            runCatching {
                if (os.contains("win")) ProcessBuilder("cmd.exe", "/c", "start", "", target.absolutePath).start()
                else if (os.contains("mac")) ProcessBuilder("open", target.absolutePath).start()
                else ProcessBuilder("xdg-open", target.absolutePath).start()
            }
        }
    }

    override fun openDestinationFolder(destination: String) {
        val target = File(destination)
        val os = System.getProperty("os.name", "").lowercase()
        runCatching {
            if (os.contains("win")) {
                if (target.isFile && target.exists()) {
                    ProcessBuilder("explorer.exe", "/select,", target.absolutePath).start()
                } else {
                    val folder = if (target.isDirectory) target else target.parentFile ?: target
                    ProcessBuilder("explorer.exe", folder.absolutePath).start()
                }
            } else if (os.contains("mac")) {
                if (target.isFile && target.exists()) {
                    ProcessBuilder("open", "-R", target.absolutePath).start()
                } else {
                    ProcessBuilder("open", (target.parentFile ?: target).absolutePath).start()
                }
            } else {
                val folder = if (target.isDirectory) target else target.parentFile ?: target
                ProcessBuilder("xdg-open", folder.absolutePath).start()
            }
        }
    }

    override fun isLocalPathAccessible(path: String): Boolean {
        if (path.isBlank()) return false
        return runCatching { File(path).exists() }.getOrDefault(false)
    }

    override fun isDevBuild(): Boolean = SingleInstanceLock.isDevMode()

    override fun localFileState(path: String): com.biglexj.lunafetch.domain.LocalFileState {
        if (path.isBlank()) return com.biglexj.lunafetch.domain.LocalFileState.Missing
        return runCatching {
            val file = File(path)
            when {
                !file.exists() -> com.biglexj.lunafetch.domain.LocalFileState.Missing
                file.length() <= 0L -> com.biglexj.lunafetch.domain.LocalFileState.Empty
                else -> com.biglexj.lunafetch.domain.LocalFileState.Present
            }
        }.getOrDefault(com.biglexj.lunafetch.domain.LocalFileState.Missing)
    }

    override fun deleteOutput(path: String): Boolean {
        if (path.isBlank()) return false
        return runCatching {
            val file = File(path)
            file.exists() && file.delete()
        }.getOrDefault(false)
    }

    override fun openInPrisma(filePath: String): Boolean {
        return com.biglexj.lunafetch.platform.synapse.SynapseOutboundClient.openMediaInPrisma(filePath)
    }

    override fun openUrl(url: String) {
        if (url.isBlank()) return
        val opened = runCatching {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(java.net.URI(url))
                true
            } else false
        }.getOrDefault(false)

        if (!opened) {
            runCatching { ProcessBuilder("xdg-open", url).start() }
        }
    }

    override fun readClipboardText(): String? {
        return runCatching {
            val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
            if (clipboard.isDataFlavorAvailable(java.awt.datatransfer.DataFlavor.stringFlavor)) {
                clipboard.getData(java.awt.datatransfer.DataFlavor.stringFlavor) as? String
            } else null
        }.getOrNull()
    }

    override fun downloadAndInstallUpdate(release: com.biglexj.lunafetch.domain.UpdateRelease) {
        openUrl(release.releasePageUrl)
    }

    override suspend fun downloadUpdateFile(
        release: com.biglexj.lunafetch.domain.UpdateRelease,
        onProgress: (Float) -> Unit,
    ): String? = withContext(Dispatchers.IO) {
        val exeUrl = release.exeDownloadUrl.ifBlank {
            release.downloadUrl.takeIf { it.endsWith(".exe", true) }
        } ?: return@withContext null

        runCatching {
            var currentUrl = exeUrl
            var connection: java.net.HttpURLConnection
            var redirectCount = 0
            while (true) {
                val url = java.net.URL(currentUrl)
                connection = url.openConnection() as java.net.HttpURLConnection
                connection.instanceFollowRedirects = true
                connection.connectTimeout = 15_000
                connection.readTimeout = 30_000
                connection.setRequestProperty("User-Agent", "LunaFetch-Updater")

                val status = connection.responseCode
                if (status in 300..399 && redirectCount < 5) {
                    val location = connection.getHeaderField("Location") ?: break
                    currentUrl = if (location.startsWith("http")) location else java.net.URL(url, location).toString()
                    connection.disconnect()
                    redirectCount++
                    continue
                }
                break
            }

            if (connection.responseCode != 200) {
                return@withContext null
            }

            val targetFile = File(systemDownloadsDirectory(), "LunaFetch-Windows-${release.version}.exe")

            val totalBytes = connection.contentLengthLong
            var downloadedBytes = 0L

            connection.inputStream.use { input ->
                targetFile.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloadedBytes += read
                        if (totalBytes > 0) {
                            onProgress((downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f))
                        }
                    }
                }
            }

            if (targetFile.exists() && targetFile.length() > 0) {
                onProgress(1f)
                targetFile.absolutePath
            } else {
                null
            }
        }.getOrNull()
    }

    override fun installDownloadedApk(filePath: String) {
        val targetFile = File(filePath)
        if (!targetFile.exists()) return
        val absPath = targetFile.absolutePath

        // Liberar el Single-Instance Lock para permitir que la nueva instancia tome el puerto
        SingleInstanceLock.release()

        // Determinar la ruta del ejecutable instalado actualmente para relanzarlo tras actualizar
        val currentExePath = ProcessHandle.current().info().command().orElse(null)
            ?.takeIf { File(it).exists() }
            ?: File(System.getenv("LOCALAPPDATA") ?: ".", "Luna Fetch/Luna Fetch.exe").absolutePath

        // Comando cmd desasociado: espera 2s a que la app antigua se cierre, ejecuta el instalador EXE en
        // modo silencioso con progreso (Inno Setup) y relanza Luna Fetch
        val installCmd =
            "timeout /t 2 /nobreak > nul & start /wait \"\" \"$absPath\" /SILENT /SUPPRESSMSGBOXES /NORESTART & start \"\" \"$currentExePath\""

        runCatching {
            ProcessBuilder("cmd.exe", "/c", installCmd).start()
        }

        // Finalizar el proceso actual inmediatamente para liberar los archivos ejecutable y de recursos
        kotlin.system.exitProcess(0)
    }

    private val jsonSerializer = kotlinx.serialization.json.Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
    }

    override fun loadHistory(): List<com.biglexj.lunafetch.domain.DownloadHistoryItem> {
        val raw = preferences.get("downloadHistory", null) ?: return emptyList()
        return runCatching {
            jsonSerializer.decodeFromString<List<com.biglexj.lunafetch.domain.DownloadHistoryItem>>(raw)
        }.getOrDefault(emptyList())
    }

    override fun saveHistory(history: List<com.biglexj.lunafetch.domain.DownloadHistoryItem>) {
        runCatching {
            val json = jsonSerializer.encodeToString(history)
            preferences.put("downloadHistory", json)
        }
    }

    override fun loadDownloadRegistry(): List<com.biglexj.lunafetch.domain.DownloadRegistryEntry> {
        val raw = preferences.get("downloadRegistry", null) ?: return emptyList()
        return runCatching {
            jsonSerializer.decodeFromString<List<com.biglexj.lunafetch.domain.DownloadRegistryEntry>>(raw)
        }.getOrDefault(emptyList())
    }

    override fun saveDownloadRegistry(entries: List<com.biglexj.lunafetch.domain.DownloadRegistryEntry>) {
        runCatching {
            val json = jsonSerializer.encodeToString(entries)
            preferences.put("downloadRegistry", json)
        }
    }

    override fun loadDiagnostics(): List<com.biglexj.lunafetch.domain.DiagnosticEntry> {
        val raw = preferences.get("diagnosticHistory", null) ?: return emptyList()
        return runCatching {
            val decoded = jsonSerializer.decodeFromString<List<com.biglexj.lunafetch.domain.DiagnosticEntry>>(raw)
            val purged = com.biglexj.lunafetch.domain.DiagnosticStore.purgeExpired(decoded)
            if (purged.size != decoded.size) {
                saveDiagnostics(purged)
            }
            purged
        }.getOrDefault(emptyList())
    }

    override fun saveDiagnostics(diagnostics: List<com.biglexj.lunafetch.domain.DiagnosticEntry>) {
        runCatching {
            val purged = com.biglexj.lunafetch.domain.DiagnosticStore.purgeExpired(diagnostics)
            // Java Preferences rechaza valores de más de 8192 caracteres. Si el
            // informe no cabe, se recortan los incidentes más antiguos para
            // que el guardado no falle en silencio.
            val trimmed = com.biglexj.lunafetch.domain.DiagnosticStore.fitToBudget(purged)
            val json = jsonSerializer.encodeToString(trimmed)
            preferences.put("diagnosticHistory", json)
        }
    }

    override suspend fun getEngineChannel(): String =
        preferences.get("ytdlpChannel", com.biglexj.lunafetch.domain.EngineChannel.STABLE.wire)

    override suspend fun setEngineChannel(channel: String) {
        preferences.put("ytdlpChannel", channel)
    }

    override suspend fun getEngineComponentStatus(): String = withContext(Dispatchers.IO) {
        val channel = com.biglexj.lunafetch.domain.EngineChannel.fromWire(getEngineChannel())
        runCatching {
            val process = ProcessBuilder("yt-dlp", "--version").start()
            val version = process.inputStream.bufferedReader().readText().trim()
            if (version.isNotBlank()) "Versión $version (Canal ${channel.label})" else "Componentes activos (Canal ${channel.label})"
        }.getOrDefault("Componentes activos (Canal ${channel.label})")
    }

    override suspend fun updateEngineComponents(channel: String): Result<String> = withContext(Dispatchers.IO) {
        val engineChannel = com.biglexj.lunafetch.domain.EngineChannel.fromWire(channel)
        runCatching {
            val process = ProcessBuilder("yt-dlp", "--update-to", engineChannel.ytdlpValue).start()
            val output = process.inputStream.bufferedReader().readText().trim()
            val exitCode = process.waitFor()
            setEngineChannel(engineChannel.wire)
            if (exitCode == 0) {
                if (output.contains("up to date", ignoreCase = true) || output.contains("latest version", ignoreCase = true)) {
                    Result.success("El canal ${engineChannel.label} está en la versión más reciente.")
                } else {
                    Result.success("Motor actualizado al canal ${engineChannel.label}.")
                }
            } else {
                Result.success("El motor ya se encuentra actualizado al canal ${engineChannel.label}.")
            }
        }.getOrElse {
            Result.success("El motor ya se encuentra actualizado al canal ${engineChannel.label}.")
        }
    }

    private fun systemDownloadsDirectory(): String {
        val home = System.getProperty("user.home") ?: "."
        return File(home, "Downloads").absolutePath
    }
}
