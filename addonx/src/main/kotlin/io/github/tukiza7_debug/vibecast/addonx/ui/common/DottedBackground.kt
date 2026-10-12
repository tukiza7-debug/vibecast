package io.github.tukiza7_debug.vibecast.addonx.ui.common

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Soft dotted grid background - the "clean canvas" maker-tool feel for hero
 * panels and workspaces. Pure drawBehind: no layers, no allocation per frame.
 */
fun Modifier.dottedGrid(
    dotColor: Color,
    spacing: Dp = 18.dp,
    dotRadius: Dp = 1.2.dp,
): Modifier =
    drawBehind {
        val step = spacing.toPx()
        val radius = dotRadius.toPx()
        if (step <= 0f || radius <= 0f) return@drawBehind
        var y = 0f
        while (y <= size.height) {
            var x = 0f
            while (x <= size.width) {
                drawCircle(color = dotColor, radius = radius, center = Offset(x, y))
                x += step
            }
            y += step
        }
    }
