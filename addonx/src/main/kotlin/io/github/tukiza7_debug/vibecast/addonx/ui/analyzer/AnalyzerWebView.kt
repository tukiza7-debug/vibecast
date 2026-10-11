package io.github.tukiza7_debug.vibecast.addonx.ui.analyzer

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.outlined.FiberManualRecord
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

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
    val pageUrl by viewModel.pageUrl.collectAsState()
    val webView = remember { AnalyzerWebViewHolder() }

    Column(modifier) {
        Surface(
            tonalElevation = 1.dp,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = pickMode,
                    onClick = { viewModel.togglePickMode(webView) },
                    label = { Text(if (pickMode) "Picking" else "Pick") },
                    leadingIcon = { Icon(Icons.Filled.TouchApp, contentDescription = null, modifier = Modifier.width(16.dp)) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer),
                )
                FilterChip(
                    selected = recording,
                    onClick = { viewModel.toggleRecording() },
                    label = { Text(if (recording) "Recording" else "Record") },
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.FiberManualRecord,
                            contentDescription = null,
                            modifier = Modifier.width(16.dp),
                            tint = if (recording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
                Text(
                    "$blockedCount blocked",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (loading) LinearProgressIndicator(Modifier.width(56.dp).height(4.dp))
            }
        }
        Text(
            pageUrl.ifEmpty { url },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    layoutParams =
                        ViewGroup.LayoutParams(
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
            Card(
                Modifier.fillMaxWidth().padding(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
            ) {
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
    override fun onPageFinished(
        view: WebView,
        url: String,
    ) {
        super.onPageFinished(view, url)
        viewModel.onPageLoaded(view, url)
    }

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest,
    ): WebResourceResponse? {
        val url = request.url.toString()
        viewModel.recordMediaCandidate(url)
        val contentType = request.requestHeaders?.get("Accept")
        if (contentType?.contains("application/json") == true ||
            url.substringBefore('?').endsWith(".json")
        ) {
            viewModel.recordJsonCandidate(url)
        }
        return super.shouldInterceptRequest(view, request)
    }
}
