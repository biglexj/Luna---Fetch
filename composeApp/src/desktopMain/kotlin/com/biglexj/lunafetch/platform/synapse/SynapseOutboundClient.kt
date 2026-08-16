package com.biglexj.lunafetch.platform.synapse

import com.biglexj.lunafetch.domain.synapse.SynapseEnvelope
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.awt.Desktop
import java.io.File
import java.net.InetAddress
import java.net.Socket
import java.net.URI
import java.net.URLEncoder

/**
 * Cliente Emisor de Acciones Synapse (Aurora Synapse Outbound Client).
 * Permite a Luna Fetch invocar capacidades de otras aplicaciones del ecosistema (ej. Prisma).
 */
object SynapseOutboundClient {

    const val PRISMA_PORT = 49280

    /**
     * Delega la reproducción multimedia a Prisma.
     * 1. Intenta contactar la instancia activa de Prisma vía IPC localhost:49280.
     * 2. Si no responde, despacha el esquema URI nativo `prisma://open?path=...&autoplay=true`.
     * 3. Como respaldo si Prisma no está presente, abre el archivo con el reproductor del sistema.
     */
    fun openMediaInPrisma(filePath: String, mediaCategory: String = "music", autoPlay: Boolean = true): Boolean {
        val file = File(filePath)
        if (!file.exists()) return false
        val canonicalPath = file.canonicalPath

        // 1. Enviar payload estructurado por socket IPC si Prisma ya está corriendo
        val envelope = SynapseEnvelope(
            synapseVersion = "1.0",
            sourceApp = "luna",
            targetApp = "prisma",
            action = "open_media",
            timestampUtc = java.time.Instant.now().toString(),
            payload = buildJsonObject {
                put("file_path", canonicalPath)
                put("media_category", mediaCategory)
                put("auto_play", autoPlay)
            },
        )
        val jsonPayload = SynapseEnvelope.toJson(envelope)

        val ipcSuccess = runCatching {
            Socket(InetAddress.getByName("127.0.0.1"), PRISMA_PORT).use { socket ->
                socket.soTimeout = 2000
                val writer = socket.getOutputStream().bufferedWriter()
                writer.write(jsonPayload + "\n")
                writer.flush()
                true
            }
        }.getOrDefault(false)

        if (ipcSuccess) return true

        // 2. Si no está en ejecución, invocar mediante Deep Link registrado ante el sistema operativo
        val encodedPath = runCatching { URLEncoder.encode(canonicalPath, "UTF-8") }.getOrDefault(canonicalPath)
        val prismaUri = "prisma://open?path=$encodedPath&autoplay=$autoPlay"

        val uriSuccess = runCatching {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI(prismaUri))
                true
            } else false
        }.getOrDefault(false)

        if (uriSuccess) return true

        // 3. Fallback: Abrir con el visor/reproductor nativo del SO
        return runCatching {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(file)
                true
            } else {
                ProcessBuilder("xdg-open", canonicalPath).start()
                true
            }
        }.getOrDefault(false)
    }
}
