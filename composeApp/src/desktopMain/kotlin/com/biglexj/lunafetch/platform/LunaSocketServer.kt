package com.biglexj.lunafetch.platform

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.File
import java.io.InputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder

/**
 * Local TCP / HTTP server that listens on 127.0.0.1:51234.
 *
 * Receives download requests from the Luna Fetch browser extension via HTTP
 * fetch() or local TCP sockets.
 *
 * Endpoints:
 *   POST /analyze
 *   POST /download
 *   POST /download/batch
 *   GET  /download?url=...&format=mp4|mp3
 *   GET  /health
 *   OPTIONS /...  (CORS preflight)
 */
class LunaSocketServer(
    private val onDownloadRequest: (url: String, format: String, quality: String?, cookieFile: String?) -> com.biglexj.lunafetch.domain.EnqueueResult,
    private val onAnalyzeRequest: (suspend (url: String, cookieFile: String?) -> Pair<List<com.biglexj.lunafetch.domain.QualityOption>, List<com.biglexj.lunafetch.domain.QualityOption>>)? = null,
    private val onPendingRequest: () -> Int = { 0 },
    private val port: Int = System.getProperty("lunafetch.port")?.toIntOrNull() ?: PORT,
    private val onDownloadWithOptions: ((url: String, format: String, quality: String?, cookieFile: String?, downloadLyrics: Boolean) -> com.biglexj.lunafetch.domain.EnqueueResult)? = null,
) {
    companion object {
        const val PORT = 51234

        /** Cabeceras CORS: la extensión vive en otro origen y usa `fetch`. */
        private const val CORS_HEADERS = "Access-Control-Allow-Origin: *\r\n" +
            "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n" +
            "Access-Control-Allow-Headers: *\r\n" +
            // Chrome/Edge piden permiso explícito para llegar a la red local
            // desde un origen público (Local Network Access).
            "Access-Control-Allow-Private-Network: true\r\n" +
            "Access-Control-Max-Age: 600\r\n"

        /** Tope de cabeceras: 64 KB bastan de sobra para estas peticiones. */
        private const val MAX_HEADER_BYTES = 64 * 1024

        /** Tope del cuerpo: las cookies de sesión ocupan unos pocos KB. */
        private const val MAX_BODY_BYTES = 4 * 1024 * 1024

        private fun jsonString(json: String, key: String): String? {
            val pattern = Regex(""""$key"\s*:\s*"([^"\\]*(?:\\.[^"\\]*)*)"""")
            return pattern.find(json)?.groupValues?.get(1)
        }

        /** Convierte las secuencias escapadas de una cadena JSON a su valor real. */
        private fun unescapeJson(value: String): String {
            if (!value.contains('\\')) return value
            val out = StringBuilder(value.length)
            var index = 0
            while (index < value.length) {
                val char = value[index]
                if (char != '\\' || index == value.length - 1) {
                    out.append(char)
                    index += 1
                    continue
                }
                when (val escaped = value[index + 1]) {
                    'n' -> out.append('\n')
                    'r' -> out.append('\r')
                    't' -> out.append('\t')
                    'b' -> out.append('\b')
                    'f' -> out.append('\u000C')
                    'u' -> {
                        val hex = value.substring(index + 2, minOf(index + 6, value.length))
                        val code = hex.toIntOrNull(16)
                        if (code != null) {
                            out.append(code.toChar())
                            index += 6
                            continue
                        }
                        out.append(escaped)
                    }
                    else -> out.append(escaped)
                }
                index += 2
            }
            return out.toString()
        }

        /**
         * Lee un arreglo de cadenas del cuerpo JSON: `{"urls":["…","…"]}`.
         * Se usa para el envío por lotes del importador de Suno.
         */
        private fun jsonStringArray(json: String, key: String): List<String> {
            val block = Regex(""""$key"\s*:\s*\[(.*?)]""", RegexOption.DOT_MATCHES_ALL)
                .find(json)?.groupValues?.get(1) ?: return emptyList()
            return Regex(""""([^"\\]*(?:\\.[^"\\]*)*)"""")
                .findAll(block)
                .map { unescapeJson(it.groupValues[1]) }
                .filter { it.isNotBlank() }
                .toList()
        }

        private fun parseQueryParam(queryString: String, paramName: String): String? {
            val pattern = Regex("""[?&]${Regex.escape(paramName)}=([^&]*)""")
            val match = pattern.find(queryString) ?: return null
            return runCatching { URLDecoder.decode(match.groupValues[1], "UTF-8") }.getOrNull()
        }

        private fun saveCookiesFile(cookiesText: String?): String? {
            if (cookiesText.isNullOrBlank()) return null
            return runCatching {
                val file = cookieFile()
                file.parentFile?.mkdirs()
                file.writeText(cookiesText, Charsets.UTF_8)
                file.absolutePath
            }.getOrNull()
        }

        /**
         * Fichero donde se guardan las cookies de sesión que envía la
         * extensión. Se puede redirigir con `-Dlunafetch.cookies=<ruta>` para
         * que las pruebas no toquen el fichero del usuario.
         */
        private fun cookieFile(): File {
            val override = System.getProperty("lunafetch.cookies")
            if (!override.isNullOrBlank()) return File(override)
            return File(System.getProperty("java.io.tmpdir"), "luna_session_cookies.txt")
        }

        /** Escapa un texto para poder meterlo dentro de una cadena JSON. */
        private fun jsonEscape(value: String): String =
            value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " ")

        /** Petición HTTP leída del socket: línea, cabeceras y cuerpo. */
        private data class RawRequest(
            val method: String,
            val path: String,
            val contentLength: Int,
            val body: String,
        )

        /**
         * Lee una petición HTTP completa.
         *
         * El cuerpo se lee en BYTES: `Content-Length` cuenta bytes, así que
         * leerlo como caracteres dejaba el servidor esperando de más cuando el
         * cuerpo incluía un carácter multibyte (por ejemplo una cookie con
         * tilde). En ese caso la conexión moría sin responder y la extensión
         * creía que la aplicación estaba apagada.
         */
        private fun readRequest(input: InputStream): RawRequest {
            val headerBytes = ByteArrayOutputStream()
            var state = 0
            while (true) {
                val byte = input.read()
                if (byte < 0) throw EOFException("Conexión cerrada antes de enviar la cabecera")
                headerBytes.write(byte)
                state = when {
                    byte == '\r'.code && state % 2 == 0 -> state + 1
                    byte == '\n'.code && state % 2 == 1 -> state + 1
                    else -> 0
                }
                if (state == 4) break
                if (headerBytes.size() > MAX_HEADER_BYTES) {
                    throw EOFException("Cabeceras HTTP demasiado grandes")
                }
            }

            val headerText = String(headerBytes.toByteArray(), Charsets.UTF_8)
            val lines = headerText.split("\r\n")
            val requestLine = lines.firstOrNull().orEmpty()
            val method = requestLine.split(" ").getOrNull(0)?.trim().orEmpty().uppercase()
            val path = requestLine.split(" ").getOrNull(1)?.trim().orEmpty()

            var contentLength = 0
            for (line in lines.drop(1)) {
                if (line.startsWith("Content-Length:", ignoreCase = true)) {
                    contentLength = line.substringAfter(":").trim().toIntOrNull() ?: 0
                }
            }
            if (contentLength < 0 || contentLength > MAX_BODY_BYTES) {
                throw EOFException("Cuerpo HTTP fuera de rango: $contentLength bytes")
            }

            val body = ByteArray(contentLength)
            var read = 0
            while (read < contentLength) {
                val count = input.read(body, read, contentLength - read)
                if (count < 0) throw EOFException("Cuerpo HTTP incompleto: $read de $contentLength bytes")
                read += count
            }

            return RawRequest(method, path, contentLength, String(body, Charsets.UTF_8))
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private var serverSocket: ServerSocket? = null

    /** Puerto real en escucha (útil cuando se pide un puerto efímero en pruebas). */
    var boundPort: Int = port
        private set

    fun start() {
        scope.launch {
            runCatching {
                val ss = ServerSocket().apply {
                    reuseAddress = true
                    bind(java.net.InetSocketAddress(java.net.InetAddress.getByName("127.0.0.1"), port), 10)
                }
                serverSocket = ss
                boundPort = ss.localPort
                println("[LunaSocketServer] Servidor para extensión activo en 127.0.0.1:$boundPort")
                while (!ss.isClosed) {
                    val client = runCatching { ss.accept() }.getOrNull() ?: break
                    // Cada conexión se atiende en su propia corrutina para que
                    // varios envíos simultáneos (cola de enlaces) no se bloqueen.
                    scope.launch { runCatching { handleClient(client) } }
                }
            }.onFailure { err ->
                System.err.println("[LunaSocketServer] No se pudo enlazar el puerto $PORT (posible instancia previa en ejecución): ${err.message}")
            }
        }
    }

    fun stop() {
        runCatching { serverSocket?.close() }
    }

    /**
     * Registro de peticiones: deja constancia de lo que llega desde la
     * extensión para poder diagnosticar sin depender de la consola.
     */
    private fun logRequest(line: String) {
        runCatching {
            val file = File(System.getProperty("java.io.tmpdir"), "luna_fetch_server.log")
            if (file.exists() && file.length() > 1024 * 1024) file.delete()
            file.appendText("${java.time.LocalDateTime.now()} $line\n", Charsets.UTF_8)
        }
    }

    private suspend fun handleClient(socket: Socket) {
        socket.use { s ->
            s.soTimeout = 8000
            val input = s.getInputStream()
            val writer = s.getOutputStream().bufferedWriter(Charsets.UTF_8)
            val startedAt = System.currentTimeMillis()

            fun sendJsonResponse(status: String, jsonBody: String) {
                val bytes = jsonBody.toByteArray(Charsets.UTF_8)
                val response = "$status\r\n" +
                               CORS_HEADERS +
                               "Content-Type: application/json; charset=utf-8\r\n" +
                               "Content-Length: ${bytes.size}\r\n\r\n" +
                               jsonBody
                writer.write(response)
                writer.flush()
            }

            var request: RawRequest? = null
            try {
                request = readRequest(input)
                val method = request.method
                val path = request.path

                if (method == "OPTIONS") {
                    writer.write("HTTP/1.1 200 OK\r\n${CORS_HEADERS}Content-Length: 0\r\n\r\n")
                    writer.flush()
                    logRequest("OPTIONS $path -> 204 (preflight)")
                    return
                }

                // Comprobación de vida: la extensión la usa para saber si la
                // aplicación está abierta antes de enviar nada.
                if (path.startsWith("/health")) {
                    val pending = runCatching { onPendingRequest() }.getOrDefault(0)
                    val version = System.getProperty("lunafetch.version").orEmpty()
                    sendJsonResponse(
                        "HTTP/1.1 200 OK",
                        """{"ok":true,"app":"Luna Fetch","version":"${jsonEscape(version)}","pid":${ProcessHandle.current().pid()},"pending":$pending}""",
                    )
                    logRequest("GET $path -> 200 (health, pendientes=$pending)")
                    return
                }

                val bodyString = request.body
                val url: String? = parseQueryParam(path, "url") ?: jsonString(bodyString, "url")
                val format: String = parseQueryParam(path, "format") ?: jsonString(bodyString, "format") ?: "mp4"
                val quality: String? = parseQueryParam(path, "quality") ?: jsonString(bodyString, "quality")
                val downloadLyrics: Boolean = bodyString.contains(""""downloadLyrics":true""") || path.contains("downloadLyrics=true")
                val cookiesRaw = jsonString(bodyString, "cookies")?.replace("\\n", "\n")?.replace("\\t", "\t")
                val cookieFile = saveCookiesFile(cookiesRaw)

                val executeEnqueue: (String) -> com.biglexj.lunafetch.domain.EnqueueResult = { targetUrl ->
                    onDownloadWithOptions?.invoke(targetUrl, format, quality, cookieFile, downloadLyrics)
                        ?: onDownloadRequest(targetUrl, format, quality, cookieFile)
                }

                // Endpoint dedicado de sincronización de cookies desde la extensión.
                // El cuerpo debe ser JSON con { "cookies": "<contenido Netscape>" }.
                if (path.startsWith("/cookies")) {
                    val saved = cookieFile != null
                    sendJsonResponse("HTTP/1.1 200 OK", """{"ok":true,"saved":$saved}""")
                    logRequest("POST $path -> 200 (cookies guardadas=$saved)")
                    return
                }

                // Envío por lotes: una sola petición con N enlaces, en lugar de N
                // idas y venidas al servidor. Lo usa el importador de listas de Suno.
                if (path.startsWith("/download/batch") && method == "POST") {
                    val urls = jsonStringArray(bodyString, "urls")
                    if (urls.isEmpty()) {
                        sendJsonResponse("HTTP/1.1 400 Bad Request", """{"ok":false,"error":"No se recibió ningún enlace"}""")
                        logRequest("POST $path -> 400 (lote vacío)")
                        return
                    }
                    val results = mutableListOf<String>()
                    var queued = 0
                    var skipped = 0
                    var invalid = 0
                    var pending = 0
                    for (candidate in urls) {
                        val result = executeEnqueue(candidate)
                        pending = result.pending
                        val safeUrl = candidate.replace("\\", "\\\\").replace("\"", "\\\"")
                        when (result.outcome) {
                            com.biglexj.lunafetch.domain.EnqueueOutcome.Queued -> {
                                queued += 1
                                results += """{"url":"$safeUrl","queued":true,"pending":${result.pending}}"""
                            }
                            com.biglexj.lunafetch.domain.EnqueueOutcome.DuplicateInQueue -> {
                                skipped += 1
                                results += """{"url":"$safeUrl","queued":false,"reason":"duplicate_queue","pending":${result.pending}}"""
                            }
                            com.biglexj.lunafetch.domain.EnqueueOutcome.AlreadyDownloaded -> {
                                skipped += 1
                                results += """{"url":"$safeUrl","queued":false,"reason":"already_downloaded","pending":${result.pending}}"""
                            }
                            com.biglexj.lunafetch.domain.EnqueueOutcome.InvalidUrl -> {
                                skipped += 1
                                invalid += 1
                                results += """{"url":"$safeUrl","queued":false,"reason":"invalid_url"}"""
                            }
                        }
                    }
                    val body = """{"ok":${invalid == 0},"queued":$queued,"skipped":$skipped,"invalid":$invalid,""" +
                        """"pending":$pending,"results":[${results.joinToString(",")}]}"""
                    sendJsonResponse("HTTP/1.1 200 OK", body)
                    logRequest("POST $path -> 200 (lote: encolados=$queued, omitidos=$skipped)")
                    return
                }

                val analyzeHandler = onAnalyzeRequest
                if (path.startsWith("/analyze") && (method == "GET" || method == "POST") && !url.isNullOrBlank() && analyzeHandler != null) {
                    runCatching { analyzeHandler.invoke(url, cookieFile) }
                        .onSuccess { (videoQualities, audioQualities) ->
                            val vqJson = videoQualities.joinToString(",") { """{"id":"${it.displayName}","label":"${it.displayName}"}""" }
                            val aqJson = audioQualities.joinToString(",") { """{"id":"${it.displayName}","label":"${it.displayName}"}""" }
                            val body = """{"ok":true,"videoQualities":[$vqJson],"audioQualities":[$aqJson]}"""
                            sendJsonResponse("HTTP/1.1 200 OK", body)
                        }
                        .onFailure { error ->
                            val body = """{"ok":false,"error":"${jsonEscape(error.message ?: "No se pudo analizar el enlace")}"}"""
                            sendJsonResponse("HTTP/1.1 400 Bad Request", body)
                        }
                    logRequest("$method $path -> 200 (análisis)")
                    return
                }

                if (!url.isNullOrBlank()) {
                    val result = executeEnqueue(url)
                    when (result.outcome) {
                        com.biglexj.lunafetch.domain.EnqueueOutcome.InvalidUrl ->
                            sendJsonResponse("HTTP/1.1 400 Bad Request", """{"ok":false,"error":"URL no válida"}""")
                        com.biglexj.lunafetch.domain.EnqueueOutcome.DuplicateInQueue ->
                            sendJsonResponse(
                                "HTTP/1.1 200 OK",
                                """{"ok":true,"queued":false,"reason":"duplicate_queue","pending":${result.pending}}""",
                            )
                        com.biglexj.lunafetch.domain.EnqueueOutcome.AlreadyDownloaded ->
                            sendJsonResponse(
                                "HTTP/1.1 200 OK",
                                """{"ok":true,"queued":false,"reason":"already_downloaded","pending":${result.pending}}""",
                            )
                        com.biglexj.lunafetch.domain.EnqueueOutcome.Queued ->
                            sendJsonResponse("HTTP/1.1 200 OK", """{"ok":true,"queued":true,"pending":${result.pending}}""")
                    }
                    logRequest("$method $path -> 200 (${result.outcome}, pendientes=${result.pending})")
                } else {
                    sendJsonResponse("HTTP/1.1 400 Bad Request", """{"ok":false,"error":"URL vacía"}""")
                    logRequest("$method $path -> 400 (URL vacía)")
                }
            } catch (error: Exception) {
                // Antes el fallo se tragaba la conexión y la extensión
                // receiveía un error de red ("no está abierto") sin saber por
                // qué. Ahora se responde con el motivo real.
                val message = error.message ?: error.javaClass.simpleName
                runCatching {
                    sendJsonResponse(
                        "HTTP/1.1 500 Internal Server Error",
                        """{"ok":false,"error":"${jsonEscape(message)}"}""",
                    )
                }
                logRequest("${request?.method ?: "?"} ${request?.path ?: "?"} -> 500 ($message)")
            } finally {
                val elapsed = System.currentTimeMillis() - startedAt
                if (elapsed > 2000) {
                    logRequest("petición lenta: ${request?.path ?: "?"} tardó ${elapsed} ms")
                }
            }
        }
    }
}
