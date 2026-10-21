package com.biglexj.lunafetch.feature.download

import com.biglexj.lunafetch.feature.components.LunaCard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.biglexj.lunafetch.domain.LunaFetchPresenter
import com.biglexj.lunafetch.domain.LunaFetchState
import com.biglexj.lunafetch.domain.QueueItemStatus
import com.biglexj.lunafetch.domain.QueuedDownload

/**
 * Cola de descargas: muestra los enlaces enviados desde la extensión, Synapse
 * o el historial, en el orden exacto en que serán procesados.
 */
@Composable
fun QueueCard(state: LunaFetchState, presenter: LunaFetchPresenter) {
    val queue = state.queue
    if (queue.isEmpty()) return

    val pending = queue.count { it.status == QueueItemStatus.Pending }
    val running = queue.firstOrNull { it.status == QueueItemStatus.Running }

    LunaCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Cola de descargas",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Text(
                    text = if (pending > 0) "$pending en espera" else "Sin pendientes",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
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

        Spacer(Modifier.height(10.dp))
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            queue.forEach { item ->
                QueueRow(item = item, presenter = presenter)
            }
        }

        if (queue.any { it.isFinished } || pending > 0) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (queue.any { it.isFinished }) {
                    TextButton(onClick = presenter::clearFinishedQueueItems) {
                        Text("Limpiar terminados", style = MaterialTheme.typography.labelMedium)
                    }
                }
                if (pending > 0) {
                    TextButton(onClick = presenter::clearPendingQueueItems) {
                        Text("Vaciar cola", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueRow(item: QueuedDownload, presenter: LunaFetchPresenter) {
    val label = item.title.ifBlank { item.url }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = statusIcon(item.status),
            style = MaterialTheme.typography.titleSmall,
        )
        Spacer(Modifier.width(10.dp))
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
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (item.status == QueueItemStatus.Pending) {
            Spacer(Modifier.width(8.dp))
            TextButton(
                onClick = { presenter.removeQueueItem(item.id) },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                modifier = Modifier.size(width = 40.dp, height = 32.dp),
            ) {
                Text("✕", style = MaterialTheme.typography.labelLarge)
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
