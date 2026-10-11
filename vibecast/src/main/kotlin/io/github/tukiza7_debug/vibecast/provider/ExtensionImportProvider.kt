package io.github.tukiza7_debug.vibecast.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.github.tukiza7_debug.vibecast.data.repo.ExtensionRepository
import io.github.tukiza7_debug.vibecast.data.repo.ImportErrorException
import io.github.tukiza7_debug.vibecast.importcode.ImportPromptBus
import kotlinx.coroutines.runBlocking

/**
 * Signature-protected insert point used by AddOnX on the same device. Values must
 * carry "extension_json"; the insert validates before anything is stored.
 */
class ExtensionImportProvider : ContentProvider() {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ProviderEntryPoint {
        fun extensionRepository(): ExtensionRepository

        fun importPromptBus(): ImportPromptBus
    }

    private fun entryPoint(): ProviderEntryPoint =
        EntryPointAccessors.fromApplication(
            context!!.applicationContext,
            ProviderEntryPoint::class.java,
        )

    override fun onCreate(): Boolean = true

    override fun insert(
        uri: Uri,
        values: ContentValues?,
    ): Uri? {
        val json = values?.getAsString("extension_json") ?: return null
        val repository = entryPoint().extensionRepository()
        val bus = entryPoint().importPromptBus()
        try {
            runBlocking {
                val result = repository.importJson(json, "addonx")
                bus.postAdded(result.extension.id, result.extension.name, result.extension.version, undoable = true)
            }
            return uri.buildUpon().appendPath("ok").build()
        } catch (e: ImportErrorException) {
            return uri.buildUpon().appendPath("error").fragment(e.message?.take(200)).build()
        }
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0
}
