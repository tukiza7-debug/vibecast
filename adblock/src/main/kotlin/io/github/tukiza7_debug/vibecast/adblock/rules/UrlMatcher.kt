package io.github.tukiza7_debug.vibecast.adblock.rules

import java.net.URI

data class BlockRequest(
    val url: String,
    val pageHost: String?,
    val type: ResourceType?,
    val isUserGesture: Boolean = false,
)

sealed class Decision {
    object Allow : Decision()

    data class Block(val rule: NetworkRule) : Decision()

    data class AllowException(val rule: NetworkRule) : Decision()
}

/**
 * Indexed matcher for parsed network rules. Lookups walk host-suffix buckets for
 * "||domain" rules and token buckets for generic rules, so no request scans the
 * full rule list and no java.util.regex runs per request.
 */
class UrlMatcher(rules: List<NetworkRule>) {
    private val hostAnchored = HashMap<String, MutableList<NetworkRule>>()
    private val hostAnchoredExceptions = HashMap<String, MutableList<NetworkRule>>()
    private val generic = ArrayList<NetworkRule>()
    private val genericExceptions = ArrayList<NetworkRule>()
    private val genericByToken = HashMap<String, MutableList<NetworkRule>>()
    private val genericFallback = ArrayList<NetworkRule>()
    private val genericExceptionFallback = ArrayList<NetworkRule>()

    init {
        rules.forEach { rule -> index(rule) }
    }

    private fun index(rule: NetworkRule) {
        if (rule.isHostAnchor) {
            val bucket = if (rule.isException) hostAnchoredExceptions else hostAnchored
            bucket.getOrPut(rule.hostPart) { ArrayList(4) }.add(rule)
            return
        }
        if (rule.isException) {
            genericExceptions.add(rule)
        } else {
            generic.add(rule)
        }
        // Token bucket build happens eagerly; the caller parses lists off the main
        // thread anyway and this keeps decide() allocation free.
        val token = longestToken(rule.pattern)
        if (token == null) {
            if (rule.isException) genericExceptionFallback.add(rule) else genericFallback.add(rule)
        } else {
            genericByToken.getOrPut(token) { ArrayList(8) }.add(rule)
        }
    }

    fun decide(request: BlockRequest): Decision {
        val host = hostOf(request.url) ?: return Decision.Allow
        val url = request.url.lowercase()
        val schemeEnd = url.indexOf("://") + 3
        val hostStart = if (schemeEnd >= 3) url.indexOf(host, schemeEnd).coerceAtLeast(schemeEnd) else 0

        var blocked: NetworkRule? = findBlocking(url, host, hostStart, request)
        if (blocked != null) {
            // $important outranks whitelisting exceptions (ABP semantics).
            if (blocked.isImportant) return Decision.Block(blocked)
            val exception = findException(url, host, hostStart, request)
            if (exception != null) return Decision.AllowException(exception)
            return Decision.Block(blocked)
        }
        return Decision.Allow
    }

    private fun findBlocking(
        url: String,
        host: String,
        hostStart: Int,
        request: BlockRequest,
    ): NetworkRule? {
        var domain: String? = host
        while (domain != null) {
            hostAnchored[domain]?.forEach { rule ->
                if (ruleAppliesAndMatches(rule, url, host, hostStart, request)) return rule
            }
            domain = nextSuffix(domain)
        }
        val tokens = urlTokens(url)
        val candidates = ArrayList<NetworkRule>(32)
        tokens.forEach { token -> genericByToken[token]?.let(candidates::addAll) }
        candidates.addAll(genericFallback)
        candidates.forEach { rule ->
            if (ruleAppliesAndMatches(rule, url, host, -1, request)) return rule
        }
        return null
    }

