package com.biglexj.lunafetch.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Verifica el reparto del historial en páginas de once descargas.
 */
class HistoryPaginatorTest {

    @Test
    fun pageCountFollowsTheNumberOfDownloads() {
        assertEquals(0, HistoryPaginator.pageCount(0))
        assertEquals(1, HistoryPaginator.pageCount(1))
        assertEquals(1, HistoryPaginator.pageCount(11))
        assertEquals(2, HistoryPaginator.pageCount(12))
        assertEquals(3, HistoryPaginator.pageCount(25))
    }

    @Test
    fun eachPageShowsElevenDownloads() {
        val items = (1..25).toList()

        assertEquals((1..11).toList(), HistoryPaginator.itemsForPage(items, 0))
        assertEquals((12..22).toList(), HistoryPaginator.itemsForPage(items, 1))
        assertEquals((23..25).toList(), HistoryPaginator.itemsForPage(items, 2))
    }

    @Test
    fun pagesOutOfRangeAreClamped() {
        val items = (1..13).toList()

        assertEquals((1..11).toList(), HistoryPaginator.itemsForPage(items, -5))
        assertEquals((12..13).toList(), HistoryPaginator.itemsForPage(items, 99))
    }

    @Test
    fun anEmptyHistoryHasNoPages() {
        assertTrue(HistoryPaginator.itemsForPage(emptyList<Int>(), 0).isEmpty())
    }
}
