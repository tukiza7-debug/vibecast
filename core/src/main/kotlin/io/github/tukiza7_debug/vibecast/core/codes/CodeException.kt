package io.github.tukiza7_debug.vibecast.core.codes

class CodeException(reason: String) : Exception(reason)

class ExtensionValidationException(val issues: List<String>) :
    Exception("extension rejected: " + issues.joinToString("; "))
