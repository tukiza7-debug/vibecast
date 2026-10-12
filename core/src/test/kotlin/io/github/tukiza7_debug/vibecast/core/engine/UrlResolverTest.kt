package io.github.tukiza7_debug.vibecast.core.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class UrlResolverTest {
    private val base = "https://example.net/videos/listing"

    @Test
    fun `absolute urls pass through`() {
        assertEquals("http://other.org/a", UrlResolver.absolutize("http://other.org/a", base))
        assertEquals("https://example.net/x", UrlResolver.absolutize("https://example.net/x", base))
    }

    @Test
    fun `protocol relative inherits base scheme`() {
        assertEquals("https://cdn.example.net/v.mp4", UrlResolver.absolutize("//cdn.example.net/v.mp4", base))
    }

    @Test
    fun `root relative joins base authority`() {
        assertEquals("https://example.net/watch/1", UrlResolver.absolutize("/watch/1", base))
    }

    @Test
    fun `query only keeps base path`() {
        assertEquals("https://example.net/videos/listing?page=2", UrlResolver.absolutize("?page=2", base))
    }

    @Test
    fun `dot segments are normalized`() {
        assertEquals("https://example.net/a/c", UrlResolver.absolutize("/a/b/../c", base))
        assertEquals("https://example.net/videos/c", UrlResolver.absolutize("./c", base))
        assertEquals("https://example.net/c", UrlResolver.absolutize("../c", base))
    }

    @Test
    fun `template variables are substituted and encoded`() {
        val resolved =
            UrlResolver.resolveTemplate(
                "https://example.net/search?q={query}&page={page}",
                base,
                mapOf("query" to "star wars", "page" to "2"),
            )
        assertEquals("https://example.net/search?q=star+wars&page=2", resolved)
    }

    @Test
    fun `empty url falls back to base`() {
        assertEquals(base, UrlResolver.absolutize("", base))
    }

    @Test
    fun `whitespace is trimmed`() {
        assertEquals("https://example.net/watch/1", UrlResolver.absolutize("  /watch/1  ", base))
    }

    @Test
    fun `hostOf lowercases the host`() {
        assertEquals("example.net", UrlResolver.hostOf("https://Example.NET/path"))
        assertEquals(null, UrlResolver.hostOf("not a url"))
    }
}
