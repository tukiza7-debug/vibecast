package io.github.tukiza7_debug.vibecast.core.engine

import io.github.tukiza7_debug.vibecast.core.http.HostFilter
import io.github.tukiza7_debug.vibecast.core.http.HttpClient
import io.github.tukiza7_debug.vibecast.core.http.HttpRequest
import io.github.tukiza7_debug.vibecast.core.http.HttpResponse
import io.github.tukiza7_debug.vibecast.core.http.NoopHostFilter
import io.github.tukiza7_debug.vibecast.core.json.MiniJsonPath
import io.github.tukiza7_debug.vibecast.core.model.DetailResult
import io.github.tukiza7_debug.vibecast.core.model.Episode
import io.github.tukiza7_debug.vibecast.core.model.Extension
import io.github.tukiza7_debug.vibecast.core.model.FieldExtraction
import io.github.tukiza7_debug.vibecast.core.model.ListStep
import io.github.tukiza7_debug.vibecast.core.model.OptionalList
import io.github.tukiza7_debug.vibecast.core.model.SearchItem
import io.github.tukiza7_debug.vibecast.core.model.StreamRef
import io.github.tukiza7_debug.vibecast.core.model.SubtitleTrack
import io.github.tukiza7_debug.vibecast.core.model.VideoStep
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.io.IOException

/**
 * Executes extension steps against real network sources. All methods block, so callers
 * must run them off the main thread. Every failure names its step and field.
 */
