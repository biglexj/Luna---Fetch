package com.biglexj.lunafetch.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FormatCatalogTest {

    private val videoQualities = listOf(
        "Mejor calidad",
        "8K · Ultra HD",
        "4K · Ultra HD",
        "1440p · 2K",
        "1080p · Full HD",
        "720p · HD",
        "480p",
        "360p",
    )

    @Test
    fun allVideoQualitiesAreAlwaysOffered() {
        val qualities = FormatCatalog.qualities(MediaFormat.Mp4)
        assertEquals(videoQualities, qualities.map(QualityOption::displayName))
    }

    @Test
    fun mejorCalidadIsTheDefaultAndHasNoLimit() {
        val qualities = FormatCatalog.qualities(MediaFormat.Mp4)
        val best = qualities.first()
        assertEquals("Mejor calidad", best.displayName)
        assertFalse("height<=" in best.formatSelector)
        assertTrue("[ext=mp4]" in best.formatSelector)
    }

    @Test
    fun eachQualityLimitsTheDownloadToItsOwnHeight() {
        val qualities = FormatCatalog.qualities(MediaFormat.Mp4)
        val twoK = qualities.first { it.displayName == "1440p · 2K" }
        val fourK = qualities.first { it.displayName == "4K · Ultra HD" }
        assertTrue("height<=1440" in twoK.formatSelector)
        assertTrue("height<=2160" in fourK.formatSelector)
        assertFalse("height<=4320" in twoK.formatSelector)
    }

    @Test
    fun audioQualitiesUseAudioSelectors() {
        val qualities = FormatCatalog.qualities(MediaFormat.Mp3)
        assertEquals(3, qualities.size)
        assertTrue(qualities.all { it.formatSelector == "bestaudio/best" })
    }

    @Test
    fun videoSelectorsKeepProgressiveAndGenericFallbacks() {
        val selector = FormatCatalog.qualities(MediaFormat.Mp4)
            .first { it.displayName == "1080p · Full HD" }.formatSelector
        assertTrue("[ext=mp4]" in selector)
        assertTrue("best[height<=1080]" in selector)
        assertTrue(selector.endsWith("/best"))
    }

    @Test
    fun losslessFlacAndWavAreSupported() {
        assertTrue(MediaFormat.entries.any { it.extension == "flac" })
        assertTrue(MediaFormat.entries.any { it.extension == "wav" })
    }

    @Test
    fun urlValidationAcceptsOnlyHttpUrlsWithoutWhitespace() {
        assertTrue(LunaFetchPresenter.isSupportedUrl("https://youtu.be/example"))
        assertFalse(LunaFetchPresenter.isSupportedUrl("file:///video.mp4"))
        assertFalse(LunaFetchPresenter.isSupportedUrl("https://bad url.example"))
    }

    @Test
    fun videoQualitiesInclude4KAnd8K() {
        val qualities = FormatCatalog.qualities(MediaFormat.Mp4)
        assertTrue(qualities.any { it.displayName == "4K · Ultra HD" })
        assertTrue(qualities.any { it.displayName == "8K · Ultra HD" })
    }
}
