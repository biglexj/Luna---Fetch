package com.biglexj.lunafetch.platform

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.biglexj.lunafetch.domain.DownloadEngine
import com.biglexj.lunafetch.domain.DownloadException
import com.biglexj.lunafetch.domain.DownloadProgress
import com.biglexj.lunafetch.domain.DownloadRequest
import com.biglexj.lunafetch.domain.DownloadResult
import com.biglexj.lunafetch.domain.CollectionEntry
import com.biglexj.lunafetch.domain.VideoInfo
import com.biglexj.lunafetch.domain.YtdlpProtocol
import com.biglexj.lunafetch.domain.isCollection
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.ffmpeg.FFmpeg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

class AndroidDownloadEngine(private val context: Context) : DownloadEngine {
    @Volatile
    private var processId: String? = null
    @Volatile
    private var initialized = false

    override suspend fun analyze(url: String): VideoInfo = withContext(Dispatchers.IO) {
        initialize()
        AndroidCookieJar.ensureClearanceCookies(context, url)
        try {
            executeAnalyze(url)
        } catch (error: Exception) {
            val message = error.message.orEmpty()
            android.util.Log.w("LunaFetchEngine", "First analyze attempt failed: $message. Checking cookie resolution or update fallback...")
            if (message.contains("403", ignoreCase = true) ||
                message.contains("Forbidden", ignoreCase = true) ||
                message.contains("Unable to download webpage", ignoreCase = true) ||
                message.contains("Unable to extract", ignoreCase = true) ||
                message.contains("Unexpected response", ignoreCase = true) ||
                message.contains("please report this issue", ignoreCase = true)
            ) {
                if (message.contains("403", ignoreCase = true) ||
                    message.contains("Forbidden", ignoreCase = true) ||
                    message.contains("Unable to download webpage", ignoreCase = true)
                ) {
                    android.util.Log.d("LunaFetchEngine", "Attempting WebView cookie resolution for $url...")
                    val cookieResolved = runCatching { AndroidCookieJar.resolveWebCookies(context, url) }.getOrDefault(false)
                    if (cookieResolved) {
                        android.util.Log.d("LunaFetchEngine", "WebView cookies resolved successfully. Retrying analyze...")
                        val retryRes = runCatching { executeAnalyze(url) }
                        if (retryRes.isSuccess) {
                            return@withContext retryRes.getOrThrow()
                        }
                    }
                }

                android.util.Log.d("LunaFetchEngine", "Triggering forced update to NIGHTLY and retrying analyze...")
                updateYtdlpIfNeeded(forceNightly = true)
                runCatching { executeAnalyze(url) }.getOrElse {
                    android.util.Log.e("LunaFetchEngine", "Subsequent analyze attempt also failed: ${it.message}")
                    throw DownloadException(it.message ?: error.message ?: "No se pudo analizar el enlace en Android.", it)
                }
            } else {
                throw DownloadException(error.message ?: "No se pudo analizar el enlace en Android.", error)
            }
        }
    }

    private fun executeAnalyze(url: String): VideoInfo {
        val cmds = YtdlpProtocol.buildAnalyzeArguments(url) + "--no-warnings"
        android.util.Log.d("LunaFetchEngine", "executeAnalyze url: $url, commands: $cmds")
        val response = YoutubeDL.execute(
            androidRequest(url).addCommands(cmds),
        )
        android.util.Log.d("LunaFetchEngine", "executeAnalyze exitCode: ${response.exitCode}, err: ${response.err.take(500)}")
        if (response.exitCode != 0) {
            throw DownloadException(response.err.ifBlank { "yt-dlp no pudo analizar el enlace." })
        }
        val parsed = response.out.toVideoInfo(url)
        return if (parsed.thumbnailUrl.isBlank() && parsed.isCollection) {
            response.out.firstCollectionVideoUrl()?.let { firstVideoUrl ->
                val firstItem = YoutubeDL.getInfo(
                    androidRequest(firstVideoUrl).addOption("--no-playlist"),
                )
                parsed.copy(
                    thumbnailUrl = firstItem.thumbnail.orEmpty().ifBlank {
                        firstItem.thumbnails?.lastOrNull()?.url.orEmpty()
                    },
                )
            } ?: parsed
        } else {
            parsed
        }
    }

