package io.github.tukiza7_debug.vibecast.data.repo

import io.github.tukiza7_debug.vibecast.core.engine.ExtensionEngine
import io.github.tukiza7_debug.vibecast.core.model.DetailResult
import io.github.tukiza7_debug.vibecast.core.model.Episode
import io.github.tukiza7_debug.vibecast.core.model.Extension
import io.github.tukiza7_debug.vibecast.core.model.SearchItem
import io.github.tukiza7_debug.vibecast.core.model.StreamRef
import io.github.tukiza7_debug.vibecast.data.db.ExtensionEntity
import io.github.tukiza7_debug.vibecast.data.prefs.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs extension steps on the shared engine, always off the main thread. The engine
 * instance is per-call so ad-blocking state stays in the injected HostFilter.
 */
@Singleton
class FeedRepository
    @Inject
    constructor(
        private val extensionRepository: ExtensionRepository,
        private val engineProvider: EngineProvider,
        private val settings: SettingsStore,
    ) {
        @Singleton
        class EngineProvider
            @Inject
            constructor(
                private val httpClient: io.github.tukiza7_debug.vibecast.core.http.HttpClient,
                private val hostFilter: io.github.tukiza7_debug.vibecast.core.http.HostFilter,
                private val webSniffer: io.github.tukiza7_debug.vibecast.core.engine.WebSniffer,
            ) {
                fun get(): ExtensionEngine = ExtensionEngine(httpClient, hostFilter, webSniffer)
            }

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        private suspend fun engine(): ExtensionEngine = engineProvider.get()

        suspend fun load(extension: ExtensionEntity): Extension =
            withContext(Dispatchers.Default) {
                extensionRepository.parse(extension.json)
            }

        suspend fun latest(
            extension: Extension,
            page: Int,
        ): List<SearchItem> = withContext(Dispatchers.IO) { engine().latest(extension, page) }

        suspend fun popular(
            extension: Extension,
            page: Int,
        ): List<SearchItem> = withContext(Dispatchers.IO) { engine().popular(extension, page) }

        suspend fun search(
            extension: Extension,
            query: String,
            page: Int,
        ): List<SearchItem> = withContext(Dispatchers.IO) { engine().search(extension, query, page) }

        suspend fun detail(
            extension: Extension,
            itemUrl: String,
        ): DetailResult = withContext(Dispatchers.IO) { engine().detail(extension, itemUrl) }

        suspend fun episodes(
            extension: Extension,
            itemUrl: String,
        ): List<Episode> = withContext(Dispatchers.IO) { engine().episodes(extension, itemUrl) }

        suspend fun stream(
            extension: Extension,
            episodeUrl: String,
        ): StreamRef = withContext(Dispatchers.IO) { engine().video(extension, episodeUrl) }

        /** Latest (and optional popular) rows per enabled extension; a broken extension
         *  becomes an error row instead of silently disappearing. */
        suspend fun homeRows(extensions: List<ExtensionEntity>): List<HomeRow> =
            coroutineScope {
                val defaultId = settings.defaultExtensionId.firstOrNull()
                val ordered = if (defaultId == null) extensions else extensions.sortedByDescending { it.id == defaultId }
                ordered.map { entity ->
                    async {
                        try {
                            val ext = load(entity)
                            val latestItems = latest(ext, 1)
                            val popularItems = runCatching { popular(ext, 1) }.getOrDefault(emptyList())
                            listOfNotNull(
                                HomeRow(entity.id, entity.name, "Latest", latestItems, error = null),
                                if (popularItems.isEmpty()) {
                                    null
                                } else {
                                    HomeRow(entity.id, entity.name, "Popular", popularItems, error = null)
                                },
                            )
                        } catch (e: Exception) {
                            listOf(HomeRow(entity.id, entity.name, "Latest", emptyList(), error = e.message ?: "failed to load"))
                        }
                    }
                }.awaitAll().flatten()
            }

        data class HomeRow(
            val extensionId: String,
            val extensionName: String,
            val title: String,
            val items: List<SearchItem>,
            val error: String?,
        )
    }
