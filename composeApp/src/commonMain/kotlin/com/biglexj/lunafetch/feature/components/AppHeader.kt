package com.biglexj.lunafetch.feature.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.biglexj.lunafetch.core.theme.ThemeMode
import com.biglexj.lunafetch.domain.LunaFetchPresenter
import com.biglexj.lunafetch.domain.LunaFetchState
import com.biglexj.lunafetch.domain.PlatformBindings
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun AppHeader(
    mode: ThemeMode,
    onThemeSelected: (ThemeMode) -> Unit,
    platform: PlatformBindings,
    presenter: LunaFetchPresenter,
    state: LunaFetchState,
) {
    val hasSettings = platform.isAutoStartEnabled != null
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        if (maxWidth < 520.dp) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Luna Fetch",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    HistoryButton(presenter, state, platform)
                    Spacer(Modifier.width(8.dp))
                    if (hasSettings) SettingsButton(platform)
                    Spacer(Modifier.width(8.dp))
                    AboutButton(platform, presenter)
                    Spacer(Modifier.width(8.dp))
                    ThemeModeButton(mode, onThemeSelected)
                }
                Text(
                    "Descarga videos y audio en alta calidad",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Luna Fetch", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "Descarga videos y audio en alta calidad",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HistoryButton(presenter, state, platform)
                Spacer(Modifier.width(8.dp))
                if (hasSettings) SettingsButton(platform)
                Spacer(Modifier.width(8.dp))
                AboutButton(platform, presenter)
                Spacer(Modifier.width(8.dp))
                ThemeModeButton(mode, onThemeSelected)
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryButton(presenter: LunaFetchPresenter, state: LunaFetchState, platform: PlatformBindings) {
    var showDialog by remember { mutableStateOf(false) }
    val label = "Historial de descargas"

    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                .clickable(role = Role.Button, onClickLabel = label, onClick = { showDialog = true }),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.size(20.dp)) {
                val color = androidx.compose.ui.graphics.Color(0xFF8A8A8A)
                val sw = 1.8.dp.toPx()
                val r = size.minDimension / 2f
                drawCircle(color = color, radius = r, center = center, style = Stroke(sw))
                drawLine(color = color, start = center, end = Offset(center.x, center.y - r * 0.55f), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(color = color, start = center, end = Offset(center.x + r * 0.45f, center.y), strokeWidth = sw, cap = StrokeCap.Round)
            }
        }
    }

    if (showDialog) {
        androidx.compose.runtime.LaunchedEffect(Unit) {
            presenter.refreshHistory()
        }
        HistoryDialog(state = state, presenter = presenter, platform = platform, onDismiss = { showDialog = false })
    }
}

