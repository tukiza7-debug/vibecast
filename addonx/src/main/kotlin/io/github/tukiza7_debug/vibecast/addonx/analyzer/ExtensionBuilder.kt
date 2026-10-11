package io.github.tukiza7_debug.vibecast.addonx.analyzer

import io.github.tukiza7_debug.vibecast.core.engine.UrlResolver
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Builds the extension JSON from the picked selectors, keeping the mapping grounded
 * in what the user actually assigned rather than invented defaults.
 */
object ExtensionBuilder {
    data class StepDraft(
        val name: String,
        val requestUrl: String,
        val sourceType: String,
        val itemSelector: String?,
        val jsonPath: String?,
        val fields: LinkedHashMap<String, FieldDraft>,
    )

    data class FieldDraft(
        val selector: String? = null,
        val attribute: String? = null,
        val path: String? = null,
        val template: String? = null,
        val regex: String? = null,
    )

    data class VideoDraft(
        val requestUrl: String,
        val strategy: String,
        val selector: String? = null,
        val pattern: String? = null,
        val headers: Map<String, String> = emptyMap(),
        val timeoutMs: Long = 15_000,
    )

    data class Draft(
        val id: String,
        val name: String,
        val version: Int,
        val lang: String,
        val baseUrl: String,
        val headers: Map<String, String>,
        val search: StepDraft,
        val latest: StepDraft?,
        val detail: StepDraft,
        val episodes: StepDraft,
        val video: VideoDraft,
    )

    private val json =
        Json {
            prettyPrint = true
            encodeDefaults = false
            explicitNulls = false
        }

    fun build(draft: Draft): String {
        val root =
            linkedMapOf<String, Any?>(
                "schemaVersion" to 1,
                "id" to draft.id,
                "name" to draft.name,
                "version" to draft.version,
                "lang" to draft.lang,
                "baseUrl" to draft.baseUrl,
            )
        if (draft.headers.isNotEmpty()) root["headers"] = draft.headers
        root["search"] = stepJson(draft.search)
        draft.latest?.let { root["latest"] = stepJson(it) }
        root["detail"] = stepJson(draft.detail)
        root["episodes"] = stepJson(draft.episodes)
        root["video"] = videoJson(draft.video)
        return json.encodeToString(
            kotlinx.serialization.json.JsonObject.serializer(),
            kotlinx.serialization.json.JsonObject(root.mapValues { (_, v) -> anyToElement(v) }),
        )
    }

    private fun stepJson(step: StepDraft): Map<String, Any?> =
        linkedMapOf(
            "request" to linkedMapOf<String, Any?>("url" to step.requestUrl),
            "source" to
                buildMap<String, Any?> {
                    put("type", step.sourceType)
                    step.itemSelector?.let { put("selector", it) }
                    step.jsonPath?.let { put("path", it) }
                },
            // Fields without any extraction directive would serialize as invalid empty
            // objects; omit them instead of inventing values.
            "fields" to
                step.fields
                    .filterValues { field ->
                        field.selector != null || field.path != null || field.template != null || field.regex != null
                    }
                    .mapValues { (_, field) -> fieldJson(field) },
        )

    private fun fieldJson(field: FieldDraft): Map<String, Any?> =
        buildMap {
            field.selector?.let { put("selector", it) }
            field.attribute?.takeIf { it != "text" }?.let { put("attribute", it) }
            field.path?.let { put("path", it) }
            field.template?.let { put("template", it) }
            field.regex?.let { put("regex", it) }
        }

    private fun videoJson(video: VideoDraft): Map<String, Any?> =
        buildMap {
            put("request", linkedMapOf<String, Any?>("url" to video.requestUrl))
            put("strategy", video.strategy)
            video.selector?.let { put("selector", it) }
            video.pattern?.let { put("pattern", it) }
            if (video.headers.isNotEmpty()) put("headers", video.headers)
            put("timeoutMs", video.timeoutMs)
        }

    private fun anyToElement(value: Any?): kotlinx.serialization.json.JsonElement =
        when (value) {
            null -> kotlinx.serialization.json.JsonNull
            is kotlinx.serialization.json.JsonElement -> value
            is String -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            is Boolean -> JsonPrimitive(value)
            is Map<*, *> -> JsonObject(value.entries.associate { (k, v) -> k.toString() to anyToElement(v) })
            is List<*> -> JsonArray(value.map { anyToElement(it) })
            else -> JsonPrimitive(value.toString())
        }

    /** Derives an extension id from the site host: com.example.videos. */
    fun idForHost(baseUrl: String): String {
        val host = UrlResolver.hostOf(baseUrl) ?: return "local.extension"
        val parts = host.removePrefix("www.").split(".").filter { it.isNotBlank() }
        return parts.reversed().joinToString(".") { part ->
            part.lowercase().replace(Regex("[^a-z0-9-]"), "").ifBlank { "x" }
        }.ifBlank { "local.extension" }
    }
}