    private fun findException(
        url: String,
        host: String,
        hostStart: Int,
        request: BlockRequest,
    ): NetworkRule? {
        var domain: String? = host
        while (domain != null) {
            hostAnchoredExceptions[domain]?.forEach { rule ->
                if (ruleAppliesAndMatches(rule, url, host, hostStart, request)) return rule
            }
            domain = nextSuffix(domain)
        }
        val tokens = urlTokens(url)
        val candidates = ArrayList<NetworkRule>(16)
        tokens.forEach { token ->
            genericByToken[token]?.forEach { if (it.isException) candidates.add(it) }
        }
        candidates.addAll(genericExceptionFallback)
        candidates.forEach { rule ->
            if (ruleAppliesAndMatches(rule, url, host, -1, request)) return rule
        }
        return null
    }

    private fun ruleAppliesAndMatches(
        rule: NetworkRule,
        url: String,
        requestHost: String,
        hostStart: Int,
        request: BlockRequest,
    ): Boolean {
        if (rule.types != null && !rule.appliesToType(request.type)) return false
        if (!rule.appliesToDomains(request.pageHost)) return false
        if (!rule.appliesToParty(requestHost, request.pageHost)) return false
        return if (rule.isHostAnchor) {
            matchGlob(rule.segments, url, hostStart + requestHost.length, strictStart = true, endAnchored = rule.isEndAnchor)
        } else {
            matchGlob(rule.segments, url, 0, strictStart = rule.isStartAnchor, endAnchored = rule.isEndAnchor)
        }
    }

    private fun matchGlob(
        segments: List<NetworkRule.Segment>,
        url: String,
        start: Int,
        strictStart: Boolean,
        endAnchored: Boolean,
    ): Boolean {
        var position = start
        var jumped = !strictStart
        segments.forEach { segment ->
            when (segment) {
                is NetworkRule.Segment.Literal -> {
                    if (jumped) {
                        val found = url.indexOf(segment.text, position)
                        if (found < 0) return false
                        position = found + segment.text.length
                    } else {
                        if (!url.startsWith(segment.text, position)) return false
                        position += segment.text.length
                    }
                    jumped = false
                }
                NetworkRule.Segment.Separator -> {
                    if (position < url.length) {
                        val c = url[position]
                        if (c.isLetterOrDigit() || c == '_' || c == '-' || c == '.' || c == '%') return false
                        position++
                    }
                    // A separator at the end of the URL counts as a match (ABP semantics).
                    jumped = false
                }
                NetworkRule.Segment.Star -> jumped = true
            }
        }
        return !endAnchored || position == url.length
    }

    companion object {
        fun hostOf(url: String): String? =
            runCatching {
                URI(url).host?.lowercase()
            }.getOrNull()

        fun nextSuffix(host: String): String? {
            val dot = host.indexOf('.')
            return if (dot < 0 || dot == host.length - 1) null else host.substring(dot + 1)
        }

        fun urlTokens(url: String): Set<String> {
            val tokens = HashSet<String>(16)
            var start = -1
            for (i in url.indices) {
                val c = url[i]
                if (c.isLetterOrDigit()) {
                    if (start < 0) start = i
                } else {
                    if (start >= 0) addToken(tokens, url.substring(start, i))
                    start = -1
                }
            }
            if (start >= 0) addToken(tokens, url.substring(start))
            return tokens
        }

        private fun addToken(
            tokens: MutableSet<String>,
            token: String,
        ) {
            if (token.length >= 4) tokens.add(token)
        }

        fun longestToken(pattern: String): String? {
            var best: String? = null
            var start = -1
            for (i in pattern.indices) {
                val c = pattern[i]
                if (c.isLetterOrDigit()) {
                    if (start < 0) start = i
                } else {
                    if (start >= 0) {
                        val token = pattern.substring(start, i)
                        if (token.length >= 4 && (best == null || token.length > best.length)) best = token
                    }
                    start = -1
                }
            }
            if (start >= 0) {
                val token = pattern.substring(start)
                if (token.length >= 4 && (best == null || token.length > best.length)) best = token
            }
            return best
        }
    }
}
