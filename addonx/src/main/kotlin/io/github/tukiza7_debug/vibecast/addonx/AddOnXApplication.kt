package io.github.tukiza7_debug.vibecast.addonx

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import io.github.tukiza7_debug.vibecast.addonx.ui.analyzer.AppContextHolder

@HiltAndroidApp
class AddOnXApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppContextHolder.init(this)
    }
}
