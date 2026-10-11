package io.github.tukiza7_debug.vibecast

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import io.github.tukiza7_debug.vibecast.adblock.update.FilterEngineLoader
import io.github.tukiza7_debug.vibecast.adblock.update.FilterUpdateWorker
import io.github.tukiza7_debug.vibecast.data.repo.ExtensionRepository
import io.github.tukiza7_debug.vibecast.work.Scheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class VibecastApplication : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var filterEngineLoader: FilterEngineLoader

    @Inject lateinit var bundledExtensionInstaller: dagger.Lazy<ExtensionRepository>

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            filterEngineLoader.load()
        }
        FilterUpdateWorker.schedule(this)
        Scheduler.scheduleUpdateChecks(this)
        appScope.launch {
            installBundledExtensionIfPresent()
        }
    }

    private suspend fun installBundledExtensionIfPresent() {
        val encoded = getString(R.string.bundled_extension_b64)
        if (encoded.isBlank()) return
        val json =
            try {
                String(android.util.Base64.decode(encoded, android.util.Base64.DEFAULT), Charsets.UTF_8)
            } catch (_: IllegalArgumentException) {
                return
            }
        bundledExtensionInstaller.get().installBundled(json)
    }
}
