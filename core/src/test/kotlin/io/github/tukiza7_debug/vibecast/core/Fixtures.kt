package io.github.tukiza7_debug.vibecast.core

import java.io.File

object Fixtures {
    fun read(name: String): String {
        val url = Fixtures::class.java.classLoader.getResource(name) ?: error("fixture not found: $name")
        return File(url.toURI()).readText()
    }
}
