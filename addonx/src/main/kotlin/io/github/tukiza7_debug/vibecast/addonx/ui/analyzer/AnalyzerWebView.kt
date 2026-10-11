package io.github.tukiza7_debug.vibecast.addonx.ui.analyzer

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp

/** WebView pane with the ad-blocking client plus request recording and the picker bridge. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AnalyzerWebView(
    url: String,
    viewModel: AnalyzerViewModel,
    modifier: Modifier = Modifier,
) {
    val blockedCount by viewModel.blockedCount.collectAsState()
    val pickMode by viewModel.pickMode.collectAsState()
    val recording by viewModel.recording.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val webView = remember { AnalyzerWebViewHolder() }

    Column(modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            FilterChip(selected = pickMode, onClick = { viewModel.togglePickMode(webView) }, label = {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Icon(Icons.Filled.TouchApp, contentDescription = null, modifier = Modifier.width(16.dp))
                    Text(if (pickMode) " Picking" else " Pick", style = MaterialTheme.typography.labelSmall)
                }
            })
            FilterChip(selected = recording, onClick = { viewModel.toggleRecording() }, label = {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Icon(Icons.Filled.BugReport, contentDescription = null, modifier = Modifier.width(16.dp))
                    Text(if (recording) " Recording" else " Record", style = MaterialTheme.typography.labelSmall)
                }
            })
            Text("blocked: $blockedCount", style = MaterialTheme.typography.labelSmall)
            if (loading) LinearProgressIndicator(Modifier.width(60.dp).height(4.dp))
        }
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    webViewClient = AnalyzerWebViewClient(viewModel)
                    webChromeClient = io.github.tukiza7_debug.vibecast.adblock.web.AdBlockWebChromeClient()
                    addJavascriptInterface(viewModel.pickBridge(this@apply), "VibePick")
                    loadUrl(url)
                    webView.holder = this
                }
            },
            modifier = Modifier.weight(1f),
        )
        if (pickMode) {
            Card(Modifier.fillMaxWidth().padding(8.dp)) {
                Text(
                    "Tap an element in the page to assign it to '${viewModel.currentAssignment() ?: "inspect"}'",
                    modifier = Modifier.padding(10.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

class AnalyzerWebViewHolder {
    var holder: WebView? = null
}

/** Bridges ad-block client events into the analyzer view model. */
open class AnalyzerWebViewClient(private val viewModel: AnalyzerViewModel) :
    io.github.tukiza7_debug.vibecast.adblock.web.AdBlockWebViewClient(
        viewModel.engine,
        viewModel.policyProvider,
        onBlocked = { viewModel.onBlocked(it) },
        onRequest = { url, type, blocked -> viewModel.onRequest(url, type, blocked) },
    ) {

    override fun onPageFinished(view: WebView, url: String) {
        super.onPageFinished(view, url)
        viewModel.onPageLoaded(view, url)
    }

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        viewModel.recordMediaCandidate(request.url.toString())
        return super.shouldInterceptRequest(view, request)
    }
}
