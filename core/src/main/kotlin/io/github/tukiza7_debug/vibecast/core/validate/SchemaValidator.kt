package io.github.tukiza7_debug.vibecast.core.validate

import io.github.tukiza7_debug.vibecast.core.codes.ExtensionValidationException
import io.github.tukiza7_debug.vibecast.core.model.Extension
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * Validates extension JSON before anything is stored. Issues always name the step and
 * the field so the person who built the extension can fix the exact spot.
 */
object SchemaValidator {
    const val SUPPORTED_SCHEMA_VERSION = 1
    const val MAX_JSON_BYTES = 512 * 1024

    private val idRegex = Regex("^[a-z0-9]+([.-][a-z0-9]+)+$")
    private val langRegex = Regex("^[a-z]{2}(-[A-Z]{2})?$")
    private val strategies = setOf("direct", "iframe", "regex", "webviewSniff")
    private val listSteps = listOf("search", "latest", "popular")
    private val requiredListSteps = listOf("search")
    private val requiredVideoFields =
        mapOf(
            "direct" to emptyList(),
            "iframe" to listOf("selector"),
            "regex" to listOf("pattern"),
            "webviewSniff" to emptyList(),
        )

    private val lenient =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            encodeDefaults = false
            isLenient = true
        }

    fun decodeAndValidate(text: String): Extension {
        val issues = validate(text)
        if (issues.isNotEmpty()) throw ExtensionValidationException(issues)
        return try {
            lenient.decodeFromString(Extension.serializer(), text)
        } catch (e: kotlinx.serialization.SerializationException) {
            throw ExtensionValidationException(listOf("structure: ${e.message}"), e)
        } catch (e: IllegalArgumentException) {
            throw ExtensionValidationException(listOf("structure: ${e.message}"), e)
        }
    }

    fun validate(text: String): List<String> {
        val issues = mutableListOf<String>()
        if (text.toByteArray(Charsets.UTF_8).size > MAX_JSON_BYTES) {
            issues.add("document: larger than ${MAX_JSON_BYTES / 1024} KB limit")
        }
        val root =
            try {
                Json.parseToJsonElement(text).jsonObject
            } catch (e: Exception) {
                return listOf("document: not a valid JSON object (${e.message})")
            }
        checkRoot(root, issues)
        val steps = mutableSetOf<String>()
        root.forEach { (key, value) ->
            when {
                key in listOf("search", "latest", "popular", "detail", "episodes") -> {
                    val obj =
                        value as? JsonObject ?: run {
                            issues.add("$key: must be an object")
                            return@forEach
                        }
                    checkListStep(key, obj, issues)
                    steps.add(key)
                }
                key == "video" -> {
                    val obj =
                        value as? JsonObject ?: run {
                            issues.add("video: must be an object")
                            return@forEach
                        }
                    checkVideoStep(obj, issues)
                    steps.add(key)
                }
            }
        }
        requiredListSteps.forEach { if (it !in steps) issues.add("$it: required step is missing") }
        listOf("detail", "episodes", "video").forEach { if (it !in steps) issues.add("$it: required step is missing") }
        return issues
    }

    private fun checkRoot(
        root: JsonObject,
        issues: MutableList<String>,
    ) {
        when (val schemaVersion = root["schemaVersion"]) {
            is JsonPrimitive ->
                if (schemaVersion.content != SUPPORTED_SCHEMA_VERSION.toString()) {
                    issues.add("schemaVersion: unsupported value ${schemaVersion.content}, expected $SUPPORTED_SCHEMA_VERSION")
                }
            else -> issues.add("schemaVersion: missing or not a number")
        }
        checkString(root, "id", issues) { value ->
            if (!idRegex.matches(value)) "id: '$value' must be lowercase dotted (example: net.example.source)" else null
        }
        checkString(root, "name", issues) { value ->
            if (value.length > 100) "name: longer than 100 characters" else null
        }
        when (val version = root["version"]) {
            is JsonPrimitive ->
                if (version.content.toIntOrNull()?.let { it > 0 } != true) {
                    issues.add("version: must be a positive integer")
                }
            else -> issues.add("version: missing or not a number")
        }
        checkString(root, "lang", issues) { value ->
            if (!langRegex.matches(value)) "lang: '$value' must be an ISO code like 'en' or 'pt-BR'" else null
        }
        checkString(root, "baseUrl", issues) { value ->
            when {
                !value.startsWith("http://") && !value.startsWith("https://") ->
                    "baseUrl: must start with http:// or https://"
                hostInvalid(value) -> "baseUrl: no host"
                else -> null
            }
        }
        val headers = root["headers"]
        if (headers != null && headers !is JsonObject) issues.add("headers: must be an object of name to value")
    }

    private fun hostInvalid(url: String): Boolean =
        runCatching {
            java.net.URI(url).host.isNullOrBlank()
        }.getOrDefault(true)

    private fun checkString(
        root: JsonObject,
        key: String,
        issues: MutableList<String>,
        extra: (String) -> String?,
    ) {
        when (val value = root[key]) {
            is JsonPrimitive -> {
                if (!value.isString) {
                    issues.add("$key: must be a string")
                } else if (value.content.isBlank()) {
                    issues.add("$key: must not be blank")
                } else {
                    extra(value.content)?.let { issues.add(it) }
                }
            }
            else -> issues.add("$key: missing or not a string")
        }
    }

    private fun checkListStep(
        stepName: String,
        step: JsonObject,
        issues: MutableList<String>,
    ) {
        val request =
            step["request"] as? JsonObject ?: run {
                issues.add("$stepName.request: missing")
                return
            }
        checkRequestUrl(stepName, request, issues)
        val source =
            step["source"] as? JsonObject ?: run {
                issues.add("$stepName.source: missing")
                return
            }
        val type = (source["type"] as? JsonPrimitive)?.content
        when (type) {
            "html" -> {
                val selector = (source["selector"] as? JsonPrimitive)?.content
                if (selector.isNullOrBlank() && (stepName in listSteps || stepName == "episodes")) {
                    issues.add("$stepName.source: selector is required for an html source")
                }
            }
            "json" ->
                if ((source["path"] as? JsonPrimitive)?.content.isNullOrBlank() &&
                    (stepName in listSteps || stepName == "episodes")
                ) {
                    issues.add("$stepName.source: path is required for a json source")
                }
            else -> issues.add("$stepName.source: type must be html or json, got '$type'")
        }
        val fields =
            step["fields"] as? JsonObject ?: run {
                issues.add("$stepName.fields: missing")
                return
            }
        fields.forEach { (fieldName, value) ->
            val extraction =
                value as? JsonObject ?: run {
                    issues.add("$stepName.fields.$fieldName: must be an object")
                    return@forEach
                }
            checkExtraction(stepName, fieldName, extraction, type ?: "", issues)
        }
        val required =
            when (stepName) {
                "search", "latest", "popular" -> listOf("title", "url")
                "episodes" -> listOf("url")
                else -> emptyList()
            }
        required.forEach { field ->
            if (fields[field] == null) issues.add("$stepName.fields: required field '$field' is missing")
        }
        val filterField = (source["filterField"] as? JsonPrimitive)?.content
        if (filterField != null) {
            val hasCriterion =
                listOf("filterEquals", "filterSuffix", "filterContains").any {
                    !(source[it] as? JsonPrimitive)?.content.isNullOrBlank()
                }
            if (!hasCriterion) {
                issues.add("$stepName.source: filterField needs filterEquals, filterSuffix or filterContains")
            }
        } else {
            listOf("filterEquals", "filterSuffix", "filterContains").forEach { key ->
                if (!(source[key] as? JsonPrimitive)?.content.isNullOrBlank()) {
                    issues.add("$stepName.source: $key needs filterField")
                }
            }
        }
    }

    private fun checkExtraction(
        stepName: String,
        fieldName: String,
        extraction: JsonObject,
        sourceType: String,
        issues: MutableList<String>,
    ) {
        val selector = (extraction["selector"] as? JsonPrimitive)?.content
        val path = (extraction["path"] as? JsonPrimitive)?.content
        val rootPath = (extraction["rootPath"] as? JsonPrimitive)?.content
        val template = (extraction["template"] as? JsonPrimitive)?.content
        if (selector == null && path == null && rootPath == null && template == null) {
            issues.add("$stepName.fields.$fieldName: needs selector, path, rootPath or template")
            return
        }
        if (sourceType == "html" && selector == null && template == null) {
            issues.add("$stepName.fields.$fieldName: html source fields use selector")
        }
        if (sourceType == "json" && path == null && rootPath == null && template == null) {
            issues.add("$stepName.fields.$fieldName: json source fields use path or rootPath")
        }
        (extraction["regex"] as? JsonPrimitive)?.content?.let { pattern ->
            try {
                Regex(pattern)
            } catch (e: Exception) {
                issues.add("$stepName.fields.$fieldName: invalid regex '$pattern' (${e.message})")
            }
        }
        val group = (extraction["regexGroup"] as? JsonPrimitive)?.content?.toIntOrNull()
        if (group != null && group < 0) issues.add("$stepName.fields.$fieldName: regexGroup must be >= 0")
    }

    private fun checkVideoStep(
        step: JsonObject,
        issues: MutableList<String>,
    ) {
        val request =
            step["request"] as? JsonObject ?: run {
                issues.add("video.request: missing")
                return
            }
        checkRequestUrl("video", request, issues)
        val strategy = (step["strategy"] as? JsonPrimitive)?.content
        if (strategy == null) {
            issues.add("video.strategy: missing, expected one of ${strategies.joinToString()}")
            return
        }
        if (strategy !in strategies) {
            issues.add("video.strategy: unknown strategy '$strategy', expected one of ${strategies.joinToString()}")
            return
        }
        requiredVideoFields[strategy]?.forEach { field ->
            val value = (step[field] as? JsonPrimitive)?.content
            if (value.isNullOrBlank()) issues.add("video.$field: required for the '$strategy' strategy")
        }
        if (strategy == "regex") {
            val pattern = (step["pattern"] as? JsonPrimitive)?.content
            try {
                Regex(pattern ?: "")
            } catch (e: Exception) {
                issues.add("video.pattern: invalid regex '$pattern' (${e.message})")
            }
        }
        val timeout = (step["timeoutMs"] as? JsonPrimitive)?.content?.toLongOrNull()
        if (timeout != null && timeout <= 0) issues.add("video.timeoutMs: must be positive")
        val headers = step["headers"]
        if (headers != null && headers !is JsonObject) issues.add("video.headers: must be an object")
        val subtitles = step["subtitles"]
        if (subtitles != null) {
            if (subtitles !is JsonArray) {
                issues.add("video.subtitles: must be an array")
            } else {
                subtitles.forEachIndexed { index, element ->
                    val spec = element as? JsonObject
                    val url = spec?.get("url") as? JsonObject
                    if (url == null) {
                        issues.add("video.subtitles[$index]: needs a url extraction object")
                    }
                }
            }
        }
    }

    private fun checkRequestUrl(
        stepName: String,
        request: JsonObject,
        issues: MutableList<String>,
    ) {
        val url = (request["url"] as? JsonPrimitive)?.content
        if (url.isNullOrBlank()) issues.add("$stepName.request.url: missing")
        val method = (request["method"] as? JsonPrimitive)?.content
        if (method != null && method.uppercase() !in listOf("GET", "POST", "HEAD")) {
            issues.add("$stepName.request.method: unsupported method '$method'")
        }
    }
}
