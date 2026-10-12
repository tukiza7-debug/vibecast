package io.github.tukiza7_debug.vibecast.util

/** Semver helpers for versionName comparison and extension version rules. */
object SemVer {
    fun parse(versionName: String): Triple<Int, Int, Int>? {
        val core = versionName.substringBefore('-').substringBefore('+')
        val parts = core.split('.')
        if (parts.size !in 2..3) return null
        val major = parts[0].toIntOrNull() ?: return null
        val minor = parts[1].toIntOrNull() ?: return null
        val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
        return Triple(major, minor, patch)
    }

    /** Returns positive when a is newer than b. */
    fun compare(
        a: String,
        b: String,
    ): Int {
        val pa = parse(a) ?: return 0
        val pb = parse(b) ?: return 0
        val byMajor = pa.first.compareTo(pb.first)
        if (byMajor != 0) return byMajor
        val byMinor = pa.second.compareTo(pb.second)
        if (byMinor != 0) return byMinor
        return pa.third.compareTo(pb.third)
    }

    fun extensionVersionValue(version: Int): Int = version
}
