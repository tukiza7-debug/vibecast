package io.github.tukiza7_debug.vibecast.core.codes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceCodesTest {

    @Test
    fun `gc1 parses owner repo path and ref`() {
        val source = SourceCodes.parse("gc1:alice/videos/extensions/net.example.one.json@v2")
        assertEquals(GitSource.Kind.SINGLE_FILE, source.kind)
        assertEquals("alice", source.owner)
        assertEquals("videos", source.repo)
        assertEquals("extensions/net.example.one.json", source.path)
        assertEquals("v2", source.ref)
        assertEquals("https://raw.githubusercontent.com/alice/videos/v2/extensions/net.example.one.json", source.rawUrl())
    }

    @Test
    fun `gc1 without ref falls back to HEAD`() {
        val source = SourceCodes.parse("gc1:alice/videos/one.json")
        assertNull(source.ref)
        assertEquals("https://raw.githubusercontent.com/alice/videos/HEAD/one.json", source.rawUrl())
    }

    @Test
    fun `gx1 parses repository source`() {
        val source = SourceCodes.parse("gx1:alice/extension-repo@main")
        assertEquals(GitSource.Kind.REPOSITORY, source.kind)
        assertEquals("main", source.ref)
        assertEquals("https://raw.githubusercontent.com/alice/extension-repo/main/index.json", source.rawUrl())
    }

    @Test
    fun `roundtrip through code()`() {
        val source = SourceCodes.parse("gc1:alice/videos/a/b.json@feature/x")
        assertEquals(source, SourceCodes.parse(source.code()))
    }

    @Test
    fun `path traversal is rejected`() {
        assertThrows(CodeException::class.java) { SourceCodes.parse("gc1:alice/videos/../secret.json") }
        assertThrows(CodeException::class.java) { SourceCodes.parse("gc1:alice/videos/../../x") }
    }

    @Test
    fun `absolute or empty path segments are rejected`() {
        assertThrows(CodeException::class.java) { SourceCodes.parse("gc1:alice/videos//x.json") }
        assertThrows(CodeException::class.java) { SourceCodes.parse("gc1:alice/videos") }
    }

    @Test
    fun `gx1 with a path is rejected`() {
        assertThrows(CodeException::class.java) { SourceCodes.parse("gx1:alice/repo/extra") }
    }

    @Test
    fun `bad owner characters are rejected`() {
        assertThrows(CodeException::class.java) { SourceCodes.parse("gc1:ali ce/repo/x.json") }
    }

    @Test
    fun `unknown prefixes are rejected`() {
        assertThrows(CodeException::class.java) { SourceCodes.parse("ux1:alice/repo") }
        assertThrows(CodeException::class.java) { SourceCodes.parse("unix1:nope.12345678") }
    }

    @Test
    fun `detector finds codes inside shared text`() {
        assertEquals(
            "gc1:alice/videos/one.json",
            CodeDetector.findInText("Check this out: gc1:alice/videos/one.json and enjoy."),
        )
        assertEquals(
            "gx1:alice/repo@main",
            CodeDetector.findInText("follow gx1:alice/repo@main."),
        )
        assertTrue(CodeDetector.findInText("no codes here") == null)
    }

    @Test
    fun `detector trims sentence punctuation`() {
        assertEquals("unix1:abc.12345678", CodeDetector.findInText("add unix1:abc.12345678."))
    }
}
