package io.github.tukiza7_debug.vibecast.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore by preferencesDataStore(name = "vibecast_settings")

@Singleton
class SettingsStore
    @Inject
    constructor(
        @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
    ) {
        private object Keys {
            val theme = stringPreferencesKey("theme")
            val defaultExtension = stringPreferencesKey("default_extension")
            val defaultSpeed = stringPreferencesKey("default_speed")
            val defaultQuality = stringPreferencesKey("default_quality")
            val skipVersion = stringPreferencesKey("skip_version")
            val autoUpdateCheck = booleanPreferencesKey("auto_update_check")
            val updateEtag = stringPreferencesKey("update_etag")
            val lastUpdateCheck = longPreferencesKey("last_update_check")
        }

        private object Defaults {
            const val THEME_DARK = "dark"
            const val QUALITY_AUTO = "auto"
            const val SPEED_1X = "1.0"
        }

        val theme: Flow<String> = context.settingsDataStore.data.map { it[Keys.theme] ?: Defaults.THEME_DARK }
        val defaultExtensionId: Flow<String?> = context.settingsDataStore.data.map { it[Keys.defaultExtension] }
        val defaultSpeed: Flow<Float> =
            context.settingsDataStore.data.map {
                it[Keys.defaultSpeed]?.toFloatOrNull() ?: Defaults.SPEED_1X.toFloat()
            }
        val defaultQuality: Flow<String> =
            context.settingsDataStore.data.map {
                it[Keys.defaultQuality] ?: Defaults.QUALITY_AUTO
            }
        val autoUpdateCheck: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.autoUpdateCheck] ?: true }
        val skippedVersion: Flow<String?> = context.settingsDataStore.data.map { it[Keys.skipVersion] }
        val updateEtag: Flow<String?> = context.settingsDataStore.data.map { it[Keys.updateEtag] }
        val lastUpdateCheck: Flow<Long> = context.settingsDataStore.data.map { it[Keys.lastUpdateCheck] ?: 0L }

        suspend fun setTheme(value: String) = edit { it[Keys.theme] = value }

        suspend fun setDefaultExtensionId(id: String?) =
            edit {
                if (id == null) it.remove(Keys.defaultExtension) else it[Keys.defaultExtension] = id
            }

        suspend fun setDefaultSpeed(value: Float) = edit { it[Keys.defaultSpeed] = value.toString() }

        suspend fun setDefaultQuality(value: String) = edit { it[Keys.defaultQuality] = value }

        suspend fun setAutoUpdateCheck(value: Boolean) = edit { it[Keys.autoUpdateCheck] = value }

        suspend fun setSkippedVersion(version: String?) =
            edit {
                if (version == null) it.remove(Keys.skipVersion) else it[Keys.skipVersion] = version
            }

        suspend fun setUpdateEtag(etag: String?) =
            edit {
                if (etag == null) it.remove(Keys.updateEtag) else it[Keys.updateEtag] = etag
            }

        suspend fun setLastUpdateCheck(at: Long) = edit { it[Keys.lastUpdateCheck] = at }

        suspend fun clearCache() {
            context.cacheDir.deleteRecursively()
        }

        private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
            context.settingsDataStore.edit { block(it) }
        }
    }
