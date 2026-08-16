package com.biglexj.lunafetch.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class YtdlpProtocolTest {
    @Test
    fun parsesStableProgressTemplate() {
        val progress = YtdlpProtocol.parseProgress("LUNAFETCH_PROGRESS| 42.5%|50.2MiB|3.4MiB/s|00:12")
        assertEquals(42.5, progress?.percentage)
        assertEquals("50.2MiB", progress?.size)
        assertEquals("3.4MiB/s", progress?.speed)
        assertEquals("00:12", progress?.eta)
    }

    @Test
    fun recognizesPostProcessing() {
        val progress = YtdlpProtocol.parseProgress("[Merger] Merging formats into output.mp4")
        assertEquals(DownloadPhase.Processing, progress?.phase)
    }

    @Test
    fun argumentsRemainSeparatedAndEndWithOutputTemplate() {
        val request = DownloadRequest(
            url = "https://example.com/watch?v=1&list=2",
            destination = "unused",
            format = MediaFormat.Mp3,
            quality = FormatCatalog.qualities(MediaFormat.Mp3, 1080).first(),
        )
        val outputTemplate = "C:/Downloads/%(title)s.%(ext)s"
        val arguments = YtdlpProtocol.buildDownloadArguments(request, outputTemplate)
        assertTrue("-x" in arguments)
        assertTrue("--audio-format" in arguments)
        assertTrue("--embed-metadata" in arguments)
        assertTrue("--embed-thumbnail" in arguments)
        assertTrue("--convert-thumbnails" in arguments)
        assertTrue("ThumbnailsConvertor+FFmpeg_o:-vf crop=ih:ih:(iw-ih)/2:0" in arguments)
        assertTrue("--no-playlist" in arguments)
        assertFalse(arguments.any { it.contains("\"https://") })
        assertEquals(outputTemplate, arguments.last())
    }

    @Test
    fun collectionAudioAddsAlbumTrackAndPlaylistOptions() {
        val request = DownloadRequest(
            url = "https://example.com/playlist?list=2",
            destination = "unused",
            format = MediaFormat.M4a,
            quality = FormatCatalog.qualities(MediaFormat.M4a, 1080).first(),
            downloadCollection = true,
        )

        val arguments = YtdlpProtocol.buildDownloadArguments(request, "C:/Downloads/%(title)s.%(ext)s")

        assertTrue("--yes-playlist" in arguments)
        assertTrue("%(playlist_title)s:%(meta_album)s" in arguments)
        assertTrue("%(playlist_index)s:%(meta_track)s" in arguments)
        assertFalse("--no-playlist" in arguments)
    }

    @Test
    fun extractsFinalPath() {
        assertEquals("D:/Downloads/video.mp4", YtdlpProtocol.outputPath("LUNAFETCH_FILE|D:/Downloads/video.mp4"))
        assertEquals("D:\\Videos\\video.mp4", YtdlpProtocol.outputPath("[Merger] Merging formats into \"D:\\Videos\\video.mp4\""))
        assertEquals("D:\\Videos\\clip.mp4", YtdlpProtocol.outputPath("[download] Destination: D:\\Videos\\clip.mp4"))
        assertEquals("D:\\Videos\\song.mp3", YtdlpProtocol.outputPath("[download] D:\\Videos\\song.mp3 has already been downloaded"))
    }

    @Test
    fun tikTokUrlUsesCleanNativeArguments() {
        val request = DownloadRequest(
            url = "https://www.tiktok.com/@user/video/123456789",
            destination = "unused",
            format = MediaFormat.Mp4,
            quality = FormatCatalog.qualities(MediaFormat.Mp4, 1080).first(),
        )
        val arguments = YtdlpProtocol.buildDownloadArguments(request, "C:/Downloads/%(title)s.%(ext)s")
        assertTrue("--ignore-config" in arguments)
        assertFalse("--user-agent" in arguments)
        assertFalse("--referer" in arguments)

        val analyzeArgs = YtdlpProtocol.buildAnalyzeArguments("https://vt.tiktok.com/ZS123456/")
        assertTrue("--dump-single-json" in analyzeArgs)
        assertFalse("--user-agent" in analyzeArgs)
        assertFalse("--referer" in analyzeArgs)
    }
}
