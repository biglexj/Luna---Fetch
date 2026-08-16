package com.biglexj.lunafetch.domain.synapse

/**
 * Jerarquía sellada de acciones admitidas y emitidas por Luna Fetch
 * bajo el protocolo Aurora Synapse.
 */
sealed interface SynapseAction {

    /**
     * Orden de descarga de audio/video.
     * Esquema URI: luna://download?url=...&type=audio|video&quality=...&dest=...
     * Contrato JSON: action = "enqueue_download"
     */
    data class EnqueueDownload(
        val url: String,
        val mediaType: String = "video",
        val quality: String? = null,
        val targetDirectory: String? = null,
        val autoPlayOnFinish: Boolean = false,
        val embedMetadata: Boolean = true,
        val silent: Boolean = false,
    ) : SynapseAction

    /**
     * Orden de análisis previo de enlace sin iniciar descarga.
     * Esquema URI: luna://analyze?url=...
     * Contrato JSON: action = "analyze_url"
     */
    data class AnalyzeUrl(
        val url: String,
        val silent: Boolean = false,
    ) : SynapseAction

    /**
     * Orden de apertura de carpeta de descargas.
     * Esquema URI: luna://open_folder?path=...
     * Contrato JSON: action = "open_folder"
     */
    data class OpenFolder(
        val path: String? = null,
    ) : SynapseAction

    /**
     * Solicitud de foco y restauración de la ventana principal.
     */
    data object Focus : SynapseAction

    /**
     * Acción saliente: Delegar reproducción multimedia en Prisma.
     * Esquema URI destino: prisma://open?path=...&autoplay=true
     * Contrato JSON destino: action = "open_media"
     */
    data class PlayInPrisma(
        val filePath: String,
        val mediaCategory: String = "music",
        val autoPlay: Boolean = true,
    ) : SynapseAction
}
