package com.biglexj.lunafetch.feature.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.biglexj.lunafetch.domain.DownloadPhase
import com.biglexj.lunafetch.domain.LunaFetchPresenter
import com.biglexj.lunafetch.domain.LunaFetchState

@Composable
fun DownloadStatusCard(state: LunaFetchState, presenter: LunaFetchPresenter) {
    val progress = state.progress ?: return
    LunaCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(progress.statusMessage, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            Text("${progress.percentage.toInt()} %", color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(10.dp))
        LinearProgressIndicator(
            progress = { (progress.percentage / 100.0).toFloat() },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
        )
        if (progress.speed.isNotBlank() || progress.size.isNotBlank() || progress.eta.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(
                listOfNotNull(
                    progress.speed.takeIf(String::isNotBlank),
                    progress.size.takeIf(String::isNotBlank),
                    progress.eta.takeIf(String::isNotBlank)?.let { "ETA $it" },
                ).joinToString("  ·  "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (state.isDownloading) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = presenter::cancel, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                Text("Cancelar")
            }
        } else if (progress.phase == DownloadPhase.Completed && state.completedOutput != null) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = presenter::openCompletedOutput) { Text("Abrir resultado") }
        }
    }
}
