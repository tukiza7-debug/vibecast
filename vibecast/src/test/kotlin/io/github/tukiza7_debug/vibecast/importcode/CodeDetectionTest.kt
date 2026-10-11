package io.github.tukiza7_debug.vibecast.importcode

import io.github.tukiza7_debug.vibecast.core.codes.CodeDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CodeDetectionTest {
    @Test
    fun `unix codes are found in shared text and clipboard payloads`() {
        assertEquals("unix1:abc.12345678", CodeDetector.findInText("copy this unix1:abc.12345678 now"))
        assertNull(CodeDetector.findInText("no codes"))
    }

    @Test
    fun `gc and gx codes with punctuation survive extraction`() {
        assertEquals("gx1:alice/repo@main", CodeDetector.findInText("(gx1:alice/repo@main)"))
        assertEquals("gc1:alice/repo/a.json", CodeDetector.findInText("see gc1:alice/repo/a.json."))
    }
}
