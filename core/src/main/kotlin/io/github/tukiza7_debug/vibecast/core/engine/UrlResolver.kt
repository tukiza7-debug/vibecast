package io.github.tukiza7_debug.vibecast.core.engine

import java.net.URI
import java.net.URLEncoder

/** Resolves step request URL templates ({query}, {page}, {url}) and relative URLs. */
object UrlResolver {
    fun resolveTemplate(
        template: String,
        baseUrl: String,
        vars: Map<String, String>,
    ): String {
        var url = template
        vars.forEach { (name, value) ->
            val token = "{$name}"
            if (url.contains(token)) {
                val encoded = if (name == "query") URLEncoder.encode(value, Charsets.UTF_8) else value
                url = url.replace(token, encoded)
            }
        }
        return absolutize(url, baseUrl)
    }

    /**
     * Resolves [url] against [baseUrl].
     *
     * Handles absolute URLs, protocol-relative (`//host/path`), scheme-relative with
     * any protocol the site uses, root-relative (`/path`), query-only (`?page=2`),
     * dot-segment paths (`../a/./b`) and bare relative paths. Unresolvable input is
     * returned unchanged rather than throwing.
     */
    fun absolutize(
        url: String,
        baseUrl: String,
    ): String {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return baseUrl
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
        if (trimmed.startsWith("//")) {
            val scheme = baseUrl.substringBefore("://", missingDelimiterValue = "https")
            return "$scheme:$trimmed"
        }
        // java.net.URI drops the base path for query-only references; RFC 3986 keeps it.
        if (trimmed.startsWith("?")) {
            val baseWithoutQuery = baseUrl.substringBefore('?')
            return baseWithoutQuery + trimmed
        }
        return runCatching {
            val base = URI(baseUrl)
            val resolved = base.resolve(trimmed)
            normalize(resolved).toString()
        }.getOrElse {
            runCatching { URI(baseUrl).resolve(trimmed).toString() }.getOrDefault(trimmed)
        }
    }

    /** Removes dot segments (RFC 3986 section 5.2.4) from the path of [uri]. */
    private fun normalize(uri: URI): URI =
        try {
            URI(
                uri.scheme,
                uri.authority,
                removeDotSegments(uri.rawPath ?: ""),
                uri.rawQuery,
                uri.fragment,
            )
        } catch (_: Exception) {
            uri
        }

    private fun removeDotSegments(path: String): String {
        if (!path.contains("/.")) return path
        val output = mutableListOf<String>()
        for (segment in path.split('/')) {
            when (segment) {
                "." -> {}
                ".." ->
                    if (output.size > 1) {
                        output.removeAt(output.lastIndex)
                    }
                else -> output.add(segment)
            }
        }
        var joined = output.joinToString("/")
        if (path.endsWith("/.") || path.endsWith("/..")) joined += "/"
        return joined.ifEmpty { "/" }
    }

    fun hostOf(url: String): String? = runCatching { URI(url).host?.lowercase() }.getOrNull()
}
