package com.biglexj.lunafetch.feature.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.biglexj.lunafetch.domain.LunaFetchPresenter
import com.biglexj.lunafetch.domain.LunaFetchState

@Composable
fun LinkCard(state: LunaFetchState, presenter: LunaFetchPresenter) = LunaCard {
    Text("Enlace", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = state.url,
            onValueChange = presenter::setUrl,
            modifier = Modifier.weight(1f),
            enabled = !state.isAnalyzing && !state.isDownloading,
            singleLine = true,
            label = { Text("URL del video") },
            shape = RoundedCornerShape(14.dp),
        )
        Button(
            onClick = presenter::analyze,
            enabled = !state.isAnalyzing && !state.isDownloading,
            modifier = Modifier.height(56.dp),
            shape = RoundedCornerShape(14.dp),
        ) {
            Text(if (state.isAnalyzing) "Analizando…" else "Analizar")
        }
    }
    state.error?.let {
        Spacer(Modifier.height(8.dp))
        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
}
