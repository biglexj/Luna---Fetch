package com.biglexj.lunafetch.domain

/**
 * Paginación del historial de descargas.
 *
 * El historial guarda más descargas de las que caben en pantalla: la tarjeta
 * ocupa todo el alto disponible y muestra una página por vez, con su propio
 * selector, en vez de un scroll infinito. Cuántas entran en la página depende
 * del alto de la ventana, así que el tamaño de página se puede ajustar.
 */
object HistoryPaginator {

    /** Descargas que se muestran por página cuando no hay medida de la ventana. */
    const val PAGE_SIZE = 11

    /** Número de páginas necesarias para mostrar [total] descargas. */
    fun pageCount(total: Int, pageSize: Int = PAGE_SIZE): Int {
        if (total <= 0) return 0
        val size = pageSize.coerceAtLeast(1)
        return (total + size - 1) / size
    }

    /**
     * Descargas de la página [page] (base cero).
     *
     * @return Los elementos de esa página, o una lista vacía si la página no
     * existe.
     */
    fun <T> itemsForPage(items: List<T>, page: Int, pageSize: Int = PAGE_SIZE): List<T> {
        val pages = pageCount(items.size, pageSize)
        if (pages == 0) return emptyList()
        val size = pageSize.coerceAtLeast(1)
        val safePage = page.coerceIn(0, pages - 1)
        return items.drop(safePage * size).take(size)
    }
}
