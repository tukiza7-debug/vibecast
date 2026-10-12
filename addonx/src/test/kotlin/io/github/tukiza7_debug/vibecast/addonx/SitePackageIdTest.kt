package io.github.tukiza7_debug.vibecast.addonx.analyzer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SitePackageIdTest {
    private val segmentRegex = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z0-9_]+)+$")

    @Test
    fun `produces a valid applicationId for normal names`() {
        val id = SitePackageId.forAppName("StreamFlix Movies")
        assertTrue(id.startsWith("io.github.vibecast.site."))
        assertTrue("segments must be applicationId-safe: $id", segmentRegex.matches(id))
    }

    @Test
    fun `is stable for the same name`() {
        assertEquals(
            SitePackageId.forAppName("Cinema World"),
            SitePackageId.forAppName("Cinema World"),
        )
    }

    @Test
    fun `differs for different names`() {
        assertNotEquals(SitePackageId.forAppName("Alpha"), SitePackageId.forAppName("Beta"))
    }

    @Test
    fun `handles degenerate and hostile names without invalid segments`() {
        listOf("", "   ", "!!!", "123 Site", "日本語 App", "\u0000weird", "A".repeat(300))
            .forEach { name ->
                val id = SitePackageId.forAppName(name)
                assertTrue("segments must be applicationId-safe for '$name': $id", segmentRegex.matches(id))
            }
    }

    @Test
    fun `hash suffix never goes negative`() {
        // Int.MIN_VALUE.hashCode() is itself; abs() keeps it negative - the unsigned
        // base36 suffix must never reproduce that bug.
        repeat(500) { i ->
            val id = SitePackageId.forAppName("App $i")
            assertTrue(id.last().isLetterOrDigit() || id.last() == '_')
        }
    }
}
