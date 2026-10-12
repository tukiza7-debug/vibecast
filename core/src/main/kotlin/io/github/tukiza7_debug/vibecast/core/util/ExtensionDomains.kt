package io.github.tukiza7_debug.vibecast.core.util

import io.github.tukiza7_debug.vibecast.core.engine.UrlResolver
import io.github.tukiza7_debug.vibecast.core.model.Extension

/** Collects every host an extension will contact, for import confirmation sheets. */
object ExtensionDomains {
    fun of(extension: Extension): List<String> =
        buildSet {
            add(UrlResolver.hostOf(extension.baseUrl))
            val requests =
                listOf(
                    extension.search.request.url,
                    extension.latest?.request?.url,
                    extension.popular?.request?.url,
                    extension.detail.request.url,
                    extension.episodes.request.url,
                    extension.video.request.url,
                )
            requests.filterNotNull().forEach { template ->
                // Template placeholders never appear in the host part for real extensions;
                // the {url} placeholder is approximated with baseUrl because the stream host
                // is only known once episodes resolve.
                val sample =
                    template
                        .replace("{query}", "x")
                        .replace("{page}", "1")
                        .replace("{url}", extension.baseUrl)
                        .substringBefore('?')
                add(UrlResolver.hostOf(sample))
            }
        }.filterNotNull().sorted()
}
