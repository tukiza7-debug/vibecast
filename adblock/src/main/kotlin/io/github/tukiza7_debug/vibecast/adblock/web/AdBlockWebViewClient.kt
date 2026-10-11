package io.github.tukiza7_debug.vibecast.adblock.web

import android.graphics.Bitmap
import android.os.Build
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import io.github.tukiza7_debug.vibecast.adblock.FilterEngine
import io.github.tukiza7_debug.vibecast.adblock.FilterEngine.SitePolicy
import io.github.tukiza7_debug.vibecast.adblock.rules.BlockRequest
import io.github.tukiza7_debug.vibecast.adblock.rules.Decision
import io.github.tukiza7_debug.vibecast.adblock.rules.ResourceType
import io.github.tukiza7_debug.vibecast.adblock.rules.UrlMatcher
import java.io.ByteArrayInputStream

/**
 * WebViewClient with network filtering, cosmetic CSS injection and popup/redirect
 * guarding. Subclasses can observe or extend behavior (AddOnX records requests).
 */
open class AdBlockWebViewClient(
    private val engine: FilterEngine,
    private val policy: () -> SitePolicy,
    private val onBlocked: ((url: String) -> Unit)? = null,
    private val onRequest: ((url: String, type: ResourceType?, blocked: Boolean) -> Unit)? = null,
) : WebViewClient() {

    private var currentHost: String? = null

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        val url = request.url.toString()
        val type = inferType(url, request.requestHeaders["Accept"])
        val blockRequest = BlockRequest(
            url = url,
            pageHost = currentHost,
            type = type,
            isUserGesture = false,
        )
        return when (val decision = engine.decide(blockRequest, policy())) {
            is Decision.Block -> {
                onRequest?.invoke(url, type, true)
                onBlocked?.invoke(url)
                emptyResponse()
            }
            is Decision.AllowException -> {
                onRequest?.invoke(url, type, false)
                null
            }
            Decision.Allow -> {
                onRequest?.invoke(url, type, false)
                null
            }
        }
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val action = PopupGuard.onTopLevelNavigation(
            url = request.url.toString(),
            currentHost = currentHost,
            isUserGesture = request.hasGesture(),
            isRedirect = isRedirect(request),
        )
        return action == PopupGuard.Action.BLOCK
    }

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        currentHost = UrlMatcher.hostOf(url)
        injectCosmetic(view, url)
        view.evaluateJavascript(CosmeticJs.mutationObserver(), null)
        super.onPageStarted(view, url, favicon)
    }

    override fun onPageFinished(view: WebView, url: String) {
        // Re-apply in case the page replaced the <style> node while rendering.
        injectCosmetic(view, url)
        super.onPageFinished(view, url)
    }

    private fun injectCosmetic(view: WebView, url: String) {
        val css = engine.cosmeticCss(url, policy())
        if (css.isNotEmpty()) {
            val escaped = css.replace("\\", "\\\\").replace("`", "\\`").replace("$", "\\$")
            view.evaluateJavascript(CosmeticJs.applyCss(escaped), null)
        }
    }

    private fun isRedirect(request: WebResourceRequest): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && request.isRedirect

    private fun emptyResponse(): WebResourceResponse = WebResourceResponse(
        "text/plain",
        "utf-8",
        200,
        "OK",
        mapOf("Access-Control-Allow-Origin" to "*"),
        ByteArrayInputStream(ByteArray(0)),
    )

    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
        // Filtered responses surface as errors for subresources; ignore them.
        if (request.isForMainFrame) super.onReceivedError(view, request, error)
    }

    companion object {
        fun inferType(url: String, acceptHeader: String?): ResourceType? {
            acceptHeader?.lowercase()?.let { accept ->
                if (accept.contains("text/css")) return ResourceType.STYLESHEET
                if (accept.contains("image/")) return ResourceType.IMAGE
                if (accept.contains("javascript") || accept.contains("application/ecmascript")) return ResourceType.SCRIPT
            }
            val path = url.substringBefore('?').lowercase()
            return when {
                path.endsWith(".js") -> ResourceType.SCRIPT
                path.endsWith(".css") -> ResourceType.STYLESHEET
                path.endsWith(".png") || path.endsWith(".jpg") || path.endsWith(".jpeg") ||
                    path.endsWith(".gif") || path.endsWith(".webp") || path.endsWith(".svg")
                -> ResourceType.IMAGE
                path.endsWith(".m3u8") || path.endsWith(".mp4") || path.endsWith(".mpd") ||
                    path.endsWith(".webm") || path.endsWith(".ts")
                -> ResourceType.MEDIA
                else -> null
            }
        }
    }
}
