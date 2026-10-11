package io.github.tukiza7_debug.vibecast.adblock.net

import io.github.tukiza7_debug.vibecast.adblock.FilterEngine
import io.github.tukiza7_debug.vibecast.adblock.FilterEngine.SitePolicy
import io.github.tukiza7_debug.vibecast.adblock.rules.BlockRequest
import io.github.tukiza7_debug.vibecast.adblock.rules.Decision
import io.github.tukiza7_debug.vibecast.adblock.rules.ResourceType
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Applies the same network filter to the shared OkHttp client, which follows iframes
 * and embed pages. Playback requests are protected by registering their hosts in
 * SitePolicy.bypassHosts.
 */
class AdBlockInterceptor(
    private val engine: FilterEngine,
    private val policyProvider: () -> SitePolicy,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val accept = request.header("Accept")
        val type = when {
            accept?.contains("text/css") == true -> ResourceType.STYLESHEET
            accept?.contains("image/") == true -> ResourceType.IMAGE
            else -> null
        }
        val decision = engine.decide(
            BlockRequest(
                url = request.url.toString(),
                pageHost = request.header("Referer")?.let { io.github.tukiza7_debug.vibecast.adblock.rules.UrlMatcher.hostOf(it) },
                type = type,
            ),
            policyProvider(),
        )
        if (decision is Decision.Block) {
            return Response.Builder()
                .request(request)
                .protocol(okhttp3.Protocol.HTTP_1_1)
                .code(403)
                .message("Blocked by ad filter")
                .body(okhttp3.ResponseBody.create(null, ByteArray(0)))
                .build()
        }
        return chain.proceed(request)
    }
}
