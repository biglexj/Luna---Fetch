package com.biglexj.lunafetch.domain.synapse.lan

import com.biglexj.lunafetch.domain.DownloadHistoryItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

/**
 * Cliente de Red Local (Aurora Synapse LAN Client).
 * Permite despachar descargas a otros dispositivos descubiertos en la misma Wi-Fi
 * o solicitar sincronización de historial.
 */
object SynapseLanClient {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * Envía una orden de descarga a un dispositivo remoto (ej. "Mandar a descargar a PC").
     */
    suspend fun pushDownload(
        peer: SynapseDevice,
        url: String,
        mediaType: String = "video",
        quality: String? = null,
        sourceDeviceName: String = "Dispositivo Luna",
        autoPlay: Boolean = false,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val endpoint = "http://${peer.ip}:${peer.port}/api/v1/synapse/push-download"
            val req = PushDownloadRequest(
                url = url,
                mediaType = mediaType,
                quality = quality,
                sourceDevice = sourceDeviceName,
                autoPlay = autoPlay,
            )
            val jsonBody = PushDownloadRequest.toJson(req)

            val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 3000
                readTimeout = 4000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            }

            conn.outputStream.use { os ->
                os.write(jsonBody.toByteArray(Charsets.UTF_8))
                os.flush()
            }

            val code = conn.responseCode
            if (code in 200..299) {
                "Enlace enviado con éxito a ${peer.name}."
            } else {
                throw Exception("El dispositivo remoto ${peer.name} respondió con código HTTP $code.")
            }
        }
    }

    /**
     * Sincroniza e intercambia historial de descargas bidireccionalmente con un peer.
     */
    suspend fun syncHistory(
        peer: SynapseDevice,
        localHistory: List<DownloadHistoryItem>,
        sourceDeviceName: String,
    ): Result<List<DownloadHistoryItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val endpoint = "http://${peer.ip}:${peer.port}/api/v1/synapse/sync-history"
            val enrichedHistory = localHistory.map {
                if (it.originDevice.isBlank()) it.copy(originDevice = sourceDeviceName) else it
            }
            val req = LanHistorySyncRequest(
                sourceDevice = sourceDeviceName,
                historyItems = enrichedHistory,
            )
            val jsonBody = LanHistorySyncRequest.toJson(req)

            val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 3000
                readTimeout = 5000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            }

            conn.outputStream.use { os ->
                os.write(jsonBody.toByteArray(Charsets.UTF_8))
                os.flush()
            }

            val code = conn.responseCode
            if (code in 200..299) {
                val respText = conn.inputStream.bufferedReader().use { it.readText() }
                json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(DownloadHistoryItem.serializer()), respText)
            } else {
                throw Exception("Fallo en sincronización: HTTP $code")
            }
        }
    }
}
