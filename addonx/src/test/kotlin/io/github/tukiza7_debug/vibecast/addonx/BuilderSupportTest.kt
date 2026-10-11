package io.github.tukiza7_debug.vibecast.addonx

import io.github.tukiza7_debug.vibecast.addonx.analyzer.ExtensionBuilder
import io.github.tukiza7_debug.vibecast.addonx.ui.common.StepCoverage
import io.github.tukiza7_debug.vibecast.addonx.ui.common.friendlyStepError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The export checklist and friendly errors speak the maker's language. */
class BuilderSupportTest {
    private fun fullDraft() =
        ExtensionBuilder.Draft(
            id = "test.host",
            name = "Host",
            version = 1,
            lang = "en",
            baseUrl = "https://host.test",
            headers = emptyMap(),
            search =
                ExtensionBuilder.StepDraft(
                    name = "search",
                    requestUrl = "https://host.test/s?q={query}",
                    sourceType = "html",
                    itemSelector = ".card",
                    jsonPath = null,
                    fields =
                        linkedMapOf(
                            "title" to ExtensionBuilder.FieldDraft(selector = "h2"),
                            "url" to ExtensionBuilder.FieldDraft(selector = "a[href]", attribute = "href"),
                        ),
                ),
            latest = null,
            detail =
                ExtensionBuilder.StepDraft(
                    name = "detail",
                    requestUrl = "{url}",
                    sourceType = "html",
                    itemSelector = null,
                    jsonPath = null,
                    fields =
                        linkedMapOf(
                            "title" to ExtensionBuilder.FieldDraft(selector = "h1"),
                        ),
                ),
            episodes =
                ExtensionBuilder.StepDraft(
                    name = "episodes",
                    requestUrl = "{url}",
                    sourceType = "html",
                    itemSelector = ".episode",
                    jsonPath = null,
                    fields =
                        linkedMapOf(
                            "name" to ExtensionBuilder.FieldDraft(selector = "a"),
                            "url" to ExtensionBuilder.FieldDraft(selector = "a[href]", attribute = "href"),
                        ),
                ),
            video =
                ExtensionBuilder.VideoDraft(
                    requestUrl = "{url}",
                    strategy = "regex",
                    pattern = """file:\s*"([^"]+)"""",
                ),
        )

    @Test
    fun `complete draft has no warning`() {
        val states = StepCoverage.analyze(fullDraft())
        assertEquals(6, states.size)
        assertNull(StepCoverage.warning(states))
        assertTrue(states.first { it.name == "Latest" }.level == StepCoverage.Level.MISSING)
        // optional steps missing is not a problem
        assertTrue(!states.first { it.name == "Latest" }.required)
    }

    @Test
    fun `episodes without selector is partial and warns`() {
        val draft = fullDraft().copy(episodes = fullDraft().episodes.copy(itemSelector = null))
        val states = StepCoverage.analyze(draft)
        val episodes = states.first { it.name == "Episodes" }
        assertEquals(StepCoverage.Level.PARTIAL, episodes.level)
        assertTrue(episodes.note.contains("visual picker"))
        val warning = StepCoverage.warning(states)
        assertTrue(warning != null && warning.contains("episodes"))
    }

    @Test
    fun `webviewSniff failure suggests recording`() {
        val text = friendlyStepError("video", "webviewSniff requires a WebView sniffer")
        assertTrue(text!!.contains("Record"))
        assertTrue(text.contains("regex"))
    }

    @Test
    fun `missing optional step message stays calm`() {
        val text = friendlyStepError("latest", "[latest] step is not defined in this extension")
        assertTrue(text!!.contains("Optional steps can stay missing"))
        assertTrue(text.contains("latest"))
    }

    @Test
    fun `unknown errors pass through unchanged`() {
        assertEquals("some exotic failure", friendlyStepError("video", "some exotic failure"))
        assertNull(friendlyStepError("video", null))
    }
}
