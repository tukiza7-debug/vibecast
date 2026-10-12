package io.github.tukiza7_debug.vibecast.addonx.analyzer

import io.github.tukiza7_debug.vibecast.core.engine.ExtensionEngine
import io.github.tukiza7_debug.vibecast.core.engine.UrlResolver

/**
 * One-Link to Extension analyzer. Takes a single URL plus the page HTML (and optional
 * WebView recordings) and proposes a complete, testable extension draft with a
 * confidence level for every step. The analyzer never invents selectors: every
 * proposal is grounded in structures actually present in the page, and anything it
 * cannot derive honestly is flagged with an issue and a LOW confidence instead.
 */
object AutoAnalyzer {
    enum class PageType { LISTING, DETAIL, MIXED, UNKNOWN }

    data class RecordedJsonEndpoint(val url: String, val itemCount: Int, val sampleKeys: List<String>)

    data class Recording(
        val jsonEndpoints: List<RecordedJsonEndpoint> = emptyList(),
        val mediaUrls: List<String> = emptyList(),
    )

    data class StepProposal(
        val step: String,
        val confidence: DomAnalyzer.Confidence,
        val summary: String,
        val issues: List<String> = emptyList(),
    )

    /** Outcome of one live engine check (T3): run against the same engine VibeCast uses. */
    data class StepCheck(
        val step: String,
        val passed: Boolean,
        val detail: String,
    )

    data class Verification(
        val checks: List<StepCheck>,
        /** Non-empty when the live video step produced a playable stream. */
        val videoUrl: String? = null,
    ) {
        val videoPassed: Boolean get() = !videoUrl.isNullOrBlank()
    }

    data class AutoResult(
        val url: String,
        val baseUrl: String,
        val host: String,
        val suggestedName: String,
        val pageType: PageType,
        val steps: List<StepProposal>,
        val draft: ExtensionBuilder.Draft,
        val mediaCandidates: List<String>,
        val jsonEndpoint: RecordedJsonEndpoint?,
        val needsPicker: Boolean,
        val issues: List<String> = emptyList(),
        /** Set after the live engine run; null when verification could not run at all. */
        val verification: Verification? = null,
    ) {
        val overallConfidence: DomAnalyzer.Confidence
            get() =
                when {
                    verification != null ->
                        when {
                            verification.checks.all { it.passed } && verification.videoPassed -> DomAnalyzer.Confidence.HIGH
                            verification.checks.none { it.passed } -> DomAnalyzer.Confidence.LOW
                            !verification.videoPassed -> DomAnalyzer.Confidence.LOW
                            else -> DomAnalyzer.Confidence.MEDIUM
                        }
                    steps.any { it.confidence == DomAnalyzer.Confidence.LOW } -> DomAnalyzer.Confidence.LOW
                    steps.any { it.confidence == DomAnalyzer.Confidence.MEDIUM } -> DomAnalyzer.Confidence.MEDIUM
                    else -> DomAnalyzer.Confidence.HIGH
                }
    }

    /** Query parameter names commonly carrying the search term, most specific first. */
    private val searchParamNames = listOf("q", "s", "search", "query", "keyword", "keywords", "k", "searchterm")

    private val mediaPattern = Regex("""https?://[^\s"'<>\\]+\.(?:m3u8|mp4|mpd)(?:\?[^\s"'<>\\]*)?""")
    private val fileDirectivePattern = Regex("""["']?file["']?\s*:\s*["']([^"']+\.(?:m3u8|mp4|mpd))["']""")
    private val sourceTagPattern = Regex("""<source[^>]+src=["']([^"']+\.(?:m3u8|mp4|mpd))["']""", RegexOption.IGNORE_CASE)
    private val videoTagPattern = Regex("""<video[^>]+src=["']([^"']+\.(?:m3u8|mp4|mpd))["']""", RegexOption.IGNORE_CASE)

    /** Query parameter names carrying pagination, most specific first. */
    private val pageParamNames = listOf("page", "paged", "pagina", "pg", "p", "offset", "start", "from")

