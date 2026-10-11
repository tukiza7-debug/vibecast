package io.github.tukiza7_debug.vibecast.adblock

import io.github.tukiza7_debug.vibecast.adblock.rules.BlockRequest
import io.github.tukiza7_debug.vibecast.adblock.rules.Decision
import io.github.tukiza7_debug.vibecast.adblock.rules.FilterParser
import io.github.tukiza7_debug.vibecast.adblock.rules.NetworkRule
import io.github.tukiza7_debug.vibecast.adblock.rules.ResourceType
import io.github.tukiza7_debug.vibecast.adblock.rules.UrlMatcher
import javax.inject.Inject
import java.util.concurrent.atomic.AtomicLong

/**
 * Thread-safe facade over the parsed filter lists. The engine swaps the immutable
 * matcher atomically after background updates; request handling never blocks.
 */
class FilterEngine @Inject constructor() {

    data class Lists(
        val easyList: String?,
        val easyPrivacy: String?,
        val customRules: String,
        val easyListEnabled: Boolean = true,
        val easyPrivacyEnabled: Boolean = true,
    )

    data class SitePolicy(
        val disabledHosts: Set<String> = emptySet(),
        val bypassHosts: Set<String> = emptySet(),
    )

    data class Stats(
        val networkRules: Int,
        val cosmeticGlobal: Int,
        val cosmeticDomains: Int,
    )

    private data class State(
        val matcher: UrlMatcher,
        val cosmeticGlobal: List<String>,
        val cosmeticByDomain: Map<String, List<String>>,
        val cosmeticExceptions: Set<String>,
        val stats: Stats,
    )

    private val emptyState = State(
        matcher = UrlMatcher(emptyList()),
        cosmeticGlobal = emptyList(),
        cosmeticByDomain = emptyMap(),
        cosmeticExceptions = emptySet(),
        stats = Stats(0, 0, 0),
    )

    @Volatile
    private var state: State = emptyState

    val blockedRequests = AtomicLong()

    fun replace(lists: Lists, policy: SitePolicy) {
        val parsed = mutableListOf<NetworkRule>()
        val cosmeticGlobal = mutableListOf<String>()
        val cosmeticByDomain = mutableMapOf<String, MutableList<String>>()
        val cosmeticExceptions = mutableSetOf<String>()
        var rules = 0
        fun absorb(text: String) {
            val p = FilterParser.parse(text)
            parsed += p.network
            rules += p.network.size
            cosmeticGlobal += p.cosmeticGlobal
            p.cosmeticByDomain.forEach { (domain, selectors) ->
                cosmeticByDomain.getOrPut(domain) { mutableListOf() }.addAll(selectors)
            }
            cosmeticExceptions += p.cosmeticExceptions
        }
        if (lists.easyListEnabled && lists.easyList != null) absorb(lists.easyList)
        if (lists.easyPrivacyEnabled && lists.easyPrivacy != null) absorb(lists.easyPrivacy)
        if (lists.customRules.isNotBlank()) absorb(lists.customRules)
        state = State(
            matcher = UrlMatcher(parsed),
            cosmeticGlobal = cosmeticGlobal.toList(),
            cosmeticByDomain = cosmeticByDomain,
            cosmeticExceptions = cosmeticExceptions.toSet(),
            stats = Stats(rules, cosmeticGlobal.size, cosmeticByDomain.size),
        )
    }

    fun stats(): Stats = state.stats

    fun shouldBlockPage(pageHost: String?, policy: SitePolicy): Boolean {
        if (pageHost == null) return false
        return pageHost !in policy.disabledHosts
    }

    fun decide(request: BlockRequest, policy: SitePolicy): Decision {
        val host = UrlMatcher.hostOf(request.url) ?: return Decision.Allow
        if (host in policy.bypassHosts) return Decision.Allow
        if (request.pageHost != null && request.pageHost in policy.disabledHosts) return Decision.Allow
        val decision = state.matcher.decide(request)
        if (decision is Decision.Block) blockedRequests.incrementAndGet()
        return decision
    }

    /** CSS injected at page start; global selectors plus those scoped to this domain. */
    fun cosmeticCss(pageUrl: String, policy: SitePolicy): String {
        val pageHost = UrlMatcher.hostOf(pageUrl) ?: return ""
        if (pageHost in policy.disabledHosts) return ""
        val scoped = mutableListOf<String>()
        var domain: String? = pageHost
        while (domain != null) {
            state.cosmeticByDomain[domain]?.let(scoped::addAll)
            domain = UrlMatcher.nextSuffix(domain)
        }
        val exceptions = state.cosmeticExceptions
        val selectors = (state.cosmeticGlobal + scoped).filterNot { it in exceptions }
        if (selectors.isEmpty()) return ""
        return selectors.joinToString(",") { it } + "{display:none!important;visibility:hidden!important}"
    }

    fun exceptionSelectors(): Set<String> = state.cosmeticExceptions

    companion object {
        fun resourceTypeName(type: String?): ResourceType? = when (type) {
            "script" -> ResourceType.SCRIPT
            "image" -> ResourceType.IMAGE
            "stylesheet" -> ResourceType.STYLESHEET
            "subdocument", "iframe" -> ResourceType.SUBDOCUMENT
            "xhr", "fetch", "xmlhttprequest" -> ResourceType.XMLHTTPREQUEST
            "media", "video", "audio" -> ResourceType.MEDIA
            "font" -> ResourceType.FONT
            "object" -> ResourceType.OBJECT
            "popup" -> ResourceType.POPUP
            else -> ResourceType.OTHER
        }
    }
}
