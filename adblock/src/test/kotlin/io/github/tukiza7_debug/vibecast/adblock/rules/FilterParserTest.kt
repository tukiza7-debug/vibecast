package io.github.tukiza7_debug.vibecast.adblock.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterParserTest {
    @Test
    fun `parses host anchored rules with separator`() {
        val parsed = FilterParser.parse("||ads.example.com^\n||tracker.net/script.js")
        assertEquals(2, parsed.network.size)
        val first = parsed.network[0]
        assertTrue(first.isHostAnchor)
        assertEquals("ads.example.com", first.hostPart)
        val second = parsed.network[1]
        assertTrue(second.segments.first() is NetworkRule.Segment.Literal)
        assertEquals("/script.js", (second.segments.first() as NetworkRule.Segment.Literal).text)
    }

    @Test
    fun `parses options`() {
        val parsed = FilterParser.parse("||ads.com^${'$'}script,third-party,domain=foo.com|~bar.com")
        val rule = parsed.network.single()
        assertEquals(setOf(ResourceType.SCRIPT), rule.types)
        assertTrue(rule.thirdPartyOnly)
        assertEquals(listOf("foo.com"), rule.includedDomains)
        assertEquals(listOf("bar.com"), rule.excludedDomains)
    }

    @Test
    fun `parses exceptions`() {
        val parsed = FilterParser.parse("@@||good.com^${'$'}image")
        val rule = parsed.network.single()
        assertTrue(rule.isException)
        assertEquals(setOf(ResourceType.IMAGE), rule.types)
    }

    @Test
    fun `parses anchors and wildcards`() {
        val parsed = FilterParser.parse("|http://start.com^*.gif|")
        val rule = parsed.network.single()
        assertTrue(rule.isStartAnchor)
        assertTrue(rule.isEndAnchor)
        assertFalse(rule.isHostAnchor)
        assertTrue(rule.segments.any { it is NetworkRule.Segment.Star })
    }

    @Test
    fun `parses cosmetic rules`() {
        val parsed =
            FilterParser.parse(
                """
                ##.ad-banner
                example.com##.promo
                foo.com,bar.com##.modal
                #@#.keep-me
                """.trimIndent(),
            )
        assertEquals(listOf(".ad-banner"), parsed.cosmeticGlobal)
        assertEquals(listOf(".promo"), parsed.cosmeticByDomain["example.com"])
        assertEquals(listOf(".modal"), parsed.cosmeticByDomain["foo.com"])
        assertEquals(listOf(".modal"), parsed.cosmeticByDomain["bar.com"])
        assertEquals(setOf(".keep-me"), parsed.cosmeticExceptions)
    }

    @Test
    fun `skips comments and unsupported pseudo classes`() {
        val parsed =
            FilterParser.parse(
                """
                ! comment
                [Adblock Plus 2.0]
                ##.ad:has(.child)
                ##.plain-ad
                """.trimIndent(),
            )
        assertEquals(listOf(".plain-ad"), parsed.cosmeticGlobal)
        assertTrue(parsed.network.isEmpty())
    }

    @Test
    fun `rules are lowercased for matching but raw text is kept`() {
        val parsed = FilterParser.parse("||ADs.Example.COM^${'$'}Script")
        val rule = parsed.network.single()
        assertEquals("ads.example.com", rule.hostPart)
        assertEquals("||ADs.Example.COM^${'$'}Script", rule.raw)
    }

    @Test
    fun `document option rules are dropped`() {
        val parsed = FilterParser.parse("@@||example.com^${'$'}document")
        assertNull(parsed.network.firstOrNull())
    }

    @Test
    fun `parses a real easylist snapshot`() {
        val text =
            javaClass.classLoader!!.getResourceAsStream("easylist-head.txt")!!
                .bufferedReader().readText()
        val parsed = FilterParser.parse(text)
        assertTrue(parsed.network.size > 30)
        val matcher = UrlMatcher(parsed.network)
        val decision =
            matcher.decide(
                BlockRequest("https://pagead2.googlesyndication.com/x", "example.org", ResourceType.SCRIPT),
            )
        assertTrue(decision is Decision.Block || decision is Decision.Allow)
    }
}
