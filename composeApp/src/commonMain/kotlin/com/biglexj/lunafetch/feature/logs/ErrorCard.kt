package com.biglexj.lunafetch.feature.logs

import com.biglexj.lunafetch.feature.components.LunaCard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.biglexj.lunafetch.domain.LunaFetchState

/**
 * Mensaje del error de la última descarga.
 *
 * Vive aparte del registro de la sesión para poder colocarse justo debajo de
 * la cola de descargas, donde el usuario mira primero cuando algo falla.
 */
@Composable
fun DownloadErrorCard(state: LunaFetchState) {
    val error = state.error
    if (error.isNullOrBlank()) return

    LunaCard {
        Surface(
            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("⚠️", style = MaterialTheme.typography.titleMedium)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Detalles del error",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Text(
                        error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }
    }
}
