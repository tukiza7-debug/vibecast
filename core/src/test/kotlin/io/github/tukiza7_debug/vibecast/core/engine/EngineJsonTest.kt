package io.github.tukiza7_debug.vibecast.core.engine

import io.github.tukiza7_debug.vibecast.core.TestExtensions
import io.github.tukiza7_debug.vibecast.core.http.OkHttpFetcher
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EngineJsonTest {
    private lateinit var server: MockWebServer
    private lateinit var engine: ExtensionEngine
    private lateinit var extension: io.github.tukiza7_debug.vibecast.core.model.Extension

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        engine = ExtensionEngine(OkHttpFetcher(OkHttpClient()))
        val json = TestExtensions.jsonSource(server.url("/").toString())
        extension =
            io.github.tukiza7_debug.vibecast.core.validate.SchemaValidator
                .decodeAndValidate(json)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun enqueue(
        body: String,
        contentType: String = "application/json",
    ) {
        server.enqueue(
            MockResponse().setResponseCode(200).setHeader("Content-Type", contentType).setBody(body),
        )
    }

    @Test
    fun `search maps json docs to items`() {
        enqueue(io.github.tukiza7_debug.vibecast.core.Fixtures.read("fixtures/search-response.json"))
        val items = engine.search(extension, "night", 1)
        assertEquals(3, items.size)
        assertEquals("Alpha Night", items[0].title)
        assertEquals("${server.url("/")}details/alpha-night", items[0].url)
        assertEquals("${server.url("/")}img/alpha-night.jpg", items[0].cover)
    }

    @Test
    fun `detail reads metadata object`() {
        enqueue(io.github.tukiza7_debug.vibecast.core.Fixtures.read("fixtures/metadata.json"))
        val detail = engine.detail(extension, "${server.url("/")}metadata/alpha-night")
        assertEquals("Alpha Night", detail.title)
        assertEquals("A public-domain noir from the fixture archive.", detail.synopsis)
    }

    @Test
    fun `episodes filter by format and build download urls with root value`() {
        enqueue(io.github.tukiza7_debug.vibecast.core.Fixtures.read("fixtures/metadata.json"))
        val episodes = engine.episodes(extension, "${server.url("/")}metadata/alpha-night")
        assertEquals(2, episodes.size)
        assertEquals("alpha-night.mp4", episodes[0].name)
        assertEquals("${server.url("/")}download/alpha-night/alpha-night.mp4", episodes[0].url)
        assertEquals("${server.url("/")}download/alpha-night/extras.mp4", episodes[1].url)
    }

    @Test
    fun `direct video probes with HEAD and reads mime`() {
        server.enqueue(MockResponse().setResponseCode(200).setHeader("Content-Type", "video/mp4"))
        val stream = engine.video(extension, "${server.url("/")}download/alpha-night/alpha-night.mp4")
        assertEquals("${server.url("/")}download/alpha-night/alpha-night.mp4", stream.url)
        assertEquals("video/mp4", stream.mimeType)
        assertEquals("Original", stream.quality)
        assertEquals("HEAD", server.takeRequest().method)
    }

    @Test
    fun `direct video without extension falls back to probe mime and tolerates 405`() {
        server.enqueue(MockResponse().setResponseCode(405))
        val stream = engine.video(extension, "${server.url("/")}media/alpha-night")
        assertNull(stream.mimeType)
    }

    @Test(expected = EngineException::class)
    fun `direct video fails explicitly on 404 probe`() {
        server.enqueue(MockResponse().setResponseCode(404))
        engine.video(extension, "${server.url("/")}media/gone.mp4")
    }

    @Test(expected = EngineException::class)
    fun `malformed json names the step`() {
        enqueue("this is not json")
        engine.search(extension, "night", 1)
    }

    @Test
    fun `empty json array yields empty results`() {
        enqueue("""{"response":{"numFound":0,"docs":[]}}""")
        val items = engine.search(extension, "nothing", 1)
        assertTrue(items.isEmpty())
    }

    @Test(expected = EngineException::class)
    fun `episodes with no matching items fail explicitly`() {
        enqueue("""{"metadata":{"identifier":"x"},"files":[]}""")
        engine.episodes(extension, "${server.url("/")}metadata/x")
    }

    @Test(expected = EngineException::class)
    fun `missing required field names step and field`() {
        enqueue("""{"response":{"docs":[{"identifier":"a"}]}}""")
        engine.search(extension, "a", 1)
    }
}
