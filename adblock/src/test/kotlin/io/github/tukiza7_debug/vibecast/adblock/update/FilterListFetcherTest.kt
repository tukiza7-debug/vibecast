package io.github.tukiza7_debug.vibecast.adblock.update

import java.io.File
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FilterListFetcherTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var fetcher: FilterListFetcher
    private lateinit var target: File

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        fetcher = FilterListFetcher(OkHttpClient())
        target = File(tmp.root, "list.txt")
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private val validList = "[Adblock Plus 2.0]\n! Title: Test\n" + "||ad${'$'}RULES.test^\n".repeat(200)

    @Test
    fun `first download writes file and returns etag`() {
        server.enqueue(MockResponse().setBody(validList).setHeader("ETag", "\"abc\""))
        val result = fetcher.updateIfChanged(server.url("/list.txt").toString(), target, null)
        assertTrue(result is FilterListFetcher.Result.Updated)
        assertEquals("\"abc\"", (result as FilterListFetcher.Result.Updated).etag)
        assertEquals(validList, target.readText())
    }

    @Test
    fun `conditional request with etag gets 304 and keeps file`() {
        target.writeText(validList)
        server.enqueue(MockResponse().setResponseCode(304))
        val result = fetcher.updateIfChanged(server.url("/list.txt").toString(), target, "\"abc\"")
        assertEquals(FilterListFetcher.Result.Unmodified, result)
        assertEquals(validList, target.readText())
        assertEquals("\"abc\"", server.takeRequest().getHeader("If-None-Match"))
    }

    @Test
    fun `invalid content is not swapped in`() {
        target.writeText(validList)
        server.enqueue(MockResponse().setBody("<html>not a filter list</html>"))
        val result = fetcher.updateIfChanged(server.url("/list.txt").toString(), target, null)
        assertTrue(result is FilterListFetcher.Result.Failed)
        assertEquals(validList, target.readText())
    }

    @Test
    fun `network failure keeps previous file`() {
        target.writeText(validList)
        server.shutdown()
        val result = fetcher.updateIfChanged(server.url("/list.txt").toString(), target, null)
        assertTrue(result is FilterListFetcher.Result.Failed)
        assertEquals(validList, target.readText())
    }
}
