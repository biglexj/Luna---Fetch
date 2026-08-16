package com.biglexj.lunafetch.feature.header

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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
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
                    SettingsButton(platform, presenter)
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
                SettingsButton(platform, presenter)
                Spacer(Modifier.width(8.dp))
                ThemeModeButton(mode, onThemeSelected)
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsButton(platform: PlatformBindings, presenter: LunaFetchPresenter) {
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
            Canvas(modifier = Modifier.size(20.dp)) {
                val c = center
                val color = androidx.compose.ui.graphics.Color(0xFF8A8A8A)
                val sw = 1.8.dp.toPx()
                val rOuter = size.minDimension / 2f
                val rInner = rOuter * 0.65f
                val rHole = rOuter * 0.28f
                val teeth = 6
                val toothPath = Path()
                val toothAngle = (2 * Math.PI / teeth).toFloat()
                val halfTooth = toothAngle * 0.28f

                for (i in 0 until teeth) {
                    val angle = toothAngle * i
                    val a1 = angle - halfTooth
                    val a2 = angle + halfTooth

                    val x1 = c.x + rInner * cos(a1)
                    val y1 = c.y + rInner * sin(a1)
                    val x2 = c.x + rOuter * cos(a1)
                    val y2 = c.y + rOuter * sin(a1)
                    val x3 = c.x + rOuter * cos(a2)
                    val y3 = c.y + rOuter * sin(a2)
                    val x4 = c.x + rInner * cos(a2)
                    val y4 = c.y + rInner * sin(a2)

                    if (i == 0) toothPath.moveTo(x1, y1) else toothPath.lineTo(x1, y1)
                    toothPath.lineTo(x2, y2)
                    toothPath.lineTo(x3, y3)
                    toothPath.lineTo(x4, y4)
                }
                toothPath.close()
                drawPath(toothPath, color = color, style = Stroke(sw, cap = StrokeCap.Round))
                drawCircle(color = color, radius = rHole, center = c, style = Stroke(sw))
            }
        }
    }

    if (showDialog) {
        SettingsDialog(platform, presenter, onDismiss = { showDialog = false })
    }
}

@Composable
private fun SettingsDialog(
    platform: PlatformBindings,
    presenter: LunaFetchPresenter,
    onDismiss: () -> Unit,
) {
    val state by presenter.state.collectAsState()
    var autoStart       by remember { mutableStateOf(platform.isAutoStartEnabled ?: false) }
    var minimizeToTray  by remember { mutableStateOf(platform.isMinimizeToTrayEnabled ?: false) }
    var nativeInstalled by remember { mutableStateOf(platform.isNativeHostInstalled ?: false) }

    var componentStatus by remember { mutableStateOf("") }
    var componentMessage by remember { mutableStateOf<String?>(null) }
    var isUpdatingComponents by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val handleDismiss = {
        presenter.clearUpdateMessage()
        onDismiss()
    }

    LaunchedEffect(Unit) {
        componentStatus = platform.getEngineComponentStatus()
        presenter.clearUpdateMessage()
    }

    LaunchedEffect(state.showUpdateModal, state.availableUpdate) {
        if (state.showUpdateModal && state.availableUpdate != null) {
            handleDismiss()
        }
    }

    BoxWithConstraints {
        val isMobile = maxWidth < 520.dp
        val dialogModifier = if (isMobile) {
            Modifier.widthIn(max = 440.dp).fillMaxWidth(0.80f)
        } else {
            Modifier.widthIn(max = 500.dp).fillMaxWidth(0.92f)
        }
        val mainSpacing = if (isMobile) 12.dp else 8.dp
        val dividerPadding = if (isMobile) 4.dp else 2.dp

        AlertDialog(
            onDismissRequest = handleDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = dialogModifier,
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Ajustes", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "v${com.biglexj.lunafetch.domain.AppConfig.APP_VERSION}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(mainSpacing),
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                ) {
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

                    // Controladores del motor (versiones/actualizaciones de binarios)
                    Column(verticalArrangement = Arrangement.spacedBy(if (isMobile) 6.dp else 4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(
                                    "Controladores del motor",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    if (componentStatus.isNotBlank()) "Estado: $componentStatus" else "Componentes de extracción y conversión.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    isUpdatingComponents = true
                                    componentMessage = null
                                    scope.launch {
                                        val res = platform.updateEngineComponents()
                                        componentMessage = res.getOrDefault("Componentes al día")
                                        componentStatus = platform.getEngineComponentStatus()
                                        isUpdatingComponents = false
                                    }
                                },
                                enabled = !isUpdatingComponents,
                                shape = RoundedCornerShape(50),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                                modifier = Modifier.height(34.dp),
                            ) {
                                if (isUpdatingComponents) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                } else {
                                    Text("🔄 Actualizar", style = MaterialTheme.typography.labelMedium)
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
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = dividerPadding), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))

                    // Sección "Acerca de Luna Fetch" unificada
                    Column(verticalArrangement = Arrangement.spacedBy(if (isMobile) 8.dp else 6.dp)) {
                        Text(
                            "Acerca de Luna Fetch",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            "Una herramienta moderna y gratuita para descargar videos, audio y contenido multimedia de YouTube, TikTok sin marca de agua, Instagram y más.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            onClick = { platform.openUrl("https://www.biglexj.com/donaciones") },
                            modifier = Modifier.fillMaxWidth().height(if (isMobile) 42.dp else 38.dp),
                            shape = RoundedCornerShape(50),
                            contentPadding = PaddingValues(vertical = 2.dp),
                        ) {
                            Text("Donar (Yape / Plin / Web) 🤍", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { platform.openUrl("https://buymeacoffee.com/biglexj") },
                                modifier = Modifier.weight(1f).height(if (isMobile) 38.dp else 36.dp),
                                shape = RoundedCornerShape(50),
                                contentPadding = PaddingValues(vertical = 2.dp),
                            ) {
                                Text("☕ Buy Me a Coffee", style = MaterialTheme.typography.labelSmall)
                            }
                            OutlinedButton(
                                onClick = { platform.openUrl("https://github.com/biglexj") },
                                modifier = Modifier.weight(1f).height(if (isMobile) 38.dp else 36.dp),
                                shape = RoundedCornerShape(50),
                                contentPadding = PaddingValues(vertical = 2.dp),
                            ) {
                                Text("⭐ GitHub", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        OutlinedButton(
                            onClick = { presenter.checkForUpdates(manual = true) },
                            modifier = Modifier.fillMaxWidth().height(if (isMobile) 38.dp else 36.dp),
                            shape = RoundedCornerShape(50),
                            contentPadding = PaddingValues(vertical = 2.dp),
                        ) {
                            Text("🔄 Buscar actualizaciones de la app", style = MaterialTheme.typography.labelMedium)
                        }
                        androidx.compose.animation.AnimatedVisibility(
                            visible = state.updateMessage != null,
                            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandVertically(),
                            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.shrinkVertically(),
                        ) {
                            if (state.updateMessage != null) {
                                Text(
                                    state.updateMessage!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = handleDismiss,
                    modifier = Modifier.padding(top = if (isMobile) 4.dp else 0.dp, bottom = 0.dp),
                ) {
                    Text("Cerrar", fontWeight = FontWeight.Bold)
                }
            },
        )
    }
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
