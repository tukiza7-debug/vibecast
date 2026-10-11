package io.github.tukiza7_debug.vibecast.core.engine

import io.github.tukiza7_debug.vibecast.core.TestExtensions
import io.github.tukiza7_debug.vibecast.core.http.OkHttpFetcher
import io.github.tukiza7_debug.vibecast.core.model.OptionalList
import io.github.tukiza7_debug.vibecast.core.validate.SchemaValidator
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Optional steps (`latest`, `popular`) must report NotPresent instead of throwing
 * when the extension does not define them, and must still surface runtime failures.
 */
class EngineOptionalStepsTest {
    private lateinit var server: MockWebServer
    private lateinit var engine: ExtensionEngine

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        engine = ExtensionEngine(OkHttpFetcher(OkHttpClient()))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `latestOptional returns NotPresent when step missing`() {
        val ext = minimalExtension()
        val result = engine.latestOptional(ext, 1)
        assertTrue(result is OptionalList.NotPresent)
        assertEquals(null, result.itemsOrNull)
    }

    @Test
    fun `popularOptional returns NotPresent when step missing`() {
        val ext = minimalExtension()
        val result = engine.popularOptional(ext, 1)
        assertTrue(result is OptionalList.NotPresent)
    }

    @Test
    fun `latestOptional returns Present with items when defined`() {
        val ext = SchemaValidator.decodeAndValidate(TestExtensions.jsonSource(server.url("/").toString()))
        // add a latest step equal to search but with a page-only request
        val withLatest =
            ext.copy(
                latest = ext.search.copy(request = ext.search.request.copy(url = "${server.url("/")}listing?page={page}")),
            )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"response":{"docs":[{"title":"A","identifier":"a"}]}}"""),
        )
        val result = engine.latestOptional(withLatest, 1)
        assertTrue(result is OptionalList.Present)
        assertEquals(1, result.itemsOrNull?.size)
        assertEquals("A", result.itemsOrNull?.first()?.title)
    }

    @Test
    fun `throwing variant still throws when step missing`() {
        val ext = minimalExtension()
        val thrown =
            try {
                engine.latest(ext, 1)
                null
            } catch (e: EngineException) {
                e
            }
        assertTrue(thrown != null)
        assertTrue(thrown!!.message!!.contains("latest"))
    }

    /** Minimal valid extension with only the required steps and no network use here. */
    private fun minimalExtension() =
        SchemaValidator.decodeAndValidate(
            """
            {
              "schemaVersion": 1,
              "id": "test.minimal",
              "name": "Minimal",
              "version": 1,
              "lang": "en",
              "baseUrl": "https://example.com",
              "search": {
                "request": { "url": "https://example.com/s?q={query}" },
                "source": { "type": "json", "path": "$.items[*]" },
                "fields": { "title": { "path": "$.t" }, "url": { "path": "$.u" } }
              },
              "detail": {
                "request": { "url": "{url}" },
                "source": { "type": "json" },
                "fields": { "title": { "path": "$.t" } }
              },
              "episodes": {
                "request": { "url": "{url}" },
                "source": { "type": "json", "path": "$.eps[*]" },
                "fields": { "name": { "path": "$.n" }, "url": { "path": "$.u" } }
              },
              "video": { "request": { "url": "{url}" }, "strategy": "direct" }
            }
            """.trimIndent(),
        )
}
