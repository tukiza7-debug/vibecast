package io.github.tukiza7_debug.vibecast.ui.common

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest

/**
 * The single cover/poster component of the app.
 *
 * - shimmer placeholder while loading (never a flat dark box)
 * - letter-tile fallback with the title initial when loading fails,
 *   plus a subtle tap-to-retry affordance
 * - crossfade handled by the shared ImageLoader
 */
@Composable
fun CoverImage(
    url: String?,
    title: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    contentDescription: String? = title,
) {
    var failed by remember(url) { mutableStateOf(false) }
    var attempt by remember(url) { mutableIntStateOf(0) }

    Box(modifier = modifier.animateContentSize()) {
        if (url.isNullOrBlank()) {
            LetterTile(title = title, modifier = Modifier.fillMaxSize())
        } else {
            Box(Modifier.fillMaxSize()) {
                if (!failed) {
                    ShimmerSurface(Modifier.matchParentSize())
                }
                AsyncImage(
                    model =
                        ImageRequest
                            .Builder(LocalContext.current)
                            .data(url)
                            .setParameter("retry", attempt)
                            .build(),
                    contentDescription = contentDescription,
                    contentScale = contentScale,
                    onState = { state ->
                        when (state) {
                            is AsyncImagePainter.State.Error -> failed = true
                            is AsyncImagePainter.State.Success -> failed = false
                            else -> Unit
                        }
                    },
                    modifier = Modifier.matchParentSize(),
                )
                if (failed) {
                    LetterTile(
                        title = title,
                        showRetry = true,
                        modifier =
                            Modifier
                                .matchParentSize()
                                .clickable { attempt += 1 },
                    )
                }
            }
        }
    }
}

/** Letter tile used for missing and failed covers: gradient + initial, not a dark void. */
@Composable
fun LetterTile(
    title: String,
    modifier: Modifier = Modifier,
    showRetry: Boolean = false,
) {
    val base = MaterialTheme.colorScheme.surfaceVariant
    val highlight = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
    Box(
        modifier =
            modifier.background(
                Brush.linearGradient(listOf(base, highlight, base), start = Offset.Zero, end = Offset.Infinite),
                shape = MaterialTheme.shapes.small,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = title.trim().take(1).uppercase().ifBlank { "?" },
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (showRetry) {
                Icon(
                    Icons.Outlined.Refresh,
                    contentDescription = "Retry loading image",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier =
                        Modifier
                            .padding(top = 6.dp)
                            .size(18.dp),
                )
            }
        }
    }
}

@Composable
fun ShimmerSurface(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = MaterialTheme.shapes.small,
) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmerProgress",
    )
    val base = MaterialTheme.colorScheme.surfaceVariant
    val sheen = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)
    val startPx = 700f
    val brush =
        Brush.linearGradient(
            colors = listOf(base, sheen, base),
            start = Offset(progress * startPx - startPx, 0f),
            end = Offset(progress * startPx, 220f),
        )
    Box(modifier.clip(shape).background(brush))
}

/** A rounded skeleton placeholder used by loading feeds. */
@Composable
fun PosterSkeleton(modifier: Modifier = Modifier) {
    ShimmerSurface(modifier = modifier.clip(MaterialTheme.shapes.medium))
}
