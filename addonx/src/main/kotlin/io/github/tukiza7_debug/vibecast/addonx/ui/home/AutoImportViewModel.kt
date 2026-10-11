package io.github.tukiza7_debug.vibecast.addonx.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.tukiza7_debug.vibecast.addonx.analyzer.AutoAnalyzer
import io.github.tukiza7_debug.vibecast.addonx.data.BuildStore
import io.github.tukiza7_debug.vibecast.addonx.data.DraftSession
import io.github.tukiza7_debug.vibecast.addonx.data.HistoryEntry
import io.github.tukiza7_debug.vibecast.addonx.data.HistoryStore
import io.github.tukiza7_debug.vibecast.core.engine.ExtensionEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed interface AutoUiState {
    data object Idle : AutoUiState

    data class Analyzing(
        val url: String,
        val stage: String,
    ) : AutoUiState

    data class Done(val result: AutoAnalyzer.AutoResult) : AutoUiState

    data class Failed(
        val url: String,
        val reason: String,
    ) : AutoUiState
}

/** A desktop-ish mobile UA so sites serve their full HTML to the analyzer. */
private const val MOBILE_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

/**
 * Drives the One-Link flow: paste a URL, fetch and analyze the page, keep the
 * result for the preview card and remember the link in the recent history.
 */
