package io.github.tukiza7_debug.vibecast.addonx.analyzer

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive

/**
 * Decodes the payload of WebView.evaluateJavascript calls. The result is a
 * JSON-encoded string literal ("null" when nothing was returned), so it must be
 * parsed as JSON - chained string replaces corrupt escapes in the wrong order
 * and silently break the DOM analysis.
 */
object EvaluatedHtml {
    fun decode(raw: String?): String? {
        if (raw.isNullOrEmpty() || raw == "null") return null
        return runCatching {
            val primitive = Json.parseToJsonElement(raw) as? JsonPrimitive
            if (primitive == null || !primitive.isString) {
                throw IllegalArgumentException("evaluateJavascript returned a non-string value")
            }
            primitive.content
        }.getOrNull()
    }
}
