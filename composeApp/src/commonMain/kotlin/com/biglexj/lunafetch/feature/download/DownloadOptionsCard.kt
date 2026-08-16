package com.biglexj.lunafetch.feature.download

import com.biglexj.lunafetch.feature.components.LunaCard

import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import com.biglexj.lunafetch.domain.LunaFetchPresenter
import com.biglexj.lunafetch.domain.LunaFetchState
import com.biglexj.lunafetch.domain.MediaFormat
import com.biglexj.lunafetch.domain.PlatformBindings
import com.biglexj.lunafetch.domain.QualityOption
import com.biglexj.lunafetch.domain.isCollection

@Composable
fun DownloadOptionsCard(
    state: LunaFetchState,
    presenter: LunaFetchPresenter,
    platform: PlatformBindings,
) = LunaCard {
    Text("Descarga", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Selector(
            label = "Formato",
            selected = state.selectedFormat.displayName,
            options = MediaFormat.entries,
            optionLabel = MediaFormat::displayName,
            onSelected = presenter::selectFormat,
            modifier = Modifier.weight(1f),
            enabled = !state.isDownloading,
        )
        Selector(
            label = "Calidad",
            selected = state.selectedQuality.displayName,
            options = state.qualities,
            optionLabel = QualityOption::displayName,
            onSelected = presenter::selectQuality,
            modifier = Modifier.weight(1f),
            enabled = !state.isDownloading,
        )
    }
    if (state.selectedFormat.isAudio) {
        Spacer(Modifier.height(10.dp))
        Text(
            "Incluye los metadatos y portada disponibles.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    state.video?.takeIf { it.isCollection }?.let { collection ->
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Descargar colección completa", style = MaterialTheme.typography.labelLarge)
                Text(
                    "${collection.collectionCount} elementos",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = state.downloadCollection,
                onCheckedChange = presenter::setDownloadCollection,
                enabled = !state.isDownloading,
            )
        }
    }
    Spacer(Modifier.height(12.dp))
    Text("Destino", style = MaterialTheme.typography.labelLarge)
    Spacer(Modifier.height(6.dp))
    OutlinedButton(
        onClick = presenter::chooseDestination,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        enabled = !state.isDownloading,
        shape = RoundedCornerShape(20.dp),
    ) {
        Text(
            platform.destinationLabel(state.destination),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    Spacer(Modifier.height(14.dp))
    Button(
        onClick = presenter::download,
        modifier = Modifier.fillMaxWidth().height(54.dp),
        enabled = state.video != null && !state.isDownloading,
        shape = RoundedCornerShape(50),
    ) {
        Text(
            if (state.downloadCollection) "Descargar colección"
            else "Descargar ${state.selectedFormat.extension.uppercase()}",
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun <T> Selector(
    label: String,
    selected: String,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            shape = RoundedCornerShape(20.dp),
            border = androidx.compose.foundation.BorderStroke(
                width = if (expanded) 2.dp else 1.dp,
                color = if (expanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f),
            ),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Text(
                    selected,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface)
                .clip(RoundedCornerShape(16.dp)),
        ) {
            options.forEach { option ->
                val text = optionLabel(option)
                val isCurrent = text == selected
                DropdownMenuItem(
                    text = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                            if (isCurrent) {
                                Text(
                                    "✓",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelected(option)
                    },
                )
            }
        }
    }
}
