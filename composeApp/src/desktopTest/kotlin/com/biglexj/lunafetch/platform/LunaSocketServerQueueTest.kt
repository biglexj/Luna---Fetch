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
}
