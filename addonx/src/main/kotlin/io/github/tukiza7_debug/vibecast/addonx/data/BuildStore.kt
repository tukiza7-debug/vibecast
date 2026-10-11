package io.github.tukiza7_debug.vibecast.addonx.data

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.buildDataStore by preferencesDataStore(name = "addonx_settings")

/** GitHub token storage backed by EncryptedSharedPreferences. */
@Singleton
class TokenStore @Inject constructor(@ApplicationContext context: Context) {

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "addonx_secure",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun token(): String? = prefs.getString("github_token", null)?.takeIf { it.isNotBlank() }

    fun saveToken(token: String?) {
        prefs.edit().putString("github_token", token).apply()
    }
}

@Singleton
class BuildStore @Inject constructor(@ApplicationContext private val context: Context) {

    private object Keys {
        val repoOwner = stringPreferencesKey("repo_owner")
        val repoName = stringPreferencesKey("repo_name")
        val publishPath = stringPreferencesKey("publish_path")
        val theme = stringPreferencesKey("theme")
    }

    val repoOwner: Flow<String?> = context.buildDataStore.data.map { it[Keys.repoOwner] }
    val repoName: Flow<String?> = context.buildDataStore.data.map { it[Keys.repoName] }
    val publishPath: Flow<String?> = context.buildDataStore.data.map { it[Keys.publishPath] }
    val theme: Flow<String> = context.buildDataStore.data.map { it[Keys.theme] ?: "dark" }

    suspend fun currentRepo(): Pair<String, String>? {
        val owner = repoOwner.first()
        val name = repoName.first()
        return if (owner.isNullOrBlank() || name.isNullOrBlank()) null else owner to name
    }

    suspend fun setRepo(owner: String, name: String) = edit {
        it[Keys.repoOwner] = owner.trim()
        it[Keys.repoName] = name.trim()
    }

    suspend fun setPublishPath(path: String) = edit { it[Keys.publishPath] = path }
    suspend fun setTheme(value: String) = edit { it[Keys.theme] = value }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.buildDataStore.edit { block(it) }
    }
}
