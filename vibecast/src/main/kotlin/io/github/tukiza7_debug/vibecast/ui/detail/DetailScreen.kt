package io.github.tukiza7_debug.vibecast.ui.detail

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.tukiza7_debug.vibecast.data.db.ExtensionEntity
import io.github.tukiza7_debug.vibecast.data.repo.ExtensionRepository
import io.github.tukiza7_debug.vibecast.data.repo.FeedRepository
import io.github.tukiza7_debug.vibecast.data.repo.HistoryRepository
import io.github.tukiza7_debug.vibecast.player.PlayerConnection
import io.github.tukiza7_debug.vibecast.ui.common.CoverImage
import io.github.tukiza7_debug.vibecast.ui.common.ErrorState
import io.github.tukiza7_debug.vibecast.ui.common.pressScale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DetailState(
    val loading: Boolean = true,
    val error: String? = null,
    val synopsis: String? = null,
    val cover: String? = null,
    val episodes: List<io.github.tukiza7_debug.vibecast.core.model.Episode> = emptyList(),
    val resumeMs: Long = 0L,
    val isFavorite: Boolean = false,
    val resolving: Boolean = false,
)

@HiltViewModel
class DetailViewModel
    @Inject
    constructor(
        private val extensionRepository: ExtensionRepository,
        private val feedRepository: FeedRepository,
        private val historyRepository: HistoryRepository,
        private val playerConnection: PlayerConnection,
        private val settings: io.github.tukiza7_debug.vibecast.data.prefs.SettingsStore,
    ) : ViewModel() {
        private val _state = MutableStateFlow(DetailState())
        val state: StateFlow<DetailState> = _state.asStateFlow()

        private var extension: io.github.tukiza7_debug.vibecast.core.model.Extension? = null
        private var entity: ExtensionEntity? = null
        private var itemUrl: String = ""
        private var title: String = ""

        fun load(
            extensionId: String,
            itemUrl: String,
            title: String,
            cover: String?,
        ) {
            if (this.itemUrl == itemUrl && !_state.value.loading && extension != null) return
            this.itemUrl = itemUrl
            this.title = title
            _state.value = DetailState(loading = true, cover = cover)
            viewModelScope.launch {
                try {
                    val e = extensionRepository.get(extensionId) ?: throw IllegalStateException("extension not installed")
                    entity = e
                    val ext = feedRepository.load(e)
                    extension = ext
                    val detail = feedRepository.detail(ext, itemUrl)
                    val episodes = feedRepository.episodes(ext, itemUrl)
                    // Aggregate position for the header hint: the newest history row for
                    // this item (per-episode resume is applied in play()).
                    val resume = historyRepository.itemResume(extensionId, itemUrl)
                    _state.value =
                        DetailState(
                            loading = false,
                            synopsis = detail.synopsis,
                            cover = cover ?: detail.cover,
                            episodes = episodes,
                            resumeMs = resume,
                            isFavorite = historyRepository.isFavorite(extensionId, itemUrl),
                        )
                } catch (ex: Exception) {
                    _state.value =
                        DetailState(
                            loading = false,
                            error = friendlyDetailError(ex),
                            cover = cover,
                        )
                }
            }
        }

        fun toggleFavorite() {
            val id = entity?.id ?: return
            viewModelScope.launch {
                val added = historyRepository.toggleFavorite(id, title, itemUrl, _state.value.cover)
                _state.value = _state.value.copy(isFavorite = added)
            }
        }

        /**
         * Resolves the selected episode first and starts playback immediately; the
         * remaining episodes are resolved lazily in the background and appended to
         * the queue so auto-advance keeps working without blocking the start.
         */
        fun play(
            episodeIndex: Int,
            onReady: () -> Unit,
        ) {
            val ext = extension ?: return
            val id = entity?.id ?: return
            val episodes = _state.value.episodes
            if (episodes.isEmpty()) return
            val requested = episodes.getOrNull(episodeIndex) ?: return
            viewModelScope.launch {
                _state.value = _state.value.copy(resolving = true, error = null)
                try {
                    val stream = feedRepository.stream(ext, requested.url)
                    // Resume must match the episode being played, not the detail page.
                    val startMs = historyRepository.resumePosition(id, requested.url)
                    val selectedItem =
                        PlayerConnection.PlaybackItem(
                            mediaId = requested.url,
                            title = requested.name,
                            streamRef = stream,
                            extensionId = id,
                            cover = _state.value.cover,
                            baseUrl = ext.baseUrl,
                            itemUrl = itemUrl,
                        )
                    val speed = settings.defaultSpeed.firstOrNull() ?: 1f
                    val started = playerConnection.setPlaylist(listOf(selectedItem), 0, startMs, speed)
                    if (!started) {
                        throw IllegalStateException(playerConnection.error.value ?: "playback could not start")
                    }
                    onReady()
                    // Background prefetch in episode order; failures skip just that episode.
                    episodes.forEachIndexed { index, episode ->
                        if (index == episodeIndex) return@forEachIndexed
                        val resolved = runCatching { feedRepository.stream(ext, episode.url) }.getOrNull() ?: return@forEachIndexed
                        playerConnection.append(
                            listOf(
                                PlayerConnection.PlaybackItem(
                                    mediaId = episode.url,
                                    title = episode.name,
                                    streamRef = resolved,
                                    extensionId = id,
                                    cover = _state.value.cover,
                                    baseUrl = ext.baseUrl,
                                    itemUrl = itemUrl,
                                ),
                            ),
                        )
                    }
                } catch (ex: Exception) {
                    _state.value = _state.value.copy(error = friendlyDetailError(ex))
                } finally {
                    _state.value = _state.value.copy(resolving = false)
                }
            }
        }
    }

