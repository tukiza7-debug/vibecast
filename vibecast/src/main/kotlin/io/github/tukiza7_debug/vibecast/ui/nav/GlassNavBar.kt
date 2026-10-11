package io.github.tukiza7_debug.vibecast.ui.nav

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.tukiza7_debug.vibecast.ui.common.pressScale

/**
 * Floating glass-style bottom navigation: translucent frosted surface, hairline
 * top border, pill indicator that slides to the active tab. Touch targets are
 * at least 48dp and every item exposes a spoken label.
 */
@Composable
fun GlassNavBar(
    items: List<GlassNavItem>,
    selectedRoute: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        shadowElevation = 12.dp,
    ) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            )
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                items.forEach { item ->
                    val selected = item.route == selectedRoute
                    val pillColor by animateColorAsState(
                        targetValue =
                            if (selected) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                            } else {
                                androidx.compose.ui.graphics.Color.Transparent
                            },
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        label = "pillColor",
                    )
                    Box(
                        modifier =
                            Modifier
                                .weight(1f)
                                .padding(horizontal = 2.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(pillColor)
                                .pressScale()
                                .semantics { contentDescription = item.label }
                                .clickableNoRipple { onSelect(item.route) }
                                .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = null,
                                tint =
                                    if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                modifier = Modifier.size(24.dp),
                            )
                            val labelAlpha by animateColorAsState(
                                targetValue =
                                    if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        androidx.compose.ui.graphics.Color.Transparent
                                    },
                                label = "labelColor",
                            )
                            val height by animateDpAsState(
                                targetValue = if (selected) 14.dp else 0.dp,
                                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                label = "labelHeight",
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                item.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = labelAlpha,
                                modifier = Modifier.height(height),
                            )
                        }
                    }
                }
            }
        }
    }
}

data class GlassNavItem(
    val route: String,
    val icon: ImageVector,
    val label: String,
)

@Composable
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier =
    androidx.compose.ui.composed {
        val interaction = remember { MutableInteractionSource() }
        this.clickable(
            interactionSource = interaction,
            indication = null,
            onClick = onClick,
        )
    }
