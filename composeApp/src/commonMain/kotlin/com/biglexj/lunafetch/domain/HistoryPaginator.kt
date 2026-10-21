package com.biglexj.lunafetch.domain

/**
 * Paginación del historial de descargas.
 *
 * El historial guarda más descargas de las que caben en pantalla: la tarjeta
 * muestra diez por página con su propio selector, en vez de un scroll
 * infinito.
 */
object HistoryPaginator {

    /** Descargas que se muestran por página. */
    const val PAGE_SIZE = 10

    /** Número de páginas necesarias para mostrar [total] descargas. */
    fun pageCount(total: Int): Int {
        if (total <= 0) return 0
        return (total + PAGE_SIZE - 1) / PAGE_SIZE
    }

    /**
     * Descargas de la página [page] (base cero).
     *
     * @return Los elementos de esa página, o una lista vacía si la página no
     * existe.
     */
    fun <T> itemsForPage(items: List<T>, page: Int): List<T> {
        val pages = pageCount(items.size)
        if (pages == 0) return emptyList()
        val safePage = page.coerceIn(0, pages - 1)
        return items.drop(safePage * PAGE_SIZE).take(PAGE_SIZE)
    }
}
