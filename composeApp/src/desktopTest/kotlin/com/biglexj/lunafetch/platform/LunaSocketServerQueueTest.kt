package com.biglexj.lunafetch.platform

import com.biglexj.lunafetch.domain.DownloadEngine
import com.biglexj.lunafetch.domain.DownloadPhase
import com.biglexj.lunafetch.domain.DownloadProgress
import com.biglexj.lunafetch.domain.DownloadRequest
import com.biglexj.lunafetch.domain.DownloadResult
import com.biglexj.lunafetch.domain.LunaFetchPresenter
import com.biglexj.lunafetch.domain.PlatformBindings
import com.biglexj.lunafetch.domain.QueueItemStatus
import com.biglexj.lunafetch.domain.UpdateRelease
import com.biglexj.lunafetch.domain.VideoInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.net.Socket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Flujo real de la extensión: varias peticiones /download seguidas deben
 * encolarse y descargarse una detrás de otra, sin que una cancele a la otra.
 */
class LunaSocketServerQueueTest {

    private class RecordingEngine(private val delayMs: Long = 30) : DownloadEngine {
        val downloadedUrls = java.util.Collections.synchronizedList(mutableListOf<String>())
        var maxConcurrent = 0
        private var active = 0

        override suspend fun analyze(url: String): VideoInfo = VideoInfo(
            url = url,
            title = "Video ${url.substringAfterLast('/')}",
            uploader = "Canal",
            durationSeconds = 30.0,
            thumbnailUrl = "",
            maxHeight = 1080,
        )

        override suspend fun download(
            request: DownloadRequest,
            onProgress: (DownloadProgress) -> Unit,
            onLog: (String) -> Unit,
        ): DownloadResult {
            synchronized(this) {
                active++
                if (active > maxConcurrent) maxConcurrent = active
            }
            try {
                delay(delayMs)
                downloadedUrls.add(request.url)
                onProgress(DownloadProgress(100.0, phase = DownloadPhase.Completed))
                return DownloadResult(outputPaths = listOf("C:/Downloads/${request.url.substringAfterLast('/')}.mp4"))
            } finally {
                synchronized(this) { active-- }
            }
        }

        override fun cancel() {}
    }

    private class FakePlatform(override val engine: DownloadEngine) : PlatformBindings {
        override val defaultDestination: String = "C:/Downloads"
        override suspend fun checkUpdate(): UpdateRelease? = null
        override suspend fun chooseDestination(current: String): String? = null
        override fun destinationLabel(destination: String): String = destination
        override fun rememberDestination(destination: String) {}
        override fun openOutput(path: String) {}
    }

    private fun httpGet(port: Int, path: String): String {
        Socket("127.0.0.1", port).use { socket ->
            socket.soTimeout = 5000
            val request = "GET $path HTTP/1.1\r\nHost: 127.0.0.1:$port\r\nConnection: close\r\n\r\n"
            socket.getOutputStream().write(request.toByteArray(Charsets.UTF_8))
            socket.getOutputStream().flush()
            return socket.getInputStream().bufferedReader().readText()
        }
    }

    private fun httpPost(port: Int, path: String, body: String): String {
        Socket("127.0.0.1", port).use { socket ->
            socket.soTimeout = 5000
            val payload = body.toByteArray(Charsets.UTF_8)
            val request = buildString {
                append("POST $path HTTP/1.1\r\n")
                append("Host: 127.0.0.1:$port\r\n")
                append("Content-Type: application/json\r\n")
                append("Content-Length: ${payload.size}\r\n")
                append("Connection: close\r\n\r\n")
            }
            socket.getOutputStream().write(request.toByteArray(Charsets.UTF_8))
            socket.getOutputStream().write(payload)
            socket.getOutputStream().flush()
            return socket.getInputStream().bufferedReader().readText()
        }
    }

