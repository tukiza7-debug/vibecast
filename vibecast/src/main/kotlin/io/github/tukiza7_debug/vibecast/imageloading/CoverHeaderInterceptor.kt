package io.github.tukiza7_debug.vibecast.imageloading

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

/**
 * Applies per-extension headers (and a sane default User-Agent for known hosts)
 * to cover/poster image requests. Headers are derived from the URL, so the cache
 * key stays stable and Coil's memory/disk caches keep working.
 */
class CoverHeaderInterceptor(
    private val registry: ImageHeaderRegistry,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request: Request = chain.request()
        val extra = registry.headersFor(request.url.toString())
        val builder = request.newBuilder()
        if (extra.isNotEmpty()) {
            extra.forEach { (name, value) ->
                when (name.lowercase()) {
                    "user-agent" -> builder.header("User-Agent", value)
                    "referer" -> builder.header("Referer", value)
                    else -> builder.header(name, value)
                }
            }
        } else if (request.header("User-Agent") == null && KNOWN_IMAGE_HOSTS.any { request.url.host.endsWith(it) }) {
            // not an extension host we know; still use a browser-like UA so generic
            // CDNs do not reject the okhttp default
            builder.header("User-Agent", registry.defaultUserAgent)
        }
        return chain.proceed(builder.build())
    }

    private companion object {
        /** Host fragments that commonly serve poster art on extension sites. */
        val KNOWN_IMAGE_HOSTS = listOf("cdn", "img", "image", "static", "media", "thumb")
    }
}
