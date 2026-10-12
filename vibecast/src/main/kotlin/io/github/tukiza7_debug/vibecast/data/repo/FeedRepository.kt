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
import kotlinx.coroutines.launch
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

        /**
         * Latest (and optional popular) rows per enabled extension.
         *
         * Resilience contract:
         * - Optional steps that the extension does not define are **skipped silently**
         *   (no error row) - a missing `latest`/`popular` must never break Home.
         * - A step that is defined but fails at runtime becomes a per-section error row
         *   (with a Retry affordance in the UI), never a full-screen error.
         * - Rows are delivered incrementally via [onRows] as soon as each extension
         *   finishes, so one slow source cannot stall the whole feed.
         */
        suspend fun homeRows(
            extensions: List<ExtensionEntity>,
            onRows: (rows: List<HomeRow>) -> Unit,
        ) {
            coroutineScope {
                val defaultId = settings.defaultExtensionId.firstOrNull()
                val ordered = if (defaultId == null) extensions else extensions.sortedByDescending { it.id == defaultId }
                val channel = kotlinx.coroutines.channels.Channel<List<HomeRow>>(kotlinx.coroutines.channels.Channel.UNLIMITED)
                val producer =
                    launch {
                        ordered.map { entity ->
                            async {
                                val rows = rowsForEntity(entity)
                                channel.send(rows)
                            }
                        }.awaitAll()
                        channel.close()
                    }
                for (rows in channel) onRows(rows)
                producer.join()
            }
        }

        /** Snapshot variant kept for tests and callers that want the full list. */
        suspend fun homeRows(extensions: List<ExtensionEntity>): List<HomeRow> {
            val all = mutableListOf<HomeRow>()
            homeRows(extensions) { all += it }
            return all
        }

        /** Never throws: every failure is folded into an error row. Also used for
         *  single-extension retries requested from the Home screen. */
        suspend fun rowsForEntity(entity: ExtensionEntity): List<HomeRow> =
            buildList {
                val ext =
                    try {
                        load(entity)
                    } catch (e: Exception) {
                        add(
                            HomeRow(
                                entity.id,
                                entity.name,
                                "Latest",
                                emptyList(),
                                error = friendly(e),
                                canRetry = true,
                            ),
                        )
                        return@buildList
                    }

                when {
                    ext.latest == null -> Unit // optional step not present: hide the section
                    else ->
                        runCatching { latest(ext, 1) }.fold(
                            onSuccess = { items ->
                                if (items.isNotEmpty()) {
                                    add(HomeRow(entity.id, entity.name, "Latest", items, error = null))
                                }
                            },
                            onFailure = { e ->
                                if (isOptionalMissing(e)) return@fold
                                add(
                                    HomeRow(
                                        entity.id,
                                        entity.name,
                                        "Latest",
                                        emptyList(),
                                        error = friendly(e),
                                        canRetry = true,
                                    ),
                                )
                            },
                        )
                }

                when {
                    ext.popular == null -> Unit // optional step not present: hide the section
                    else ->
                        runCatching { popular(ext, 1) }.fold(
                            onSuccess = { items ->
                                if (items.isNotEmpty()) {
                                    add(HomeRow(entity.id, entity.name, "Popular", items, error = null))
                                }
                            },
                            onFailure = { e ->
                                if (isOptionalMissing(e)) return@fold
                                add(
                                    HomeRow(
                                        entity.id,
                                        entity.name,
                                        "Popular",
                                        emptyList(),
                                        error = friendly(e),
                                        canRetry = true,
                                    ),
                                )
                            },
                        )
                }
            }

        /** Short, human-readable failure text; the technical detail stays in logs. */
        private fun friendly(e: Throwable): String = friendlySourceError(e)

        private fun isOptionalMissing(e: Throwable): Boolean = e.message?.contains("step is not defined", ignoreCase = true) == true

        data class HomeRow(
            val extensionId: String,
            val extensionName: String,
            val title: String,
            val items: List<SearchItem>,
            val error: String?,
            val canRetry: Boolean = false,
        )
    }
