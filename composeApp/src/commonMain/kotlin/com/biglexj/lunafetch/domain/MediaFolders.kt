package com.biglexj.lunafetch.domain

/**
 * Carpetas de destino por tipo de medio.
 *
 * Luna Fetch separa el material dentro de la carpeta elegida por el usuario:
 * las descargas sonoras van a `Música` y las visuales a `Videos`. Son los
 * nombres con los que el usuario ya ordena su biblioteca, de modo que el
 * material de la aplicación queda junto al que ya tenía, sin carpetas
 * paralelas ni duplicados.
 */
object MediaFolders {

    const val AUDIO = "Música"
    const val VIDEO = "Videos"

    /**
     * Nombres antiguos de las subcarpetas.
     *
     * Se reconocen para que la reparación de un archivo descargado antes del
     * cambio no termine anidando `…/audio/Música`.
     */
    private val LEGACY_NAMES = setOf("audio", "video")

    /** Nombre de la subcarpeta que corresponde al formato solicitado. */
    fun folderFor(format: MediaFormat): String = if (format.isAudio) AUDIO else VIDEO

    /**
     * ¿La ruta ya termina en la subcarpeta indicada?
     *
     * Importa en las reparaciones: el archivo dañado ya vive en `…/Música` y su
     * carpeta padre es esa misma subcarpeta, así que no debe volver a anidarse
     * como `Música/Música`. Los nombres antiguos (`audio`, `video`) cuentan
     * también como carpeta de medio.
     */
    fun pointsToFolder(destination: String, folder: String): Boolean {
        val trimmed = destination.trim().trimEnd('/', '\\')
        if (trimmed.isBlank()) return false
        val lastSegment = trimmed.substringAfterLast('/').substringAfterLast('\\')
        val normalized = lastSegment.lowercase()
        return normalized == folder.lowercase() || normalized in LEGACY_NAMES
    }

    /**
     * Resuelve la carpeta final de una descarga: la carpeta elegida más la
     * subcarpeta del formato. Si la ruta ya apunta a esa subcarpeta se
     * devuelve intacta.
     */
    fun resolve(destination: String, format: MediaFormat): String {
        val trimmed = destination.trim().trimEnd('/', '\\')
        if (trimmed.isBlank()) return destination
        val folder = folderFor(format)
        if (pointsToFolder(trimmed, folder)) return trimmed
        return "$trimmed/$folder"
    }
}
