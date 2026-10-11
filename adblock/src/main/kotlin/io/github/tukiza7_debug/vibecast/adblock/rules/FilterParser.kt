package io.github.tukiza7_debug.vibecast.adblock.rules

/**
 * Parses the EasyList/Adblock Plus subset the engine supports:
 * "||domain^" host anchors, "|" start/end anchors, "*" wildcards, "^" separators,
 * "$third-party, $script, $image, $media, $popup, $stylesheet, $subdocument,
 * $xmlhttprequest, $object, $font, $other, $first-party, $important", "domain=" lists,
 * "@@" exceptions, "##selector" and "domain##selector" element hiding, "#@#" exceptions.
 */
object FilterParser {
    data class ParsedRules(
        val network: List<NetworkRule>,
        val cosmeticGlobal: List<String>,
        val cosmeticByDomain: Map<String, List<String>>,
        val cosmeticExceptions: Set<String>,
    )

    private val supportedTypes =
        mapOf(
            "script" to ResourceType.SCRIPT,
            "image" to ResourceType.IMAGE,
            "stylesheet" to ResourceType.STYLESHEET,
            "subdocument" to ResourceType.SUBDOCUMENT,
            "xmlhttprequest" to ResourceType.XMLHTTPREQUEST,
            "media" to ResourceType.MEDIA,
            "popup" to ResourceType.POPUP,
            "font" to ResourceType.FONT,
            "object" to ResourceType.OBJECT,
            "other" to ResourceType.OTHER,
        )