@HiltViewModel
class AutoImportViewModel
    @Inject
    constructor(
        private val httpClient: io.github.tukiza7_debug.vibecast.core.http.HttpClient,
        private val historyStore: HistoryStore,
        private val draftSession: DraftSession,
        private val tokenStore: io.github.tukiza7_debug.vibecast.addonx.data.TokenStore,
        private val buildStore: BuildStore,
        private val gitHubApi: io.github.tukiza7_debug.vibecast.addonx.github.GitHubApi,
        private val workflowRunner: io.github.tukiza7_debug.vibecast.addonx.github.GitHubWorkflowRunner,
    ) : ViewModel() {
        val recents: StateFlow<List<HistoryEntry>> =
            historyStore.recents.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        val builds: StateFlow<List<io.github.tukiza7_debug.vibecast.addonx.data.SavedBuild>> =
            historyStore.builds.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        private val _state = MutableStateFlow<AutoUiState>(AutoUiState.Idle)
        val state: StateFlow<AutoUiState> = _state.asStateFlow()

        private val engine = ExtensionEngine(httpClient)

        fun analyze(rawUrl: String) {
            val url = normalizeUrl(rawUrl)
            if (url == null) {
                _state.value = AutoUiState.Failed(rawUrl.trim(), "that does not look like a web link")
                return
            }
            _state.value = AutoUiState.Analyzing(url, "loading the page")
            viewModelScope.launch {
                val result =
                    withContext(Dispatchers.IO) {
                        runCatching {
                            val response =
                                httpClient.fetch(
                                    io.github.tukiza7_debug.vibecast.core.http.HttpRequest(
                                        url,
                                        headers =
                                            mapOf(
                                                "User-Agent" to
                                                    MOBILE_USER_AGENT,
                                                "Accept" to "text/html,application/xhtml+xml",
                                            ),
                                    ),
                                )
                            if (response.code !in 200..399) {
                                throw IllegalStateException("HTTP ${response.code} - the site refused the analyzer request")
                            }
                            _state.value = AutoUiState.Analyzing(url, "reading the page structure")
                            val html = response.bodyText()
                            // network evidence for the one-link flow: probe JSON data
                            // endpoints the page references (same idea as the manual
                            // analyzer's record button, minus the WebView)
                            _state.value = AutoUiState.Analyzing(url, "listening for data endpoints")
                            val recording =
                                runCatching {
                                    AutoAnalyzer.Recording(
                                        jsonEndpoints =
                                            io.github.tukiza7_debug.vibecast.addonx.analyzer.JsonEndpointProbe.probeAll(
                                                httpClient,
                                                html,
                                                url,
                                            ),
                                    )
                                }.getOrDefault(AutoAnalyzer.Recording())
                            AutoAnalyzer.analyze(url, html, recording, engine)
                        }
                    }
                result
                    .onSuccess { auto ->
                        historyStore.addRecent(
                            HistoryEntry(
                                url = url,
                                host = auto.host,
                                title = auto.suggestedName,
                                analyzedAtMs = System.currentTimeMillis(),
                                confidence = auto.overallConfidence.name.lowercase(),
                                pageType = auto.pageType.name.lowercase(),
                            ),
                        )
                        _state.value = AutoUiState.Done(auto)
                    }
                    .onFailure { e ->
                        _state.value = AutoUiState.Failed(url, e.message ?: "the page could not be analyzed")
                    }
            }
        }

        /** Opens the draft produced by Auto Import in the analyzer for editing/testing. */
        fun openInAnalyzer(
            result: AutoAnalyzer.AutoResult,
            openTestTab: Boolean = false,
        ) {
            draftSession.open(result.draft, fromAutoImport = true, openTestTab = openTestTab)
        }

        fun reset() {
            _state.value = AutoUiState.Idle
        }

        /** Validates the draft, saves it under My Builds and returns the (json, code) pair. */
        fun export(
            result: AutoAnalyzer.AutoResult,
            name: String,
            onDone: (Result<Pair<String, String>>) -> Unit,
        ) {
            viewModelScope.launch(Dispatchers.Default) {
                val outcome =
                    runCatching {
                        val draft = result.draft.copy(name = name.ifBlank { result.suggestedName })
                        val json = io.github.tukiza7_debug.vibecast.addonx.analyzer.ExtensionBuilder.build(draft)
                        io.github.tukiza7_debug.vibecast.core.validate.SchemaValidator.decodeAndValidate(json)
                        val code = io.github.tukiza7_debug.vibecast.core.codes.UnixCodec.encode(json)
                        historyStore.addBuild(
                            io.github.tukiza7_debug.vibecast.addonx.data.SavedBuild(
                                name = draft.name,
                                id = draft.id,
                                json = json,
                                unixCode = code,
                                createdAtMs = System.currentTimeMillis(),
                                sourceUrl = result.url,
                            ),
                        )
                        json to code
                    }
                withContext(Dispatchers.Main) { onDone(outcome) }
            }
        }

        fun sendToVibeCast(
            json: String,
            onResult: (Result<Unit>) -> Unit,
        ) {
            viewModelScope.launch(Dispatchers.IO) {
                val outcome =
                    runCatching {
                        io.github.tukiza7_debug.vibecast.addonx.ui.analyzer.VibeCastInstaller.insert(json)
                    }
                withContext(Dispatchers.Main) { onResult(outcome) }
            }
        }

        fun publishToGitHub(
            json: String,
            path: String,
            onResult: (Result<io.github.tukiza7_debug.vibecast.addonx.github.GitHubApi.PublishResult>) -> Unit,
        ) {
            viewModelScope.launch(Dispatchers.IO) {
                val token = tokenStore.token()
                if (token == null) {
                    withContext(Dispatchers.Main) {
                        onResult(
                            Result.failure(
                                io.github.tukiza7_debug.vibecast.addonx.github.GitHubException(
                                    "no GitHub token configured - add one in Settings",
                                ),
                            ),
                        )
                    }
                    return@launch
                }
                val repo = buildStore.currentRepo()
                if (repo == null) {
                    withContext(Dispatchers.Main) {
                        onResult(
                            Result.failure(
                                io.github.tukiza7_debug.vibecast.addonx.github.GitHubException(
                                    "target repository is not configured - set it in Settings",
                                ),
                            ),
                        )
                    }
                    return@launch
                }
                val outcome =
                    runCatching {
                        gitHubApi.publishExtension(token, repo.first, repo.second, path, json, "Add AddOnX extension $path")
                    }
                withContext(Dispatchers.Main) { onResult(outcome) }
            }
        }

        fun buildSiteApk(
            code: String,
            appName: String,
            onResult: (Result<ByteArray>) -> Unit,
        ) {
            viewModelScope.launch(Dispatchers.IO) {
                val token = tokenStore.token()
                if (token == null) {
                    withContext(Dispatchers.Main) {
                        onResult(
                            Result.failure(
                                io.github.tukiza7_debug.vibecast.addonx.github.GitHubException(
                                    "no GitHub token configured - add one in Settings",
                                ),
                            ),
                        )
                    }
                    return@launch
                }
                val repo = buildStore.currentRepo()
                if (repo == null) {
                    withContext(Dispatchers.Main) {
                        onResult(
                            Result.failure(
                                io.github.tukiza7_debug.vibecast.addonx.github.GitHubException(
                                    "target repository is not configured - set it in Settings",
                                ),
                            ),
                        )
                    }
                    return@launch
                }
                val packageId = "io.github.vibecast.site." + (kotlin.math.abs(appName.hashCode()) % 100000)
                val outcome = workflowRunner.buildSiteApk(token, repo.first, repo.second, code, appName, packageId)
                val mapped =
                    when (outcome) {
                        is io.github.tukiza7_debug.vibecast.addonx.github.GitHubWorkflowRunner.SiteBuildResult.Apk ->
                            Result.success(
                                outcome.bytes,
                            )
                        is io.github.tukiza7_debug.vibecast.addonx.github.GitHubWorkflowRunner.SiteBuildResult.Failed ->
                            Result.failure(io.github.tukiza7_debug.vibecast.addonx.github.GitHubException(outcome.reason))
                    }
                withContext(Dispatchers.Main) { onResult(mapped) }
            }
        }

        fun removeRecent(url: String) {
            viewModelScope.launch { historyStore.removeRecent(url) }
        }

        fun clearHistory() {
            viewModelScope.launch { historyStore.clearRecents() }
        }

        private fun normalizeUrl(input: String): String? {
            val trimmed = input.trim()
            if (trimmed.isEmpty()) return null
            val withScheme: String =
                when {
                    trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
                    else -> "https://$trimmed"
                }
            val host = io.github.tukiza7_debug.vibecast.core.engine.UrlResolver.hostOf(withScheme) ?: return null
            if (!host.contains('.')) return null
            return withScheme
        }
    }
