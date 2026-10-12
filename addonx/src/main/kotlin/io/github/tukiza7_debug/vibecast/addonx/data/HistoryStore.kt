package io.github.tukiza7_debug.vibecast.addonx.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.historyDataStore by preferencesDataStore(name = "addonx_history")

/** One analyzed link in the Auto Import history. */
@Serializable
data class HistoryEntry(
    val url: String,
    val host: String,
    val title: String,
    val analyzedAtMs: Long,
    val confidence: String,
    val pageType: String,
)

/** One exported extension kept in My Builds. */
@Serializable
data class SavedBuild(
    val name: String,
    val id: String,
    val json: String,
    val unixCode: String,
    val createdAtMs: Long,
    val sourceUrl: String,
)

private val json =
    Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

/**
 * Recent Auto Import links and exported builds, persisted as small JSON lists.
 * Kept local-only; nothing here ever leaves the device except the exported JSON.
 */
@Singleton
class HistoryStore
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private object Keys {
            val recents = stringPreferencesKey("recent_links")
            val builds = stringPreferencesKey("saved_builds")
            val onboardingDone = booleanPreferencesKey("onboarding_done")
        }

        val recents: Flow<List<HistoryEntry>> =
            context.historyDataStore.data.map { prefs -> decodeList(prefs[Keys.recents]) }

        val builds: Flow<List<SavedBuild>> =
            context.historyDataStore.data.map { prefs -> decodeList(prefs[Keys.builds]) }

        val onboardingDone: Flow<Boolean> = context.historyDataStore.data.map { it[Keys.onboardingDone] ?: false }

        suspend fun addRecent(entry: HistoryEntry) {
            edit { prefs ->
                val current = decodeList<HistoryEntry>(prefs[Keys.recents]).filterNot { it.url == entry.url }
                prefs[Keys.recents] = json.encodeToString(listOf(entry) + current.take(MAX_RECENTS - 1))
            }
        }

        suspend fun removeRecent(url: String) {
            edit { prefs ->
                val current = decodeList<HistoryEntry>(prefs[Keys.recents]).filterNot { it.url == url }
                prefs[Keys.recents] = json.encodeToString(current)
            }
        }

        suspend fun clearRecents() {
            edit { it.remove(Keys.recents) }
        }

        suspend fun addBuild(build: SavedBuild) {
            edit { prefs ->
                val current = decodeList<SavedBuild>(prefs[Keys.builds]).filterNot { it.id == build.id && it.json == build.json }
                prefs[Keys.builds] = json.encodeToString((listOf(build) + current).take(MAX_BUILDS))
            }
        }

        suspend fun removeBuild(build: SavedBuild) {
            edit { prefs ->
                val current = decodeList<SavedBuild>(prefs[Keys.builds]).filterNot { it.createdAtMs == build.createdAtMs }
                prefs[Keys.builds] = json.encodeToString(current)
            }
        }

        suspend fun clearBuilds() {
            edit { it.remove(Keys.builds) }
        }

        suspend fun setOnboardingDone() {
            edit { it[Keys.onboardingDone] = true }
        }

        private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
            context.historyDataStore.edit { block(it) }
        }

        private inline fun <reified T> decodeList(raw: String?): List<T> =
            raw?.let {
                runCatching { json.decodeFromString<List<T>>(it) }.getOrDefault(emptyList())
            } ?: emptyList()

        private companion object {
            const val MAX_RECENTS = 20
            const val MAX_BUILDS = 50
        }
    }
