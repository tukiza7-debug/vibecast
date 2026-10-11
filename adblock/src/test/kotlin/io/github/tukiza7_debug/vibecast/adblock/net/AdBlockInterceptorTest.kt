package io.github.tukiza7_debug.vibecast.adblock.net

import io.github.tukiza7_debug.vibecast.adblock.FilterEngine
import io.github.tukiza7_debug.vibecast.adblock.FilterEngine.SitePolicy
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Serves a local fixture page over MockWebServer and proves the interceptor blocks
 * filtered paths without ever touching a real ad network.
 */
class AdBlockInterceptorTest {
    private lateinit var server: MockWebServer
    private lateinit var engine: FilterEngine

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        engine = FilterEngine()
        val fixture =
            javaClass.classLoader!!.getResourceAsStream("fixture-page.txt")!!
                .bufferedReader().readText()
        engine.replace(
            FilterEngine.Lists(easyList = fixture, easyPrivacy = null, customRules = ""),
            SitePolicy(),
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun client() =
        OkHttpClient.Builder()
            .addInterceptor(AdBlockInterceptor(engine) { SitePolicy() })
            .build()

    @Test
    fun `blocked path never reaches the server`() {
        val response =
            client().newCall(
                Request.Builder()
                    .url(server.url("/ads/badge.gif"))
                    .header("Referer", "https://page.example/")
                    .build(),
            ).execute()
        response.use {
            assertEquals(403, it.code)
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `first-party media requests marked required are not blocked`() {
        server.enqueue(MockResponse().setBody("media"))
        val policy = SitePolicy(bypassHosts = setOf(AdBlockInterceptorTest::server.name.let { "localhost" }))
        val client =
            OkHttpClient.Builder()
                .addInterceptor(AdBlockInterceptor(engine) { policy })
                .build()
        val response =
            client.newCall(
                Request.Builder()
                    .url(server.url("/ads/badge.gif"))
                    .header("Referer", "https://page.example/")
                    .build(),
            ).execute()
        response.use { assertTrue(it.isSuccessful) }
    }
}
