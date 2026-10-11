package io.github.tukiza7_debug.vibecast.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkScheme =
    darkColorScheme(
        primary = EmberOrange,
        onPrimary = Color(0xFF1A0E08),
        primaryContainer = EmberOrangeDim,
        onPrimaryContainer = Color(0xFFFFE1D2),
        secondary = TealAccent,
        onSecondary = Color(0xFF062A26),
        secondaryContainer = Color(0xFF123B36),
        onSecondaryContainer = Color(0xFFB8F1E8),
        tertiary = AmberHighlight,
        onTertiary = Color(0xFF2A2103),
        background = CharcoalBg,
        onBackground = TextPrimary,
        surface = CharcoalSurface,
        onSurface = TextPrimary,
        surfaceVariant = CharcoalSurfaceVariant,
        onSurfaceVariant = TextSecondary,
        surfaceContainerLowest = Color(0xFF0B0E14),
        surfaceContainerLow = Color(0xFF10141D),
        surfaceContainer = Color(0xFF141926),
        surfaceContainerHigh = Color(0xFF1A2130),
        surfaceContainerHighest = Color(0xFF222A3C),
        outline = SlateOutline,
        outlineVariant = Color(0xFF232C3D),
        error = ErrorRed,
        errorContainer = Color(0xFF3A1715),
        onErrorContainer = Color(0xFFF8D7D5),
    )

private val LightScheme =
    lightColorScheme(
        primary = EmberOrangeDim,
        onPrimary = Color.White,
        primaryContainer = Color(0xFFFFDBCB),
        onPrimaryContainer = Color(0xFF3B1A09),
        secondary = Color(0xFF0F766E),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFD2F1EC),
        onSecondaryContainer = Color(0xFF06302B),
        tertiary = Color(0xFFB45309),
        background = LightBg,
        onBackground = LightText,
        surface = LightSurface,
        onSurface = LightText,
        surfaceVariant = LightSurfaceVariant,
        onSurfaceVariant = LightTextSecondary,
        surfaceContainerLow = Color(0xFFF1EEE9),
        surfaceContainer = Color(0xFFEAE6E0),
        surfaceContainerHigh = Color(0xFFE3DFD8),
        outline = Color(0xFFC9C3BA),
        outlineVariant = Color(0xFFDBD6CE),
    )

@Composable
fun VibeCastTheme(
    themeMode: String,
    content: @Composable () -> Unit,
) {
    val dark =
        when (themeMode) {
            "dark" -> true
            "light" -> false
            else -> isSystemInDarkTheme()
        }
    MaterialTheme(
        colorScheme = if (dark) DarkScheme else LightScheme,
        typography = VibeCastTypography,
        shapes = VibeCastShapes,
        content = content,
    )
}
