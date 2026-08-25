package com.biglexj.lunafetch.feature.header

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.biglexj.lunafetch.domain.LunaFetchPresenter
import com.biglexj.lunafetch.domain.PlatformBindings

/**
 * Diálogo modular exclusivo para Configuraciones y Preferencias del Sistema.
 *
 * Sigue el estándar de Diálogos Modulares (ver `.agents/rules/design_system.md`):
 * `Dialog` + `Surface` propio, ancho y alto máximos controlados y botón cerrar flotante.
 */
@Composable
fun SettingsDialog(
    platform: PlatformBindings,
    presenter: LunaFetchPresenter,
    onDismiss: () -> Unit,
) {
    val state by presenter.state.collectAsState()
    var autoStart       by remember { mutableStateOf(platform.isAutoStartEnabled ?: false) }
    var minimizeToTray  by remember { mutableStateOf(platform.isMinimizeToTrayEnabled ?: false) }
    var nativeInstalled by remember { mutableStateOf(platform.isNativeHostInstalled ?: false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
                .pointerInput(Unit) { detectTapGestures(onTap = { onDismiss() }) },
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
                                "Configuración",
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

                        // ── 1. Preferencias del Sistema (Solo Desktop) ─────────────
                        val hasSystemSettings = platform.isAutoStartEnabled != null || platform.isMinimizeToTrayEnabled != null || platform.isNativeHostInstalled != null

                        if (hasSystemSettings) {
                            Text(
                                "Sistema y Ventana",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )

                            if (platform.isAutoStartEnabled != null) {
                                SettingsRow(
                                    title = "Iniciar con Windows",
                                    subtitle = "Abre Luna Fetch automáticamente al encender el equipo.",
                                    checked = autoStart,
                                    onCheckedChange = { autoStart = it; platform.setAutoStart(it) },
                                )
                                HorizontalDivider(modifier = Modifier.padding(vertical = dividerPadding), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                            }

                            if (platform.isMinimizeToTrayEnabled != null) {
                                SettingsRow(
                                    title = "Minimizar en lugar de cerrar",
                                    subtitle = "Al pulsar ✕, la app se oculta en la bandeja del sistema.",
                                    checked = minimizeToTray,
                                    onCheckedChange = { minimizeToTray = it; platform.setMinimizeToTray(it) },
                                )
                                HorizontalDivider(modifier = Modifier.padding(vertical = dividerPadding), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                            }

                            if (platform.isNativeHostInstalled != null) {
                                SettingsRow(
                                    title = "Extensión de navegador",
                                    subtitle = if (nativeInstalled)
                                        "Host registrado en Windows. Abre la página para agregar la extensión a Chrome/Edge."
                                    else
                                        "Registra el host para que Chrome/Edge puedan comunicarse con Luna Fetch.",
                                    checked = nativeInstalled,
                                    onCheckedChange = {
                                        nativeInstalled = it
                                        if (it) {
                                            platform.installNativeHost()
                                            platform.openUrl("https://github.com/biglexj/Luna---Fetch/tree/main/browser-extension#readme")
                                        } else {
                                            platform.uninstallNativeHost()
                                        }
                                    },
                                )
                                HorizontalDivider(modifier = Modifier.padding(vertical = dividerPadding), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                            }
                        }

                        // ── 2. Red Local (Aurora Synapse LAN) ───────────────────────
                        Text(
                            "Red Local (Aurora Synapse LAN)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            "Dispositivo actual: ${platform.deviceName} (${platform.deviceOs})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        if (state.discoveredPeers.isEmpty()) {
                            Text(
                                "Buscando otros dispositivos Luna en tu red Wi-Fi…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            )
                        } else {
                            state.discoveredPeers.forEach { peer ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("${peer.icon} ${peer.name}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                        Text("IP: ${peer.ip} • Puerto: ${peer.port}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    OutlinedButton(
                                        onClick = { presenter.syncHistoryWithPeer(peer) },
                                        modifier = Modifier.height(32.dp),
                                        shape = RoundedCornerShape(50),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                                    ) {
                                        Text("🔄 Sincronizar", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
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

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}