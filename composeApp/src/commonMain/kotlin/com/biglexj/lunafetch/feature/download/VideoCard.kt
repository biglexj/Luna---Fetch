package com.biglexj.lunafetch.feature.download

import com.biglexj.lunafetch.feature.components.LunaCard
import com.biglexj.lunafetch.feature.components.formatDuration

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.biglexj.lunafetch.domain.CollectionEntry
import com.biglexj.lunafetch.domain.LunaFetchPresenter
import com.biglexj.lunafetch.domain.LunaFetchState
import com.biglexj.lunafetch.domain.VideoInfo
import com.biglexj.lunafetch.domain.isCollection

@Composable
fun VideoCard(state: LunaFetchState, presenter: LunaFetchPresenter) {
    val video = state.video ?: return
    if (video.isCollection) {
        CollectionEntriesCard(video, state.selectedFormat.isAudio)
        return
    }
    val openModifier = if (state.completedOutput != null) {
        Modifier.clickable(onClick = presenter::openCompletedOutput)
    } else {
        Modifier
    }
    LunaCard(modifier = openModifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            val thumbnailModifier = if (state.selectedFormat.isAudio) {
                Modifier.size(88.dp)
            } else {
                Modifier.size(width = 150.dp, height = 88.dp)
            }
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = "Miniatura de ${video.title}",
                modifier = thumbnailModifier.clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    video.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(video.uploader, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
                val detail = if (video.isCollection) {
                    "Lista · ${video.collectionCount} elementos"
                } else {
                    formatDuration(video.durationSeconds)
                }
                Text(detail, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
fun CollectionEntriesCard(video: VideoInfo, isAudio: Boolean) {
    val entries = video.collectionEntries
    if (entries.isEmpty()) return

    val totalEntries = entries.size
    var visibleLimit by remember(video.url, totalEntries) { mutableStateOf(5) }
    val displayedEntries = if (totalEntries <= 5) entries else entries.take(visibleLimit)

    LunaCard(modifier = Modifier.animateContentSize()) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            CoverThumbnail(video.thumbnailUrl, "Portada de ${video.collectionTitle ?: "la colección"}", isAudio)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    video.collectionTitle ?: "Lista de reproducción",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(video.uploader, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "$totalEntries canciones detectadas",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        displayedEntries.forEach { CollectionEntryRow(it) }

        if (totalEntries > 5) {
            Spacer(Modifier.height(10.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Mostrando ${displayedEntries.size} de $totalEntries canciones",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (visibleLimit > 5) {
                        TextButton(
                            onClick = { visibleLimit = 5 },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Text("Mostrar menos", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                if (visibleLimit < totalEntries) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = { visibleLimit = minOf(visibleLimit + 10, totalEntries) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(50),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Text("Mostrar más (+10)", style = MaterialTheme.typography.labelMedium)
                        }
                        OutlinedButton(
                            onClick = { visibleLimit = totalEntries },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(50),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Text("Mostrar todo", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CoverThumbnail(model: String, description: String, isAudio: Boolean) {
    Box(
        modifier = (if (isAudio) Modifier.size(88.dp) else Modifier.size(width = 150.dp, height = 88.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = model,
            contentDescription = description,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
    }
}

@Composable
fun CollectionEntryRow(entry: CollectionEntry) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            entry.index.toString().padStart(2, '0'),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelLarge,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(entry.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (entry.uploader.isNotBlank()) {
                Text(
                    entry.uploader,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (entry.durationSeconds > 0) {
            Text(
                formatDuration(entry.durationSeconds),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
