package io.github.tukiza7_debug.vibecast.addonx.ui.analyzer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import io.github.tukiza7_debug.vibecast.addonx.analyzer.ExtensionBuilder
import io.github.tukiza7_debug.vibecast.addonx.ui.common.friendlyStepError

/** A desktop-ish mobile UA for cover fetches that declare none. */
private const val TEST_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

/** Cover thumbnail honoring the tested draft's declared headers + Referer. */
@Composable
private fun TestCover(
    url: String?,
    draft: ExtensionBuilder.Draft?,
    title: String,
    modifier: Modifier = Modifier,
) {
    if (url.isNullOrBlank()) {
        Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(title.take(1), style = MaterialTheme.typography.titleLarge)
            }
        }
        return
    }
    val context = LocalContext.current
    val request =
        remember(url, draft) {
            ImageRequest
                .Builder(context)
                .data(url)
                .crossfade(true)
                .apply {
                    draft?.headers?.forEach { (name, value) -> setHeader(name, value) }
                    draft?.baseUrl?.takeIf { it.isNotBlank() }?.let { setHeader("Referer", it) }
                    if (draft?.headers?.keys?.none { it.equals("User-Agent", ignoreCase = true) } == true) {
                        setHeader("User-Agent", TEST_USER_AGENT)
                    }
                }
                .build()
        }
    AsyncImage(
        model = request,
        contentDescription = title,
        contentScale = ContentScale.Crop,
        modifier = modifier.clip(MaterialTheme.shapes.small),
    )
}

/** Test tab: builds the draft and runs every step through the real core engine. */
@Composable
fun TestPanel(
    viewModel: AnalyzerViewModel,
    modifier: Modifier = Modifier,
    testViewModel: StepTestViewModel = hiltViewModel(),
) {
    val results by testViewModel.results.collectAsState()
    val pipelineRunning by testViewModel.pipelineRunning.collectAsState()
    val lastAction by viewModel.lastAction.collectAsState()
    val testedDraft by testViewModel.draft.collectAsState()
    var previewUrl by remember { mutableStateOf<String?>(null) }
    var previewMime by remember { mutableStateOf<String?>(null) }

    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(14.dp)) {
        Text("Test with the real engine", style = MaterialTheme.typography.titleLarge)
        Text(
            "This runs the current draft through search, detail, episodes and video exactly like VibeCast " +
                "would - no mocks. Video results preview with Media3.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = {
                    val draft =
                        viewModel.buildDraft(
                            id = ExtensionBuilder.idForHost(viewModel.pageUrl.value.ifEmpty { "https://invalid.test" }),
                            name = "Test draft",
                            version = 1,
                        )
                    if (draft == null) {
                        testViewModel.reportSetupError(
                            "nothing to test yet: load a page with proposals or open a draft from Auto Import",
                        )
                    } else {
                        testViewModel.runPipeline(draft)
                    }
                },
                enabled = !pipelineRunning,
            ) {
                if (pipelineRunning) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text("Run all steps")
            }
            OutlinedButton(onClick = { testViewModel.clear() }, enabled = !pipelineRunning) {
                Text("Clear")
            }
        }
        lastAction?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
        results.forEach { result ->
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (result.error == null) Icons.Filled.CheckCircle else Icons.Filled.Error,
                            contentDescription = null,
                            tint = if (result.error == null) Color(0xFF4ADE80) else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(result.step.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.weight(1f))
                        Text(
                            "${result.timingMs} ms",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (result.error != null) {
                        val friendly = friendlyStepError(result.step, result.error)
                        Text(
                            friendly ?: result.error.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        if (friendly != null && friendly != result.error) {
                            Text(
                                "detail: ${result.error}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        if (result.step == "search" && result.items.any { !it.cover.isNullOrBlank() }) {
                            // real cover preview with the draft's own headers
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(result.items.size) { index ->
                                    val item = result.items[index]
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        TestCover(
                                            url = item.cover,
                                            draft = testedDraft,
                                            title = item.title,
                                            modifier = Modifier.size(width = 78.dp, height = 110.dp),
                                        )
                                        Text(
                                            item.title,
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.width(78.dp),
                                        )
                                    }
                                }
                            }
                        }
                        if (result.step == "detail" && result.coverUrl != null) {
                            TestCover(
                                url = result.coverUrl,
                                draft = testedDraft,
                                title = result.output,
                                modifier = Modifier.size(width = 78.dp, height = 110.dp),
                            )
                        }
                        Text(
                            result.output.take(400),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (result.step == "video" && result.streamUrl != null) {
                            OutlinedButton(onClick = {
                                previewUrl = result.streamUrl
                                previewMime = result.streamMime
                            }) {
                                Icon(Icons.Filled.PlayCircle, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Preview with Media3")
                            }
                        }
                    }
                }
            }
        }
    }

    previewUrl?.let { url ->
        AlertDialog(
            onDismissRequest = { previewUrl = null },
            confirmButton = {
                TextButton(onClick = { previewUrl = null }) { Text("Close") }
            },
            title = { Text("Media3 preview") },
            text = {
                VideoPreview(
                    url = url,
                    mimeType = previewMime,
                    modifier = Modifier.fillMaxWidth().height(220.dp),
                )
            },
        )
    }
}

/** Real ExoPlayer surface: HLS for m3u8, progressive for anything else. */
@Composable
private fun VideoPreview(
    url: String,
    mimeType: String?,
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val player =
        remember(url) {
            ExoPlayer.Builder(context).build().apply {
                val item =
                    when (mimeType) {
                        MimeTypes.APPLICATION_M3U8 -> MediaItem.fromUri(url)
                        else -> MediaItem.fromUri(url)
                    }
                setMediaItem(item)
                prepare()
                playWhenReady = true
            }
        }
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                useController = true
            }
        },
        modifier = modifier,
    )
}
