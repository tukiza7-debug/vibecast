package io.github.tukiza7_debug.vibecast.core.codes

import io.github.tukiza7_debug.vibecast.core.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class IndexJsonTest {
    @Test
    fun `parses a valid index`() {
        val index = IndexJson.parse(Fixtures.read("index-sample.json"))
        assertEquals(1, index.schemaVersion)
        assertEquals(2, index.extensions.size)
        assertEquals("deadbeef", index.extensions[0].sha256)
    }

    @Test
    fun `rejects malformed json`() {
        assertThrows(CodeException::class.java) { IndexJson.parse("{ not json") }
    }

    @Test
    fun `rejects wrong schema version`() {
        assertThrows(CodeException::class.java) {
            IndexJson.parse("""{"schemaVersion": 2, "extensions": []}""")
        }
    }

    @Test
    fun `rejects entries with traversal or absolute paths`() {
        assertThrows(CodeException::class.java) {
            IndexJson.parse("""{"schemaVersion": 1, "extensions": [{"file": "../escape.json"}]}""")
        }
        assertThrows(CodeException::class.java) {
            IndexJson.parse("""{"schemaVersion": 1, "extensions": [{"file": "/etc/passwd"}]}""")
        }
    }

    @Test
    fun `rejects entries without a file`() {
        assertThrows(CodeException::class.java) {
            IndexJson.parse("""{"schemaVersion": 1, "extensions": [{"sha256": "ab"}]}""")
        }
    }

    @Test
    fun `encode and decode roundtrip`() {
        val index =
            ExtensionIndex(
                schemaVersion = 1,
                name = "Repo",
                extensions = listOf(IndexEntry(file = "a.json", sha256 = "ff"), IndexEntry(file = "b.json")),
            )
        assertEquals(index.extensions, IndexJson.parse(IndexJson.encode(index)).extensions)
    }
}
