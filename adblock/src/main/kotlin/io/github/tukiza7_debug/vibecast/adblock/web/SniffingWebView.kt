package io.github.tukiza7_debug.vibecast.adblock.web

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import io.github.tukiza7_debug.vibecast.adblock.FilterEngine
import io.github.tukiza7_debug.vibecast.adblock.FilterEngine.SitePolicy
import io.github.tukiza7_debug.vibecast.core.engine.SniffOutcome
import io.github.tukiza7_debug.vibecast.core.engine.WebSniffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Loads an embed page in a hidden WebView with ad blocking on and captures the first
 * media request (m3u8/mp4/mpd) plus its request headers. This implements the
 * extension video step's webviewSniff strategy. Call from a thread willing to wait;
 * the WebView itself runs on the main looper.
 */
class SniffingWebView(
    private val appContext: Context,
    private val engine: FilterEngine,
    private val policy: () -> SitePolicy,
) : WebSniffer {
    override fun sniff(
        pageUrl: String,
        headers: Map<String, String>,
        timeoutMs: Long,
    ): SniffOutcome {
        val latch = CountDownLatch(1)
        val capture = AtomicReference<Capture?>(null)
        val failure = AtomicReference<String?>(null)
        Handler(Looper.getMainLooper()).post {
            runCatching { attachAndLoad(pageUrl, headers, latch, capture, failure) }
                .onFailure {
                    failure.set(it.message ?: "failed to attach WebView")
                    latch.countDown()
                }
        }
        val finished = latch.await(timeoutMs, TimeUnit.MILLISECONDS)
        Handler(Looper.getMainLooper()).post { cleanup() }
        val result = capture.get()
        return when {
            result != null -> SniffOutcome.Found(result.url, result.mimeType, result.requestHeaders)
            !finished -> SniffOutcome.Failed("no media request captured within $timeoutMs ms")
            else -> SniffOutcome.Failed(failure.get() ?: "no media request captured on $pageUrl")
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun attachAndLoad(
        pageUrl: String,
        headers: Map<String, String>,
        latch: CountDownLatch,
        capture: AtomicReference<Capture?>,
        failure: AtomicReference<String?>,
    ) {
        val webView = WebView(appContext)
        view = webView
        webView.settings.javaScriptEnabled = true
        webView.settings.mediaPlaybackRequiresUserGesture = false
        webView.settings.blockNetworkImage = true
        headers["User-Agent"]?.let { webView.settings.userAgentString = it }
        webView.webViewClient =
            object : AdBlockWebViewClient(engine, policy) {
                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest,
                ): WebResourceResponse? {
                    val response = super.shouldInterceptRequest(view, request)
                    val url = request.url.toString()
                    if (capture.get() == null && SniffRequestJudge.isMediaUrl(url)) {
                        capture.set(
                            Capture(
                                url = url,
                                mimeType = SniffRequestJudge.mimeTypeFromUrl(url),
                                requestHeaders = request.requestHeaders?.filterValues { it != null } ?: emptyMap(),
                            ),
                        )
                        latch.countDown()
                    }
                    return response
                }
            }
        webView.loadUrl(pageUrl)
    }

    private fun cleanup() {
        view?.stopLoading()
        view?.destroy()
        view = null
    }

    private var view: WebView? = null

    data class Capture(val url: String, val mimeType: String?, val requestHeaders: Map<String, String>)
}

/** Pure URL classification used by the sniffer; unit tested on the JVM. */
object SniffRequestJudge {
    fun isMediaUrl(url: String): Boolean {
        val lower = url.lowercase()
        val path = lower.substringBefore('?')
        if (path.endsWith(".m3u8") || path.endsWith(".mp4") || path.endsWith(".mpd") ||
            path.endsWith(".webm") || path.endsWith(".ts")
        ) {
            return true
        }
        // Some CDNs serve media without a file extension; look for known tokens anywhere.
        return lower.contains(".m3u8") || lower.contains(".mp4") || lower.contains(".mpd")
    }

    fun mimeTypeFromUrl(url: String): String? {
        val path = url.substringBefore('?').lowercase()
        return when {
            path.endsWith(".m3u8") -> "application/x-mpegurl"
            path.endsWith(".mpd") -> "application/dash+xml"
            path.endsWith(".mp4") -> "video/mp4"
            path.endsWith(".webm") -> "video/webm"
            path.endsWith(".ts") -> "video/mp2t"
            else -> null
        }
    }
}
