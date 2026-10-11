package io.github.tukiza7_debug.vibecast.data.repo

import io.github.tukiza7_debug.vibecast.core.model.StreamRef
import io.github.tukiza7_debug.vibecast.data.db.ExtensionDao
import io.github.tukiza7_debug.vibecast.player.PlaybackMetaRegistry
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Re-runs the extension video step for a media id when its URL expired, so playback
 * can resume at the same position on a fresh stream.
 */
@Singleton
class StreamRefresher
    @Inject
    constructor(
        private val extensionDao: ExtensionDao,
        private val feedRepository: FeedRepository,
        private val metaRegistry: PlaybackMetaRegistry,
    ) : io.github.tukiza7_debug.vibecast.player.StreamRefresher {
        override suspend fun refresh(mediaId: String): Boolean {
            val refreshed = resolve(mediaId) ?: return false
            return true
        }

        suspend fun resolve(mediaId: String): StreamRef? {
            val meta = metaRegistry.get(mediaId) ?: return null
            val entity = extensionDao.byId(meta.extensionId) ?: return null
            val extension = runCatching { feedRepository.load(entity) }.getOrNull() ?: return null
            return runCatching { feedRepository.stream(extension, mediaId) }.getOrNull()
        }
    }
