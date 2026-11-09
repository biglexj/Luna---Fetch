package com.biglexj.lunafetch.domain

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Verifica que el formato que llega desde la extensión del navegador o el
 * importador de Suno vuelve a su formato: un FLAC se descarga en FLAC y no
 * como MP3.
 */
class MediaFormatTest {

    @Test
    fun audioFormatsKeepTheirOwnFormat() {
        assertEquals(MediaFormat.Mp3, MediaFormat.resolve("mp3"))
        assertEquals(MediaFormat.M4a, MediaFormat.resolve("m4a"))
        assertEquals(MediaFormat.Flac, MediaFormat.resolve("flac"))
        assertEquals(MediaFormat.Wav, MediaFormat.resolve("wav"))
    }

    @Test
    fun theDisplayNamesAlsoResolve() {
        assertEquals(MediaFormat.Mp3, MediaFormat.resolve("Audio MP3"))
        assertEquals(MediaFormat.M4a, MediaFormat.resolve("Audio M4A"))
        assertEquals(MediaFormat.Flac, MediaFormat.resolve("Audio FLAC (Lossless)"))
        assertEquals(MediaFormat.Wav, MediaFormat.resolve("Audio WAV (Lossless)"))
        assertEquals(MediaFormat.Mp4, MediaFormat.resolve("Video MP4"))
        assertEquals(MediaFormat.WebM, MediaFormat.resolve("Video WebM"))
    }

    @Test
    fun videoFormatsResolveToVideo() {
        assertEquals(MediaFormat.Mp4, MediaFormat.resolve("mp4"))
        assertEquals(MediaFormat.WebM, MediaFormat.resolve("webm"))
    }

    @Test
    fun audioWithoutFormatFallsBackToMp3() {
        assertEquals(MediaFormat.Mp3, MediaFormat.resolve("audio"))
        assertEquals(MediaFormat.Mp3, MediaFormat.resolve("Audio"))
    }

    @Test
    fun unknownFormatsFallBackToVideo() {
        assertEquals(MediaFormat.Mp4, MediaFormat.resolve(""))
        assertEquals(MediaFormat.Mp4, MediaFormat.resolve("avi"))
    }

    @Test
    fun theResolutionIsCaseAndSpaceInsensitive() {
        assertEquals(MediaFormat.Flac, MediaFormat.resolve("  FLAC  "))
        assertEquals(MediaFormat.Wav, MediaFormat.resolve("Wav"))
    }

    @Test
    fun everyFormatHasItsOwnExtension() {
        val extensions = MediaFormat.entries.map { it.extension }
        assertEquals(extensions.size, extensions.toSet().size)
    }
}
