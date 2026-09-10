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

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadNotificationTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeEngine(
        var shouldFail: Boolean = false,
        var resultPath: String = "C:/Downloads/video.mp4",
    ) : DownloadEngine {
        override suspend fun analyze(url: String): VideoInfo = VideoInfo(
            url = url,
            title = "Test Video Title",
            uploader = "Test Channel",
            durationSeconds = 120.0,
            thumbnailUrl = "https://example.com/thumb.jpg",
            maxHeight = 1080,
        )

        override suspend fun download(
            request: DownloadRequest,
            onProgress: (DownloadProgress) -> Unit,
            onLog: (String) -> Unit,
        ): DownloadResult {
            if (shouldFail) {
                throw DownloadException("Simulated download error")
            }
            onProgress(DownloadProgress(100.0, phase = DownloadPhase.Completed))
            return DownloadResult(
                outputPaths = listOf(resultPath),
                openPath = resultPath,
            )
        }

        override fun cancel() {}
    }

    private class FakePlatformBindings(
        override val engine: DownloadEngine,
        override val defaultDestination: String = "C:/Downloads",
    ) : PlatformBindings {
        override var isNotificationsEnabled: Boolean? = true
        var lastCompletedTitle: String? = null
        var lastCompletedPath: String? = null
        var lastFailedTitle: String? = null
        var lastFailedError: String? = null

        override fun setNotificationsEnabled(enabled: Boolean) {
            isNotificationsEnabled = enabled
        }

        override fun notifyDownloadCompleted(title: String, filePath: String) {
            lastCompletedTitle = title
            lastCompletedPath = filePath
        }

        override fun notifyDownloadFailed(title: String, error: String) {
            lastFailedTitle = title
            lastFailedError = error
        }

        override suspend fun chooseDestination(current: String): String? = null
        override fun destinationLabel(destination: String): String = destination
        override fun rememberDestination(destination: String) {}
        override fun openOutput(path: String) {}
    }

    @Test
    fun successfulDownloadTriggersCompletionNotification() = runTest(testDispatcher) {
        val fakeEngine = FakeEngine(shouldFail = false, resultPath = "C:/Downloads/luna.mp4")
        val fakePlatform = FakePlatformBindings(fakeEngine)
        val job = kotlinx.coroutines.Job()
        val presenterScope = kotlinx.coroutines.CoroutineScope(testDispatcher + job)
        val presenter = LunaFetchPresenter(fakePlatform, scope = presenterScope)

        try {
            presenter.startDirectDownload("https://youtu.be/test1234", "mp4")
            testScheduler.advanceUntilIdle()

            assertEquals("Test Video Title", fakePlatform.lastCompletedTitle)
            assertEquals("C:/Downloads/luna.mp4", fakePlatform.lastCompletedPath)
            assertTrue(presenter.state.value.toastMessage?.contains("Descarga completada") == true)
        } finally {
            job.cancel()
        }
    }

    @Test
    fun failedDownloadTriggersFailureNotification() = runTest(testDispatcher) {
        val fakeEngine = FakeEngine(shouldFail = true)
        val fakePlatform = FakePlatformBindings(fakeEngine)
        val job = kotlinx.coroutines.Job()
        val presenterScope = kotlinx.coroutines.CoroutineScope(testDispatcher + job)
        val presenter = LunaFetchPresenter(fakePlatform, scope = presenterScope)

        try {
            presenter.startDirectDownload("https://youtu.be/test1234", "mp4")
            testScheduler.advanceUntilIdle()

            assertEquals("Test Video Title", fakePlatform.lastFailedTitle)
            assertTrue(fakePlatform.lastFailedError?.contains("Simulated download error") == true)
            assertTrue(presenter.state.value.toastMessage?.contains("Simulated download error") == true)
        } finally {
            job.cancel()
        }
    }

    @Test
    fun notificationsToggleCanBeDisabled() {
        val fakeEngine = FakeEngine()
        val fakePlatform = FakePlatformBindings(fakeEngine)

        fakePlatform.setNotificationsEnabled(false)
        assertEquals(false, fakePlatform.isNotificationsEnabled)

        fakePlatform.setNotificationsEnabled(true)
        assertEquals(true, fakePlatform.isNotificationsEnabled)
    }

    @Test
    fun downloadWithEmptyDestinationTriggersNotificationAndToast() = runTest(testDispatcher) {
        val fakeEngine = FakeEngine(shouldFail = false)
        val fakePlatform = FakePlatformBindings(fakeEngine, defaultDestination = "")
        val job = kotlinx.coroutines.Job()
        val presenterScope = kotlinx.coroutines.CoroutineScope(testDispatcher + job)
        val presenter = LunaFetchPresenter(fakePlatform, scope = presenterScope)

        try {
            presenter.startDirectDownload("https://youtu.be/test1234", "mp4")
            testScheduler.advanceUntilIdle()

            assertTrue(presenter.state.value.toastMessage?.contains("carpeta de destino") == true)
            assertEquals("Test Video Title", fakePlatform.lastFailedTitle)
            assertTrue(fakePlatform.lastFailedError?.contains("carpeta de destino") == true)
        } finally {
            job.cancel()
        }
    }
}
