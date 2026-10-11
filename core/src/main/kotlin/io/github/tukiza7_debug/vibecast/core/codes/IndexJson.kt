package io.github.tukiza7_debug.vibecast.core.codes

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * index.json at the root of a GX repository. Lists extension files relative to the
 * repository root; sha256 is optional and verified when present.
 */
@Serializable
data class ExtensionIndex(
    @SerialName("schemaVersion") val schemaVersion: Int = 1,
    val name: String = "",
    val extensions: List<IndexEntry> = emptyList(),
) {
    init {
        if (schemaVersion != 1) throw CodeException("index schemaVersion must be 1, got $schemaVersion")
    }
}

@Serializable
data class IndexEntry(
    val file: String,
    val sha256: String? = null,
) {
    init {
        if (file.isBlank()) throw CodeException("index entry has empty file")
        if (file.startsWith("/") || file.split('/').contains("..")) {
            throw CodeException("index entry file must be a relative path without '..': $file")
        }
    }
}

object IndexJson {
    val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
            explicitNulls = false
        }

    fun parse(text: String): ExtensionIndex =
        try {
            json.decodeFromString(ExtensionIndex.serializer(), text)
        } catch (e: CodeException) {
            throw e
        } catch (e: Exception) {
            throw CodeException("malformed index.json: ${e.message}", e)
        }

    fun encode(index: ExtensionIndex): String = json.encodeToString(ExtensionIndex.serializer(), index)
}
