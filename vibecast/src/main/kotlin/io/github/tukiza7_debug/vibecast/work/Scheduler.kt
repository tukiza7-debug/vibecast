package io.github.tukiza7_debug.vibecast.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.github.tukiza7_debug.vibecast.data.prefs.SettingsStore
import io.github.tukiza7_debug.vibecast.data.remote.UpdateRepository
import io.github.tukiza7_debug.vibecast.data.repo.ExtensionRepository
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

object Scheduler {
    fun scheduleUpdateChecks(context: Context) {
        val workManager = WorkManager.getInstance(context)
        workManager.enqueueUniquePeriodicWork(
            "app_update_check",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<AppUpdateWorker>(6, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build(),
        )
        workManager.enqueueUniquePeriodicWork(
            "extension_update_check",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<ExtensionUpdateWorker>(24, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build(),
        )
    }
}

@HiltWorker
class AppUpdateWorker
    @AssistedInject
    constructor(
        @Assisted appContext: Context,
        @Assisted params: WorkerParameters,
        private val updateRepository: UpdateRepository,
    ) : CoroutineWorker(appContext, params) {
        override suspend fun doWork(): Result =
            when (val result = updateRepository.check()) {
                is UpdateRepository.CheckResult.Failed -> Result.retry()
                else -> Result.success()
            }
    }

@HiltWorker
class ExtensionUpdateWorker
    @AssistedInject
    constructor(
        @Assisted appContext: Context,
        @Assisted params: WorkerParameters,
        private val extensionRepository: ExtensionRepository,
    ) : CoroutineWorker(appContext, params) {
        override suspend fun doWork(): Result {
            val updated = runCatching { extensionRepository.checkForUpdates() }.getOrDefault(emptyList())
            return Result.success()
        }
    }

/** Periodic extension update runner that keeps the source references pinned. */
@Singleton
class ExtensionUpdateService
    @Inject
    constructor(
        private val extensionRepository: ExtensionRepository,
        private val settings: SettingsStore,
    ) {
        suspend fun runOnce(): List<String> = extensionRepository.checkForUpdates()

        suspend fun shouldCheckAutomatically(): Boolean = settings.autoUpdateCheck.first()
    }
