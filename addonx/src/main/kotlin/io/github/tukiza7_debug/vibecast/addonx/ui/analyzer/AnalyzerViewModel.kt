package io.github.tukiza7_debug.vibecast.addonx.ui.analyzer

import android.annotation.SuppressLint
import android.webkit.WebView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.tukiza7_debug.vibecast.adblock.FilterEngine
import io.github.tukiza7_debug.vibecast.adblock.FilterEngine.SitePolicy
import io.github.tukiza7_debug.vibecast.addonx.analyzer.DomAnalyzer
import io.github.tukiza7_debug.vibecast.addonx.analyzer.ExtensionBuilder
import io.github.tukiza7_debug.vibecast.addonx.data.BuildStore
import io.github.tukiza7_debug.vibecast.addonx.data.TokenStore
import io.github.tukiza7_debug.vibecast.addonx.github.GitHubApi
import io.github.tukiza7_debug.vibecast.addonx.github.GitHubException
import io.github.tukiza7_debug.vibecast.addonx.github.GitHubWorkflowRunner
import io.github.tukiza7_debug.vibecast.core.codes.UnixCodec
import io.github.tukiza7_debug.vibecast.core.engine.ExtensionEngine
import io.github.tukiza7_debug.vibecast.core.http.HttpClient
import io.github.tukiza7_debug.vibecast.core.http.HttpRequest
import io.github.tukiza7_debug.vibecast.core.validate.SchemaValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import javax.inject.Inject