    /**
     * Analyzes one page. Runs fully off the request thread: the only network access is
     * fetching [url] (and, when present, the recorded JSON endpoint) before parsing.
     */
    fun analyze(
        url: String,
        html: String,
        recording: Recording,
        engine: ExtensionEngine,
    ): AutoResult {
        val baseUrl = UrlResolver.absolutize("/", url).removeSuffix("/")
        val host = UrlResolver.hostOf(url) ?: "unknown.host"
        val analysis = DomAnalyzer.analyze(html, url)
        val detailFields = DomAnalyzer.analyzeDetail(html, url)
        val issues = mutableListOf<String>()

        val pageType =
            when {
                analysis.proposals.isNotEmpty() && detailFields.isNotEmpty() -> PageType.MIXED
                analysis.proposals.isNotEmpty() -> PageType.LISTING
                detailFields.isNotEmpty() -> PageType.DETAIL
                else -> PageType.UNKNOWN
            }

        // 1. Choose the item pattern for list steps.
        val best = analysis.proposals.firstOrNull()
        val jsonEndpoint = recording.jsonEndpoints.maxByOrNull { it.itemCount }

        // 2. Derive the search template from the URL (query + page params), or from
        // detected pagination links, or fall back to the listing itself.
        val searchTemplate = withPageSupport(detectSearchTemplate(url), url, analysis.pagination)
        val searchConfidence =
            when {
                searchTemplate?.contains("{query}") == true -> DomAnalyzer.Confidence.HIGH
                jsonEndpoint != null -> DomAnalyzer.Confidence.MEDIUM
                else -> DomAnalyzer.Confidence.LOW
            }
        if (searchTemplate?.contains("{query}") != true) {
            issues.add(
                "no search query parameter was found in the URL; the search step lists the page itself " +
                    "until you set the query template manually",
            )
        }

        // 3. Source and fields for the search step.
        val searchStepDraft: ExtensionBuilder.StepDraft
        var searchStep: StepProposal
        if (jsonEndpoint != null && recording.jsonEndpoints.isNotEmpty() && analysis.proposals.isEmpty()) {
            // JSON API page: map the endpoint array keys to the schema fields honestly.
            val keys = jsonEndpoint.sampleKeys.map { it.lowercase() }
            val titleKey = keys.firstOrNull { it in setOf("title", "name", "label") }
            val urlKey = keys.firstOrNull { it in setOf("url", "href", "link", "slug", "identifier") }
            val coverKey = keys.firstOrNull { it in setOf("cover", "image", "thumbnail", "poster", "art") }
            searchStepDraft =
                ExtensionBuilder.StepDraft(
                    name = "search",
                    requestUrl = searchTemplate ?: jsonEndpoint.url,
                    sourceType = "json",
                    itemSelector = null,
                    jsonPath = null,
                    fields =
                        linkedMapOf(
                            "title" to ExtensionBuilder.FieldDraft(path = titleKey?.let { "$.$it" }),
                            "url" to ExtensionBuilder.FieldDraft(path = urlKey?.let { "$.$it" }),
                            "cover" to ExtensionBuilder.FieldDraft(path = coverKey?.let { "$.$it" }),
                        ),
                )
            if (titleKey == null || urlKey == null) {
                issues.add(
                    "JSON endpoint keys ${jsonEndpoint.sampleKeys.joinToString()} lack a clear title/url pair; map them in the step editor",
                )
            }
            searchStep =
                StepProposal(
                    step = "search",
                    confidence = if (titleKey != null && urlKey != null) DomAnalyzer.Confidence.MEDIUM else DomAnalyzer.Confidence.LOW,
                    summary = "JSON endpoint with ${jsonEndpoint.itemCount} items (keys: ${jsonEndpoint.sampleKeys.joinToString()})",
                )
        } else if (best != null) {
            searchStepDraft =
                ExtensionBuilder.StepDraft(
                    name = "search",
                    requestUrl = searchTemplate ?: url,
                    sourceType = "html",
                    itemSelector = best.itemSelector,
                    jsonPath = null,
                    fields =
                        linkedMapOf(
                            "title" to ExtensionBuilder.FieldDraft(selector = best.titleSelector ?: "a"),
                            "url" to ExtensionBuilder.FieldDraft(selector = best.linkSelector ?: "a[href]", attribute = "href"),
                            "cover" to ExtensionBuilder.FieldDraft(selector = best.coverSelector, attribute = best.coverAttribute ?: "src"),
                        ),
                )
            searchStep =
                StepProposal(
                    step = "search",
                    confidence = if (searchTemplate?.contains("{query}") == true) best.confidence else DomAnalyzer.Confidence.LOW,
                    summary = "${best.itemCount} repeated cards matched (${best.itemSelector})",
                )
        } else {
            searchStepDraft =
                ExtensionBuilder.StepDraft(
                    name = "search",
                    requestUrl = searchTemplate ?: url,
                    sourceType = "html",
                    itemSelector = null,
                    jsonPath = null,
                    fields =
                        linkedMapOf(
                            "title" to ExtensionBuilder.FieldDraft(selector = null),
                            "url" to ExtensionBuilder.FieldDraft(selector = "a[href]", attribute = "href"),
                        ),
                )
            searchStep =
                StepProposal(
                    step = "search",
                    confidence = DomAnalyzer.Confidence.LOW,
                    summary = "no repeated card pattern was detected; use the visual picker to mark one card",
                    issues = listOf("the page looks like a single-page app or has no list structure in its server HTML"),
                )
        }

        // 4. Detail step from detected fields, honest about what is missing.
        val detailStepDraft =
            ExtensionBuilder.StepDraft(
                name = "detail",
                requestUrl = "{url}",
                sourceType = "html",
                itemSelector = null,
                jsonPath = null,
                fields =
                    linkedMapOf(
                        "title" to ExtensionBuilder.FieldDraft(selector = detailFields["title"]?.selector ?: "h1"),
                        "synopsis" to
                            ExtensionBuilder.FieldDraft(
                                selector = detailFields["synopsis"]?.selector,
                                attribute = detailFields["synopsis"]?.attribute,
                            ),
                        "cover" to
                            ExtensionBuilder.FieldDraft(
                                selector = detailFields["cover"]?.selector,
                                attribute = detailFields["cover"]?.attribute ?: "src",
                            ),
                    ),
            )
        val detailStep =
            StepProposal(
                step = "detail",
                confidence =
                    when {
                        detailFields["title"] != null && detailFields["synopsis"] != null -> DomAnalyzer.Confidence.HIGH
                        detailFields["title"] != null -> DomAnalyzer.Confidence.MEDIUM
                        else -> DomAnalyzer.Confidence.LOW
                    },
                summary =
                    buildString {
                        append("detected: ")
                        append(
                            listOfNotNull(
                                detailFields["title"]?.let { "title" },
                                detailFields["synopsis"]?.let { "synopsis" },
                                detailFields["cover"]?.let { "cover" },
                            ).ifEmpty { listOf("none") }.joinToString(),
                        )
                    },
                issues =
                    if (detailFields.isEmpty()) {
                        listOf(
                            "no detail fields detected; assign them with the visual picker",
                        )
                    } else {
                        emptyList()
                    },
            )

        // 5. Episodes step: reuse the listing pattern on detail pages when it exists.
        val episodesStepDraft =
            ExtensionBuilder.StepDraft(
                name = "episodes",
                requestUrl = "{url}",
                sourceType = "html",
                itemSelector = best?.itemSelector,
                jsonPath = null,
                fields =
                    linkedMapOf(
                        "name" to ExtensionBuilder.FieldDraft(selector = best?.titleSelector ?: "a"),
                        "url" to ExtensionBuilder.FieldDraft(selector = best?.linkSelector ?: "a[href]", attribute = "href"),
                    ),
            )
        val episodesStep =
            StepProposal(
                step = "episodes",
                confidence = best?.confidence ?: DomAnalyzer.Confidence.LOW,
                summary =
                    if (best != null) {
                        "reuses the listing pattern (${best.itemSelector}) on the detail page"
                    } else {
                        "no episode list pattern detected; mark one episode entry with the visual picker"
                    },
                issues = if (best == null) listOf("episodes need a selector; the visual picker guides this") else emptyList(),
            )

        // 6. Video strategy from what the page actually exposes.
        val mediaInPage = collectMediaCandidates(html)
        val allMedia = (recording.mediaUrls + mediaInPage).distinct()
        val (videoDraft, videoStep) = proposeVideoStep(mediaInPage, html, allMedia)

        val draft =
            ExtensionBuilder.Draft(
                id = ExtensionBuilder.idForHost(url),
                name = defaultName(host),
                version = 1,
                lang = "en",
                baseUrl = baseUrl,
                headers = ExtensionBuilder.browserHeaders(baseUrl),
                search = searchStepDraft,
                latest = null,
                detail = detailStepDraft,
                episodes = episodesStepDraft,
                video = videoDraft,
            )

        // 7. Structural verification without network: keep the draft honest.
        runCatching { engine.dryRun(convert(draft)) }
            .onSuccess { structuralIssues -> issues.addAll(structuralIssues) }
            .onFailure { e ->
                issues.add("the proposed structure is not valid yet: ${e.message?.take(240)}")
            }

        val needsPicker = pageType == PageType.UNKNOWN || best == null
        return AutoResult(
            url = url,
            baseUrl = baseUrl,
            host = host,
            suggestedName = defaultName(host),
            pageType = pageType,
            steps = listOf(searchStep, detailStep, episodesStep, videoStep),
            draft = draft,
            mediaCandidates = allMedia.take(12),
            jsonEndpoint = jsonEndpoint,
            needsPicker = needsPicker,
            issues = issues.distinct(),
        )
    }

