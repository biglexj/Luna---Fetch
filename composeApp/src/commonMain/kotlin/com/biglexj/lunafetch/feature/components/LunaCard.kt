package com.biglexj.lunafetch.feature.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Tarjeta base de la interfaz.
 *
 * Cuando la tarjeta es pulsable se usa la variante `Card(onClick = …)`, que
 * dibuja la indicación de hover y de presión sobre la propia superficie: así el
 * resplandor respeta las esquinas redondeadas en lugar de mostrar un marco
 * rectangular alrededor de la tarjeta.
 */
@Composable
fun LunaCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    val border = BorderStroke(
        1.dp,
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
    )
    val elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)

    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            colors = colors,
            elevation = elevation,
            border = border,
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(20.dp), content = content)
        }
    } else {
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            colors = colors,
            border = border,
            elevation = elevation,
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(20.dp), content = content)
        }
    }
}

fun formatDuration(seconds: Double): String {
    val total = seconds.toLong().coerceAtLeast(0)
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val remaining = total % 60
    val minuteText = minutes.toString().padStart(2, '0')
    val secondText = remaining.toString().padStart(2, '0')
    return if (hours > 0) "$hours:$minuteText:$secondText" else "$minutes:$secondText"
}
