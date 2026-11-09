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

@kotlinx.serialization.Serializable
data class DownloadHistoryItem(
    val id: String,
    val title: String,
    val formatLabel: String,
    val path: String,
    val url: String = "",
    val timestampMs: Long = System.currentTimeMillis(),
    val originDevice: String = "",
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
    Flac("Audio FLAC (Lossless)", "flac", true),
    Wav("Audio WAV (Lossless)", "wav", true);

    companion object {
        /**
         * Formato que corresponde a un nombre recibido desde fuera.
         *
         * La extensión del navegador y el importador de Suno envían el formato
         * como texto ("mp3", "flac", "Audio FLAC (Lossless)"…): cada uno debe
         * volver a su formato, no convertirse en MP3.
         */
        fun resolve(name: String): MediaFormat {
            val value = name.trim().lowercase()
            return when {
                value.contains("flac") -> Flac
                value.contains("wav") -> Wav
                value.contains("m4a") -> M4a
                value.contains("webm") -> WebM
                value.contains("mp3") -> Mp3
                value.contains("mp4") -> Mp4
                // Audio sin especificar: MP3 es el formato habitual.
                value.contains("audio") -> Mp3
                else -> Mp4
            }
        }
    }
}

data class QualityOption(
    val displayName: String,
    val formatSelector: String,
    val audioQuality: String? = null,
)

object FormatCatalog {

    /**
     * Calidades ofrecidas para un formato de vídeo.
     *
     * La primera opción, «Mejor calidad», no impone techo: se descarga la mayor
     * que exista en el video. Las demás sí limitan: al elegir 2K, la descarga
     * no pasa de 2K. Todas están disponibles aunque el video no llegue a esa
     * altura, porque el selector `bestvideo*[height<=X]` cae a la mejor que haya.
     */
    fun qualities(format: MediaFormat): List<QualityOption> {
        if (format.isAudio) {
            return if (format == MediaFormat.Flac || format == MediaFormat.Wav) {
                listOf(
                    QualityOption("Máxima fidelidad sin pérdidas", "bestaudio/best", "0"),
                )
            } else {
                listOf(
                    QualityOption("Mejor calidad", "bestaudio/best", "0"),
                    QualityOption("Estándar · 192 kbps", "bestaudio/best", "5"),
                    QualityOption("Ligera · 128 kbps", "bestaudio/best", "9"),
                )
            }
        }

        val list = mutableListOf(QualityOption("Mejor calidad", bestSelector(format)))
        listOf(4320, 2160, 1440, 1080, 720, 480, 360).forEach { height ->
            val label = when (height) {
                4320 -> "8K · Ultra HD"
                2160 -> "4K · Ultra HD"
                1440 -> "1440p · 2K"
                1080 -> "1080p · Full HD"
                720 -> "720p · HD"
                else -> "${height}p"
            }
            list.add(QualityOption(label, videoSelector(format, height)))
        }
        return list
    }

    /** Selector sin límite de altura: la mejor calidad que exista en el video. */
    private fun bestSelector(format: MediaFormat): String = when (format) {
        MediaFormat.Mp4 -> "bestvideo*[ext=mp4]+bestaudio[ext=m4a]/bestvideo*[ext=mp4]+bestaudio/bestvideo*+bestaudio/best"
        MediaFormat.WebM -> "bestvideo*[ext=webm]+bestaudio[ext=webm]/bestvideo*[ext=webm]+bestaudio/bestvideo*+bestaudio/best"
        else -> "bestaudio/best"
    }

    private fun videoSelector(format: MediaFormat, height: Int, maxFps: Int? = null): String {
        val fpsConstraint = if (maxFps != null) "[fps<=$maxFps]" else ""
        val preferred = when (format) {
            MediaFormat.Mp4 -> "bestvideo*[height<=$height]$fpsConstraint[ext=mp4]+bestaudio[ext=m4a]/bestvideo*[height<=$height]$fpsConstraint[ext=mp4]+bestaudio/best[height<=$height][ext=mp4]"
            MediaFormat.WebM -> "bestvideo*[height<=$height]$fpsConstraint[ext=webm]+bestaudio[ext=webm]/bestvideo*[height<=$height]$fpsConstraint[ext=webm]+bestaudio/best[height<=$height][ext=webm]"
            else -> error("El selector de vídeo requiere un formato de vídeo.")
        }
        return "$preferred/bestvideo*[height<=$height]$fpsConstraint+bestaudio/best[height<=$height]/bestvideo*+bestaudio/best"
    }
}

data class DownloadRequest(
    val url: String,
    val destination: String,
    val format: MediaFormat,
    val quality: QualityOption,
    val downloadCollection: Boolean = false,
    /**
     * Indica que la descarga debe sobrescribir el archivo que ya existe en la
     * carpeta de destino. Se usa para reparar un archivo dañado.
     */
    val replaceExisting: Boolean = false,
    /**
     * Fichero de cookies de sesión (formato Netscape) que acompaña al enlace.
     *
     * Lo envía la extensión de navegador: permite leer la ficha real de
     * plataformas que solo la muestran a la sesión iniciada (Suno, entre
     * otras) sin necesidad de que el contenido sea público.
     */
    val cookieFile: String? = null,
    /**
     * Indica si se debe guardar el archivo de letra (.txt) cuando esté
     * disponible. Desactivado por defecto.
     */
    val downloadLyrics: Boolean = false,
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
