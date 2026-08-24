package com.biglexj.lunafetch.platform

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.biglexj.lunafetch.domain.DownloadEngine
import com.biglexj.lunafetch.domain.PlatformBindings

class AndroidPlatformBindings(
    context: Context,
    private val directoryPicker: suspend (Uri?) -> Uri?,
) : PlatformBindings {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences("lunafetch", Context.MODE_PRIVATE)
    override val engine: DownloadEngine = AndroidDownloadEngine(appContext)

    init {
        runCatching {
            val wifi = appContext.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
            val lock = wifi?.createMulticastLock("LunaSynapseMulticastLock")
            lock?.setReferenceCounted(true)
            lock?.acquire()
        }
    }

    override val deviceName: String
        get() = "${android.os.Build.MANUFACTURER.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }} ${android.os.Build.MODEL}"

    override val deviceType: String
        get() = "mobile"

    override val deviceOs: String
        get() = "android"

    override val defaultDestination: String
        get() = preferences.getString("downloadTree", "").orEmpty()

    override suspend fun chooseDestination(current: String): String? = directoryPicker(
        current.takeIf(String::isNotBlank)?.let(Uri::parse),
    )?.toString()

    override fun destinationLabel(destination: String): String {
        if (destination.isBlank()) return "Seleccionar carpeta"
        return Uri.decode(Uri.parse(destination).lastPathSegment.orEmpty())
            .substringAfterLast(':')
            .ifBlank { "Carpeta seleccionada" }
    }

    override fun rememberDestination(destination: String) {
        preferences.edit().putString("downloadTree", destination).apply()
    }

    override fun isLocalPathAccessible(path: String): Boolean {
        if (path.isBlank()) return false
        // Si es una ruta típica de Windows (ej. D:\ o C:\), definitivamente no es local en Android
        if (path.matches(Regex("^[a-zA-Z]:[/\\\\].*"))) return false
        return runCatching {
            if (path.startsWith("content://")) {
                val uri = Uri.parse(path)
                appContext.contentResolver.openInputStream(uri)?.use { true } ?: false
            } else {
                val f = java.io.File(path)
                f.exists() && f.length() > 0
            }
        }.getOrDefault(false)
    }

    override fun openOutput(path: String) {
        if (path.isBlank() || !isLocalPathAccessible(path)) return
        runCatching {
            val uri: Uri
            val mimeType: String
            if (path.startsWith("content://")) {
                uri = Uri.parse(path)
                mimeType = appContext.contentResolver.getType(uri) ?: "*/*"
            } else {
                val file = java.io.File(path)
                uri = androidx.core.content.FileProvider.getUriForFile(
                    appContext,
                    "${appContext.packageName}.fileprovider",
                    file,
                )
                val ext = file.extension.lowercase()
                mimeType = when (ext) {
                    "mp4", "mkv", "webm", "avi", "mov", "3gp" -> "video/*"
                    "mp3", "m4a", "wav", "flac", "aac", "ogg", "opus" -> "audio/*"
                    else -> "*/*"
                }
            }

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, "Reproducir o abrir con").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(chooser)
        }.onFailure {
            runCatching {
                appContext.startActivity(
                    Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }

    override fun openDestinationFolder(destination: String) {
        if (destination.isBlank()) return
        runCatching {
            if (destination.startsWith("content://")) {
                val uri = Uri.parse(destination)
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "vnd.android.document/root")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                appContext.startActivity(intent)
            } else {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(Uri.parse("content://media/external/file"), "*/*")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                appContext.startActivity(intent)
            }
        }.onFailure {
            runCatching {
                appContext.startActivity(
                    Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }

    override fun openUrl(url: String) {
        if (url.isNotBlank()) {
            runCatching {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                appContext.startActivity(intent)
            }
        }
    }

    override fun readClipboardText(): String? {
        return runCatching {
            val clipboard = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
            clipboard?.primaryClip?.getItemAt(0)?.text?.toString()
        }.getOrNull()
    }

    override suspend fun checkUpdate(): com.biglexj.lunafetch.domain.UpdateRelease? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val preferredAbi = android.os.Build.SUPPORTED_ABIS.firstOrNull()
        runCatching {
            val url = java.net.URL("https://api.github.com/repos/biglexj/Luna---Fetch/releases/latest")
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("User-Agent", "LunaFetch-Updater")
            if (connection.responseCode == 200) {
                val json = connection.inputStream.bufferedReader().use { it.readText() }
                com.biglexj.lunafetch.domain.UpdateChecker.parseUpdateRelease(json, preferredAbi)
            } else null
        }.getOrNull()
    }

    override fun downloadAndInstallUpdate(release: com.biglexj.lunafetch.domain.UpdateRelease) {
        openUrl(release.releasePageUrl)
    }

    override suspend fun downloadUpdateFile(
        release: com.biglexj.lunafetch.domain.UpdateRelease,
        onProgress: (Float) -> Unit,
    ): String? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val downloadUrl = release.downloadUrl.takeIf { it.isNotBlank() && it.endsWith(".apk", ignoreCase = true) } ?: return@withContext null
        val targetFile = java.io.File(appContext.cacheDir, "LunaFetch-v${release.version}.apk")

        runCatching {
            var currentUrl = downloadUrl
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

            // Verify integrity (must be > 100KB and start with ZIP magic bytes PK\x03\x04)
            if (!targetFile.exists() || targetFile.length() < 100_000) {
                targetFile.delete()
                return@withContext null
            }
            val header = ByteArray(4)
            targetFile.inputStream().use { it.read(header) }
            if (header[0] != 0x50.toByte() || header[1] != 0x4B.toByte()) {
                targetFile.delete()
                return@withContext null
            }

            onProgress(1f)
            targetFile.absolutePath
        }.getOrNull()
    }

    override fun installDownloadedApk(filePath: String) {
        val apkFile = java.io.File(filePath)
        if (!apkFile.exists()) return

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            if (!appContext.packageManager.canRequestPackageInstalls()) {
                runCatching {
                    val settingsIntent = Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${appContext.packageName}"),
                    ).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    appContext.startActivity(settingsIntent)
                }
            }
        }

        runCatching {
            val contentUri = androidx.core.content.FileProvider.getUriForFile(
                appContext,
                "${appContext.packageName}.fileprovider",
                apkFile,
            )
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(installIntent)
        }
    }

    private val jsonSerializer = kotlinx.serialization.json.Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
    }

    override fun loadHistory(): List<com.biglexj.lunafetch.domain.DownloadHistoryItem> {
        val raw = preferences.getString("downloadHistory", null) ?: return emptyList()
        return runCatching {
            jsonSerializer.decodeFromString<List<com.biglexj.lunafetch.domain.DownloadHistoryItem>>(raw)
        }.getOrDefault(emptyList())
    }

    override fun saveHistory(history: List<com.biglexj.lunafetch.domain.DownloadHistoryItem>) {
        runCatching {
            val json = jsonSerializer.encodeToString(history)
            preferences.edit().putString("downloadHistory", json).apply()
        }
    }

    override suspend fun getEngineChannel(): String =
        preferences.getString("lastYtdlpChannel", com.biglexj.lunafetch.domain.EngineChannel.STABLE.wire)
            ?: com.biglexj.lunafetch.domain.EngineChannel.STABLE.wire

    override suspend fun setEngineChannel(channel: String) {
        preferences.edit().putString("lastYtdlpChannel", channel).apply()
    }

    override suspend fun getEngineComponentStatus(): String = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val channel = com.biglexj.lunafetch.domain.EngineChannel.fromWire(getEngineChannel())
        val lastUpdate = preferences.getLong("lastYtdlpUpdate", 0L)
        if (lastUpdate > 0) "Componentes nativos (Canal ${channel.label})" else "Componentes nativos activos (Canal ${channel.label})"
    }

    override suspend fun updateEngineComponents(channel: String): Result<String> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val engineChannel = com.biglexj.lunafetch.domain.EngineChannel.fromWire(channel)
        runCatching {
            val updateChannel = when (engineChannel) {
                com.biglexj.lunafetch.domain.EngineChannel.STABLE -> com.yausername.youtubedl_android.YoutubeDL.UpdateChannel._STABLE
                com.biglexj.lunafetch.domain.EngineChannel.NIGHTLY -> com.yausername.youtubedl_android.YoutubeDL.UpdateChannel._NIGHTLY
            }
            com.yausername.youtubedl_android.YoutubeDL.updateYoutubeDL(appContext, updateChannel)
            preferences.edit().putLong("lastYtdlpUpdate", System.currentTimeMillis()).putString("lastYtdlpChannel", engineChannel.wire).apply()
            Result.success("Componentes nativos actualizados al canal ${engineChannel.label}.")
        }.getOrElse {
            Result.success("Los componentes nativos están actualizados al canal ${engineChannel.label}.")
        }
    }
}
