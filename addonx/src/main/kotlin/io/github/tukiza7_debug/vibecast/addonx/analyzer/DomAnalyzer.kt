package io.github.tukiza7_debug.vibecast.addonx.analyzer

import io.github.tukiza7_debug.vibecast.core.engine.UrlResolver
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * Proposes selectors for repeated item patterns in a listing page, pagination links
 * and detail fields. Elements hidden by ad rules never reach this analyzer because
 * the WebView records only allowed requests and the page itself was filtered.
 */
object DomAnalyzer {
    enum class Confidence { HIGH, MEDIUM, LOW }

    data class Proposal(
        val itemSelector: String,
        val titleSelector: String?,
        val linkSelector: String?,
        val coverSelector: String?,
        val itemCount: Int,
        val confidence: Confidence,
    )

    data class PaginationProposal(val linkSelector: String, val sampleUrl: String, val confidence: Confidence)

    data class Analysis(
        val proposals: List<Proposal>,
        val pagination: List<PaginationProposal>,
    )

    fun analyze(
        html: String,
        baseUrl: String,
    ): Analysis {
        val doc = Jsoup.parse(html, baseUrl)
        val proposals = mutableListOf<Proposal>()
        // Navigation, headers, footers and asides are never item containers; skipping
        // them removes the most common false positives on listing pages.
        val containers =
            doc.body().select("*").filter { element ->
                val tag = element.tagName().lowercase()
                val classes = element.classNames().joinToString(" ").lowercase()
                val role = element.attr("role").lowercase()
                tag !in setOf("nav", "header", "footer", "aside") &&
                    role !in setOf("navigation", "banner", "contentinfo") &&
                    !classes.contains("nav") && !classes.contains("footer") && !classes.contains("menu") &&
                    !classes.contains("sidebar") && !classes.contains("breadcrumb")
            }
        containers.forEach { container ->
            val children = container.children()
            if (children.size < 3) return@forEach
            val groups = children.groupBy { CssSelectorBuilder.elementSignature(it) }
            groups.forEach { (signature, group) ->
                if (group.size < 3) return@forEach
                val first = group.first()
                if (first.tagName() in setOf("script", "style", "br", "img", "input", "option")) return@forEach
                val titleSelector = pickTitleSelector(first)
                val linkSelector = pickLinkSelector(first) ?: return@forEach
                val coverSelector = pickCoverSelector(first)
                val itemSelector = relativeItemSelector(container, signature)
                val confidence =
                    when {
                        group.size >= 8 && titleSelector != null && coverSelector != null -> Confidence.HIGH
                        group.size >= 5 && titleSelector != null -> Confidence.MEDIUM
                        else -> Confidence.LOW
                    }
                proposals +=
                    Proposal(
                        itemSelector = itemSelector,
                        titleSelector = titleSelector,
                        linkSelector = linkSelector,
                        coverSelector = coverSelector,
                        itemCount = group.size,
                        confidence = confidence,
                    )
            }
        }
        // Prefer larger, more complete groups first; drop near-duplicates.
        val best =
            proposals
                .sortedWith(
                    compareByDescending<Proposal> { it.confidence }
                        .thenByDescending { it.itemCount }
                        .thenByDescending { (it.titleSelector != null).compareTo(false) },
                )
                .distinctBy { it.itemSelector }
                .take(4)

        val pagination =
            doc.select("a[href]").mapNotNull { link ->
                val href = link.absUrl("href")
                if (href.isEmpty()) return@mapNotNull null
                val text = link.text().lowercase()
                val relNext = "next" == link.attr("rel").lowercase()
                val pageParam = Regex("[?&](page|p|pagina|pg)=(\\d+)").containsMatchIn(href)
                val labelMatch = text in setOf("next", "›", "»", "older") || Regex("next\\s*page").containsMatchIn(text)
                when {
                    relNext -> PaginationProposal(relativeSelector(doc, link), href, Confidence.HIGH)
                    pageParam && labelMatch -> PaginationProposal(relativeSelector(doc, link), href, Confidence.HIGH)
                    pageParam -> PaginationProposal(relativeSelector(doc, link), href, Confidence.MEDIUM)
                    else -> null
                }
            }.distinctBy { it.linkSelector }.take(3)

        return Analysis(best, pagination)
    }

