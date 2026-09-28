package com.biglexj.lunafetch.domain

data class NetscapeCookie(
    val domain: String,
    val includeSubdomains: Boolean = true,
    val path: String = "/",
    val isSecure: Boolean = false,
    val expiration: Long = 2147483647L,
    val name: String,
    val value: String,
) {
    fun toNetscapeLine(): String =
        "${if (includeSubdomains && !domain.startsWith(".")) ".$domain" else domain}\t" +
            "${if (includeSubdomains) "TRUE" else "FALSE"}\t" +
            "$path\t" +
            "${if (isSecure) "TRUE" else "FALSE"}\t" +
            "$expiration\t" +
            "$name\t" +
            value
}

object NetscapeCookieJar {
    private const val HEADER = "# Netscape HTTP Cookie File"

    fun parse(content: String): List<NetscapeCookie> {
        val list = mutableListOf<NetscapeCookie>()
        content.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isBlank() || (trimmed.startsWith("#") && !trimmed.startsWith("#HttpOnly_"))) {
                return@forEach
            }
            val lineToParse = if (trimmed.startsWith("#HttpOnly_")) {
                trimmed.removePrefix("#HttpOnly_")
            } else {
                trimmed
            }
            val parts = lineToParse.split('\t')
            if (parts.size >= 7) {
                val domain = parts[0].trim()
                val includeSub = parts[1].trim().equals("TRUE", ignoreCase = true)
                val path = parts[2].trim().ifBlank { "/" }
                val isSecure = parts[3].trim().equals("TRUE", ignoreCase = true)
                val expires = parts[4].trim().toLongOrNull() ?: 2147483647L
                val name = parts[5].trim()
                val value = parts[6].trim()
                if (domain.isNotBlank() && name.isNotBlank()) {
                    list.add(
                        NetscapeCookie(
                            domain = domain,
                            includeSubdomains = includeSub,
                            path = path,
                            isSecure = isSecure,
                            expiration = expires,
                            name = name,
                            value = value,
                        )
                    )
                }
            }
        }
        return list
    }

    fun parseHeaderString(domain: String, cookieHeader: String): List<NetscapeCookie> {
        val cleanDomain = domain.removePrefix("https://").removePrefix("http://")
            .substringBefore('/')
            .substringBefore(':')
            .trim()
        if (cleanDomain.isBlank()) return emptyList()

        val dotDomain = if (cleanDomain.startsWith(".")) cleanDomain else ".$cleanDomain"
        val expiry = 2147483647L // Max 32-bit timestamp
        val result = mutableListOf<NetscapeCookie>()
        val pairs = cookieHeader.split(';')
        for (pair in pairs) {
            val trimmed = pair.trim()
            if (trimmed.isEmpty()) continue
            val equalsIndex = trimmed.indexOf('=')
            if (equalsIndex > 0) {
                val name = trimmed.substring(0, equalsIndex).trim()
                val value = trimmed.substring(equalsIndex + 1).trim()
                if (name.isNotBlank()) {
                    result.add(
                        NetscapeCookie(
                            domain = dotDomain,
                            includeSubdomains = true,
                            path = "/",
                            isSecure = false,
                            expiration = expiry,
                            name = name,
                            value = value,
                        )
                    )
                }
            }
        }
        return result
    }

    fun merge(existing: List<NetscapeCookie>, updates: List<NetscapeCookie>): List<NetscapeCookie> {
        val map = linkedMapOf<String, NetscapeCookie>()
        for (c in existing) {
            val key = "${c.domain.lowercase()}|${c.path}|${c.name}"
            map[key] = c
        }
        for (c in updates) {
            val key = "${c.domain.lowercase()}|${c.path}|${c.name}"
            map[key] = c
        }
        return map.values.toList()
    }

    fun serialize(cookies: List<NetscapeCookie>): String = buildString {
        appendLine(HEADER)
        appendLine("# This file is managed by Luna Fetch for yt-dlp cookie authentication.")
        cookies.forEach { appendLine(it.toNetscapeLine()) }
    }

    fun defaultClearanceForUrl(url: String): List<NetscapeCookie> {
        val lower = url.lowercase()
        val list = mutableListOf<NetscapeCookie>()
        val farExpiry = 2147483647L

        fun add(domain: String, name: String, value: String) {
            list.add(
                NetscapeCookie(
                    domain = if (domain.startsWith(".")) domain else ".$domain",
                    includeSubdomains = true,
                    path = "/",
                    isSecure = false,
                    expiration = farExpiry,
                    name = name,
                    value = value,
                )
            )
        }

        if (lower.contains("pornhub") || lower.contains("phncdn")) {
            val phDomains = listOf("pornhub.com", "www.pornhub.com", "pornhubpremium.com")
            for (d in phDomains) {
                add(d, "platform", "pc")
                add(d, "age_verified", "1")
                add(d, "accessAgeDisclaimerPH", "1")
                add(d, "hasVisited", "1")
                add(d, "cookiesBanner", "1")
                add(d, "ua", "7675d59b5e84e0a878ee6f0a97f9056f")
            }
        }
        if (lower.contains("redtube")) {
            add("redtube.com", "platform", "pc")
            add("redtube.com", "age_verified", "1")
            add("redtube.com", "accessAgeDisclaimerPH", "1")
            add("redtube.com", "ua", "7675d59b5e84e0a878ee6f0a97f9056f")
        }
        if (lower.contains("youporn")) {
            add("youporn.com", "platform", "pc")
            add("youporn.com", "age_verified", "1")
            add("youporn.com", "accessAgeDisclaimerPH", "1")
            add("youporn.com", "ua", "7675d59b5e84e0a878ee6f0a97f9056f")
        }
        if (lower.contains("xvideos")) {
            add("xvideos.com", "age_verified", "1")
            add("xvideos.com", "has_verified_age", "1")
        }
        if (lower.contains("xnxx")) {
            add("xnxx.com", "age_verified", "1")
            add("xnxx.com", "has_verified_age", "1")
        }
        if (lower.contains("spankbang")) {
            add("spankbang.com", "age_verified", "1")
            add("spankbang.com", "country_verified", "1")
        }
        if (lower.contains("eporner")) {
            add("eporner.com", "age_verified", "1")
        }
        return list
    }
}
