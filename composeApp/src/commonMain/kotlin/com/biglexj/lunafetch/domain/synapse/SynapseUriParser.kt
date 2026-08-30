package com.biglexj.lunafetch.domain.synapse

/**
 * Parser y validador de esquemas URI y mensajes JSON del Aurora Synapse Protocol.
 * Soporta esquemas directos (`luna://`) y canónicos (`aurora-synapse://luna/`).
 * Incorpora reglas de seguridad `PathGuard` para prevenir directory traversal.
 */
object SynapseUriParser {

    /**
     * Procesa una entrada cruda (puede ser un URI, un JSON de envoltorio o una orden simple)
     * y retorna la acción estructurada correspondiente, o null si es inválida.
     */
    fun parse(rawInput: String): SynapseAction? {
        val input = rawInput.trim()
        if (input.isBlank()) return null

        // 1. Mensaje de foco directo
        if (input.equals("FOCUS", ignoreCase = true)) {
            return SynapseAction.Focus
        }

        // 2. Intento de parseo como JSON Envelope (Nivel 4)
        if (input.startsWith("{") && input.endsWith("}")) {
            val envelope = SynapseEnvelope.fromJson(input)
            if (envelope != null) {
                return fromEnvelope(envelope)
            }
        }

        // 3. Intento de parseo como esquema URI (Nivel 1)
        return parseUri(input)
    }

    /**
     * Parsea un URI de tipo:
     * - `luna://download?url=...&type=audio&quality=...&dest=...`
     * - `luna://analyze?url=...`
     * - `luna://open_folder?path=...`
     * - `aurora-synapse://luna/download?...`
     */
    fun parseUri(uriString: String): SynapseAction? {
        val cleanUri = uriString.trim()

        val (scheme, rest) = when {
            cleanUri.startsWith("luna://", ignoreCase = true) -> {
                "luna" to cleanUri.substring("luna://".length)
            }
            cleanUri.startsWith("aurora-synapse://luna/", ignoreCase = true) -> {
                "aurora-synapse" to cleanUri.substring("aurora-synapse://luna/".length)
            }
            cleanUri.startsWith("aurora-synapse://luna", ignoreCase = true) -> {
                val rem = cleanUri.substring("aurora-synapse://luna".length)
                "aurora-synapse" to rem.removePrefix("/")
            }
            else -> return null
        }

        // Si el resto es directamente un URL web (luna://https://... o aurora-synapse://luna/https://...)
        if (rest.startsWith("http://", ignoreCase = true) || rest.startsWith("https://", ignoreCase = true)) {
            return SynapseAction.EnqueueDownload(url = rest)
        }

        val actionPart = rest.substringBefore("?").trim().lowercase()
        val queryPart = if (rest.contains("?")) rest.substringAfter("?") else ""
        val params = parseQueryParams(queryPart)

        return when (actionPart) {
            "", "download", "enqueue", "enqueue_download" -> {
                val url = params["url"] ?: if (rest.startsWith("http://", true) || rest.startsWith("https://", true)) rest else return null
                val type = params["type"] ?: params["media_type"] ?: "video"
                val quality = params["quality"] ?: params["quality_profile"]
                val dest = params["dest"] ?: params["target_directory"]
                val autoPlay = params["autoplay"]?.toBooleanStrictOrNull() ?: false
                val embedMetadata = params["embed_metadata"]?.toBooleanStrictOrNull() ?: true
                val silent = params["silent"]?.toBooleanStrictOrNull() ?: false

                if (!sanitizePathGuard(dest)) return null

                SynapseAction.EnqueueDownload(
                    url = url,
                    mediaType = type,
                    quality = quality,
                    targetDirectory = dest,
                    autoPlayOnFinish = autoPlay,
                    embedMetadata = embedMetadata,
                    silent = silent,
                )
            }
            "analyze", "analyze_url" -> {
                val url = params["url"] ?: return null
                val silent = params["silent"]?.toBooleanStrictOrNull() ?: false
                SynapseAction.AnalyzeUrl(url = url, silent = silent)
            }
            "open_folder", "open-folder" -> {
                val path = params["path"]
                if (!sanitizePathGuard(path)) return null
                SynapseAction.OpenFolder(path = path)
            }
            "focus" -> SynapseAction.Focus
            else -> {
                // Fallback si actionPart contiene un host/protocolo
                if (actionPart.startsWith("http") || actionPart.contains(".com") || actionPart.contains(".org") || actionPart.contains("youtu")) {
                    SynapseAction.EnqueueDownload(url = rest)
                } else null
            }
        }
    }

