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
     * Ventana de análisis alrededor del objeto de la canción dentro del
     * payload de la página. Antes del ancla se lee el título (Suno lo coloca
     * justo delante del identificador) y después el resto de campos.
     */
    private const val CLIP_WINDOW_BEFORE = 4000
    private const val CLIP_WINDOW_AFTER = 20000

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
            val query = clean.substringAfter('?', "")
            return@withContext if (query.isNotBlank()) "https://suno.com/song/$directId?$query" else "https://suno.com/song/$directId"
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
                    if (location == "/" || location.equals("https://suno.com/", ignoreCase = true) || location.equals("https://suno.com", ignoreCase = true)) {
                        throw DownloadException("El enlace compartido de Suno ha caducado o no existe (Suno redirigió a la página principal).")
                    }
                    val target = if (location.startsWith("http")) location else "https://suno.com$location"
                    val id = extractId(target)
                    if (id != null) {
                        return@withContext if (target.contains("?")) target else "https://suno.com/song/$id"
                    }
                }
            }
        }.getOrThrow()
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
        /**
         * Indica que Suno generó la canción como instrumental: en ese caso la
         * letra viene vacía por diseño y no hay nada que guardar.
         */
        val isInstrumental: Boolean = false,
    )

    /**
     * Analiza una pista de Suno y obtiene todos sus metadatos (título, autor, portada, letra, audio y video).
     *
     * @param cookieFile Fichero de cookies de sesión (formato Netscape). Con él,
     * la ficha llega también en canciones privadas: sin sesión, Suno solo
     * entrega el esqueleto de la página y el título sería genérico.
     */
    suspend fun analyze(rawUrl: String, cookieFile: String? = null): VideoInfo = withContext(Dispatchers.IO) {
        val canonicalUrl = resolveCanonicalUrl(rawUrl)
        val id = extractId(canonicalUrl) ?: throw DownloadException("No se pudo identificar el ID único de la canción de Suno.")

        val metadata = fetchMetadata(id, cookieFile, targetPageUrl = canonicalUrl.ifBlank { rawUrl })

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
        val meta = fetchMetadata(id, request.cookieFile, targetPageUrl = canonicalUrl.ifBlank { request.url })

        val destinationDir = File(request.destination)
        if (!destinationDir.exists() && !destinationDir.mkdirs()) {
            throw DownloadException("No se pudo crear la carpeta de destino.")
        }

        val sanitizedTitle = sanitizeFilename("${meta.title} - ${meta.displayName.ifBlank { meta.creatorHandle.ifBlank { "Suno AI" } }}")
        val isAudio = request.format.isAudio

        onProgress(DownloadProgress(0.0, phase = DownloadPhase.Preparing))
        onLog("Iniciando extracción de Suno AI para: ${meta.title}")

        val candidates = if (isAudio) {
            val list = mutableListOf<StreamCandidate>()
            if (meta.audioUrl.isNotBlank() && !meta.audioUrl.contains("/forbidden", ignoreCase = true)) {
                list.add(StreamCandidate(meta.audioUrl, "audio directo de Suno"))
            }
            // Para canciones privadas/compartidas, el stream seguro Mango de CloudFront
            // es el único con acceso legítimo sin depender de permisos de cdn1/cdn2.
            list.add(StreamCandidate(SunoMangoDecryptor.streamUrlForId(id), "stream seguro Suno (Mango Opus M4A)"))
            list.add(StreamCandidate("https://cdn1.suno.ai/$id.mp3", "stream MP3 (cdn1)"))
            list.add(StreamCandidate("https://cdn2.suno.ai/$id.mp3", "stream MP3 (cdn2)"))
            list.add(StreamCandidate("https://cdn1.suno.ai/$id.m4a", "stream M4A (cdn1)"))
            list.add(StreamCandidate("https://cdn2.suno.ai/$id.m4a", "stream M4A (cdn2)"))
            if (meta.videoUrl.isNotBlank()) {
                list.add(StreamCandidate(meta.videoUrl, "stream de video de Suno"))
            }
            list.add(StreamCandidate("https://cdn1.suno.ai/$id.mp4", "stream MP4 canónico (cdn1)"))
            list.add(StreamCandidate("https://cdn2.suno.ai/$id.mp4", "stream MP4 canónico (cdn2)"))
            list.distinctBy { it.url }
        } else {
            val list = mutableListOf<StreamCandidate>()
            if (meta.videoUrl.isNotBlank()) {
                list.add(StreamCandidate(meta.videoUrl, "stream de video de Suno"))
            }
            list.add(StreamCandidate("https://cdn1.suno.ai/$id.mp4", "stream de video MP4 (cdn1)"))
            list.add(StreamCandidate("https://cdn2.suno.ai/$id.mp4", "stream de video MP4 (cdn2)"))
            list.distinctBy { it.url }
        }

        val finalExtension = request.format.extension
        val finalFile = uniqueOutputFile(destinationDir, sanitizedTitle, finalExtension)
        val sanitizedBaseName = finalFile.nameWithoutExtension
        val tempSourceFile = if (isAudio) {
            File(destinationDir, "${sanitizedBaseName}_suno_source_${System.currentTimeMillis()}.tmp")
        } else {
            finalFile
        }

        val chosenStream = downloadFirstAvailableStream(candidates, tempSourceFile, request.cookieFile, onLog) { pct, speed, size ->
            onProgress(DownloadProgress(pct, speed = speed, size = size, phase = DownloadPhase.Downloading))
        }

        // Si se pidió audio, procesar / transcodificar con FFmpeg si está disponible o preservar stream nativo
        var actualOutputFile = finalFile
        if (isAudio) {
            onProgress(DownloadProgress(100.0, phase = DownloadPhase.Processing))
            onLog("Procesando audio en formato ${request.format.displayName}…")
            // La portada se incrusta en el archivo de audio para que el
            // reproductor muestre la carátula real de la canción.
            val coverFile = downloadCover(meta.coverUrl, destinationDir, onLog)
            val ffmpegSuccess = convertAudioWithFfmpeg(tempSourceFile, finalFile, request.format, meta, coverFile, onLog)
            deleteQuietly(coverFile)
            if (ffmpegSuccess && finalFile.exists() && finalFile.length() > 0) {
                actualOutputFile = finalFile
                deleteQuietly(tempSourceFile)
            } else {
                // Si FFmpeg no está disponible o la conversión falló, preservar el archivo original descargado
                val rawExtension = chosenStream.url.substringBefore('?').substringAfterLast('.', "mp3")
                val preservedFile = uniqueOutputFile(destinationDir, sanitizedBaseName, rawExtension)
                if (tempSourceFile.exists()) {
                    if (!tempSourceFile.renameTo(preservedFile)) {
                        runCatching {
                            tempSourceFile.copyTo(preservedFile, overwrite = true)
                            deleteQuietly(tempSourceFile)
                        }
                    }
                }
                actualOutputFile = preservedFile
                onLog("⚠️ Transcodificación no completada. Stream original preservado en: ${preservedFile.name}")
            }
        }

        // Guardar la letra de la canción si el usuario lo activó y la canción NO es instrumental
        val isInstrumentalPrompt = meta.isInstrumental ||
            meta.lyricsPrompt.trim().equals("[Instrumental]", ignoreCase = true) ||
            meta.lyricsPrompt.trim().equals("Instrumental", ignoreCase = true) ||
            meta.lyricsPrompt.trim().startsWith("[Instrumental", ignoreCase = true)

        if (request.downloadLyrics && !isInstrumentalPrompt && meta.lyricsPrompt.isNotBlank() && !meta.lyricsPrompt.equals("Prompt", ignoreCase = true)) {
            runCatching {
                val lyricsFile = File(destinationDir, "$sanitizedBaseName.lyrics.txt")
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
        } else if (isInstrumentalPrompt) {
            onLog("🎼 La canción es instrumental: no se genera archivo de letra.")
        }

        onProgress(DownloadProgress(100.0, phase = DownloadPhase.Completed))
        onLog("✅ Descarga de Suno completada con éxito.")

        val outputList = mutableListOf(actualOutputFile.absolutePath)
        val lyricsCandidate = File(destinationDir, "$sanitizedBaseName.lyrics.txt")
        if (lyricsCandidate.exists()) {
            outputList.add(lyricsCandidate.absolutePath)
        }

        DownloadResult(
            outputPaths = outputList,
            openPath = actualOutputFile.absolutePath,
        )
    }

    private fun fetchMetadata(
        id: String,
        cookieFile: String? = null,
        targetPageUrl: String? = null,
    ): SunoSongMetadata {
        var currentUrl = when {
            !targetPageUrl.isNullOrBlank() && targetPageUrl.startsWith("http") -> targetPageUrl
            else -> "https://suno.com/song/$id"
        }
        var redirectCount = 0
        var connection: HttpURLConnection? = null

        val html = try {
            while (true) {
                val url = URL(currentUrl)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    requestMethod = "GET"
                    connectTimeout = 12_000
                    readTimeout = 12_000
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36")
                    setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    setRequestProperty("Accept-Language", "es-ES,es;q=0.9,en;q=0.8")
                    setRequestProperty("Referer", "https://suno.com/")
                    sessionCookieHeader(cookieFile)?.let { setRequestProperty("Cookie", it) }
                }
                connection = conn

                val status = conn.responseCode
                if (status in 300..399 && redirectCount < 5) {
                    val loc = conn.getHeaderField("Location") ?: break
                    currentUrl = if (loc.startsWith("http")) loc else URL(url, loc).toString()
                    redirectCount++
                    conn.disconnect()
                    continue
                }
                break
            }
            connection!!.inputStream.bufferedReader().use { it.readText() }
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
                coverUrl = defaultCoverUrl(id),
                videoUrl = "https://cdn1.suno.ai/$id.mp4",
                audioUrl = "",
            )
        }

        return parseMetadata(html, id)
    }

    /**
     * Extrae los metadatos de la canción a partir del HTML servido por Suno.
     *
     * Suno entrega la ficha dentro del payload de React Server Components: las
     * cadenas viajan escapadas (`\"title\":\"…\"`) y el objeto de la canción
     * viene precedido por su identificador. El análisis se ancla en ese id
     * porque la interfaz comparte las mismas claves JSON (`title`, `tags`,
     * `prompt`…) en sus menús y textos: sin el ancla, el título recuperado era
     * el de un botón de la app y no el de la canción.
     */
    internal fun parseMetadata(html: String, id: String): SunoSongMetadata {
        val clipIndex = findClipIndex(html, id)
        val windowStart = if (clipIndex >= 0) maxOf(0, clipIndex - CLIP_WINDOW_BEFORE) else 0
        val windowEnd = if (clipIndex >= 0) minOf(html.length, clipIndex + CLIP_WINDOW_AFTER) else 0

        // Solo se extrae el título del payload si se localizó el clip; de lo contrario
        // se evita escanear la página para no capturar textos promocionales como
        // «Consigue la experiencia completa de la app» de la portada de Suno.
        val payloadTitle = if (clipIndex >= 0) extractTitle(html, windowStart, windowEnd, clipIndex) else null
        val author = if (clipIndex >= 0) extractAuthor(html, windowStart, windowEnd, clipIndex) else null
        val displayName = author?.first
        val handle = author?.second
        val duration = if (clipIndex >= 0) extractNumber(html, "duration", windowStart, windowEnd) else null
        val styleTags = if (clipIndex >= 0) extractField(html, "tags", windowStart, windowEnd, clipIndex, maxLength = 6000) else null
        val lyrics = if (clipIndex >= 0) extractLyrics(html, windowStart, windowEnd, clipIndex) else ""
        // La cabecera HTML (<title> y la meta descripción) sirve de red de
        // seguridad cuando el payload interno cambia de forma.
        val head = parseHeadMetadata(html)

        val cover = if (clipIndex >= 0) {
            extractUrlField(html, "image_large_url", windowStart, windowEnd)
                ?: extractUrlField(html, "image_url", windowStart, windowEnd)
                ?: head.coverUrl
                ?: defaultCoverUrl(id)
        } else {
            head.coverUrl ?: defaultCoverUrl(id)
        }

        val isInstrumental = if (clipIndex >= 0) {
            Regex("""\\?"make_instrumental\\?"\s*:\s*true""")
                .containsMatchIn(html.substring(windowStart, windowEnd))
        } else false

        val audioUrl = extractUrlField(html, "audio_url", windowStart, windowEnd)
            ?.takeIf { !it.contains("/forbidden", ignoreCase = true) }
            .orEmpty()
        val videoUrl = extractUrlField(html, "video_url", windowStart, windowEnd)
            ?: "https://cdn1.suno.ai/$id.mp4"

        return SunoSongMetadata(
            id = id,
            title = payloadTitle?.takeIf { it.isNotBlank() }
                ?: head.title
                ?: "Canción Suno ${id.take(8)}",
            creatorHandle = handle?.takeIf { it.isNotBlank() } ?: head.handle.orEmpty(),
            displayName = displayName?.takeIf { it.isNotBlank() } ?: head.author ?: "Suno AI",
            durationSeconds = duration ?: 120.0,
            lyricsPrompt = lyrics.takeIf { it.isNotBlank() && !it.equals("Prompt", ignoreCase = true) }.orEmpty(),
            tags = styleTags.orEmpty(),
            coverUrl = cover,
            videoUrl = videoUrl,
            audioUrl = audioUrl,
            isInstrumental = isInstrumental,
        )
    }

    /**
     * Localiza el objeto de la canción dentro del payload.
     *
     * @return el índice del identificador de la canción o -1 si no aparece.
     */
    private fun findClipIndex(html: String, id: String): Int {
        val escaped = Regex.escape(id)
        val byId = Regex("""\\?"id\\?"\s*:\s*\\?"$escaped\\?""").find(html)
        if (byId != null) return byId.range.first
        val byVideo = Regex("""cdn1\.suno\.ai/$escaped\.mp4""").find(html)
        if (byVideo != null) return byVideo.range.first
        return -1
    }

    /**
     * Recupera el valor de una clave JSON del payload.
     *
     * Cuando hay varias coincidencias dentro de la ventana se elige la más
     * cercana al ancla de la canción, que es la que corresponde a la ficha.
     */
    private fun extractField(
        html: String,
        key: String,
        fromIndex: Int,
        toIndex: Int,
        anchor: Int,
        maxLength: Int = 600,
    ): String? {
        if (toIndex <= fromIndex) return null
        val segment = html.substring(fromIndex, toIndex)
        val regex = Regex("""\\?"$key\\?"\s*:\s*\\?"(${valuePattern(maxLength)})\\?"(?=\s*[,}\]])""")
        var bestIndex = Int.MAX_VALUE
        var bestValue: String? = null
        for (match in regex.findAll(segment)) {
            val absolute = fromIndex + match.range.first
            val value = decodeFlightValue(match.groupValues[1])
            if (value.isBlank()) continue
            val distance = if (anchor >= 0) kotlin.math.abs(absolute - anchor) else absolute
            if (distance < bestIndex) {
                bestIndex = distance
                bestValue = value
            }
        }
        return bestValue
    }

    /**
     * Cuerpo de un valor de cadena dentro del payload.
     *
     * El delimitador de las cadenas es la secuencia `\"`. Una comilla escapada
     * dentro del valor llega como `\\\"` (cuatro caracteres), así que el
     * cuerpo acepta ese bloque completo pero nunca una comilla simple: de
     * otro modo el valor se tragaría el cierre del campo y la clave siguiente.
     */
    private fun valuePattern(maxLength: Int): String =
        """(?:\\\\\\"|\\[^"]|[^"\\]){0,$maxLength}?"""

    /** Recupera un valor numérico (la duración, por ejemplo). */
    private fun extractNumber(html: String, key: String, fromIndex: Int, toIndex: Int): Double? {
        if (toIndex <= fromIndex) return null
        // Suno publica la duración como número, pero se tolera también entrecomillada.
        val regex = Regex("""\\?"$key\\?"\s*:\s*\\?"?([0-9]+(?:\.[0-9]+)?)""")
        val match = regex.find(html.substring(fromIndex, toIndex)) ?: return null
        return match.groupValues[1].toDoubleOrNull()
    }

    /**
     * Recupera la letra de la canción.
     *
     * Suno guarda el texto en un fragmento del payload y lo referencia desde
     * la ficha (`"prompt":"$c9"`); en ese caso se resuelve el fragmento. Como
     * red de seguridad se lee también el área de texto donde la propia página
     * muestra la letra.
     */
    private fun extractLyrics(html: String, fromIndex: Int, toIndex: Int, anchor: Int): String {
        val promptValue = extractField(html, "prompt", fromIndex, toIndex, anchor, maxLength = 12000)
        if (!promptValue.isNullOrBlank()) {
            if (promptValue.startsWith("$")) {
                resolveChunkText(html, promptValue, fromIndex)?.let { chunk ->
                    if (chunk.isNotBlank()) return chunk
                }
            } else if (!promptValue.equals("Prompt", ignoreCase = true)) {
                return promptValue
            }
        }
        return extractLyricsFromTextarea(html).orEmpty()
    }

    /**
     * Resuelve un fragmento de texto del payload (`$c9` → `c9:Tc08,<letra>`).
     */
    private fun resolveChunkText(html: String, reference: String, fromIndex: Int): String? {
        val chunkId = reference.removePrefix("$").trim()
        if (chunkId.isBlank()) return null
        val marker = "$chunkId:T"
        val markerIndex = html.indexOf(marker, fromIndex).takeIf { it >= 0 }
            ?: html.indexOf(marker).takeIf { it >= 0 }
            ?: return null

        var contentStart = markerIndex + marker.length
        val comma = html.indexOf(',', contentStart)
        if (comma in 0..(contentStart + 32)) {
            val between = html.substring(contentStart, comma)
            // El fragmento puede llevar un identificador propio (`Tc08,`).
            contentStart = comma + 1
            if (between.isBlank()) contentStart = markerIndex + marker.length
        }

        val terminator = Regex("""[0-9a-z]{1,4}:(?:\[|T|null|"|\{)""").find(html, contentStart)
        val contentEnd = terminator?.range?.first ?: html.length
        if (contentEnd <= contentStart) return null

        return decodeFlightValue(html.substring(contentStart, contentEnd)).trim().takeIf { it.isNotBlank() }
    }

    /**
     * Lee la letra del área de texto donde la página muestra la ficha.
     */
    private fun extractLyricsFromTextarea(html: String): String? {
        val match = Regex("""<textarea[^>]*>(\[[^<]{2,})</textarea>""").find(html) ?: return null
        val raw = match.groupValues[1]
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
        return decodeFlightValue(raw).trim().takeIf { it.isNotBlank() }
    }

    private val GENERIC_UI_TITLES = setOf(
        "consigue la experiencia completa de la app",
        "get the full app experience",
        "sharing settings",
        "configuración para compartir",
        "publish song",
        "publicar canción",
        "edit song details",
        "editar detalles de la canción",
        "enter phone number",
        "enter email address",
        "enter code",
        "suno | ai music generator",
        "suno",
        "you're invited to suno",
        "restaurando tu sesión...",
        "restaurando tu sesión",
        "restaurando tu sesion...",
        "restaurando tu sesion",
        "restoring your session...",
        "restoring your session",
        "restoring session...",
        "restoring session",
    )

    private fun isGenericTitle(title: String?): Boolean {
        if (title.isNullOrBlank()) return true
        val lower = title.trim().lowercase()
        return GENERIC_UI_TITLES.contains(lower) ||
            lower.startsWith("consigue la experiencia") ||
            lower.startsWith("get the full app") ||
            lower.startsWith("restaurando tu sesi") ||
            lower.startsWith("restoring your session") ||
            lower.startsWith("restoring session")
    }

    /**
     * Recupera el título de la canción.
     *
     * Se prefiere el título que precede a los contadores de la ficha
     * (`play_count`), porque la página también usa la clave `title` en los
     * textos de la interfaz.
     */
    private fun extractTitle(html: String, fromIndex: Int, toIndex: Int, anchor: Int): String? {
        if (toIndex <= fromIndex) return null
        val segment = html.substring(fromIndex, toIndex)
        val withCounters = Regex(
            """\\?"title\\?"\s*:\s*\\?"(${valuePattern(300)})\\?"\s*,\s*\\?"play_count\\?""",
        ).find(segment)
        if (withCounters != null) {
            val value = decodeFlightValue(withCounters.groupValues[1])
            if (value.isNotBlank() && !isGenericTitle(value)) return value
        }
        val candidate = extractField(html, "title", fromIndex, toIndex, anchor)
        return candidate?.takeIf { !isGenericTitle(it) }
    }

    /**
     * Recupera el autor de la canción: nombre visible y alias.
     *
     * La página incluye los nombres de las personas de Suno (`V6`, `COVER`…)
     * con la misma clave `display_name`, así que se exige la pareja
     * `display_name` + `handle`, que solo la ficha del autor tiene.
     */
    private fun extractAuthor(html: String, fromIndex: Int, toIndex: Int, anchor: Int): Pair<String, String>? {
        if (toIndex <= fromIndex) return null
        val segment = html.substring(fromIndex, toIndex)
        val regex = Regex(
            """\\?"display_name\\?"\s*:\s*\\?"(${valuePattern(200)})\\?"\s*,\s*\\?"handle\\?"\s*:\s*\\?"(${valuePattern(200)})\\?"(?=\s*[,}\]])""",
        )
        var bestDistance = Int.MAX_VALUE
        var best: Pair<String, String>? = null
        for (match in regex.findAll(segment)) {
            val displayName = decodeFlightValue(match.groupValues[1])
            val handle = decodeFlightValue(match.groupValues[2])
            if (displayName.isBlank() || handle.isBlank()) continue
            val distance = if (anchor >= 0) kotlin.math.abs(fromIndex + match.range.first - anchor) else 0
            if (distance < bestDistance) {
                bestDistance = distance
                best = displayName to handle
            }
        }
        return best
    }

    /** Recupera una URL (la portada, por ejemplo) del payload. */
    private fun extractUrlField(html: String, key: String, fromIndex: Int, toIndex: Int): String? {
        if (toIndex <= fromIndex) return null
        val regex = Regex("""\\?"$key\\?"\s*:\s*\\?"(https?://[^"\\]+)\\?"""")
        return regex.find(html.substring(fromIndex, toIndex))
            ?.groupValues?.get(1)
            ?.takeIf { it.isNotBlank() }
    }

    /**
     * Lee la cabecera HTML de la ficha: título, autor, alias y portada.
     */
    private fun parseHeadMetadata(html: String): HeadMetadata {
        val ogTitle = Regex("""<meta\s+(?:property|name)=["'](?:og:title|twitter:title)["']\s+content=["']([^"']{1,200})["']""")
            .find(html)?.groupValues?.get(1)?.trim()
            ?.replace("&amp;", "&")
            ?.replace("&quot;", "\"")
            ?.takeIf { !isGenericTitle(it) }

        val titleTag = Regex("""<title>([^<]{1,200})</title>""").find(html)?.groupValues?.get(1)?.trim()
        val rawTitle = titleTag
            ?.removeSuffix("| Suno")
            ?.trim()
            ?.takeIf { it.isNotBlank() }

        val description = Regex("""<meta\s+(?:property|name)=["'](?:description|og:description)["']\s+content=["']([^"']{1,400})["']""")
            .find(html)?.groupValues?.get(1)
            ?.replace("&amp;", "&")
            ?.replace("&quot;", "\"")
            ?.trim()

        val authorFromDescription = description
            ?.substringBefore(". Listen and make your own on Suno.")
            ?.let { segment ->
                val byIndex = segment.indexOf(" by ")
                if (byIndex <= 0) null else {
                    val author = segment.substring(byIndex + 4).trim()
                    val handle = Regex("""@([A-Za-z0-9._-]+)""").find(author)?.groupValues?.get(1)
                    val display = author.substringBefore(" (@").trim()
                    display to handle
                }
            }

        val titleFromTag = rawTitle?.let { full ->
            val byIndex = full.indexOf(" by ")
            val withoutAuthor = if (byIndex > 0) full.substring(0, byIndex).trim() else full
            withoutAuthor.ifBlank { null }
        }?.takeIf { !isGenericTitle(it) }

        val coverFromMeta = Regex("""<meta\s+(?:property|name)=["'](?:og:image|twitter:image)["']\s+content=["'](https?://[^"']+)["']""")
            .find(html)?.groupValues?.get(1)?.trim()

        return HeadMetadata(
            title = ogTitle ?: titleFromTag,
            author = authorFromDescription?.first?.takeIf { it.isNotBlank() },
            handle = authorFromDescription?.second,
            coverUrl = coverFromMeta,
        )
    }

    private data class HeadMetadata(
        val title: String? = null,
        val author: String? = null,
        val handle: String? = null,
        val coverUrl: String? = null,
    )

    /**
     * Decodifica un valor del payload de Suno.
     *
     * El texto viaja en dos niveles de escapado: primero dentro de un literal
     * de JavaScript y después dentro del JSON de la ficha. Se deshacen ambos.
     */
    private fun decodeFlightValue(raw: String): String {
        return decodeEscapes(decodeEscapes(raw)).trim()
    }

    private fun decodeEscapes(raw: String): String {
        val sb = StringBuilder(raw.length)
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            if (c == '\\' && i + 1 < raw.length) {
                when (raw[i + 1]) {
                    'n' -> { sb.append('\n'); i += 2 }
                    'r' -> { sb.append('\r'); i += 2 }
                    't' -> { sb.append('\t'); i += 2 }
                    'b' -> { sb.append('\b'); i += 2 }
                    'f' -> { sb.append('\u000C'); i += 2 }
                    '"' -> { sb.append('"'); i += 2 }
                    '\\' -> { sb.append('\\'); i += 2 }
                    '/' -> { sb.append('/'); i += 2 }
                    'u' -> {
                        val hex = if (i + 6 <= raw.length) raw.substring(i + 2, i + 6) else ""
                        val code = hex.takeIf { it.length == 4 }?.toIntOrNull(16)
                        if (code != null) { sb.append(code.toChar()); i += 6 } else { sb.append(c); i++ }
                    }
                    else -> { sb.append(c); i++ }
                }
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }

    private fun defaultCoverUrl(id: String): String = "https://cdn2.suno.ai/image_large_$id.jpeg"

    /**
     * Borra un archivo temporal tolerando el bloqueo de Windows: el proceso
     * de FFmpeg puede tardar un instante en liberar el archivo.
     */
    private fun deleteQuietly(file: File?) {
        if (file == null) return
        repeat(4) {
            if (!file.exists()) return
            val deleted = runCatching { file.delete() }.getOrDefault(false)
            if (deleted) return
            runCatching { Thread.sleep(150) }
        }
    }

    /**
     * Descarga la portada de la canción para incrustarla en el archivo de audio.
     */
    private fun downloadCover(coverUrl: String, destinationDir: File, onLog: (String) -> Unit): File? {
        if (coverUrl.isBlank()) return null
        return runCatching {
            val target = File(destinationDir, ".suno_cover.jpg")
            val connection = (URL(coverUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 20_000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36")
                setRequestProperty("Referer", "https://suno.com/")
            }
            if (connection.responseCode !in 200..299) return null
            connection.inputStream.use { input ->
                FileOutputStream(target).use { output -> input.copyTo(output) }
            }
            if (target.length() <= 0L) return null
            target
        }.getOrNull()
    }

    /**
     * Cabecera `Cookie` de la sesión de Suno a partir del fichero Netscape.
     *
     * El fichero lo escribe la extensión de navegador con las cookies de la
     * cuenta iniciada. Sin él, Suno solo entrega la ficha pública de la
     * canción; con él, la ficha real llega también a las canciones privadas.
     */
    private fun sessionCookieHeader(cookieFile: String?): String? {
        if (cookieFile.isNullOrBlank()) return null
        val content = runCatching { File(cookieFile).readText() }.getOrNull() ?: return null
        val sunoCom = NetscapeCookieJar.cookieHeaderForHost(content, "suno.com")
        val sunoAi = NetscapeCookieJar.cookieHeaderForHost(content, "suno.ai")
        val combined = listOfNotNull(sunoCom.takeIf { it.isNotBlank() }, sunoAi.takeIf { it.isNotBlank() })
            .joinToString("; ")
        return combined.takeIf { it.isNotBlank() }
    }

    private data class StreamCandidate(val url: String, val description: String)

    private fun downloadFirstAvailableStream(
        candidates: List<StreamCandidate>,
        targetFile: File,
        cookieFile: String?,
        onLog: (String) -> Unit,
        onProgress: (percentage: Double, speed: String, size: String) -> Unit,
    ): StreamCandidate {
        var had403 = false
        var lastException: Exception? = null

        for (candidate in candidates) {
            onLog("Probando stream (${candidate.description}): ${candidate.url}")
            try {
                if (candidate.url.contains(SunoMangoDecryptor.MANGO_CDN_HOST)) {
                    downloadMangoStream(candidate.url, targetFile, cookieFile, onLog, onProgress)
                } else {
                    downloadHttpStream(candidate.url, targetFile, cookieFile, onProgress)
                }
                if (targetFile.exists() && targetFile.length() > 0) {
                    onLog("Descarga completada desde ${candidate.description}")
                    return candidate
                }
            } catch (ex: DownloadException) {
                lastException = ex
                if (ex.message?.contains("privada", ignoreCase = true) == true || ex.message?.contains("403") == true) {
                    had403 = true
                }
                onLog("Stream no disponible (${ex.message}): ${candidate.url}")
                deleteQuietly(targetFile)
            } catch (t: Throwable) {
                lastException = Exception(t.message, t)
                onLog("Error al acceder a ${candidate.url}: ${t.message}")
                deleteQuietly(targetFile)
            }
        }

        if (had403) {
            throw DownloadException("La canción es privada en Suno. Hazla pública en Suno (Compartir → Pública) para poder descargarla.")
        }
        throw DownloadException(lastException?.message ?: "No se pudo descargar ningún stream de la pista de Suno.")
    }

    private fun downloadMangoStream(
        streamUrl: String,
        targetFile: File,
        cookieFile: String?,
        onLog: (String) -> Unit,
        onProgress: (percentage: Double, speed: String, size: String) -> Unit,
    ) {
        val id = extractId(streamUrl) ?: throw DownloadException("No se encontró el ID del clip de Suno para descifrado Mango.")
        val cookieHeader = sessionCookieHeader(cookieFile)
        onLog("Obteniendo credenciales de reproducción de Suno (Mango Rights)...")
        val rights = SunoMangoDecryptor.fetchRights(id, cookieHeader)
            ?: throw DownloadException("No se pudo obtener la autorización de reproducción segura de Suno.")

        onLog("Descargando y descifrando stream seguro en tiempo real...")
        val connection = (URL(streamUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 25_000
            setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36")
            setRequestProperty("Accept", "*/*")
            setRequestProperty("Referer", "https://suno.com/")
            setRequestProperty("Origin", "https://suno.com")
            if (!cookieHeader.isNullOrBlank()) setRequestProperty("Cookie", cookieHeader)
        }

        if (connection.responseCode !in 200..299) {
            throw DownloadException("El servidor de audio seguro de Suno respondió con código HTTP ${connection.responseCode}")
        }

        val totalBytes = connection.contentLengthLong
        var downloadedBytes = 0L
        val startTime = System.currentTimeMillis()

        connection.inputStream.use { input ->
            FileOutputStream(targetFile).use { output ->
                SunoMangoDecryptor.decryptStream(
                    clipId = id,
                    rights = rights,
                    inputStream = input,
                    outputStream = output,
                ) { totalDecrypted ->
                    downloadedBytes = totalDecrypted
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

    private fun downloadHttpStream(
        streamUrl: String,
        targetFile: File,
        cookieFile: String? = null,
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
                sessionCookieHeader(cookieFile)?.let { setRequestProperty("Cookie", it) }
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

        if (connection.responseCode == 403) {
            throw DownloadException("La canción es privada en Suno. Hazla pública en Suno (Compartir → Pública) para poder descargarla.")
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
        coverFile: File?,
        onLog: (String) -> Unit,
    ): Boolean {
        val audioCodec = when (format) {
            MediaFormat.Mp3 -> listOf("-c:a", "libmp3lame", "-q:a", "0")
            MediaFormat.M4a -> listOf("-c:a", "aac", "-b:a", "256k")
            MediaFormat.Flac -> listOf("-c:a", "flac")
            MediaFormat.Wav -> listOf("-c:a", "pcm_s16le")
            else -> listOf("-c:a", "copy")
        }

        val metadataArgs = listOf(
            "-metadata", "title=${metadata.title}",
            "-metadata", "artist=${metadata.displayName.ifBlank { metadata.creatorHandle }}",
            "-metadata", "album=Suno AI",
            "-metadata", "genre=${metadata.tags.take(64)}",
            "-metadata", "comment=Generado en Suno AI · Descargado con Luna Fetch",
        )

        // WAV no admite carátula incrustada; el resto de formatos sí.
        val usableCover = coverFile?.takeIf { it.exists() && format != MediaFormat.Wav }

        if (usableCover != null) {
            // Sin `-vn`: la imagen debe entrar en el archivo como carátula
            // incrustada, y esa opción descartaría la pista de la portada.
            val command = mutableListOf("ffmpeg", "-y", "-i", inputFile.absolutePath, "-i", usableCover.absolutePath)
            command.addAll(listOf("-map", "0:a", "-map", "1:v"))
            command.addAll(audioCodec)
            command.addAll(listOf("-c:v", "mjpeg", "-disposition:v:0", "attached_pic"))
            command.addAll(metadataArgs)
            command.add(outputFile.absolutePath)
            if (runFfmpeg(command)) {
                onLog("Audio transcodificado con metadatos ID3 y portada incrustada.")
                return true
            }
            // Si la portada impide la conversión se reintenta sin ella.
            onLog("Portada no incrustable en ${format.displayName}; se reintenta sin carátula.")
        }

        val command = mutableListOf("ffmpeg", "-y", "-i", inputFile.absolutePath, "-vn")
        command.addAll(audioCodec)
        command.addAll(metadataArgs)
        command.add(outputFile.absolutePath)

        return if (runFfmpeg(command)) {
            onLog("Audio transcodificado con metadatos ID3 exitosamente.")
            true
        } else {
            false
        }
    }

    private fun runFfmpeg(command: List<String>): Boolean {
        return runCatching {
            // FFmpeg escribe mucho en la salida estándar: si nadie la lee, el
            // proceso se bloquea al llenarse el canal y la descarga se queda
            // colgada. La salida se drena en un hilo aparte.
            val process = ProcessBuilder(command).redirectErrorStream(true).start()
            val drain = Thread {
                runCatching { process.inputStream.bufferedReader().use { it.readText() } }
            }.apply {
                isDaemon = true
                start()
            }
            val exitCode = process.waitFor()
            drain.join(2000)
            process.destroy()
            exitCode == 0
        }.getOrDefault(false)
    }

    private fun uniqueOutputFile(dir: File, baseName: String, extension: String): File {
        var file = File(dir, "$baseName.$extension")
        var counter = 1
        while (file.exists() && file.length() > 0L) {
            file = File(dir, "$baseName ($counter).$extension")
            counter++
        }
        return file
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
}