    @Test
    fun severalExtensionRequestsAreQueuedAndDownloadedInOrder() = runBlocking {
        val engine = RecordingEngine()
        val presenterScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val presenter = LunaFetchPresenter(FakePlatform(engine), scope = presenterScope)
        val server = LunaSocketServer(
            onDownloadRequest = { url, format, quality, cookieFile ->
                presenter.startDirectDownload(url, format, quality)
            },
            port = 0,
        )
        server.start()

        try {
            withTimeout(5000) {
                while (server.boundPort == 0) delay(20)
            }
            val port = server.boundPort

            val responses = listOf("videoA", "videoB", "videoC").map { id ->
                val encoded = java.net.URLEncoder.encode("https://youtu.be/$id", "UTF-8")
                val response = httpGet(port, "/download?url=$encoded&format=mp4")
                assertTrue(response.contains("\"ok\":true"), "Respuesta inesperada: $response")
                response
            }
            assertTrue(responses.all { it.contains("\"queued\":true") })

            withTimeout(15000) {
                while (presenter.state.value.queue.count { it.status == QueueItemStatus.Completed } < 3) {
                    delay(50)
                }
            }

            assertEquals(
                listOf("https://youtu.be/videoA", "https://youtu.be/videoB", "https://youtu.be/videoC"),
                engine.downloadedUrls.toList(),
            )
            assertEquals(1, engine.maxConcurrent)
        } finally {
            server.stop()
            presenterScope.cancel()
        }
    }

    /**
     * Un enlace repetido no se encola dos veces: el servidor responde que ya
     * estaba en la cola en lugar desumar otra descarga.
     */
    @Test
    fun repeatedLinkIsReportedAsDuplicate() = runBlocking {
        val engine = RecordingEngine(delayMs = 2000)
        val presenterScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val presenter = LunaFetchPresenter(FakePlatform(engine), scope = presenterScope)
        val server = LunaSocketServer(
            onDownloadRequest = { url, format, quality, cookieFile ->
                presenter.startDirectDownload(url, format, quality)
            },
            port = 0,
        )
        server.start()

        try {
            withTimeout(5000) {
                while (server.boundPort == 0) delay(20)
            }
            val port = server.boundPort
            val encoded = java.net.URLEncoder.encode("https://youtu.be/repetido", "UTF-8")

            val first = httpGet(port, "/download?url=$encoded&format=mp4")
            val second = httpGet(port, "/download?url=$encoded&format=mp4")

            assertTrue(first.contains("\"ok\":true"), "Respuesta inesperada: $first")
            assertTrue(first.contains("\"queued\":true"), "El primer envío debe encolarse: $first")
            assertTrue(second.contains("\"queued\":false"), "El enlace repetido no debe encolarse: $second")
            assertTrue(second.contains("duplicate_queue"), "Debe informar que ya está en la cola: $second")
        } finally {
            server.stop()
            presenterScope.cancel()
        }
    }

    /**
     * Envío con cuerpo JSON (el camino que usa la extensión para las listas de
     * Suno): el servidor debe leer el enlace, el formato y las cookies de
     * sesión, y entregarlas al motor de descarga.
     */
    @Test
    fun jsonBodyCarriesUrlFormatAndSessionCookies() = runBlocking {
        val engine = RecordingEngine()
        val presenterScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val presenter = LunaFetchPresenter(FakePlatform(engine), scope = presenterScope)
        val received = java.util.Collections.synchronizedList(mutableListOf<String>())
        val server = LunaSocketServer(
            onDownloadRequest = { url, format, quality, cookieFile ->
                received.add(url)
                assertEquals("mp3", format)
                assertTrue(
                    cookieFile?.let { java.io.File(it).readText().contains("suno.com") } == true,
                    "El fichero de cookies debe contener las cookies de Suno: $cookieFile",
                )
                presenter.startDirectDownload(url, format, quality, cookieFile = cookieFile)
            },
            port = 0,
        )
        server.start()

        try {
            withTimeout(5000) {
                while (server.boundPort == 0) delay(20)
            }
            val port = server.boundPort

            val body = """
                {"url":"https://suno.com/song/11111111-1111-4111-8111-111111111111","format":"mp3",
                 "cookies":".suno.com\tTRUE\t/\tTRUE\t0\tsession\tvalor-de-sesion"}
            """.trimIndent()
            val response = httpPost(port, "/download", body)

            assertTrue(response.contains("\"ok\":true"), "Respuesta inesperada: $response")
            assertTrue(response.contains("\"queued\":true"), "El enlace debe encolarse: $response")
            assertEquals(listOf("https://suno.com/song/11111111-1111-4111-8111-111111111111"), received.toList())
        } finally {
            server.stop()
            presenterScope.cancel()
        }
    }

