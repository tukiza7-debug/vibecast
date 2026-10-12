package io.github.tukiza7_debug.vibecast.addonx.data

import io.github.tukiza7_debug.vibecast.addonx.analyzer.ExtensionBuilder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds the extension draft currently being worked on so the Auto Import result
 * screen can hand it to the analyzer (edit / test / export) without losing state.
 */
@Singleton
class DraftSession
    @Inject
    constructor() {
        data class Session(
            val draft: ExtensionBuilder.Draft,
            val fromAutoImport: Boolean,
            val openTestTab: Boolean = false,
        )

        private val _session = MutableStateFlow<Session?>(null)
        val session: StateFlow<Session?> = _session.asStateFlow()

        fun open(
            draft: ExtensionBuilder.Draft,
            fromAutoImport: Boolean,
            openTestTab: Boolean = false,
        ) {
            _session.value = Session(draft, fromAutoImport, openTestTab)
        }

        /** Consumes the test-tab request flag without dropping the draft. */
        fun consumeTestTabRequest(): Boolean {
            val wants = _session.value?.openTestTab == true
            _session.value = _session.value?.copy(openTestTab = false)
            return wants
        }

        fun clear() {
            _session.value = null
        }
    }
