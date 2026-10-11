package io.github.tukiza7_debug.vibecast.adblock.rules

/** Resource types a network rule option can restrict a rule to. */
enum class ResourceType {
    SCRIPT,
    IMAGE,
    STYLESHEET,
    SUBDOCUMENT,
    XMLHTTPREQUEST,
    MEDIA,
    POPUP,
    FONT,
    OBJECT,
    OTHER,
}

/**
 * One parsed network filter rule. Matching never uses java.util.regex at request time:
 * patterns are compiled into literal/separator segment lists instead.
 */
class NetworkRule(
    val raw: String,
    val pattern: String,
    val isException: Boolean,
    val isHostAnchor: Boolean,
    val isStartAnchor: Boolean,
    val isEndAnchor: Boolean,
    val hostPart: String,
    val segments: List<Segment>,
    val types: Set<ResourceType>?,
    val thirdPartyOnly: Boolean,
    val firstPartyOnly: Boolean,
    val includedDomains: List<String>,
    val excludedDomains: List<String>,
) {

    sealed class Segment {
        data class Literal(val text: String) : Segment()
        object Separator : Segment()
        object Star : Segment()
    }

    /** True when the rule has type or domain restrictions that need per-request checks. */
    val hasContextChecks: Boolean
        get() = types != null || thirdPartyOnly || firstPartyOnly ||
            includedDomains.isNotEmpty() || excludedDomains.isNotEmpty()

    fun appliesToType(type: ResourceType?): Boolean {
        if (types == null) return true
        if (type == null) return false
        return type in types
    }

    fun appliesToDomains(pageHost: String?): Boolean {
        if (includedDomains.isEmpty() && excludedDomains.isEmpty()) return true
        if (pageHost == null) return includedDomains.isEmpty()
        if (excludedDomains.any { hostMatches(pageHost, it) }) return false
        if (includedDomains.isEmpty()) return true
        return includedDomains.any { hostMatches(pageHost, it) }
    }

    fun appliesToParty(requestHost: String?, pageHost: String?): Boolean {
        if (!thirdPartyOnly && !firstPartyOnly) return true
        if (requestHost == null || pageHost == null) return true
        val third = !hostMatches(requestHost, pageHost) && !hostMatches(pageHost, requestHost)
        return if (thirdPartyOnly) third else !third
    }

    companion object {
        fun hostMatches(host: String, domain: String): Boolean =
            host == domain || host.endsWith(".$domain")
    }
}