@HiltViewModel
class AnalyzerViewModel
    @Inject
    constructor(
        val engine: FilterEngine,
        private val httpClient: HttpClient,
        private val tokenStore: TokenStore,
        private val buildStore: BuildStore,
        private val gitHubApi: GitHubApi,
        private val workflowRunner: GitHubWorkflowRunner,
        private val bridgeFactory: PickBridgeFactory,
    ) : ViewModel() {
        val policyProvider: () -> SitePolicy = { SitePolicy() }

        private val engineInstance = ExtensionEngine(httpClient)

        private val _blockedCount = MutableStateFlow(0)
        val blockedCount: StateFlow<Int> = _blockedCount.asStateFlow()

        private val _pickMode = MutableStateFlow(false)
        val pickMode: StateFlow<Boolean> = _pickMode.asStateFlow()

        private val _recording = MutableStateFlow(false)
        val recording: StateFlow<Boolean> = _recording.asStateFlow()

        private val _loading = MutableStateFlow(true)
        val loading: StateFlow<Boolean> = _loading.asStateFlow()

        private val _analysis = MutableStateFlow<DomAnalyzer.Analysis?>(null)
        val analysis: StateFlow<DomAnalyzer.Analysis?> = _analysis.asStateFlow()

        private val _recordedJson = MutableStateFlow<List<RecordedJson>>(emptyList())
        val recordedJson: StateFlow<List<RecordedJson>> = _recordedJson.asStateFlow()

        private val _recordedMedia = MutableStateFlow<List<String>>(emptyList())
        val recordedMedia: StateFlow<List<String>> = _recordedMedia.asStateFlow()

        private val _pageUrl = MutableStateFlow("")
        val pageUrl: StateFlow<String> = _pageUrl.asStateFlow()

        private val _picked = MutableStateFlow<Assignment?>(null)
        val picked: StateFlow<Assignment?> = _picked.asStateFlow()

        private val _assignments = MutableStateFlow<Map<String, String>>(emptyMap())
        val assignments: StateFlow<Map<String, String>> = _assignments.asStateFlow()

        private val _lastAction = MutableStateFlow<String?>(null)
        val lastAction: StateFlow<String?> = _lastAction.asStateFlow()

        data class RecordedJson(val url: String, val itemCount: Int, val sampleKeys: List<String>)

        data class Assignment(val selector: String, val outerHtml: String, val text: String)

        private var lastWebView: WebView? = null

        fun pickBridge(webView: WebView): PickBridge = bridgeFactory.create(this)

        fun currentAssignment(): String? = null

        fun togglePickMode(holder: AnalyzerWebViewHolder) {
            _pickMode.value = !_pickMode.value
            lastWebView = holder.holder
            holder.holder?.evaluateJavascript(
                if (_pickMode.value) PickerJs.enable() else PickerJs.disable(),
                null,
            )
        }

        fun toggleRecording() {
            _recording.value = !_recording.value
        }

        fun onBlocked(url: String) {
            _blockedCount.value += 1
        }

        fun onRequest(
            url: String,
            type: io.github.tukiza7_debug.vibecast.adblock.rules.ResourceType?,
            blocked: Boolean,
        ) {
            if (blocked || !_recording.value) return
        }

        fun recordMediaCandidate(url: String) {
            if (io.github.tukiza7_debug.vibecast.core.engine.MimeInference.MEDIA_URL_REGEX.containsMatchIn(url) &&
                _recordedMedia.value.none { it == url }
            ) {
                _recordedMedia.value = _recordedMedia.value + url
            }
        }

        fun onPageLoaded(
            view: WebView,
            url: String,
        ) {
            _loading.value = false
            _pageUrl.value = url
            view.evaluateJavascript("document.documentElement.outerHTML") { html ->
                viewModelScope.launch(Dispatchers.Default) {
                    val text = html?.trim('"')?.replace("\\\"", "\"")?.replace("\\\\", "\\") ?: return@launch
                    _analysis.value = DomAnalyzer.analyze(text, url)
                    _detailFields.value = DomAnalyzer.analyzeDetail(text, url)
                }
            }
        }

        private val _detailFields = MutableStateFlow<Map<String, String>>(emptyMap())
        val detailFields: StateFlow<Map<String, String>> = _detailFields.asStateFlow()

        /** Called from the JS bridge when the user taps an element in pick mode. */
        fun onPicked(
            selector: String,
            outerHtml: String,
            text: String,
        ) {
            _picked.value = Assignment(selector, outerHtml, text)
        }

        fun assignToField(field: String) {
            val pickedValue = _picked.value ?: return
            _assignments.value = _assignments.value + (field to pickedValue.selector)
            _lastAction.value = "assigned $field -> ${pickedValue.selector}"
        }

        fun clearAssignment(field: String) {
            _assignments.value = _assignments.value - field
        }

        /** Fetches a recorded JSON endpoint to propose mappings for it. */
        fun inspectJsonEndpoint(url: String) {
            viewModelScope.launch(Dispatchers.IO) {
                runCatching {
                    val response = httpClient.fetch(HttpRequest(url, headers = mapOf("User-Agent" to "AddOnX/1.0")))
                    val element = Json.parseToJsonElement(response.bodyText())
                    val array = firstArray(element) ?: JsonArray(emptyList())
                    val keys = (array.firstOrNull() as? JsonObject)?.keys?.toList() ?: emptyList()
                    _recordedJson.value = _recordedJson.value + RecordedJson(url, array.size, keys)
                }
            }
        }

        private fun firstArray(element: JsonElement): JsonArray? =
            when (element) {
                is JsonArray -> element
                is JsonObject -> element.values.firstNotNullOfOrNull { firstArray(it) }
                else -> null
            }

        fun buildDraft(
            id: String,
            name: String,
            version: Int,
        ): ExtensionBuilder.Draft? {
            val analysis = _analysis.value ?: return null
            val search = analysis.proposals.firstOrNull() ?: return null
            val base =
                io.github.tukiza7_debug.vibecast.core.engine.UrlResolver.absolutize(
                    "",
                    _pageUrl.value.ifEmpty { "https://invalid.test" },
                )
            val searchTemplate = DomAnalyzer.buildSearchTemplate(_pageUrl.value, "test") ?: _pageUrl.value
            return ExtensionBuilder.Draft(
                id = id,
                name = name,
                version = version,
                lang = "en",
                baseUrl = base,
                headers = emptyMap(),
                search =
                    ExtensionBuilder.StepDraft(
                        name = "search",
                        requestUrl = searchTemplate,
                        sourceType = "html",
                        itemSelector = search.itemSelector,
                        jsonPath = null,
                        fields =
                            linkedMapOf(
                                "title" to ExtensionBuilder.FieldDraft(selector = search.titleSelector),
                                "url" to ExtensionBuilder.FieldDraft(selector = search.linkSelector),
                                "cover" to ExtensionBuilder.FieldDraft(selector = search.coverSelector),
                            ),
                    ),
                latest = null,
                detail =
                    ExtensionBuilder.StepDraft(
                        name = "detail",
                        requestUrl = "{url}",
                        sourceType = "html",
                        itemSelector = null,
                        jsonPath = null,
                        fields =
                            linkedMapOf(
                                "title" to ExtensionBuilder.FieldDraft(selector = _assignments.value["title"]),
                                "synopsis" to ExtensionBuilder.FieldDraft(selector = _assignments.value["synopsis"]),
                                "cover" to ExtensionBuilder.FieldDraft(selector = _assignments.value["cover"]),
                            ),
                    ),
                episodes =
                    ExtensionBuilder.StepDraft(
                        name = "episodes",
                        requestUrl = "{url}",
                        sourceType = "html",
                        itemSelector = _assignments.value["episodes"] ?: ".episode",
                        jsonPath = null,
                        fields =
                            linkedMapOf(
                                "name" to ExtensionBuilder.FieldDraft(selector = _assignments.value["episodeName"]),
                                "url" to ExtensionBuilder.FieldDraft(selector = _assignments.value["episodeUrl"]),
                            ),
                    ),
                video =
                    ExtensionBuilder.VideoDraft(
                        requestUrl = "{url}",
                        strategy = "regex",
                        pattern = "file\\s*:\\s*\"([^\"]+\\.(?:m3u8|mp4))\"",
                    ),
            )
        }

        fun exportExtension(
            draft: ExtensionBuilder.Draft,
            onReady: (Result<Pair<String, String>>) -> Unit,
        ) {
            viewModelScope.launch(Dispatchers.Default) {
                val result =
                    runCatching {
                        val json = ExtensionBuilder.build(draft)
                        SchemaValidator.decodeAndValidate(json)
                        json to UnixCodec.encode(json)
                    }
                onReady(result)
            }
        }

        fun publishToGitHub(
            extensionJson: String,
            owner: String,
            repo: String,
            path: String,
            onResult: (Result<GitHubApi.PublishResult>) -> Unit,
        ) {
            viewModelScope.launch(Dispatchers.IO) {
                val token = tokenStore.token()
                if (token == null) {
                    onResult(Result.failure(GitHubException("no GitHub token configured")))
                    return@launch
                }
                onResult(
                    runCatching {
                        gitHubApi.publishExtension(token, owner, repo, path, extensionJson, "Add AddOnX extension $path")
                    },
                )
            }
        }

        fun addToVibeCast(
            extensionJson: String,
            onResult: (Result<Unit>) -> Unit,
        ) {
            viewModelScope.launch(Dispatchers.IO) {
                onResult(runCatching { VibeCastInstaller.insert(extensionJson) })
            }
        }

        fun buildSiteApk(
            extensionCode: String,
            appName: String,
            packageId: String,
            onResult: (Result<ByteArray>) -> Unit,
        ) {
            viewModelScope.launch(Dispatchers.IO) {
                val token = tokenStore.token()
                if (token == null) {
                    onResult(Result.failure(GitHubException("no GitHub token configured")))
                    return@launch
                }
                val repo = buildStore.currentRepo()
                if (repo == null) {
                    onResult(Result.failure(GitHubException("target repository is not configured")))
                    return@launch
                }
                val outcome = workflowRunner.buildSiteApk(token, repo.first, repo.second, extensionCode, appName, packageId)
                onResult(
                    when (outcome) {
                        is GitHubWorkflowRunner.SiteBuildResult.Apk -> Result.success(outcome.bytes)
                        is GitHubWorkflowRunner.SiteBuildResult.Failed -> Result.failure(GitHubException(outcome.reason))
                    },
                )
            }
        }
    }

