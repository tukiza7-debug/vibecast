package io.github.tukiza7_debug.vibecast

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import dagger.hilt.android.HiltAndroidApp
import io.github.tukiza7_debug.vibecast.adblock.update.FilterEngineLoader
import io.github.tukiza7_debug.vibecast.adblock.update.FilterUpdateWorker
import io.github.tukiza7_debug.vibecast.data.repo.ExtensionRepository
import io.github.tukiza7_debug.vibecast.imageloading.CoverHeaderInterceptor
import io.github.tukiza7_debug.vibecast.imageloading.ImageHeaderRegistry
import io.github.tukiza7_debug.vibecast.work.Scheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class VibecastApplication : Application(), Configuration.Provider, ImageLoaderFactory {
    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var filterEngineLoader: FilterEngineLoader

    @Inject lateinit var bundledExtensionInstaller: dagger.Lazy<ExtensionRepository>

    @Inject lateinit var imageHeaderRegistry: ImageHeaderRegistry

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
        appScope.launch {
            // keep per-extension image headers in sync with the installed set
            bundledExtensionInstaller.get().observeAll().collect { entities ->
                imageHeaderRegistry.refresh(entities)
            }
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

    /**
     * Cover/poster loader: dedicated OkHttp client that attaches extension-declared
     * headers (Referer/User-Agent/cookies) per host, a browser-like fallback UA,
     * crossfade and a healthy cache budget so posters stay crisp and instant.
     */
    override fun newImageLoader(): ImageLoader {
        val imageClient =
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .addInterceptor(CoverHeaderInterceptor(imageHeaderRegistry))
                .build()
        return ImageLoader.Builder(this)
            .callFactory(imageClient)
            .crossfade(220)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.18)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(96L * 1024 * 1024)
                    .build()
            }
            .respectCacheHeaders(false)
            .build()
    }
}