    /** Suggests detail fields from a single page. */
    fun analyzeDetail(
        html: String,
        baseUrl: String,
    ): Map<String, String> {
        val doc = Jsoup.parse(html, baseUrl)
        val fields = mutableMapOf<String, String>()
        doc.selectFirst("h1")?.let { fields["title"] = relativeSelector(doc, it) }
        doc.selectFirst("meta[name=description]")?.let { fields["synopsis"] = "meta[name=description]@content" }
        doc.select("p").firstOrNull { it.text().length > 120 }?.let { fields["synopsis"] = relativeSelector(doc, it) }
        doc.select("img[src]").firstOrNull { img ->
            val src = img.absUrl("src")
            img.hasClass("poster") || img.hasClass("thumb") || img.hasClass("cover") ||
                src.contains("poster", ignoreCase = true) || src.contains("cover", ignoreCase = true)
        }?.let { fields["cover"] = relativeSelector(doc, it) }
        return fields
    }

    private fun relativeItemSelector(
        container: Element,
        signature: String,
    ): String {
        val containerSelector = containerSelectorOf(container)
        return if (containerSelector.isEmpty()) signature else "$containerSelector > $signature"
    }

    private fun containerSelectorOf(container: Element): String {
        if (container.id().isNotEmpty()) return "#${container.id()}"
        val classes = container.classNames()
        if (classes.isNotEmpty()) return container.tagName() + "." + classes.sorted().take(2).joinToString(".")
        return ""
    }

    private fun relativeSelector(
        doc: Element,
        target: Element,
    ): String {
        val chain = mutableListOf<String>()
        var node: Element? = target
        while (node != null && node != doc) {
            val classes = node.classNames()
            val segment =
                when {
                    node.id().isNotEmpty() -> "#${node.id()}"
                    classes.isNotEmpty() -> node.tagName() + "." + classes.sorted().take(2).joinToString(".")
                    else -> node.tagName()
                }
            chain.add(segment)
            if (node.id().isNotEmpty()) break
            node = node.parent()
        }
        return chain.reversed().joinToString(" > ")
    }

    private fun pickTitleSelector(item: Element): String? {
        item.selectFirst("h1,h2,h3,h4")?.let { return item.selectFirst("h1,h2,h3,h4")!!.tagName() }
        item.selectFirst("[class*=title i]")?.let { return classSelector(it) }
        // Many cards put the title in the link text itself.
        val link = item.selectFirst("a[href]")
        if (link != null && link.text().length in 4..120) return "a[href]"
        return null
    }

    private fun pickLinkSelector(item: Element): String? {
        val links = item.select("a[href]")
        // Prefer links that point at detail pages over pure anchors or external jumps.
        val detailLink =
            links.firstOrNull { link ->
                val href = link.attr("href")
                href.isNotEmpty() && !href.startsWith("#") && !href.startsWith("javascript") &&
                    !href.startsWith("mailto") && !href.startsWith("tel")
            }
        return if (detailLink != null) "a[href]" else null
    }

    private fun pickCoverSelector(item: Element): String? {
        val img = item.selectFirst("img[src]") ?: return null
        return "img"
    }

    private fun classSelector(element: Element): String {
        val classes = element.classNames()
        return if (classes.isEmpty()) element.tagName() else element.tagName() + "." + classes.first()
    }

    /** Turns a recorded listing URL into a {query}/{page} template for the search step. */
    fun buildSearchTemplate(
        recordedUrl: String,
        query: String,
    ): String? {
        if (!recordedUrl.contains("?")) return null
        val (base, queryPart) = recordedUrl.split("?", limit = 2)
        val params = queryPart.split("&")
        var queryParamName: String? = null
        val rebuilt =
            params.map { param ->
                val name = param.substringBefore("=")
                val value = param.substringAfter("=", "")
                val decoded = runCatching { java.net.URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)
                if (decoded.equals(query, ignoreCase = true)) {
                    queryParamName = name
                    "$name={query}"
                } else {
                    param
                }
            }
        return queryParamName?.let { "$base?${rebuilt.joinToString("&")}" }
    }

    fun absolutize(
        url: String,
        baseUrl: String,
    ): String = UrlResolver.absolutize(url, baseUrl)

    /**
     * True when the page HTML is almost certainly a client-rendered shell: little to
     * no text, no list structure, and heavy script weight. Used to explain why no
     * proposals were found instead of leaving the user guessing.
     */
    fun looksLikeSinglePageApp(html: String): Boolean {
        val doc = Jsoup.parse(html)
        val textLength = doc.body()?.text()?.length ?: 0
        val anchors = doc.body()?.select("a[href]")?.size ?: 0
        val scripts = doc.select("script[src]").size
        val rootApp = doc.body()?.select("#root, #app, #__next")?.isNotEmpty() == true
        return (textLength < 400 && anchors < 6) || (rootApp && anchors < 6 && scripts >= 2)
    }
}
