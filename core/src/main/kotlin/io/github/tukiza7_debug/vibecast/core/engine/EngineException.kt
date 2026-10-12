package io.github.tukiza7_debug.vibecast.core.engine

open class EngineException(
    val step: String,
    val field: String?,
    reason: String,
    cause: Throwable? = null,
) : Exception(buildMessage(step, field, reason), cause) {
    companion object {
        private fun buildMessage(
            step: String,
            field: String?,
            reason: String,
        ) = if (field == null) "[$step] $reason" else "[$step.$field] $reason"
    }
}