/** The JS bridge object injected into the page as window.VibePick. */
class PickBridge(private val viewModel: AnalyzerViewModel) {

    @android.webkit.JavascriptInterface
    fun pick(selector: String, outerHtml: String, text: String) {
        viewModel.onPicked(selector, outerHtml, text)
    }
}

class PickBridgeFactory @Inject constructor() {

    fun create(viewModel: AnalyzerViewModel): PickBridge = PickBridge(viewModel)
}

/** Inserts the extension into a same-device VibeCast through its signature-protected provider. */
object VibeCastInstaller {
    fun insert(extensionJson: String) {
        val appContext = AppContextHolder.get()
        val authorities =
            appContext.packageManager
                .getInstalledPackages(0)
                .firstOrNull { it.packageName.startsWith("io.github.tukiza7_debug.vibecast") }
                ?.packageName
                ?.plus(".extensions")
                ?: throw IllegalStateException("VibeCast is not installed on this device")
        val uri = android.net.Uri.parse("content://$authorities/extension")
        val values = android.content.ContentValues().apply { put("extension_json", extensionJson) }
        val resolver = appContext.contentResolver
        val result = resolver.insert(uri, values)
        if (result == null || result.pathSegments.lastOrNull() == "error") {
            throw IllegalStateException("VibeCast rejected the extension")
        }
    }
}

