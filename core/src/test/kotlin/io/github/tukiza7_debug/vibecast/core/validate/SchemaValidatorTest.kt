package io.github.tukiza7_debug.vibecast.core.validate

import io.github.tukiza7_debug.vibecast.core.Fixtures
import io.github.tukiza7_debug.vibecast.core.TestExtensions
import io.github.tukiza7_debug.vibecast.core.codes.ExtensionValidationException
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SchemaValidatorTest {
    private fun base(): String =
        MockWebServer().let {
            it.start()
            val url = it.url("/").toString()
            it.shutdown()
            url
        }

    @Test
    fun `accepts a valid json extension`() {
        val issues = SchemaValidator.validate(TestExtensions.jsonSource(base()))
        assertEquals(emptyList<String>(), issues)
    }

    @Test
    fun `accepts a valid html extension`() {
        val issues = SchemaValidator.validate(TestExtensions.htmlSource(base()))
        assertEquals(emptyList<String>(), issues)
    }

    @Test
    fun `accepts the shipped sample extension`() {
        val issues = SchemaValidator.validate(Fixtures.read("sample-internet-archive.json"))
        assertEquals(emptyList<String>(), issues)
    }

    @Test
    fun `missing steps are named`() {
        val text = """{"schemaVersion":1,"id":"a.b","name":"n","version":1,"lang":"en","baseUrl":"https://x.test"}"""
        val issues = SchemaValidator.validate(text)
        listOf("search", "detail", "episodes", "video").forEach { step ->
            assertTrue("expected issue for $step", issues.any { issue -> issue.startsWith("$step:") })
        }
    }

    @Test
    fun `unsupported schema version is rejected`() {
        val text = TestExtensions.jsonSource(base()).replace("\"schemaVersion\": 1", "\"schemaVersion\": 2")
        val issues = SchemaValidator.validate(text)
        assertTrue(issues.any { it.startsWith("schemaVersion") })
    }

    @Test
    fun `bad id is rejected`() {
        val text = TestExtensions.jsonSource(base()).replace("\"id\": \"test.jsonsource\"", "\"id\": \"Bad Id\"")
        val issues = SchemaValidator.validate(text)
        assertTrue(issues.any { it.startsWith("id:") })
    }

    @Test
    fun `html list steps require a selector`() {
        val text = TestExtensions.htmlSource(base()).replace("\"selector\": \".listing .card\"", "\"selector\": \"\"")
        val issues = SchemaValidator.validate(text)
        assertTrue(issues.any { it == "search.source: selector is required for an html source" })
    }

    @Test
    fun `json list steps require a path`() {
        val text = TestExtensions.jsonSource(base()).replace("\"path\": \"$.response.docs[*]\"", "\"path\": \"\"")
        val issues = SchemaValidator.validate(text)
        assertTrue(issues.any { it == "search.source: path is required for a json source" })
    }

    @Test
    fun `video strategy requirements are enforced`() {
        val text =
            TestExtensions.jsonSource(base())
                .replace("\"strategy\": \"direct\"", "\"strategy\": \"regex\"")
        val issues = SchemaValidator.validate(text)
        assertTrue(issues.any { it == "video.pattern: required for the 'regex' strategy" })
    }

    @Test
    fun `invalid regex is reported with the field`() {
        val text =
            TestExtensions.jsonSource(base()).replace(
                "\"strategy\": \"direct\"",
                "\"strategy\": \"regex\", \"pattern\": \"file([bad\"",
            )
        val issues = SchemaValidator.validate(text)
        assertTrue(issues.any { it.startsWith("video.pattern: invalid regex") })
    }

    @Test
    fun `oversized documents are rejected`() {
        val text = TestExtensions.jsonSource(base()) + " ".repeat(SchemaValidator.MAX_JSON_BYTES + 1)
        val issues = SchemaValidator.validate(text)
        assertTrue(issues.any { it.startsWith("document: larger") })
    }

    @Test(expected = ExtensionValidationException::class)
    fun `decodeAndValidate throws with issues`() {
        SchemaValidator.decodeAndValidate("{}")
    }

    @Test
    fun `filterField without any criterion is rejected`() {
        val text =
            TestExtensions.jsonSource(base())
                .replace(
                    "\"filterField\": \"format\", \"filterEquals\": \"MPEG4\"",
                    "\"filterField\": \"format\"",
                )
        val issues = SchemaValidator.validate(text)
        assertTrue(issues.any { it == "episodes.source: filterField needs filterEquals, filterSuffix or filterContains" })
    }

    @Test
    fun `filter criterion without filterField is rejected`() {
        val text =
            TestExtensions.jsonSource(base())
                .replace(
                    "\"filterField\": \"format\", \"filterEquals\": \"MPEG4\"",
                    "\"filterContains\": \"MP4\"",
                )
        val issues = SchemaValidator.validate(text)
        assertTrue(issues.any { it == "episodes.source: filterContains needs filterField" })
    }

    @Test
    fun `filterContains with filterField is accepted`() {
        val text =
            TestExtensions.jsonSource(base())
                .replace(
                    "\"filterField\": \"format\", \"filterEquals\": \"MPEG4\"",
                    "\"filterField\": \"name\", \"filterContains\": \"1080p\"",
                )
        val issues = SchemaValidator.validate(text)
        assertEquals(emptyList<String>(), issues)
    }
}
