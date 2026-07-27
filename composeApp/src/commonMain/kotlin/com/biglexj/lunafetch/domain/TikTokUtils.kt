package com.biglexj.lunafetch.domain

object TikTokUtils {
    private val TikTokDomains = listOf(
        "tiktok.com",
        "vt.tiktok.com",
        "vm.tiktok.com",
        "v.tiktok.com",
        "m.tiktok.com",
    )

    private val UrlRegex = Regex("""https?://[^\s<>"{}|\\^`]+""", RegexOption.IGNORE_CASE)

    /**
     * Extrae y limpia una URL desde un texto plano (por ejemplo, el texto generado al compartir desde la app de TikTok).
     * Elimina parámetros de seguimiento como `is_from_webapp`, `sender_device`, `_r`, `_t`, etc.
     */
    fun sanitizeUrl(input: String): String {
        val extracted = UrlRegex.find(input.trim())?.value ?: input.trim()
        if (!isTikTokUrl(extracted)) {
            return extracted
        }
        val lower = extracted.lowercase()
        if (lower.contains("vt.tiktok.com/") || lower.contains("vm.tiktok.com/") || lower.contains("v.tiktok.com/")) {
            return extracted
        }
        return cleanTrackingParameters(extracted)
    }

    /**
     * Verifica si una URL dada pertenece al dominio de TikTok.
     */
    fun isTikTokUrl(url: String): Boolean {
        val lower = url.trim().lowercase()
        return TikTokDomains.any { domain ->
            lower.contains("://$domain/") || lower.contains(".$domain/") || lower == "https://$domain" || lower == "http://$domain"
        }
    }

    private fun cleanTrackingParameters(url: String): String {
        val parts = url.split("?", limit = 2)
        if (parts.size < 2) return url

        val baseUrl = parts[0]
        val queryParams = parts[1].split("&").filterNot { param ->
            val key = param.substringBefore("=").lowercase()
            key in trackingKeys
        }

        return if (queryParams.isEmpty()) {
            baseUrl
        } else {
            "$baseUrl?${queryParams.joinToString("&")}"
        }
    }

    private val trackingKeys = setOf(
        "is_from_webapp",
        "sender_device",
        "sender_web_id",
        "share_item_id",
        "share_link_id",
        "share_app_id",
        "share_author_id",
        "checksum",
        "_r",
        "_t",
        "timestamp",
    )
}
