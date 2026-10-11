package io.github.tukiza7_debug.vibecast.core.model

data class SearchItem(
    val title: String,
    val url: String,
    val cover: String? = null,
)

data class DetailResult(
    val title: String? = null,
    val cover: String? = null,
    val synopsis: String? = null,
)

data class Episode(
    val name: String,
    val number: Int? = null,
    val url: String,
)

data class SubtitleTrack(
    val url: String,
    val language: String,
    val label: String,
)

data class StreamRef(
    val url: String,
    val mimeType: String?,
    val headers: Map<String, String>,
    val quality: String? = null,
    val subtitles: List<SubtitleTrack> = emptyList(),
)
