package io.github.tukiza7_debug.vibecast.player

import androidx.media3.common.PlaybackException
import io.github.tukiza7_debug.vibecast.core.model.StreamRef
import io.github.tukiza7_debug.vibecast.core.model.SubtitleTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaItemFactoryTest {

    private fun ref(url: String, mime: String? = null) = StreamRef(url, mime, emptyMap())

    @Test
    fun `m3u8 url without mime resolves to hls`() {
        assertEquals(
            androidx.media3.common.MimeTypes.APPLICATION_M3U8,
            MediaItemFactory.resolveMimeType(ref("https://cdn.example/live/master.m3u8")),
        )
    }

    @Test
    fun `extensionless url with explicit mime keeps mime`() {
        assertEquals(
            androidx.media3.common.MimeTypes.APPLICATION_M3U8,
            MediaItemFactory.resolveMimeType(ref("https://cdn.example/get?token=1", "application/x-mpegurl")),
        )
    }

    @Test
    fun `extensionless url without mime stays progressive for extractor sniffing`() {
        assertNull(MediaItemFactory.resolveMimeType(ref("https://cdn.example/get?token=1")))
    }

    @Test
    fun `url extension wins over declared mime`() {
        assertEquals(
            androidx.media3.common.MimeTypes.APPLICATION_MPD,
            MediaItemFactory.resolveMimeType(ref("https://cdn.example/v.mpd", "application/x-mpegurl")),
        )
    }

    @Test
    fun `content type normalization maps to media3 constants`() {
        assertEquals(
            androidx.media3.common.MimeTypes.APPLICATION_M3U8,
            MediaItemFactory.media3MimeType("application/vnd.apple.mpegurl"),
        )
        assertEquals(
            androidx.media3.common.MimeTypes.APPLICATION_MPD,
            MediaItemFactory.media3MimeType("application/dash+xml"),
        )
        assertEquals(androidx.media3.common.MimeTypes.VIDEO_MP4, MediaItemFactory.media3MimeType("video/mp4"))
        assertNull(MediaItemFactory.media3MimeType("text/html"))
    }
}

class HeadersRegistryTest {

    @Test
    fun `headers apply per stream host and subdomain`() {
        val registry = HeadersRegistry()
        registry.register(
            "ep1",
            "https://media.stream-a.net/hls/index.m3u8",
            "https://site.example",
            mapOf("Referer" to "https://site.example", "User-Agent" to "UA-A"),
        )
        registry.register(
            "ep2",
            "https://cdn.other.net/v.mp4",
            "https://other.example",
            mapOf("Referer" to "https://other.example"),
        )

        assertEquals("https://site.example", registry.headersFor("media.stream-a.net")["Referer"])
        assertEquals("https://site.example", registry.headersFor("hls.media.stream-a.net")["Referer"])
        assertEquals("https://other.example", registry.headersFor("cdn.other.net")["Referer"])
        assertTrue(registry.headersFor("unrelated.example").isEmpty())
    }

    @Test
    fun `required hosts feed the ad filter bypass`() {
        val registry = HeadersRegistry()
        registry.register("ep1", "https://media.stream-a.net/x.m3u8", "https://site.example", emptyMap())
        assertTrue(registry.requiredHosts().contains("media.stream-a.net"))
        registry.clear()
        assertTrue(registry.requiredHosts().isEmpty())
    }
}

class PlaybackRecoveryTest {

    @Test
    fun `network errors retry with backoff up to the limit`() {
        assertEquals(
            PlaybackRecovery.Action.RETRY_NETWORK,
            PlaybackRecovery.decide(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, null, 0, 0),
        )
        assertEquals(
            PlaybackRecovery.Action.FAIL,
            PlaybackRecovery.decide(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, null, 2, 0),
        )
    }

    @Test
    fun `403 and 410 refresh the stream once`() {
        assertEquals(
            PlaybackRecovery.Action.RESOLVE_STREAM_AGAIN,
            PlaybackRecovery.decide(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, 403, 0, 0),
        )
        assertEquals(
            PlaybackRecovery.Action.RESOLVE_STREAM_AGAIN,
            PlaybackRecovery.decide(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, 410, 0, 0),
        )
        assertEquals(
            PlaybackRecovery.Action.FAIL,
            PlaybackRecovery.decide(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, 403, 0, 1),
        )
    }

    @Test
    fun `decoder failures surface to the user`() {
        assertEquals(
            PlaybackRecovery.Action.FAIL,
            PlaybackRecovery.decide(PlaybackException.ERROR_CODE_DECODING_FAILED, null, 0, 0),
        )
    }

    @Test
    fun `normal status codes are not treated as expired`() {
        assertFalse(PlaybackRecovery.isExpiredOrForbidden(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, 404))
    }
}
