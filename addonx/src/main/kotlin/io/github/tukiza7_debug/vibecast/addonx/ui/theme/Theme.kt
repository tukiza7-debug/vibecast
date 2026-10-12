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

// Shared VibeCast-family accents: ember orange drives primary actions in both apps.
// AddOnX keeps its builder personality with deep navy surfaces and a sky-blue
// supporting hue; semantic colors below are identical to the VibeCast app so the
// family reads as one product.
val EmberOrange = Color(0xFFFF7A45)
val SkyBlue = Color(0xFF38BDF8)
val DeepNavy = Color(0xFF0C1220)
val NavySurface = Color(0xFF131B2C)
val NavyVariant = Color(0xFF1B2740)
val NavyOutline = Color(0xFF2B3A57)
val OnNavy = Color(0xFFE7EDF6)
val OnNavyDim = Color(0xFF94A3B8)
val ConfidenceHigh = Color(0xFF4ADE80)
val ConfidenceMedium = Color(0xFFF5C518)
val ConfidenceLow = Color(0xFFF87171)

private val DarkScheme =
    darkColorScheme(
        primary = EmberOrange,
        onPrimary = Color(0xFF1A0E08),
        primaryContainer = Color(0xFFB5532C),
        onPrimaryContainer = Color(0xFFFFE1D2),
        secondary = SkyBlue,
        onSecondary = Color(0xFF04212F),
        secondaryContainer = Color(0xFF0E4A66),
        onSecondaryContainer = Color(0xFFD3EEFF),
        tertiary = Color(0xFFA3E635),
        onTertiary = Color(0xFF14260A),
        background = DeepNavy,
        onBackground = OnNavy,
        surface = NavySurface,
        onSurface = OnNavy,
        surfaceVariant = NavyVariant,
        onSurfaceVariant = OnNavyDim,
        outline = NavyOutline,
        error = Color(0xFFEF5350),
    )

private val LightScheme =
    lightColorScheme(
        primary = Color(0xFFC2410C),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFFFE1D2),
        onPrimaryContainer = Color(0xFF5B1E07),
        secondary = Color(0xFF0369A1),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFD3EEFF),
        onSecondaryContainer = Color(0xFF062B3E),
        background = Color(0xFFF6F4F1),
        onBackground = Color(0xFF1A1D23),
        surface = Color.White,
        onSurface = Color(0xFF1A1D23),
        surfaceVariant = Color(0xFFE8E4DE),
        onSurfaceVariant = Color(0xFF52607A),
        outline = Color(0xFFC9C3BA),
        error = Color(0xFFC62828),
    )

private val Typography =
    Typography(
        headlineLarge = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, letterSpacing = (-0.4).sp),
        headlineMedium = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 25.sp, letterSpacing = (-0.3).sp),
        titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 19.sp, letterSpacing = (-0.1).sp),
        titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
        bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
        bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
        labelSmall = TextStyle(fontSize = 11.sp, letterSpacing = 0.4.sp),
    )

private val Shapes =
    Shapes(
        extraSmall = RoundedCornerShape(6.dp),
        small = RoundedCornerShape(8.dp),
        medium = RoundedCornerShape(14.dp),
        large = RoundedCornerShape(22.dp),
        extraLarge = RoundedCornerShape(28.dp),
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
