package io.github.tukiza7_debug.vibecast.addonx.analyzer

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import io.github.tukiza7_debug.vibecast.adblock.FilterEngine
import io.github.tukiza7_debug.vibecast.adblock.web.AdBlockWebChromeClient
import io.github.tukiza7_debug.vibecast.adblock.web.AdBlockWebViewClient
import io.github.tukiza7_debug.vibecast.core.engine.MimeInference
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Off-screen WebView renderer for the One-Link flow (True Auto, stage RENDER).
 * Loads the page with JavaScript and ad blocking on, waits for the network to
 * settle, captures the rendered DOM plus every JSON endpoint and media request
 * the page made, then destroys the WebView. The captured evidence lets the
 * analyzer build honest drafts for SPA sites that plain HTTP fetching cannot see.
 */
object AutoWebRenderer {
    data class RenderResult(
        val finalUrl: String,
        val html: String,
        val jsonCandidates: List<String>,
        val mediaCandidates: List<String>,
        val blockedCount: Int,
    )

    /** Grace period after onPageFinished so XHR/fetch traffic can arrive. */
    private const val SETTLE_MS = 1_800L

    /** Hard ceiling for the whole render, settle included. */
    private const val OVERALL_TIMEOUT_MS = 20_000L

    private const val MAX_HTML_CHARS = 2_000_000

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun render(
        appContext: Context,
        url: String,
        engine: FilterEngine,
        policyProvider: () -> FilterEngine.SitePolicy,
    ): RenderResult {
        val done = CompletableDeferred<RenderResult>()
        val main = Handler(Looper.getMainLooper())
        main.post {
            var webView: WebView? = null
            val finished = AtomicBoolean(false)
            val jsonCandidates = LinkedHashSet<String>()
            val mediaCandidates = LinkedHashSet<String>()
            var blocked = 0
            var lastUrl = url

            fun complete(result: RenderResult?) {
                if (finished.compareAndSet(false, true)) {
                    try {
                        webView?.stopLoading()
                        webView?.destroy()
                    } catch (_: Exception) {
                    }
                    if (result == null) {
                        done.completeExceptionally(IllegalStateException("render failed"))
                    } else {
                        done.complete(result)
                    }
                }
            }

            fun captureHtml(view: WebView) {
                view.evaluateJavascript("document.documentElement.outerHTML") { raw ->
                    val html = EvaluatedHtml.decode(raw)
                    if (html.isNullOrBlank()) {
                        complete(null)
                    } else {
                        complete(
                            RenderResult(
                                finalUrl = lastUrl,
                                html = html.take(MAX_HTML_CHARS),
                                jsonCandidates = jsonCandidates.toList(),
                                mediaCandidates = mediaCandidates.toList(),
                                blockedCount = blocked,
                            ),
                        )
                    }
                }
            }

            try {
                val client =
                    object :
                        AdBlockWebViewClient(
                            engine,
                            policyProvider,
                            onBlocked = { blocked += 1 },
                            onRequest = { _, _, _ -> },
                        ) {
                        override fun shouldInterceptRequest(
                            view: WebView,
                            request: WebResourceRequest,
                        ): WebResourceResponse? {
                            val target = request.url.toString()
                            if (MimeInference.MEDIA_URL_REGEX.containsMatchIn(target)) {
                                mediaCandidates.add(target)
                            }
                            val accept = request.requestHeaders?.get("Accept")
                            if (accept?.contains("application/json") == true || target.substringBefore('?').endsWith(".json")) {
                                jsonCandidates.add(target)
                            }
                            return super.shouldInterceptRequest(view, request)
                        }

                        override fun onPageFinished(
                            view: WebView,
                            finishedUrl: String,
                        ) {
                            super.onPageFinished(view, finishedUrl)
                            lastUrl = finishedUrl
                            // Give XHR/fetch traffic a moment, then take the DOM.
                            main.postDelayed({ if (!finished.get()) captureHtml(view) }, SETTLE_MS)
                        }

                        override fun onReceivedError(
                            view: WebView,
                            request: WebResourceRequest,
                            error: android.webkit.WebResourceError,
                        ) {
                            super.onReceivedError(view, request, error)
                            if (request.isForMainFrame) complete(null)
                        }
                    }

                webView =
                    WebView(appContext).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.javaScriptCanOpenWindowsAutomatically = false
                        settings.loadsImagesAutomatically = true
                        webViewClient = client
                        webChromeClient = AdBlockWebChromeClient()
                        loadUrl(url)
                    }

                main.postDelayed({ if (!finished.get()) webView?.let { captureHtml(it) } }, OVERALL_TIMEOUT_MS)
            } catch (t: Throwable) {
                if (finished.compareAndSet(false, true)) {
                    try {
                        webView?.stopLoading()
                        webView?.destroy()
                    } catch (_: Exception) {
                    }
                    done.completeExceptionally(IllegalStateException("render failed: ${t.message}", t))
                }
            }
        }
        return done.await()
    }
}
