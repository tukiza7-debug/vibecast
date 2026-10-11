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

/**
 * Result of an optional list step (`latest`, `popular`).
 *
 * Optional steps must never break a screen: when the extension does not define
 * the step the engine reports [OptionalList.NotPresent] instead of throwing, so
 * callers can simply hide the section. A step that exists but fails at runtime
 * still throws [io.github.tukiza7_debug.vibecast.core.engine.EngineException].
 */
sealed interface OptionalList {
    /** The step is defined in the extension and produced [items] (possibly empty). */
    data class Present(val items: List<SearchItem>) : OptionalList

    /** The step is not defined in this extension; callers should hide the section. */
    data object NotPresent : OptionalList

    val itemsOrNull: List<SearchItem>?
        get() = (this as? Present)?.items
}
