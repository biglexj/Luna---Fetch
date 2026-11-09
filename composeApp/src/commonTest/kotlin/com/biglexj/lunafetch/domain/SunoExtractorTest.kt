package com.biglexj.lunafetch.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SunoExtractorTest {

    @Test
    fun detectsSunoUrls() {
        assertTrue(SunoExtractor.isSunoUrl("https://suno.com/song/20f569e1-fb1b-4b4e-af8e-8518ffb5837a"))
        assertTrue(SunoExtractor.isSunoUrl("https://suno.com/s/kuuNnXWLBeiaN1wU"))
        assertTrue(SunoExtractor.isSunoUrl("https://suno.com/hook/20f569e1-fb1b-4b4e-af8e-8518ffb5837a"))
        assertTrue(SunoExtractor.isSunoUrl("https://suno.ai/song/20f569e1-fb1b-4b4e-af8e-8518ffb5837a"))
        assertTrue(SunoExtractor.isSunoUrl("https://usesuno.com/tools/downloader/?url=https://suno.com/song/20f569e1-fb1b-4b4e-af8e-8518ffb5837a"))

        assertFalse(SunoExtractor.isSunoUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertFalse(SunoExtractor.isSunoUrl("https://www.tiktok.com/@user/video/123"))
    }

    @Test
    fun extractsUuidFromUrls() {
        val uuid = "20f569e1-fb1b-4b4e-af8e-8518ffb5837a"
        assertEquals(uuid, SunoExtractor.extractId("https://suno.com/song/$uuid"))
        assertEquals(uuid, SunoExtractor.extractId("https://suno.com/song/$uuid?sh=test"))
        assertEquals(uuid, SunoExtractor.extractId("https://cdn1.suno.ai/$uuid.mp4"))
        assertEquals(uuid, SunoExtractor.extractId("https://cdn2.suno.ai/image_large_$uuid.jpeg"))
    }

    @Test
    fun testShortUrlFetch() {
        kotlinx.coroutines.runBlocking {
            val info = SunoExtractor.analyze("https://suno.com/s/fWsmzN5UEkCxls3m")
            assertEquals("Hola, Cómo Estás", info.title)
            assertTrue(info.uploader.contains("Biglex J"))
            assertTrue(info.durationSeconds > 300)
            assertTrue(info.thumbnailUrl.isNotBlank())
        }
    }

    @Test
    fun testSongAnalyzeLive() {
        kotlinx.coroutines.runBlocking {
            val info = SunoExtractor.analyze("https://suno.com/song/6a011fc0-5d07-4810-b422-b3f8af4032fc")
            assertEquals("Y Que Pasó", info.title)
            assertTrue(info.uploader.contains("Biglex J"))
            assertTrue(info.durationSeconds > 200.0)
            assertTrue(info.thumbnailUrl.isNotBlank())
        }
    }

    @Test
    fun testLiveDownloadFlacAndLyrics() {
        kotlinx.coroutines.runBlocking {
            val tempDir = java.io.File(System.getProperty("java.io.tmpdir"), "suno_test_${System.currentTimeMillis()}")
            tempDir.mkdirs()
            try {
                val req = DownloadRequest(
                    url = "https://suno.com/song/6a011fc0-5d07-4810-b422-b3f8af4032fc",
                    destination = tempDir.absolutePath,
                    format = MediaFormat.Flac,
                    quality = FormatCatalog.qualities(MediaFormat.Flac).first(),
                    downloadLyrics = true,
                )

                val meta = SunoExtractor.analyze("https://suno.com/song/6a011fc0-5d07-4810-b422-b3f8af4032fc")
                println("ANALYZE INFO: $meta")

                val result = SunoExtractor.download(
                    request = req,
                    onProgress = { /* noop */ },
                    onLog = { println("LOG: $it") },
                )

                assertTrue(result.outputPaths.isNotEmpty())
                val audioFile = java.io.File(result.openPath.orEmpty())
                assertTrue(audioFile.exists())
                assertTrue(audioFile.length() > 100_000L) // Valid converted audio file

                val files = tempDir.listFiles().orEmpty()
                println("FILES IN TEMPDIR: " + files.map { it.name })
                val lyricsFile = files.find { it.name.endsWith(".lyrics.txt") }
                assertTrue(lyricsFile != null && lyricsFile.exists())
                val lyricsText = lyricsFile.readText()
                assertTrue(lyricsText.contains("Y Que Pasó"))
                assertTrue(lyricsText.contains("Biglex J"))
                assertTrue(lyricsText.contains("--- LETRA DE LA CANCIÓN ---"))
                assertTrue(lyricsText.contains("(Ely... para tu corazón)"))
                assertFalse(lyricsText.contains("Prompt"))
            } finally {
                tempDir.deleteRecursively()
            }
        }
    }

    @Test
    fun testSharedLinkAnalyzeAndDownload() {
        kotlinx.coroutines.runBlocking {
            val shareUrl = "https://suno.com/s/KRR1DtfevY96GOaM"
            val info = SunoExtractor.analyze(shareUrl)
            println("SHARED LINK INFO: $info")
            assertEquals("Cozy Futuristic Chill", info.title)
            assertTrue(info.uploader.contains("Biglex J"))

            val tempDir = java.io.File(System.getProperty("java.io.tmpdir"), "suno_shared_test_${System.currentTimeMillis()}")
            tempDir.mkdirs()
            try {
                val req = DownloadRequest(
                    url = shareUrl,
                    destination = tempDir.absolutePath,
                    format = MediaFormat.Flac,
                    quality = FormatCatalog.qualities(MediaFormat.Flac).first(),
                    downloadLyrics = true,
                )

                val result = SunoExtractor.download(
                    request = req,
                    onProgress = { /* noop */ },
                    onLog = { println("LOG: $it") },
                )

                assertTrue(result.outputPaths.isNotEmpty())
                val audioFile = java.io.File(result.openPath.orEmpty())
                assertTrue(audioFile.exists())
                assertTrue(audioFile.length() > 500_000L) // Valid FLAC converted from decrypted Opus
                println("SUCCESSFULLY DOWNLOADED AND DECRYPTED: ${audioFile.name} (${audioFile.length()} bytes)")
            } finally {
                tempDir.deleteRecursively()
            }
        }
    }
}
