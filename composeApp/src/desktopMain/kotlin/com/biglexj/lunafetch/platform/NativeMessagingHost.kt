package com.biglexj.lunafetch.platform

import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.system.exitProcess

/**
 * Native Messaging Host mode.
 *
 * Chrome/Edge launches this exe with the path registered in
 * HKCU\Software\Google\Chrome\NativeMessagingHosts\com.biglexj.lunafetch
 *
 * Protocol:
 *   - Read:  4-byte LE length + UTF-8 JSON from stdin
 *   - Write: 4-byte LE length + UTF-8 JSON to stdout
 *
 * This host connects to the already-running Luna Fetch instance on
 * localhost:51234 and forwards the message. If Luna Fetch is not
 * running it responds with an error so the extension can show feedback.
 */
object NativeMessagingHost {

    private const val LUNA_PORT = 51234

    fun run() {
        try {
            val msg = readStdinMessage()
            val response = forwardToLunaFetch(msg)
            writeStdoutMessage(response)
        } catch (e: Exception) {
            writeStdoutMessage("""{"ok":false,"error":"${e.message?.replace("\"", "'")}"}""")
        } finally {
            exitProcess(0)
        }
    }

    // ── stdin → read one Chrome NM message ─────────────────────────────────
    private fun readStdinMessage(): String {
        val din = DataInputStream(System.`in`)
        val lenBytes = ByteArray(4)
        din.readFully(lenBytes)
        val len = ByteBuffer.wrap(lenBytes).order(ByteOrder.LITTLE_ENDIAN).int
        val body = ByteArray(len)
        din.readFully(body)
        return String(body, Charsets.UTF_8)
    }

    // ── stdout → write one Chrome NM message ───────────────────────────────
    private fun writeStdoutMessage(json: String) {
        val body = json.toByteArray(Charsets.UTF_8)
        val lenBytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(body.size).array()
        val out = System.out
        out.write(lenBytes)
        out.write(body)
        out.flush()
    }

    // ── forward message to running Luna Fetch instance ───────────────────────
    private fun forwardToLunaFetch(json: String): String {
        return try {
            Socket("127.0.0.1", LUNA_PORT).use { socket ->
                socket.soTimeout = 3000
                val out = DataOutputStream(socket.getOutputStream())
                val line = json.trimEnd() + "\n"
                out.write(line.toByteArray(Charsets.UTF_8))
                out.flush()
                val response = socket.getInputStream().bufferedReader().readLine()
                response ?: """{"ok":true}"""
            }
        } catch (e: Exception) {
            """{"ok":false,"error":"Luna Fetch no est\u00e1 abierto. \u00c1brelo primero."}"""
        }
    }
}