    /**
     * Envío por lotes: una sola petición con varios enlaces encola todos los
     * válidos, excluye los duplicados y devuelve un recuento honesto.
     */
    @Test
    fun batchRequestQueuesEveryLinkAndCountsDuplicates() = runBlocking {
        val engine = RecordingEngine(delayMs = 4000)
        val presenterScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val presenter = LunaFetchPresenter(FakePlatform(engine), scope = presenterScope)
        val server = LunaSocketServer(
            onDownloadRequest = { url, format, quality, cookieFile ->
                presenter.startDirectDownload(url, format, quality)
            },
            port = 0,
        )
        server.start()

        try {
            withTimeout(5000) {
                while (server.boundPort == 0) delay(20)
            }
            val port = server.boundPort

            val body = """
                {"urls":["https://suno.com/song/11111111-1111-4111-8111-111111111111",
                          "https://suno.com/song/22222222-2222-4222-8222-222222222222",
                          "https://suno.com/song/11111111-1111-4111-8111-111111111111"],
                 "format":"mp3"}
            """.trimIndent()
            val response = httpPost(port, "/download/batch", body)

            assertTrue(response.contains("\"ok\":true"), "Respuesta inesperada: $response")
            assertTrue(response.contains("\"queued\":2"), "Deben encolarse dos enlaces: $response")
            assertTrue(response.contains("\"skipped\":1"), "El repetido debe quedar fuera: $response")
            assertTrue(response.contains("duplicate_queue"), "Debe marcar el duplicado: $response")
            assertTrue(response.contains("\"pending\":2"), "Debe informar de los pendientes: $response")

            withTimeout(15000) {
                while (presenter.state.value.queue.count { it.status == QueueItemStatus.Completed } < 2) {
                    delay(50)
                }
            }
            assertEquals(
                listOf(
                    "https://suno.com/song/11111111-1111-4111-8111-111111111111",
                    "https://suno.com/song/22222222-2222-4222-8222-222222222222",
                ),
                engine.downloadedUrls.toList(),
            )
        } finally {
            server.stop()
            presenterScope.cancel()
        }
    }

    /**
     * Un lote sin enlaces debe responder con error claro, no con un recuento
     * vacío que el importador interpretaría como un éxito.
     */
    @Test
    fun batchRequestWithoutLinksIsRejected() = runBlocking {
        val engine = RecordingEngine()
        val presenterScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val presenter = LunaFetchPresenter(FakePlatform(engine), scope = presenterScope)
        val server = LunaSocketServer(
            onDownloadRequest = { url, format, quality, cookieFile ->
                presenter.startDirectDownload(url, format, quality)
            },
            port = 0,
        )
        server.start()

        try {
            withTimeout(5000) {
                while (server.boundPort == 0) delay(20)
            }
            val response = httpPost(server.boundPort, "/download/batch", """{"urls":[],"format":"mp3"}""")

            assertTrue(response.contains("400 Bad Request"), "Debe rechazar el lote vacío: $response")
            assertTrue(response.contains("\"ok\":false"), "Debe informar del fallo: $response")
        } finally {
            server.stop()
            presenterScope.cancel()
        }
    }
}
