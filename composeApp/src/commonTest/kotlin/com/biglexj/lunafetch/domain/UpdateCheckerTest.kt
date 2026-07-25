package com.biglexj.lunafetch.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class UpdateCheckerTest {
    @Test
    fun comparesVersionsCorrectly() {
        assertTrue(UpdateChecker.isNewerVersion("1.0.7", "1.0.8"))
        assertTrue(UpdateChecker.isNewerVersion("1.0.7", "1.1.0"))
        assertTrue(UpdateChecker.isNewerVersion("1.0.7", "2.0.0"))
        assertFalse(UpdateChecker.isNewerVersion("1.0.7", "1.0.7"))
        assertFalse(UpdateChecker.isNewerVersion("1.0.7", "1.0.6"))
    }

    @Test
    fun parsesGitHubReleaseJson() {
        val sampleJson = """
            {
              "tag_name": "v1.1.0",
              "html_url": "https://github.com/biglexj/Luna---Fetch/releases/tag/v1.1.0",
              "body": "Novedades de la versión 1.1.0",
              "assets": [
                {
                  "name": "LunaFetch-v1.1.0.apk",
                  "browser_download_url": "https://github.com/biglexj/Luna---Fetch/releases/download/v1.1.0/LunaFetch-v1.1.0.apk"
                }
              ]
            }
        """.trimIndent()

        val parsed = UpdateChecker.parseUpdateRelease(sampleJson)
        assertNotNull(parsed)
        assertEquals("1.1.0", parsed.version)
        assertEquals("https://github.com/biglexj/Luna---Fetch/releases/download/v1.1.0/LunaFetch-v1.1.0.apk", parsed.downloadUrl)
        assertEquals("https://github.com/biglexj/Luna---Fetch/releases/tag/v1.1.0", parsed.releasePageUrl)
    }
}
