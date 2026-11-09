package com.biglexj.lunafetch.domain

/**
 * Convierte un enlace en una clave canónica estable.
 *
 * Dos enlaces que apuntan al mismo contenido producen la misma clave, de modo
 * que `youtube.com/watch?v=abc`, `youtu.be/abc` y `youtube.com/shorts/abc` se
 * reconocen como el mismo video. Los parámetros de seguimiento se descartan
 * porque no cambian el contenido descargado.
 */
object LinkCanonicalizer {

    private val trackingParams = setOf(
        "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content", "utm_id",
        "si", "feature", "fbclid", "gclid", "igshid", "igsh", "vd_source",
        "ref", "ref_src", "ref_url", "source", "app", "share",
        "sender_device", "sender_web_id", "share_item_id", "share_link_id",
        "share_app_id", "share_author_id", "checksum", "_r", "_t", "timestamp",
        "is_from_webapp",
    )

    /**
     * @return Clave canónica del enlace (por ejemplo `yt:dQw4w9WgXcQ`) o una
     * cadena vacía si el enlace está en blanco.
     */
    fun canonicalKey(rawUrl: String): String {
        val url = rawUrl.trim()
        if (url.isBlank()) return ""

        val withoutScheme = url.substringAfter("://", url)
        val slash = withoutScheme.indexOf('/')
        val host = (if (slash >= 0) withoutScheme.substring(0, slash) else withoutScheme)
            .lowercase()
            .removePrefix("www.")
        // La ruta conserva la barra inicial: "example.com/video" → "/video".
        val rawPath = if (slash >= 0) withoutScheme.substring(slash) else ""
        val path = rawPath.substringBefore('?').trimEnd('/')
        val pathNoSlash = path.removePrefix("/")
        val query = rawPath.substringAfter('?', "")

        if (host == "youtu.be") {
            val id = pathNoSlash.substringBefore('/')
            return if (id.isNotBlank()) "yt:$id" else "web:$host"
        }
        if (host.endsWith("youtube.com") || host.endsWith("youtube-nocookie.com")) {
            val id = youtubeId(pathNoSlash, query)
            return if (id.isNotBlank()) "yt:$id" else "web:$host$path"
        }
        if (host.endsWith("tiktok.com")) {
            return "tt:$pathNoSlash"
        }
        if (host.endsWith("suno.com")) {
            val uuidRegex = Regex("""[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}""", RegexOption.IGNORE_CASE)
            val match = uuidRegex.find(url)
            if (match != null) {
                return "suno:${match.value.lowercase()}"
            }
        }

        val params = query.split('&')
            .filter { it.isNotBlank() }
            .filterNot { it.substringBefore('=').lowercase() in trackingParams }
            .sorted()
        return buildString {
            append("web:").append(host).append(path)
            if (params.isNotEmpty()) append('?').append(params.joinToString("&"))
        }
    }

    private fun youtubeId(path: String, query: String): String = when {
        path == "watch" || path.startsWith("watch/") ->
            query.substringAfter("v=", "").substringBefore('&')
        path.startsWith("shorts/") -> path.removePrefix("shorts/").substringBefore('/')
        path.startsWith("embed/") -> path.removePrefix("embed/").substringBefore('/')
        path.startsWith("live/") -> path.removePrefix("live/").substringBefore('/')
        else -> ""
    }

    /**
     * Deja el enlace apuntando al contenido exacto, sin la lista o la radio.
     *
     * Un enlace de YouTube puede traer `list=` o `start_radio=`, y entonces el
     * analizador lo expande a la colección completa (cientos de elementos). Al
     * re-descargar o reparar un archivo del historial interesa ese archivo
     * concreto, no la colección en la que vivía.
     */
    fun singleItemUrl(rawUrl: String): String {
        val url = rawUrl.trim()
        if (url.isBlank()) return url
        val scheme = if (url.contains("://")) url.substringBefore("://") + "://" else ""
        val rest = url.substringAfter("://", url)
        val host = rest.substringBefore('/')
        val normalizedHost = host.lowercase().removePrefix("www.")
        val isYouTube = normalizedHost.endsWith("youtube.com") ||
            normalizedHost.endsWith("youtube-nocookie.com") ||
            normalizedHost == "youtu.be"
        if (!isYouTube) return url

        val rawPath = if (rest.contains('/')) rest.substringAfter('/') else ""
        val basePath = rawPath.substringBefore('?')
        val query = rawPath.substringAfter('?', "")
        val params = query.split('&')
            .filter { it.isNotBlank() }
            .filterNot {
                val name = it.substringBefore('=').lowercase()
                name in playlistParams || name in trackingParams
            }
        return buildString {
            append(scheme).append(host)
            if (basePath.isNotEmpty()) append('/').append(basePath)
            if (params.isNotEmpty()) append('?').append(params.joinToString("&"))
        }
    }

    private val playlistParams = setOf("list", "start_radio", "index", "radio", "playlist")
}
