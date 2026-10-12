package io.github.tukiza7_debug.vibecast.core.model

import kotlinx.serialization.Serializable

/**
 * Declarative extension schema. Extensions carry no executable code; every step
 * is data that the engine in this module interprets.
 */
@Serializable
data class Extension(
    val schemaVersion: Int,
    val id: String,
    val name: String,
    val version: Int,
    val lang: String,
    val baseUrl: String,
    val headers: Map<String, String> = emptyMap(),
    val search: ListStep,
    val latest: ListStep? = null,
    val popular: ListStep? = null,
    val detail: ListStep,
    val episodes: ListStep,
    val video: VideoStep,
)

@Serializable
data class ListStep(
    val request: StepRequest,
    val source: SourceSpec,
    val fields: Map<String, FieldExtraction>,
)

@Serializable
data class StepRequest(
    val url: String,
    val method: String = "GET",
    val headers: Map<String, String> = emptyMap(),
)

@Serializable
data class SourceSpec(
    val type: String,
    val selector: String? = null,
    val path: String? = null,
    val filterField: String? = null,
    val filterEquals: String? = null,
    val filterSuffix: String? = null,
    val filterContains: String? = null,
)

@Serializable
data class FieldExtraction(
    val selector: String? = null,
    val attribute: String? = null,
    val path: String? = null,
    val rootPath: String? = null,
    val regex: String? = null,
    val regexGroup: Int = 1,
    val template: String? = null,
    val value2: FieldExtraction? = null,
)

@Serializable
data class VideoStep(
    val request: StepRequest,
    val strategy: String,
    val selector: String? = null,
    val pattern: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val timeoutMs: Long = 15_000,
    val quality: FieldExtraction? = null,
    val subtitles: List<SubtitleSpec> = emptyList(),
)

@Serializable
data class SubtitleSpec(
    val url: FieldExtraction,
    val language: String = "",
    val label: String = "",
)
