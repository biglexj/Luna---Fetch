package com.biglexj.lunafetch.feature.header

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.biglexj.lunafetch.domain.AppConfig
import com.biglexj.lunafetch.feature.components.ActionButton
import com.biglexj.lunafetch.domain.EngineChannel
import com.biglexj.lunafetch.domain.LunaFetchPresenter
import com.biglexj.lunafetch.domain.LunaFetchState
import com.biglexj.lunafetch.domain.PlatformBindings
import kotlinx.coroutines.launch

/**
 * Información y actualizaciones integradas en Configuración.
 *
 * Disposición compacta: actualizaciones de Luna y motor de Luna (yt-dlp) van en dos
 * columnas cuando hay ancho suficiente; la ficha del proyecto ocupa el ancho completo.
 */
@Composable
fun AboutUpdatesContent(platform: PlatformBindings, presenter: LunaFetchPresenter) {
    val state by presenter.state.collectAsState()
    var componentStatus by remember { mutableStateOf("") }
    var componentMessage by remember { mutableStateOf<String?>(null) }
    var isUpdatingComponents by remember { mutableStateOf(false) }
    var selectedChannel by remember { mutableStateOf(EngineChannel.STABLE) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        componentStatus = platform.getEngineComponentStatus()
        selectedChannel = EngineChannel.fromWire(platform.getEngineChannel())
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "Acerca de",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "v${AppConfig.APP_VERSION}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        val engineCard: @Composable () -> Unit = {
            EngineCard(
                componentStatus = componentStatus,
                componentMessage = componentMessage,
                isUpdating = isUpdatingComponents,
                selectedChannel = selectedChannel,
                onChannelSelected = { channel ->
                    selectedChannel = channel
                    scope.launch { platform.setEngineChannel(channel.wire) }
                },
                onUpdate = {
                    isUpdatingComponents = true
                    componentMessage = null
                    scope.launch {
                        val res = platform.updateEngineComponents(selectedChannel.wire)
                        componentMessage = res.getOrDefault("Componentes al día")
                        componentStatus = platform.getEngineComponentStatus()
                        selectedChannel = EngineChannel.fromWire(platform.getEngineChannel())
                        isUpdatingComponents = false
                    }
                },
            )
        }

        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val wide = maxWidth >= 620.dp
            if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) { UpdateCard(state, presenter) }
                    Column(Modifier.weight(1f)) { engineCard() }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    UpdateCard(state, presenter)
                    engineCard()
                }
            }
        }

        AboutCard(
            title = "Acerca de Luna Fetch",
            subtitle = "Herramienta moderna y gratuita para descargar videos, audio y listas de " +
                "YouTube, TikTok sin marca de agua, Instagram y más.",
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ActionButton(
                    text = "Apoyar el proyecto",
                    onClick = { platform.openUrl("https://www.biglexj.com/donaciones") },
                    modifier = Modifier.weight(1f),
                )
                ActionButton(
                    text = "☕ Buy Me a Coffee",
                    onClick = { platform.openUrl("https://buymeacoffee.com/biglexj") },
                    modifier = Modifier.weight(1f),
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ActionButton(
                    text = "⭐ GitHub",
                    onClick = { platform.openUrl("https://github.com/biglexj") },
                    modifier = Modifier.weight(1f),
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ActionButton(
                    text = "Enviar comentarios",
                    onClick = { platform.openUrl("https://github.com/biglexj/Luna---Fetch/issues") },
                    modifier = Modifier.weight(1f),
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Text(
            "Creado por biglexj · Licencia MIT · 2026. Gracias por usar Luna Fetch.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Actualizaciones de Luna: buscar una versión nueva y ver el resultado. */
@Composable
private fun UpdateCard(state: LunaFetchState, presenter: LunaFetchPresenter) {
    AboutCard(
        title = "Actualizaciones de Luna",
        subtitle = "Mantén Luna Fetch al día con las últimas mejoras y correcciones del ecosistema.",
    ) {
        ActionButton(
            text = "Buscar actualizaciones",
            onClick = { presenter.checkForUpdates(manual = true) },
            modifier = Modifier.fillMaxWidth(),
        )
        state.updateMessage?.let { message ->
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = if (message.startsWith("⚠️")) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
        }
    }
}

/** Motor de Luna (yt-dlp): estado de componentes, canal y actualización. */
@Composable
private fun EngineCard(
    componentStatus: String,
    componentMessage: String?,
    isUpdating: Boolean,
    selectedChannel: EngineChannel,
    onChannelSelected: (EngineChannel) -> Unit,
    onUpdate: () -> Unit,
) {
    AboutCard(
        title = "Motor de Luna",
        subtitle = "Componentes de extracción y conversión (yt-dlp y ffmpeg).",
    ) {
        Text(
            if (componentStatus.isNotBlank()) "Estado: $componentStatus" else "yt-dlp y ffmpeg integrados.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            EngineChannel.entries.forEach { channel ->
                ChoicePill(
                    label = channel.label,
                    selected = selectedChannel == channel,
                    onClick = { onChannelSelected(channel) },
                )
            }
        }
        ActionButton(
            text = if (isUpdating) "Actualizando…" else "Actualizar motor",
            onClick = onUpdate,
            enabled = !isUpdating,
            modifier = Modifier.fillMaxWidth(),
        )
        componentMessage?.let { message ->
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** Tarjeta base del panel: mismo lenguaje visual que el resto de Ajustes. */
@Composable
private fun AboutCard(
    title: String,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            content()
        }
    }
}
