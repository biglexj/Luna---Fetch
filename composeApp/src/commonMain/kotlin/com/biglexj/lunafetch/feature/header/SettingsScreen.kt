package com.biglexj.lunafetch.feature.header

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.biglexj.lunafetch.core.theme.ThemeMode
import com.biglexj.lunafetch.feature.components.ActionButton
import com.biglexj.lunafetch.domain.LunaFetchPresenter
import com.biglexj.lunafetch.domain.PlatformBindings

@Composable
fun SettingsScreen(
    platform: PlatformBindings,
    presenter: LunaFetchPresenter,
    themeMode: ThemeMode,
    onThemeSelected: (ThemeMode) -> Unit,
) {
    var selectedTab by remember { mutableStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Text("Ajustes", Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        TabRow(selectedTabIndex = selectedTab) {
            listOf("General", "Synapse", "Acerca de").forEachIndexed { index, title ->
                Tab(selected = selectedTab == index, onClick = { selectedTab = index }, text = { Text(title) })
            }
        }
        BoxWithConstraints(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
            when (selectedTab) {
                0 -> GeneralSettings(platform, themeMode, onThemeSelected, maxWidth >= 720.dp)
                1 -> SynapseSettings(platform, presenter)
                else -> AboutUpdatesContent(platform, presenter)
            }
        }
    }
}

@Composable
private fun GeneralSettings(platform: PlatformBindings, mode: ThemeMode, onMode: (ThemeMode) -> Unit, wide: Boolean) {
    var autoStart by remember { mutableStateOf(platform.isAutoStartEnabled ?: false) }
    var tray by remember { mutableStateOf(platform.isMinimizeToTrayEnabled ?: false) }
    var notifications by remember { mutableStateOf(platform.isNotificationsEnabled ?: true) }
    var extension by remember { mutableStateOf(platform.isNativeHostInstalled ?: false) }
    val appearance: @Composable () -> Unit = {
        SettingsSection("Apariencia") {
            Text("Tema", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ThemeMode.entries.forEach { theme ->
                    ChoicePill(
                        label = when (theme) {
                            ThemeMode.System -> "Automático"
                            ThemeMode.Light -> "Claro"
                            ThemeMode.Dark -> "Oscuro"
                        },
                        selected = theme == mode,
                        onClick = { onMode(theme) },
                    )
                }
            }
        }
        if (platform.isNativeHostInstalled != null) SettingsSection("Navegador") {
            SettingsRow("Extensión de navegador", "Conecta Chrome o Edge con Luna Fetch.", extension) {
                if (it) platform.installNativeHost() else platform.uninstallNativeHost()
                extension = platform.isNativeHostInstalled ?: false
            }
            ActionButton(
                text = "Cómo instalar la extensión",
                onClick = { platform.openUrl("https://github.com/biglexj/Luna---Fetch/tree/main/browser-extension#readme") },
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    val system: @Composable () -> Unit = {
        if (platform.isAutoStartEnabled != null || platform.isMinimizeToTrayEnabled != null || platform.isNotificationsEnabled != null) {
            SettingsSection("Comportamiento") {
                if (platform.isAutoStartEnabled != null) SettingsRow("Iniciar con Windows", "Inicia en la bandeja al entrar en Windows.", autoStart) {
                    platform.setAutoStart(it); autoStart = platform.isAutoStartEnabled ?: false
                }
                if (platform.isMinimizeToTrayEnabled != null) SettingsRow("Cerrar en la bandeja", "Al cerrar la ventana, las descargas continúan.", tray) {
                    platform.setMinimizeToTray(it); tray = it
                }
                if (platform.isNotificationsEnabled != null) SettingsRow("Notificaciones", "Avisa cuando una descarga termina o falla.", notifications) {
                    platform.setNotificationsEnabled(it); notifications = it
                }
            }
        }
    }
    if (wide) Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(20.dp)) { system() }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(20.dp)) { appearance() }
    } else Column(verticalArrangement = Arrangement.spacedBy(20.dp)) { appearance(); system() }
}

@Composable
private fun SynapseSettings(platform: PlatformBindings, presenter: LunaFetchPresenter) {
    val state by presenter.state.collectAsState()
    SettingsSection("Synapse") {
        Text("Comparte descargas y sincroniza el historial entre tus dispositivos de la red local.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(platform.deviceName, style = MaterialTheme.typography.titleSmall)
        if (state.discoveredPeers.isEmpty()) {
            Text("Buscando dispositivos… Conecta tus equipos a la misma red y abre Luna Fetch en ellos.",
                style = MaterialTheme.typography.bodyMedium)
        }
        state.discoveredPeers.forEach { peer ->
            HorizontalDivider()
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${peer.icon} ${peer.name}", fontWeight = FontWeight.SemiBold)
                Text(peer.ip, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = { presenter.syncHistoryWithPeer(peer) }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Sincronizar historial")
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

/**
 * Fila de ajuste con contenedor propio: borde redondeado y padding interno.
 *
 * El Surface recorta la indicacion de hover/ripple a la forma redondeada, de modo que
 * el realce queda contenido dentro de la caja en lugar de un rectangulo recto sobre la tarjeta.
 */
@Composable
private fun SettingsRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = 60.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = null)
        }
    }
}
