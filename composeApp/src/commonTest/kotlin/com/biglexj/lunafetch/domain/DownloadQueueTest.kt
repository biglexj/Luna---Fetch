package com.biglexj.lunafetch.domain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
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
        val requests = mutableListOf<DownloadRequest>()
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
                requests.add(request)
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
        var registry: List<DownloadRegistryEntry> = emptyList()
        val fileStates = mutableMapOf<String, LocalFileState>()
        val deletedPaths = mutableListOf<String>()

        override suspend fun checkUpdate(): UpdateRelease? = null
        override suspend fun chooseDestination(current: String): String? = null
        override fun destinationLabel(destination: String): String = destination
        override fun rememberDestination(destination: String) {}
        override fun openOutput(path: String) {}
        override fun loadDownloadRegistry(): List<DownloadRegistryEntry> = registry
        override fun saveDownloadRegistry(entries: List<DownloadRegistryEntry>) {
            registry = entries
        }
        override fun localFileState(path: String): LocalFileState =
            fileStates[path] ?: LocalFileState.Missing
        override fun deleteOutput(path: String): Boolean {
            deletedPaths.add(path)
            fileStates.remove(path)
            return true
        }
    }

    private fun presenterFor(
        engine: DownloadEngine,
        scope: kotlinx.coroutines.CoroutineScope,
    ): LunaFetchPresenter = LunaFetchPresenter(FakePlatformBindings(engine), scope = scope)

    private fun presenterFor(
        platform: FakePlatformBindings,
        scope: kotlinx.coroutines.CoroutineScope,
    ): LunaFetchPresenter = LunaFetchPresenter(platform, scope = scope)

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
            assertEquals(EnqueueOutcome.Queued, presenter.startDirectDownload("https://youtu.be/uno", "mp4").outcome)
            // El mismo enlace otra vez: se ignora, pero sigue habiendo un pendiente.
            assertEquals(EnqueueOutcome.DuplicateInQueue, presenter.startDirectDownload("https://youtu.be/uno", "mp4").outcome)
            assertEquals(1, presenter.startDirectDownload("https://youtu.be/uno", "mp4").pending)
            assertEquals(2, presenter.startDirectDownload("https://youtu.be/dos", "mp4").pending)
            assertEquals(3, presenter.startDirectDownload("https://youtu.be/tres", "mp4").pending)
            assertEquals(EnqueueOutcome.InvalidUrl, presenter.startDirectDownload("no-es-una-url", "mp4").outcome)
        } finally {
            job.cancel()
        }
    }

    // ── Duplicados y verificación de archivos ─────────────────────────────

    @Test
    fun duplicateLinkInTheQueueIsIgnored() = runTest(testDispatcher) {
        val engine = RecordingEngine()
        val job = kotlinx.coroutines.Job()
        val presenter = presenterFor(engine, kotlinx.coroutines.CoroutineScope(testDispatcher + job))

        try {
            val first = presenter.startDirectDownload("https://youtu.be/repetido", "mp4")
            val second = presenter.startDirectDownload("https://youtu.be/repetido", "mp4")

            assertEquals(EnqueueOutcome.Queued, first.outcome)
            assertEquals(EnqueueOutcome.DuplicateInQueue, second.outcome)

            testScheduler.advanceUntilIdle()
            assertEquals(listOf("https://youtu.be/repetido"), engine.downloadedUrls)
        } finally {
            job.cancel()
        }
    }

    @Test
    fun equivalentLinksOfDifferentFormatsAreNotDuplicates() = runTest(testDispatcher) {
        val engine = RecordingEngine()
        val job = kotlinx.coroutines.Job()
        val presenter = presenterFor(engine, kotlinx.coroutines.CoroutineScope(testDispatcher + job))

        try {
            presenter.startDirectDownload("https://youtu.be/mismo", "mp4")
            val audio = presenter.startDirectDownload("https://www.youtube.com/watch?v=mismo", "mp3")

            assertEquals(EnqueueOutcome.Queued, audio.outcome)
            testScheduler.advanceUntilIdle()
            assertEquals(2, engine.downloadedUrls.size)
        } finally {
            job.cancel()
        }
    }

    @Test
    fun alreadyDownloadedLinkIsIgnoredWhenTheFileIsPresent() = runTest(testDispatcher) {
        val engine = RecordingEngine()
        val platform = FakePlatformBindings(engine)
        platform.registry = listOf(
            DownloadRegistryEntry(
                canonicalKey = "yt:listo",
                formatKey = "mp4",
                path = "C:/Downloads/listo.mp4",
                title = "Video listo",
            )
        )
        platform.fileStates["C:/Downloads/listo.mp4"] = LocalFileState.Present
        val job = kotlinx.coroutines.Job()
        val presenter = presenterFor(platform, kotlinx.coroutines.CoroutineScope(testDispatcher + job))

        try {
            val result = presenter.startDirectDownload("https://youtu.be/listo", "mp4")
            assertEquals(EnqueueOutcome.AlreadyDownloaded, result.outcome)

            testScheduler.advanceUntilIdle()
            assertTrue(engine.downloadedUrls.isEmpty())
        } finally {
            job.cancel()
        }
    }

    @Test
    fun damagedFileIsDownloadedAgainAndReplacedInPlace() = runTest(testDispatcher) {
        val engine = RecordingEngine()
        val platform = FakePlatformBindings(engine)
        platform.registry = listOf(
            DownloadRegistryEntry(
                canonicalKey = "yt:danado",
                formatKey = "mp4",
                path = "C:/Downloads/danado.mp4",
                title = "Video dañado",
            )
        )
        // El archivo existe pero está vacío: está dañado, no movido.
        platform.fileStates["C:/Downloads/danado.mp4"] = LocalFileState.Empty
        val job = kotlinx.coroutines.Job()
        val presenter = presenterFor(platform, kotlinx.coroutines.CoroutineScope(testDispatcher + job))

        try {
            val result = presenter.startDirectDownload("https://youtu.be/danado", "mp4")
            assertEquals(EnqueueOutcome.Queued, result.outcome)

            testScheduler.advanceUntilIdle()
            assertEquals(listOf("https://youtu.be/danado"), engine.downloadedUrls)
            val request = engine.requests.first()
            assertTrue(request.replaceExisting, "La reparación debe sobrescribir el archivo dañado")
            assertEquals("C:/Downloads", request.destination, "La reparación se escribe en la carpeta original")
        } finally {
            job.cancel()
        }
    }

    @Test
    fun movedFileIsDownloadedAgainWithoutReplacing() = runTest(testDispatcher) {
        val engine = RecordingEngine()
        val platform = FakePlatformBindings(engine)
        platform.registry = listOf(
            DownloadRegistryEntry(
                canonicalKey = "yt:movido",
                formatKey = "mp4",
                path = "C:/Downloads/movido.mp4",
                title = "Video movido",
            )
        )
        // El archivo ya no está: se movió, no está dañado.
        platform.fileStates["C:/Downloads/movido.mp4"] = LocalFileState.Missing
        val job = kotlinx.coroutines.Job()
        val presenter = presenterFor(platform, kotlinx.coroutines.CoroutineScope(testDispatcher + job))

        try {
            val result = presenter.startDirectDownload("https://youtu.be/movido", "mp4")
            assertEquals(EnqueueOutcome.Queued, result.outcome)

            testScheduler.advanceUntilIdle()
            assertEquals(listOf("https://youtu.be/movido"), engine.downloadedUrls)
            val request = engine.requests.first()
            assertTrue(!request.replaceExisting, "Un archivo movido se descarga de nuevo con normalidad")
            assertEquals("C:/Downloads", request.destination)
        } finally {
            job.cancel()
        }
    }

    @Test
    fun concurrentDuplicatesAreEnqueuedOnlyOnce() = runTest(testDispatcher) {
        val engine = RecordingEngine()
        val job = kotlinx.coroutines.Job()
        val presenter = presenterFor(engine, kotlinx.coroutines.CoroutineScope(testDispatcher + job))

        try {
            // La comprobación de duplicados y el encolado deben ser atómicos:
            // varios envíos simultáneos del mismo enlace solo pueden encolar uno.
            val results = kotlinx.coroutines.coroutineScope {
                (1..8).map {
                    this.async(Dispatchers.Default) {
                        presenter.startDirectDownload("https://youtu.be/concurrente", "mp4")
                    }
                }.map { it.await() }
            }

            assertEquals(1, results.count { it.outcome == EnqueueOutcome.Queued })
            assertEquals(7, results.count { it.outcome == EnqueueOutcome.DuplicateInQueue })
        } finally {
            job.cancel()
        }
    }

    @Test
    fun completedDownloadIsRegisteredForFutureDuplicates() = runTest(testDispatcher) {
        val engine = RecordingEngine()
        val platform = FakePlatformBindings(engine)
        val job = kotlinx.coroutines.Job()
        val presenter = presenterFor(platform, kotlinx.coroutines.CoroutineScope(testDispatcher + job))

        try {
            presenter.startDirectDownload("https://youtu.be/registrado", "mp4")
            testScheduler.advanceUntilIdle()

            val entry = platform.registry.firstOrNull { it.canonicalKey == "yt:registrado" }
            assertTrue(entry != null, "La descarga completada debe quedar en el registro")
            assertEquals("mp4", entry.formatKey)
        } finally {
            job.cancel()
        }
    }

    /**
     * En la cola, lo que ya terminó (completado, fallido o cancelado) se
     * muestra al final; lo que sigue vivo, en su orden de encolado.
     */
    @Test
    fun finishedItemsAreShownAtTheEndOfTheQueue() {
        val queue = listOf(
            QueuedDownload(formatName = "mp4", id = "completado", url = "https://youtu.be/a", status = QueueItemStatus.Completed),
            QueuedDownload(formatName = "mp4", id = "espera", url = "https://youtu.be/b", status = QueueItemStatus.Pending),
            QueuedDownload(formatName = "mp4", id = "fallido", url = "https://youtu.be/c", status = QueueItemStatus.Failed),
            QueuedDownload(formatName = "mp4", id = "descargando", url = "https://youtu.be/d", status = QueueItemStatus.Running),
            QueuedDownload(formatName = "mp4", id = "cancelado", url = "https://youtu.be/e", status = QueueItemStatus.Cancelled),
        )

        val ordered = queue.sortedForDisplay()

        assertEquals(
            listOf("espera", "descargando", "completado", "fallido", "cancelado"),
            ordered.map { it.id },
        )
    }

    /** El orden dentro de cada grupo se conserva: la cola no se reordena sola. */
    @Test
    fun displayOrderKeepsTheOrderWithinEachGroup() {
        val queue = listOf(
            QueuedDownload(formatName = "mp4", id = "fallido-1", url = "https://youtu.be/a", status = QueueItemStatus.Failed),
            QueuedDownload(formatName = "mp4", id = "espera-1", url = "https://youtu.be/b", status = QueueItemStatus.Pending),
            QueuedDownload(formatName = "mp4", id = "espera-2", url = "https://youtu.be/c", status = QueueItemStatus.Pending),
            QueuedDownload(formatName = "mp4", id = "completado-1", url = "https://youtu.be/d", status = QueueItemStatus.Completed),
        )

        assertEquals(
            listOf("espera-1", "espera-2", "fallido-1", "completado-1"),
            queue.sortedForDisplay().map { it.id },
        )
    }
}