class ExtensionEngine(
    private val http: HttpClient,
    private val hostFilter: HostFilter = NoopHostFilter,
    private val sniffer: WebSniffer? = null,
) {
    fun search(
        extension: Extension,
        query: String,
        page: Int,
    ): List<SearchItem> =
        runItems(extension, extension.search, "search", mapOf("query" to query, "page" to page.toString()))
            .map { SearchItem(title = it.required("search", "title"), url = it.required("search", "url"), cover = it["cover"]) }

    fun latest(
        extension: Extension,
        page: Int,
    ): List<SearchItem> {
        val step = requireStep(extension.latest, "latest")
        return runItems(extension, step, "latest", mapOf("page" to page.toString()))
            .map { SearchItem(title = it.required("latest", "title"), url = it.required("latest", "url"), cover = it["cover"]) }
    }

    fun popular(
        extension: Extension,
        page: Int,
    ): List<SearchItem> {
        val step = requireStep(extension.popular, "popular")
        return runItems(extension, step, "popular", mapOf("page" to page.toString()))
            .map { SearchItem(title = it.required("popular", "title"), url = it.required("popular", "url"), cover = it["cover"]) }
    }

    /**
     * Runs the optional `latest` step without throwing when it is missing.
     * Returns [OptionalList.NotPresent] when the extension does not define the
     * step; a defined step that fails at network level still throws
     * [EngineException] so callers can show a per-section error with retry.
     */
    fun latestOptional(
        extension: Extension,
        page: Int,
    ): OptionalList {
        val step = extension.latest ?: return OptionalList.NotPresent
        return OptionalList.Present(latest(extension, page))
    }

    /**
     * Runs the optional `popular` step without throwing when it is missing.
     * See [latestOptional] for the missing-versus-failed contract.
     */
    fun popularOptional(
        extension: Extension,
        page: Int,
    ): OptionalList {
        val step = extension.popular ?: return OptionalList.NotPresent
        return OptionalList.Present(popular(extension, page))
    }

    fun detail(
        extension: Extension,
        itemUrl: String,
    ): DetailResult {
        val raw = runItems(extension, extension.detail, "detail", mapOf("url" to itemUrl)).firstOrNull().orEmpty()
        return DetailResult(title = raw["title"], cover = raw["cover"], synopsis = raw["synopsis"])
    }

    fun episodes(
        extension: Extension,
        itemUrl: String,
    ): List<Episode> {
        val items = runItems(extension, extension.episodes, "episodes", mapOf("url" to itemUrl))
        if (items.isEmpty()) throw EngineException("episodes", "source", "no episodes found at $itemUrl")
        return items.mapIndexed { index, map ->
            Episode(
                name = map["name"]?.takeIf { it.isNotBlank() } ?: "Episode ${index + 1}",
                number = map["number"]?.trim()?.toIntOrNull(),
                url = map.required("episodes", "url"),
            )
        }
    }

    fun video(
        extension: Extension,
        episodeUrl: String,
    ): StreamRef {
        val step = extension.video
        val headers = extension.headers + step.headers
        val pageUrl = UrlResolver.resolveTemplate(step.request.url, extension.baseUrl, mapOf("url" to episodeUrl))
        val resolved =
            when (step.strategy) {
                STRATEGY_DIRECT -> resolveDirect(step, extension.baseUrl, episodeUrl, headers)
                STRATEGY_REGEX -> resolveRegex(step, pageUrl, headers)
                STRATEGY_IFRAME -> resolveIframe(step, pageUrl, headers)
                STRATEGY_WEBVIEW -> return resolveWithSniffer(step, pageUrl, headers)
                else -> throw EngineException("video", "strategy", "unknown strategy '${step.strategy}'")
            }
        val finalHeaders = resolved.referer?.let { headers + mapOf("Referer" to it) } ?: headers
        val mimeType =
            when {
                MimeInference.fromUrl(resolved.url) != null -> MimeInference.fromUrl(resolved.url)
                resolved.probeDone -> resolved.probedMime
                else -> probeMimeType(resolved.url, finalHeaders, step.timeoutMs)
            }
        return StreamRef(
            url = resolved.url,
            mimeType = mimeType,
            headers = finalHeaders,
            quality = extractFromPage(resolved.contextText, resolved.referer ?: pageUrl, step.quality, "video", "quality"),
            subtitles =
                step.subtitles.mapNotNull { spec ->
                    val url = extractFromPage(resolved.contextText, pageUrl, spec.url, "video", "subtitles") ?: return@mapNotNull null
                    SubtitleTrack(UrlResolver.absolutize(url, pageUrl), spec.language, spec.label)
                },
        )
    }

    /**
     * Validates an extension structurally without touching the network: templates must
     * resolve with sample variables, selectors must parse against an empty document and
     * every regex must compile. Returns all issues found, named by step and field.
     */
    fun dryRun(extension: Extension): List<String> {
        val issues = mutableListOf<String>()
        val sampleVars =
            mapOf(
                "query" to "sample query",
                "page" to "1",
                "url" to extension.baseUrl,
            )

        fun checkListStep(
            name: String,
            step: ListStep?,
        ) {
            step ?: return
            try {
                UrlResolver.resolveTemplate(step.request.url, extension.baseUrl, sampleVars)
            } catch (e: Exception) {
                issues.add("[$name.request.url] template does not resolve: ${e.message}")
            }
            if (step.source.type.lowercase() == SOURCE_HTML && !step.source.selector.isNullOrBlank()) {
                try {
                    Jsoup.parse("<html><body></body></html>").select(step.source.selector)
                } catch (e: Exception) {
                    issues.add("[$name.source] invalid selector '${step.source.selector}': ${e.message}")
                }
            }
            step.fields.forEach { (fieldName, extraction) ->
                if (extraction.regex != null) {
                    try {
                        Regex(extraction.regex)
                    } catch (e: Exception) {
                        issues.add("[$name.fields.$fieldName] invalid regex '${extraction.regex}': ${e.message}")
                    }
                }
                if (extraction.selector != null && step.source.type.lowercase() == SOURCE_HTML) {
                    try {
                        Jsoup.parse("<html><body></body></html>").select(extraction.selector)
                    } catch (e: Exception) {
                        issues.add("[$name.fields.$fieldName] invalid selector '${extraction.selector}': ${e.message}")
                    }
                }
            }
            if (step.source.filterField != null &&
                step.source.filterEquals == null &&
                step.source.filterSuffix == null &&
                step.source.filterContains == null
            ) {
                issues.add("[$name.source] filterField needs filterEquals, filterSuffix or filterContains")
            }
        }
        checkListStep("search", extension.search)
        checkListStep("latest", extension.latest)
        checkListStep("popular", extension.popular)
        checkListStep("detail", extension.detail)
        checkListStep("episodes", extension.episodes)
        try {
            UrlResolver.resolveTemplate(extension.video.request.url, extension.baseUrl, sampleVars)
        } catch (e: Exception) {
            issues.add("[video.request.url] template does not resolve: ${e.message}")
        }
        if (extension.video.strategy !in listOf(STRATEGY_DIRECT, STRATEGY_REGEX, STRATEGY_IFRAME, STRATEGY_WEBVIEW)) {
            issues.add("[video.strategy] unknown strategy '${extension.video.strategy}'")
        }
        extension.video.pattern?.let { pattern ->
            try {
                Regex(pattern)
            } catch (e: Exception) {
                issues.add("[video.pattern] invalid regex '$pattern': ${e.message}")
            }
        }
        return issues
    }

    private fun resolveDirect(
        step: VideoStep,
        baseUrl: String,
        episodeUrl: String,
        headers: Map<String, String>,
    ): ResolvedStream {
        val url = UrlResolver.resolveTemplate(step.request.url, baseUrl, mapOf("url" to episodeUrl))
        val probedMime = probeMimeType(url, headers, step.timeoutMs)
        return ResolvedStream(url = url, contextText = null, referer = null, probedMime = probedMime, probeDone = true)
    }

    private fun resolveRegex(
        step: VideoStep,
        pageUrl: String,
        headers: Map<String, String>,
    ): ResolvedStream {
        val pattern = requirePattern(step)
        val text = fetchText(pageUrl, headers, step.timeoutMs)
        val found =
            runRegex(pattern, text)
                ?: throw EngineException("video", "pattern", "pattern did not match page $pageUrl")
        return ResolvedStream(UrlResolver.absolutize(found, pageUrl), text, null)
    }

    private fun resolveIframe(
        step: VideoStep,
        pageUrl: String,
        headers: Map<String, String>,
    ): ResolvedStream {
        val doc = Jsoup.parse(fetchText(pageUrl, headers, step.timeoutMs), pageUrl)
        val iframe =
            doc.selectFirst(step.selector ?: "iframe")
                ?: throw EngineException("video", "selector", "no iframe matched '${step.selector ?: "iframe"}' on $pageUrl")
        val src = iframe.absUrl("src").ifEmpty { iframe.absUrl("data-src") }
        if (src.isEmpty()) throw EngineException("video", "selector", "iframe has no src attribute")
        if (MimeInference.fromUrl(src) != null) return ResolvedStream(src, null, pageUrl)
        val pattern =
            step.pattern ?: throw EngineException(
                "video",
                "strategy",
                "iframe src $src is an embed page; add a pattern or use webviewSniff",
            )
        val embedHeaders = headers + mapOf("Referer" to pageUrl)
        val embedText = fetchText(src, embedHeaders, step.timeoutMs)
        val found =
            runRegex(pattern, embedText)
                ?: throw EngineException("video", "pattern", "pattern did not match embed page $src")
        return ResolvedStream(UrlResolver.absolutize(found, src), embedText, src)
    }

    private fun resolveWithSniffer(
        step: VideoStep,
        pageUrl: String,
        headers: Map<String, String>,
    ): StreamRef {
        val snifferImpl =
            sniffer ?: throw SniffingUnavailableException(
                "webviewSniff needs a WebView host; this environment cannot load embed pages",
            )
        return when (val outcome = snifferImpl.sniff(pageUrl, headers, step.timeoutMs)) {
            is SniffOutcome.Found ->
                StreamRef(
                    url = outcome.url,
                    mimeType = outcome.mimeType ?: MimeInference.fromUrl(outcome.url),
                    headers = headers + outcome.requestHeaders,
                    quality = extractFromPage(null, pageUrl, step.quality, "video", "quality"),
                    subtitles = emptyList(),
                )
            is SniffOutcome.Failed -> throw EngineException(
                "video",
                "strategy",
                "webviewSniff failed after ${step.timeoutMs} ms: ${outcome.reason}",
            )
        }
    }

    private fun runItems(
        extension: Extension,
        step: ListStep,
        stepName: String,
        vars: Map<String, String>,
    ): List<Map<String, String?>> {
        val requestUrl = UrlResolver.resolveTemplate(step.request.url, extension.baseUrl, vars)
        val response = fetchChecked(requestUrl, stepName, extension.headers + step.request.headers)
        val targets: List<Target> =
            when (step.source.type.lowercase()) {
                SOURCE_HTML -> {
                    val doc = Jsoup.parse(response.bodyText(), requestUrl)
                    val selector = step.source.selector
                    val elements = if (selector.isNullOrBlank()) listOf(doc) else doc.select(selector)
                    elements.map { HtmlTarget(it) }
                }
                SOURCE_JSON -> {
                    val root = parseJson(response.bodyText(), stepName, requestUrl)
                    val path = step.source.path
                    val targets =
                        when {
                            path == null -> listOf(JsonTarget(root, root))
                            else -> {
                                val resolved =
                                    MiniJsonPath.resolve(root, path)
                                        ?: throw EngineException(
                                            stepName,
                                            "source",
                                            "path '$path' did not resolve in response from $requestUrl",
                                        )
                                when (resolved) {
                                    is JsonArray -> resolved.map { JsonTarget(it, root) }
                                    else -> listOf(JsonTarget(resolved, root))
                                }
                            }
                        }
                    targets
                }
                else -> throw EngineException(stepName, "source", "unknown source type '${step.source.type}'")
            }
        val filtered = applyFilter(targets, step, stepName, requestUrl)
        return filtered.map { target ->
            step.fields.mapValues { (fieldName, extraction) ->
                extractFromTarget(target, extraction, stepName, fieldName)
            }
        }
    }

    private fun applyFilter(
        targets: List<Target>,
        step: ListStep,
        stepName: String,
        requestUrl: String,
    ): List<Target> {
        val field = step.source.filterField
        val expected = step.source.filterEquals
        val suffix = step.source.filterSuffix
        val contains = step.source.filterContains
        if (field == null || (expected == null && suffix == null && contains == null)) return targets
        val extraction =
            if (targets.firstOrNull() is JsonTarget) {
                FieldExtraction(path = if (field.startsWith("$")) field else "$." + field)
            } else {
                FieldExtraction(selector = field)
            }
        var result = targets
        if (expected != null) {
            result = result.filter { extractFromTarget(it, extraction, stepName, field) == expected }
        }
        if (suffix != null) {
            result =
                result.filter {
                    extractFromTarget(it, extraction, stepName, field)
                        .orEmpty()
                        .endsWith(suffix, ignoreCase = true)
                }
        }
        if (contains != null) {
            result =
                result.filter {
                    extractFromTarget(it, extraction, stepName, field)
                        .orEmpty()
                        .contains(contains, ignoreCase = true)
                }
        }
        return result
    }

    private fun extractFromTarget(
        target: Target,
        extraction: FieldExtraction,
        stepName: String,
        fieldName: String,
    ): String? {
        val raw =
            when (target) {
                is HtmlTarget -> {
                    if (extraction.selector == null) return null
                    val element = target.element.selectFirst(extraction.selector) ?: return null
                    readElement(element, extraction.attribute ?: "text")
                }
                is JsonTarget -> {
                    val path = extraction.path ?: extraction.rootPath ?: return null
                    val source = if (extraction.path != null) target.element else target.root
                    MiniJsonPath.asString(MiniJsonPath.resolve(source, path))
                }
            }?.trim() ?: return null
        var value = transform(raw, extraction, stepName, fieldName) ?: return null
        val second = extraction.value2
        if (second != null) {
            val secondValue = extractFromTarget(target, second.copy(template = null, value2 = null), stepName, fieldName) ?: return null
            value = value.replace("{value2}", secondValue)
        }
        return value
    }

    private fun extractFromPage(
        pageText: String?,
        pageUrl: String,
        extraction: FieldExtraction?,
        stepName: String,
        fieldName: String,
    ): String? {
        if (extraction == null) return null
        if (extraction.selector == null && extraction.path == null && extraction.template != null) {
            return transform("", extraction, stepName, fieldName)
        }
        if (pageText == null) return null
        val extracted: String? =
            if (extraction.path != null && pageText.trimStart().startsWith("{")) {
                val root =
                    runCatching { Json.parseToJsonElement(pageText).jsonObject }.getOrNull()
                        ?: throw EngineException(stepName, fieldName, "page content is not a JSON object")
                MiniJsonPath.asString(MiniJsonPath.resolve(root, extraction.path))
            } else if (extraction.selector != null) {
                val doc = Jsoup.parse(pageText, pageUrl)
                readElement(doc.selectFirst(extraction.selector) ?: return null, extraction.attribute ?: "text")
            } else {
                null
            }
        val raw = extracted?.trim() ?: return null
        return transform(raw, extraction, stepName, fieldName)
    }

    private fun readElement(
        element: Element,
        attribute: String,
    ): String =
        when (attribute) {
            "text" -> element.text()
            "html" -> element.html()
            else -> element.absUrl(attribute).ifEmpty { element.attr(attribute) }
        }

    private fun transform(
        raw: String,
        extraction: FieldExtraction,
        stepName: String,
        fieldName: String,
    ): String? {
        var value = raw
        if (extraction.regex != null) {
            value = runRegex(extraction.regex, value, extraction.regexGroup)
                ?: throw EngineException(stepName, fieldName, "regex '${extraction.regex}' did not match '$raw'")
        }
        if (extraction.template != null) value = extraction.template.replace("{value}", value)
        return value.trim().ifBlank { null }
    }

    private fun runRegex(
        pattern: String,
        text: String,
        group: Int = 1,
    ): String? =
        try {
            Regex(pattern).find(text)?.groups?.get(group)?.value
        } catch (e: Exception) {
            throw EngineException("video", "pattern", "invalid regex '$pattern': ${e.message}", e)
        }

    private fun fetchText(
        url: String,
        headers: Map<String, String>,
        timeoutMs: Long,
        stepName: String = "video",
    ): String {
        val response = fetchChecked(url, stepName, headers, timeoutMs)
        return response.bodyText()
    }

    private fun fetchChecked(
        url: String,
        stepName: String,
        extraHeaders: Map<String, String> = emptyMap(),
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    ): HttpResponse {
        if (hostFilter.isBlocked(url)) throw EngineException(stepName, "request", "request to blocked host: $url")
        val response =
            try {
                http.fetch(HttpRequest(url, "GET", extraHeaders, timeoutMs))
            } catch (e: IOException) {
                throw EngineException(stepName, "request", "network error for $url: ${e.message}", e)
            }
        if (response.code in 200..399) return response
        val hint =
            when {
                response.code == 403 ->
                    "the site refused the request (403); it may require browser headers or block non-browser clients"
                response.code == 404 -> "the URL resolved to a missing page (404); check the request template"
                response.code == 429 -> "the site is rate limiting (429); retry later"
                response.code in 500..599 -> "the site returned a server error"
                else -> "the site redirected to an error page"
            }
        throw EngineException(stepName, "request", "HTTP ${response.code} for $url: $hint")
    }

    private fun probeMimeType(
        url: String,
        headers: Map<String, String>,
        timeoutMs: Long,
    ): String? {
        val response =
            try {
                http.fetch(HttpRequest(url, "HEAD", headers, timeoutMs))
            } catch (e: IOException) {
                throw EngineException("video", "request", "media URL unreachable: ${e.message}", e)
            }
        return when (response.code) {
            in 200..399 -> MimeInference.normalize(response.contentType)
            403, 405, 501 -> null
            else -> throw EngineException("video", "request", "media probe returned HTTP ${response.code} for $url")
        }
    }

    private fun parseJson(
        text: String,
        stepName: String,
        url: String,
    ): JsonElement =
        try {
            Json.parseToJsonElement(text)
        } catch (e: Exception) {
            throw EngineException(stepName, "source", "malformed JSON from $url: ${e.message}", e)
        }

    private fun requireStep(
        step: ListStep?,
        name: String,
    ): ListStep =
        step ?: throw EngineException(
            name,
            null,
            "step is not defined in this extension (this step is optional - callers should check for it first)",
        )

    private fun requirePattern(step: VideoStep): String =
        step.pattern ?: throw EngineException("video", "pattern", "regex strategy requires a pattern")

    private fun Map<String, String?>.required(
        step: String,
        field: String,
    ): String =
        this[field]?.takeIf { it.isNotBlank() }
            ?: throw EngineException(step, field, "field mapping produced no value")

    private sealed interface Target

    private data class HtmlTarget(val element: Element) : Target

    private data class JsonTarget(val element: JsonElement, val root: JsonElement) : Target

    private data class ResolvedStream(
        val url: String,
        val contextText: String?,
        val referer: String?,
        val probedMime: String? = null,
        val probeDone: Boolean = false,
    )

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 15_000L
        const val SOURCE_HTML = "html"
        const val SOURCE_JSON = "json"
        const val STRATEGY_DIRECT = "direct"
        const val STRATEGY_REGEX = "regex"
        const val STRATEGY_IFRAME = "iframe"
        const val STRATEGY_WEBVIEW = "webviewSniff"
    }
}
