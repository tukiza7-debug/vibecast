package io.github.tukiza7_debug.vibecast.adblock.web

import android.os.Message
import android.webkit.WebChromeClient
import android.webkit.WebView

/**
 * WebChromeClient half of the ad blocking pair: blocks window.open and onCreateWindow
 * without a user gesture. Pair it with AdBlockWebViewClient on every WebView.
 */
open class AdBlockWebChromeClient : WebChromeClient() {
    override fun onCreateWindow(
        view: WebView,
        isDialog: Boolean,
        isUserGesture: Boolean,
        resultMsg: Message?,
    ): Boolean = PopupGuard.onCreateWindow(isUserGesture) == PopupGuard.Action.BLOCK
}
