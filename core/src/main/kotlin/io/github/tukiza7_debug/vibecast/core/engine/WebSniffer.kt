package io.github.tukiza7_debug.vibecast.core.engine

import io.github.tukiza7_debug.vibecast.core.http.HostFilter
import io.github.tukiza7_debug.vibecast.core.model.SubtitleTrack

/** WebView-backed sniffer port; implemented by the Android layer with ad blocking on. */
interface WebSniffer {
    fun sniff(pageUrl: String, headers: Map<String, String>, timeoutMs: Long): SniffOutcome
}

sealed class SniffOutcome {
    data class Found(
        val url: String,
        val mimeType: String?,
        val requestHeaders: Map<String, String>,
    ) : SniffOutcome()

    data class Failed(val reason: String) : SniffOutcome()
}

class SniffingUnavailableException(reason: String) : EngineException("video", "strategy", reason)
