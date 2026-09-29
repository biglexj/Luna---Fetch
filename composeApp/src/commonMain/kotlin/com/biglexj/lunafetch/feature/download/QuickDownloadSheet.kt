package com.biglexj.lunafetch.feature.download

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.biglexj.lunafetch.domain.LunaFetchPresenter
import com.biglexj.lunafetch.domain.LunaFetchState
import com.biglexj.lunafetch.domain.MediaFormat
import com.biglexj.lunafetch.domain.PlatformBindings
import com.biglexj.lunafetch.domain.isCollection
import java.net.URI

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun QuickDownloadSheet(
    state: LunaFetchState,
    presenter: LunaFetchPresenter,
    platform: PlatformBindings,
    onDismiss: () -> Unit,
) {
    var selectedFormat by remember {
        mutableStateOf(if (state.selectedFormat.isAudio) MediaFormat.Mp3 else MediaFormat.Mp4)
    }

    val currentUrl = state.video?.url ?: state.url
    val domain = remember(currentUrl) {
        runCatching {
            val host = URI(currentUrl).host.orEmpty()
            host.removePrefix("www.").ifBlank { "Enlace web" }
        }.getOrDefault("Enlace web")
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.28f)) {
        ModalBottomSheet(onDismissRequest = onDismiss) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // ── Header ───────────────────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Descarga rápida", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    if (state.isAnalyzing) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Text(
                                "Escaneando…",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                // ── Preview (Thumbnail si ya cargó, o Pill de dominio/enlace) ────
                val video = state.video
                if (video != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth().animateContentSize(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CoverThumbnail(
                            model = video.thumbnailUrl,
                            description = "Miniatura de ${video.title}",
                            isAudio = selectedFormat.isAudio,
                            sourceUrl = video.url,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                video.collectionTitle ?: video.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                video.uploader.ifBlank { domain },
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (video.isCollection) {
                                Text(
                                    "${video.collectionCount} canciones detectadas",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(if (selectedFormat.isAudio) "🎵" else "🎬", style = MaterialTheme.typography.titleMedium)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    domain,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    currentUrl,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }

                // ── Pestaña Música o Video (Segmented Tab Bar) ────────────────────
                Text("Tipo de descarga", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    val options = listOf(MediaFormat.Mp4 to "🎬 Video", MediaFormat.Mp3 to "🎵 Música")
                    options.forEach { (format, label) ->
                        val selected = selectedFormat == format
                        val bg = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                        val textColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(bg)
                                .clickable {
                                    selectedFormat = format
                                    presenter.selectFormat(format)
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = textColor,
                            )
                        }
                    }
                }

                // ── Mejor Calidad Garantizada (Auto) ──────────────────────────────
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(if (selectedFormat.isAudio) "🎵" else "✨", style = MaterialTheme.typography.titleMedium)
                        Column {
                            Text(
                                if (selectedFormat.isAudio) "Audio de máxima fidelidad (MP3 · 320 kbps)"
                                else "Resolución máxima disponible (1080p / 4K / HD)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "Selección automática de la mejor calidad",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                // ── Destino ───────────────────────────────────────────────────────
                Text("Destino", style = MaterialTheme.typography.labelLarge)
                OutlinedButton(
                    onClick = presenter::chooseDestination,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (state.destination.isBlank()) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                                         else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                        contentColor = if (state.destination.isBlank()) MaterialTheme.colorScheme.error
                                       else MaterialTheme.colorScheme.onSurface,
                    ),
                    border = BorderStroke(
                        width = if (state.destination.isBlank()) 1.5.dp else 1.dp,
                        color = if (state.destination.isBlank()) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    ),
                ) {
                    Text(
                        if (state.destination.isBlank()) "⚠️ Seleccionar carpeta de destino"
                        else "📂 " + platform.destinationLabel(state.destination),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = if (state.destination.isBlank()) FontWeight.Bold else FontWeight.Normal,
                    )
                }

                // ── Botón Principal de Descarga Inmediata (Flujo Inverso) ─────────
                Button(
                    onClick = {
                        if (state.destination.isBlank()) {
                            presenter.showToast("⚠️ Selecciona una carpeta de destino para guardar")
                            presenter.chooseDestination()
                        } else {
                            val targetUrl = state.video?.url ?: state.url
                            presenter.startDirectDownload(targetUrl, if (selectedFormat.isAudio) "mp3" else "mp4")
                            onDismiss()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(50),
                ) {
                    Text(
                        if (selectedFormat.isAudio) "🎵 Descargar Música" else "🎬 Descargar Video",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Text(
                    "⚡ Se procesará en segundo plano con notificación activa",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                )

                // ── Aurora Synapse LAN Peers (Opcional si hay dispositivos) ───────
                if (state.discoveredPeers.isNotEmpty()) {
                    state.discoveredPeers.forEach { peer ->
                        OutlinedButton(
                            onClick = {
                                presenter.pushDownloadToPeer(peer)
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                                contentColor = MaterialTheme.colorScheme.primary,
                            ),
                            border = BorderStroke(
                                width = 1.5.dp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                            ),
                        ) {
                            Text("🚀 Mandar a descargar a ${peer.name}", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(50)) {
                    Text("Cancelar")
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}