/** Short, human error text for detail/playback failures. */
internal fun friendlyDetailError(ex: Exception): String =
    when {
        ex.message?.contains("not installed", ignoreCase = true) == true -> "this source is no longer installed"
        ex is java.io.IOException -> "network problem - check your connection and try again"
        ex.message?.contains("no episodes", ignoreCase = true) == true -> "no episodes were found on this page"
        ex.message?.contains("stream URL is empty", ignoreCase = true) == true -> "this episode has no playable stream"
        ex.message?.contains("no playable streams", ignoreCase = true) == true -> "no playable stream was found for this episode"
        else -> "this item could not be loaded right now"
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    extensionId: String,
    itemUrl: String,
    title: String,
    cover: String?,
    onBack: () -> Unit,
    autoplay: Boolean = false,
    viewModel: DetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(extensionId, itemUrl) {
        viewModel.load(extensionId, itemUrl, title, cover)
    }
    // autoplay: start the first episode (or the resumed one) once episodes arrive
    LaunchedEffect(autoplay, state.episodes, state.loading) {
        if (autoplay && !state.loading && state.episodes.isNotEmpty()) {
            viewModel.play(0) { openPlayerScreen(context) }
        }
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleFavorite() }) {
                        Icon(
                            if (state.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = "Favorite",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        }
        item {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(210.dp),
            ) {
                CoverImage(
                    url = state.cover ?: cover,
                    title = title,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.6f to Color.Black.copy(alpha = 0.2f),
                                1f to Color.Black.copy(alpha = 0.8f),
                            ),
                        ),
                )
                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp),
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (state.resumeMs > 30_000) {
                        Text(
                            "Resume at ${formatMs(state.resumeMs)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                state.synopsis?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                }
                Button(
                    onClick = { viewModel.play(0) { openPlayerScreen(context) } },
                    enabled = !state.loading && state.episodes.isNotEmpty() && !state.resolving,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    if (state.resolving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("Preparing stream...")
                    } else {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            when {
                                state.resumeMs > 30_000 -> "Resume"
                                state.episodes.size == 1 -> "Play"
                                else -> "Play first episode"
                            },
                        )
                    }
                }
            }
        }
        item { Spacer(Modifier.height(2.dp)) }
        when {
            state.loading ->
                item {
                    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            state.error != null -> item { ErrorState(state.error ?: "") }
            else ->
                itemsIndexed(state.episodes, key = { index, episode -> "$index-${episode.url}" }) { index, episode ->
                    EpisodeRow(
                        index = index,
                        name = episode.name,
                        number = episode.number,
                        onClick = { viewModel.play(index) { openPlayerScreen(context) } },
                    )
                }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

/** Episode row styled as a soft card with a numbered badge and hover-like press scale. */
@Composable
private fun EpisodeRow(
    index: Int,
    name: String,
    number: Int?,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier =
            Modifier
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .fillMaxWidth()
                .pressScale(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    (number ?: (index + 1)).toString(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = "Play $name",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Opens the always-immersive player screen bound to the shared playback session. */
fun openPlayerScreen(context: Context) {
    context.startActivity(Intent(context, io.github.tukiza7_debug.vibecast.player.PlayerActivity::class.java))
}

internal fun formatMs(ms: Long): String {
    val totalSeconds = ms / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
