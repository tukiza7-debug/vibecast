package io.github.tukiza7_debug.vibecast.ui.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.tukiza7_debug.vibecast.data.db.FavoriteEntity
import io.github.tukiza7_debug.vibecast.data.db.HistoryEntity
import io.github.tukiza7_debug.vibecast.data.repo.ExtensionRepository
import io.github.tukiza7_debug.vibecast.data.repo.FeedRepository
import io.github.tukiza7_debug.vibecast.data.repo.HistoryRepository
import io.github.tukiza7_debug.vibecast.ui.common.EmptyState
import io.github.tukiza7_debug.vibecast.ui.nav.detailRouteFor
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel
    @Inject
    constructor(
        historyRepository: HistoryRepository,
        private val extensionRepository: ExtensionRepository,
        private val feedRepository: FeedRepository,
    ) : ViewModel() {
        val favorites =
            historyRepository.observeFavorites()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        val history =
            historyRepository.observeHistory()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        fun routeFor(
            entity: FavoriteEntity,
            onReady: (String?) -> Unit,
        ) {
            viewModelScope.launch {
                onReady(route(entity.extensionId, entity.url, entity.title, entity.cover))
            }
        }

        fun routeFor(
            entity: HistoryEntity,
            onReady: (String?) -> Unit,
        ) {
            viewModelScope.launch {
                onReady(route(entity.extensionId, entity.url, entity.title, entity.cover))
            }
        }

        private suspend fun route(
            extensionId: String,
            url: String,
            title: String,
            cover: String?,
        ): String? {
            val e = extensionRepository.get(extensionId) ?: return null
            val parsed = runCatching { feedRepository.load(e) }.getOrNull() ?: return null
            // URL is the canonical item key; detail needs the item page, not an episode url.
            val itemUrl = if (parsed.episodes.request.url.contains("{url}")) url else url
            return detailRouteFor(extensionId, io.github.tukiza7_debug.vibecast.core.model.SearchItem(title, itemUrl, cover))
        }
    }

@Composable
fun LibraryScreen(
    onOpenDetail: (String) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    var tab by remember { mutableIntStateOf(0) }
    val favorites by viewModel.favorites.collectAsState()
    val history by viewModel.history.collectAsState()

    Column(Modifier.fillMaxSize()) {
        Text(
            "Library",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(16.dp),
        )
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Favorites") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("History") })
        }
        when (tab) {
            0 ->
                if (favorites.isEmpty()) {
                    EmptyState("Nothing saved yet")
                } else {
                    LazyColumn {
                        items(favorites, key = { it.itemKey }) { favorite ->
                            LibraryCard(
                                title = favorite.title,
                                cover = favorite.cover,
                                subtitle = favorite.extensionId,
                                onClick = { viewModel.routeFor(favorite) { route -> route?.let(onOpenDetail) } },
                            )
                        }
                    }
                }
            else ->
                if (history.isEmpty()) {
                    EmptyState("Watch something first")
                } else {
                    LazyColumn {
                        items(history, key = { it.itemKey }) { entry ->
                            LibraryCard(
                                title = entry.title,
                                cover = entry.cover,
                                subtitle = "Watched at ${entry.positionMs / 60000} min",
                                onClick = { viewModel.routeFor(entry) { route -> route?.let(onOpenDetail) } },
                            )
                        }
                    }
                }
        }
    }
}

@Composable
private fun LibraryCard(
    title: String,
    cover: String?,
    subtitle: String,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = cover,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(10.dp)),
            )
            Column(Modifier.padding(12.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
