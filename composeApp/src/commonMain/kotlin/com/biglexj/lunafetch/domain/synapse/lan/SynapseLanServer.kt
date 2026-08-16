package com.biglexj.lunafetch.domain.synapse.lan

import com.biglexj.lunafetch.domain.DownloadHistoryItem
import kotlinx.serialization.json.Json
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/**
 * Servidor HTTP/JSON Ligero de Red Local (Aurora Synapse LAN Server).
 * Puerto reservado: 49288 (TCP).
 *
 * Permite recibir solicitudes remotas de descarga (ej. "Mandar a descargar a PC")
 * y sincronizar historiales entre dispositivos conectados a la misma Wi-Fi.
 */
class SynapseLanServer(
    val port: Int = LAN_PORT,
    private val onRemoteDownloadReceived: (PushDownloadRequest) -> Unit,
    private val onHistorySyncReceived: (List<DownloadHistoryItem>) -> List<DownloadHistoryItem>,
    private val onPeerDiscovered: (SynapseDevice) -> Unit = {},
) {
    companion object {
        const val LAN_PORT = 49288
        private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }
    }

    private var serverSocket: ServerSocket? = null
    private var isRunning = false

    fun start(): Boolean {
        if (isRunning) return true
        return runCatching {
            val ss = ServerSocket().apply {
                reuseAddress = true
                bind(java.net.InetSocketAddress(InetAddress.getByName("0.0.0.0"), port), 25)
            }
            serverSocket = ss
            isRunning = true

            thread(isDaemon = true, name = "LunaSynapseLanServerListener") {
                while (isRunning && !ss.isClosed) {
                    runCatching {
                        val client = ss.accept()
                        handleClient(client)
                    }
                }
            }
            true
        }.getOrDefault(false)
    }

    fun stop() {
        isRunning = false
        runCatching { serverSocket?.close() }
        serverSocket = null
    }

    private fun handleClient(socket: Socket) {
        thread(isDaemon = true, name = "LunaSynapseLanWorker") {
            socket.use { s ->
                s.soTimeout = 5000
                val reader = s.getInputStream().bufferedReader()
                val writer = s.getOutputStream().bufferedWriter()

                val requestLine = reader.readLine() ?: return@use
                val parts = requestLine.split(" ")
                if (parts.size < 2) return@use

                val method = parts[0].uppercase()
                val path = parts[1]

                // Leer headers
                var contentLength = 0
                var line: String? = reader.readLine()
                while (!line.isNullOrBlank()) {
                    if (line.startsWith("Content-Length:", ignoreCase = true)) {
                        contentLength = line.substringAfter(":").trim().toIntOrNull() ?: 0
                    }
                    line = reader.readLine()
                }

                // Leer body si existe
                val body = if (contentLength > 0) {
                    val buffer = CharArray(contentLength)
                    var read = 0
                    while (read < contentLength) {
                        val r = reader.read(buffer, read, contentLength - read)
                        if (r == -1) break
                        read += r
                    }
                    String(buffer, 0, read)
                } else ""

                val clientIp = socket.inetAddress?.hostAddress.orEmpty().substringBefore("%")

                when {
                    method == "GET" && path.startsWith("/api/v1/synapse/ping") -> {
                        val source = path.substringAfter("source=", "").substringBefore("&").takeIf { it.isNotBlank() }
                            ?.let { runCatching { java.net.URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }
                            ?: "Dispositivo Luna"
                        val os = path.substringAfter("os=", "").substringBefore("&").ifBlank { "mobile" }
                        val peer = SynapseDevice(
                            id = "luna_${source.hashCode().toString(16)}",
                            name = source,
                            type = if (os == "android") "mobile" else "desktop",
                            ip = clientIp,
                            port = LAN_PORT,
                            os = os,
                            lastSeenMs = System.currentTimeMillis(),
                        )
                        onPeerDiscovered(peer)
                        sendJsonResponse(writer, 200, LanGenericResponse(true, "PONG"))
                    }
                    method == "POST" && path.startsWith("/api/v1/synapse/push-download") -> {
                        val req = PushDownloadRequest.fromJson(body)
                        if (req != null) {
                            if (req.sourceDevice.isNotBlank() && clientIp.isNotBlank()) {
                                onPeerDiscovered(
                                    SynapseDevice(
                                        id = "luna_${req.sourceDevice.hashCode().toString(16)}",
                                        name = req.sourceDevice,
                                        type = "mobile",
                                        ip = clientIp,
                                        port = LAN_PORT,
                                        os = "android",
                                        lastSeenMs = System.currentTimeMillis(),
                                    )
                                )
                            }
                            onRemoteDownloadReceived(req)
                            sendJsonResponse(writer, 200, LanGenericResponse(true, "Descarga recibida correctamente en este dispositivo."))
                        } else {
                            sendJsonResponse(writer, 400, LanGenericResponse(false, "Cuerpo JSON de descarga inválido."))
                        }
                    }
                    method == "POST" && path.startsWith("/api/v1/synapse/sync-history") -> {
                        val req = LanHistorySyncRequest.fromJson(body)
                        if (req != null) {
                            if (req.sourceDevice.isNotBlank() && clientIp.isNotBlank()) {
                                onPeerDiscovered(
                                    SynapseDevice(
                                        id = "luna_${req.sourceDevice.hashCode().toString(16)}",
                                        name = req.sourceDevice,
                                        type = "mobile",
                                        ip = clientIp,
                                        port = LAN_PORT,
                                        os = "android",
                                        lastSeenMs = System.currentTimeMillis(),
                                    )
                                )
                            }
                            val merged = onHistorySyncReceived(req.historyItems)
                            val respJson = json.encodeToString(kotlinx.serialization.builtins.ListSerializer(DownloadHistoryItem.serializer()), merged)
                            sendRawResponse(writer, 200, "application/json", respJson)
                        } else {
                            sendJsonResponse(writer, 400, LanGenericResponse(false, "Solicitud de sincronización inválida."))
                        }
                    }
                    method == "GET" && path.startsWith("/api/v1/synapse/status") -> {
                        sendJsonResponse(writer, 200, LanGenericResponse(true, "Nodo Luna Synapse Activo y Operativo."))
                    }
                    method == "OPTIONS" -> {
                        sendCorsHeaders(writer)
                    }
                    else -> {
                        sendJsonResponse(writer, 404, LanGenericResponse(false, "Endpoint no encontrado."))
                    }
                }
            }
        }
    }

    private fun sendJsonResponse(writer: java.io.BufferedWriter, statusCode: Int, data: LanGenericResponse) {
        val jsonString = LanGenericResponse.toJson(data)
        sendRawResponse(writer, statusCode, "application/json", jsonString)
    }

    private fun sendRawResponse(writer: java.io.BufferedWriter, statusCode: Int, contentType: String, content: String) {
        val statusText = if (statusCode == 200) "OK" else if (statusCode == 400) "Bad Request" else "Not Found"
        val bytes = content.toByteArray(Charsets.UTF_8)
        writer.write("HTTP/1.1 $statusCode $statusText\r\n")
        writer.write("Content-Type: $contentType; charset=UTF-8\r\n")
        writer.write("Content-Length: ${bytes.size}\r\n")
        writer.write("Access-Control-Allow-Origin: *\r\n")
        writer.write("Connection: close\r\n\r\n")
        writer.write(content)
        writer.flush()
    }

    private fun sendCorsHeaders(writer: java.io.BufferedWriter) {
        writer.write("HTTP/1.1 200 OK\r\n")
        writer.write("Access-Control-Allow-Origin: *\r\n")
        writer.write("Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n")
        writer.write("Access-Control-Allow-Headers: Content-Type\r\n")
        writer.write("Content-Length: 0\r\n\r\n")
        writer.flush()
    }
}
