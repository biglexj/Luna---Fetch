package com.biglexj.lunafetch.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Verifica el registro de descargas y el cálculo de la carpeta que contiene
 * al archivo (necesaria para reemplazar un archivo dañado en su sitio).
 */
class DownloadRegistryTest {

    private fun entry(
        key: String,
        format: String = "mp4",
        path: String = "C:/Downloads/video.mp4",
    ) = DownloadRegistryEntry(
        canonicalKey = key,
        formatKey = format,
        path = path,
        title = "Video",
    )

    @Test
    fun findsTheEntryOfTheSameContentAndFormat() {
        val entries = listOf(entry("yt:uno"), entry("yt:dos", format = "mp3"))

        assertNotNull(DownloadRegistry.find(entries, "yt:uno", "mp4"))
        assertNotNull(DownloadRegistry.find(entries, "yt:dos", "mp3"))
        assertNull(DownloadRegistry.find(entries, "yt:dos", "mp4"))
        assertNull(DownloadRegistry.find(entries, "yt:tres", "mp4"))
    }

    @Test
    fun upsertReplacesTheEntryOfTheSameContentAndFormat() {
        val entries = listOf(entry("yt:uno", path = "C:/Downloads/antiguo.mp4"))

        val updated = DownloadRegistry.upsert(entries, entry("yt:uno", path = "C:/Downloads/nuevo.mp4"))

        assertEquals(1, updated.size)
        assertEquals("C:/Downloads/nuevo.mp4", updated.first().path)
    }

    @Test
    fun upsertKeepsOtherEntriesAndPutsTheNewestFirst() {
        val entries = listOf(entry("yt:uno"), entry("yt:dos"))

        val updated = DownloadRegistry.upsert(entries, entry("yt:tres"))

        assertEquals(listOf("yt:tres", "yt:uno", "yt:dos"), updated.map { it.canonicalKey })
    }

    @Test
    fun registryIsBounded() {
        val entries = (1..(DownloadRegistry.MAX_ENTRIES + 20)).map { entry("yt:$it") }

        val updated = DownloadRegistry.upsert(entries, entry("yt:nuevo"))

        assertEquals(DownloadRegistry.MAX_ENTRIES, updated.size)
        assertEquals("yt:nuevo", updated.first().canonicalKey)
    }

    @Test
    fun parentFolderIsResolvedForWindowsAndPosixPaths() {
        assertEquals("C:/Downloads", parentFolderOf("C:/Downloads/video.mp4"))
        assertEquals("C:\\Downloads", parentFolderOf("C:\\Downloads\\video.mp4"))
        assertEquals("/home/user", parentFolderOf("/home/user/video.mp4"))
    }

    @Test
    fun parentFolderIsEmptyForContentUrisAndBlankPaths() {
        assertEquals("", parentFolderOf("content://com.android.externalstorage/documents/1"))
        assertEquals("", parentFolderOf(""))
    }
}
