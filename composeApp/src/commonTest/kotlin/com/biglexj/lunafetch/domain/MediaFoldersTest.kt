package com.biglexj.lunafetch.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MediaFoldersTest {

    @Test
    fun separatesAudioAndVideo() {
        assertEquals("Música", MediaFolders.folderFor(MediaFormat.Mp3))
        assertEquals("Música", MediaFolders.folderFor(MediaFormat.M4a))
        assertEquals("Música", MediaFolders.folderFor(MediaFormat.Flac))
        assertEquals("Música", MediaFolders.folderFor(MediaFormat.Wav))
        assertEquals("Videos", MediaFolders.folderFor(MediaFormat.Mp4))
        assertEquals("Videos", MediaFolders.folderFor(MediaFormat.WebM))
    }

    @Test
    fun addsSubfolderToDestination() {
        assertEquals("C:/Descargas/Música", MediaFolders.resolve("C:/Descargas", MediaFormat.Mp3))
        assertEquals("C:/Descargas/Videos", MediaFolders.resolve("C:/Descargas", MediaFormat.Mp4))
        assertEquals("/home/user/Descargas/Música", MediaFolders.resolve("/home/user/Descargas", MediaFormat.Flac))
    }

    @Test
    fun toleratesTrailingSeparators() {
        assertEquals("C:/Descargas/Música", MediaFolders.resolve("C:/Descargas/", MediaFormat.Mp3))
        assertEquals("C:/Descargas/Música", MediaFolders.resolve("C:/Descargas\\", MediaFormat.Mp3))
    }

    @Test
    fun doesNotNestTwice() {
        // Una reparación escribe en la carpeta del archivo dañado, que ya vive
        // dentro de la subcarpeta: no debe aparecer `Música/Música`.
        assertEquals("C:/Descargas/Música", MediaFolders.resolve("C:/Descargas/Música", MediaFormat.Mp3))
        assertEquals("C:/Descargas/Videos", MediaFolders.resolve("C:/Descargas/Videos", MediaFormat.Mp4))
    }

    @Test
    fun doesNotNestLegacyFolders() {
        // Los archivos descargados antes del cambio viven en `audio`/`video`:
        // repararlos no debe crear `audio/Música`.
        assertEquals("C:/Descargas/audio", MediaFolders.resolve("C:/Descargas/audio", MediaFormat.Mp3))
        assertEquals("C:/Descargas/video", MediaFolders.resolve("C:/Descargas/video", MediaFormat.Mp4))
    }

    @Test
    fun keepsEmptyDestinationUntouched() {
        assertEquals("", MediaFolders.resolve("", MediaFormat.Mp3))
    }

    @Test
    fun detectsFolderPointer() {
        assertTrue(MediaFolders.pointsToFolder("C:/Descargas/Música", "Música"))
        assertTrue(MediaFolders.pointsToFolder("C:/Descargas\\VIDEOS", "Videos"))
        assertTrue(MediaFolders.pointsToFolder("C:/Descargas/audio", "Música"))
        assertFalse(MediaFolders.pointsToFolder("C:/Descargas", "Música"))
        assertFalse(MediaFolders.pointsToFolder("", "Música"))
    }
}
