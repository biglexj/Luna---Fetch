package com.biglexj.lunafetch.platform.synapse

import com.biglexj.lunafetch.domain.synapse.SynapseAction
import com.biglexj.lunafetch.domain.synapse.SynapseUriParser
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import javax.swing.SwingUtilities
import kotlin.concurrent.thread

/**
 * Servidor de Despacho en Instancia Única (Aurora Synapse Dispatcher Server).
 * Puerto oficial asignado a Luna Fetch: 49282 (localhost).
 *
 * Recibe peticiones IPC locales de:
 * 1. Deep Links `luna://...` despachados desde el explorador o navegador.
 * 2. Cargas útiles JSON de agentes o scripts CLI (`--synapse-action`).
 * 3. Enlaces desde otras apps (Prisma, Ely Intelligence, etc.).
 * 4. Peticiones de foco de instancias secundarias.
 */
class SynapseDispatcherServer(
    private val port: Int = SYNAPSE_PORT,
    private val onActionReceived: (SynapseAction) -> Unit,
    private val onBringToFront: () -> Unit,
) {
    companion object {
        const val SYNAPSE_PORT = 49282
        const val LEGACY_LOCK_PORT = 51235

        /**
         * Intenta transmitir una acción a una instancia viva ya en ejecución.
         * Retorna true si la instancia viva respondió y aceptó el mensaje.
         */
        fun transmitToRunningInstance(payload: String, targetPort: Int = SYNAPSE_PORT): Boolean {
            return runCatching {
                Socket(InetAddress.getByName("127.0.0.1"), targetPort).use { socket ->
                    socket.soTimeout = 3000
                    val writer = socket.getOutputStream().bufferedWriter()
                    writer.write(payload.trim() + "\n")
                    writer.flush()
                    true
                }
            }.getOrDefault(false)
        }
    }

    private var serverSocket: ServerSocket? = null

    fun startListening(): Boolean {
        return runCatching {
            val ss = ServerSocket().apply {
                reuseAddress = true
                bind(java.net.InetSocketAddress(InetAddress.getByName("127.0.0.1"), port), 20)
            }
            serverSocket = ss

            thread(isDaemon = true, name = "LunaSynapseDispatcherListener") {
                while (!ss.isClosed) {
                    runCatching {
                        val client = ss.accept()
                        handleClient(client)
                    }
                }
            }
            true
        }.getOrDefault(false)
    }

    private fun handleClient(client: Socket) {
        thread(isDaemon = true, name = "LunaSynapseClientWorker") {
            client.use { s ->
                s.soTimeout = 3000
                val reader = s.getInputStream().bufferedReader()
                val line = reader.readLine() ?: return@use
                val action = SynapseUriParser.parse(line) ?: SynapseAction.Focus

                SwingUtilities.invokeLater {
                    when (action) {
                        is SynapseAction.Focus -> {
                            onBringToFront()
                        }
                        is SynapseAction.EnqueueDownload -> {
                            if (!action.silent) onBringToFront()
                            onActionReceived(action)
                        }
                        is SynapseAction.AnalyzeUrl -> {
                            if (!action.silent) onBringToFront()
                            onActionReceived(action)
                        }
                        is SynapseAction.OpenFolder -> {
                            onActionReceived(action)
                        }
                        is SynapseAction.PlayInPrisma -> {
                            onActionReceived(action)
                        }
                    }
                }
            }
        }
    }

    fun stop() {
        runCatching {
            serverSocket?.close()
            serverSocket = null
        }
    }
}
