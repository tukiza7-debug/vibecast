package io.github.tukiza7_debug.vibecast.core.engine

import io.github.tukiza7_debug.vibecast.core.Fixtures
import io.github.tukiza7_debug.vibecast.core.TestExtensions
import io.github.tukiza7_debug.vibecast.core.http.HostFilter
import io.github.tukiza7_debug.vibecast.core.http.OkHttpFetcher
import io.github.tukiza7_debug.vibecast.core.validate.SchemaValidator
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EngineHtmlTest {

    private lateinit var server: MockWebServer
    private lateinit var engine: ExtensionEngine
    private lateinit var extension: io.github.tukiza7_debug.vibecast.core.model.Extension
    private lateinit var iframeExtension: io.github.tukiza7_debug.vibecast.core.model.Extension

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        engine = ExtensionEngine(OkHttpFetcher(OkHttpClient()))
        val base = server.url("/").toString()
        extension = SchemaValidator.decodeAndValidate(TestExtensions.htmlSource(base))
        iframeExtension = SchemaValidator.decodeAndValidate(TestExtensions.iframeSource(base))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun enqueueHtml(body: String) {
        server.enqueue(
            MockResponse().setResponseCode(200).setHeader("Content-Type", "text/html").setBody(body),
        )
    }

    @Test
    fun `search resolves relative hrefs and srcs against the page`() {
        enqueueHtml(Fixtures.read("fixtures/search-page.html"))
        val items = engine.search(extension, "ignored", 2)
        assertEquals(3, items.size)
        assertEquals("Alpha Night", items[0].title)
        assertEquals("${server.url("/")}title/alpha-night", items[0].url)
        assertEquals("${server.url("/")}img/alpha.jpg", items[0].cover)
    }

    @Test
    fun `detail extracts title synopsis and cover`() {
        enqueueHtml(Fixtures.read("fixtures/detail-page.html"))
        val detail = engine.detail(extension, "${server.url("/")}title/alpha-night")
        assertEquals("Alpha Night", detail.title)
        assertEquals("A public-domain noir from the fixture archive.", detail.synopsis)
        assertEquals("${server.url("/")}img/alpha-big.jpg", detail.cover)
    }

    @Test
    fun `episodes map names numbers and urls`() {
        enqueueHtml(Fixtures.read("fixtures/detail-page.html"))
        val episodes = engine.episodes(extension, "${server.url("/")}title/alpha-night")
        assertEquals(2, episodes.size)
        assertEquals("Pilot", episodes[0].name)
        assertEquals(1, episodes[0].number)
        assertEquals("${server.url("/")}play/alpha-1", episodes[0].url)
    }

    @Test
    fun `regex strategy extracts stream from script text`() {
        enqueueHtml(Fixtures.read("fixtures/detail-page.html"))
        val stream = engine.video(extension, "${server.url("/")}play/alpha-1")
        assertEquals("https://cdn.fixture.test/hls/alpha/index.m3u8", stream.url)
        assertEquals("application/x-mpegurl", stream.mimeType)
    }

    @Test
    fun `iframe strategy follows the frame then extracts with pattern`() {
        enqueueHtml(Fixtures.read("fixtures/iframe-page.html"))
        enqueueHtml(Fixtures.read("fixtures/embed-page.html"))
        val stream = engine.video(iframeExtension, "${server.url("/")}play/alpha-1")
        assertEquals("https://cdn.embed.test/hls/alpha/master.m3u8", stream.url)
        assertEquals("${server.url("/")}embed/alpha", stream.headers["Referer"])
        assertEquals("GET", server.takeRequest().method)
        assertEquals("GET", server.takeRequest().method)
    }

    @Test
    fun `html selector matching nothing yields empty search results`() {
        enqueueHtml("<html><body><p>no results here</p></body></html>")
        assertTrue(engine.search(extension, "night", 1).isEmpty())
    }

    @Test(expected = EngineException::class)
    fun `episodes with zero matches fail explicitly`() {
        enqueueHtml("<html><body><p>no episodes</p></body></html>")
        engine.episodes(extension, "${server.url("/")}title/alpha-night")
    }

    @Test(expected = EngineException::class)
    fun `regex strategy fails with explicit reason when pattern misses`() {
        enqueueHtml("<html><body>nothing here</body></html>")
        engine.video(extension, "${server.url("/")}play/alpha-1")
    }

    @Test(expected = EngineException::class)
    fun `blocked hosts fail the step before any network call`() {
        val blockingEngine = ExtensionEngine(
            OkHttpFetcher(OkHttpClient()),
            hostFilter = object : HostFilter {
                override fun isBlocked(url: String): Boolean = true
            },
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody("should not be read"))
        blockingEngine.search(extension, "night", 1)
    }

    @Test
    fun `unknown strategy is rejected by the validator`() {
        val base = server.url("/").toString()
        val broken = TestExtensions.htmlSource(base)
            .replace("\"strategy\": \"regex\"", "\"strategy\": \"magic\"")
        val error = org.junit.Assert.assertThrows(
            io.github.tukiza7_debug.vibecast.core.codes.ExtensionValidationException::class.java,
        ) { SchemaValidator.decodeAndValidate(broken) }
        assertTrue(error.issues.any { it.startsWith("video.strategy") })
    }
}