    private fun proposeVideoStep(
        mediaInPage: List<String>,
        html: String,
        allMedia: List<String>,
    ): Pair<ExtensionBuilder.VideoDraft, StepProposal> =
        when {
            html.contains(fileDirectivePattern) ->
                ExtensionBuilder.VideoDraft(
                    requestUrl = "{url}",
                    strategy = "regex",
                    pattern = """["']?file["']?\s*:\s*["']([^"']+\.(?:m3u8|mp4|mpd))["']""",
                ) to
                    StepProposal(
                        step = "video",
                        confidence = DomAnalyzer.Confidence.HIGH,
                        summary = "player configuration with a file directive found; regex strategy",
                    )
            html.contains(sourceTagPattern) ->
                ExtensionBuilder.VideoDraft(
                    requestUrl = "{url}",
                    strategy = "regex",
                    pattern = """<source[^>]+src=["']([^"']+\.(?:m3u8|mp4|mpd))["']""",
                ) to
                    StepProposal(
                        step = "video",
                        confidence = DomAnalyzer.Confidence.HIGH,
                        summary = "<source> tag with a direct media file found; regex strategy",
                    )
            html.contains(videoTagPattern) ->
                ExtensionBuilder.VideoDraft(
                    requestUrl = "{url}",
                    strategy = "regex",
                    pattern = """<video[^>]+src=["']([^"']+\.(?:m3u8|mp4|mpd))["']""",
                ) to
                    StepProposal(
                        step = "video",
                        confidence = DomAnalyzer.Confidence.MEDIUM,
                        summary = "<video> tag with a direct media file found; regex strategy",
                    )
            mediaInPage.isNotEmpty() ->
                ExtensionBuilder.VideoDraft(
                    requestUrl = "{url}",
                    strategy = "regex",
                    pattern = """https?://[^\s"'<>\\]+\.(?:m3u8|mp4|mpd)""",
                ) to
                    StepProposal(
                        step = "video",
                        confidence = DomAnalyzer.Confidence.MEDIUM,
                        summary = "media links present in the page (${mediaInPage.size} found); generic media regex",
                    )
            allMedia.isNotEmpty() ->
                ExtensionBuilder.VideoDraft(
                    requestUrl = "{url}",
                    strategy = "webviewSniff",
                ) to
                    StepProposal(
                        step = "video",
                        confidence = DomAnalyzer.Confidence.MEDIUM,
                        summary =
                            "media was captured while browsing (${allMedia.size} urls); webviewSniff replays " +
                                "it at runtime inside VibeCast",
                        issues = listOf("not ready yet: the video step still needs a live pass in VibeCast"),
                    )
            else ->
                ExtensionBuilder.VideoDraft(
                    requestUrl = "{url}",
                    strategy = "webviewSniff",
                ) to
                    StepProposal(
                        step = "video",
                        confidence = DomAnalyzer.Confidence.LOW,
                        summary = "no media detected in the server HTML; webviewSniff needs a real device WebView",
                        issues =
                            listOf(
                                "not ready yet: no media evidence was captured for this site",
                                "if the site loads video from embeds, open an episode page, record the network and analyze again",
                            ),
                    )
        }

