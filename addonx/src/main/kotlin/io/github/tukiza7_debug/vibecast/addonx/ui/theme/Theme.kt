package io.github.tukiza7_debug.vibecast.addonx.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// AddOnX identity: sky blue builder surface with lime accents on deep navy.
val SkyBlue = Color(0xFF38BDF8)
val DeepNavy = Color(0xFF0C1220)
val NavySurface = Color(0xFF131B2C)
val NavyVariant = Color(0xFF1B2740)
val LimeAccent = Color(0xFFA3E635)
val OrangeAccent = Color(0xFFFF7A45)
val NavyOutline = Color(0xFF2B3A57)
val OnNavy = Color(0xFFE7EDF6)
val OnNavyDim = Color(0xFF94A3B8)

private val DarkScheme =
    darkColorScheme(
        primary = SkyBlue,
        onPrimary = Color(0xFF04212F),
        primaryContainer = Color(0xFF0E4A66),
        secondary = LimeAccent,
        onSecondary = Color(0xFF14260A),
        tertiary = OrangeAccent,
        background = DeepNavy,
        onBackground = OnNavy,
        surface = NavySurface,
        onSurface = OnNavy,
        surfaceVariant = NavyVariant,
        onSurfaceVariant = OnNavyDim,
        outline = NavyOutline,
    )

private val LightScheme =
    lightColorScheme(
        primary = Color(0xFF0369A1),
        onPrimary = Color.White,
        secondary = Color(0xFF4D7C0F),
        background = Color(0xFFF5F7FA),
        onBackground = Color(0xFF141A24),
        surface = Color.White,
        onSurface = Color(0xFF141A24),
        surfaceVariant = Color(0xFFE4E9F0),
        onSurfaceVariant = Color(0xFF52607A),
    )

private val Typography =
    Typography(
        headlineMedium = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 25.sp),
        titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 19.sp),
        titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
        bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
        labelSmall = TextStyle(fontSize = 11.sp, letterSpacing = 0.4.sp),
    )

private val Shapes =
    Shapes(
        small = RoundedCornerShape(8.dp),
        medium = RoundedCornerShape(14.dp),
        large = RoundedCornerShape(22.dp),
    )

@Composable
fun AddOnXTheme(
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
        typography = Typography,
        shapes = Shapes,
        content = content,
    )
}
