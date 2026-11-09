package com.biglexj.lunafetch.domain

/**
 * Limpieza de las URLs antes de analizarlas.
 *
 * Además de los parámetros de seguimiento de TikTok, quita de YouTube los que
 * apuntan a la radio automática: al reproducirse un video, YouTube añade
 * `list=RD…`, `index` y `start_radio`, y con ellos el motor devuelve la mezcla
 * completa (cientos de videos) en lugar del video. Sin ese video suelto la
 * calidad se queda en 1080p, porque una lista no tiene formatos propios.
 */
object UrlUtils {

    private val UrlRegex = Regex("""https?://[^\s<>"{}|\\^`]+""", RegexOption.IGNORE_CASE)

    /** Extrae la URL del texto y deja la dirección en la que se va a analizar. */
    fun normalize(input: String): String {
        val extracted = UrlRegex.find(input.trim())?.value ?: input.trim()
        if (isYouTubeWatchUrl(extracted)) return stripYouTubeRadio(extracted)
        return TikTokUtils.sanitizeUrl(extracted)
    }

    private fun isYouTubeWatchUrl(url: String): Boolean {
        val lower = url.lowercase()
        val isYouTube = lower.contains("youtube.com/watch") || lower.contains("youtu.be/")
        return isYouTube && lower.contains("v=")
    }

    private fun stripYouTubeRadio(url: String): String {
        val parts = url.split("?", limit = 2)
        if (parts.size < 2) return url

        val kept = parts[1].split("&").filterNot { param ->
            val key = param.substringBefore("=").lowercase()
            val value = param.substringAfter("=", "")
            when (key) {
                "start_radio", "index", "rindex" -> true
                // Las radios automáticas de YouTube identifican su lista con RD.
                "list" -> value.startsWith("RD", ignoreCase = true)
                else -> false
            }
        }

        return if (kept.isEmpty()) parts[0] else "${parts[0]}?${kept.joinToString("&")}"
    }
}
