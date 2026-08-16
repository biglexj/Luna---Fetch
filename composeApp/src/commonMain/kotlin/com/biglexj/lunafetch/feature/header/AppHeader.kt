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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
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
        if (maxWidth < 560.dp) {
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
                    if (state.discoveredPeers.isNotEmpty()) {
                        LanMeshBadge(state, presenter)
                        Spacer(Modifier.width(6.dp))
                    }
                    AboutUpdatesButton(platform, presenter)
                    Spacer(Modifier.width(6.dp))
                    SettingsButton(platform, presenter)
                    Spacer(Modifier.width(6.dp))
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
                if (state.discoveredPeers.isNotEmpty()) {
                    LanMeshBadge(state, presenter)
                    Spacer(Modifier.width(10.dp))
                }
                AboutUpdatesButton(platform, presenter)
                Spacer(Modifier.width(8.dp))
                SettingsButton(platform, presenter)
                Spacer(Modifier.width(8.dp))
                ThemeModeButton(mode, onThemeSelected)
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
}

@Composable
private fun LanMeshBadge(state: LunaFetchState, presenter: LunaFetchPresenter) {
    var showMenu by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { showMenu = true },
            shape = RoundedCornerShape(50),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            modifier = Modifier.height(38.dp),
        ) {
            Text(
                "📶 ${state.discoveredPeers.size} en red",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        androidx.compose.material3.DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            modifier = Modifier.clip(RoundedCornerShape(16.dp)),
        ) {
            state.discoveredPeers.forEach { peer ->
                androidx.compose.material3.DropdownMenuItem(
                    text = {
                        Column {
                            Text("${peer.icon} ${peer.name}", fontWeight = FontWeight.Bold)
                            Text("IP: ${peer.ip} • Sincronizar historial", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    onClick = {
                        showMenu = false
                        presenter.syncHistoryWithPeer(peer)
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AboutUpdatesButton(platform: PlatformBindings, presenter: LunaFetchPresenter) {
    var showDialog by remember { mutableStateOf(false) }
    val label = "Acerca de & Actualizaciones"

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
                // Círculo exterior
                drawCircle(color = color, style = Stroke(sw))
                // Punto de la 'i'
                drawCircle(color = color, radius = 1.3.dp.toPx(), center = Offset(center.x, center.y - 4.2.dp.toPx()))
                // Cuerpo de la 'i'
                drawLine(
                    color = color,
                    start = Offset(center.x, center.y - 1.2.dp.toPx()),
                    end = Offset(center.x, center.y + 4.8.dp.toPx()),
                    strokeWidth = sw,
                    cap = StrokeCap.Round,
                )
            }
        }
    }

    if (showDialog) {
        AboutUpdatesDialog(platform, presenter, onDismiss = { showDialog = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsButton(platform: PlatformBindings, presenter: LunaFetchPresenter) {
    var showDialog by remember { mutableStateOf(false) }
    val label = "Configuración"

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
private fun ThemeModeGlyph(
    mode: ThemeMode,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val color = androidx.compose.ui.graphics.Color(0xFF8A8A8A)
        val strokeWidth = 1.8.dp.toPx()
        when (mode) {
            ThemeMode.System -> {
                val monitorWidth = size.width * 0.9f
                val monitorHeight = size.height * 0.62f
                val monitorLeft = (size.width - monitorWidth) / 2f
                val monitorTop = size.height * 0.12f

                drawRoundRect(
                    color = color,
                    topLeft = Offset(monitorLeft, monitorTop),
                    size = Size(monitorWidth, monitorHeight),
                    cornerRadius = CornerRadius(2.5.dp.toPx(), 2.5.dp.toPx()),
                    style = Stroke(width = strokeWidth),
                )
                drawLine(
                    color = color,
                    start = Offset(size.width / 2f, monitorTop + monitorHeight),
                    end = Offset(size.width / 2f, monitorTop + monitorHeight + size.height * 0.16f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = color,
                    start = Offset(size.width * 0.32f, size.height * 0.9f),
                    end = Offset(size.width * 0.68f, size.height * 0.9f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
            }
            ThemeMode.Light -> {
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = size.minDimension * 0.22f
                drawCircle(color = color, radius = radius, center = center, style = Stroke(width = strokeWidth))

                val rayLength = size.minDimension * 0.14f
                val rayStart = radius + 2.5.dp.toPx()
                for (i in 0 until 8) {
                    val angle = (i * Math.PI / 4).toFloat()
                    val startX = center.x + rayStart * cos(angle)
                    val startY = center.y + rayStart * sin(angle)
                    val endX = center.x + (rayStart + rayLength) * cos(angle)
                    val endY = center.y + (rayStart + rayLength) * sin(angle)
                    drawLine(
                        color = color,
                        start = Offset(startX, startY),
                        end = Offset(endX, endY),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round,
                    )
                }
            }
            ThemeMode.Dark -> {
                val center = Offset(size.width * 0.48f, size.height * 0.5f)
                val radius = size.minDimension * 0.36f
                val moonPath = Path().apply {
                    arcTo(
                        rect = androidx.compose.ui.geometry.Rect(
                            center.x - radius, center.y - radius,
                            center.x + radius, center.y + radius,
                        ),
                        startAngleDegrees = -90f,
                        sweepAngleDegrees = 270f,
                        forceMoveTo = true,
                    )
                    arcTo(
                        rect = androidx.compose.ui.geometry.Rect(
                            center.x - radius * 0.55f, center.y - radius,
                            center.x + radius * 0.85f, center.y + radius,
                        ),
                        startAngleDegrees = 180f,
                        sweepAngleDegrees = -180f,
                        forceMoveTo = false,
                    )
                    close()
                }
                drawPath(moonPath, color = color, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
            }
        }
    }
}
