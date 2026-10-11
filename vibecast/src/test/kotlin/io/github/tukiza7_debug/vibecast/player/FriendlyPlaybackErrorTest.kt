package io.github.tukiza7_debug.vibecast.player

import io.github.tukiza7_debug.vibecast.ui.player.friendlyPlaybackError
import org.junit.Assert.assertEquals
import org.junit.Test

/** The player error banner must speak plainly and keep DRM/unsupported honest. */
class FriendlyPlaybackErrorTest {
    @Test
    fun `drm wording`() {
        val text = friendlyPlaybackError("PlaybackException: ERROR_CODE_DRM_SCHEME_UNSUPPORTED")
        assertEquals("This stream is DRM-protected and cannot be played here.", text)
    }

    @Test
    fun `unsupported format wording`() {
        val text = friendlyPlaybackError("Source error: unsupported container")
        assertEquals("This source uses a format the player cannot handle yet.", text)
    }

    @Test
    fun `expired link wording points at retry`() {
        val text = friendlyPlaybackError("Response code: 410")
        assertEquals("This stream link has expired. Tap Retry to re-resolve it from the source.", text)
    }

    @Test
    fun `network wording`() {
        val text = friendlyPlaybackError("Unable to connect to https://example.test")
        assertEquals("The stream could not be reached. Check your connection and retry.", text)
    }

    @Test
    fun `unknown errors stay generic and null is safe`() {
        assertEquals("Playback stopped unexpectedly.", friendlyPlaybackError("weird internal"))
        assertEquals("Playback stopped unexpectedly.", friendlyPlaybackError(null))
    }
}
