package com.biglexj.lunafetch.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MediaFoldersTest {

    @Test
    fun separatesAudioAndVideo() {
        assertEquals("audio", MediaFolders.folderFor(MediaFormat.Mp3))
        assertEquals("audio", MediaFolders.folderFor(MediaFormat.M4a))
        assertEquals("audio", MediaFolders.folderFor(MediaFormat.Flac))
        assertEquals("audio", MediaFolders.folderFor(MediaFormat.Wav))
        assertEquals("video", MediaFolders.folderFor(MediaFormat.Mp4))
        assertEquals("video", MediaFolders.folderFor(MediaFormat.WebM))
    }

    @Test
    fun addsSubfolderToDestination() {
        assertEquals("C:/Descargas/audio", MediaFolders.resolve("C:/Descargas", MediaFormat.Mp3))
        assertEquals("C:/Descargas/video", MediaFolders.resolve("C:/Descargas", MediaFormat.Mp4))
        assertEquals("/home/user/Descargas/audio", MediaFolders.resolve("/home/user/Descargas", MediaFormat.Flac))
    }

    @Test
    fun toleratesTrailingSeparators() {
        assertEquals("C:/Descargas/audio", MediaFolders.resolve("C:/Descargas/", MediaFormat.Mp3))
        assertEquals("C:/Descargas/audio", MediaFolders.resolve("C:/Descargas\\", MediaFormat.Mp3))
    }

    @Test
    fun doesNotNestTwice() {
        // Una reparación escribe en la carpeta del archivo dañado, que ya vive
        // dentro de la subcarpeta: no debe aparecer `audio/audio`.
        assertEquals("C:/Descargas/audio", MediaFolders.resolve("C:/Descargas/audio", MediaFormat.Mp3))
        assertEquals("C:/Descargas/video", MediaFolders.resolve("C:/Descargas/video", MediaFormat.Mp4))
    }

    @Test
    fun keepsEmptyDestinationUntouched() {
        assertEquals("", MediaFolders.resolve("", MediaFormat.Mp3))
    }

    @Test
    fun detectsFolderPointer() {
        assertTrue(MediaFolders.pointsToFolder("C:/Descargas/audio", "audio"))
        assertTrue(MediaFolders.pointsToFolder("C:/Descargas\\VIDEO", "video"))
        assertFalse(MediaFolders.pointsToFolder("C:/Descargas", "audio"))
        assertFalse(MediaFolders.pointsToFolder("", "audio"))
    }
}
