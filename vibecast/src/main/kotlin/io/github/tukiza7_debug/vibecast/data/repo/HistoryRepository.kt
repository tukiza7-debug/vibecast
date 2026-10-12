package io.github.tukiza7_debug.vibecast.data.repo

import io.github.tukiza7_debug.vibecast.data.db.FavoriteEntity
import io.github.tukiza7_debug.vibecast.data.db.FavoritesDao
import io.github.tukiza7_debug.vibecast.data.db.HistoryDao
import io.github.tukiza7_debug.vibecast.data.db.HistoryEntity
import io.github.tukiza7_debug.vibecast.player.PlaybackMeta
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HistoryRepository
    @Inject
    constructor(
        private val historyDao: HistoryDao,
        private val favoritesDao: FavoritesDao,
    ) {
        fun observeHistory(): Flow<List<HistoryEntity>> = historyDao.observeAll()

        fun observeFavorites(): Flow<List<FavoriteEntity>> = favoritesDao.observeAll()

        suspend fun resumePosition(
            extensionId: String,
            episodeUrl: String,
        ): Long = historyDao.byKey(key(extensionId, episodeUrl))?.positionMs ?: 0L

        /**
         * Newest saved position across all episodes of one item page. Used for the
         * aggregate "resume at" hint on the detail screen; playback itself resumes
         * per episode via [resumePosition].
         */
        suspend fun itemResume(
            extensionId: String,
            itemUrl: String,
        ): Long = historyDao.byItem(extensionId, itemUrl)?.positionMs ?: 0L

        suspend fun historyFor(
            extensionId: String,
            episodeUrl: String,
        ): HistoryEntity? = historyDao.byKey(key(extensionId, episodeUrl))

        suspend fun save(
            meta: PlaybackMeta,
            positionMs: Long,
            durationMs: Long,
        ) {
            historyDao.upsert(
                HistoryEntity(
                    itemKey = key(meta.extensionId, meta.mediaId),
                    extensionId = meta.extensionId,
                    title = meta.title,
                    url = meta.mediaId,
                    itemUrl = meta.itemUrl,
                    cover = meta.cover,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }

        suspend fun isFavorite(
            extensionId: String,
            episodeUrl: String,
        ): Boolean = favoritesDao.byKey(key(extensionId, episodeUrl)) != null

        suspend fun toggleFavorite(
            extensionId: String,
            title: String,
            url: String,
            cover: String?,
        ): Boolean {
            val itemKey = key(extensionId, url)
            val existing = favoritesDao.byKey(itemKey)
            if (existing != null) {
                favoritesDao.delete(itemKey)
                return false
            }
            favoritesDao.upsert(
                FavoriteEntity(
                    itemKey = itemKey,
                    extensionId = extensionId,
                    title = title,
                    url = url,
                    cover = cover,
                    addedAt = System.currentTimeMillis(),
                ),
            )
            return true
        }

        companion object {
            fun key(
                extensionId: String,
                url: String,
            ): String = "$extensionId|$url"
        }
    }
