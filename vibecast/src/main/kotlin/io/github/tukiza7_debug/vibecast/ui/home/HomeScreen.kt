package io.github.tukiza7_debug.vibecast.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import io.github.tukiza7_debug.vibecast.data.db.HistoryEntity
import io.github.tukiza7_debug.vibecast.data.repo.ExtensionRepository
import io.github.tukiza7_debug.vibecast.data.repo.FeedRepository
import io.github.tukiza7_debug.vibecast.data.repo.HistoryRepository
import io.github.tukiza7_debug.vibecast.ui.common.EmptyState
import io.github.tukiza7_debug.vibecast.ui.common.ErrorState
import io.github.tukiza7_debug.vibecast.ui.common.PosterCard
import io.github.tukiza7_debug.vibecast.ui.common.RowHeader
import io.github.tukiza7_debug.vibecast.ui.nav.detailRouteFor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        private val extensionRepository: ExtensionRepository,
        private val feedRepository: FeedRepository,
        historyRepository: HistoryRepository,
    ) : ViewModel() {
        val extensions =
            extensionRepository.observeAll()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        val continueWatching: StateFlow<List<HistoryEntity>> =
            historyRepository.observeHistory()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        private val _rows = MutableStateFlow<List<FeedRepository.HomeRow>>(emptyList())
        val rows: StateFlow<List<FeedRepository.HomeRow>> = _rows.asStateFlow()

        private val _refreshing = MutableStateFlow(false)
        val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

        init {
            refresh()
            viewModelScope.launch {
                extensions.collect { refresh() }
            }
        }

        fun refresh() {
            viewModelScope.launch {
                _refreshing.value = true
                val enabled = extensionRepository.enabled()
                _rows.value = feedRepository.homeRows(enabled)
                _refreshing.value = false
            }
        }
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenDetail: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenExtensions: () -> Unit,
    snackbarHostState: androidx.compose.material3.SnackbarHostState,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val rows by viewModel.rows.collectAsState()
    val refreshing by viewModel.refreshing.collectAsState()
    val history by viewModel.continueWatching.collectAsState()

    PullToRefreshBox(isRefreshing = refreshing, onRefresh = { viewModel.refresh() }) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            item {
                TopAppBar(
                    title = { Text("VibeCast", style = MaterialTheme.typography.headlineMedium) },
                    actions = {
                        IconButton(onClick = { viewModel.refresh() }) {
                            Icon(Icons.Outlined.Refresh, contentDescription = "Refresh")
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Filled.Settings, contentDescription = "Settings")
                        }
                    },
                )
            }
            if (rows.isEmpty() && !refreshing) {
                item {
                    EmptyState(
                        icon = Icons.Outlined.Extension,
                        title = "Add your first source",
                        body =
                            "VibeCast plays through extensions. Add one with a UNIX, GC or GX code, " +
                                "install the bundled sample, or build your own with AddOnX.",
                        actionLabel = "Go to Extensions",
                        onAction = onOpenExtensions,
                    )
                }
            }
            if (history.isNotEmpty()) {
                item(key = "continue-header") {
                    RowHeader("Continue watching", subtitle = null)
                }
                item(key = "continue-row") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(history.take(12), key = { it.itemKey }) { entry ->
                            ResumeCard(entry) {
                                onOpenDetail(
                                    detailRouteFor(
                                        entry.extensionId,
                                        io.github.tukiza7_debug.vibecast.core.model.SearchItem(entry.title, entry.url, entry.cover),
                                    ),
                                )
                            }
                        }
                    }
                }
                item(key = "continue-space") { Spacer(Modifier.height(10.dp)) }
            }
            rows.forEach { row ->
                item(key = "${row.extensionId}-${row.title}-header") {
                    RowHeader("${row.extensionName} - ${row.title}", subtitle = null)
                }
                if (row.error != null) {
                    item(key = "${row.extensionId}-${row.title}-error") {
                        ErrorState(row.error ?: "")
                    }
                } else {
                    item(key = "${row.extensionId}-${row.title}-items") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(row.items, key = { it.url }) { item ->
                                PosterCard(item) { onOpenDetail(detailRouteFor(row.extensionId, item)) }
                            }
                        }
                    }
                }
                item(key = "${row.extensionId}-${row.title}-space") { Spacer(Modifier.height(10.dp)) }
            }
        }
    }
}

/** Resume card with a progress bar showing how far the last session got. */
@Composable
private fun ResumeCard(
    entry: HistoryEntity,
    onClick: () -> Unit,
) {
    androidx.compose.material3.Card(onClick = onClick, modifier = Modifier.width(150.dp)) {
        Column {
            AsyncImage(
                model = entry.cover,
                contentDescription = entry.title,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(84.dp)
                        .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)),
            )
            Column(Modifier.padding(10.dp)) {
                Text(
                    entry.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val progress =
                    if (entry.durationMs > 0) {
                        (entry.positionMs.toFloat() / entry.durationMs).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                )
                Text(
                    "${formatMinutes(entry.positionMs)} of ${formatMinutes(entry.durationMs)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

private fun formatMinutes(ms: Long): String {
    val totalMinutes = ms / 60000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "$hours h $minutes min" else "$minutes min"
}
