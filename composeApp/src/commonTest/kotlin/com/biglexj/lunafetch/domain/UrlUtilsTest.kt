package com.biglexj.lunafetch.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class UrlUtilsTest {

    @Test
    fun removesTheAutomaticRadioOfYouTube() {
        val raw = "https://www.youtube.com/watch?v=6k7a8bw451M&list=RD6k7a8bw451M&start_radio=1"
        assertEquals("https://www.youtube.com/watch?v=6k7a8bw451M", UrlUtils.normalize(raw))
    }

    @Test
    fun removesRadioIndexParams() {
        val raw = "https://www.youtube.com/watch?v=6k7a8bw451M&list=RD6k7a8bw451M&index=3&rindex=1"
        assertEquals("https://www.youtube.com/watch?v=6k7a8bw451M", UrlUtils.normalize(raw))
    }

    @Test
    fun keepsRealPlaylists() {
        val raw = "https://www.youtube.com/watch?v=abc12345678&list=PL1234567890"
        assertEquals(raw, UrlUtils.normalize(raw))
    }

    @Test
    fun keepsOtherParametersOfTheVideo() {
        val raw = "https://www.youtube.com/watch?v=abc12345678&t=30"
        assertEquals(raw, UrlUtils.normalize(raw))
    }

    @Test
    fun stillCleansTikTokTrackingParams() {
        val raw = "https://www.tiktok.com/@user/video/123456789?is_from_webapp=1&sender_device=pc"
        assertEquals("https://www.tiktok.com/@user/video/123456789", UrlUtils.normalize(raw))
    }

    @Test
    fun extractsTheUrlFromSharedText() {
        val shared = "Mira esto https://www.youtube.com/watch?v=6k7a8bw451M&list=RD6k7a8bw451M en YouTube"
        assertEquals("https://www.youtube.com/watch?v=6k7a8bw451M", UrlUtils.normalize(shared))
    }
}
