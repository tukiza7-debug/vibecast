package io.github.tukiza7_debug.vibecast.core.codes

class CodeException(reason: String, cause: Throwable? = null) : Exception(reason, cause)

class ExtensionValidationException(val issues: List<String>, cause: Throwable? = null) :
    Exception("extension rejected: " + issues.joinToString("; "), cause)
