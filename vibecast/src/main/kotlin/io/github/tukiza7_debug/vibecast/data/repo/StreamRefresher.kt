package io.github.tukiza7_debug.vibecast.data.repo

import io.github.tukiza7_debug.vibecast.core.model.StreamRef
import io.github.tukiza7_debug.vibecast.data.db.ExtensionDao
import io.github.tukiza7_debug.vibecast.player.PlaybackMetaRegistry
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Re-runs the extension video step for a media id when its URL expired, so playback
 * can resume at the same position on a fresh stream. The resolved [StreamRef] is
 * consumed by [io.github.tukiza7_debug.vibecast.player.PlayerConnection.refreshStream],
 * which swaps the media item and seeks back.
 */
@Singleton
class StreamRefresher
    @Inject
    constructor(
        private val extensionDao: ExtensionDao,
        private val feedRepository: FeedRepository,
        private val metaRegistry: PlaybackMetaRegistry,
    ) {
        suspend fun resolve(mediaId: String): StreamRef? {
            val meta = metaRegistry.get(mediaId) ?: return null
            val entity = extensionDao.byId(meta.extensionId) ?: return null
            val extension = runCatching { feedRepository.load(entity) }.getOrNull() ?: return null
            return runCatching { feedRepository.stream(extension, mediaId) }.getOrNull()
        }
    }
