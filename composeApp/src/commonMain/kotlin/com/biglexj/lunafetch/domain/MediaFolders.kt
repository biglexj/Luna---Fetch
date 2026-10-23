package com.biglexj.lunafetch.domain

/**
 * Carpetas de destino por tipo de medio.
 *
 * Luna Fetch separa el material dentro de la carpeta elegida por el usuario:
 * las descargas sonoras van a `audio` y las visuales a `video`. Así el disco
 * queda ordenado por naturaleza del contenido sin perder el control de la
 * ruta raíz.
 */
object MediaFolders {

    const val AUDIO = "audio"
    const val VIDEO = "video"

    /** Nombre de la subcarpeta que corresponde al formato solicitado. */
    fun folderFor(format: MediaFormat): String = if (format.isAudio) AUDIO else VIDEO

    /**
     * ¿La ruta ya termina en la subcarpeta indicada?
     *
     * Importa en las reparaciones: el archivo dañado ya vive en `…/audio` y su
     * carpeta padre es esa misma subcarpeta, así que no debe volver a anidarse
     * como `audio/audio`.
     */
    fun pointsToFolder(destination: String, folder: String): Boolean {
        val trimmed = destination.trim().trimEnd('/', '\\')
        if (trimmed.isBlank()) return false
        val lastSegment = trimmed.substringAfterLast('/').substringAfterLast('\\')
        return lastSegment.equals(folder, ignoreCase = true)
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
