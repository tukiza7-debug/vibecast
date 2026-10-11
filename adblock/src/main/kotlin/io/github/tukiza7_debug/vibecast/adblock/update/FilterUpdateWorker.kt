package io.github.tukiza7_debug.vibecast.adblock.update

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
import io.github.tukiza7_debug.vibecast.adblock.prefs.AdBlockSettings
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Refreshes EasyList and EasyPrivacy at most once a day. A failed download keeps the
 * previous file, which itself falls back to the bundled snapshot on first run.
 */
@HiltWorker
class FilterUpdateWorker
    @AssistedInject
    constructor(
        @Assisted appContext: Context,
        @Assisted params: WorkerParameters,
        private val fetcher: FilterListFetcher,
        private val settings: AdBlockSettings,
        private val loader: FilterEngineLoader,
    ) : CoroutineWorker(appContext, params) {
        override suspend fun doWork(): Result {
            val dir = java.io.File(applicationContext.filesDir, "filters").apply { mkdirs() }
            var changed = false
            var newEasyListEtag: String? = null
            var newEasyPrivacyEtag: String? = null

            when (val result = fetcher.updateIfChanged(EASYLIST_URL, java.io.File(dir, "easylist.txt"), settings.easyListEtag.first())) {
                is FilterListFetcher.Result.Updated -> {
                    changed = true
                    newEasyListEtag = result.etag
                }
                is FilterListFetcher.Result.Failed -> return Result.retry()
                FilterListFetcher.Result.Unmodified -> Unit
            }
            when (
                val result =
                    fetcher.updateIfChanged(
                        EASYPRIVACY_URL,
                        java.io.File(dir, "easyprivacy.txt"),
                        settings.easyPrivacyEtag.first(),
                    )
            ) {
                is FilterListFetcher.Result.Updated -> {
                    changed = true
                    newEasyPrivacyEtag = result.etag
                }
                is FilterListFetcher.Result.Failed -> return Result.retry()
                FilterListFetcher.Result.Unmodified -> Unit
            }
            if (changed) {
                settings.setEtags(newEasyListEtag, newEasyPrivacyEtag)
                loader.load()
            }
            return Result.success()
        }

        companion object {
            const val EASYLIST_URL = "https://easylist.to/easylist/easylist.txt"
            const val EASYPRIVACY_URL = "https://easylist.to/easylist/easyprivacy.txt"
            const val WORK_NAME = "filter_list_update"

            fun schedule(context: Context) {
                val request =
                    PeriodicWorkRequestBuilder<FilterUpdateWorker>(24, TimeUnit.HOURS)
                        .setConstraints(
                            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                        )
                        .build()
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request,
                )
            }
        }
    }
