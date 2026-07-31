package com.biglexj.lunafetch.core.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

enum class ThemeMode { System, Light, Dark }

@Composable
expect fun platformDynamicColorScheme(useDarkTheme: Boolean): ColorScheme?

private val LunaLightColors = lightColorScheme(
    primary = Color(0xFF00796B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB2DFDB),
    onPrimaryContainer = Color(0xFF00201B),
    secondary = Color(0xFF4DB6AC),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0F2F1),
    onSecondaryContainer = Color(0xFF00201B),
    tertiary = Color(0xFF0288D1),
    tertiaryContainer = Color(0xFFE1F5FE),
    onTertiaryContainer = Color(0xFF001F2D),
    background = Color(0xFFF6FBF9),
    onBackground = Color(0xFF141E1C),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF141E1C),
    surfaceVariant = Color(0xFFEEF6F4),
    onSurfaceVariant = Color(0xFF455450),
    surfaceTint = Color.Transparent,
    outline = Color(0xFF70827D),
    outlineVariant = Color(0xFFD4E3DF),
)

private val LunaDarkColors = darkColorScheme(
    primary = Color(0xFF00E5C1),
    onPrimary = Color(0xFF00372E),
    primaryContainer = Color(0xFF004D42),
    onPrimaryContainer = Color(0xFF80F7E4),
    secondary = Color(0xFF80D8C8),
    onSecondary = Color(0xFF00372E),
    secondaryContainer = Color(0xFF223835),
    onSecondaryContainer = Color(0xFFCCE8E1),
    tertiary = Color(0xFF82CFFF),
    tertiaryContainer = Color(0xFF004C6D),
    onTertiaryContainer = Color(0xFFC7E7FF),
    background = Color(0xFF0D1415),
    onBackground = Color(0xFFE1E8E6),
    surface = Color(0xFF141E20),
    onSurface = Color(0xFFE1E8E6),
    surfaceVariant = Color(0xFF1C2B2E),
    onSurfaceVariant = Color(0xFFB5C4C0),
    surfaceTint = Color.Transparent,
    outline = Color(0xFF50615D),
    outlineVariant = Color(0xFF263639),
)

private val LunaShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
)



@Composable
fun rememberDynamicSystemInDarkTheme(): Boolean {
    val composeSystemDark = isSystemInDarkTheme()
    val platformDark = isPlatformInDarkTheme()
    var isDarkState by remember(composeSystemDark, platformDark) {
        mutableStateOf(platformDark ?: composeSystemDark)
    }

    LaunchedEffect(Unit) {
        while (isActive) {
            delay(1_000)
            val currentPlatform = isPlatformInDarkTheme()
            if (currentPlatform != null && isDarkState != currentPlatform) {
                isDarkState = currentPlatform
            }
        }
    }
    return isDarkState
}

@Composable
fun LunaFetchTheme(
    mode: ThemeMode,
    content: @Composable () -> Unit,
) {
    val systemInDark = rememberDynamicSystemInDarkTheme()
    val dark = when (mode) {
        ThemeMode.System -> systemInDark
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val colors = platformDynamicColorScheme(dark) ?: if (dark) LunaDarkColors else LunaLightColors
    MaterialTheme(colorScheme = colors, shapes = LunaShapes, content = content)
}
