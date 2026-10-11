package io.github.tukiza7_debug.vibecast.player

import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dagger.hilt.android.AndroidEntryPoint
import io.github.tukiza7_debug.vibecast.MainActivity
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Holds the ExoPlayer instance and the media session; UI binds through MediaController. */
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    @Inject lateinit var okHttpClient: okhttp3.OkHttpClient

    @Inject lateinit var headersRegistry: HeadersRegistry

    @Inject lateinit var connection: PlayerConnection

    private var mediaSession: MediaSession? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var networkRetries = 0
    private var streamRefreshes = 0

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(HeaderProvidingDataSourceFactory(OkHttpDataSource.Factory(okHttpClient), headersRegistry)),
            )
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        player.addListener(errorListener)
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(
                android.app.PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java),
                    android.app.PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        scope.cancel()
        super.onDestroy()
    }

    private val errorListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            val position = mediaSession?.player?.currentPosition ?: 0
            val action = PlaybackRecovery.decide(
                error.errorCode,
                PlaybackRecovery.httpStatusCodeOf(error.cause),
                networkRetries,
                streamRefreshes,
            )
            val session = mediaSession ?: return
            when (action) {
                PlaybackRecovery.Action.RETRY_NETWORK -> {
                    networkRetries++
                    session.player.seekTo(position)
                    session.player.prepare()
                }
                PlaybackRecovery.Action.RESOLVE_STREAM_AGAIN -> {
                    streamRefreshes++
                    scope.launch {
                        val mediaId = session.player.currentMediaItem?.mediaId
                        val refreshed = if (mediaId == null) false else connection.refreshStream(mediaId, position)
                        if (!refreshed) connection.notifyPlaybackError("stream expired and could not be refreshed")
                    }
                }
                PlaybackRecovery.Action.FAIL -> connection.notifyPlaybackError(error.message ?: "playback failed")
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) {
                networkRetries = 0
                streamRefreshes = 0
            }
        }
    }
}

/** Applies per-stream headers to every request the player makes for that stream. */
class HeaderProvidingDataSourceFactory(
    private val upstream: OkHttpDataSource.Factory,
    private val registry: HeadersRegistry,
) : DataSource.Factory {

    override fun createDataSource(): DataSource = HeaderDataSource(upstream.createDataSource(), registry)
}

private class HeaderDataSource(
    private val wrapped: DataSource,
    private val registry: HeadersRegistry,
) : DataSource by wrapped {

    override fun open(dataSpec: DataSpec): Long {
        val host = HeadersRegistry.hostOf(dataSpec.uri.toString())
        val headers = registry.headersFor(host)
        val effective = if (headers.isEmpty()) {
            dataSpec
        } else {
            val merged = HashMap(headers)
            merged.putAll(dataSpec.httpRequestHeaders)
            dataSpec.buildUpon().setHttpRequestHeaders(merged).build()
        }
        return wrapped.open(effective)
    }

    override fun addTransferListener(transferListener: TransferListener) = wrapped.addTransferListener(transferListener)
}
