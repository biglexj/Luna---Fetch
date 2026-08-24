package com.biglexj.lunafetch.platform

import com.biglexj.lunafetch.domain.DownloadEngine
import com.biglexj.lunafetch.domain.DownloadException
import com.biglexj.lunafetch.domain.DownloadPhase
import com.biglexj.lunafetch.domain.DownloadProgress
import com.biglexj.lunafetch.domain.DownloadRequest
import com.biglexj.lunafetch.domain.DownloadResult
import com.biglexj.lunafetch.domain.CollectionEntry
import com.biglexj.lunafetch.domain.VideoInfo
import com.biglexj.lunafetch.domain.YtdlpProtocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.contentOrNull
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference

class DesktopDownloadEngine(
    private val executable: String = "yt-dlp",
) : DownloadEngine {
    private val activeProcess = AtomicReference<Process?>(null)
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun analyze(url: String): VideoInfo = analyzeWithCookieFile(url, null)

    suspend fun analyzeWithCookieFile(url: String, customCookieFile: String? = null): VideoInfo = withContext(Dispatchers.IO) {
        val analyzeOptions = YtdlpProtocol.buildAnalyzeArguments(url)

        val (exitCode, outputs) = executeWithCookieFallback(analyzeOptions, url, customCookieFile = customCookieFile)
        val (stdout, stderr) = outputs
        if (exitCode != 0) {
            throw DownloadException(stderr.trim().ifBlank { stdout.trim() }.ifBlank { "yt-dlp terminó con código $exitCode." })
        }
        parseVideoInfo(url, stdout)
    }

    override suspend fun download(
        request: DownloadRequest,
        onProgress: (DownloadProgress) -> Unit,
        onLog: (String) -> Unit,
    ): DownloadResult = downloadWithCookieFile(request, null, onProgress, onLog)

    suspend fun downloadWithCookieFile(
        request: DownloadRequest,
        customCookieFile: String? = null,
        onProgress: (DownloadProgress) -> Unit,
        onLog: (String) -> Unit,
    ): DownloadResult = withContext(Dispatchers.IO) {
        val destination = File(request.destination)
        if (!destination.exists() && !destination.mkdirs()) {
            throw DownloadException("No se pudo crear la carpeta de destino.")
        }
        if (!destination.isDirectory) throw DownloadException("El destino seleccionado no es una carpeta.")

        val outputTemplate = File(
            destination,
            if (request.downloadCollection) "%(playlist_index)03d - %(title)s.%(ext)s" else "%(title)s.%(ext)s",
        ).absolutePath
        val downloadOptions = YtdlpProtocol.buildDownloadArguments(request, outputTemplate)
        val finalPaths = mutableListOf<String>()

        onProgress(DownloadProgress(0.0, phase = DownloadPhase.Preparing))

        val (exitCode, outputs) = executeWithCookieFallback(downloadOptions, request.url, customCookieFile = customCookieFile) { line ->
            onLog(line)
            YtdlpProtocol.parseProgress(line)?.let(onProgress)
            YtdlpProtocol.outputPath(line)?.let(finalPaths::add)
        }

        if (exitCode != 0) {
            val combinedError = outputs.first + "\n" + outputs.second
            throw DownloadException(friendlyError(exitCode, combinedError))
        }

        val completedPaths = finalPaths.distinct().ifEmpty {
            destination.walkTopDown()
                .filter(File::isFile)
                .filterNot { it.extension.equals("part", true) }
                .map(File::getAbsolutePath)
                .toList()
        }

        DownloadResult(
            outputPaths = completedPaths,
            openPath = completedPaths.firstOrNull() ?: destination.absolutePath,
        )
    }

    override fun cancel() {
        activeProcess.getAndSet(null)?.let { process ->
            runCatching { process.destroyForcibly() }
        }
    }

    private fun friendlyError(exitCode: Int, combinedError: String): String = when {
        combinedError.contains("403", ignoreCase = true) || combinedError.contains("forbidden", ignoreCase = true) ->
            "La plataforma rechazó la descarga (HTTP 403). Suele ser detección anti-bot del sitio. Intenta el canal del motor Nocturno o deja que se extraigan las cookies del navegador, y vuelve a intentar."
        combinedError.contains("sign in to confirm", ignoreCase = true) ->
            "El sitio pide confirmar el inicio de sesión (anti-bot). Deja que se extraigan las cookies del navegador o actualiza el motor a Nocturno."
        combinedError.contains("unable to download video data", ignoreCase = true) ->
            "No se pudo descargar el video (los datos fueron rechazados por el servidor). Intenta cambiar el canal del motor o revisa el registro técnico para el detalle."
        else -> "yt-dlp terminó con código $exitCode. Revisa el registro técnico."
    }

    private fun executeWithCookieFallback(
        options: List<String>,
        url: String,
        customCookieFile: String? = null,
        onLine: ((String) -> Unit)? = null,
    ): Pair<Int, Pair<String, String>> {
        val sessionCookieFile = customCookieFile ?: File(System.getProperty("java.io.tmpdir"), "luna_session_cookies.txt").let {
            if (it.exists() && it.length() > 0) it.absolutePath else null
        }

        fun runWith(cookieBrowser: String? = null, cookieFilePath: String? = null): Pair<Int, Pair<String, String>> {
            val command = mutableListOf(executable).apply {
                addAll(options)
                if (!cookieFilePath.isNullOrBlank()) {
                    addAll(listOf("--cookies", cookieFilePath))
                } else if (!cookieBrowser.isNullOrBlank()) {
                    addAll(listOf("--cookies-from-browser", cookieBrowser))
                }
                add("--")
                add(url)
            }
            val proc = startProcess(command)
            activeProcess.set(proc)
            try {
                val stdoutSb = StringBuilder()
                val stderrSb = StringBuilder()
                val t1 = Thread {
                    proc.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach { line ->
                            stdoutSb.appendLine(line)
                            onLine?.invoke(line)
                        }
                    }
                }
                val t2 = Thread {
                    proc.errorStream.bufferedReader().useLines { lines ->
                        lines.forEach { line ->
                            stderrSb.appendLine(line)
                            onLine?.invoke(line)
                        }
                    }
                }
                t1.start()
                t2.start()
                val code = proc.waitFor()
                t1.join()
                t2.join()
                return code to (stdoutSb.toString() to stderrSb.toString())
            } finally {
                activeProcess.compareAndSet(proc, null)
            }
        }

        // 1. Try with exported session cookies file if available
        if (!sessionCookieFile.isNullOrBlank()) {
            val res = runWith(cookieFilePath = sessionCookieFile)
            if (res.first == 0) return res
        }

        // 2. Try default unauthenticated call
        var res = runWith(cookieBrowser = null, cookieFilePath = null)
        val (code, outputs) = res
        val (stdout, stderr) = outputs
        val combinedError = "$stdout\n$stderr"

        // 3. Fallback to browser cookies if bot detection error occurs
        if (code != 0 && (combinedError.contains("Sign in to confirm", ignoreCase = true) ||
                          combinedError.contains("cookies", ignoreCase = true) ||
                          combinedError.contains("bot", ignoreCase = true))) {
            val browsers = listOf("edge", "chrome", "firefox", "brave", "opera")
            for (browser in browsers) {
                val fallbackRes = runWith(cookieBrowser = browser)
                if (fallbackRes.first == 0) {
                    return fallbackRes
                }
            }
        }
        return res
    }

    private fun startProcess(command: List<String>): Process {
        val pb = ProcessBuilder(command)
        pb.environment().putIfAbsent("PATH", System.getenv("PATH"))
        return try {
            pb.start()
        } catch (e: IOException) {
            throw DownloadException("No se pudo ejecutar yt-dlp. Asegúrate de tenerlo instalado y disponible en el PATH: ${e.message}")
        }
    }

    private fun parseVideoInfo(url: String, jsonOutput: String): VideoInfo {
        if (jsonOutput.isBlank()) throw DownloadException("yt-dlp no devolvió información en formato JSON.")
        val root = try {
            json.parseToJsonElement(jsonOutput).jsonObject
        } catch (e: Exception) {
            throw DownloadException("No se pudo procesar la respuesta JSON de yt-dlp.")
        }

        val title = root["title"]?.jsonPrimitive?.contentOrNull ?: "Video sin título"
        val uploader = root["uploader"]?.jsonPrimitive?.contentOrNull
            ?: root["channel"]?.jsonPrimitive?.contentOrNull
            ?: "Autor desconocido"
        val duration = root["duration"]?.jsonPrimitive?.doubleOrNull ?: 0.0
        var thumbnail = root["thumbnail"]?.jsonPrimitive?.contentOrNull
            ?: root["thumbnails"]?.jsonArray?.lastOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull
            ?: ""

        val entries = root["entries"]?.jsonArray?.mapIndexedNotNull { index, item ->
            val obj = item.jsonObject
            val entryTitle = obj["title"]?.jsonPrimitive?.contentOrNull ?: return@mapIndexedNotNull null
            val entryUploader = obj["uploader"]?.jsonPrimitive?.contentOrNull
                ?: obj["channel"]?.jsonPrimitive?.contentOrNull
                ?: ""
            val entryDuration = obj["duration"]?.jsonPrimitive?.doubleOrNull ?: 0.0
            CollectionEntry(
                index = index + 1,
                title = entryTitle,
                uploader = entryUploader,
                durationSeconds = entryDuration,
            )
        }.orEmpty()

        val isPlaylist = entries.size > 1 || root["_type"]?.jsonPrimitive?.contentOrNull == "playlist"
        val maxHeight = extractMaxHeight(root)

        // Fallback to video ID thumbnail if empty
        if (thumbnail.isBlank()) {
            val videoIdMatch = Regex("""(?:v=|/v/|youtu\.be/|/embed/)([a-zA-Z0-9_-]{11})""").find(url)
            val videoId = videoIdMatch?.groupValues?.get(1)
            if (!videoId.isNullOrBlank()) {
                thumbnail = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
            }
        }

        return VideoInfo(
            url = url,
            title = title,
            uploader = uploader,
            durationSeconds = duration,
            thumbnailUrl = thumbnail,
            maxHeight = maxHeight,
            collectionTitle = if (isPlaylist) title else null,
            collectionCount = if (isPlaylist) entries.size else 0,
            collectionEntries = entries,
        )
    }

    private fun extractMaxHeight(root: kotlinx.serialization.json.JsonObject): Int {
        val formats = root["formats"]?.jsonArray ?: return 1080
        val maxH = formats.mapNotNull {
            it.jsonObject["height"]?.jsonPrimitive?.intOrNull
        }.maxOrNull() ?: 1080
        return if (maxH > 0) maxH else 1080
    }
}
