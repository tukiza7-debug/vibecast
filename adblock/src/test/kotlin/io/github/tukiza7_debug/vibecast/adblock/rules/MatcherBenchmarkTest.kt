package io.github.tukiza7_debug.vibecast.adblock.rules

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Proves the indexing strategy keeps average per-request decision cost under 1 ms
 * with the full bundled EasyList loaded. Runs on the JVM with the real snapshot.
 */
class MatcherBenchmarkTest {
    private fun assetFile(name: String): File {
        var dir = File(System.getProperty("user.dir") ?: ".")
        repeat(4) {
            val candidate = File(dir, "adblock/src/main/assets/filters/$name")
            if (candidate.exists()) return candidate
            dir = dir.parentFile ?: dir
        }
        error("asset not found: $name")
    }

    private val urls =
        (0 until 20_000).map { i ->
            val kind = i % 5
            when (kind) {
                0 -> "https://www.example$i.org/page/$i/index.html"
                1 -> "https://static.example$i.net/assets/app-$i.js"
                2 -> "https://img.example$i.com/photos/$i/pic.jpg"
                3 -> "https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js?slot=$i"
                else -> "https://cdn.example$i.io/video/$i/manifest.m3u8"
            }
        }

    @Test
    fun `average decision under 1ms with full easylist`() {
        val text = assetFile("easylist.txt").readText()
        val parsed = FilterParser.parse(text)
        val matcher = UrlMatcher(parsed.network)

        // Warm-up.
        urls.take(200).forEach { matcher.decide(BlockRequest(it, "example.org", ResourceType.SCRIPT)) }

        val start = System.nanoTime()
        urls.forEach { matcher.decide(BlockRequest(it, "example.org", ResourceType.SCRIPT)) }
        val elapsedNanos = System.nanoTime() - start
        val avgMicros = elapsedNanos / urls.size / 1000

        println("MATCHER BENCHMARK: ${parsed.network.size} rules, ${urls.size} requests, avg $avgMicros us/request")
        assertTrue("average ${avgMicros}us per request exceeds 1ms", avgMicros < 1000)
    }

    @Test
    fun `average decision under 1ms with easylist plus easyprivacy`() {
        val easyList = FilterParser.parse(assetFile("easylist.txt").readText())
        val easyPrivacy = FilterParser.parse(assetFile("easyprivacy.txt").readText())
        val matcher = UrlMatcher(easyList.network + easyPrivacy.network)

        urls.take(200).forEach { matcher.decide(BlockRequest(it, "example.org", ResourceType.OTHER)) }

        val start = System.nanoTime()
        urls.forEach { matcher.decide(BlockRequest(it, "example.org", ResourceType.OTHER)) }
        val elapsedNanos = System.nanoTime() - start
        val avgMicros = elapsedNanos / urls.size / 1000

        println("MATCHER BENCHMARK COMBINED: ${easyList.network.size + easyPrivacy.network.size} rules, avg $avgMicros us/request")
        assertTrue("average ${avgMicros}us per request exceeds 1ms", avgMicros < 1000)
    }
}
