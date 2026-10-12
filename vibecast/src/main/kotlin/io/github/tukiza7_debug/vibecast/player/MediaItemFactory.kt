package io.github.tukiza7_debug.vibecast.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import io.github.tukiza7_debug.vibecast.core.engine.MimeInference
import io.github.tukiza7_debug.vibecast.core.model.StreamRef

/**
 * Builds Media3 MediaItems from extension stream references. MIME is set explicitly
 * whenever it is known or inferable, so HLS/DASH work even on extensionless URLs.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
object MediaItemFactory {
    fun media3MimeType(mime: String?): String? =
        when (mime?.lowercase()) {
            "application/x-mpegurl", "application/vnd.apple.mpegurl", "audio/mpegurl", "audio/x-mpegurl" -> MimeTypes.APPLICATION_M3U8
            "application/dash+xml" -> MimeTypes.APPLICATION_MPD
            "video/mp4", "video/mp4v-es", null -> if (mime == null) null else MimeTypes.VIDEO_MP4
            "video/webm" -> MimeTypes.VIDEO_WEBM
            "video/mp2t" -> MimeTypes.VIDEO_MP2T
            "text/vtt" -> MimeTypes.TEXT_VTT
            "application/x-subrip" -> MimeTypes.APPLICATION_SUBRIP
            else -> null
        }

    fun build(
        streamRef: StreamRef,
        mediaId: String,
        title: String,
        subtitleLabel: String?,
    ): MediaItem {
        val url = streamRef.url.trim()
        require(url.isNotEmpty()) { "stream URL is empty (mediaId=$mediaId)" }
        val mimeType = resolveMimeType(streamRef)
        val builder =
            MediaItem.Builder()
                .setMediaId(mediaId)
                .setUri(Uri.parse(url))
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(title)
                        .setArtist(subtitleLabel)
                        .build(),
                )
        if (mimeType != null) {
            builder.setMimeType(mimeType)
        }
        val validSubtitles = streamRef.subtitles.filter { it.url.isNotBlank() }
        if (validSubtitles.isNotEmpty()) {
            val configs =
                validSubtitles.map { track ->
                    MediaItem.SubtitleConfiguration.Builder(Uri.parse(track.url.trim()))
                        .setMimeType(media3MimeType(trackMime(track.url)) ?: MimeTypes.TEXT_VTT)
                        .setLanguage(track.language.ifBlank { null })
                        .setLabel(track.label.ifBlank { track.language.ifBlank { "Subtitles" } })
                        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                        .build()
                }
            builder.setSubtitleConfigurations(configs)
        }
        return builder.build()
    }

    /**
     * MIME selection: an explicit extension on the URL wins, the extension-declared
     * MIME fills gaps, an explicit MIME is used when the URL has no usable extension,
     * and unknown URLs stay null so the progressive extractors sniff the content.
     */
    fun resolveMimeType(streamRef: StreamRef): String? {
        val fromUrl = MimeInference.fromUrl(streamRef.url)?.let(::media3MimeType)
        if (fromUrl != null) return fromUrl
        return media3MimeType(streamRef.mimeType)
    }

    private fun trackMime(url: String): String? =
        when {
            url.substringBefore('?').endsWith(".vtt") -> "text/vtt"
            url.substringBefore('?').endsWith(".srt") -> "application/x-subrip"
            else -> null
        }
}
