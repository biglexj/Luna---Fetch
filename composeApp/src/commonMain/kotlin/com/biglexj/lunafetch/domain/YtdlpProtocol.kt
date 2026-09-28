package com.biglexj.lunafetch.domain

object YtdlpProtocol {
    const val ProgressPrefix = "LUNAFETCH_PROGRESS|"
    const val OutputPrefix = "LUNAFETCH_FILE|"

    private val classicProgress = Regex(
        """\[download]\s+(\d+(?:\.\d+)?)%\s+of\s+(?:~)?([^\s]+)\s+at\s+([^\s]+)\s+ETA\s+([^\s]+)""",
        RegexOption.IGNORE_CASE,
    )

    fun parseProgress(line: String): DownloadProgress? {
        val clean = line.trim()
        if (clean.startsWith(ProgressPrefix)) {
            val fields = clean.removePrefix(ProgressPrefix).split('|')
            val percentage = fields.getOrNull(0)
                ?.replace("%", "")
                ?.trim()
                ?.toDoubleOrNull()
                ?: return null
            return DownloadProgress(
                percentage = percentage.coerceIn(0.0, 100.0),
                size = fields.getOrNull(1).normalizedMetric(),
                speed = fields.getOrNull(2).normalizedMetric(),
                eta = fields.getOrNull(3).normalizedMetric(),
            )
        }

        val match = classicProgress.find(clean)
        if (match != null) {
            return DownloadProgress(
                percentage = match.groupValues[1].toDouble().coerceIn(0.0, 100.0),
                size = match.groupValues[2],
                speed = match.groupValues[3],
                eta = match.groupValues[4],
            )
        }

        if (clean.contains("[Merger]", true) ||
            clean.contains("[ExtractAudio]", true) ||
            clean.contains("[VideoConvertor]", true)
        ) {
            return DownloadProgress(100.0, phase = DownloadPhase.Processing)
        }
        return null
    }

    fun outputPath(line: String): String? {
        val clean = line.trim()
        if (clean.startsWith(OutputPrefix)) {
            return clean.removePrefix(OutputPrefix).takeIf { it.isNotBlank() }
        }
        val mergerMatch = Regex("""\[Merger\]\s+Merging formats into ["']?([^"']+)["']?""", RegexOption.IGNORE_CASE).find(clean)
        if (mergerMatch != null) {
            return mergerMatch.groupValues[1].trim()
        }
        val destMatch = Regex("""\[download\]\s+Destination:\s+([^\r\n]+)""", RegexOption.IGNORE_CASE).find(clean)
        if (destMatch != null) {
            val path = destMatch.groupValues[1].trim().removeSurrounding("\"").removeSurrounding("'")
            if (!path.endsWith(".part", true) && !path.contains(".f1") && !path.contains(".f2") && !path.contains(".f3")) {
                return path
            }
        }
        val alreadyMatch = Regex("""\[download\]\s+(.+?)\s+has already been downloaded""", RegexOption.IGNORE_CASE).find(clean)
        if (alreadyMatch != null) {
            return alreadyMatch.groupValues[1].trim().removeSurrounding("\"").removeSurrounding("'")
        }
        val extractMatch = Regex("""\[ExtractAudio\]\s+Destination:\s+([^\r\n]+)""", RegexOption.IGNORE_CASE).find(clean)
        if (extractMatch != null) {
            return extractMatch.groupValues[1].trim().removeSurrounding("\"").removeSurrounding("'")
        }
        val fixupMatch = Regex("""\[Fixup[a-zA-Z0-9]+\]\s+Fixing[a-zA-Z0-9\s]*in ["']?([^"']+)["']?""", RegexOption.IGNORE_CASE).find(clean)
        if (fixupMatch != null) {
            return fixupMatch.groupValues[1].trim()
        }
        return null
    }

    private fun originHeaders(url: String): List<String> {
        val lower = url.lowercase()
        if (lower.contains("tiktok.com") || lower.contains("douyin.com")) {
            return emptyList()
        }
        val match = Regex("""^(https?)://([^/?#]+)""", RegexOption.IGNORE_CASE).find(url.trim())
            ?: return emptyList()
        val scheme = match.groupValues[1]
        val host = match.groupValues[2]
        val origin = "$scheme://$host"
        return listOf(
            "--referer", "$origin/",
            "--user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36",
        )
    }

    fun buildAnalyzeArguments(url: String): List<String> = buildList {
        addAll(
            listOf(
                "--ignore-config",
                "--no-colors",
                "--no-check-certificates",
                "--dump-single-json",
                "--flat-playlist",
                "--yes-playlist",
            )
        )
        addAll(originHeaders(url))
    }

    fun buildDownloadArguments(request: DownloadRequest, outputTemplate: String): List<String> = buildList {
        addAll(
            listOf(
                "--ignore-config",
                "--no-colors",
                "--no-check-certificates",
                "--newline",
                "--progress",
                "--progress-template",
                "download:${ProgressPrefix}%(progress._percent_str)s|%(progress._total_bytes_str)s|%(progress._speed_str)s|%(progress._eta_str)s",
                "--print",
                "after_move:${OutputPrefix}%(filepath)s",
                "--print",
                "filename:${OutputPrefix}%(filename)s",
                "-f",
                request.quality.formatSelector,
            ),
        )
        addAll(originHeaders(request.url))
        if (request.format.isAudio) {
            addAll(
                listOf(
                    "-x",
                    "--audio-format",
                    request.format.extension,
                    "--embed-metadata",
                    "--embed-thumbnail",
                    "--convert-thumbnails",
                    "jpg",
                    "--postprocessor-args",
                    "ThumbnailsConvertor+FFmpeg_o:-vf crop=ih:ih:(iw-ih)/2:0",
                ),
            )
            request.quality.audioQuality?.let { addAll(listOf("--audio-quality", it)) }
            if (request.downloadCollection) {
                addAll(listOf("--parse-metadata", "%(playlist_title)s:%(meta_album)s"))
                addAll(listOf("--parse-metadata", "%(playlist_index)s:%(meta_track)s"))
            }
        } else {
            addAll(listOf("--merge-output-format", request.format.extension))
        }
        add(if (request.downloadCollection) "--yes-playlist" else "--no-playlist")
        addAll(listOf("-o", outputTemplate))
    }

    private fun String?.normalizedMetric(): String = this
        ?.trim()
        ?.takeUnless { it.isBlank() || it.equals("NA", true) || it.equals("N/A", true) }
        .orEmpty()
}