    override suspend fun download(
        request: DownloadRequest,
        onProgress: (DownloadProgress) -> Unit,
        onLog: (String) -> Unit,
    ): DownloadResult = withContext(Dispatchers.IO) {
        initialize()
        AndroidCookieJar.ensureClearanceCookies(context, request.url)
        if (request.destination.isBlank()) {
            throw DownloadException("No se ha seleccionado una carpeta de destino en este dispositivo.")
        }
        val treeUri = runCatching { Uri.parse(request.destination) }.getOrNull()
            ?.takeIf { it != Uri.EMPTY && !it.scheme.isNullOrBlank() }
            ?: throw DownloadException("Selecciona una carpeta de destino válida con permisos de almacenamiento.")
        val workRoot = File(context.cacheDir, "downloads")
        val workDirectory = File(workRoot, System.currentTimeMillis().toString()).apply { mkdirs() }
        val outputTemplate = File(
            workDirectory,
            if (request.downloadCollection) "%(playlist_index)03d - %(title)s.%(ext)s" else "%(title)s.%(ext)s",
        ).absolutePath
        val id = "lunafetch-${System.currentTimeMillis()}"
        processId = id
        DownloadForegroundService.start(context)

        try {
            val command = YtdlpProtocol.buildDownloadArguments(request, outputTemplate)
            val youtubeRequest = androidRequest(request.url).addCommands(command)
            val response = YoutubeDL.execute(youtubeRequest, id) { percentage, etaSeconds, line ->
                onLog(line)
                YtdlpProtocol.parseProgress(line)?.let(onProgress)
                val progress = DownloadProgress(
                    percentage = percentage.toDouble().coerceIn(0.0, 100.0),
                    eta = etaSeconds.takeIf { it >= 0 }?.let { "${it}s" }.orEmpty(),
                )
                onProgress(progress)
                DownloadForegroundService.update(context, percentage.toInt())
            }
            if (response.exitCode != 0) {
                throw DownloadException(response.err.ifBlank { "yt-dlp terminó con código ${response.exitCode}." })
            }
            response.out.lineSequence().filter(String::isNotBlank).forEach(onLog)
            val downloaded = response.out.lineSequence()
                .mapNotNull(YtdlpProtocol::outputPath)
                .map(::File)
                .filter(File::isFile)
                .distinctBy { it.absolutePath }
                .toList()
                .ifEmpty {
                    workDirectory.walkTopDown()
                        .filter(File::isFile)
                        .filterNot { it.extension.equals("part", true) }
                        .toList()
                }
            if (downloaded.isEmpty()) {
                throw DownloadException("La descarga terminó, pero no se encontró el archivo resultante.")
            }
            val resultUris = copyToTree(downloaded, treeUri)
            DownloadResult(
                outputPaths = resultUris.map(Uri::toString),
                openPath = if (resultUris.size == 1) resultUris.first().toString() else treeUri.toString(),
            )
        } catch (error: Exception) {
            throw DownloadException(error.message ?: "No se pudo completar la descarga en Android.", error)
        } finally {
            processId = null
            DownloadForegroundService.stop(context)
            workDirectory.deleteRecursively()
        }
    }

    override fun cancel() {
        processId?.let(YoutubeDL::destroyProcessById)
        processId = null
        DownloadForegroundService.stop(context)
    }

    @Synchronized
    private fun initialize() {
        if (initialized) return
        try {
            YoutubeDL.init(context)
            FFmpeg.init(context)
            updateYtdlpIfNeeded()
            initialized = true
        } catch (error: Exception) {
            throw DownloadException("No se pudo inicializar el motor local de Android.", error)
        }
    }

