package io.github.tukiza7_debug.vibecast.ui.nav

import io.github.tukiza7_debug.vibecast.data.repo.FeedRepository
import java.net.URLDecoder
import java.net.URLEncoder

object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val LIBRARY = "library"
    const val EXTENSIONS = "extensions"
    const val SETTINGS = "settings"

    fun detail(
        extensionId: String,
        itemUrl: String,
        title: String,
        cover: String?,
        autoplay: Boolean = false,
    ): String {
        // URLEncoder is form-encoding and turns spaces into '+', which Navigation
        // does not decode back; encode manually for path/query-safe segments.
        fun enc(raw: String): String = URLEncoder.encode(raw, "UTF-8").replace("+", "%20")
        return buildString {
            append("detail/").append(enc(extensionId))
            append("/").append(enc(itemUrl))
            append("/").append(enc(title))
            append("?cover=").append(enc(cover ?: ""))
            if (autoplay) append("&autoplay=1")
        }
    }
}

data class DetailArgs(
    val extensionId: String,
    val itemUrl: String,
    val title: String,
    val cover: String?,
    val autoplay: Boolean = false,
)

fun parseDetailRoute(route: String): DetailArgs? {
    val path = route.substringBefore("?")
    val query = route.substringAfter("?", "")
    val parts = path.removePrefix("detail/").split("/")
    if (parts.size < 3) return null

    fun queryParam(name: String): String? =
        query.split("&")
            .mapNotNull { pair -> pair.split("=", limit = 2).takeIf { it.size == 2 } }
            .firstOrNull { it[0] == name }
            ?.get(1)
    val url = URLDecoder.decode(parts[1], "UTF-8")
    val title = URLDecoder.decode(parts[2], "UTF-8")
    val coverRaw = queryParam("cover")
    val cover = coverRaw?.takeIf { it.isNotEmpty() }?.let { URLDecoder.decode(it, "UTF-8") }
    val autoplay = queryParam("autoplay") == "1"
    return DetailArgs(URLDecoder.decode(parts[0], "UTF-8"), url, title, cover, autoplay)
}

/** Maps a home row item to its detail route. */
fun detailRouteFor(
    extensionId: String,
    item: io.github.tukiza7_debug.vibecast.core.model.SearchItem,
    autoplay: Boolean = false,
): String = Routes.detail(extensionId, item.url, item.title, item.cover, autoplay)

typealias HomeRow = FeedRepository.HomeRow
