package io.github.tukiza7_debug.vibecast.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.tukiza7_debug.vibecast.data.db.HistoryEntity
import io.github.tukiza7_debug.vibecast.data.repo.ExtensionRepository
import io.github.tukiza7_debug.vibecast.data.repo.FeedRepository
import io.github.tukiza7_debug.vibecast.data.repo.HistoryRepository
import io.github.tukiza7_debug.vibecast.ui.common.CoverImage
import io.github.tukiza7_debug.vibecast.ui.common.EmptyState
import io.github.tukiza7_debug.vibecast.ui.common.ErrorState
import io.github.tukiza7_debug.vibecast.ui.common.PosterCard
import io.github.tukiza7_debug.vibecast.ui.common.PosterSkeleton
import io.github.tukiza7_debug.vibecast.ui.common.RowHeader
import io.github.tukiza7_debug.vibecast.ui.common.pressScale
import io.github.tukiza7_debug.vibecast.ui.nav.detailRouteFor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
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

        private var refreshJob: kotlinx.coroutines.Job? = null

        init {
            // observeAll() replays the current list immediately, so the collector alone
            // drives the first load (no double refresh at startup).
            viewModelScope.launch {
                extensionRepository.observeAll()
                    .map { all -> all.filter { entity -> entity.enabled } }
                    .collect { entities ->
                        refreshJob?.cancel()
                        refreshJob = launch { loadRows(entities) }
                    }
            }
        }

        fun refresh() {
            refreshJob?.cancel()
            refreshJob =
                viewModelScope.launch {
                    val entities = extensionRepository.enabled()
                    loadRows(entities)
                }
        }

        /** Retries a single failed section without reloading the whole feed. */
        fun retrySection(extensionId: String) {
            viewModelScope.launch {
                val entity = extensionRepository.enabled().firstOrNull { it.id == extensionId } ?: return@launch
                val fresh = feedRepository.rowsForEntity(entity)
                _rows.value = _rows.value.filterNot { it.extensionId == extensionId } + fresh
            }
        }

        private suspend fun loadRows(entities: List<io.github.tukiza7_debug.vibecast.data.db.ExtensionEntity>) {
            if (entities.isEmpty()) {
                _rows.value = emptyList()
                _refreshing.value = false
                return
            }
            _refreshing.value = true
            try {
                feedRepository.homeRows(entities) { incoming ->
                    // Incremental: replace this extension's rows as soon as they finish,
                    // keeping arrival order stable for a snappy feed.
                    synchronized(_rows) {
                        val kept = _rows.value.filterNot { row -> incoming.any { it.extensionId == row.extensionId } }
                        _rows.value = kept + incoming
                    }
                }
            } finally {
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
            contentPadding = PaddingValues(bottom = 24.dp),
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
            val heroRow = rows.firstOrNull { it.error == null && it.items.isNotEmpty() }
            val heroItem = heroRow?.items?.firstOrNull()
            if (heroRow != null && heroItem != null) {
                item(key = "hero") {
                    HeroBanner(
                        item = heroItem,
                        onOpen = { onOpenDetail(detailRouteFor(heroRow.extensionId, heroItem, autoplay = true)) },
                        onInfo = { onOpenDetail(detailRouteFor(heroRow.extensionId, heroItem)) },
                    )
                }
                item(key = "hero-space") { Spacer(Modifier.height(6.dp)) }
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
            if (rows.isEmpty() && refreshing) {
                // skeleton feed: hero + two poster rows while the first load runs
                item(key = "skeleton-hero") {
                    PosterSkeleton(
                        modifier =
                            Modifier
                                .padding(horizontal = 16.dp)
                                .fillMaxWidth()
                                .height(210.dp),
                    )
                }
                item(key = "skeleton-row-1") { SkeletonPosterRow() }
                item(key = "skeleton-row-2") { SkeletonPosterRow() }
            }
            if (history.isNotEmpty()) {
                item(key = "continue-header") {
                    RowHeader("Continue watching", subtitle = "Pick up where you left off")
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
                                        io.github.tukiza7_debug.vibecast.core.model.SearchItem(
                                            entry.title,
                                            // open the item page, falling back for pre-migration rows
                                            entry.itemUrl.ifBlank { entry.url },
                                            entry.cover,
                                        ),
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
                        // a failed section never blocks the rest of Home; retry only re-runs it
                        ErrorState(
                            row.error ?: "",
                            onRetry =
                                if (row.canRetry) {
                                    { viewModel.retrySection(row.extensionId) }
                                } else {
                                    null
                                },
                        )
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

/** Shimmer stand-in for a poster row during first load. */
@Composable
private fun SkeletonPosterRow() {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(top = 8.dp),
    ) {
        items(4) {
            PosterSkeleton(
                modifier =
                    Modifier
                        .width(148.dp)
                        .height(222.dp),
            )
        }
    }
}

/**
 * Featured hero: large cover, bottom-up gradient scrim, title and Play / Info.
 * Shown from the first healthy feed row so Home opens with a cinematic focal point.
 */
@Composable
private fun HeroBanner(
    item: io.github.tukiza7_debug.vibecast.core.model.SearchItem,
    onOpen: () -> Unit,
    onInfo: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .height(230.dp)
                .clip(RoundedCornerShape(22.dp))
                .pressScale()
                .clickable(onClick = onInfo),
    ) {
        CoverImage(
            url = item.cover,
            title = item.title,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            0f to androidx.compose.ui.graphics.Color.Transparent,
                            0.55f to androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.15f),
                            1f to androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.85f),
                        ),
                    ),
        )
        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(16.dp),
        ) {
            Text(
                item.title,
                style = MaterialTheme.typography.headlineMedium,
                color = androidx.compose.ui.graphics.Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = onOpen,
                    contentPadding = PaddingValues(horizontal = 22.dp, vertical = 10.dp),
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Play")
                }
                OutlinedButton(
                    onClick = onInfo,
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                ) {
                    Text("Info", color = androidx.compose.ui.graphics.Color.White)
                }
            }
        }
    }
}

/** Resume card with a glowing progress bar showing how far the last session got. */
@Composable
private fun ResumeCard(
    entry: HistoryEntity,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.width(168.dp).pressScale(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column {
            CoverImage(
                url = entry.cover,
                title = entry.title,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(94.dp),
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
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surface,
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
