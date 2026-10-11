package io.github.tukiza7_debug.vibecast.ui.nav

import io.github.tukiza7_debug.vibecast.data.repo.FeedRepository

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
    ) = "detail/$extensionId/${java.net.URLEncoder.encode(itemUrl, "UTF-8")}/${java.net.URLEncoder.encode(title, "UTF-8")}" +
        "?cover=${java.net.URLEncoder.encode(cover ?: "", "UTF-8")}"
}

data class DetailArgs(val extensionId: String, val itemUrl: String, val title: String, val cover: String?)

fun parseDetailRoute(route: String): DetailArgs? {
    val parts = route.removePrefix("detail/").split("/")
    if (parts.size < 3) return null
    val url = java.net.URLDecoder.decode(parts[1], "UTF-8")
    val title = java.net.URLDecoder.decode(parts[2], "UTF-8")
    val cover =
        route.substringAfter("cover=", "").takeIf { it.isNotEmpty() }?.let {
            java.net.URLDecoder.decode(it.substringBefore('&'), "UTF-8")
        }
    return DetailArgs(parts[0], url, title, cover)
}

/** Maps a home row item to its detail route. */
fun detailRouteFor(
    extensionId: String,
    item: io.github.tukiza7_debug.vibecast.core.model.SearchItem,
): String = Routes.detail(extensionId, item.url, item.title, item.cover)

typealias HomeRow = FeedRepository.HomeRow