object AppContextHolder {
    @Volatile
    private var appContext: android.content.Context? = null

    fun init(context: android.content.Context) {
        appContext = context.applicationContext
    }

    fun get(): android.content.Context = appContext ?: throw IllegalStateException("AppContextHolder not initialised")
}

object PickerJs {
    fun enable(): String =
        """
        (function() {
          if (window.__vibePickHandler) return;
          function buildSelector(el) {
            var path = [];
            var node = el;
            while (node && node.tagName && node.tagName.toLowerCase() !== 'html' && path.length < 6) {
              var tag = node.tagName.toLowerCase();
              var id = node.id ? node.id : null;
              var classes = Array.prototype.slice.call(node.classList || []).slice(0, 2);
              var nth = 1;
              var sib = node;
              while ((sib = sib.previousElementSibling)) {
                if (sib.tagName === node.tagName) nth++;
              }
              path.push({tag: tag, id: id, classes: classes, nth: nth});
              if (id) break;
              node = node.parentElement;
            }
            var segs = path.map(function(p) {
              var s = p.tag;
              p.classes.forEach(function(c) { s += '.' + c; });
              s += ':nth-of-type(' + p.nth + ')';
              if (p.id) s = '#' + p.id;
              return s;
            }).reverse().join(' > ');
            return segs;
          }
          window.__vibePickHandler = function(event) {
            event.stopPropagation();
            event.preventDefault();
            var el = event.target;
            var selector = buildSelector(el);
            VibePick.pick(selector, el.outerHTML.substring(0, 4000), (el.textContent || '').trim().substring(0, 200));
            document.removeEventListener('click', window.__vibePickHandler, true);
            window.__vibePickHandler = null;
            document.documentElement.style.outline = '';
          };
          document.addEventListener('click', window.__vibePickHandler, true);
          document.documentElement.style.outline = '3px solid #FF7A45';
        })();
        """.trimIndent()

    fun disable(): String =
        """
        (function() {
          if (window.__vibePickHandler) {
            document.removeEventListener('click', window.__vibePickHandler, true);
            window.__vibePickHandler = null;
          }
          document.documentElement.style.outline = '';
        })();
        """.trimIndent()
}
