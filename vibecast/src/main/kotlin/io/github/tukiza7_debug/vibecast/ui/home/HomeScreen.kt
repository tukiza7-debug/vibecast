package io.github.tukiza7_debug.vibecast.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.tukiza7_debug.vibecast.data.repo.ExtensionRepository
import io.github.tukiza7_debug.vibecast.data.repo.FeedRepository
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
    ) : ViewModel() {
        val extensions =
            extensionRepository.observeAll()
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
    snackbarHostState: androidx.compose.material3.SnackbarHostState,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val rows by viewModel.rows.collectAsState()
    val refreshing by viewModel.refreshing.collectAsState()

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
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("No extensions yet", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Open the Extensions tab and add one with a UNIX, GC or GX code, or build one with AddOnX.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
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