@Composable
private fun HistoryDialog(
    state: LunaFetchState,
    presenter: LunaFetchPresenter,
    platform: PlatformBindings,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth(0.88f).widthIn(max = 560.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Historial de descargas",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (state.history.isNotEmpty()) {
                    TextButton(
                        onClick = presenter::clearHistory,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text("Limpiar todo", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        },
        text = {
            if (state.history.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "No hay descargas recientes.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.history.forEach { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    item.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    item.formatLabel,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (item.url.isNotBlank()) {
                                OutlinedButton(
                                    onClick = { platform.openUrl(item.url) },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                ) {
                                    Text("🌐 Web", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                            if (item.path.isNotBlank()) {
                                OutlinedButton(
                                    onClick = { platform.openOutput(item.path) },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                ) {
                                    Text("Abrir", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                            IconButton(
                                onClick = { presenter.removeFromHistory(item.id) },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Text("🗑️", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsButton(platform: PlatformBindings) {
    var showDialog by remember { mutableStateOf(false) }
    val label = "Ajustes"

    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                .clickable(role = Role.Button, onClickLabel = label, onClick = { showDialog = true }),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.size(22.dp)) {
                val c = center
                val color = androidx.compose.ui.graphics.Color(0xFF8A8A8A)
                val sw = 1.8.dp.toPx()
                val outerR = size.minDimension / 2f
                val innerR = outerR * 0.42f
                val toothCount = 8
                val toothPath = Path()
                for (i in 0 until toothCount) {
                    val baseAngle = (2 * Math.PI / toothCount * i).toFloat()
                    val tipAngle = baseAngle + (Math.PI / toothCount).toFloat()
                    val bx1 = c.x + outerR * 0.68f * cos(baseAngle - 0.22f)
                    val by1 = c.y + outerR * 0.68f * sin(baseAngle - 0.22f)
                    val tx  = c.x + outerR * cos(tipAngle - (Math.PI / toothCount).toFloat())
                    val ty  = c.y + outerR * sin(tipAngle - (Math.PI / toothCount).toFloat())
                    val bx2 = c.x + outerR * 0.68f * cos(baseAngle + 0.22f)
                    val by2 = c.y + outerR * 0.68f * sin(baseAngle + 0.22f)
                    if (i == 0) toothPath.moveTo(bx1, by1)
                    else toothPath.lineTo(bx1, by1)
                    toothPath.lineTo(tx, ty)
                    toothPath.lineTo(bx2, by2)
                }
                toothPath.close()
                drawPath(toothPath, color = color, style = Stroke(sw, cap = StrokeCap.Round))
                drawCircle(color = color, radius = innerR, center = c, style = Stroke(sw))
            }
        }
    }

    if (showDialog) {
        SettingsDialog(platform, onDismiss = { showDialog = false })
    }
}

@Composable
private fun SettingsDialog(platform: PlatformBindings, onDismiss: () -> Unit) {
    var autoStart       by remember { mutableStateOf(platform.isAutoStartEnabled ?: false) }
    var minimizeToTray  by remember { mutableStateOf(platform.isMinimizeToTrayEnabled ?: false) }
    var nativeInstalled by remember { mutableStateOf(platform.isNativeHostInstalled ?: false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ajustes", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SettingsRow(
                    title = "Iniciar con Windows",
                    subtitle = "Abre Luna Fetch automáticamente al encender el equipo.",
                    checked = autoStart,
                    onCheckedChange = { autoStart = it; platform.setAutoStart(it) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                SettingsRow(
                    title = "Minimizar en lugar de cerrar",
                    subtitle = "Al pulsar ✕, la app se oculta en la bandeja del sistema.",
                    checked = minimizeToTray,
                    onCheckedChange = { minimizeToTray = it; platform.setMinimizeToTray(it) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                SettingsRow(
                    title = "Extensión de navegador",
                    subtitle = if (nativeInstalled)
                        "Host registrado en Chrome/Edge. Instala la extensión para activarla."
                    else
                        "Registra el host para que Chrome/Edge puedan comunicarse con Luna Fetch.",
                    checked = nativeInstalled,
                    onCheckedChange = {
                        nativeInstalled = it
                        if (it) platform.installNativeHost() else platform.uninstallNativeHost()
                    },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        },
    )
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.5f),
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeModeButton(currentMode: ThemeMode, onModeSelected: (ThemeMode) -> Unit) {
    val nextMode = when (currentMode) {
        ThemeMode.System -> ThemeMode.Light
        ThemeMode.Light -> ThemeMode.Dark
        ThemeMode.Dark -> ThemeMode.System
    }
    val currentLabel = when (currentMode) {
        ThemeMode.System -> "Sistema"
        ThemeMode.Light -> "Claro"
        ThemeMode.Dark -> "Oscuro"
    }
    val nextLabel = when (nextMode) {
        ThemeMode.System -> "Sistema"
        ThemeMode.Light -> "Claro"
        ThemeMode.Dark -> "Oscuro"
    }
    val actionLabel = "Cambiar al tema $nextLabel"

    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(actionLabel) } },
        state = rememberTooltipState(),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                .clickable(
                    role = Role.Button,
                    onClickLabel = actionLabel,
                    onClick = { onModeSelected(nextMode) },
                )
                .semantics { contentDescription = "Tema actual: $currentLabel. $actionLabel" },
            contentAlignment = Alignment.Center,
        ) {
            ThemeModeGlyph(
                mode = currentMode,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun ThemeModeGlyph(mode: ThemeMode, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier) {
        val strokeWidth = 1.9.dp.toPx()
        when (mode) {
            ThemeMode.System -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(size.width * 0.1f, size.height * 0.16f),
                    size = Size(size.width * 0.8f, size.height * 0.58f),
                    cornerRadius = CornerRadius(2.dp.toPx()),
                    style = Stroke(strokeWidth, cap = StrokeCap.Round),
                )
                drawLine(
                    color,
                    Offset(size.width * 0.5f, size.height * 0.74f),
                    Offset(size.width * 0.5f, size.height * 0.88f),
                    strokeWidth,
                    StrokeCap.Round,
                )
                drawLine(
                    color,
                    Offset(size.width * 0.3f, size.height * 0.88f),
                    Offset(size.width * 0.7f, size.height * 0.88f),
                    strokeWidth,
                    StrokeCap.Round,
                )
            }

            ThemeMode.Light -> {
                drawCircle(color, radius = size.minDimension * 0.19f, style = Stroke(strokeWidth))
                repeat(8) { index ->
                    val angle = Math.toRadians(index * 45.0)
                    val direction = Offset(cos(angle).toFloat(), sin(angle).toFloat())
                    val center = this.center
                    drawLine(
                        color,
                        center + direction * (size.minDimension * 0.31f),
                        center + direction * (size.minDimension * 0.43f),
                        strokeWidth,
                        StrokeCap.Round,
                    )
                }
            }

            ThemeMode.Dark -> {
                val moon = Path().apply {
                    moveTo(size.width * 0.68f, size.height * 0.08f)
                    cubicTo(
                        size.width * 0.34f,
                        size.height * 0.18f,
                        size.width * 0.22f,
                        size.height * 0.62f,
                        size.width * 0.48f,
                        size.height * 0.86f,
                    )
                    cubicTo(
                        size.width * 0.66f,
                        size.height * 1.02f,
                        size.width * 0.92f,
                        size.height * 0.88f,
                        size.width * 0.96f,
                        size.height * 0.68f,
                    )
                    cubicTo(
                        size.width * 0.66f,
                        size.height * 0.82f,
                        size.width * 0.4f,
                        size.height * 0.56f,
                        size.width * 0.52f,
                        size.height * 0.3f,
                    )
                    cubicTo(
                        size.width * 0.56f,
                        size.height * 0.2f,
                        size.width * 0.62f,
                        size.height * 0.13f,
                        size.width * 0.68f,
                        size.height * 0.08f,
                    )
                    close()
                }
                drawPath(moon, color)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AboutButton(platform: PlatformBindings, presenter: LunaFetchPresenter) {
    var showDialog by remember { mutableStateOf(false) }
    val label = "Acerca de y Donaciones"

    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                .clickable(role = Role.Button, onClickLabel = label, onClick = { showDialog = true }),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.size(20.dp)) {
                val color = androidx.compose.ui.graphics.Color(0xFF8A8A8A)
                val sw = 1.8.dp.toPx()
                val r = size.minDimension / 2f
                drawCircle(color = color, radius = r, center = center, style = Stroke(sw))
                drawCircle(color = color, radius = sw * 0.8f, center = Offset(center.x, center.y - r * 0.4f))
                drawLine(color = color, start = Offset(center.x, center.y - r * 0.1f), end = Offset(center.x, center.y + r * 0.45f), strokeWidth = sw, cap = StrokeCap.Round)
            }
        }
    }

    if (showDialog) {
        AboutDialog(platform, presenter, onDismiss = { showDialog = false })
    }
}

@Composable
private fun AboutDialog(platform: PlatformBindings, presenter: LunaFetchPresenter, onDismiss: () -> Unit) {
    BoxWithConstraints {
        if (maxWidth < 520.dp) {
            AboutDialogMobile(platform, presenter, onDismiss)
        } else {
            AboutDialogDesktop(platform, presenter, onDismiss)
        }
    }
}

@Composable
private fun AboutDialogDesktop(platform: PlatformBindings, presenter: LunaFetchPresenter, onDismiss: () -> Unit) {
    val state by presenter.state.collectAsState()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Luna Fetch", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "Versión 1.0.9 · Desarrollado por Biglex J",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    "Luna Fetch es una herramienta moderna y gratuita para descargar videos y audio de TikTok sin marca de agua, YouTube, Instagram y más.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                Text("Apoya el Desarrollo Oficial", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    "Si esta aplicación te es útil, puedes apoyar su desarrollo mediante una donación voluntaria:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = { platform.openUrl("https://www.biglexj.com/donaciones") },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(50),
                ) {
                    Text("💳 Donar (Yape / Plin / Transferencias)", fontWeight = FontWeight.Bold)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { platform.openUrl("https://buymeacoffee.com/biglexj") },
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(50),
                    ) {
                        Text("☕ Buy Me a Coffee", style = MaterialTheme.typography.labelMedium)
                    }
                    OutlinedButton(
                        onClick = { platform.openUrl("https://github.com/biglexj") },
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(50),
                    ) {
                        Text("⭐ GitHub", style = MaterialTheme.typography.labelMedium)
                    }
                }
                if (state.updateMessage != null) {
                    Text(
                        state.updateMessage!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = { presenter.checkForUpdates(manual = true) }) {
                Text("🔄 Buscar actualizaciones", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        },
    )
}

@Composable
private fun AboutDialogMobile(platform: PlatformBindings, presenter: LunaFetchPresenter, onDismiss: () -> Unit) {
    val state by presenter.state.collectAsState()
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.widthIn(max = 440.dp).fillMaxWidth(0.85f),
        title = {
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Acerca de Luna Fetch", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Luna Fetch v1.0.9",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "Licencia: MIT · Autor: Biglex J (2026)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            ) {
                Text(
                    "Una herramienta moderna y gratuita con soporte para fotos, videos y música de TikTok sin marca de agua, YouTube, Instagram y exploración local.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                Text(
                    "Apoya el desarrollo de Luna Fetch:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Button(
                    onClick = { platform.openUrl("https://www.biglexj.com/donaciones") },
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(50),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Text("Donar (Yape / Plin / Web) 🤍", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                }
                OutlinedButton(
                    onClick = { platform.openUrl("https://buymeacoffee.com/biglexj") },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(50),
                ) {
                    Text("Buy Me a Coffee ☕", style = MaterialTheme.typography.labelLarge)
                }
                OutlinedButton(
                    onClick = { platform.openUrl("https://github.com/biglexj") },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(50),
                ) {
                    Text("⭐ GitHub", style = MaterialTheme.typography.labelLarge)
                }
                OutlinedButton(
                    onClick = { presenter.checkForUpdates(manual = true) },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(50),
                ) {
                    Text("🔄 Buscar actualizaciones", style = MaterialTheme.typography.labelLarge)
                }
                if (state.updateMessage != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        state.updateMessage!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Aceptar", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
        },
    )
}
