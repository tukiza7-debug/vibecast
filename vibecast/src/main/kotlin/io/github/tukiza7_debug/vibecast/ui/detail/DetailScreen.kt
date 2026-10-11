package io.github.tukiza7_debug.vibecast.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.tukiza7_debug.vibecast.data.db.ExtensionEntity
import io.github.tukiza7_debug.vibecast.data.repo.ExtensionRepository
import io.github.tukiza7_debug.vibecast.data.repo.FeedRepository
import io.github.tukiza7_debug.vibecast.data.repo.HistoryRepository
import io.github.tukiza7_debug.vibecast.player.PlayerConnection
import io.github.tukiza7_debug.vibecast.ui.common.ErrorState
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
                    val resume = historyRepository.resumePosition(extensionId, itemUrl)
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
                    _state.value = DetailState(loading = false, error = ex.message ?: "failed to load", cover = cover)
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

        fun play(episodeIndex: Int) {
            val ext = extension ?: return
            val id = entity?.id ?: return
            val episodes = _state.value.episodes
            if (episodes.isEmpty()) return
            viewModelScope.launch {
                try {
                    val startMs = if (episodeIndex == 0) _state.value.resumeMs else 0L
                    val items =
                        episodes.map { episode ->
                            val stream = feedRepository.stream(ext, episode.url)
                            PlayerConnection.PlaybackItem(
                                mediaId = episode.url,
                                title = episode.name,
                                streamRef = stream,
                                extensionId = id,
                                cover = _state.value.cover,
                                baseUrl = ext.baseUrl,
                            )
                        }
                    val speed = settings.defaultSpeed.firstOrNull() ?: 1f
                    playerConnection.setPlaylist(items, episodeIndex, startMs, speed)
                } catch (ex: Exception) {
                    _state.value = _state.value.copy(error = ex.message ?: "playback failed to start")
                }
            }
        }
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    extensionId: String,
    itemUrl: String,
    title: String,
    cover: String?,
    onBack: () -> Unit,
    viewModel: DetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(extensionId, itemUrl) {
        viewModel.load(extensionId, itemUrl, title, cover)
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
            )
        }
        item {
            Row(Modifier.padding(horizontal = 16.dp)) {
                AsyncImage(
                    model = state.cover ?: cover,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier
                            .width(130.dp)
                            .aspectRatio(0.68f)
                            .clip(MaterialTheme.shapes.medium),
                )
                Spacer(Modifier.width(14.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(title, style = MaterialTheme.typography.titleLarge)
                    state.synopsis?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (state.resumeMs > 30_000) {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                            Text(
                                "Resume at ${formatMs(state.resumeMs)}",
                                modifier = Modifier.padding(10.dp),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
        when {
            state.loading ->
                item {
                    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            state.error != null -> item { ErrorState(state.error ?: "") }
            else ->
                items(state.episodes, key = { it.url }) { episode ->
                    val index = state.episodes.indexOf(episode)
                    ListItem(
                        headlineContent = { Text(episode.name) },
                        supportingContent = { episode.number?.let { Text("Episode $it") } },
                        leadingContent = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 0.dp),
                        tonalElevation = 0.dp,
                        trailingContent = {
                            Button(onClick = { viewModel.play(index) }) { Text("Play") }
                        },
                    )
                }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

internal fun formatMs(ms: Long): String {
    val totalSeconds = ms / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
