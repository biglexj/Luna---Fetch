package com.biglexj.lunafetch.feature.header

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

enum class LunaGlyph { Download, Queue, History, Settings, Sun, Moon, Auto }

/**
 * Iconos vectoriales con el mismo trazo, independientes de la fuente del sistema.
 *
 * [background] es el color sobre el que se dibuja el icono: la media luna se recorta
 * con ese color, por eso quien la usa debe indicar el fondo real del botón.
 */
@Composable
fun NavigationGlyph(glyph: LunaGlyph, modifier: Modifier = Modifier, background: Color = Color.Transparent) {
    val color = LocalContentColor.current
    Canvas(modifier.size(24.dp)) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            val stroke = Stroke(width = 1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            val path = Path()
            when (glyph) {
                LunaGlyph.Download -> {
                    path.moveTo(12f, 3f); path.lineTo(12f, 14f)
                    path.moveTo(7f, 10f); path.lineTo(12f, 15f); path.lineTo(17f, 10f)
                    path.moveTo(4f, 15f); path.lineTo(4f, 20f); path.lineTo(20f, 20f); path.lineTo(20f, 15f)
                    drawPath(path, color, style = stroke)
                }
                LunaGlyph.Queue -> {
                    path.moveTo(4f, 6f); path.lineTo(20f, 6f)
                    path.moveTo(4f, 11f); path.lineTo(20f, 11f)
                    path.moveTo(4f, 16f); path.lineTo(14f, 16f)
                    path.moveTo(17f, 13.5f); path.lineTo(20f, 16.5f); path.lineTo(17f, 19.5f)
                    drawPath(path, color, style = stroke)
                }
                LunaGlyph.History -> {
                    drawArc(color, startAngle = -190f, sweepAngle = 300f, useCenter = false,
                        topLeft = Offset(4f, 4f), size = Size(16f, 16f), style = stroke)
                    path.moveTo(3f, 4f); path.lineTo(3f, 9f); path.lineTo(8f, 9f)
                    path.moveTo(12f, 7f); path.lineTo(12f, 12f); path.lineTo(16f, 14f)
                    drawPath(path, color, style = stroke)
                }
                LunaGlyph.Settings -> {
                    for (i in 0 until 48) {
                        val angle = (i * Math.PI / 24).toFloat()
                        val radius = if (i % 8 in 2..5) 9f else 7f
                        val x = 12f + radius * cos(angle)
                        val y = 12f + radius * sin(angle)
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    path.close()
                    drawPath(path, color, style = stroke)
                    drawCircle(color, radius = 3f, center = Offset(12f, 12f), style = stroke)
                }
                LunaGlyph.Sun -> {
                    drawCircle(color, radius = 4.5f, center = Offset(12f, 12f), style = stroke)
                    for (i in 0 until 8) {
                        val angle = (i * Math.PI / 4).toFloat()
                        path.moveTo(12f + 7f * cos(angle), 12f + 7f * sin(angle))
                        path.lineTo(12f + 10f * cos(angle), 12f + 10f * sin(angle))
                    }
                    drawPath(path, color, style = stroke)
                }
                LunaGlyph.Moon -> {
                    // Media luna: disco lleno al que se le recorta otro con el fondo.
                    drawCircle(color, radius = 7.5f, center = Offset(11.5f, 12f))
                    drawCircle(background, radius = 7f, center = Offset(16f, 7.5f))
                }
                LunaGlyph.Auto -> {
                    // Monitor: el tema automático sigue al del sistema (la pantalla).
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(3f, 5f),
                        size = Size(18f, 11.5f),
                        cornerRadius = CornerRadius(2f, 2f),
                        style = stroke,
                    )
                    path.moveTo(12f, 16.5f); path.lineTo(12f, 19.5f)
                    path.moveTo(8.5f, 19.5f); path.lineTo(15.5f, 19.5f)
                    drawPath(path, color, style = stroke)
                }
            }
        }
    }
}
