package io.github.tukiza7_debug.vibecast.core.json

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Minimal JSONPath subset: "$", ".name", "[n]", "[*]". Enough for extension field
 * mappings without a full implementation; "$" always refers to the value the path is
 * resolved against (an item or a response object).
 */
object MiniJsonPath {

    val lenient = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = false
        isLenient = true
    }

    fun resolve(root: JsonElement, path: String): JsonElement? {
        val normalized = path.trim()
        if (normalized == "$") return root
        val rest = when {
            normalized.startsWith("$.") -> normalized.substring(2)
            normalized.startsWith("$[") -> normalized.substring(1)
            else -> return null
        }
        val segments = splitSegments(rest, normalized)
        var current: JsonElement = root
        for (segment in segments) {
            current = resolveSegment(current, segment) ?: return null
        }
        return current
    }

    fun asString(element: JsonElement?): String? = when (element) {
        null, is JsonNull -> null
        is JsonPrimitive -> element.content
        is JsonArray -> if (element.isEmpty()) null else element.firstNotNullOfOrNull { asString(it) }
        is JsonObject -> null
    }

    fun asArrayOrNull(element: JsonElement?): JsonArray? = element as? JsonArray

    private fun splitSegments(rest: String, fullPath: String): List<String> {
        val segments = mutableListOf<String>()
        val buffer = StringBuilder()
        var index = 0
        while (index < rest.length) {
            val c = rest[index]
            when {
                c == '.' && buffer.isNotEmpty() -> {
                    segments += buffer.toString()
                    buffer.clear()
                }
                c == '.' && buffer.isEmpty() -> {
                    // skip separators after bracket segments
                }
                c == '[' -> {
                    if (buffer.isNotEmpty()) {
                        segments += buffer.toString()
                        buffer.clear()
                    }
                    val close = rest.indexOf(']', index)
                    if (close <= index) throw IllegalArgumentException("Unclosed bracket in path: $fullPath")
                    segments += rest.substring(index, close + 1)
                    index = close
                }
                else -> buffer.append(c)
            }
            index++
        }
        if (buffer.isNotEmpty()) segments += buffer.toString()
        return segments
    }

    private fun resolveSegment(current: JsonElement, segment: String): JsonElement? {
        if (segment == "[*]") return current as? JsonArray
        if (segment.startsWith("[") && segment.endsWith("]")) {
            val index = segment.substring(1, segment.length - 1).toIntOrNull() ?: return null
            return (current as? JsonArray)?.getOrNull(index)
        }
        return (current as? JsonObject)?.get(segment)
    }
}
