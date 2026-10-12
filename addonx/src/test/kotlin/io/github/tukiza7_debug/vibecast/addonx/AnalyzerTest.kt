package io.github.tukiza7_debug.vibecast.addonx.analyzer

import io.github.tukiza7_debug.vibecast.core.codes.UnixCodec
import io.github.tukiza7_debug.vibecast.core.validate.SchemaValidator
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DomAnalyzerTest {
    private val listingHtml =
        """
        <html><body>
          <div class="listing">
            <article class="card"><a class="card-link" href="/t/a"><img src="/i/a.jpg"><h2 class="card-title">A</h2></a></article>
            <article class="card"><a class="card-link" href="/t/b"><img src="/i/b.jpg"><h2 class="card-title">B</h2></a></article>
            <article class="card"><a class="card-link" href="/t/c"><img src="/i/c.jpg"><h2 class="card-title">C</h2></a></article>
            <article class="card"><a class="card-link" href="/t/d"><img src="/i/d.jpg"><h2 class="card-title">D</h2></a></article>
            <article class="card"><a class="card-link" href="/t/e"><img src="/i/e.jpg"><h2 class="card-title">E</h2></a></article>
            <article class="card"><a class="card-link" href="/t/f"><img src="/i/f.jpg"><h2 class="card-title">F</h2></a></article>
          </div>
          <a rel="next" href="/listing?page=2">Next</a>
        </body></html>
        """.trimIndent()

    @Test
    fun `proposes item pattern with title link and cover`() {
        val analysis = DomAnalyzer.analyze(listingHtml, "https://site.test/listing")
        val best = analysis.proposals.firstOrNull()
        assertTrue(best != null)
        assertEquals(6, best!!.itemCount)
        assertTrue(best.itemSelector.contains("article.card"))
        assertEquals("h2", best.titleSelector)
        assertTrue(best.linkSelector != null)
        assertTrue(best.coverSelector != null)
    }

    @Test
    fun `proposes pagination from rel next`() {
        val analysis = DomAnalyzer.analyze(listingHtml, "https://site.test/listing")
        val pagination = analysis.pagination.firstOrNull()
        assertTrue(pagination != null)
        assertEquals("https://site.test/listing?page=2", pagination!!.sampleUrl)
        assertEquals(DomAnalyzer.Confidence.HIGH, pagination.confidence)
    }

    @Test
    fun `detail fields include title and description`() {
        val html =
            """
            <html><body>
              <h1 class="detail-title">Some Title</h1>
              <p>A long synopsis paragraph that comfortably exceeds one hundred and twenty characters in total length, which the analyzer needs before it will propose this paragraph as the synopsis field for the detail step of the extension.</p>
            </body></html>
            """.trimIndent()
        val fields = DomAnalyzer.analyzeDetail(html, "https://site.test/detail")
        assertTrue(fields.containsKey("title"))
        assertTrue(fields.containsKey("synopsis"))
    }

    @Test
    fun `search template replaces the query parameter`() {
        val template = DomAnalyzer.buildSearchTemplate("https://site.test/search?q=hello&page=1", "hello")
        assertEquals("https://site.test/search?q={query}&page=1", template)
    }

    @Test
    fun `template without query params is rejected`() {
        assertEquals(null, DomAnalyzer.buildSearchTemplate("https://site.test/listing", "hello"))
    }
}

class CssSelectorBuilderTest {
    @Test
    fun `prefers id when present`() {
        val selector =
            CssSelectorBuilder.build(
                listOf(
                    CssSelectorBuilder.NodeInfo("div", "root", listOf(), 1),
                    CssSelectorBuilder.NodeInfo("h1", "title-id", listOf("big"), 1),
                ),
                siblingCount = 3,
            )
        assertEquals("#title-id", selector)
    }

    @Test
    fun `builds nth-of-type chain without id`() {
        val selector =
            CssSelectorBuilder.build(
                listOf(
                    CssSelectorBuilder.NodeInfo("div", null, listOf("wrap"), 1),
                    CssSelectorBuilder.NodeInfo("li", null, listOf("item"), 3),
                ),
                siblingCount = 5,
            )
        assertEquals("div.wrap:nth-of-type(1) > li.item:nth-of-type(3)", selector)
    }

    @Test
    fun `element signature is stable across order`() {
        val doc = Jsoup.parse("""<div class="b a"></div><div class="a b"></div>""")
        val (first, second) = doc.body().children().map { CssSelectorBuilder.elementSignature(it) }
        assertEquals(first, second)
    }
}

class ExtensionBuilderTest {
    @Test
    fun `built extension passes schema validation`() {
        val draft =
            ExtensionBuilder.Draft(
                id = "test.example.built",
                name = "Built",
                version = 1,
                lang = "en",
                baseUrl = "https://site.test",
                headers = emptyMap(),
                search =
                    ExtensionBuilder.StepDraft(
                        name = "search",
                        requestUrl = "https://site.test/search?q={query}&page={page}",
                        sourceType = "html",
                        itemSelector = ".listing article.card",
                        jsonPath = null,
                        fields =
                            linkedMapOf(
                                "title" to ExtensionBuilder.FieldDraft(selector = "h2.card-title"),
                                "url" to ExtensionBuilder.FieldDraft(selector = "a.card-link", attribute = "href"),
                                "cover" to ExtensionBuilder.FieldDraft(selector = "img", attribute = "src"),
                            ),
                    ),
                latest = null,
                detail =
                    ExtensionBuilder.StepDraft(
                        name = "detail",
                        requestUrl = "{url}",
                        sourceType = "html",
                        itemSelector = null,
                        jsonPath = null,
                        fields =
                            linkedMapOf(
                                "title" to ExtensionBuilder.FieldDraft(selector = "h1.detail-title"),
                            ),
                    ),
                episodes =
                    ExtensionBuilder.StepDraft(
                        name = "episodes",
                        requestUrl = "{url}",
                        sourceType = "html",
                        itemSelector = ".episode",
                        jsonPath = null,
                        fields =
                            linkedMapOf(
                                "name" to ExtensionBuilder.FieldDraft(selector = ".ep-name"),
                                "url" to ExtensionBuilder.FieldDraft(selector = "a.ep-link", attribute = "href"),
                            ),
                    ),
                video =
                    ExtensionBuilder.VideoDraft(
                        requestUrl = "{url}",
                        strategy = "regex",
                        pattern = "file\\s*:\\s*\"([^\"]+\\.m3u8)\"",
                    ),
            )
        val json = ExtensionBuilder.build(draft)
        val issues = SchemaValidator.validate(json)
        assertEquals(emptyList<String>(), issues)
        val code = UnixCodec.encode(json)
        assertEquals(json, UnixCodec.decode(code))
    }

    @Test
    fun `id derived from host is reverse domain`() {
        assertEquals("org.archive", ExtensionBuilder.idForHost("https://archive.org/details/x"))
    }
}
