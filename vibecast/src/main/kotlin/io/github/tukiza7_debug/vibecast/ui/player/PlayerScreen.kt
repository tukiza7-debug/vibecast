package io.github.tukiza7_debug.vibecast.ui.player

import android.app.Activity
import android.media.AudioManager
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.tukiza7_debug.vibecast.player.PlayerConnection
import io.github.tukiza7_debug.vibecast.player.QualitySelectorLogic
import io.github.tukiza7_debug.vibecast.ui.detail.formatMs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.abs

@HiltViewModel
class PlayerViewModel
    @Inject
    constructor(
        val playerConnection: PlayerConnection,
    ) : ViewModel()

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun PlayerScreen(
    onFinish: () -> Unit,
    onEnterPip: () -> Unit,
    speed: Float,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val connection = viewModel.playerConnection
    val isPlaying by connection.isPlaying.collectAsState()
    val position by connection.positionMs.collectAsState()
    val duration by connection.durationMs.collectAsState()
    val error by connection.error.collectAsState()
    val tracks by connection.tracks.collectAsState()
    val currentId by connection.currentMediaId.collectAsState()
    val playlist by connection.playlist.collectAsState()
    val controller by connection.controller.collectAsState()
    val context = LocalContext.current

    var controlsVisible by remember { mutableStateOf(true) }
    var speedMenuOpen by remember { mutableStateOf(false) }
    var qualityMenuOpen by remember { mutableStateOf(false) }
    var fullscreen by remember { mutableStateOf(false) }
    var seekHint by remember { mutableStateOf<String?>(null) }

    // scrubbing state: the slider and the horizontal drag update a local target
    // and seek once on release instead of firing a seek per frame
    var scrubTargetMs by remember { mutableStateOf<Long?>(null) }
    var dragAnchorX by remember { mutableStateOf(0f) }
    var verticalDragMode by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(isPlaying, controlsVisible) {
        if (isPlaying && controlsVisible) {
            delay(3500)
            controlsVisible = false
        }
    }
    LaunchedEffect(Unit) {
        val activity = context as? Activity ?: return@LaunchedEffect
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black),
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            update = { view -> view.player = controller },
            modifier =
                Modifier
                    .fillMaxSize()
                    .then(
                        if (fullscreen) {
                            Modifier
                        } else {
                            Modifier.align(Alignment.Center).fillMaxWidth().aspectRatio(16f / 9f)
                        },
                    )
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { controlsVisible = !controlsVisible },
                            onDoubleTap = {
                                // Double tap seeks 10 seconds in the tapped direction.
                                val forward = it.x > size.width / 2
                                viewModel.viewModelScope.launch {
                                    connection.seekTo(
                                        (if (forward) position + 10_000 else position - 10_000).coerceIn(0, duration),
                                    )
                                }
                                seekHint = if (forward) "+10s" else "-10s"
                            },
                        )
                    }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                dragAnchorX = offset.x
                                verticalDragMode = null
                            },
                            onDragEnd = {
                                // one seek per gesture, not one per frame
                                scrubTargetMs?.let { target ->
                                    viewModel.viewModelScope.launch { connection.seekTo(target) }
                                }
                                scrubTargetMs = null
                            },
                        ) { change, dragAmount ->
                            change.consume()
                            val horizontal = abs(dragAmount.x) > abs(dragAmount.y)
                            if (horizontal) {
                                val base = scrubTargetMs ?: position
                                val target = (base + dragAmount.x * 120).coerceIn(0f, duration.toFloat())
                                scrubTargetMs = target.toLong()
                                seekHint = formatMs(target.toLong())
                            } else {
                                // left half = brightness, right half = volume (standard gesture)
                                val mode =
                                    verticalDragMode
                                        ?: (if (dragAnchorX < size.width / 2) "brightness" else "volume")
                                            .also { verticalDragMode = it }
                                if (mode == "volume") {
                                    adjustVolume(context, -dragAmount.y / 14f)
                                    seekHint = null
                                } else {
                                    adjustBrightness(context, -dragAmount.y / 14f)
                                    seekHint = null
                                }
                            }
                        }
                    },
        )
        seekHint?.let { hint ->
            Text(
                hint,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.align(Alignment.Center).background(Color(0x88000000)).padding(8.dp),
            )
            LaunchedEffect(hint) {
                delay(700)
                seekHint = null
            }
        }
        if (!isPlaying && controlsVisible) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
        if (error != null) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            ) {
                Row(
                    Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            friendlyPlaybackError(error),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        if (friendlyPlaybackError(error) != error) {
                            Text(
                                error.orEmpty(),
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                    androidx.compose.material3.TextButton(onClick = {
                        // real retry: re-resolve the stream through the extension,
                        // falling back to a plain play attempt
                        val id = currentId
                        viewModel.viewModelScope.launch {
                            val refreshed =
                                if (id != null) {
                                    runCatching { connection.refreshStream(id, position) }.getOrDefault(false)
                                } else {
                                    false
                                }
                            if (!refreshed) connection.playPause()
                        }
                    }) { Text("Retry") }
                }
            }
        }
        if (controlsVisible) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color(0xCC0B0E14))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Slider(
                    value =
                        when {
                            scrubTargetMs != null && duration > 0 -> scrubTargetMs!!.toFloat() / duration
                            duration > 0 -> position.toFloat() / duration
                            else -> 0f
                        },
                    onValueChange = { fraction ->
                        scrubTargetMs = (fraction * duration).toLong()
                        seekHint = formatMs(scrubTargetMs ?: 0)
                    },
                    onValueChangeFinished = {
                        scrubTargetMs?.let { target ->
                            viewModel.viewModelScope.launch { connection.seekTo(target) }
                        }
                        scrubTargetMs = null
                        seekHint = null
                    },
                    colors =
                        androidx.compose.material3.SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                        ),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { viewModel.viewModelScope.launch { connection.previous() } }) {
                        Icon(Icons.Filled.SkipPrevious, "Previous", tint = Color.White)
                    }
                    IconButton(onClick = { viewModel.viewModelScope.launch { connection.playPause() } }) {
                        Icon(
                            if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            "Play/pause",
                            tint = Color.White,
                        )
                    }
                    IconButton(onClick = { viewModel.viewModelScope.launch { connection.next() } }) {
                        Icon(Icons.Filled.SkipNext, "Next", tint = Color.White)
                    }
                    Text("${formatMs(position)} / ${formatMs(duration)}", color = Color.White)
                    Spacer(Modifier.weight(1f))
                    Box {
                        IconButton(onClick = { speedMenuOpen = true }) {
                            Icon(Icons.Filled.Speed, "Speed", tint = Color.White)
                        }
                        DropdownMenu(expanded = speedMenuOpen, onDismissRequest = { speedMenuOpen = false }) {
                            listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { s ->
                                DropdownMenuItem(
                                    text = { Text("${s}x${if (s == speed) " (current)" else ""}") },
                                    onClick = {
                                        viewModel.viewModelScope.launch { connection.setSpeed(s) }
                                        speedMenuOpen = false
                                    },
                                )
                            }
                        }
                    }
                    Box {
                        IconButton(onClick = { qualityMenuOpen = true }) {
                            Icon(Icons.Filled.Fullscreen, "Quality", tint = Color.White)
                        }
                        DropdownMenu(expanded = qualityMenuOpen, onDismissRequest = { qualityMenuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Auto") },
                                onClick = {
                                    viewModel.viewModelScope.launch {
                                        connection.applyTrackParameters { clearOverridesOfType(C.TRACK_TYPE_VIDEO) }
                                    }
                                    qualityMenuOpen = false
                                },
                            )
                            tracks?.let { t ->
                                QualitySelectorLogic.videoOptions(t).forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option.label) },
                                        onClick = {
                                            viewModel.viewModelScope.launch {
                                                connection.applyTrackParameters {
                                                    clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                                                    setOverrideForType(
                                                        TrackSelectionOverride(option.group.mediaTrackGroup, option.trackIndex),
                                                    )
                                                }
                                            }
                                            qualityMenuOpen = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                    IconButton(onClick = { fullscreen = !fullscreen }) {
                        Icon(Icons.Filled.Fullscreen, "Fullscreen", tint = Color.White)
                    }
                    IconButton(onClick = onEnterPip) {
                        Icon(Icons.Filled.PictureInPictureAlt, "Picture in picture", tint = Color.White)
                    }
                    IconButton(onClick = onFinish) {
                        Icon(Icons.Filled.Close, "Close", tint = Color.White)
                    }
                }
                Text(
                    "Playing ${playlist.indexOf(currentId).plus(1)} of ${playlist.size}",
                    color = Color(0xFF9AA5B5),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

private fun adjustVolume(
    context: android.content.Context,
    delta: Float,
) {
    val audio = context.getSystemService(android.content.Context.AUDIO_SERVICE) as? AudioManager ?: return
    val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    val current = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
    val target = (current + (delta * max).toInt()).coerceIn(0, max)
    audio.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
}

private fun adjustBrightness(
    context: android.content.Context,
    delta: Float,
) {
    val activity = context as? Activity ?: return
    val attributes = activity.window.attributes
    val current = if (attributes.screenBrightness < 0) 0.5f else attributes.screenBrightness
    attributes.screenBrightness = (current + delta).coerceIn(0.01f, 1f)
    activity.window.attributes = attributes
}

/**
 * Human playback error lines; the raw Media3 message stays as a secondary line
 * so nothing is hidden. "Unsupported source" cases get the clearest wording.
 */
internal fun friendlyPlaybackError(raw: String?): String {
    val message = raw ?: return "Playback stopped unexpectedly."
    val lower = message.lowercase()
    return when {
        "drm" in lower || "protected content" in lower ->
            "This stream is DRM-protected and cannot be played here."
        "unsupported" in lower || "does not support" in lower ->
            "This source uses a format the player cannot handle yet."
        "403" in lower || "forbidden" in lower ->
            "The stream refused this device (403). Its link may have expired - tap Retry to re-resolve it."
        "410" in lower || "gone" in lower ->
            "This stream link has expired. Tap Retry to re-resolve it from the source."
        "timeout" in lower || "timed out" in lower || "unable to connect" in lower ->
            "The stream could not be reached. Check your connection and retry."
        "network" in lower -> "A network problem interrupted playback."
        else -> "Playback stopped unexpectedly."
    }
}
