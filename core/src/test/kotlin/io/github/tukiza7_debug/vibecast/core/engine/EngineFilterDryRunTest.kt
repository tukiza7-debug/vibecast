package io.github.tukiza7_debug.vibecast.core.engine

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

class EngineFilterDryRunTest {
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

    private fun extensionWithFilter(
        field: String,
        criterion: String,
    ): io.github.tukiza7_debug.vibecast.core.model.Extension =
        SchemaValidator.decodeAndValidate(
            """
            {
              "schemaVersion": 1,
              "id": "test.filter",
              "name": "Filter Test",
              "version": 1,
              "lang": "en",
              "baseUrl": "${server.url("/")}",
              "search": {
                "request": { "url": "${server.url("/")}list" },
                "source": { "type": "json", "path": "$.items[*]", "filterField": "$field", $criterion },
                "fields": {
                  "title": { "path": "$.name" },
                  "url": { "path": "$.href" }
                }
              },
              "detail": {
                "request": { "url": "{url}" },
                "source": { "type": "json" },
                "fields": { "title": { "path": "$.name" } }
              },
              "episodes": {
                "request": { "url": "{url}" },
                "source": { "type": "json", "path": "$.files[*]" },
                "fields": { "url": { "path": "$.href" } }
              },
              "video": { "request": { "url": "{url}" }, "strategy": "direct" }
            }
            """.trimIndent(),
        )

