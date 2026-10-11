package io.github.tukiza7_debug.vibecast.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkScheme = darkColorScheme(
    primary = EmberOrange,
    onPrimary = Color(0xFF1A0E08),
    primaryContainer = EmberOrangeDim,
    onPrimaryContainer = Color(0xFFFFE1D2),
    secondary = TealAccent,
    onSecondary = Color(0xFF062A26),
    tertiary = AmberHighlight,
    background = CharcoalBg,
    onBackground = TextPrimary,
    surface = CharcoalSurface,
    onSurface = TextPrimary,
    surfaceVariant = CharcoalSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = SlateOutline,
    error = ErrorRed,
)

private val LightScheme = lightColorScheme(
    primary = EmberOrangeDim,
    onPrimary = Color.White,
    secondary = Color(0xFF0F766E),
    tertiary = Color(0xFFB45309),
    background = LightBg,
    onBackground = LightText,
    surface = LightSurface,
    onSurface = LightText,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    outline = Color(0xFFC9C3BA),
)

@Composable
fun VibeCastTheme(themeMode: String, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
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
