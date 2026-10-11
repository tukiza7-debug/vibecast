package io.github.tukiza7_debug.vibecast.adblock.web

import io.github.tukiza7_debug.vibecast.adblock.rules.UrlMatcher

/**
 * Pure decision logic for popup and redirect blocking so it can be unit tested
 * without a WebView. Applied from WebViewClient navigation callbacks.
 */
object PopupGuard {

    enum class Action { ALLOW, BLOCK }

    private val safeSchemes = setOf("about", "blob", "data", "javascript", "file")

    fun onCreateWindow(isUserGesture: Boolean): Action =
        if (isUserGesture) Action.ALLOW else Action.BLOCK

    fun onTopLevelNavigation(
        url: String,
        currentHost: String?,
        isUserGesture: Boolean,
        isRedirect: Boolean,
    ): Action {
        val scheme = url.substringBefore(':').lowercase()
        val isWeb = scheme == "http" || scheme == "https"
        if (!isWeb) {
            // intent://, market:// and every other custom scheme must not launch
            // on its own; only an explicit user gesture may trigger it.
            return if (scheme in safeSchemes || isUserGesture) Action.ALLOW else Action.BLOCK
        }
        if (isUserGesture || isRedirect) return Action.ALLOW
        val target = UrlMatcher.hostOf(url) ?: return Action.ALLOW
        val current = currentHost ?: return Action.ALLOW
        return if (sameSite(target, current)) Action.ALLOW else Action.BLOCK
    }

    private fun sameSite(a: String, b: String): Boolean {
        if (a == b) return true
        return a.endsWith(".$b") || b.endsWith(".$a")
    }
}
