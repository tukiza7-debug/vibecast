package io.github.tukiza7_debug.vibecast.core.engine

import java.net.URI
import java.net.URLEncoder

/** Resolves step request URL templates ({query}, {page}, {url}) and relative URLs. */
object UrlResolver {

    fun resolveTemplate(template: String, baseUrl: String, vars: Map<String, String>): String {
        var url = template
        vars.forEach { (name, value) ->
            val token = "{$name}"
            if (url.contains(token)) {
                val encoded = if (name == "query") URLEncoder.encode(value, Charsets.UTF_8) else value
                url = url.replace(token, encoded)
            }
        }
        return absolutize(url, baseUrl)
    }

    fun absolutize(url: String, baseUrl: String): String {
        if (url.startsWith("http://") || url.startsWith("https://")) return url
        return runCatching { URI(baseUrl).resolve(url).toString() }.getOrDefault(url)
    }

    fun hostOf(url: String): String? = runCatching { URI(url).host?.lowercase() }.getOrNull()
}
