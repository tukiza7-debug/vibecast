package io.github.tukiza7_debug.vibecast.importcode

import android.content.ClipboardManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.tukiza7_debug.vibecast.core.codes.CodeDetector
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Bus for import confirmations: deep links, shared text, clipboard and AddOnX inserts. */
@Singleton
class ImportPromptBus @Inject constructor() {

    data class Prompt(val code: String, val origin: String)

    data class Added(val extensionId: String, val name: String, val version: Int, val undoable: Boolean)

    private val _prompts = MutableSharedFlow<Prompt>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val prompts: SharedFlow<Prompt> = _prompts.asSharedFlow()

    private val _additions = MutableSharedFlow<Added>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val additions: SharedFlow<Added> = _additions.asSharedFlow()

    fun post(code: String, origin: String) {
        _prompts.tryEmit(Prompt(code, origin))
    }

    fun postAdded(extensionId: String, name: String, version: Int, undoable: Boolean) {
        _additions.tryEmit(Added(extensionId, name, version, undoable))
    }
}

@Singleton
class ClipboardCodeWatcher @Inject constructor(@ApplicationContext private val context: Context) {

    private var lastSeen: String? = null

    /** Reads the clipboard only while the app is in the foreground (called from ON_RESUME). */
    fun detectForegroundCode(): String? {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
        val text = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString() ?: return null
        val code = CodeDetector.findInText(text) ?: return null
        if (code == lastSeen) return null
        lastSeen = code
        return code
    }

    fun reset() {
        lastSeen = null
    }
}
