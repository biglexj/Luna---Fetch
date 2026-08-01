package com.biglexj.lunafetch.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class UpdateCheckerTest {
    @Test
    fun comparesVersionsCorrectly() {
        assertTrue(UpdateChecker.isNewerVersion("1.0.8", "1.0.9"))
        assertTrue(UpdateChecker.isNewerVersion("1.0.8", "1.1.0"))
        assertTrue(UpdateChecker.isNewerVersion("1.0.8", "2.0.0"))
        assertFalse(UpdateChecker.isNewerVersion("1.0.8", "1.0.8"))
        assertFalse(UpdateChecker.isNewerVersion("1.0.8", "1.0.7"))
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

    @Test
    fun selectsPreferredAbiApk() {
        val sampleJson = """
            {
              "tag_name": "v1.2.0",
              "html_url": "https://github.com/biglexj/Luna---Fetch/releases/tag/v1.2.0",
              "body": "Novedades 1.2.0",
              "assets": [
                {
                  "name": "LunaFetch-Android-x86_64-1.2.0.apk",
                  "browser_download_url": "https://github.com/biglexj/Luna---Fetch/releases/download/v1.2.0/LunaFetch-Android-x86_64-1.2.0.apk"
                },
                {
                  "name": "LunaFetch-Android-arm64-v8a-1.2.0.apk",
                  "browser_download_url": "https://github.com/biglexj/Luna---Fetch/releases/download/v1.2.0/LunaFetch-Android-arm64-v8a-1.2.0.apk"
                }
              ]
            }
        """.trimIndent()

        val parsedArm = UpdateChecker.parseUpdateRelease(sampleJson, preferredAbi = "arm64-v8a")
        assertNotNull(parsedArm)
        assertEquals("https://github.com/biglexj/Luna---Fetch/releases/download/v1.2.0/LunaFetch-Android-arm64-v8a-1.2.0.apk", parsedArm.downloadUrl)
    }

    @Test
    fun returnsEmptyDownloadUrlWhenNoApkAssetExists() {
        val sampleJson = """
            {
              "tag_name": "v1.2.0",
              "html_url": "https://github.com/biglexj/Luna---Fetch/releases/tag/v1.2.0",
              "body": "Sin APK",
              "assets": [
                {
                  "name": "LunaFetch-Windows-1.2.0.exe",
                  "browser_download_url": "https://github.com/biglexj/Luna---Fetch/releases/download/v1.2.0/LunaFetch-Windows-1.2.0.exe"
                }
              ]
            }
        """.trimIndent()

        val parsed = UpdateChecker.parseUpdateRelease(sampleJson)
        assertNotNull(parsed)
        assertEquals("", parsed.downloadUrl)
        assertEquals("https://github.com/biglexj/Luna---Fetch/releases/download/v1.2.0/LunaFetch-Windows-1.2.0.exe", parsed.exeDownloadUrl)
        assertEquals("https://github.com/biglexj/Luna---Fetch/releases/tag/v1.2.0", parsed.releasePageUrl)
    }
}
