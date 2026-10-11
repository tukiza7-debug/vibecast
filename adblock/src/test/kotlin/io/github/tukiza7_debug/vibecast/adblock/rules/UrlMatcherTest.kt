package io.github.tukiza7_debug.vibecast.adblock.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UrlMatcherTest {
    private fun matcher(vararg rules: String): UrlMatcher = UrlMatcher(FilterParser.parse(rules.joinToString("\n")).network)

    private fun req(
        url: String,
        pageHost: String? = "example.com",
        type: ResourceType? = null,
    ) = BlockRequest(url, pageHost, type)

    @Test
    fun `host anchored rule blocks subdomains and bare host`() {
        val m = matcher("||ads.example.com^")
        assertTrue(m.decide(req("https://ads.example.com/x.gif", "news.com")) is Decision.Block)
        assertTrue(m.decide(req("https://track.ads.example.com/pixel", "news.com")) is Decision.Block)
        assertTrue(m.decide(req("https://notads.example.com/x", "news.com")) is Decision.Allow)
    }

    @Test
    fun `separator matches slash and end of url`() {
        val m = matcher("||ads.com^")
        assertTrue(m.decide(req("https://ads.com/banner", "news.com")) is Decision.Block)
        assertTrue(m.decide(req("https://ads.com", "news.com")) is Decision.Block)
        assertTrue(m.decide(req("https://ads.common.com/x", "news.com")) is Decision.Allow)
    }

    @Test
    fun `exception wins over block`() {
        val m = matcher("||ads.com^", "@@||ads.com^good/*")
        val decision = m.decide(req("https://ads.com/good/frame.js", "news.com"))
        assertTrue(decision is Decision.AllowException)
        assertTrue(m.decide(req("https://ads.com/bad/x.js", "news.com")) is Decision.Block)
    }

    @Test
    fun `type option restricts rule`() {
        val m = matcher("||cdn.com/track^${'$'}image")
        assertTrue(m.decide(req("https://cdn.com/track/pix.gif", "news.com", ResourceType.IMAGE)) is Decision.Block)
        assertTrue(m.decide(req("https://cdn.com/track/req.js", "news.com", ResourceType.SCRIPT)) is Decision.Allow)
        // Unknown type must not match a type-restricted rule.
        assertTrue(m.decide(req("https://cdn.com/track/pix.gif", "news.com", null)) is Decision.Allow)
    }

    @Test
    fun `third-party option compares hosts`() {
        val m = matcher("||tracker.io^${'$'}third-party")
        assertTrue(m.decide(req("https://tracker.io/x.js", "publisher.net")) is Decision.Block)
        assertTrue(m.decide(req("https://tracker.io/x.js", "tracker.io")) is Decision.Allow)
    }

    @Test
    fun `first-party option blocks only cross-site`() {
        val m = matcher("/analytics^${'$'}first-party")
        assertTrue(m.decide(req("https://site.com/analytics/collect", "site.com")) is Decision.Block)
        assertTrue(m.decide(req("https://site.com/analytics/collect", "other.org")) is Decision.Allow)
    }

    @Test
    fun `domain list restricts rule to page hosts`() {
        val m = matcher("||ads.net^${'$'}domain=foo.com")
        assertTrue(m.decide(req("https://ads.net/a.js", "foo.com")) is Decision.Block)
        assertTrue(m.decide(req("https://ads.net/a.js", "sub.foo.com")) is Decision.Block)
        assertTrue(m.decide(req("https://ads.net/a.js", "bar.com")) is Decision.Allow)
    }

    @Test
    fun `wildcards and start anchor match literally`() {
        val m = matcher("|http://cdn.com/banners/*.swf|")
        assertTrue(m.decide(req("http://cdn.com/banners/summer/123.swf", "x.com")) is Decision.Block)
        assertTrue(m.decide(req("http://cdn.com/banners/summer/123.swf?x=1", "x.com")) is Decision.Allow)
        assertTrue(m.decide(req("https://cdn.com/banners/123.swf", "x.com")) is Decision.Allow)
    }

    @Test
    fun `popup rules only apply to popup type`() {
        val m = matcher("||pop.net^${'$'}popup")
        assertTrue(m.decide(req("https://pop.net/x", "news.com", ResourceType.POPUP)) is Decision.Block)
        assertTrue(m.decide(req("https://pop.net/x", "news.com", ResourceType.SCRIPT)) is Decision.Allow)
    }

    @Test
    fun `generic rules match by token without regex`() {
        val m = matcher("/adserver/zoneid")
        assertTrue(m.decide(req("https://media.example.org/adserver/zoneid/123", "example.org")) is Decision.Block)
        assertTrue(m.decide(req("https://media.example.org/normal/page", "example.org")) is Decision.Allow)
    }

    @Test
    fun `real easylist snapshot blocks typical ad hosts`() {
        val fullList =
            File(System.getProperty("user.dir") ?: ".")
                .let { dir ->
                    var d = dir
                    repeat(4) {
                        val candidate = File(d, "adblock/src/main/assets/filters/easylist.txt")
                        if (candidate.exists()) return@let candidate.readText()
                        d = d.parentFile ?: d
                    }
                    javaClass.classLoader!!.getResourceAsStream("easylist-head.txt")!!
                        .bufferedReader().readText()
                }
        val parsed = FilterParser.parse(fullList)
        val m = UrlMatcher(parsed.network)
        val samples =
            listOf(
                "https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js",
                "https://ad.doubleclick.net/ddm/adj/N123",
            )
        val blocked = samples.count { m.decide(BlockRequest(it, "example.org", ResourceType.SCRIPT)) is Decision.Block }
        assertTrue("expected at least one real rule hit, got $blocked", blocked >= 1)
    }

    @Test
    fun `suffix walking stops at tld`() {
        assertEquals("b.com", UrlMatcher.nextSuffix("a.b.com"))
        assertEquals(null, UrlMatcher.nextSuffix("com"))
    }

    @Test
    fun `important rule outranks exception`() {
        val m =
            matcher(
                "||ads.example.com^" + "\$important",
                "@@||ads.example.com^",
            )
        assertTrue(m.decide(req("https://ads.example.com/x.js", "news.com")) is Decision.Block)
    }

    @Test
    fun `without important the exception wins`() {
        val m =
            matcher(
                "||ads.example.com^",
                "@@||ads.example.com^",
            )
        assertTrue(m.decide(req("https://ads.example.com/x.js", "news.com")) is Decision.AllowException)
    }

    @Test
    fun `important is combined with other options`() {
        val m =
            matcher(
                "||tracker.net^" + "\$script,important",
            )
        assertTrue(m.decide(req("https://tracker.net/t.js", "news.com", ResourceType.SCRIPT)) is Decision.Block)
        assertTrue(m.decide(req("https://tracker.net/t.png", "news.com", ResourceType.IMAGE)) is Decision.Allow)
    }
}
