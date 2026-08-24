package com.biglexj.lunafetch.feature.header

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.biglexj.lunafetch.domain.EngineChannel
import com.biglexj.lunafetch.domain.LunaFetchPresenter
import com.biglexj.lunafetch.domain.PlatformBindings
import kotlinx.coroutines.launch

/**
 * Diálogo modular exclusivo para el Centro de Actualizaciones,
 * Controladores del Motor, Información del Proyecto y Comunidad.
 */
@Composable
fun AboutUpdatesDialog(
    platform: PlatformBindings,
    presenter: LunaFetchPresenter,
    onDismiss: () -> Unit,
) {
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

    // Si hay una versión nueva, cerrar "Acerca de" y dejar paso al modal de actualización
    LaunchedEffect(state.showUpdateModal) {
        if (state.showUpdateModal) onDismiss()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            val isMobile = maxWidth < 380.dp
            val sectionSpacing = if (isMobile) 7.dp else 10.dp
            val dividerPadding = if (isMobile) 1.dp else 2.dp

Surface(
                modifier = Modifier
                    .widthIn(min = 320.dp, max = 520.dp)
                    .fillMaxWidth(0.92f)
                    .heightIn(max = maxHeight * 0.90f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { /* consume clicks to prevent dismiss on content tap */ },
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                shadowElevation = 6.dp,
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 14.dp)
                            .padding(bottom = 44.dp),
                        verticalArrangement = Arrangement.spacedBy(sectionSpacing),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                "Acerca de & Actualizaciones",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "v1.1.7",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = dividerPadding), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))

                        // ── 1. Centro de Actualizaciones de la Aplicación ───────────
                        Text(
                            "Actualizaciones de la Aplicación",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            "Mantén Luna Fetch al día con las últimas mejoras, correcciones y funciones del ecosistema.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        OutlinedButton(
                            onClick = { presenter.checkForUpdates(manual = true) },
                            modifier = Modifier.fillMaxWidth().height(if (isMobile) 40.dp else 38.dp),
                            shape = RoundedCornerShape(50),
                        ) {
                            Text("🚀 Buscar Actualizaciones de la App", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                        }

                        if (state.updateMessage != null) {
                            Text(
                                state.updateMessage!!,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = if (state.updateMessage.orEmpty().startsWith("⚠️")) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            )
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = dividerPadding), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))

                        // ── 2. Controladores del Motor (yt-dlp & ffmpeg) ──────────
                        Text(
                            "Controladores del Motor Multimedia",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(
                                    "Componentes de extracción y conversión",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    if (componentStatus.isNotBlank()) "Estado: $componentStatus" else "yt-dlp y ffmpeg integrados.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            OutlinedButton(
                                onClick = {
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
                                enabled = !isUpdatingComponents,
                                shape = RoundedCornerShape(50),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                            ) {
                                if (isUpdatingComponents) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Text("🔄 Actualizar", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }

                        Text(
                            "Canal del motor",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            EngineChannel.entries.forEachIndexed { index, channel ->
                                SegmentedButton(
                                    selected = selectedChannel == channel,
                                    onClick = {
                                        selectedChannel = channel
                                        scope.launch { platform.setEngineChannel(channel.wire) }
                                    },
                                    shape = SegmentedButtonDefaults.itemShape(index = index, count = EngineChannel.entries.size),
                                ) {
                                    Text(channel.label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }

                        if (componentMessage != null) {
                            Text(
                                componentMessage!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                            )
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = dividerPadding), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))

                        // ── 3. Acerca de y Comunidad ────────────────────────────────
                        Text(
                            "Acerca de Luna Fetch",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            "Herramienta moderna y gratuita para descargar videos, audio y listas de YouTube, TikTok sin marca de agua, Instagram y más.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Button(
                            onClick = { platform.openUrl("https://www.biglexj.com/donaciones") },
                            modifier = Modifier.fillMaxWidth().height(if (isMobile) 40.dp else 36.dp),
                            shape = RoundedCornerShape(50),
                            contentPadding = PaddingValues(vertical = 2.dp),
                        ) {
                            Text("Donar (Yape / Plin / Web) 🤍", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { platform.openUrl("https://buymeacoffee.com/biglexj") },
                                modifier = Modifier.weight(1f).height(if (isMobile) 34.dp else 32.dp),
                                shape = RoundedCornerShape(50),
                                contentPadding = PaddingValues(vertical = 2.dp),
                            ) {
                                Text("☕ Buy Me a Coffee", style = MaterialTheme.typography.labelSmall)
                            }
                            OutlinedButton(
                                onClick = { platform.openUrl("https://github.com/biglexj") },
                                modifier = Modifier.weight(1f).height(if (isMobile) 34.dp else 32.dp),
                                shape = RoundedCornerShape(50),
                                contentPadding = PaddingValues(vertical = 2.dp),
                            ) {
                                Text("⭐ GitHub", style = MaterialTheme.typography.labelSmall)
                            }
                        }

                        OutlinedButton(
                            onClick = { platform.openUrl("https://github.com/biglexj/Luna---Fetch/issues") },
                            modifier = Modifier.fillMaxWidth().height(if (isMobile) 34.dp else 32.dp),
                            shape = RoundedCornerShape(50),
                            contentPadding = PaddingValues(vertical = 2.dp),
                        ) {
                            Text("💬 Enviar Feedback / Reportar Error", style = MaterialTheme.typography.labelSmall)
                        }

                        Text(
                            "Copyright © 2026 Biglex J. Todos los derechos reservados.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        )
                    }

                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 12.dp, bottom = 10.dp)
                            .clickable(role = Role.Button, onClickLabel = "Cerrar", onClick = onDismiss),
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shadowElevation = 4.dp,
                    ) {
                        Text(
                            "✕",
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
    }
}