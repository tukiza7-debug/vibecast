package io.github.tukiza7_debug.vibecast.adblock.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import kotlinx.coroutines.flow.map

private val Context.adblockDataStore by preferencesDataStore(name = "adblock_settings")

class AdBlockSettings @Inject constructor(private val context: Context) {

    private object Keys {
        val enabled = booleanPreferencesKey("enabled")
        val easyList = booleanPreferencesKey("easylist_enabled")
        val easyPrivacy = booleanPreferencesKey("easyprivacy_enabled")
        val customRules = stringPreferencesKey("custom_rules")
        val disabledHosts = stringSetPreferencesKey("disabled_hosts")
        val blockedTotal = longPreferencesKey("blocked_total")
        val easyListEtag = stringPreferencesKey("easylist_etag")
        val easyPrivacyEtag = stringPreferencesKey("easyprivacy_etag")
    }

    val enabled: Flow<Boolean> = context.adblockDataStore.data.map { it[Keys.enabled] ?: true }
    val easyListEnabled: Flow<Boolean> = context.adblockDataStore.data.map { it[Keys.easyList] ?: true }
    val easyPrivacyEnabled: Flow<Boolean> = context.adblockDataStore.data.map { it[Keys.easyPrivacy] ?: true }
    val customRules: Flow<String> = context.adblockDataStore.data.map { it[Keys.customRules] ?: "" }
    val disabledHosts: Flow<Set<String>> = context.adblockDataStore.data.map { it[Keys.disabledHosts] ?: emptySet() }
    val blockedTotal: Flow<Long> = context.adblockDataStore.data.map { it[Keys.blockedTotal] ?: 0L }
    val easyListEtag: Flow<String?> = context.adblockDataStore.data.map { it[Keys.easyListEtag] }
    val easyPrivacyEtag: Flow<String?> = context.adblockDataStore.data.map { it[Keys.easyPrivacyEtag] }

    suspend fun currentBlockedTotal(): Long = blockedTotal.first()

    suspend fun setEnabled(value: Boolean) = edit { it[Keys.enabled] = value }
    suspend fun setEasyListEnabled(value: Boolean) = edit { it[Keys.easyList] = value }
    suspend fun setEasyPrivacyEnabled(value: Boolean) = edit { it[Keys.easyPrivacy] = value }
    suspend fun setCustomRules(value: String) = edit { it[Keys.customRules] = value }
    suspend fun addBlocked(count: Long) = edit { it[Keys.blockedTotal] = (it[Keys.blockedTotal] ?: 0L) + count }
    suspend fun setDisabledHosts(hosts: Set<String>) = edit { it[Keys.disabledHosts] = hosts }
    suspend fun setEtags(easyList: String?, easyPrivacy: String?) = edit {
        easyList?.let { v -> it[Keys.easyListEtag] = v }
        easyPrivacy?.let { v -> it[Keys.easyPrivacyEtag] = v }
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.adblockDataStore.edit { block(it) }
    }
}
