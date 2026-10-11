package io.github.tukiza7_debug.vibecast.addonx.ui.analyzer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.tukiza7_debug.vibecast.addonx.analyzer.ExtensionBuilder
import io.github.tukiza7_debug.vibecast.core.engine.EngineException
import io.github.tukiza7_debug.vibecast.core.engine.ExtensionEngine
import io.github.tukiza7_debug.vibecast.core.http.HttpClient
import io.github.tukiza7_debug.vibecast.core.model.Extension
import io.github.tukiza7_debug.vibecast.core.model.SearchItem
import io.github.tukiza7_debug.vibecast.core.validate.SchemaValidator
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
    val streamUrl: String? = null,
    val streamMime: String? = null,
    /** Sample items from list steps, so the panel can preview real covers. */
    val items: List<SearchItem> = emptyList(),
    /** Detail cover, when the step produced one. */
    val coverUrl: String? = null,
)

/**
 * Runs extension steps against the real engine. The pipeline mirrors VibeCast's
 * navigation order: search picks the first item, detail and episodes run on it,
 * video resolves on the first episode.
 */
@HiltViewModel
class StepTestViewModel
    @Inject
    constructor(
        private val httpClient: HttpClient,
    ) : ViewModel() {
        private val _results = MutableStateFlow<List<StepResult>>(emptyList())
        val results: StateFlow<List<StepResult>> = _results.asStateFlow()

        private val _pipelineRunning = MutableStateFlow(false)
        val pipelineRunning: StateFlow<Boolean> = _pipelineRunning.asStateFlow()

        private val engine = ExtensionEngine(httpClient)

        fun reportSetupError(message: String) {
            _results.value = listOf(StepResult(step = "setup", timingMs = 0, output = "", error = message)) + _results.value
        }

        fun clear() {
            _results.value = emptyList()
            _draft.value = null
        }

        private val _draft = MutableStateFlow<ExtensionBuilder.Draft?>(null)
        val draft: StateFlow<ExtensionBuilder.Draft?> = _draft.asStateFlow()

        fun runPipeline(draft: ExtensionBuilder.Draft) {
            _pipelineRunning.value = true
            _results.value = emptyList()
            _draft.value = draft
            viewModelScope.launch(Dispatchers.IO) {
                val outcome =
                    runCatching {
                        val json = ExtensionBuilder.build(draft)
                        val extension = SchemaValidator.decodeAndValidate(json)
                        runSteps(extension)
                    }
                withContext(Dispatchers.Main) { _pipelineRunning.value = false }
                outcome.exceptionOrNull()?.let { e ->
                    appendResult(StepResult(step = "pipeline", timingMs = 0, output = "", error = e.message))
                }
            }
        }

        private suspend fun runSteps(extension: Extension) {
            val started = System.currentTimeMillis()
            val searchItems =
                runCatching { engine.search(extension, "test", 1).take(5) }.getOrElse { e ->
                    appendResult(
                        StepResult(
                            "search",
                            System.currentTimeMillis() - started,
                            "",
                            (e as? EngineException)?.message ?: e.message,
                        ),
                    )
                    null
                }
            if (searchItems == null) return
            appendResult(
                StepResult(
                    step = "search",
                    timingMs = System.currentTimeMillis() - started,
                    output = "${searchItems.size} items; first: ${searchItems.firstOrNull()?.title}",
                    error = null,
                    items = searchItems,
                ),
            )
            val firstUrl = searchItems.firstOrNull()?.url
            if (firstUrl == null) {
                appendResult(StepResult("detail", 0, "", "skipped: search returned no items"))
                appendResult(StepResult("episodes", 0, "", "skipped: search returned no items"))
                appendResult(StepResult("video", 0, "", "skipped: search returned no items"))
                return
            }
            val detailStarted = System.currentTimeMillis()
            runCatching { engine.detail(extension, firstUrl) }
                .onSuccess { d ->
                    appendResult(
                        StepResult(
                            step = "detail",
                            timingMs = System.currentTimeMillis() - detailStarted,
                            output = "title=${d.title ?: "-"} synopsis=${d.synopsis?.take(60) ?: "-"}",
                            error = null,
                            coverUrl = d.cover,
                        ),
                    )
                }
                .onFailure { e ->
                    appendResult(
                        StepResult(
                            "detail",
                            System.currentTimeMillis() - detailStarted,
                            "",
                            (e as? EngineException)?.message ?: e.message,
                        ),
                    )
                }
            val episodes =
                runCatching { engine.episodes(extension, firstUrl) }.getOrElse { e ->
                    appendResult(StepResult("episodes", 0, "", (e as? EngineException)?.message ?: e.message))
                    null
                }
            if (episodes != null) {
                appendResult(measured("episodes") { "${episodes.size} episodes; first: ${episodes.firstOrNull()?.name}" })
                val episodeUrl = episodes.firstOrNull()?.url
                if (episodeUrl != null) {
                    val started = System.currentTimeMillis()
                    runCatching { engine.video(extension, episodeUrl) }
                        .onSuccess { stream ->
                            appendResult(
                                StepResult(
                                    step = "video",
                                    timingMs = System.currentTimeMillis() - started,
                                    output = "url=${stream.url.take(120)} mime=${stream.mimeType ?: "-"}",
                                    error = null,
                                    streamUrl = stream.url,
                                    streamMime = stream.mimeType,
                                ),
                            )
                        }
                        .onFailure { e ->
                            appendResult(
                                StepResult(
                                    step = "video",
                                    timingMs = System.currentTimeMillis() - started,
                                    output = "",
                                    error = (e as? EngineException)?.message ?: e.message,
                                ),
                            )
                        }
                } else {
                    appendResult(StepResult("video", 0, "", "skipped: no episode url"))
                }
            } else {
                appendResult(StepResult("video", 0, "", "skipped: episodes failed"))
            }
        }

        private suspend fun measured(
            step: String,
            block: suspend () -> String,
        ): StepResult {
            val started = System.currentTimeMillis()
            return try {
                StepResult(step = step, timingMs = System.currentTimeMillis() - started, output = block(), error = null)
            } catch (e: Exception) {
                StepResult(
                    step = step,
                    timingMs = System.currentTimeMillis() - started,
                    output = "",
                    error = (e as? EngineException)?.message ?: e.message,
                )
            }
        }

        private fun appendResult(result: StepResult) {
            _results.value = listOf(result) + _results.value
        }
    }
