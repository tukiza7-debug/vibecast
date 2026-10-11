package io.github.tukiza7_debug.vibecast.addonx.ui.analyzer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.tukiza7_debug.vibecast.core.engine.EngineException
import io.github.tukiza7_debug.vibecast.core.engine.ExtensionEngine
import io.github.tukiza7_debug.vibecast.core.http.HttpClient
import io.github.tukiza7_debug.vibecast.core.model.Extension
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class StepResult(
    val step: String,
    val timingMs: Long,
    val output: String,
    val error: String?,
)

@HiltViewModel
class StepTestViewModel
    @Inject
    constructor(
        private val httpClient: HttpClient,
    ) : ViewModel() {
        private val _results = MutableStateFlow<List<StepResult>>(emptyList())
        val results: StateFlow<List<StepResult>> = _results.asStateFlow()

        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running.asStateFlow()

        private val engine = ExtensionEngine(httpClient)

        fun runStep(
            extension: Extension,
            step: String,
            itemUrl: String,
        ) {
            _running.value = true
            viewModelScope.launch(Dispatchers.IO) {
                val started = System.currentTimeMillis()
                val result =
                    runCatching {
                        withContext(Dispatchers.IO) {
                            when (step) {
                                "search" -> engine.search(extension, "test", 1).take(5).toString()
                                "latest" -> engine.latest(extension, 1).take(5).toString()
                                "detail" -> engine.detail(extension, itemUrl).toString()
                                "episodes" -> engine.episodes(extension, itemUrl).take(20).toString()
                                "video" -> engine.video(extension, itemUrl).let { "${it.url} mime=${it.mimeType} quality=${it.quality}" }
                                else -> throw EngineException(step, null, "unknown step")
                            }
                        }
                    }
                val elapsed = System.currentTimeMillis() - started
                _results.value = listOf(
                    StepResult(
                        step = step,
                        timingMs = elapsed,
                        output = result.getOrNull() ?: "",
                        error = result.exceptionOrNull()?.message,
                    ),
                ) + _results.value
                _running.value = false
            }
        }
    }

@Composable
fun TestPanel(
    viewModel: AnalyzerViewModel,
    modifier: Modifier = Modifier,
) {
    val detailFields by viewModel.detailFields.collectAsState()
    val recordedMedia by viewModel.recordedMedia.collectAsState()
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(14.dp)) {
        Text("Step tests", style = MaterialTheme.typography.titleLarge)
        Text(
            "Build the draft from the current proposals and run each step against the real engine. " +
                "The video step result can be previewed after export.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        detailFields.forEach { (field, selector) ->
            Text("$field -> $selector", style = MaterialTheme.typography.bodyMedium)
        }
        Text("Media candidates: ${recordedMedia.size}", style = MaterialTheme.typography.bodyMedium)
    }
}