    private fun androidRequest(url: String): YoutubeDLRequest {
        val req = YoutubeDLRequest(url)
        val isYouTube = url.contains("youtube.com", ignoreCase = true) || url.contains("youtu.be", ignoreCase = true)
        if (isYouTube) {
            req.addOption("--js-runtimes", "quickjs")
            req.addOption("--remote-components", "ejs:github")
        } else {
            req.addOption("--no-check-certificates")
        }

        val cookieFile = AndroidCookieJar.cookieFile(context).let {
            if (it.exists() && it.length() > 0) it else File(context.cacheDir, "luna_session_cookies.txt")
        }
        if (cookieFile.exists() && cookieFile.length() > 0) {
            req.addOption("--cookies", cookieFile.absolutePath)
        }
        return req
    }

    private fun updateYtdlpIfNeeded(forceNightly: Boolean = false) {
        val preferences = context.getSharedPreferences("lunafetch", Context.MODE_PRIVATE)
        val lastUpdate = preferences.getLong("lastYtdlpUpdate", 0L)
        val lastChannel = preferences.getString("lastYtdlpChannel", "NIGHTLY") ?: "NIGHTLY"
        val now = System.currentTimeMillis()
        android.util.Log.d("LunaFetchEngine", "updateYtdlpIfNeeded: forceNightly=$forceNightly, lastChannel=$lastChannel, currentVersion=${runCatching { YoutubeDL.version(context) }.getOrNull()}")
        if (!forceNightly && lastChannel == "NIGHTLY" && now - lastUpdate < UpdateIntervalMillis) {
            android.util.Log.d("LunaFetchEngine", "updateYtdlpIfNeeded: Skipped because updated recently (${now - lastUpdate}ms ago)")
            return
        }

        val nightlyResult = runCatching {
            android.util.Log.d("LunaFetchEngine", "Updating to NIGHTLY channel...")
            val res = YoutubeDL.updateYoutubeDL(context, YoutubeDL.UpdateChannel._NIGHTLY)
            android.util.Log.d("LunaFetchEngine", "Update to NIGHTLY status: $res, version is now: ${YoutubeDL.version(context)}")
            preferences.edit().putLong("lastYtdlpUpdate", now).putString("lastYtdlpChannel", "NIGHTLY").apply()
        }
        if (nightlyResult.isFailure) {
            android.util.Log.e("LunaFetchEngine", "Update to NIGHTLY failed: ${nightlyResult.exceptionOrNull()?.message}", nightlyResult.exceptionOrNull())
            runCatching {
                android.util.Log.d("LunaFetchEngine", "Falling back to STABLE channel...")
                val res = YoutubeDL.updateYoutubeDL(context, YoutubeDL.UpdateChannel._STABLE)
                android.util.Log.d("LunaFetchEngine", "Update to STABLE status: $res, version is now: ${YoutubeDL.version(context)}")
                preferences.edit().putLong("lastYtdlpUpdate", now).putString("lastYtdlpChannel", "STABLE").apply()
            }.onFailure {
                android.util.Log.e("LunaFetchEngine", "Update to STABLE also failed: ${it.message}", it)
            }
        }
    }

    private fun copyToTree(sources: List<File>, treeUri: Uri): List<Uri> {
        val resolver = context.contentResolver
        return try {
            val parent = DocumentsContract.buildDocumentUriUsingTree(
                treeUri,
                DocumentsContract.getTreeDocumentId(treeUri),
            )
            sources.map { source ->
                val created = DocumentsContract.createDocument(
                    resolver,
                    parent,
                    mimeType(source.extension),
                    source.name,
                ) ?: throw DownloadException("Android no permitió crear el archivo en la carpeta elegida.")
                resolver.openOutputStream(created, "w")?.use { output ->
                    source.inputStream().use { input -> input.copyTo(output) }
                } ?: throw DownloadException("Android no permitió escribir el archivo descargado.")
                created
            }
        } catch (se: SecurityException) {
            throw DownloadException("Permiso de almacenamiento revocado o carpeta inaccesible. Vuelve a seleccionarla en Luna Fetch.", se)
        } catch (iae: IllegalArgumentException) {
            throw DownloadException("La carpeta seleccionada ya no es válida. Vuelve a seleccionarla en Luna Fetch.", iae)
        }
    }

