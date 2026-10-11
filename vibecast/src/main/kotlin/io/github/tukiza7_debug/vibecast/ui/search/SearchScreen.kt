package io.github.tukiza7_debug.vibecast.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.tukiza7_debug.vibecast.data.repo.ExtensionRepository
import io.github.tukiza7_debug.vibecast.data.repo.FeedRepository
import io.github.tukiza7_debug.vibecast.ui.common.EmptyState
import io.github.tukiza7_debug.vibecast.ui.common.ErrorState
import io.github.tukiza7_debug.vibecast.ui.common.PosterCard
import io.github.tukiza7_debug.vibecast.ui.common.RowHeader
import io.github.tukiza7_debug.vibecast.ui.nav.detailRouteFor
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchGroup(
    val extensionId: String,
    val extensionName: String,
    val items: List<io.github.tukiza7_debug.vibecast.core.model.SearchItem>,
    val error: String?,
)

@HiltViewModel
class SearchViewModel
    @Inject
    constructor(
        private val extensionRepository: ExtensionRepository,
        private val feedRepository: FeedRepository,
    ) : ViewModel() {
        val extensions =
            extensionRepository.observeAll()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        private val _query = MutableStateFlow("")
        val query: StateFlow<String> = _query.asStateFlow()

        private val _selected = MutableStateFlow<String?>(null)
        val selected: StateFlow<String?> = _selected.asStateFlow()

        private val _groups = MutableStateFlow<List<SearchGroup>>(emptyList())
        val groups: StateFlow<List<SearchGroup>> = _groups.asStateFlow()

        private val _busy = MutableStateFlow(false)
        val busy: StateFlow<Boolean> = _busy.asStateFlow()

        private var searchJob: Job? = null

        fun onQueryChange(value: String) {
            _query.value = value
            searchJob?.cancel()
            if (value.isBlank()) {
                _groups.value = emptyList()
                return
            }
            searchJob =
                viewModelScope.launch {
                    delay(300)
                    runSearch(value)
                }
        }

        fun onExtensionFilter(id: String?) {
            _selected.value = id
            if (_query.value.isNotBlank()) {
                searchJob?.cancel()
                searchJob = viewModelScope.launch { runSearch(_query.value) }
            }
        }

        private suspend fun runSearch(query: String) {
            _busy.value = true
            val enabled = extensionRepository.enabled()
            val targets = enabled.filter { _selected.value == null || it.id == _selected.value }
            _groups.value =
                targets.map { entity ->
                    try {
                        val ext = feedRepository.load(entity)
                        SearchGroup(entity.id, entity.name, feedRepository.search(ext, query, 1), error = null)
                    } catch (e: Exception) {
                        SearchGroup(entity.id, entity.name, emptyList(), error = e.message ?: "search failed")
                    }
                }
            _busy.value = false
        }
    }

@Composable
fun SearchScreen(
    onOpenDetail: (String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsState()
    val selected by viewModel.selected.collectAsState()
    val groups by viewModel.groups.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val extensions by viewModel.extensions.collectAsState()
    var textFieldValue by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = textFieldValue,
            onValueChange = {
                textFieldValue = it
                viewModel.onQueryChange(it)
            },
            label = { Text("Search one or all extensions") },
            singleLine = true,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                FilterChip(selected = selected == null, onClick = { viewModel.onExtensionFilter(null) }, label = { Text("All") })
            }
            items(extensions, key = { it.id }) { ext ->
                FilterChip(
                    selected = selected == ext.id,
                    onClick = { viewModel.onExtensionFilter(ext.id) },
                    label = { Text(ext.name) },
                )
            }
        }
        if (query.isBlank()) {
            EmptyState("Type to search across installed extensions", Modifier.weight(1f))
        } else if (busy && groups.isEmpty()) {
            EmptyState("Searching…", Modifier.weight(1f))
        } else {
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 16.dp)) {
                groups.forEach { group ->
                    item(key = "${group.extensionId}-header") { RowHeader(group.extensionName, subtitle = group.error) }
                    when {
                        group.error != null -> item(key = "${group.extensionId}-err") { ErrorState(group.error ?: "") }
                        group.items.isEmpty() -> item(key = "${group.extensionId}-empty") { EmptyState("No results") }
                        else ->
                            item(key = "${group.extensionId}-items") {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    items(group.items, key = { it.url }) { item ->
                                        PosterCard(item) { onOpenDetail(detailRouteFor(group.extensionId, item)) }
                                    }
                                }
                            }
                    }
                    item(key = "${group.extensionId}-space") { Spacer(Modifier.height(10.dp)) }
                }
            }
        }
    }
}