    private fun enqueueItems() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {"items":[
                      {"name":"Film A","kind":"movie","href":"/a.mp4"},
                      {"name":"Trailer A","kind":"trailer","href":"/a-trailer.mp4"},
                      {"name":"Film B","kind":"movie","href":"/b.mp4"}
                    ]}
                    """.trimIndent(),
                ),
        )
    }

    @Test
    fun `filterEquals keeps only exact matches`() {
        enqueueItems()
        val items = engine.search(extensionWithFilter("kind", "\"filterEquals\": \"movie\""), "q", 1)
        assertEquals(2, items.size)
        assertEquals("Film A", items[0].title)
    }

    @Test
    fun `filterSuffix matches case insensitively`() {
        enqueueItems()
        val items = engine.search(extensionWithFilter("href", "\"filterSuffix\": \".MP4\""), "q", 1)
        assertEquals(3, items.size)
    }

    @Test
    fun `filterContains matches substrings`() {
        enqueueItems()
        val items = engine.search(extensionWithFilter("kind", "\"filterContains\": \"trailer\""), "q", 1)
        assertEquals(1, items.size)
        assertEquals("Trailer A", items[0].title)
    }

    @Test
    fun `combined criteria intersect`() {
        enqueueItems()
        val extension =
            SchemaValidator.decodeAndValidate(
                """
                {
                  "schemaVersion": 1,
                  "id": "test.filter2",
                  "name": "Filter Test 2",
                  "version": 1,
                  "lang": "en",
                  "baseUrl": "${server.url("/")}",
                  "search": {
                    "request": { "url": "${server.url("/")}list" },
                    "source": {
                      "type": "json", "path": "$.items[*]",
                      "filterField": "href", "filterSuffix": ".mp4", "filterContains": "a"
                    },
                    "fields": { "title": { "path": "$.name" }, "url": { "path": "$.href" } }
                  },
                  "detail": {
                    "request": { "url": "{url}" },
                    "source": { "type": "json" },
                    "fields": { "title": { "path": "$.name" } }
                  },
                  "episodes": {
                    "request": { "url": "{url}" },
                    "source": { "type": "json", "path": "$.files[*]" },
                    "fields": { "url": { "path": "$.href" } }
                  },
                  "video": { "request": { "url": "{url}" }, "strategy": "direct" }
                }
                """.trimIndent(),
            )
        val items = engine.search(extension, "q", 1)
        assertEquals(2, items.size)
    }

    @Test
    fun `dryRun accepts a valid extension with no issues`() {
        val extension =
            SchemaValidator.decodeAndValidate(
                io.github.tukiza7_debug.vibecast.core.TestExtensions.htmlSource(server.url("/").toString()),
            )
        assertTrue(engine.dryRun(extension).isEmpty())
    }

    @Test
    fun `dryRun reports invalid selector and regex with step and field names`() {
        val extension =
            io.github.tukiza7_debug.vibecast.core.model.Extension(
                schemaVersion = 1,
                id = "test.dryrun",
                name = "Dry Run",
                version = 1,
                lang = "en",
                baseUrl = server.url("/").toString(),
                search =
                    step(
                        requestUrl = server.url("/").toString() + "s?q={query}",
                        source =
                            io.github.tukiza7_debug.vibecast.core.model.SourceSpec(
                                type = "html",
                                selector = "div.items[broken",
                            ),
                        fields =
                            mapOf(
                                "title" to
                                    io.github.tukiza7_debug.vibecast.core.model.FieldExtraction(
                                        selector = "h3",
                                        regex = "([unclosed",
                                    ),
                                "url" to io.github.tukiza7_debug.vibecast.core.model.FieldExtraction(selector = "a"),
                            ),
                    ),
                detail =
                    step(
                        requestUrl = "{url}",
                        source = io.github.tukiza7_debug.vibecast.core.model.SourceSpec(type = "html", selector = ".detail"),
                        fields = mapOf("title" to io.github.tukiza7_debug.vibecast.core.model.FieldExtraction(selector = "h1")),
                    ),
                episodes =
                    step(
                        requestUrl = "{url}",
                        source = io.github.tukiza7_debug.vibecast.core.model.SourceSpec(type = "html", selector = ".ep"),
                        fields = mapOf("url" to io.github.tukiza7_debug.vibecast.core.model.FieldExtraction(selector = "a")),
                    ),
                video =
                    io.github.tukiza7_debug.vibecast.core.model.VideoStep(
                        request = io.github.tukiza7_debug.vibecast.core.model.StepRequest(url = "{url}"),
                        strategy = "nonsense",
                    ),
            )
        val issues = engine.dryRun(extension)
        assertTrue(issues.any { it.startsWith("[search.source]") && it.contains("invalid selector") })
        assertTrue(issues.any { it.startsWith("[search.fields.title]") && it.contains("invalid regex") })
        assertTrue(issues.any { it.startsWith("[video.strategy]") && it.contains("nonsense") })
    }

    private fun step(
        requestUrl: String,
        source: io.github.tukiza7_debug.vibecast.core.model.SourceSpec,
        fields: Map<String, io.github.tukiza7_debug.vibecast.core.model.FieldExtraction>,
    ): io.github.tukiza7_debug.vibecast.core.model.ListStep =
        io.github.tukiza7_debug.vibecast.core.model.ListStep(
            request = io.github.tukiza7_debug.vibecast.core.model.StepRequest(url = requestUrl),
            source = source,
            fields = fields,
        )

    @Test
    fun `dryRun flags filterField without any criterion`() {
        val extension =
            io.github.tukiza7_debug.vibecast.core.model.Extension(
                schemaVersion = 1,
                id = "test.dryrun2",
                name = "Dry Run 2",
                version = 1,
                lang = "en",
                baseUrl = server.url("/").toString(),
                search =
                    step(
                        requestUrl = server.url("/").toString() + "s",
                        source =
                            io.github.tukiza7_debug.vibecast.core.model.SourceSpec(
                                type = "json",
                                path = "$.x[*]",
                                filterField = "kind",
                            ),
                        fields =
                            mapOf(
                                "title" to io.github.tukiza7_debug.vibecast.core.model.FieldExtraction(path = "$.n"),
                                "url" to io.github.tukiza7_debug.vibecast.core.model.FieldExtraction(path = "$.h"),
                            ),
                    ),
                detail =
                    step(
                        requestUrl = "{url}",
                        source = io.github.tukiza7_debug.vibecast.core.model.SourceSpec(type = "json"),
                        fields = mapOf("title" to io.github.tukiza7_debug.vibecast.core.model.FieldExtraction(path = "$.n")),
                    ),
                episodes =
                    step(
                        requestUrl = "{url}",
                        source =
                            io.github.tukiza7_debug.vibecast.core.model.SourceSpec(
                                type = "json",
                                path = "$.f[*]",
                            ),
                        fields = mapOf("url" to io.github.tukiza7_debug.vibecast.core.model.FieldExtraction(path = "$.h")),
                    ),
                video =
                    io.github.tukiza7_debug.vibecast.core.model.VideoStep(
                        request = io.github.tukiza7_debug.vibecast.core.model.StepRequest(url = "{url}"),
                        strategy = "direct",
                    ),
            )
        val issues = engine.dryRun(extension)
        assertTrue(issues.any { it.startsWith("[search.source]") && it.contains("filterField needs") })
    }

    @Test
    fun `http error responses carry a hint`() {
        server.enqueue(MockResponse().setResponseCode(403).setBody("denied"))
        val extension =
            SchemaValidator.decodeAndValidate(
                """
                {
                  "schemaVersion": 1,
                  "id": "test.http",
                  "name": "HTTP Test",
                  "version": 1,
                  "lang": "en",
                  "baseUrl": "${server.url("/")}",
                  "search": {
                    "request": { "url": "${server.url("/")}list" },
                    "source": { "type": "json", "path": "$.x[*]" },
                    "fields": { "title": { "path": "$.n" }, "url": { "path": "$.h" } }
                  },
                  "detail": { "request": { "url": "{url}" }, "source": { "type": "json" }, "fields": { "title": { "path": "$.n" } } },
                  "episodes": { "request": { "url": "{url}" }, "source": { "type": "json", "path": "$.f[*]" }, "fields": { "url": { "path": "$.h" } } },
                  "video": { "request": { "url": "{url}" }, "strategy": "direct" }
                }
                """.trimIndent(),
            )
        val error =
            runCatching { engine.search(extension, "q", 1) }.exceptionOrNull()
        assertTrue(error?.message?.contains("403") == true)
        assertTrue(error?.message?.contains("browser headers") == true)
    }
}
