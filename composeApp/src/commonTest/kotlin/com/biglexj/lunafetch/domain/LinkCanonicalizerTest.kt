package com.biglexj.lunafetch.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Verifica que los enlaces equivalentes al mismo contenido compartan clave
 * canónica y que los parámetros de seguimiento no rompan la comparación.
 */
class LinkCanonicalizerTest {

    @Test
    fun youtubeVariantsShareTheSameKey() {
        val expected = "yt:abc123"
        assertEquals(expected, LinkCanonicalizer.canonicalKey("https://www.youtube.com/watch?v=abc123"))
        assertEquals(expected, LinkCanonicalizer.canonicalKey("https://youtube.com/watch?v=abc123&t=42s"))
        assertEquals(expected, LinkCanonicalizer.canonicalKey("https://youtu.be/abc123"))
        assertEquals(expected, LinkCanonicalizer.canonicalKey("https://m.youtube.com/shorts/abc123"))
        assertEquals(expected, LinkCanonicalizer.canonicalKey("https://www.youtube.com/embed/abc123"))
        assertEquals(expected, LinkCanonicalizer.canonicalKey("https://www.youtube-nocookie.com/embed/abc123"))
    }

    @Test
    fun differentVideosHaveDifferentKeys() {
        assertNotEquals(
            LinkCanonicalizer.canonicalKey("https://youtu.be/uno"),
            LinkCanonicalizer.canonicalKey("https://youtu.be/dos"),
        )
    }

    @Test
    fun trackingParametersAreIgnored() {
        val clean = LinkCanonicalizer.canonicalKey("https://example.com/video")
        assertEquals(clean, LinkCanonicalizer.canonicalKey("https://example.com/video?utm_source=news"))
        assertEquals(clean, LinkCanonicalizer.canonicalKey("https://example.com/video?fbclid=abc"))
        assertEquals(clean, LinkCanonicalizer.canonicalKey("https://www.example.com/video/"))
    }

    @Test
    fun meaningfulParametersAreKept() {
        assertEquals(
            "web:example.com/video?id=7",
            LinkCanonicalizer.canonicalKey("https://example.com/video?id=7&utm_source=news"),
        )
    }

    @Test
    fun tiktokLinksDropTheShareParameters() {
        assertEquals(
            "tt:@usuario/video/123",
            LinkCanonicalizer.canonicalKey("https://www.tiktok.com/@usuario/video/123/?is_from_webapp=1&sender_device=pc"),
        )
    }

    @Test
    fun blankUrlProducesBlankKey() {
        assertEquals("", LinkCanonicalizer.canonicalKey("   "))
    }

    @Test
    fun keyIsStableForRepeatedCalls() {
        val url = "https://www.youtube.com/watch?v=abc123&feature=share"
        assertTrue(LinkCanonicalizer.canonicalKey(url) == LinkCanonicalizer.canonicalKey(url))
    }

    @Test
    fun singleItemUrlDropsThePlaylistAndRadioParameters() {
        assertEquals(
            "https://www.youtube.com/watch?v=uHFqL-7pe_0",
            LinkCanonicalizer.singleItemUrl(
                "https://www.youtube.com/watch?v=uHFqL-7pe_0&list=RDuHFqL-7pe_0&start_radio=1"
            ),
        )
        assertEquals(
            "https://youtu.be/abc123",
            LinkCanonicalizer.singleItemUrl("https://youtu.be/abc123?list=PL123&index=4"),
        )
        assertEquals(
            "https://www.youtube.com/shorts/abc123",
            LinkCanonicalizer.singleItemUrl("https://www.youtube.com/shorts/abc123?feature=share&list=PL123"),
        )
    }

    @Test
    fun singleItemUrlKeepsTheVideoParameterAndOtherSites() {
        assertEquals(
            "https://www.youtube.com/watch?v=abc123&t=30",
            LinkCanonicalizer.singleItemUrl("https://www.youtube.com/watch?v=abc123&t=30&list=PL123"),
        )
        assertEquals(
            "https://example.com/video?id=7&list=1",
            LinkCanonicalizer.singleItemUrl("https://example.com/video?id=7&list=1"),
        )
    }
}