    private fun collectMediaCandidates(html: String): List<String> = mediaPattern.findAll(html).map { it.value }.distinct().toList()

    /**
     * Turns the page URL into a {query}/{page} template when a query-looking or
     * pagination-looking parameter exists. Returns the URL unchanged when neither
     * is present.
     */
    fun detectSearchTemplate(url: String): String? {
        if (!url.contains("?")) return null
        val (base, queryPart) = url.split("?", limit = 2)
        val params = queryPart.split("&")
        var queryParamName: String? = null
        var pageParamFound = false
        val rebuilt =
            params.map { param ->
                val name = param.substringBefore("=")
                val value = param.substringAfter("=", "")
                val looksTextual = value.isNotEmpty() && !value.chars().allMatch { Character.isDigit(it) }
                val looksNumeric = value.isNotEmpty() && value.chars().allMatch { Character.isDigit(it) }
                when {
                    name.lowercase() in searchParamNames && looksTextual -> {
                        queryParamName = name
                        "$name={query}"
                    }
                    name.lowercase() in pageParamNames && looksNumeric -> {
                        pageParamFound = true
                        "$name={page}"
                    }
                    else -> param
                }
            }
        val rebuiltUrl = "$base?${rebuilt.joinToString("&")}"
        return when {
            queryParamName != null -> rebuiltUrl
            pageParamFound -> rebuiltUrl
            else -> null
        }
    }

