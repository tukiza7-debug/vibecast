package io.github.tukiza7_debug.vibecast.core.codes

import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * UNIX code: "unix1:<payload>.<crc32>". Payload is the raw-deflate compressed
 * extension JSON, base64url encoded without padding; the trailer is the CRC32 of the
 * payload string as eight lowercase hex characters. Self-contained and offline.
 */
object UnixCodec {

    const val PREFIX = "unix1:"
    const val MAX_DECOMPRESSED_BYTES = 512 * 1024
    const val MAX_COMPRESSED_CHARS = 700_000

    private val trailerRegex = Regex("\\.([0-9a-fA-F]{8})$")

    fun encode(extensionJson: String): String {
        val payload = compress(extensionJson.toByteArray(Charsets.UTF_8))
        val crc = crcOf(payload)
        return PREFIX + payload + "." + "%08x".format(crc)
    }

    fun decode(code: String): String {
        val trimmed = code.trim()
        if (!trimmed.startsWith(PREFIX)) throw CodeException("not a unix1 code (missing prefix)")
        val body = trimmed.removePrefix(PREFIX)
        if (body.isEmpty()) throw CodeException("empty payload")
        val trailer = trailerRegex.find(body) ?: throw CodeException("missing CRC32 trailer")
        val expected = trailer.groupValues[1].lowercase()
        val payload = body.substringBeforeLast('.')
        if (payload.length > MAX_COMPRESSED_CHARS) {
            throw CodeException("payload exceeds ${MAX_COMPRESSED_CHARS / 1000} KB compressed limit")
        }
        val actual = crcOf(payload)
        if ("%08x".format(actual) != expected) throw CodeException("CRC32 mismatch: the code is corrupted")
        val json = decompress(payload)
        if (json.size > MAX_DECOMPRESSED_BYTES) {
            throw CodeException(
                "decompressed payload is ${json.size / 1024} KB, above the ${MAX_DECOMPRESSED_BYTES / 1024} KB limit",
            )
        }
        return String(json, Charsets.UTF_8)
    }

    private fun crcOf(payload: String): Long =
        CRC32().apply { update(payload.toByteArray(Charsets.US_ASCII)) }.value

    private fun compress(bytes: ByteArray): String {
        val deflater = Deflater(Deflater.BEST_COMPRESSION, true)
        val out = ByteArrayOutputStream()
        try {
            deflater.setInput(bytes)
            deflater.finish()
            val buffer = ByteArray(16 * 1024)
            while (!deflater.finished()) {
                val count = deflater.deflate(buffer)
                out.write(buffer, 0, count)
            }
        } finally {
            deflater.end()
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(out.toByteArray())
    }

    private fun decompress(payload: String): ByteArray {
        val padded = payload + "=".repeat((4 - payload.length % 4) % 4)
        val compressed = try {
            Base64.getUrlDecoder().decode(padded)
        } catch (_: IllegalArgumentException) {
            throw CodeException("payload is not valid base64url")
        }
        val inflater = Inflater(true)
        val out = ByteArrayOutputStream()
        try {
            inflater.setInput(compressed)
            val buffer = ByteArray(8 * 1024)
            while (!inflater.finished()) {
                val count = inflater.inflate(buffer)
                out.write(buffer, 0, count)
                if (out.size() > MAX_DECOMPRESSED_BYTES) {
                    throw CodeException(
                        "decompressed payload exceeds the ${MAX_DECOMPRESSED_BYTES / 1024} KB limit",
                    )
                }
                if (count == 0 && inflater.needsInput()) {
                    throw CodeException("payload is not a valid deflate stream")
                }
            }
        } catch (e: CodeException) {
            throw e
        } catch (_: Exception) {
            throw CodeException("payload is not a valid deflate stream")
        } finally {
            inflater.end()
        }
        return out.toByteArray()
    }
}
