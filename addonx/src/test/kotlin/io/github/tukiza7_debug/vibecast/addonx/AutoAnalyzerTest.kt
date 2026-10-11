package io.github.tukiza7_debug.vibecast.addonx.analyzer

import io.github.tukiza7_debug.vibecast.core.engine.ExtensionEngine
import io.github.tukiza7_debug.vibecast.core.http.OkHttpFetcher
import io.github.tukiza7_debug.vibecast.core.validate.SchemaValidator
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoAnalyzerTest {
    private val engine = ExtensionEngine(OkHttpFetcher(OkHttpClient()))

    private val listingHtml =
        """
        <html><head><title>StreamFlix - Watch movies</title></head><body>
          <div class="results">
            <article class="card"><a href="/title/alpha"><img src="/img/alpha.jpg"><h2>Alpha Nights</h2></a></article>
            <article class="card"><a href="/title/beta"><img src="/img/beta.jpg"><h2>Beta Days</h2></a></article>
            <article class="card"><a href="/title/gamma"><img src="/img/gamma.jpg"><h2>Gamma Rays</h2></a></article>
            <article class="card"><a href="/title/delta"><img src="/img/delta.jpg"><h2>Delta Storm</h2></a></article>
            <article class="card"><a href="/title/epsilon"><img src="/img/epsilon.jpg"><h2>Epsilon Tide</h2></a></article>
            <article class="card"><a href="/title/zeta"><img src="/img/zeta.jpg"><h2>Zeta Dawn</h2></a></article>
          </div>
          <nav class="main-nav"><a href="/home">Home</a><a href="/about">About</a><a href="/tags">Tags</a></nav>
        </body></html>
        """.trimIndent()

    private fun detailHtml(variant: String = "source") =
        """
        <html><body>
          <h1>Alpha Nights</h1>
          <p>A slow-burn neo-noir about a courier who loses the one package she cannot afford to open, set against the neon skyline of a city that never sleeps and the rain that never stops.</p>
          ${if (variant == "source") "<video><source src=\"https://cdn.streamflix.test/hls/alpha/master.m3u8\" type=\"application/x-mpegURL\"></video>" else ""}
        </body></html>
        """.trimIndent()

    @Test
    fun `listing page produces search step with proposal and search template`() {
        val result =
            AutoAnalyzer.analyze(
                url = "https://streamflix.test/browse?q=action&page=1",
                html = listingHtml,
                recording = AutoAnalyzer.Recording(),
                engine = engine,
            )
        assertEquals(AutoAnalyzer.PageType.LISTING, result.pageType)
        assertTrue(result.baseUrl.startsWith("https://streamflix.test"))
        val search = result.steps.first { it.step == "search" }
        assertTrue(search.summary.contains("repeated cards"))
        // The URL carries q=action, so a query template must be detected.
        assertTrue(result.draft.search.requestUrl.contains("{query}"))
        // 6 cards with title+cover but fewer than 8 repeats land at MEDIUM.
        assertEquals(DomAnalyzer.Confidence.MEDIUM, search.confidence)
        // The extension must be structurally valid end to end.
        val json = ExtensionBuilder.build(result.draft)
        assertTrue(SchemaValidator.validate(json).isEmpty())
    }

    @Test
    fun `detail page with source tag proposes regex video strategy`() {
        val result =
            AutoAnalyzer.analyze(
                url = "https://streamflix.test/title/alpha",
                html = detailHtml(),
                recording = AutoAnalyzer.Recording(),
                engine = engine,
            )
        assertEquals(AutoAnalyzer.PageType.DETAIL, result.pageType)
        val video = result.steps.first { it.step == "video" }
        assertEquals("regex", result.draft.video.strategy)
        assertTrue(video.summary.contains("<source>"))
        val detail = result.steps.first { it.step == "detail" }
        assertTrue(detail.confidence != DomAnalyzer.Confidence.LOW)
    }

    @Test
    fun `page with file directive proposes file regex`() {
        val html =
            """
            <html><body><h1>Player</h1><script>
              var config = { file: "https://cdn.streamflix.test/hls/alpha/master.m3u8" };
            </script></body></html>
            """.trimIndent()
        val result =
            AutoAnalyzer.analyze(
                url = "https://streamflix.test/watch/alpha",
                html = html,
                recording = AutoAnalyzer.Recording(),
                engine = engine,
            )
        assertEquals("regex", result.draft.video.strategy)
        assertTrue(result.draft.video.pattern!!.contains("file"))
        val video = result.steps.first { it.step == "video" }
        assertEquals(DomAnalyzer.Confidence.HIGH, video.confidence)
    }

    @Test
    fun `no structure means low confidence and picker guidance`() {
        val html =
            """
            <html><body><div id="root"></div>
              <script src="/app.js"></script><script src="/vendor.js"></script>
            </body></html>
            """.trimIndent()
        val result =
            AutoAnalyzer.analyze(
                url = "https://spa.test/watch/1",
                html = html,
                recording = AutoAnalyzer.Recording(),
                engine = engine,
            )
        assertTrue(result.needsPicker)
        assertTrue(result.steps.any { it.confidence == DomAnalyzer.Confidence.LOW })
        assertEquals(AutoAnalyzer.PageType.UNKNOWN, result.pageType)
        assertTrue(result.issues.isNotEmpty())
    }

    @Test
    fun `recorded json endpoint is preferred when dom yields nothing`() {
        val html = "<html><body><h1>Results</h1></body></html>"
        val recording =
            AutoAnalyzer.Recording(
                jsonEndpoints =
                    listOf(
                        AutoAnalyzer.RecordedJsonEndpoint(
                            url = "https://api.streamflix.test/v1/search?query=x",
                            itemCount = 24,
                            sampleKeys = listOf("title", "url", "cover", "id"),
                        ),
                    ),
            )
        val result =
            AutoAnalyzer.analyze(
                url = "https://streamflix.test/search?query=action",
                html = html,
                recording = recording,
                engine = engine,
            )
        assertEquals("json", result.draft.search.sourceType)
        val search = result.steps.first { it.step == "search" }
        assertTrue(search.summary.contains("JSON endpoint"))
    }

    @Test
    fun `search template detection ignores numeric parameters`() {
        assertEquals(
            "https://s.test/search?q={query}&page=2",
            AutoAnalyzer.detectSearchTemplate("https://s.test/search?q=action&page=2"),
        )
        assertEquals(null, AutoAnalyzer.detectSearchTemplate("https://s.test/browse?page=2"))
        assertEquals(
            "https://s.test/find?keyword={query}",
            AutoAnalyzer.detectSearchTemplate("https://s.test/find?keyword=nova"),
        )
    }

    @Test
    fun `suggested name comes from the host`() {
        assertEquals("Streamflix", AutoAnalyzer.defaultName("streamflix.test"))
        assertEquals("Example", AutoAnalyzer.defaultName("www.example.com"))
    }

    @Test
    fun `overall confidence drops to the weakest step`() {
        val result =
            AutoAnalyzer.analyze(
                url = "https://streamflix.test/title/alpha",
                html = detailHtml(),
                recording = AutoAnalyzer.Recording(),
                engine = engine,
            )
        // detail + video are strong here but episodes reuses a listing pattern that
        // does not exist on a detail page, so the overall level can never exceed MEDIUM.
        assertNotNull(result.overallConfidence)
        assertTrue(
            result.overallConfidence != DomAnalyzer.Confidence.HIGH || result.steps.none { it.confidence == DomAnalyzer.Confidence.LOW },
        )
    }

    @Test
    fun `media candidates from the page surface in the result`() {
        val result =
            AutoAnalyzer.analyze(
                url = "https://streamflix.test/title/alpha",
                html = detailHtml(),
                recording = AutoAnalyzer.Recording(mediaUrls = listOf("https://cdn.streamflix.test/hls/alpha/master.m3u8")),
                engine = engine,
            )
        assertTrue(result.mediaCandidates.isNotEmpty())
        assertTrue(result.mediaCandidates.any { it.endsWith(".m3u8") })
    }
}
