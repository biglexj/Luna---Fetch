package com.biglexj.lunafetch.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TikTokUtilsTest {
    @Test
    fun detectsTikTokDomains() {
        assertTrue(TikTokUtils.isTikTokUrl("https://www.tiktok.com/@username/video/71234567890"))
        assertTrue(TikTokUtils.isTikTokUrl("https://vt.tiktok.com/ZS123456/"))
        assertTrue(TikTokUtils.isTikTokUrl("https://vm.tiktok.com/ZS123456/"))
        assertTrue(TikTokUtils.isTikTokUrl("https://m.tiktok.com/v/71234567890.html"))
        assertFalse(TikTokUtils.isTikTokUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
    }

    @Test
    fun sanitizesTikTokUrlAndStripsTrackingParams() {
        val raw = "https://www.tiktok.com/@user/video/123456789?is_from_webapp=1&sender_device=pc&_r=1"
        val cleaned = TikTokUtils.sanitizeUrl(raw)
        assertEquals("https://www.tiktok.com/@user/video/123456789", cleaned)
    }

    @Test
    fun extractsUrlFromSharedText() {
        val sharedText = "¡Mira este increíble video de TikTok! https://vt.tiktok.com/ZSabc123/ #viral"
        val cleaned = TikTokUtils.sanitizeUrl(sharedText)
        assertEquals("https://vt.tiktok.com/ZSabc123/", cleaned)
    }

    @Test
    fun preservesNonTikTokUrls() {
        val raw = "https://youtu.be/dQw4w9WgXcQ?si=12345"
        val cleaned = TikTokUtils.sanitizeUrl(raw)
        assertEquals("https://youtu.be/dQw4w9WgXcQ?si=12345", cleaned)
    }
}
