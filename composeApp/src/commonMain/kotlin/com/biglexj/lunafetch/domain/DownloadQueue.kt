package com.biglexj.lunafetch.domain

/**
 * Estado de un enlace dentro de la cola de descargas.
 *
 * La cola garantiza que cada enlace se procese por completo (análisis + descarga)
 * antes de tomar el siguiente, aunque lleguen varios enlaces casi simultáneos desde
 * la extensión del navegador, Aurora Synapse o el Launcher nativo.
 */
enum class QueueItemStatus {
    Pending,
    Running,
    Completed,
    Failed,
    Cancelled,
}

/**
 * Un enlace encolado.
 *
 * @param id Identificador único del ítem (clave de orden de la cola).
 * @param url Enlace a descargar.
 * @param formatName Formato solicitado (mp4, mp3, m4a...).
 * @param quality Calidad solicitada de forma opcional (por ejemplo "1080p").
 * @param source Origen del envío: extensión, Synapse, historial, etc.
 * @param status Estado actual dentro de la cola.
 * @param title Título resuelto tras analizar el enlace (vacío hasta entonces).
 * @param errorMessage Motivo del fallo cuando el estado es [QueueItemStatus.Failed].
 */
data class QueuedDownload(
    val id: String,
    val url: String,
    val formatName: String,
    val quality: String? = null,
    val source: String = "Extensión",
    val status: QueueItemStatus = QueueItemStatus.Pending,
    val title: String = "",
    val errorMessage: String? = null,
) {
    val isPending: Boolean get() = status == QueueItemStatus.Pending
    val isRunning: Boolean get() = status == QueueItemStatus.Running
    val isFinished: Boolean
        get() = status == QueueItemStatus.Completed ||
            status == QueueItemStatus.Failed ||
            status == QueueItemStatus.Cancelled

    /** Etiqueta corta del formato solicitado para la interfaz. */
    val formatLabel: String
        get() = when {
            formatName.contains("mp3", ignoreCase = true) -> "MP3"
            formatName.contains("m4a", ignoreCase = true) -> "M4A"
            formatName.contains("flac", ignoreCase = true) -> "FLAC"
            formatName.contains("wav", ignoreCase = true) -> "WAV"
            formatName.contains("webm", ignoreCase = true) -> "WEBM"
            else -> formatName.uppercase().ifBlank { "MP4" }
        }
}
