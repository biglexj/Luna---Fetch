package com.biglexj.lunafetch.domain.synapse

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SynapseUriParserTest {

    @Test
    fun testParseDirectDownloadUri() {
        val uri = "luna://download?url=https://youtube.com/watch?v=dQw4w9WgXcQ&type=audio&quality=bestaudio&silent=true"
        val action = SynapseUriParser.parse(uri)

        assertNotNull(action)
        assertIs<SynapseAction.EnqueueDownload>(action)
        assertEquals("https://youtube.com/watch?v=dQw4w9WgXcQ", action.url)
        assertEquals("audio", action.mediaType)
        assertEquals("bestaudio", action.quality)
        assertTrue(action.silent)
    }

    @Test
    fun testParseCanonicalAnalyzeUri() {
        val uri = "aurora-synapse://luna/analyze?url=https://tiktok.com/@user/video/12345"
        val action = SynapseUriParser.parse(uri)

        assertNotNull(action)
        assertIs<SynapseAction.AnalyzeUrl>(action)
        assertEquals("https://tiktok.com/@user/video/12345", action.url)
        assertFalse(action.silent)
    }

    @Test
    fun testParseJsonEnvelope() {
        val json = """
            {
              "synapse_version": "1.0",
              "source_app": "prisma",
              "target_app": "luna",
              "action": "enqueue_download",
              "payload": {
                "url": "https://soundcloud.com/artist/track",
                "media_type": "audio",
                "auto_play_on_finish": "true"
              }
            }
        """.trimIndent()

        val action = SynapseUriParser.parse(json)
        assertNotNull(action)
        assertIs<SynapseAction.EnqueueDownload>(action)
        assertEquals("https://soundcloud.com/artist/track", action.url)
        assertEquals("audio", action.mediaType)
        assertTrue(action.autoPlayOnFinish)
    }

    @Test
    fun testParseFocus() {
        val action = SynapseUriParser.parse("FOCUS")
        assertEquals(SynapseAction.Focus, action)
    }

    @Test
    fun testPathGuardRejection() {
        val maliciousUri = "luna://download?url=https://youtube.com/watch?v=123&dest=../../Windows/System32"
        val action = SynapseUriParser.parse(maliciousUri)
        assertNull(action, "Debe rechazar rutas con directory traversal")

        assertFalse(SynapseUriParser.sanitizePathGuard("../secret"))
        assertFalse(SynapseUriParser.sanitizePathGuard("C:/Windows/System32"))
        assertTrue(SynapseUriParser.sanitizePathGuard("D:/Musica/Descargas"))
    }
}
