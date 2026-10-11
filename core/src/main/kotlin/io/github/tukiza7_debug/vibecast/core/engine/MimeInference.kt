package io.github.tukiza7_debug.vibecast.core.engine

/** Maps a stream URL to a MIME type; the player layer maps these to Media3 constants. */
object MimeInference {

    fun fromUrl(url: String): String? {
        val path = url.substringBefore('?').substringBefore('#').lowercase()
        return when {
            path.endsWith(".m3u8") -> "application/x-mpegurl"
            path.endsWith(".mpd") -> "application/dash+xml"
            path.endsWith(".mp4") -> "video/mp4"
            path.endsWith(".m4v") -> "video/mp4"
            path.endsWith(".webm") -> "video/webm"
            path.endsWith(".mkv") -> "video/x-matroska"
            path.endsWith(".ts") -> "video/mp2t"
            path.endsWith(".vtt") -> "text/vtt"
            path.endsWith(".srt") -> "application/x-subrip"
            else -> null
        }
    }

    fun normalize(contentType: String?): String? {
        val raw = contentType?.substringBefore(';')?.trim()?.lowercase() ?: return null
        if (raw.isEmpty()) return null
        return when (raw) {
            "application/vnd.apple.mpegurl", "application/mpegurl", "application/x-mpegurl",
            "audio/mpegurl", "audio/x-mpegurl",
            -> "application/x-mpegurl"
            else -> raw
        }
    }
}
