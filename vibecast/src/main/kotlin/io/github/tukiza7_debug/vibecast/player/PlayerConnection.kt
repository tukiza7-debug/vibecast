package io.github.tukiza7_debug.vibecast.player

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.tukiza7_debug.vibecast.core.model.StreamRef
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Metadata attached to every queued item, used for history, resume and refresh. */
data class PlaybackMeta(
    val mediaId: String,
    val extensionId: String,
    val title: String,
    val cover: String?,
    val baseUrl: String,
    val quality: String?,
    /** Item page URL for detail navigation; blank when unknown. */
    val itemUrl: String = "",
)

interface StreamRefresher {
    suspend fun refresh(mediaId: String): Boolean
}

@Singleton
class PlaybackMetaRegistry
    @Inject
    constructor() {
        private val map = LinkedHashMap<String, PlaybackMeta>()

        @Synchronized
        fun put(meta: PlaybackMeta) {
            map[meta.mediaId] = meta
        }

        @Synchronized
        fun get(mediaId: String?): PlaybackMeta? = mediaId?.let { map[it] }

        @Synchronized
        fun clear() = map.clear()
    }

@Singleton
class PlayerConnection
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val headersRegistry: HeadersRegistry,
        private val metaRegistry: PlaybackMetaRegistry,
        private val historyRepository: io.github.tukiza7_debug.vibecast.data.repo.HistoryRepository,
        private val streamRefresher: io.github.tukiza7_debug.vibecast.data.repo.StreamRefresher,
        private val appScope: CoroutineScope,
    ) {
        data class PlaybackItem(
            val mediaId: String,
            val title: String,
            val streamRef: StreamRef,
            val extensionId: String,
            val cover: String?,
            val baseUrl: String,
            /** Item page URL kept for Continue Watching routing. */
            val itemUrl: String = "",
        )

        private val controllerFuture = MutableStateFlow<MediaController?>(null)

        val controller: StateFlow<MediaController?> = controllerFuture.asStateFlow()

        private val _isPlaying = MutableStateFlow(false)
        val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

        private val _positionMs = MutableStateFlow(0L)
        val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

        private val _durationMs = MutableStateFlow(0L)
        val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

        private val _currentMediaId = MutableStateFlow<String?>(null)
        val currentMediaId: StateFlow<String?> = _currentMediaId.asStateFlow()

        private val _tracks = MutableStateFlow<Tracks?>(null)
        val tracks: StateFlow<Tracks?> = _tracks.asStateFlow()

        private val _speed = MutableStateFlow(1f)
        val speed: StateFlow<Float> = _speed.asStateFlow()

        private val _error = MutableStateFlow<String?>(null)
        val error: StateFlow<String?> = _error.asStateFlow()

        private val _playlist = MutableStateFlow<List<String>>(emptyList())
        val playlist: StateFlow<List<String>> = _playlist.asStateFlow()

        private var historyJob: Job? = null
        private var lastSavedPosition = 0L

        init {
            appScope.launch(Dispatchers.Main) {
                val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
                val future = MediaController.Builder(context, token).buildAsync()
                val controller =
                    try {
                        future.get()
                    } catch (e: Exception) {
                        _error.value = "player unavailable: ${e.message}"
                        return@launch
                    }
                controllerFuture.value = controller
                attach(controller)
            }
        }

        private fun attach(controller: MediaController) {
            controller.addListener(
                object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _isPlaying.value = isPlaying
                        if (!isPlaying) saveHistory(force = true)
                        startOrStopHistoryTicker(isPlaying)
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_ENDED || playbackState == Player.STATE_IDLE) {
                            saveHistory(force = true)
                        }
                    }

                    override fun onMediaItemTransition(
                        mediaItem: MediaItem?,
                        reason: Int,
                    ) {
                        _currentMediaId.value = mediaItem?.mediaId
                        lastSavedPosition = 0
                        saveHistory(force = true)
                    }

                    override fun onTracksChanged(tracks: Tracks) {
                        _tracks.value = tracks
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        // Recovery lives in the service; the UI listens here for messaging only.
                        _error.value = error.message
                    }
                },
            )
            appScope.launch(Dispatchers.Main) {
                while (isActive && controllerFuture.value === controller) {
                    _positionMs.value = controller.currentPosition.coerceAtLeast(0)
                    _durationMs.value = controller.duration.takeIf { it > 0 } ?: 0
                    delay(250)
                }
            }
        }

        private fun startOrStopHistoryTicker(playing: Boolean) {
            historyJob?.cancel()
            if (!playing) return
            historyJob =
                appScope.launch(Dispatchers.Main) {
                    while (isActive) {
                        delay(5_000)
                        saveHistory(force = false)
                    }
                }
        }

        private fun saveHistory(force: Boolean) {
            val controller = controllerFuture.value ?: return
            val meta = metaRegistry.get(controller.currentMediaItem?.mediaId) ?: return
            val position = controller.currentPosition.coerceAtLeast(0)
            if (!force && position - lastSavedPosition < 4_000) return
            lastSavedPosition = position
            appScope.launch(Dispatchers.IO) {
                historyRepository.save(meta, position, controller.duration.takeIf { it > 0 } ?: 0L)
            }
        }

        /**
         * Prepares playback. Returns false (with a user-visible error) when the queue
         * is empty or the start position is out of range instead of letting Media3
         * throw an IllegalArgumentException.
         */
        suspend fun setPlaylist(
            items: List<PlaybackItem>,
            startIndex: Int,
            startMs: Long,
            speed: Float,
        ): Boolean =
            withContext(Dispatchers.Main) {
                if (items.isEmpty()) {
                    _error.value = "no playable streams were found for this item"
                    return@withContext false
                }
                _error.value = null
                val controller = awaitController()
                items.forEach { item ->
                    metaRegistry.put(
                        PlaybackMeta(
                            mediaId = item.mediaId,
                            extensionId = item.extensionId,
                            title = item.title,
                            cover = item.cover,
                            baseUrl = item.baseUrl,
                            quality = item.streamRef.quality,
                            itemUrl = item.itemUrl,
                        ),
                    )
                    headersRegistry.register(item.mediaId, item.streamRef.url, item.baseUrl, item.streamRef.headers)
                }
                _playlist.value = items.map { it.mediaId }
                val mediaItems =
                    items.map { item ->
                        MediaItemFactory.build(item.streamRef, item.mediaId, item.title, item.streamRef.quality)
                    }
                val safeIndex = startIndex.coerceIn(0, mediaItems.lastIndex)
                val safeStartMs = startMs.coerceAtLeast(0L)
                controller.setMediaItems(mediaItems, safeIndex, safeStartMs)
                controller.setPlaybackSpeed(speed)
                controller.prepare()
                controller.play()
                true
            }

        /**
         * Appends lazily resolved episodes to the end of the current queue so the
         * player can auto-advance without waiting for every stream to resolve up front.
         */
        suspend fun append(items: List<PlaybackItem>): Boolean =
            withContext(Dispatchers.Main) {
                if (items.isEmpty()) return@withContext false
                val controller = controllerFuture.value ?: return@withContext false
                items.forEach { item ->
                    metaRegistry.put(
                        PlaybackMeta(
                            mediaId = item.mediaId,
                            extensionId = item.extensionId,
                            title = item.title,
                            cover = item.cover,
                            baseUrl = item.baseUrl,
                            quality = item.streamRef.quality,
                            itemUrl = item.itemUrl,
                        ),
                    )
                    headersRegistry.register(item.mediaId, item.streamRef.url, item.baseUrl, item.streamRef.headers)
                    controller.addMediaItem(MediaItemFactory.build(item.streamRef, item.mediaId, item.title, item.streamRef.quality))
                }
                _playlist.value = _playlist.value + items.map { it.mediaId }
                true
            }

        suspend fun refreshStream(
            mediaId: String,
            positionMs: Long,
        ): Boolean =
            withContext(Dispatchers.Main) {
                val controller = awaitController()
                val index = _playlist.value.indexOf(mediaId)
                if (index < 0) return@withContext false
                val refreshed = runCatching { streamRefresher.resolve(mediaId) }.getOrNull() ?: return@withContext false
                if (refreshed.url.isBlank()) return@withContext false
                headersRegistry.register(mediaId, refreshed.url, refreshedBaseUrl(mediaId), refreshed.headers)
                val meta = metaRegistry.get(mediaId) ?: return@withContext false
                controller.replaceMediaItem(
                    index,
                    MediaItemFactory.build(refreshed, mediaId, meta.title, refreshed.quality),
                )
                controller.seekTo(index, positionMs)
                controller.prepare()
                controller.play()
                true
            }

        private suspend fun refreshedBaseUrl(mediaId: String): String = metaRegistry.get(mediaId)?.baseUrl ?: ""

        fun notifyPlaybackError(message: String) {
            _error.value = message
        }

        suspend fun playPause() = withContext(Dispatchers.Main) { awaitController().let { if (it.isPlaying) it.pause() else it.play() } }

        suspend fun seekTo(positionMs: Long) = withContext(Dispatchers.Main) { awaitController().seekTo(positionMs) }

        suspend fun next() = withContext(Dispatchers.Main) { awaitController().seekToNextMediaItem() }

        suspend fun previous() = withContext(Dispatchers.Main) { awaitController().seekToPreviousMediaItem() }

        suspend fun setSpeed(speed: Float) =
            withContext(Dispatchers.Main) {
                _speed.value = speed
                awaitController().setPlaybackSpeed(speed)
            }

        suspend fun applyTrackParameters(builder: TrackSelectionParameters.Builder.() -> Unit) =
            withContext(Dispatchers.Main) {
                awaitController().trackSelectionParameters =
                    awaitController().trackSelectionParameters
                        .buildUpon().apply(builder).build()
            }

        suspend fun releaseMeta() {
            metaRegistry.clear()
            headersRegistry.clear()
        }

        private suspend fun awaitController(): MediaController {
            var waited = 0L
            while (controllerFuture.value == null && waited < 8_000) {
                delay(50)
                waited += 50
            }
            return controllerFuture.value
                ?: throw IllegalStateException("the player service is still starting - try again in a moment")
        }

        fun controllerOrNull(): MediaController? = controllerFuture.value
    }
