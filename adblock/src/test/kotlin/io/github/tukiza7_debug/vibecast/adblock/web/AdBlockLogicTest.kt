package io.github.tukiza7_debug.vibecast.adblock.web

import io.github.tukiza7_debug.vibecast.adblock.FilterEngine
import io.github.tukiza7_debug.vibecast.adblock.FilterEngine.SitePolicy
import io.github.tukiza7_debug.vibecast.adblock.rules.ResourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PopupGuardTest {
    @Test
    fun `window creation without gesture is blocked`() {
        assertEquals(PopupGuard.Action.BLOCK, PopupGuard.onCreateWindow(false))
        assertEquals(PopupGuard.Action.ALLOW, PopupGuard.onCreateWindow(true))
    }

    @Test
    fun `intent and market schemes need a gesture`() {
        assertEquals(PopupGuard.Action.BLOCK, PopupGuard.onTopLevelNavigation("intent://pay/#Intent;end", "news.com", false, false))
        assertEquals(PopupGuard.Action.BLOCK, PopupGuard.onTopLevelNavigation("market://details?id=x", "news.com", false, false))
        assertEquals(PopupGuard.Action.ALLOW, PopupGuard.onTopLevelNavigation("market://details?id=x", "news.com", true, false))
        assertEquals(PopupGuard.Action.ALLOW, PopupGuard.onTopLevelNavigation("about:blank", "news.com", false, false))
    }

    @Test
    fun `cross-site top-level navigation without gesture is blocked`() {
        assertEquals(
            PopupGuard.Action.BLOCK,
            PopupGuard.onTopLevelNavigation("https://spam.example.org/x", "news.com", false, false),
        )
    }

    @Test
    fun `redirects and same-site navigation pass`() {
        assertEquals(
            PopupGuard.Action.ALLOW,
            PopupGuard.onTopLevelNavigation("https://cdn.news.com/asset", "news.com", false, false),
        )
        assertEquals(
            PopupGuard.Action.ALLOW,
            PopupGuard.onTopLevelNavigation("https://tracker.example.net/jump", "news.com", false, true),
        )
        assertEquals(
            PopupGuard.Action.ALLOW,
            PopupGuard.onTopLevelNavigation("https://other.example.net/jump", "news.com", true, false),
        )
    }
}

class FilterEngineTest {
    private fun engineWith(
        rules: String,
        custom: String = "",
    ): FilterEngine {
        val engine = FilterEngine()
        engine.replace(
            FilterEngine.Lists(easyList = rules, easyPrivacy = null, customRules = custom),
            SitePolicy(),
        )
        return engine
    }

    @Test
    fun `replace swaps state atomically and counters increment`() {
        val engine = engineWith("||ads.example.com^")
        val decision =
            engine.decide(
                io.github.tukiza7_debug.vibecast.adblock.rules.BlockRequest("https://ads.example.com/x", "news.com", null),
                SitePolicy(),
            )
        assertTrue(decision is io.github.tukiza7_debug.vibecast.adblock.rules.Decision.Block)
        assertEquals(1L, engine.blockedRequests.get())
    }

    @Test
    fun `bypass hosts are never blocked`() {
        val engine = engineWith("||cdn.media^")
        val policy = SitePolicy(bypassHosts = setOf("cdn.media"))
        val decision =
            engine.decide(
                io.github.tukiza7_debug.vibecast.adblock.rules.BlockRequest("https://cdn.media/video.m3u8", "news.com", null),
                policy,
            )
        assertTrue(decision is io.github.tukiza7_debug.vibecast.adblock.rules.Decision.Allow)
    }

    @Test
    fun `per-site disable stops filtering for that page`() {
        val engine = engineWith("||ads.example.com^")
        val policy = SitePolicy(disabledHosts = setOf("news.com"))
        val decision =
            engine.decide(
                io.github.tukiza7_debug.vibecast.adblock.rules.BlockRequest("https://ads.example.com/x", "news.com", null),
                policy,
            )
        assertTrue(decision is io.github.tukiza7_debug.vibecast.adblock.rules.Decision.Allow)
    }

    @Test
    fun `cosmetic css includes global and scoped selectors and honors exceptions`() {
        val engine = FilterEngine()
        engine.replace(
            FilterEngine.Lists(
                easyList = "##.banner\ncustom.example##.inline-promo\n#@#.banner",
                easyPrivacy = null,
                customRules = "",
            ),
            SitePolicy(),
        )
        val css = engine.cosmeticCss("https://custom.example/page", SitePolicy())
        assertTrue(css.contains(".inline-promo"))
        assertTrue(!css.contains(".banner"))
    }

    @Test
    fun `cosmetic css empty for disabled site`() {
        val engine = engineWith("##.banner")
        val css = engine.cosmeticCss("https://news.com/x", SitePolicy(disabledHosts = setOf("news.com")))
        assertEquals("", css)
    }

    @Test
    fun `custom rules participate in decisions`() {
        val engine = engineWith("||base.example^", custom = "||extra.example^")
        assertTrue(
            engine.decide(
                io.github.tukiza7_debug.vibecast.adblock.rules.BlockRequest("https://extra.example/a.js", "x.com", null),
                SitePolicy(),
            ) is io.github.tukiza7_debug.vibecast.adblock.rules.Decision.Block,
        )
    }

    @Test
    fun `webview accept header infers resource type`() {
        assertEquals(ResourceType.STYLESHEET, AdBlockWebViewClient.inferType("https://x.com/a", "text/css,*/*"))
        assertEquals(ResourceType.IMAGE, AdBlockWebViewClient.inferType("https://x.com/a", "image/webp"))
        assertEquals(ResourceType.SCRIPT, AdBlockWebViewClient.inferType("https://x.com/a.js", null))
        assertEquals(ResourceType.MEDIA, AdBlockWebViewClient.inferType("https://x.com/v.m3u8", null))
    }
}

class SniffRequestJudgeTest {
    @Test
    fun `media urls are detected by path and query`() {
        assertTrue(SniffRequestJudge.isMediaUrl("https://cdn.example/hls/master.m3u8"))
        assertTrue(SniffRequestJudge.isMediaUrl("https://cdn.example/v.mp4?token=1"))
        assertTrue(SniffRequestJudge.isMediaUrl("https://cdn.example/get?file=master.m3u8&wmsAuthSign=x"))
        assertTrue(!SniffRequestJudge.isMediaUrl("https://cdn.example/page.html"))
        assertTrue(!SniffRequestJudge.isMediaUrl("https://cdn.example/api/data.json"))
    }

    @Test
    fun `mime types map by extension`() {
        assertEquals("application/x-mpegurl", SniffRequestJudge.mimeTypeFromUrl("https://c/x.m3u8"))
        assertEquals("application/dash+xml", SniffRequestJudge.mimeTypeFromUrl("https://c/x.mpd"))
        assertEquals("video/mp4", SniffRequestJudge.mimeTypeFromUrl("https://c/x.mp4"))
        assertEquals(null, SniffRequestJudge.mimeTypeFromUrl("https://c/x.php?id=1"))
    }
}
