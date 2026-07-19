package com.biglexj.lunafetch.domain

data class VideoInfo(
    val url: String,
    val title: String,
    val uploader: String,
    val durationSeconds: Double,
    val thumbnailUrl: String,
    val maxHeight: Int,
    val collectionTitle: String? = null,
    val collectionCount: Int = 0,
    val collectionEntries: List<CollectionEntry> = emptyList(),
)

val VideoInfo.isCollection: Boolean
    get() = !collectionTitle.isNullOrBlank() && (collectionCount > 1 || collectionEntries.size > 1)

data class CollectionEntry(
    val index: Int,
    val title: String,
    val uploader: String = "",
    val durationSeconds: Double = 0.0,
)

data class DownloadHistoryItem(
    val id: String,
    val title: String,
    val formatLabel: String,
    val path: String,
    val url: String = "",
    val timestampMs: Long = System.currentTimeMillis(),
)

enum class MediaFormat(
    val displayName: String,
    val extension: String,
    val isAudio: Boolean,
) {
    Mp4("Video MP4", "mp4", false),
    WebM("Video WebM", "webm", false),
    Mp3("Audio MP3", "mp3", true),
    M4a("Audio M4A", "m4a", true),
}

data class QualityOption(
    val displayName: String,
    val formatSelector: String,
    val audioQuality: String? = null,
)

object FormatCatalog {
    fun qualities(format: MediaFormat, maxHeight: Int): List<QualityOption> {
        if (format.isAudio) {
            return listOf(
                QualityOption("Mejor calidad", "bestaudio/best", "0"),
                QualityOption("Estándar · 192 kbps", "bestaudio/best", "5"),
                QualityOption("Ligera · 128 kbps", "bestaudio/best", "9"),
            )
        }

        val safeHeight = maxHeight.coerceAtLeast(360)
        val list = mutableListOf<QualityOption>()
        listOf(4320, 2160, 1440, 1080, 720, 480, 360).forEach { height ->
            if (height <= safeHeight || height == 360) {
                val label = when (height) {
                    4320 -> "8K · Ultra HD"
                    2160 -> "4K · Ultra HD"
                    1440 -> "1440p · 2K"
                    1080 -> "1080p · Full HD"
                    720 -> "720p · HD"
                    else -> "${height}p"
                }

                if (height >= 720) {
                    list.add(QualityOption("$label (60 FPS)", videoSelector(format, height)))
                    list.add(QualityOption("$label (30 FPS)", videoSelector(format, height, 30)))
                } else {
                    list.add(QualityOption(label, videoSelector(format, height)))
                }
            }
        }
        return list
    }

    private fun videoSelector(format: MediaFormat, height: Int, maxFps: Int? = null): String {
        val fpsConstraint = if (maxFps != null) "[fps<=$maxFps]" else ""
        val preferred = when (format) {
            MediaFormat.Mp4 -> "bestvideo[height<=$height]$fpsConstraint[ext=mp4]+bestaudio[ext=m4a]"
            MediaFormat.WebM -> "bestvideo[height<=$height]$fpsConstraint[ext=webm]+bestaudio[ext=webm]"
            else -> error("El selector de vídeo requiere un formato de vídeo.")
        }
        return "$preferred/bestvideo[height<=$height]$fpsConstraint+bestaudio/best[height<=$height]/best"
    }
}

data class DownloadRequest(
    val url: String,
    val destination: String,
    val format: MediaFormat,
    val quality: QualityOption,
    val downloadCollection: Boolean = false,
)

enum class DownloadPhase {
    Preparing,
    Downloading,
    Processing,
    Completed,
    Cancelled,
}

data class DownloadProgress(
    val percentage: Double,
    val speed: String = "",
    val eta: String = "",
    val size: String = "",
    val phase: DownloadPhase = DownloadPhase.Downloading,
) {
    val statusMessage: String
        get() = when (phase) {
            DownloadPhase.Preparing -> "Preparando descarga…"
            DownloadPhase.Downloading -> "Descargando… ${percentage.toInt()} %"
            DownloadPhase.Processing -> "Procesando audio y video…"
            DownloadPhase.Completed -> "Descarga completada"
            DownloadPhase.Cancelled -> "Descarga cancelada"
        }
}

data class DownloadResult(
    val outputPaths: List<String>,
    val openPath: String? = outputPaths.singleOrNull(),
)

class DownloadException(message: String, cause: Throwable? = null) : Exception(message, cause)
