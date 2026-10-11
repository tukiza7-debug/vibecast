package io.github.tukiza7_debug.vibecast.player

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Registry of per-stream HTTP headers (Referer, User-Agent, cookies) returned by the
 * extension video step. The player data source consults it by request host so headers
 * apply to that stream's manifest, segment and key requests only.
 */
@Singleton
class HeadersRegistry
    @Inject
    constructor() {
        data class Entry(val hosts: Set<String>, val headers: Map<String, String>)

        private val entries = LinkedHashMap<String, Entry>()

        @Synchronized
        fun register(
            mediaId: String,
            streamUrl: String,
            baseUrl: String,
            headers: Map<String, String>,
        ) {
            val hosts =
                buildSet {
                    HeadersRegistry.hostOf(streamUrl)?.let(::add)
                    HeadersRegistry.hostOf(baseUrl)?.let(::add)
                }
            if (hosts.isEmpty()) return
            entries[mediaId] = Entry(hosts, headers)
        }

        @Synchronized
        fun headersFor(requestHost: String?): Map<String, String> {
            if (requestHost == null) return emptyMap()
            entries.values.forEach { entry ->
                entry.hosts.forEach { registered ->
                    if (requestHost == registered || requestHost.endsWith(".$registered")) {
                        return entry.headers
                    }
                }
            }
            return emptyMap()
        }

        @Synchronized
        fun requiredHosts(): Set<String> = entries.values.flatMapTo(HashSet()) { it.hosts }

        @Synchronized
        fun clear() = entries.clear()

        companion object {
            fun hostOf(url: String): String? =
                runCatching {
                    java.net.URI(url).host?.lowercase()
                }.getOrNull()
        }
    }
