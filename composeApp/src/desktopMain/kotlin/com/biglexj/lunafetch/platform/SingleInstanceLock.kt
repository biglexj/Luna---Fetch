package com.biglexj.lunafetch.platform

import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/**
 * Garantía de Instancia Única (Single Instance Lock) para Luna Fetch Desktop JVM.
 * Estandarizado según `.agents/rules/desktop_app_standards.md` (Regla 1).
 * Previene la duplicación de procesos y la proliferación de iconos duplicados en la bandeja en producción.
 * Incluye bypass obligatorio en modo desarrollo (isDev) para no interferir con ejecuciones de `./gradlew run` o IDEs.
 */
object SingleInstanceLock {
    private const val LOCK_PORT = 51235
    private var serverSocket: ServerSocket? = null

    fun acquireOrBringToFront(): Boolean {
        // Bypass obligatorio en modo desarrollo (isDev) según desktop_app_standards.md
        val execPath = ProcessHandle.current().info().command().orElse("").lowercase()
        val isInstalledExe = (execPath.endsWith("lunafetch.exe") || execPath.endsWith("luna fetch.exe")) &&
                (execPath.contains("appdata") || execPath.contains("program files"))

        val isDev = !isInstalledExe ||
                System.getProperty("lunafetch.dev") == "true" ||
                System.getProperty("idea.active") != null ||
                System.getProperty("sun.java.command")?.let {
                    it.contains("MainKt", ignoreCase = true) ||
                    it.contains("Gradle", ignoreCase = true) ||
                    it.contains("idea", ignoreCase = true)
                } == true

        if (isDev) {
            return true
        }

        return try {
            serverSocket = ServerSocket(LOCK_PORT, 10, InetAddress.getByName("127.0.0.1"))
            true
        } catch (e: Exception) {
            // El bloqueo pertenece a otra instancia activa en producción
            notifyExistingInstance()
            false
        }
    }

    private fun notifyExistingInstance() {
        runCatching {
            Socket(InetAddress.getByName("127.0.0.1"), LOCK_PORT).use { socket ->
                socket.soTimeout = 2000
                val writer = socket.getOutputStream().bufferedWriter()
                writer.write("FOCUS\n")
                writer.flush()
            }
        }
    }

    fun listenForFocusRequests(onFocusRequested: () -> Unit) {
        val ss = serverSocket ?: return
        thread(isDaemon = true, name = "LunaSingleInstanceLockListener") {
            while (!ss.isClosed) {
                runCatching {
                    val client = ss.accept()
                    client.use { s ->
                        s.soTimeout = 2000
                        val line = s.getInputStream().bufferedReader().readLine()
                        if (line == "FOCUS") {
                            onFocusRequested()
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