    private fun fromEnvelope(envelope: SynapseEnvelope): SynapseAction? {
        if (!envelope.targetApp.equals("luna", ignoreCase = true)) return null

        return when (envelope.action.lowercase()) {
            "enqueue_download", "download" -> {
                val url = envelope.payloadString("url") ?: return null
                val type = envelope.payloadString("media_type") ?: envelope.payloadString("type") ?: "video"
                val quality = envelope.payloadString("quality_profile") ?: envelope.payloadString("quality")
                val dest = envelope.payloadString("target_directory") ?: envelope.payloadString("dest")
                val autoPlay = envelope.payloadBoolean("auto_play_on_finish", false)
                val embedMetadata = envelope.payloadBoolean("embed_metadata", true)
                val silent = envelope.payloadBoolean("silent", false)

                if (!sanitizePathGuard(dest)) return null

                SynapseAction.EnqueueDownload(
                    url = url,
                    mediaType = type,
                    quality = quality,
                    targetDirectory = dest,
                    autoPlayOnFinish = autoPlay,
                    embedMetadata = embedMetadata,
                    silent = silent,
                )
            }
            "analyze_url", "analyze" -> {
                val url = envelope.payloadString("url") ?: return null
                val silent = envelope.payloadBoolean("silent", false)
                SynapseAction.AnalyzeUrl(url = url, silent = silent)
            }
            "open_folder" -> {
                val path = envelope.payloadString("path") ?: envelope.payloadString("folder_path")
                if (!sanitizePathGuard(path)) return null
                SynapseAction.OpenFolder(path = path)
            }
            "focus" -> SynapseAction.Focus
            else -> null
        }
    }

    private fun parseQueryParams(queryString: String): Map<String, String> {
        if (queryString.isBlank()) return emptyMap()
        val result = mutableMapOf<String, String>()
        val pairs = queryString.split("&")
        for (pair in pairs) {
            if (pair.isBlank()) continue
            val parts = pair.split("=", limit = 2)
            val key = parts[0].trim()
            val value = if (parts.size > 1) decodeUrl(parts[1]) else ""
            if (key.isNotBlank()) {
                result[key] = value
            }
        }
        return result
    }

    private fun decodeUrl(value: String): String {
        return runCatching {
            // Decodificación simple y segura compatible con KMP
            var decoded = value.replace("+", " ")
            val hexRegex = Regex("%([0-9A-Fa-f]{2})")
            decoded = hexRegex.replace(decoded) { match ->
                val hex = match.groupValues[1]
                hex.toInt(16).toChar().toString()
            }
            decoded
        }.getOrDefault(value)
    }

    /**
     * Regla PathGuard: Rechaza rutas sospechosas de directory traversal o acceso a directorios del sistema.
     */
    fun sanitizePathGuard(path: String?): Boolean {
        if (path.isNullOrBlank()) return true
        val normalized = path.replace('\\', '/')
        if (normalized.contains("../") || normalized.contains("/..") || normalized == "..") {
            return false
        }
        val lower = normalized.lowercase()
        if (lower.startsWith("/etc") || lower.startsWith("/sys") || lower.startsWith("/root") ||
            lower.contains("c:/windows") || lower.contains("c:/winnt") || lower.contains("%systemroot%")
        ) {
            return false
        }
        return true
    }
}
