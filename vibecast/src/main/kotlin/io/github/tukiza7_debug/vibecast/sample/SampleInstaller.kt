package io.github.tukiza7_debug.vibecast.sample

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.tukiza7_debug.vibecast.data.repo.ExtensionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Installs the Internet Archive public-domain sample bundled in the APK assets.
 * The JSON goes through the exact same validation and version rules as any import.
 *
 * Returns null when the sample is already installed at the same or a newer version.
 */
@Singleton
class SampleInstaller
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val repository: ExtensionRepository,
    ) {
        suspend fun install(): String? =
            withContext(Dispatchers.IO) {
                val json =
                    runCatching {
                        context.assets.open(SAMPLE_ASSET).bufferedReader().use { it.readText() }
                    }.getOrElse {
                        throw IllegalStateException("the bundled sample is missing from this build: ${it.message}")
                    }
                val existing = repository.get(SAMPLE_ID)
                try {
                    repository.importJson(json, "bundled sample")
                } catch (e: io.github.tukiza7_debug.vibecast.data.repo.ImportErrorException) {
                    // Same or newer sample already present: nothing to do, not an error.
                    if (e.error !is io.github.tukiza7_debug.vibecast.data.repo.ExtensionRepository.ImportError.Outdated) throw e
                }
                if (existing != null) null else "installed"
            }

        private companion object {
            const val SAMPLE_ASSET = "sample/internet-archive.json"
            const val SAMPLE_ID = "org.archive.publicdomain"
        }
    }
