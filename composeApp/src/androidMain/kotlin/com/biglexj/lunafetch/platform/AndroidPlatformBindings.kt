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

    override fun openOutput(path: String) {
        val uri = Uri.parse(path)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, appContext.contentResolver.getType(uri) ?: "*/*")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { appContext.startActivity(intent) }.onFailure {
            appContext.startActivity(
                Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
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

    override fun downloadAndInstallUpdate(release: com.biglexj.lunafetch.domain.UpdateRelease) {
        if (release.downloadUrl.endsWith(".apk", ignoreCase = true)) {
            val dm = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as? android.app.DownloadManager
            if (dm != null) {
                runCatching {
                    val request = android.app.DownloadManager.Request(Uri.parse(release.downloadUrl)).apply {
                        setTitle("Descargando Luna Fetch v${release.version}")
                        setDescription("Actualización disponible de Luna Fetch")
                        setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        setDestinationInExternalPublicDir(
                            android.os.Environment.DIRECTORY_DOWNLOADS,
                            "LunaFetch-v${release.version}.apk",
                        )
                        setMimeType("application/vnd.android.package-archive")
                    }
                    dm.enqueue(request)
                    return
                }
            }
        }
        openUrl(release.releasePageUrl)
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
}
