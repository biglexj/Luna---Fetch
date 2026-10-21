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
    /** Clave canónica del enlace, para detectar duplicados dentro de la cola. */
    val canonicalKey: String = "",
    /**
     * Ruta del archivo dañado que hay que rehacer. Cuando está presente la
     * descarga se escribe en la misma carpeta y reemplaza el archivo original.
     */
    val repairPath: String = "",
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

    /** Indica que este enlace repara un archivo dañado y debe reemplazarlo. */
    val isRepair: Boolean get() = repairPath.isNotBlank()
}

/**
 * Resultado de intentar encolar un enlace.
 */
enum class EnqueueOutcome {
    /** El enlace se encoló y se descargará. */
    Queued,

    /** El enlace no es válido o no está soportado. */
    InvalidUrl,

    /** El mismo enlace ya está pendiente o descargándose en la cola. */
    DuplicateInQueue,

    /** El enlace ya fue descargado y su archivo está en pie. */
    AlreadyDownloaded,
}

/**
 * Resultado de encolar un enlace, con el número de pendientes en la cola.
 */
data class EnqueueResult(
    val outcome: EnqueueOutcome,
    val pending: Int = 0,
) {
    val isQueued: Boolean get() = outcome == EnqueueOutcome.Queued
}