    /**
     * Adds {page} support to the search template from live pagination evidence:
     * either a page query parameter seen in pagination links, or a /page/N path.
     */
    private fun withPageSupport(
        template: String?,
        listingUrl: String,
        pagination: List<DomAnalyzer.PaginationProposal>,
    ): String? {
        if (template?.contains("{page}") == true) return template
        val pageParam =
            pagination.firstNotNullOfOrNull { proposal ->
                pageParamRegex.find(proposal.sampleUrl)?.groupValues?.get(1)
            }
        if (pageParam != null) {
            val base = template ?: listingUrl
            return when {
                Regex("[?&]$pageParam=").containsMatchIn(base) ->
                    Regex("([?&])$pageParam=[^&]*").replace(base) { match -> "${match.groupValues[1]}$pageParam={page}" }
                base.contains("?") -> "$base&$pageParam={page}"
                else -> "$base?$pageParam={page}"
            }
        }
        val pathPagination =
            pagination.firstOrNull { proposal -> pathPageRegex.containsMatchIn(proposal.sampleUrl) }
        if (pathPagination != null) {
            return pathPageRegex.replace(pathPagination.sampleUrl.substringBefore("?"), "/page/{page}")
        }
        return template
    }

    private val pageParamRegex = Regex("[?&](page|paged|pagina|pg|p|offset|start|from)=(\\d+)")
    private val pathPageRegex = Regex("/page/\\d+")

    fun defaultName(host: String): String {
        val labels = host.removePrefix("www.").split(".")
        // The last label is a TLD, not a brand word.
        val brandLabels = if (labels.size > 1) labels.dropLast(1) else labels
        return brandLabels
            .joinToString(" ") { part -> part.replaceFirstChar { it.uppercase() } }
            .ifBlank { host }
    }

    /** Converts the UI draft into the core model so the engine can dry-run it. */
    fun convert(draft: ExtensionBuilder.Draft): io.github.tukiza7_debug.vibecast.core.model.Extension {
        val json = ExtensionBuilder.build(draft)
        return io.github.tukiza7_debug.vibecast.core.validate.SchemaValidator.decodeAndValidate(json)
    }
}
