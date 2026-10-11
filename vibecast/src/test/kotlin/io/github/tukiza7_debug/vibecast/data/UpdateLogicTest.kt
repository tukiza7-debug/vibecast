package io.github.tukiza7_debug.vibecast.data

import io.github.tukiza7_debug.vibecast.data.remote.ReleaseParser
import io.github.tukiza7_debug.vibecast.util.SemVer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SemVerTest {
    @Test
    fun `compares semver versions`() {
        assertTrue(SemVer.compare("1.1.0", "1.0.9") > 0)
        assertTrue(SemVer.compare("2.0.0", "1.99.99") > 0)
        assertEquals(0, SemVer.compare("1.4.2", "1.4.2"))
        assertTrue(SemVer.compare("1.0.0", "1.0.1") < 0)
    }

    @Test
    fun `handles pre-release suffixes safely`() {
        assertEquals(0, SemVer.compare("1.0.0-rc1", "1.0.0"))
        assertTrue(SemVer.compare("1.1.0-rc1", "1.0.0") > 0)
    }
}

class ReleaseParserTest {
    private fun releaseJson(
        tag: String,
        vararg apks: String,
        drafts: Boolean = false,
    ) = """
        {
          "tag_name": "$tag",
          "draft": $drafts,
          "body": "- fix: playback\n- feat: resume",
          "assets": [
            ${apks.joinToString(",") { """{"name": "$it", "browser_download_url": "https://example/$it"}""" }},
            {"name": "SHA256SUMS.txt", "browser_download_url": "https://example/SHA256SUMS.txt"}
          ]
        }
        """.trimIndent()

    @Test
    fun `prefers the device abi`() {
        val json =
            releaseJson(
                "v1.2.0",
                "VibeCast-1.2.0-universal.apk",
                "VibeCast-1.2.0-arm64-v8a.apk",
                "VibeCast-1.2.0-x86.apk",
            )
        val release = ReleaseParser.parse(json, "x86")
        assertEquals("VibeCast-1.2.0-x86.apk", release?.apkName)
        assertEquals("https://example/SHA256SUMS.txt", release?.checksumsUrl)
        assertTrue(release?.notes!!.contains("fix: playback"))
    }

    @Test
    fun `falls back to universal when no abi matches`() {
        val json = releaseJson("v1.2.0", "VibeCast-1.2.0-universal.apk", "VibeCast-1.2.0-arm64-v8a.apk")
        assertEquals("VibeCast-1.2.0-universal.apk", ReleaseParser.parse(json, "mips")?.apkName)
    }

    @Test
    fun `ignores drafts and site releases`() {
        assertNull(ReleaseParser.parse(releaseJson("site-mybuild-1", "VibeCast-1.0.0-universal.apk"), "arm64-v8a"))
        assertNull(ReleaseParser.parse(releaseJson("v2.0.0", "VibeCast-2.0.0-universal.apk", drafts = true), "arm64-v8a"))
    }

    @Test
    fun `returns null without apk assets`() {
        assertNull(ReleaseParser.parse(releaseJson("v1.0.0"), "arm64-v8a"))
    }
}
