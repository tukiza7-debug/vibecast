package io.github.tukiza7_debug.vibecast.player

import androidx.media3.common.PlaybackException
import androidx.media3.datasource.HttpDataSource

/**
 * Classifies playback errors and decides the recovery path: retry with backoff for
 * network failures, one stream re-resolution for expired or forbidden URLs, otherwise
 * surface a user-facing error.
 */
object PlaybackRecovery {

    enum class Action { RETRY_NETWORK, RESOLVE_STREAM_AGAIN, FAIL }

    data class Policy(
        val maxNetworkRetries: Int = 2,
        val maxStreamRefreshes: Int = 1,
    )

    fun decide(
        errorCode: Int,
        httpStatusCode: Int?,
        networkRetries: Int,
        streamRefreshes: Int,
        policy: Policy = Policy(),
    ): Action = when {
        isExpiredOrForbidden(errorCode, httpStatusCode) ->
            if (streamRefreshes < policy.maxStreamRefreshes) Action.RESOLVE_STREAM_AGAIN else Action.FAIL
        isNetwork(errorCode) ->
            if (networkRetries < policy.maxNetworkRetries) Action.RETRY_NETWORK else Action.FAIL
        else -> Action.FAIL
    }

    internal fun isNetwork(errorCode: Int): Boolean = errorCode in NETWORK_CODES

    internal fun isExpiredOrForbidden(errorCode: Int, httpStatusCode: Int?): Boolean {
        if (httpStatusCode in EXPIRED_CODES) return true
        return errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS && httpStatusCode in EXPIRED_CODES
    }

    /** Unwraps the HTTP status from an OkHttp-backed playback failure, if present. */
    fun httpStatusCodeOf(cause: Throwable?): Int? {
        var current: Throwable? = cause
        repeat(4) {
            val http = current as? HttpDataSource.InvalidResponseCodeException ?: run {
                current = current?.cause
                return@repeat
            }
            return http.responseCode
        }
        return null
    }

    private val NETWORK_CODES = setOf(
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        PlaybackException.ERROR_CODE_TIMEOUT,
    )

    private val EXPIRED_CODES = setOf(403, 410, 419, 440)
}
