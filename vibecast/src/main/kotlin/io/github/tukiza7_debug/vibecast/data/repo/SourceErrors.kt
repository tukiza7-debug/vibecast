package io.github.tukiza7_debug.vibecast.data.repo

import io.github.tukiza7_debug.vibecast.core.engine.EngineException
import java.io.IOException

/**
 * One shared mapping from engine/IO failures to short, human phrases. Technical
 * details stay available through the exception chain; screens show these lines.
 */
fun friendlySourceError(e: Exception): String =
    when {
        e is EngineException -> e.message ?: "step failed"
        e is IOException -> "network problem - check your connection and try again"
        e.message?.contains("timed out", ignoreCase = true) == true -> "the source took too long to respond"
        e.message?.contains("validation failed", ignoreCase = true) == true -> "this extension is invalid or outdated"
        else -> "this source could not be loaded"
    }
