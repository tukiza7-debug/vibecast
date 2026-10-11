package io.github.tukiza7_debug.vibecast.core.validate

import io.github.tukiza7_debug.vibecast.core.TestExtensions
import io.github.tukiza7_debug.vibecast.core.util.ExtensionDomains
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Test

class ExtensionDomainsTest {
    @Test
    fun `collects distinct hosts from every step`() {
        val server = MockWebServer()
        server.start()
        val base = server.url("/").toString()
        server.shutdown()
        val extension = SchemaValidator.decodeAndValidate(TestExtensions.jsonSource(base))
        val domains = ExtensionDomains.of(extension)
        assertEquals(listOf("localhost"), domains)
    }

    @Test
    fun `includes foreign hosts from step urls`() {
        val server = MockWebServer()
        server.start()
        val base = server.url("/").toString()
        server.shutdown()
        val json =
            TestExtensions.jsonSource(base)
                .replace("${base}search?", "https://api.other.test/search?")
        val extension = SchemaValidator.decodeAndValidate(json)
        assertEquals(listOf("api.other.test", "localhost"), ExtensionDomains.of(extension))
    }
}
