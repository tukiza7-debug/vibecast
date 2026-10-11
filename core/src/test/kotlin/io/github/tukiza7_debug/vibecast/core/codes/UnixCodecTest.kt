package io.github.tukiza7_debug.vibecast.core.codes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class UnixCodecTest {

    private val json = """{"schemaVersion":1,"id":"test.sample","name":"Sample","version":1}"""

    @Test
    fun `roundtrip preserves json`() {
        val code = UnixCodec.encode(json)
        assertTrue(code.startsWith("unix1:"))
        assertEquals(json, UnixCodec.decode(code))
    }

    @Test
    fun `code has no padding and a crc trailer`() {
        val code = UnixCodec.encode(json)
        val payload = code.removePrefix("unix1:").substringBeforeLast('.')
        assertTrue(!payload.contains('='))
        assertTrue(Regex("\\.[0-9a-f]{8}$").containsMatchIn(code))
    }

    @Test
    fun `corrupted crc is rejected`() {
        val code = UnixCodec.encode(json)
        val broken = code.dropLast(1) + if (code.last() == '0') '1' else '0'
        val error = assertThrows(CodeException::class.java) { UnixCodec.decode(broken) }
        assertTrue(error.message!!.contains("CRC"))
    }

    @Test
    fun `truncated code is rejected`() {
        val code = UnixCodec.encode(json)
        assertThrows(CodeException::class.java) { UnixCodec.decode(code.take(code.length / 2)) }
    }

    @Test
    fun `unknown prefix is rejected`() {
        assertThrows(CodeException::class.java) { UnixCodec.decode("unix9:abc.12345678") }
        assertThrows(CodeException::class.java) { UnixCodec.decode("gc1:owner/repo/file.json") }
    }

    @Test
    fun `decompression bomb beyond the cap is rejected`() {
        val bomb = "{\"pad\":\"" + "0".repeat(600 * 1024) + "\"}"
        val code = UnixCodec.encode(bomb)
        val error = assertThrows(CodeException::class.java) { UnixCodec.decode(code) }
        assertTrue(error.message!!.contains("limit"))
    }

    @Test
    fun `invalid deflate payload is rejected`() {
        val fake = "unix1:" + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(byteArrayOf(1, 2, 3)) + ".12345678"
        assertThrows(CodeException::class.java) { UnixCodec.decode(fake) }
    }

    @Test
    fun `payload with base64 padding is accepted`() {
        val code = UnixCodec.encode(json)
        val payload = code.removePrefix("unix1:").substringBeforeLast('.')
        val crc = code.substringAfterLast('.')
        val padded = payload + "=".repeat((4 - payload.length % 4) % 4)
        val crcOfPadded = "%08x".format(
            java.util.zip.CRC32().apply { update(padded.toByteArray(Charsets.US_ASCII)) }.value,
        )
        assertEquals(json, UnixCodec.decode("unix1:$padded.$crcOfPadded"))
        assertEquals(json, UnixCodec.decode("unix1:$payload.$crc"))
    }
}
