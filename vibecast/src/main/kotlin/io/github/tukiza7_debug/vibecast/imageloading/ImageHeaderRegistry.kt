package io.github.tukiza7_debug.vibecast.imageloading

import android.util.Log
import io.github.tukiza7_debug.vibecast.core.model.Extension
import io.github.tukiza7_debug.vibecast.data.db.ExtensionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Knows which extra HTTP headers (User-Agent, Referer, cookies...) each installed
 * extension needs so cover/poster requests succeed on CDNs that check them.
 *
 * The registry is rebuilt from the Room extension table (JSON re-parsed off the
 * main thread) whenever the table changes. Lookup is by host with one level of
 * suffix matching so `cdn.example.com` inherits headers declared for
 * `https://example.com`.
 */
@Singleton
class ImageHeaderRegistry
    @Inject
    constructor() {
        private data class Source(val host: String, val headers: Map<String, String>)

        private val json = Json { ignoreUnknownKeys = true }

        private val sources = ConcurrentHashMap<String, Source>()

        /** A mobile browser UA applied to known extension hosts that declare none;
         *  some CDNs reject blank or okhttp default user agents. */
        val defaultUserAgent: String =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/124.0.0.0 Mobile Safari/537.36"

        suspend fun refresh(entities: List<ExtensionEntity>) {
            withContext(Dispatchers.Default) {
                val parsed = mutableListOf<Source>()
                for (entity in entities) {
                    if (!entity.enabled) continue
                    try {
                        val ext = json.decodeFromString(Extension.serializer(), entity.json)
                        if (ext.headers.isEmpty()) continue
                        val host = ext.baseUrl.toHttpUrlOrNull()?.host ?: continue
                        parsed += Source(host, normalize(ext.headers))
                    } catch (e: Exception) {
                        Log.w(TAG, "skipping headers for ${entity.id}: ${e.message}")
                    }
                }
                sources.clear()
                parsed.forEach { sources[it.host] = it }
            }
        }

        /** Extra headers for an image URL; empty when the host is unknown. */
        fun headersFor(url: String): Map<String, String> {
            val host = url.toHttpUrlOrNull()?.host ?: return emptyMap()
            val direct = sources[host]
            if (direct != null) return direct.headers
            // one level of suffix match: cdn.example.com -> example.com
            val root = host.substringAfter('.', missingDelimiterValue = "")
            val base = sources[root]
            if (base != null) return base.headers
            return emptyMap()
        }

        private fun normalize(headers: Map<String, String>): Map<String, String> =
            headers
                .filterKeys { it.isNotBlank() }
                .mapKeys { it.key.lowercase() }
                .filterValues { it.isNotBlank() }

        private companion object {
            const val TAG = "ImageHeaderRegistry"
        }
    }
