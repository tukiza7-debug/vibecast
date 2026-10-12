package io.github.tukiza7_debug.vibecast.addonx.analyzer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * evaluateJavascript returns a JSON string literal; decoding it must preserve every
 * escape sequence exactly (this is what the old chained-replace code corrupted).
 */
class EvaluatedHtmlTest {
    @Test
    fun `decodes a plain json string literal`() {
        assertEquals("<html><body>hi</body></html>", EvaluatedHtml.decode("\"<html><body>hi</body></html>\""))
    }

    @Test
    fun `decodes quotes newlines and backslashes in order`() {
        val raw = "\"<div class=\\\"a\\\">line1\\nline2\\\\end</div>\""
        assertEquals("<div class=\"a\">line1\nline2\\end</div>", EvaluatedHtml.decode(raw))
    }

    @Test
    fun `decodes unicode escapes`() {
        assertEquals("caf\u00e9", EvaluatedHtml.decode("\"caf\\u00e9\""))
    }

    @Test
    fun `null literal and null input yield null`() {
        assertNull(EvaluatedHtml.decode(null))
        assertNull(EvaluatedHtml.decode("null"))
        assertNull(EvaluatedHtml.decode(""))
    }

    @Test
    fun `malformed payloads yield null instead of throwing`() {
        assertNull(EvaluatedHtml.decode("\"unterminated"))
        assertNull(EvaluatedHtml.decode("42"))
    }
}
