package io.github.tukiza7_debug.vibecast.core

/**
 * Extension JSON builders for tests. Placeholders are substituted with the running
 * MockWebServer base URL so every step targets the local server.
 */
object TestExtensions {
    fun jsonSource(baseUrl: String): String =
        """
        {
          "schemaVersion": 1,
          "id": "test.jsonsource",
          "name": "Test JSON",
          "version": 1,
          "lang": "en",
          "baseUrl": "$baseUrl",
          "search": {
            "request": { "url": "${baseUrl}search?q={query}&page={page}" },
            "source": { "type": "json", "path": "$.response.docs[*]" },
            "fields": {
              "title": { "path": "$.title" },
              "url": { "path": "$.identifier", "template": "${baseUrl}details/{value}" },
              "cover": { "path": "$.identifier", "template": "${baseUrl}img/{value}.jpg" }
            }
          },
          "detail": {
            "request": { "url": "{url}" },
            "source": { "type": "json" },
            "fields": {
              "title": { "path": "$.metadata.title" },
              "synopsis": { "path": "$.metadata.description" }
            }
          },
          "episodes": {
            "request": { "url": "{url}" },
            "source": { "type": "json", "path": "$.files[*]", "filterField": "format", "filterEquals": "MPEG4" },
            "fields": {
              "name": { "path": "$.name" },
              "url": { "path": "$.name", "template": "${baseUrl}download/{value2}/{value}", "value2": { "rootPath": "$.metadata.identifier" } }
            }
          },
          "video": {
            "request": { "url": "{url}" },
            "strategy": "direct",
            "quality": { "template": "Original" }
          }
        }
        """.trimIndent()

    fun htmlSource(baseUrl: String): String =
        """
        {
          "schemaVersion": 1,
          "id": "test.htmlsource",
          "name": "Test HTML",
          "version": 1,
          "lang": "en",
          "baseUrl": "$baseUrl",
          "search": {
            "request": { "url": "${baseUrl}listing?page={page}" },
            "source": { "type": "html", "selector": ".listing .card" },
            "fields": {
              "title": { "selector": ".card-title" },
              "url": { "selector": ".card-link", "attribute": "href" },
              "cover": { "selector": ".thumb", "attribute": "src" }
            }
          },
          "detail": {
            "request": { "url": "{url}" },
            "source": { "type": "html" },
            "fields": {
              "title": { "selector": ".detail-title" },
              "synopsis": { "selector": ".synopsis" },
              "cover": { "selector": ".poster", "attribute": "src" }
            }
          },
          "episodes": {
            "request": { "url": "{url}" },
            "source": { "type": "html", "selector": ".episode" },
            "fields": {
              "name": { "selector": ".ep-name" },
              "number": { "selector": ".ep-num" },
              "url": { "selector": ".ep-link", "attribute": "href" }
            }
          },
          "video": {
            "request": { "url": "{url}" },
            "strategy": "regex",
            "pattern": "file\\s*:\\s*\"([^\"]+\\.m3u8)\""
          }
        }
        """.trimIndent()

    fun iframeSource(baseUrl: String): String =
        """
        {
          "schemaVersion": 1,
          "id": "test.iframesource",
          "name": "Test Iframe",
          "version": 1,
          "lang": "en",
          "baseUrl": "$baseUrl",
          "search": {
            "request": { "url": "${baseUrl}listing?page={page}" },
            "source": { "type": "html", "selector": ".listing .card" },
            "fields": { "title": { "selector": ".card-title" }, "url": { "selector": ".card-link", "attribute": "href" } }
          },
          "detail": {
            "request": { "url": "{url}" },
            "source": { "type": "html" },
            "fields": { "title": { "selector": ".detail-title" } }
          },
          "episodes": {
            "request": { "url": "{url}" },
            "source": { "type": "html", "selector": ".episode" },
            "fields": { "url": { "selector": ".ep-link", "attribute": "href" } }
          },
          "video": {
            "request": { "url": "{url}" },
            "strategy": "iframe",
            "selector": "#streamframe",
            "pattern": "file\\s*:\\s*\"([^\"]+\\.m3u8)\""
          }
        }
        """.trimIndent()

    /**
     * Extension whose video step uses the documented html strategy. The search,
     * detail and episodes steps are identical to [htmlSource]. [selector] and
     * [pattern] are inserted verbatim, so callers must pass valid JSON text.
     */
    fun htmlStrategySource(
        baseUrl: String,
        selector: String? = null,
        pattern: String? = null,
    ): String =
        """
        {
          "schemaVersion": 1,
          "id": "test.htmlstrategysource",
          "name": "Test HTML Strategy",
          "version": 1,
          "lang": "en",
          "baseUrl": "$baseUrl",
          "search": {
            "request": { "url": "${baseUrl}listing?page={page}" },
            "source": { "type": "html", "selector": ".listing .card" },
            "fields": {
              "title": { "selector": ".card-title" },
              "url": { "selector": ".card-link", "attribute": "href" },
              "cover": { "selector": ".thumb", "attribute": "src" }
            }
          },
          "detail": {
            "request": { "url": "{url}" },
            "source": { "type": "html" },
            "fields": {
              "title": { "selector": ".detail-title" },
              "synopsis": { "selector": ".synopsis" },
              "cover": { "selector": ".poster", "attribute": "src" }
            }
          },
          "episodes": {
            "request": { "url": "{url}" },
            "source": { "type": "html", "selector": ".episode" },
            "fields": {
              "name": { "selector": ".ep-name" },
              "number": { "selector": ".ep-num" },
              "url": { "selector": ".ep-link", "attribute": "href" }
            }
          },
          "video": {
            "request": { "url": "{url}" },
            "strategy": "html"${videoOptions(selector, pattern)}
          }
        }
        """.trimIndent()

    private fun videoOptions(
        selector: String?,
        pattern: String?,
    ): String =
        buildString {
            selector?.let {
                append(",\n            \"selector\": \"")
                append(it)
                append("\"")
            }
            pattern?.let {
                append(",\n            \"pattern\": \"")
                append(it)
                append("\"")
            }
        }
}
