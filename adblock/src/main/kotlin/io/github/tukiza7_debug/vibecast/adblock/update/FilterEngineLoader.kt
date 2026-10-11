package io.github.tukiza7_debug.vibecast.adblock.update

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.tukiza7_debug.vibecast.adblock.FilterEngine
import io.github.tukiza7_debug.vibecast.adblock.prefs.AdBlockSettings
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Loads the filter engine from updated files in filesDir, falling back to bundled assets. */
@Singleton
class FilterEngineLoader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val engine: FilterEngine,
    private val settings: AdBlockSettings,
) {

    suspend fun load(): Unit = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "filters")
        engine.replace(
            lists = FilterEngine.Lists(
                easyList = updatedOrBundled(File(dir, "easylist.txt"), "easylist.txt"),
                easyPrivacy = updatedOrBundled(File(dir, "easyprivacy.txt"), "easyprivacy.txt"),
                customRules = settings.customRules.first(),
                easyListEnabled = settings.easyListEnabled.first(),
                easyPrivacyEnabled = settings.easyPrivacyEnabled.first(),
            ),
            policy = FilterEngine.SitePolicy(disabledHosts = settings.disabledHosts.first()),
        )
    }

    private fun updatedOrBundled(file: File, assetName: String): String? =
        if (file.exists() && file.length() > 0) {
            try {
                file.readText()
            } catch (_: java.io.IOException) {
                bundledText(assetName)
            }
        } else {
            bundledText(assetName)
        }

    private fun bundledText(assetName: String): String? = try {
        context.assets.open("filters/$assetName").bufferedReader().use { it.readText() }
    } catch (_: java.io.IOException) {
        null
    }
}
