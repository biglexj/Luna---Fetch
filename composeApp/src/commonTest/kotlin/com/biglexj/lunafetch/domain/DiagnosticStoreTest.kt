package com.biglexj.lunafetch.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiagnosticStoreTest {

    @Test
    fun retentionWindowRespects48HoursCutoff() {
        val now = 1_000_000_000_000L
        val hourMs = 60 * 60 * 1000L

        val recentEntry = DiagnosticEntry(
            id = "recent-1",
            timestampMs = now - (24 * hourMs), // Hace 24 horas (válida)
            errorMessage = "Error reciente",
        )
        val limitEntry = DiagnosticEntry(
            id = "limit-1",
            timestampMs = now - (47 * hourMs), // Hace 47 horas (válida)
            errorMessage = "Error al límite",
        )
        val expiredEntry = DiagnosticEntry(
            id = "expired-1",
            timestampMs = now - (49 * hourMs), // Hace 49 horas (expirada)
            errorMessage = "Error expirado",
        )
        val veryOldEntry = DiagnosticEntry(
            id = "very-old-1",
            timestampMs = now - (100 * hourMs), // Hace 100 horas (expirada)
            errorMessage = "Error muy viejo",
        )

        assertFalse(recentEntry.isExpired(now))
        assertFalse(limitEntry.isExpired(now))
        assertTrue(expiredEntry.isExpired(now))
        assertTrue(veryOldEntry.isExpired(now))

        val list = listOf(recentEntry, limitEntry, expiredEntry, veryOldEntry)
        val purged = DiagnosticStore.purgeExpired(list, currentTimeMs = now)

        assertEquals(2, purged.size)
        assertTrue(purged.any { it.id == "recent-1" })
        assertTrue(purged.any { it.id == "limit-1" })
        assertFalse(purged.any { it.id == "expired-1" })
    }

    @Test
    fun addEntryInsertsAndPurgesAutomatically() {
        val now = 2_000_000_000_000L
        val hourMs = 60 * 60 * 1000L

        val old = DiagnosticEntry(id = "old", timestampMs = now - (50 * hourMs), errorMessage = "Old")
        val current = listOf(old)

        val newEntry = DiagnosticEntry(id = "new", timestampMs = now, errorMessage = "New error")

        val result = DiagnosticStore.addEntry(current, newEntry, currentTimeMs = now)
        assertEquals(1, result.size)
        assertEquals("new", result.first().id)
    }

    @Test
    fun formattingGeneratesStructuredReport() {
        val entry = DiagnosticEntry(
            id = "diag-123",
            timestampMs = 123456789L,
            url = "https://www.tiktok.com/@user/video/123",
            operation = "DOWNLOAD",
            errorMessage = "TikTok error de impersonación",
            technicalDetails = "WARNING: [TikTok] The extractor is attempting impersonation...",
            originDevice = "biglexj-pc",
        )

        val report = DiagnosticStore.formatEntryForReport(entry)
        assertTrue(report.contains("=== DIAGNÓSTICO LUNA FETCH [48H] ==="))
        assertTrue(report.contains("diag-123"))
        assertTrue(report.contains("TikTok error de impersonación"))
        assertTrue(report.contains("biglexj-pc"))
        assertTrue(report.contains("WARNING: [TikTok]"))
    }
}
