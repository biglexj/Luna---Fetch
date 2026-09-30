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
}
