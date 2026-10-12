package io.github.tukiza7_debug.vibecast.player

import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dagger.hilt.android.AndroidEntryPoint
import io.github.tukiza7_debug.vibecast.ui.player.PlayerScreen
import io.github.tukiza7_debug.vibecast.ui.theme.VibeCastTheme
import javax.inject.Inject

@AndroidEntryPoint
class PlayerActivity : ComponentActivity() {
    @Inject lateinit var playerConnection: PlayerConnection

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val speed by playerConnection.speed.collectAsState()
            VibeCastTheme(themeMode = "dark") {
                PlayerScreen(
                    onFinish = { finish() },
                    onEnterPip = { enterPip() },
                    speed = speed,
                )
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (playerConnection.isPlaying.value) {
            enterPip()
        }
    }

    private fun enterPip() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) return
        val params =
            PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
        runCatching { enterPictureInPictureMode(params) }
    }
}
