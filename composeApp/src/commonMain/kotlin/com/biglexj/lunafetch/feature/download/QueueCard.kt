package com.biglexj.lunafetch.feature.download

import com.biglexj.lunafetch.feature.components.LunaCard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.biglexj.lunafetch.domain.HistoryPaginator
import com.biglexj.lunafetch.domain.LunaFetchPresenter
import com.biglexj.lunafetch.domain.LunaFetchState
import com.biglexj.lunafetch.domain.QueueItemStatus
import com.biglexj.lunafetch.domain.QueuedDownload
import com.biglexj.lunafetch.domain.sortedForDisplay
import com.biglexj.lunafetch.feature.header.LunaGlyph
import com.biglexj.lunafetch.feature.header.NavigationGlyph

/**
 * Resumen compacto de cola para la pantalla principal de Descargas.
 * Muestra cuántos enlaces hay en cola y un acceso directo para navegar
 * a la pestaña dedicada de Cola.
 */
@Composable
fun QueueSummaryCard(
    state: LunaFetchState,
    onNavigateToQueue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val queue = state.queue
    if (queue.isEmpty()) return

    val pending = queue.count { it.status == QueueItemStatus.Pending }
    val running = queue.firstOrNull { it.status == QueueItemStatus.Running }

    LunaCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f),
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(40.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        NavigationGlyph(LunaGlyph.Queue)
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    val title = if (running != null) {
                        "Descargando: ${running.title.ifBlank { running.url }}"
                    } else {
                        "Cola de descargas"
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (pending > 0) "$pending en espera" else "Todas completadas",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            TextButton(onClick = onNavigateToQueue) {
                Text("Ver cola ›", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/**
 * Pantalla completa de la cola de descargas: diseño paginado idéntico al
 * Historial (sin scroll interno: el número de filas por página se adapta
 * a la altura disponible en la ventana).
 */
@Composable
fun QueueCard(
    state: LunaFetchState,
    presenter: LunaFetchPresenter,
    emptyFallback: Boolean = false,
) {
    val queue = state.queue
    if (queue.isEmpty()) {
        if (!emptyFallback) return
        LunaCard(modifier = Modifier.fillMaxSize(), fillHeight = true) {
            Text("Cola de descargas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            Text(
                "No hay descargas en cola.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Los enlaces enviados desde la extensión del navegador o importados aparecerán aquí para descargarse automáticamente uno por uno.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val running = queue.firstOrNull { it.status == QueueItemStatus.Running }
        val headerHeight = if (running != null) 58.dp else 40.dp
        val footerHeight = 40.dp
        val cardPadding = 40.dp
        val availableForRows = maxHeight - headerHeight - footerHeight - cardPadding
        val rowHeight = 42.dp
        val dynamicPageSize = (availableForRows / rowHeight).toInt().coerceIn(3, 40)

        val displayQueue = queue.sortedForDisplay()
        val pageCount = HistoryPaginator.pageCount(displayQueue.size, dynamicPageSize)
        var page by remember(displayQueue.firstOrNull()?.id, displayQueue.size, dynamicPageSize) { mutableStateOf(0) }
        val currentPage = page.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        val pageItems = HistoryPaginator.itemsForPage(displayQueue, currentPage, dynamicPageSize)

        val pending = queue.count { it.status == QueueItemStatus.Pending }

        LunaCard(modifier = Modifier.fillMaxSize(), fillHeight = true) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        "Cola de descargas",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (pending > 0) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ) {
                            Text(
                                text = "$pending en espera",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (queue.any { it.isFinished }) {
                        TextButton(onClick = presenter::clearFinishedQueueItems) {
                            Text("Limpiar terminados", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (pending > 0) {
                        TextButton(onClick = presenter::clearPendingQueueItems) {
                            Text("Vaciar cola", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            if (running != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "⏳ Descargando: ${running.title.ifBlank { running.url }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(8.dp))

            Column(Modifier.weight(1f).fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    pageItems.forEach { item ->
                        QueueRow(item = item, presenter = presenter)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                    }
                }

                if (pageCount > 1) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = { page = (currentPage - 1).coerceAtLeast(0) },
                            enabled = currentPage > 0,
                        ) {
                            Text("‹ Anterior")
                        }
                        Text(
                            "Página ${currentPage + 1} de $pageCount · ${displayQueue.size} en cola",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(
                            onClick = { page = (currentPage + 1).coerceAtMost(pageCount - 1) },
                            enabled = currentPage < pageCount - 1,
                        ) {
                            Text("Siguiente ›")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueRow(
    item: QueuedDownload,
    presenter: LunaFetchPresenter,
    modifier: Modifier = Modifier,
) {
    val label = item.title.ifBlank { item.url }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = statusIcon(item.status),
            style = MaterialTheme.typography.titleSmall,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildString {
                    append(item.formatLabel)
                    append(" · ")
                    append(item.source)
                    if (item.isRepair) append(" · ♻️ reparación")
                    item.quality?.let { append(" · $it") }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            item.errorMessage?.let { message ->
                Text(
                    text = message.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (item.status == QueueItemStatus.Pending) {
            IconButton(
                onClick = { presenter.removeQueueItem(item.id) },
                modifier = Modifier.size(32.dp),
            ) {
                Text("✕", fontSize = 14.sp)
            }
        }
    }
}

private fun statusIcon(status: QueueItemStatus): String = when (status) {
    QueueItemStatus.Pending -> "⏳"
    QueueItemStatus.Running -> "⬇️"
    QueueItemStatus.Completed -> "✅"
    QueueItemStatus.Failed -> "❌"
    QueueItemStatus.Cancelled -> "⛔"
}
