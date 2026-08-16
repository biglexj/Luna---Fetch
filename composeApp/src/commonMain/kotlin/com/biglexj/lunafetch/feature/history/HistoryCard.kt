package com.biglexj.lunafetch.feature.history

import com.biglexj.lunafetch.feature.components.LunaCard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.biglexj.lunafetch.domain.LunaFetchPresenter
import com.biglexj.lunafetch.domain.LunaFetchState
import com.biglexj.lunafetch.domain.PlatformBindings

@Composable
fun HistoryCard(
    state: LunaFetchState,
    presenter: LunaFetchPresenter,
    platform: PlatformBindings,
) {
    if (state.history.isEmpty()) return

    LunaCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Historial de descargas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            TextButton(onClick = presenter::clearHistory) {
                Text("Limpiar todo", style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.height(8.dp))
        state.history.forEach { item ->
            val isLocal = platform.isLocalPathAccessible(item.path)
            val isWindowsPath = item.path.matches(Regex("^[a-zA-Z]:[/\\\\].*"))
            val isAndroidContent = item.path.startsWith("content://")
            val isFromOtherDevice = (item.originDevice.isNotBlank() && !item.originDevice.equals(platform.deviceName, ignoreCase = true)) ||
                (!isLocal && isWindowsPath && platform.deviceOs == "android") ||
                (!isLocal && isAndroidContent && platform.deviceOs != "android")
            val deviceLabel = if (item.originDevice.isNotBlank() && !item.originDevice.equals(platform.deviceName, ignoreCase = true)) {
                item.originDevice
            } else if (isWindowsPath && platform.deviceOs == "android") {
                "PC"
            } else if (isAndroidContent && platform.deviceOs != "android") {
                "Móvil"
            } else {
                item.originDevice
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        item.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            item.formatLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (isFromOtherDevice) {
                            val icon = if (deviceLabel.contains("motorola", ignoreCase = true) || deviceLabel.contains("phone", ignoreCase = true) || deviceLabel.contains("android", ignoreCase = true) || deviceLabel == "Móvil") "📱" else "💻"
                            Text(
                                "• $icon $deviceLabel",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                            )
                        } else if (!isLocal && item.path.isNotBlank()) {
                            Text(
                                "• ⚠️ Archivo movido o eliminado",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
                                fontWeight = FontWeight.Normal,
                            )
                        }
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (item.url.isNotBlank()) {
                        HistoryActionButton(
                            icon = "🌐",
                            tooltip = "Ver enlace original en la web",
                            onClick = { platform.openUrl(item.url) },
                        )
                    }
                    if (isLocal) {
                        HistoryActionButton(
                            icon = "▶️",
                            tooltip = "Reproducir",
                            onClick = { presenter.playInPrisma(item.path) },
                        )
                        HistoryActionButton(
                            icon = "📁",
                            tooltip = "Abrir ubicación",
                            onClick = { platform.openDestinationFolder(item.path) },
                        )
                    } else if (item.url.isNotBlank()) {
                        HistoryActionButton(
                            icon = if (isFromOtherDevice) "⬇️" else "🔄",
                            tooltip = if (isFromOtherDevice) "Descargar en este equipo" else "Volver a descargar (archivo no encontrado)",
                            onClick = { presenter.redownloadHistoryItem(item) },
                        )
                    }
                    HistoryActionButton(
                        icon = "🗑️",
                        tooltip = "Eliminar del historial",
                        onClick = { presenter.removeFromHistory(item.id) },
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryActionButton(
    icon: String,
    tooltip: String,
    onClick: () -> Unit,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(androidx.compose.material3.TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(tooltip) } },
        state = rememberTooltipState(),
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(32.dp),
        ) {
            Text(icon, fontSize = 14.sp)
        }
    }
}

