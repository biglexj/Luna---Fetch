package com.biglexj.lunafetch.feature.header

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.biglexj.lunafetch.domain.LunaFetchPresenter
import com.biglexj.lunafetch.domain.PlatformBindings

/**
 * Diálogo modular exclusivo para Configuraciones y Preferencias del Sistema.
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

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.widthIn(min = 320.dp, max = 520.dp).fillMaxWidth(0.92f),
        shape = RoundedCornerShape(28.dp),
        title = {
            Text("Configuración", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        },
        text = {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val isMobile = maxWidth < 380.dp
                val sectionSpacing = if (isMobile) 10.dp else 14.dp
                val dividerPadding = if (isMobile) 2.dp else 4.dp

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(sectionSpacing),
                ) {
                    // ── 1. Preferencias del Sistema ─────────────────────────────
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
                                    shape = RoundedCornerShape(50),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                                ) {
                                    Text("🔄 Sincronizar", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar", fontWeight = FontWeight.Bold)
            }
        },
    )
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
