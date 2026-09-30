package com.biglexj.lunafetch.domain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object SunoExtractor {

    private val UUID_REGEX = Regex("""[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}""", RegexOption.IGNORE_CASE)
    private val SUNO_HOSTS = listOf("suno.com", "www.suno.com", "suno.ai", "www.suno.ai", "usesuno.com")

    /**
     * Determina si la URL proporcionada pertenece al ecosistema Suno AI.
     */
    fun isSunoUrl(url: String): Boolean {
        val clean = url.trim().lowercase()
        return SUNO_HOSTS.any { host ->
            clean.contains("://$host/") || clean.contains(".$host/") || clean.startsWith("https://$host")
        }
    }

    /**
     * Extrae el UUID canónico de una URL de Suno.
     */
    fun extractId(url: String): String? {
        val clean = url.trim()
        val match = UUID_REGEX.find(clean)
        return match?.value?.lowercase()
    }

    /**
     * Resuelve enlaces cortos de Suno (ej: suno.com/s/xxxx) siguiendo redirecciones HTTP.
     */
    suspend fun resolveCanonicalUrl(rawUrl: String): String = withContext(Dispatchers.IO) {
        val clean = rawUrl.trim()
        val directId = extractId(clean)
        if (directId != null && !clean.contains("/s/")) {
            return@withContext "https://suno.com/song/$directId"
        }

        runCatching {
            val connection = (URL(clean).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
            }
            val status = connection.responseCode
            if (status in 300..399) {
                val location = connection.getHeaderField("Location")
                if (!location.isNullOrBlank()) {
                    val target = if (location.startsWith("http")) location else "https://suno.com$location"
                    val id = extractId(target)
                    if (id != null) return@withContext "https://suno.com/song/$id"
                }
            }
        }
        clean
    }

    data class SunoSongMetadata(
        val id: String,
        val title: String,
        val creatorHandle: String,
        val displayName: String,
        val durationSeconds: Double,
        val lyricsPrompt: String,
        val tags: String,
        val coverUrl: String,
        val videoUrl: String,
        val audioUrl: String = "",
    )

    /**
     * Analiza una pista de Suno y obtiene todos sus metadatos (título, autor, portada, letra, audio y video).
     */
    suspend fun analyze(rawUrl: String): VideoInfo = withContext(Dispatchers.IO) {
        val canonicalUrl = resolveCanonicalUrl(rawUrl)
        val id = extractId(canonicalUrl) ?: throw DownloadException("No se pudo identificar el ID único de la canción de Suno.")

        val metadata = fetchMetadata(id)

        val uploaderText = when {
            metadata.displayName.isNotBlank() && metadata.creatorHandle.isNotBlank() ->
                "${metadata.displayName} (@${metadata.creatorHandle})"
            metadata.creatorHandle.isNotBlank() -> "@${metadata.creatorHandle}"
            metadata.displayName.isNotBlank() -> metadata.displayName
            else -> "Suno AI"
        }

        VideoInfo(
            url = "https://suno.com/song/$id",
            title = metadata.title.ifBlank { "Suno Track ${id.take(8)}" },
            uploader = uploaderText,
            durationSeconds = metadata.durationSeconds,
            thumbnailUrl = metadata.coverUrl,
            maxHeight = 1080,
        )
    }

    /**
     * Descarga el video o audio de Suno AI con reporte de progreso en vivo.
     * Si se solicita audio, prioriza el stream de audio nativo (.m4a) evitando descargar streams de video pesados.
     */
    suspend fun download(
        request: DownloadRequest,
        onProgress: (DownloadProgress) -> Unit,
        onLog: (String) -> Unit,
    ): DownloadResult = withContext(Dispatchers.IO) {
        val canonicalUrl = resolveCanonicalUrl(request.url)
        val id = extractId(canonicalUrl) ?: throw DownloadException("No se encontró el ID de la canción de Suno.")
        val meta = fetchMetadata(id)

        val destinationDir = File(request.destination)
        if (!destinationDir.exists() && !destinationDir.mkdirs()) {
            throw DownloadException("No se pudo crear la carpeta de destino.")
        }

        val sanitizedTitle = sanitizeFilename("${meta.title} - ${meta.displayName.ifBlank { meta.creatorHandle.ifBlank { "Suno AI" } }}")
        val isAudio = request.format.isAudio

        onProgress(DownloadProgress(0.0, phase = DownloadPhase.Preparing))
        onLog("Iniciando extracción de Suno AI para: ${meta.title}")

        // El stream MP4 canónico de Suno (cdn1.suno.ai/$id.mp4) contiene el audio AAC a 48kHz sin cifrar.
        // Los streams .m4a de CloudFront usan cifrado propietario (encoding: 1.0.0), por lo que siempre usamos el stream canónico de cdn1.
        val sourceStreamUrl = "https://cdn1.suno.ai/$id.mp4"

        val finalExtension = request.format.extension
        val finalFile = File(destinationDir, "$sanitizedTitle.$finalExtension")
        val tempSourceFile = if (isAudio) {
            File(destinationDir, "${sanitizedTitle}_suno_source.mp4")
        } else {
            finalFile
        }

        val logDescription = if (isAudio) "stream fuente canónico (.mp4)" else "stream de video (.mp4)"
        onLog("Descargando $logDescription: $sourceStreamUrl")

        downloadHttpStream(sourceStreamUrl, tempSourceFile) { pct, speed, size ->
            onProgress(DownloadProgress(pct, speed = speed, size = size, phase = DownloadPhase.Downloading))
        }

        // Si se pidió audio, procesar / transcodificar con FFmpeg si está disponible o preservar stream nativo
        var actualOutputFile = finalFile
        if (isAudio) {
            onProgress(DownloadProgress(100.0, phase = DownloadPhase.Processing))
            onLog("Procesando audio en formato ${request.format.displayName}…")
            val ffmpegSuccess = convertAudioWithFfmpeg(tempSourceFile, finalFile, request.format, meta, onLog)
            if (ffmpegSuccess && finalFile.exists() && finalFile.length() > 0) {
                actualOutputFile = finalFile
                runCatching { tempSourceFile.delete() }
            } else {
                // Si FFmpeg no está disponible o la conversión falló, preservar el MP4 original descargado
                val preservedFile = File(destinationDir, "$sanitizedTitle.mp4")
                if (tempSourceFile.exists()) {
                    tempSourceFile.renameTo(preservedFile)
                }
                actualOutputFile = preservedFile
                onLog("⚠️ Transcodificación no completada. Stream original preservado en: ${preservedFile.name}")
            }
        }

        // Guardar la letra de la canción si existe en un archivo .txt acompañante
        if (meta.lyricsPrompt.isNotBlank() && !meta.lyricsPrompt.equals("Prompt", ignoreCase = true)) {
            runCatching {
                val lyricsFile = File(destinationDir, "$sanitizedTitle.lyrics.txt")
                lyricsFile.writeText(buildString {
                    appendLine(meta.title)
                    appendLine("Autor: ${meta.displayName} (@${meta.creatorHandle})")
                    if (meta.tags.isNotBlank()) {
                        appendLine("Estilo/Tags: ${meta.tags}")
                    }
                    appendLine("URL: https://suno.com/song/$id")
                    appendLine()
                    appendLine("--- LETRA DE LA CANCIÓN ---")
                    appendLine(meta.lyricsPrompt)
                })
                onLog("Letra lírica guardada en: ${lyricsFile.name}")
            }
        }

        onProgress(DownloadProgress(100.0, phase = DownloadPhase.Completed))
        onLog("✅ Descarga de Suno completada con éxito.")

        val outputList = mutableListOf(actualOutputFile.absolutePath)
        val lyricsCandidate = File(destinationDir, "$sanitizedTitle.lyrics.txt")
        if (lyricsCandidate.exists()) {
            outputList.add(lyricsCandidate.absolutePath)
        }

        DownloadResult(
            outputPaths = outputList,
            openPath = actualOutputFile.absolutePath,
        )
    }

    private fun fetchMetadata(id: String): SunoSongMetadata {
        val targetUrl = "https://suno.com/song/$id"
        val connection = (URL(targetUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12_000
            readTimeout = 12_000
            setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36")
            setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            setRequestProperty("Accept-Language", "es-ES,es;q=0.9,en;q=0.8")
            setRequestProperty("Referer", "https://suno.com/")
        }

        val html = try {
            connection.inputStream.bufferedReader().use { it.readText() }
        } catch (t: Throwable) {
            // Fallback con CDN predeterminado
            return SunoSongMetadata(
                id = id,
                title = "Canción Suno $id",
                creatorHandle = "",
                displayName = "Suno AI",
                durationSeconds = 120.0,
                lyricsPrompt = "",
                tags = "",
                coverUrl = "https://cdn2.suno.ai/image_large_$id.jpeg",
                videoUrl = "https://cdn1.suno.ai/$id.mp4",
                audioUrl = "",
            )
        }

        // Localizar el bloque central del "clip" para aislar los metadatos específicos de la canción
        val clipIdx = html.indexOf("\"clip\"").takeIf { it >= 0 }
            ?: html.indexOf("""\"clip\"""").takeIf { it >= 0 }
            ?: 0

        val titleMatch = Regex("""\\?"title\\?"\s*:\s*\\?"([^"\\\r\n]{1,200})\\?"""").find(html, startIndex = clipIdx)
        val title = titleMatch?.groupValues?.get(1)?.unescapeJson()?.ifBlank { null } ?: "Canción Suno ${id.take(8)}"

        val authorMatch = Regex("""\\?"user_display_name\\?"\s*:\s*\\?"([^"\\\r\n]{1,200})\\?"""").find(html, startIndex = clipIdx)
            ?: Regex("""\\?"display_name\\?"\s*:\s*\\?"([^"\\\r\n]{1,200})\\?"""").find(html, startIndex = clipIdx)
        val displayName = authorMatch?.groupValues?.get(1)?.unescapeJson() ?: "Suno AI"

        val handleMatch = Regex("""\\?"user_handle\\?"\s*:\s*\\?"([^"\\\r\n]{1,200})\\?"""").find(html, startIndex = clipIdx)
            ?: Regex("""\\?"handle\\?"\s*:\s*\\?"([^"\\\r\n]{1,200})\\?"""").find(html, startIndex = clipIdx)
        val handle = handleMatch?.groupValues?.get(1)?.unescapeJson() ?: ""

        val durationMatch = Regex("""\\?"duration\\?"\s*:\s*([0-9.]+)""").find(html, startIndex = clipIdx)
        val duration = durationMatch?.groupValues?.get(1)?.toDoubleOrNull() ?: 120.0

        val tagsMatch = Regex("""\\?"tags\\?"\s*:\s*\\?"([^"\\\r\n]{1,300})\\?"""").find(html, startIndex = clipIdx)
        val tags = tagsMatch?.groupValues?.get(1)?.unescapeJson() ?: ""

        val videoUrl = "https://cdn1.suno.ai/$id.mp4"
        val coverUrl = Regex("""https://[a-zA-Z0-9.\-_/]+/[a-zA-Z0-9.\-_/]+image[a-zA-Z0-9._\-]*\.jpeg""").find(html)?.value
            ?: "https://cdn2.suno.ai/image_large_$id.jpeg"

        // Letra lírica (prompt de texto o chunk de hidratación Next.js)
        var lyricsPrompt = ""
        val promptMatch = Regex("""\\?"prompt\\?"\s*:\s*\\?"([^"\\\r\n]+)\\?"""").find(html, startIndex = clipIdx)
        val promptRaw = promptMatch?.groupValues?.get(1)?.trim().orEmpty()

        if (promptRaw.startsWith("$")) {
            val chunkId = promptRaw.removePrefix("$")
            val chunkPrefix = "$chunkId:T"
            val chunkIndex = html.indexOf(chunkPrefix)
            if (chunkIndex >= 0) {
                val commaIndex = html.indexOf(',', startIndex = chunkIndex)
                if (commaIndex > chunkIndex && commaIndex - chunkIndex < 25) {
                    val contentStart = commaIndex + 1
                    val nextChunkRegex = Regex("""\n\d+:|\d+:\[|\d+:\{|\d+:T[0-9a-fA-F]+,""")
                    val endMatch = nextChunkRegex.find(html, startIndex = contentStart)
                    val contentEnd = endMatch?.range?.first ?: html.length
                    lyricsPrompt = html.substring(contentStart, contentEnd).unescapeJson().trim()
                }
            }
        } else if (promptRaw.isNotBlank() && !promptRaw.equals("Prompt", ignoreCase = true)) {
            lyricsPrompt = promptRaw.unescapeJson().trim()
        }

        // Fallback: si aún está vacía o es literal "Prompt", buscar chunks con estructura lírica
        if (lyricsPrompt.isBlank() || lyricsPrompt.equals("Prompt", ignoreCase = true)) {
            val lyricChunkRegex = Regex("""\d+:T[0-9a-fA-F]+,(\[(?:Intro|Verse|Chorus|Pre-Chorus|Bridge|Outro|Hook|Refrain|Female|Male)[^\r\n]*\][\s\S]+?)(?=\n\d+:|\d+:\[|\d+:\{|$)""")
            val fallbackMatch = lyricChunkRegex.find(html)
            if (fallbackMatch != null) {
                lyricsPrompt = fallbackMatch.groupValues[1].unescapeJson().trim()
            }
        }

        return SunoSongMetadata(
            id = id,
            title = title,
            creatorHandle = handle,
            displayName = displayName,
            durationSeconds = duration,
            lyricsPrompt = lyricsPrompt,
            tags = tags,
            coverUrl = coverUrl,
            videoUrl = videoUrl,
            audioUrl = "",
        )
    }

    private fun downloadHttpStream(
        streamUrl: String,
        targetFile: File,
        onProgress: (percentage: Double, speed: String, size: String) -> Unit,
    ) {
        var currentUrl = streamUrl
        var redirectCount = 0
        var connection: HttpURLConnection

        while (true) {
            val url = URL(currentUrl)
            connection = (url.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 20_000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36")
                setRequestProperty("Accept", "*/*")
                setRequestProperty("Referer", "https://suno.com/")
                setRequestProperty("Origin", "https://suno.com")
            }

            val status = connection.responseCode
            if (status in 300..399 && redirectCount < 5) {
                val loc = connection.getHeaderField("Location") ?: break
                currentUrl = if (loc.startsWith("http")) loc else URL(url, loc).toString()
                redirectCount++
                connection.disconnect()
                continue
            }
            break
        }

        if (connection.responseCode !in 200..299) {
            throw DownloadException("El servidor de Suno respondió con código HTTP ${connection.responseCode}")
        }

        val totalBytes = connection.contentLengthLong
        var downloadedBytes = 0L
        val startTime = System.currentTimeMillis()

        connection.inputStream.use { input ->
            FileOutputStream(targetFile).use { output ->
                val buffer = ByteArray(32768)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead

                    val elapsedSeconds = ((System.currentTimeMillis() - startTime) / 1000.0).coerceAtLeast(0.01)
                    val bytesPerSecond = downloadedBytes / elapsedSeconds
                    val speed = formatSpeed(bytesPerSecond)
                    val size = "${(downloadedBytes / (1024 * 1024.0)).format(1)} MB"
                    val pct = if (totalBytes > 0) (downloadedBytes.toDouble() / totalBytes * 100.0).coerceIn(0.0, 100.0) else 50.0

                    onProgress(pct, speed, size)
                }
            }
        }
    }

    private fun convertAudioWithFfmpeg(
        inputFile: File,
        outputFile: File,
        format: MediaFormat,
        metadata: SunoSongMetadata,
        onLog: (String) -> Unit,
    ): Boolean {
        return runCatching {
            val audioCodec = when (format) {
                MediaFormat.Mp3 -> listOf("-c:a", "libmp3lame", "-q:a", "0")
                MediaFormat.M4a -> listOf("-c:a", "copy")
                MediaFormat.Flac -> listOf("-c:a", "flac")
                MediaFormat.Wav -> listOf("-c:a", "pcm_s16le")
                else -> listOf("-c:a", "copy")
            }

            val command = mutableListOf("ffmpeg", "-y", "-i", inputFile.absolutePath, "-vn")
            command.addAll(audioCodec)
            command.addAll(
                listOf(
                    "-metadata", "title=${metadata.title}",
                    "-metadata", "artist=${metadata.displayName.ifBlank { metadata.creatorHandle }}",
                    "-metadata", "album=Suno AI",
                    "-metadata", "genre=${metadata.tags.take(64)}",
                    "-metadata", "comment=Generado en Suno AI · Descargado con Luna Fetch",
                    outputFile.absolutePath,
                ),
            )

            val process = ProcessBuilder(command).start()
            val exitCode = process.waitFor()
            if (exitCode == 0 && outputFile.exists() && outputFile.length() > 0) {
                onLog("Audio transcodificado con metadatos ID3 exitosamente.")
                true
            } else {
                false
            }
        }.getOrDefault(false)
    }

    private fun sanitizeFilename(name: String): String {
        return name.replace(Regex("""[\\/:*?"<>|]"""), "_").trim().take(120)
    }

    private fun formatSpeed(bytesPerSecond: Double): String {
        val mbps = bytesPerSecond / (1024.0 * 1024.0)
        return if (mbps >= 1.0) "${mbps.format(1)} MB/s" else "${(bytesPerSecond / 1024.0).format(0)} KB/s"
    }

    private fun Double.format(digits: Int): String {
        return runCatching {
            String.format(java.util.Locale.US, "%.${digits}f", this)
        }.getOrDefault(this.toString())
    }

    private fun String.unescapeJson(): String {
        return this
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
    }
}