    fun parse(text: String): ParsedRules {
        val network = ArrayList<NetworkRule>(4096)
        val cosmeticGlobal = ArrayList<String>()
        val cosmeticByDomain = HashMap<String, MutableList<String>>()
        val cosmeticExceptions = HashSet<String>()
        text.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            when {
                line.isEmpty() || line.startsWith("!") || line.startsWith("[") -> return@forEach
                line.contains("##") || line.contains("#@#") ->
                    parseCosmetic(
                        line,
                        cosmeticGlobal,
                        cosmeticByDomain,
                        cosmeticExceptions,
                    )
                else -> parseNetwork(line)?.let(network::add)
            }
        }
        return ParsedRules(network, cosmeticGlobal, cosmeticByDomain, cosmeticExceptions)
    }

    private fun parseNetwork(rawLine: String): NetworkRule? {
        val line = rawLine
        var isException = false
        var body = line.lowercase()
        if (body.startsWith("@@")) {
            isException = true
            body = body.substring(2)
        }
        var patternPart = body
        var optionsPart: String? = null
        val dollar = body.lastIndexOf('$')
        if (dollar >= 0 && dollar < body.length - 1) {
            val candidate = body.substring(dollar + 1)
            if (candidate.none { it == '/' || it == '?' }) {
                patternPart = body.substring(0, dollar)
                optionsPart = candidate
            }
        }
        if (optionsPart == "document" || optionsPart == "elemhide") return null
        var types: MutableSet<ResourceType>? = null
        var thirdPartyOnly = false
        var firstPartyOnly = false
        var important = false
        var includedDomains: List<String> = emptyList()
        var excludedDomains: List<String> = emptyList()
        optionsPart?.split(',')?.forEach { option ->
            val name = option.trim().lowercase()
            when {
                name == "third-party" -> thirdPartyOnly = true
                name == "~third-party" || name == "first-party" -> firstPartyOnly = true
                name == "important" -> important = true
                name.startsWith("domain=") -> {
                    val (inc, exc) = parseDomainList(name.removePrefix("domain="))
                    includedDomains = inc
                    excludedDomains = exc
                }
                name.startsWith("~") -> {
                    if (supportedTypes[name.removePrefix("~")] != null) {
                        // ~type exclusions are rare; ignore the whole option rather than
                        // mis-blocking. Keep the rule only if no other type options exist.
                        if (types.isNullOrEmpty() && !thirdPartyOnly && !firstPartyOnly &&
                            includedDomains.isEmpty() && excludedDomains.isEmpty() && name.removePrefix("~") == "popup"
                        ) {
                            return null
                        }
                    }
                }
                supportedTypes.containsKey(name) -> {
                    (types ?: HashSet<ResourceType>().also { types = it }).add(supportedTypes.getValue(name))
                }
            }
        }
        if (patternPart.isEmpty()) return null
        val ruleOptions =
            RuleOptions(
                types = types,
                thirdPartyOnly = thirdPartyOnly,
                firstPartyOnly = firstPartyOnly,
                includedDomains = includedDomains,
                excludedDomains = excludedDomains,
                important = important,
            )
        var isStartAnchor = false
        var isEndAnchor = false
        if (patternPart.startsWith("||")) {
            val host = hostPartOf(patternPart.substring(2))
            if (host == null || host.isEmpty()) return null
            return buildRule(
                raw = line,
                pattern = patternPart,
                isException = isException,
                isHostAnchor = true,
                isStartAnchor = false,
                isEndAnchor = patternPart.endsWith("|"),
                hostPart = host,
                rest = patternPart.substring(2 + host.length),
                options = ruleOptions,
            )
        }
        if (patternPart.startsWith("|")) {
            isStartAnchor = true
            patternPart = patternPart.substring(1)
        }
        if (patternPart.endsWith("|") && !patternPart.endsWith("\\|")) {
            isEndAnchor = true
            patternPart = patternPart.removeSuffix("|")
        }
        if (patternPart.isEmpty()) return null
        return buildRule(
            raw = line,
            pattern = patternPart,
            isException = isException,
            isHostAnchor = false,
            isStartAnchor = isStartAnchor,
            isEndAnchor = isEndAnchor,
            hostPart = "",
            rest = patternPart,
            options = ruleOptions,
        )
    }

    private fun buildRule(
        raw: String,
        pattern: String,
        isException: Boolean,
        isHostAnchor: Boolean,
        isStartAnchor: Boolean,
        isEndAnchor: Boolean,
        hostPart: String,
        rest: String,
        options: RuleOptions,
    ): NetworkRule? {
        val segments = compileSegments(rest) ?: return null
        return NetworkRule(
            raw = raw,
            pattern = pattern,
            isException = isException,
            isHostAnchor = isHostAnchor,
            isStartAnchor = isStartAnchor,
            isEndAnchor = isEndAnchor,
            hostPart = hostPart,
            segments = segments,
            options = options,
        )
    }

    fun compileSegments(pattern: String): List<NetworkRule.Segment>? {
        if (pattern.isEmpty()) return emptyList()
        val segments = ArrayList<NetworkRule.Segment>()
        val buffer = StringBuilder()
        for (element in pattern) {
            when (element) {
                '*' -> {
                    if (buffer.isNotEmpty()) {
                        segments.add(NetworkRule.Segment.Literal(buffer.toString()))
                        buffer.setLength(0)
                    }
                    segments.add(NetworkRule.Segment.Star)
                }
                '^' -> {
                    if (buffer.isNotEmpty()) {
                        segments.add(NetworkRule.Segment.Literal(buffer.toString()))
                        buffer.setLength(0)
                    }
                    segments.add(NetworkRule.Segment.Separator)
                }
                else -> buffer.append(element)
            }
        }
        if (buffer.isNotEmpty()) segments.add(NetworkRule.Segment.Literal(buffer.toString()))
        return segments
    }

    private fun hostPartOf(pattern: String): String {
        val end = pattern.indexOfFirst { it == '/' || it == '*' || it == '^' || it == '|' }
        return if (end < 0) pattern else pattern.substring(0, end)
    }

    private fun parseDomainList(value: String): Pair<List<String>, List<String>> {
        val included = ArrayList<String>()
        val excluded = ArrayList<String>()
        value.split('|').forEach { domain ->
            val d = domain.trim().lowercase()
            if (d.isEmpty()) return@forEach
            if (d.startsWith("~")) excluded.add(d.substring(1)) else included.add(d)
        }
        return included to excluded
    }

    private fun parseCosmetic(
        line: String,
        global: MutableList<String>,
        byDomain: MutableMap<String, MutableList<String>>,
        exceptions: MutableSet<String>,
    ) {
        val exceptionMarker = "#@#"
        val normalMarker = "##"
        val isException = line.contains(exceptionMarker)
        val marker = if (isException) exceptionMarker else normalMarker
        val markerIndex = line.indexOf(marker)
        if (markerIndex < 0) return
        val domainPart = line.substring(0, markerIndex)
        val selector = line.substring(markerIndex + marker.length)
        if (selector.isEmpty()) return
        if (UNSUPPORTED_PSEUDO.any { selector.contains(it) }) return
        if (isException) {
            if (domainPart.isEmpty()) exceptions.add(selector)
            return
        }
        if (domainPart.isEmpty()) {
            global.add(selector)
            return
        }
        domainPart.split(',').forEach { domain ->
            val d = domain.trim().lowercase()
            if (d.isNotEmpty() && !d.startsWith("~")) {
                byDomain.getOrPut(d) { ArrayList() }.add(selector)
            }
        }
    }

    private val UNSUPPORTED_PSEUDO =
        listOf(
            ":has(",
            ":has-text(",
            ":matches(",
            ":matches-attr(",
            ":nth-ancestor(",
            ":upward(",
            ":remove(",
        )
}
