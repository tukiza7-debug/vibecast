package io.github.tukiza7_debug.vibecast.addonx.analyzer

import io.github.tukiza7_debug.vibecast.core.engine.UrlResolver
import io.github.tukiza7_debug.vibecast.core.http.HttpClient
import io.github.tukiza7_debug.vibecast.core.http.HttpRequest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Network evidence for the One-Link flow, mirroring what the manual analyzer's
 * WebView records: candidate JSON data endpoints referenced by the page are
 * probed once and, when they really return an array, become a RecordedJsonEndpoint.
 * The analyzer stays honest - only responses that parse to a non-empty array are
 * reported, never guessed.
 */
object JsonEndpointProbe {
    private val scriptSrcPattern =
        Regex("""(?:src|href)\s*=\s*["']([^"']+\.json(?:\?[^"']*)?)["']""", RegexOption.IGNORE_CASE)
    private val fetchCallPattern =
        Regex("""(?:fetch|axios(?:\.(?:get|post))?)\(\s*["']([^"']{4,200})["']""", RegexOption.IGNORE_CASE)
    private val apiPathPattern = Regex("""["']((?:/[A-Za-z0-9_\-./]*)?/api/[A-Za-z0-9_\-./]+(?:\.json)?)["']""")

    private const val MAX_CANDIDATES = 6
    private const val MAX_PROBES = 3
    private const val MAX_BODY_BYTES = 512 * 1024

    /** Same desktop-ish mobile UA the one-link fetch uses, so endpoints see one client. */
    private const val PROBE_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

    /** Absolute URLs from the page that look like JSON data endpoints, deduped. */
    fun candidates(
        html: String,
        baseUrl: String,
    ): List<String> {
        val found =
            buildList {
                addAll(scriptSrcPattern.findAll(html).map { it.groupValues[1] })
                addAll(fetchCallPattern.findAll(html).map { it.groupValues[1] })
                addAll(apiPathPattern.findAll(html).map { it.groupValues[1] })
            }
        return found
            .asSequence()
            .filter { it.isNotBlank() && !it.startsWith("data:") && !it.startsWith("javascript:") }
            .map { UrlResolver.absolutize(it, baseUrl) }
            .filter { it.startsWith("http") }
            .distinct()
            .take(MAX_CANDIDATES)
            .toList()
    }

    /**
     * Fetches [url] and reports it as an endpoint when the body parses to a
     * non-empty JSON array. Returns null for anything else (HTML, errors, empties).
     */
    suspend fun probe(
        httpClient: HttpClient,
        url: String,
    ): AutoAnalyzer.RecordedJsonEndpoint? {
        val response =
            try {
                httpClient.fetch(
                    HttpRequest(
                        url,
                        headers =
                            mapOf(
                                "Accept" to "application/json",
                                "User-Agent" to PROBE_USER_AGENT,
                            ),
                    ),
                )
            } catch (_: Exception) {
                return null
            }
        if (response.code !in 200..299) return null
        val body = response.bodyText().take(MAX_BODY_BYTES)
        if (!body.trimStart().startsWith("[")) return null
        return try {
            val root = Json.parseToJsonElement(body)
            val array = firstArray(root) ?: return null
            val first = array.firstOrNull() as? JsonObject ?: return null
            AutoAnalyzer.RecordedJsonEndpoint(
                url = url,
                itemCount = array.size,
                sampleKeys = first.keys.toList().take(12),
            )
        } catch (_: Exception) {
            null
        }
    }

    /** Probes up to [MAX_PROBES] candidates and keeps the endpoints that respond. */
    suspend fun probeAll(
        httpClient: HttpClient,
        html: String,
        baseUrl: String,
    ): List<AutoAnalyzer.RecordedJsonEndpoint> =
        candidates(html, baseUrl)
            .take(MAX_PROBES)
            .mapNotNull { probe(httpClient, it) }

    private fun firstArray(
        element: kotlinx.serialization.json.JsonElement,
        depth: Int = 0,
    ): JsonArray? {
        if (depth > 6) return null
        return when (element) {
            is JsonArray -> element
            is JsonObject -> element.values.firstNotNullOfOrNull { firstArray(it, depth + 1) }
            is JsonPrimitive -> null
            else -> null
        }
    }
}
