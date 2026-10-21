package com.biglexj.lunafetch.domain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Verifica la cola de descargas: varios enlaces enviados casi al mismo tiempo
 * deben procesarse uno detrás de otro, en orden, sin cancelarse entre sí.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DownloadQueueTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class RecordingEngine(
        private val failingUrls: Set<String> = emptySet(),
    ) : DownloadEngine {
        val analyzedUrls = mutableListOf<String>()
        val downloadedUrls = mutableListOf<String>()
        var maxConcurrentDownloads = 0
        private var activeDownloads = 0

        override suspend fun analyze(url: String): VideoInfo {
            analyzedUrls.add(url)
            return VideoInfo(
                url = url,
                title = "Video de $url",
                uploader = "Canal",
                durationSeconds = 60.0,
                thumbnailUrl = "",
                maxHeight = 1080,
            )
        }

        override suspend fun download(
            request: DownloadRequest,
            onProgress: (DownloadProgress) -> Unit,
            onLog: (String) -> Unit,
        ): DownloadResult {
            activeDownloads++
            if (activeDownloads > maxConcurrentDownloads) maxConcurrentDownloads = activeDownloads
            try {
                downloadedUrls.add(request.url)
                if (request.url in failingUrls) {
                    throw DownloadException("Fallo simulado para ${request.url}")
                }
                onProgress(DownloadProgress(100.0, phase = DownloadPhase.Completed))
                return DownloadResult(
                    outputPaths = listOf("C:/Downloads/${request.url.substringAfterLast('/')}.mp4"),
                    openPath = "C:/Downloads/${request.url.substringAfterLast('/')}.mp4",
                )
            } finally {
                activeDownloads--
            }
        }

        override fun cancel() {}
    }

    private class FakePlatformBindings(
        override val engine: DownloadEngine,
        override val defaultDestination: String = "C:/Downloads",
    ) : PlatformBindings {
        override suspend fun checkUpdate(): UpdateRelease? = null
        override suspend fun chooseDestination(current: String): String? = null
        override fun destinationLabel(destination: String): String = destination
        override fun rememberDestination(destination: String) {}
        override fun openOutput(path: String) {}
    }

    private fun presenterFor(
        engine: DownloadEngine,
        scope: kotlinx.coroutines.CoroutineScope,
    ): LunaFetchPresenter = LunaFetchPresenter(FakePlatformBindings(engine), scope = scope)

    @Test
    fun queuedLinksAreDownloadedInOrder() = runTest(testDispatcher) {
        val engine = RecordingEngine()
        val job = kotlinx.coroutines.Job()
        val presenter = presenterFor(engine, kotlinx.coroutines.CoroutineScope(testDispatcher + job))

        try {
            presenter.startDirectDownload("https://youtu.be/videoA", "mp4")
            presenter.startDirectDownload("https://youtu.be/videoB", "mp4")
            presenter.startDirectDownload("https://youtu.be/videoC", "mp3")
            testScheduler.advanceUntilIdle()

            assertEquals(
                listOf("https://youtu.be/videoA", "https://youtu.be/videoB", "https://youtu.be/videoC"),
                engine.downloadedUrls,
            )
            assertEquals(1, engine.maxConcurrentDownloads)
            assertEquals(3, presenter.state.value.queue.count { it.status == QueueItemStatus.Completed })
        } finally {
            job.cancel()
        }
    }

    @Test
    fun linkEnqueuedWhileAnotherIsProcessingIsNotLost() = runTest(testDispatcher) {
        val engine = RecordingEngine()
        val job = kotlinx.coroutines.Job()
        val presenter = presenterFor(engine, kotlinx.coroutines.CoroutineScope(testDispatcher + job))

        try {
            presenter.startDirectDownload("https://youtu.be/primero", "mp4")
            presenter.startDirectDownload("https://youtu.be/segundo", "mp4")
            testScheduler.advanceUntilIdle()

            assertEquals(2, engine.downloadedUrls.size)
            assertTrue(engine.downloadedUrls.contains("https://youtu.be/primero"))
            assertTrue(engine.downloadedUrls.contains("https://youtu.be/segundo"))
        } finally {
            job.cancel()
        }
    }

    @Test
    fun failedLinkDoesNotBlockTheRestOfTheQueue() = runTest(testDispatcher) {
        val engine = RecordingEngine(failingUrls = setOf("https://youtu.be/roto"))
        val job = kotlinx.coroutines.Job()
        val presenter = presenterFor(engine, kotlinx.coroutines.CoroutineScope(testDispatcher + job))

        try {
            presenter.startDirectDownload("https://youtu.be/roto", "mp4")
            presenter.startDirectDownload("https://youtu.be/sano", "mp4")
            testScheduler.advanceUntilIdle()

            assertEquals(listOf("https://youtu.be/roto", "https://youtu.be/sano"), engine.downloadedUrls)
            val queue = presenter.state.value.queue
            assertEquals(QueueItemStatus.Failed, queue.first { it.url.endsWith("roto") }.status)
            assertEquals(QueueItemStatus.Completed, queue.first { it.url.endsWith("sano") }.status)
        } finally {
            job.cancel()
        }
    }

    @Test
    fun pendingCountIsReportedToTheCaller() = runTest(testDispatcher) {
        val engine = RecordingEngine()
        val job = kotlinx.coroutines.Job()
        val presenter = presenterFor(engine, kotlinx.coroutines.CoroutineScope(testDispatcher + job))

        try {
            assertEquals(1, presenter.startDirectDownload("https://youtu.be/uno", "mp4"))
            assertEquals(2, presenter.startDirectDownload("https://youtu.be/dos", "mp4"))
            assertEquals(3, presenter.startDirectDownload("https://youtu.be/tres", "mp4"))
            assertEquals(-1, presenter.startDirectDownload("no-es-una-url", "mp4"))
        } finally {
            job.cancel()
        }
    }
}
