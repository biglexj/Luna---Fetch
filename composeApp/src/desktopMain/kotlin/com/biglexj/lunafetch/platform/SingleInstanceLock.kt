package com.biglexj.lunafetch.platform

import com.biglexj.lunafetch.platform.synapse.SynapseDispatcherServer
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import javax.swing.SwingUtilities
import kotlin.concurrent.thread

/**
 * Garantía de Instancia Única y Despacho en Caliente (Single Instance Dispatch Lock).
 * Conforme a `.agents/rules/desktop_app_standards.md` y `Core-Docs/features/aurora-synapse`.
 *
 * Si otra instancia ya está corriendo:
 * - Le transfiere los argumentos recibidos (URI `luna://`, JSON `--synapse-action` o foco).
 * - Cierra la instancia secundaria inmediatamente.
 * - Incluye bypass garantizado en modo desarrollo (isDevMode).
 */
object SingleInstanceLock {
    private const val LOCK_PORT = 51235
    private var serverSocket: ServerSocket? = null

    fun isDevMode(): Boolean {
        return System.getProperty("lunafetch.dev") == "true" ||
                System.getProperty("idea.active") != null
    }

    fun acquireOrTransfer(args: Array<String> = emptyArray()): Boolean {
        if (isDevMode()) {
            println("[SingleInstanceLock] Modo desarrollo activo. Bypass de bloqueo de instancia única permitido.")
            runCatching {
                serverSocket = ServerSocket(LOCK_PORT, 10, InetAddress.getByName("127.0.0.1"))
            }
            return true
        }

        return try {
            serverSocket = ServerSocket(LOCK_PORT, 10, InetAddress.getByName("127.0.0.1"))
            true
        } catch (e: Exception) {
            // El bloqueo pertenece a otra instancia activa en producción -> transferir orden
            val payload = extractPayloadFromArgs(args)
            transferToExistingInstance(payload)
            false
        }
    }

    private fun extractPayloadFromArgs(args: Array<String>): String {
        val directUri = args.firstOrNull { it.startsWith("luna://", true) || it.startsWith("aurora-synapse://", true) }
        if (directUri != null) return directUri

        val downloadUrl = args.firstOrNull { it.startsWith("--download-url=") }?.removePrefix("--download-url=")
        if (!downloadUrl.isNullOrBlank()) return "luna://download?url=$downloadUrl"

        val synapseActionIndex = args.indexOf("--synapse-action")
        if (synapseActionIndex != -1 && synapseActionIndex + 1 < args.size) {
            return args[synapseActionIndex + 1]
        }

        return "FOCUS"
    }

    private fun transferToExistingInstance(payload: String) {
        // Intentar primero por el puerto oficial Synapse (49282), luego por el lock port (51235)
        val sent = SynapseDispatcherServer.transmitToRunningInstance(payload, SynapseDispatcherServer.SYNAPSE_PORT)
        if (!sent) {
            runCatching {
                Socket(InetAddress.getByName("127.0.0.1"), LOCK_PORT).use { socket ->
                    socket.soTimeout = 2500
                    val writer = socket.getOutputStream().bufferedWriter()
                    writer.write(payload + "\n")
                    writer.flush()
                }
            }
        }
    }

    fun listenForLegacyRequests(onPayloadReceived: (String) -> Unit) {
        val ss = serverSocket ?: return
        thread(isDaemon = true, name = "LunaSingleInstanceLegacyListener") {
            while (!ss.isClosed) {
                runCatching {
                    val client = ss.accept()
                    client.use { s ->
                        s.soTimeout = 2000
                        val line = s.getInputStream().bufferedReader().readLine()
                        if (!line.isNullOrBlank()) {
                            SwingUtilities.invokeLater {
                                onPayloadReceived(line)
                            }
                        }
                    }
                }
            }
        }
    }

    fun release() {
        runCatching {
            serverSocket?.close()
            serverSocket = null
        }
    }
}

