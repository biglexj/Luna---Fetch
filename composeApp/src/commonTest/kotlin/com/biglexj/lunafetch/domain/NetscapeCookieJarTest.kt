package com.biglexj.lunafetch.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NetscapeCookieJarTest {

    @Test
    fun defaultClearanceForPornhubContainsVerifiedAndUa() {
        val cookies = NetscapeCookieJar.defaultClearanceForUrl("https://www.pornhub.com/view_video.php?viewkey=6aa7c178f0fce")
        assertTrue(cookies.isNotEmpty())
        val names = cookies.map { it.name }
        assertTrue(names.contains("platform"))
        assertTrue(names.contains("age_verified"))
        assertTrue(names.contains("ua"))
        val uaCookie = cookies.first { it.name == "ua" }
        assertEquals("7675d59b5e84e0a878ee6f0a97f9056f", uaCookie.value)
    }

    @Test
    fun parseAndSerializeRoundtrip() {
        val sample = """
            # Netscape HTTP Cookie File
            .pornhub.com	TRUE	/	FALSE	2147483647	platform	pc
            .pornhub.com	TRUE	/	FALSE	2147483647	age_verified	1
        """.trimIndent()
        val parsed = NetscapeCookieJar.parse(sample)
        assertEquals(2, parsed.size)
        assertEquals("platform", parsed[0].name)
        assertEquals("pc", parsed[0].value)

        val serialized = NetscapeCookieJar.serialize(parsed)
        assertTrue(serialized.contains("platform\tpc"))
        assertTrue(serialized.contains("age_verified\t1"))
    }

    @Test
    fun mergeUpdatesExistingAndPreservesOthers() {
        val initial = listOf(
            NetscapeCookie(domain = ".example.com", name = "session", value = "123"),
            NetscapeCookie(domain = ".pornhub.com", name = "platform", value = "old"),
        )
        val updates = listOf(
            NetscapeCookie(domain = ".pornhub.com", name = "platform", value = "pc"),
            NetscapeCookie(domain = ".pornhub.com", name = "age_verified", value = "1"),
        )
        val merged = NetscapeCookieJar.merge(initial, updates)
        assertEquals(3, merged.size)
        val phPlatform = merged.first { it.domain == ".pornhub.com" && it.name == "platform" }
        assertEquals("pc", phPlatform.value)
        val exSession = merged.first { it.domain == ".example.com" && it.name == "session" }
        assertEquals("123", exSession.value)
    }

    @Test
    fun parseHeaderStringGeneratesCorrectCookies() {
        val header = "age_verified=1; platform=pc; bs=abc12345"
        val cookies = NetscapeCookieJar.parseHeaderString("https://www.pornhub.com/video", header)
        assertEquals(3, cookies.size)
        assertEquals("age_verified", cookies[0].name)
        assertEquals("1", cookies[0].value)
        assertEquals(".www.pornhub.com", cookies[0].domain)
    }
}
