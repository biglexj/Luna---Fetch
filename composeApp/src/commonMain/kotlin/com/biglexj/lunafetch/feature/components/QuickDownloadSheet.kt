package com.biglexj.lunafetch.feature.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.biglexj.lunafetch.domain.LunaFetchPresenter
import com.biglexj.lunafetch.domain.LunaFetchState
import com.biglexj.lunafetch.domain.MediaFormat
import com.biglexj.lunafetch.domain.QualityOption
import com.biglexj.lunafetch.domain.isCollection

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun QuickDownloadSheet(
    state: LunaFetchState,
    presenter: LunaFetchPresenter,
    onDismiss: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.28f)) {
        ModalBottomSheet(onDismissRequest = onDismiss) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("Descarga rápida", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                when {
                    state.isAnalyzing -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
                        Text("Analizando enlace…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    state.video != null -> {
                        val video = state.video
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            CoverThumbnail(video.thumbnailUrl, "Miniatura de ${video.title}", state.selectedFormat.isAudio)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(video.collectionTitle ?: video.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(video.uploader, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (video.isCollection) Text("${video.collectionCount} canciones", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Text("Formato", style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(MediaFormat.Mp3, MediaFormat.Mp4).forEach { format ->
                                val selected = state.selectedFormat == format
                                if (selected) Button(onClick = { }, modifier = Modifier.weight(1f), enabled = false) { Text(format.displayName) }
                                else OutlinedButton(onClick = { presenter.selectFormat(format) }, modifier = Modifier.weight(1f)) { Text(format.displayName) }
                            }
                        }
                        Text("Calidad", style = MaterialTheme.typography.labelLarge)
                        Selector(
                            label = "Calidad",
                            selected = state.selectedQuality.displayName,
                            options = state.qualities,
                            optionLabel = QualityOption::displayName,
                            onSelected = presenter::selectQuality,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.isDownloading,
                        )
                        Button(
                            onClick = {
                                presenter.download()
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            enabled = !state.isDownloading,
                        ) { Text(if (state.isDownloading) "Descargando…" else if (video.isCollection) "Descargar colección" else "Iniciar descarga") }
                    }
                }
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Cancelar") }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}
