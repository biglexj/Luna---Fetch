package com.biglexj.lunafetch.platform

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
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
 *   GET /download?url=...&format=mp4|mp3
 *   OPTIONS /...  (CORS preflight)
 */
class LunaSocketServer(
    private val onDownloadRequest: (url: String, format: String, quality: String?, cookieFile: String?) -> Unit,
    private val onAnalyzeRequest: (suspend (url: String, cookieFile: String?) -> Pair<List<com.biglexj.lunafetch.domain.QualityOption>, List<com.biglexj.lunafetch.domain.QualityOption>>)? = null,
) {
    companion object {
        const val PORT = 51234

        private fun jsonString(json: String, key: String): String? {
            val pattern = Regex(""""$key"\s*:\s*"([^"\\]*(?:\\.[^"\\]*)*)"""")
            return pattern.find(json)?.groupValues?.get(1)
        }

        private fun parseQueryParam(queryString: String, paramName: String): String? {
            val pattern = Regex("""[?&]${Regex.escape(paramName)}=([^&]*)""")
            val match = pattern.find(queryString) ?: return null
            return runCatching { URLDecoder.decode(match.groupValues[1], "UTF-8") }.getOrNull()
        }

        private fun saveCookiesFile(cookiesText: String?): String? {
            if (cookiesText.isNullOrBlank()) return null
            return runCatching {
                val file = File(System.getProperty("java.io.tmpdir"), "luna_session_cookies.txt")
                file.writeText(cookiesText, Charsets.UTF_8)
                file.absolutePath
            }.getOrNull()
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private var serverSocket: ServerSocket? = null

    fun start() {
        scope.launch {
            runCatching {
                val ss = ServerSocket(PORT, 10, java.net.InetAddress.getByName("127.0.0.1"))
                serverSocket = ss
                while (!ss.isClosed) {
                    runCatching { handleClient(ss.accept()) }
                }
            }
        }
    }

    fun stop() {
        runCatching { serverSocket?.close() }
    }

    private fun handleClient(socket: Socket) {
        socket.use { s ->
            s.soTimeout = 8000
            val reader = s.getInputStream().bufferedReader()
            val writer = s.getOutputStream().bufferedWriter()
            val firstLine = reader.readLine() ?: return

            val corsHeaders = "Access-Control-Allow-Origin: *\r\n" +
                              "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n" +
                              "Access-Control-Allow-Headers: *\r\n"

            if (firstLine.startsWith("OPTIONS")) {
                writer.write("HTTP/1.1 200 OK\r\n${corsHeaders}\r\n")
                writer.flush()
                return
            }

            var contentLength = 0
            var line: String? = reader.readLine()
            while (!line.isNullOrBlank()) {
                if (line.startsWith("Content-Length:", ignoreCase = true)) {
                    contentLength = line.substringAfter(":").trim().toIntOrNull() ?: 0
                }
                line = reader.readLine()
            }

            var bodyString = ""
            if (contentLength > 0) {
                val charBuffer = CharArray(contentLength)
                var readTotal = 0
                while (readTotal < contentLength) {
                    val read = reader.read(charBuffer, readTotal, contentLength - readTotal)
                    if (read <= 0) break
                    readTotal += read
                }
                bodyString = String(charBuffer, 0, readTotal)
            }

            val path = firstLine.split(" ").getOrNull(1) ?: ""
            var url: String? = parseQueryParam(path, "url") ?: jsonString(bodyString, "url")
            var format: String = parseQueryParam(path, "format") ?: jsonString(bodyString, "format") ?: "mp4"
            var quality: String? = parseQueryParam(path, "quality") ?: jsonString(bodyString, "quality")
            val cookiesRaw = jsonString(bodyString, "cookies")?.replace("\\n", "\n")?.replace("\\t", "\t")
            val cookieFile = saveCookiesFile(cookiesRaw)

            fun sendJsonResponse(status: String, jsonBody: String) {
                val bytes = jsonBody.toByteArray(Charsets.UTF_8)
                val response = "$status\r\n" +
                               corsHeaders +
                               "Content-Type: application/json\r\n" +
                               "Content-Length: ${bytes.size}\r\n\r\n" +
                               jsonBody
                writer.write(response)
                writer.flush()
            }

            if (firstLine.startsWith("GET ") || firstLine.startsWith("POST ")) {
                if (path.startsWith("/analyze") && !url.isNullOrBlank() && onAnalyzeRequest != null) {
                    kotlinx.coroutines.runBlocking {
                        runCatching { onAnalyzeRequest.invoke(url!!, cookieFile) }
                            .onSuccess { (videoQualities, audioQualities) ->
                                val vqJson = videoQualities.joinToString(",") { """{"id":"${it.displayName}","label":"${it.displayName}"}""" }
                                val aqJson = audioQualities.joinToString(",") { """{"id":"${it.displayName}","label":"${it.displayName}"}""" }
                                val body = """{"ok":true,"videoQualities":[$vqJson],"audioQualities":[$aqJson]}"""
                                sendJsonResponse("HTTP/1.1 200 OK", body)
                            }
                            .onFailure { error ->
                                val body = """{"ok":false,"error":"${error.message ?: "No se pudo analizar el enlace"}"}"""
                                sendJsonResponse("HTTP/1.1 400 Bad Request", body)
                            }
                    }
                    return
                }

                if (!url.isNullOrBlank()) {
                    onDownloadRequest(url, format, quality, cookieFile)
                    sendJsonResponse("HTTP/1.1 200 OK", """{"ok":true}""")
                } else {
                    sendJsonResponse("HTTP/1.1 400 Bad Request", """{"ok":false,"error":"URL vacía"}""")
                }
            } else {
                if (!url.isNullOrBlank()) {
                    onDownloadRequest(url, format, quality, cookieFile)
                    sendJsonResponse("HTTP/1.1 200 OK", """{"ok":true}""")
                } else {
                    sendJsonResponse("HTTP/1.1 400 Bad Request", """{"ok":false,"error":"URL vacía"}""")
                }
            }
        }
    }
}
