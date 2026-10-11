package io.github.tukiza7_debug.vibecast.imageloading

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

/**
 * Applies extension headers to cover requests. Every image request gets a browser
 * User-Agent and a Referer, because many CDNs reject the OkHttp default client.
 */
class CoverHeaderInterceptor(
    private val registry: ImageHeaderRegistry,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request: Request = chain.request()
        val extra = registry.headersFor(request.url.toString())
        val builder = request.newBuilder()
        extra.forEach { (name, value) -> builder.header(name, value) }
        if (request.header("User-Agent") == null && extra.keys.none { it.equals("user-agent", true) }) {
            builder.header("User-Agent", registry.defaultUserAgent)
        }
        if (request.header("Referer") == null && extra.keys.none { it.equals("referer", true) }) {
            builder.header("Referer", "${request.url.scheme}://${request.url.host}/")
        }
        return chain.proceed(builder.build())
    }
}