    private fun String.toVideoInfo(url: String): VideoInfo {
        val root = Json.parseToJsonElement(this).jsonObject
        val rawEntries = root["entries"]?.jsonArray ?: JsonArray(emptyList())
        val entries = rawEntries.mapIndexedNotNull { index, entry ->
            entry.jsonObject.let {
                CollectionEntry(
                    index = it.int("playlist_index", index + 1),
                    title = it.string("title").ifBlank { return@let null },
                    uploader = it.string("uploader").ifBlank { it.string("channel") },
                    durationSeconds = it.double("duration"),
                )
            }
        }
        val isCollection = entries.size > 1
        val thumbnail = if (isCollection) {
            root.thumbnail().ifBlank { rawEntries.firstYoutubeThumbnail() }
        } else {
            root.thumbnail()
        }
        return VideoInfo(
            url = url,
            title = root.string("title").ifBlank { "Título desconocido" },
            uploader = root.string("uploader").ifBlank { root.string("channel").ifBlank { "Autor desconocido" } },
            durationSeconds = root.double("duration"),
            thumbnailUrl = thumbnail,
            maxHeight = root["formats"]?.jsonArray?.maxOfOrNull { it.jsonObject.int("height") }
                ?.takeIf { it > 0 } ?: root.int("height", 1080),
            collectionTitle = root.string("playlist_title").ifBlank { if (isCollection) root.string("title") else "" }
                .ifBlank { null },
            collectionCount = root.int("playlist_count", entries.size),
            collectionEntries = entries,
        )
    }

    private fun JsonArray.firstThumbnail(): String =
        firstOrNull()?.jsonObject?.thumbnail().orEmpty()

    private fun JsonArray.firstYoutubeThumbnail(): String =
        firstOrNull()?.jsonObject?.string("id")
            ?.takeIf { it.isNotBlank() }
            ?.let { "https://i.ytimg.com/vi/$it/hqdefault.jpg" }
            .orEmpty()

    private fun JsonObject.thumbnail(): String {
        val directThumbnail = string("thumbnail")
        if (directThumbnail.isNotBlank() && (!directThumbnail.contains("/s_p/") || directThumbnail.contains("?"))) {
            return directThumbnail
        }
        val list = this["thumbnails"]?.jsonArray
            ?.mapNotNull {
                val u = it.jsonObject.string("url")
                if (u.contains("/s_p/") && !u.contains("?")) null else u.takeIf { it.isNotBlank() }
            }
            .orEmpty()
        return list.lastOrNull() ?: directThumbnail
    }

    private fun JsonObject.string(name: String): String =
        this[name]?.jsonPrimitive?.contentOrNull.orEmpty()

    private fun JsonObject.int(name: String, fallback: Int = 0): Int =
        this[name]?.jsonPrimitive?.content?.toIntOrNull() ?: fallback

    private fun JsonObject.double(name: String): Double =
        this[name]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0

    private fun String.firstCollectionVideoUrl(): String? {
        val entry = Json.parseToJsonElement(this).jsonObject["entries"]
            ?.jsonArray
            ?.firstOrNull()
            ?.jsonObject
            ?: return null
        return entry.string("webpage_url")
            .ifBlank { entry.string("original_url") }
            .ifBlank {
                entry.string("url").takeIf { it.startsWith("http://") || it.startsWith("https://") }.orEmpty()
            }
            .ifBlank {
                entry.string("id").ifBlank { entry.string("url") }
                    .takeIf { it.isNotBlank() }
                    ?.let { "https://www.youtube.com/watch?v=$it" }
                    .orEmpty()
            }
            .takeIf { it.isNotBlank() }
    }

    private fun mimeType(extension: String): String = when (extension.lowercase()) {
        "mp4", "m4v" -> "video/mp4"
        "webm" -> "video/webm"
        "mp3" -> "audio/mpeg"
        "m4a" -> "audio/mp4"
        "flac" -> "audio/flac"
        "wav" -> "audio/wav"
        else -> "application/octet-stream"
    }

    private companion object {
        const val UpdateIntervalMillis = 24L * 60L * 60L * 1000L
    }
}
