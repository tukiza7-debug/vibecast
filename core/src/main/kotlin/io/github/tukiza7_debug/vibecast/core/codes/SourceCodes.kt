package io.github.tukiza7_debug.vibecast.core.codes

/**
 * GC ("gc1:<owner>/<repo>/<path>[@ref]") and GX ("gx1:<owner>/<repo>[@ref]") source
 * codes. They carry pointers only; content is fetched from raw.githubusercontent.com.
 */
data class GitSource(
    val kind: Kind,
    val owner: String,
    val repo: String,
    val path: String?,
    val ref: String?,
) {
    enum class Kind { SINGLE_FILE, REPOSITORY }

    fun rawUrl(): String {
        val refPart = ref ?: "HEAD"
        val pathPart =
            when (kind) {
                Kind.SINGLE_FILE -> path ?: throw IllegalStateException("single-file source requires a path")
                Kind.REPOSITORY -> "index.json"
            }
        return "https://raw.githubusercontent.com/$owner/$repo/$refPart/$pathPart"
    }

    fun code(): String =
        buildString {
            append(if (kind == Kind.SINGLE_FILE) "gc1:" else "gx1:")
            append(owner).append('/').append(repo)
            path?.let { append('/').append(it) }
            ref?.let { append('@').append(it) }
        }
}

object SourceCodes {
    const val SINGLE_PREFIX = "gc1:"
    const val REPO_PREFIX = "gx1:"

    private val ownerRegex = Regex("^[A-Za-z0-9][A-Za-z0-9-]{0,99}$")
    private val repoRegex = Regex("^[A-Za-z0-9._-]{1,100}$")
    private val refRegex = Regex("^[A-Za-z0-9._/-]{1,200}$")

    fun parse(code: String): GitSource {
        val trimmed = code.trim()
        val (prefix, kind, rest) =
            when {
                trimmed.startsWith(SINGLE_PREFIX) -> Triple(SINGLE_PREFIX, GitSource.Kind.SINGLE_FILE, trimmed.removePrefix(SINGLE_PREFIX))
                trimmed.startsWith(REPO_PREFIX) -> Triple(REPO_PREFIX, GitSource.Kind.REPOSITORY, trimmed.removePrefix(REPO_PREFIX))
                else -> throw CodeException("not a gc1 or gx1 code")
            }
        val (location, ref) = splitRef(rest, prefix)
        val rawSegments = location.split('/')
        if (rawSegments.any { it.isEmpty() }) throw CodeException("$prefix must not contain empty path segments")
        val segments = rawSegments
        val owner = segments.getOrNull(0) ?: throw CodeException("$prefix missing owner")
        val repo = segments.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: throw CodeException("$prefix missing repository")
        if (!ownerRegex.matches(owner)) throw CodeException("$prefix invalid owner '$owner'")
        if (!repoRegex.matches(repo)) throw CodeException("$prefix invalid repository '$repo'")
        if (ref != null && !refRegex.matches(ref)) throw CodeException("$prefix invalid ref '$ref'")
        return when (kind) {
            GitSource.Kind.SINGLE_FILE -> {
                val pathSegments = segments.drop(2)
                if (pathSegments.isEmpty()) throw CodeException("gc1 missing file path")
                pathSegments.forEach { segment ->
                    if (segment == "..") throw CodeException("gc1 path must not contain '..'")
                    if (segment.contains('\\')) throw CodeException("gc1 path must not contain backslashes")
                }
                GitSource(kind, owner, repo, pathSegments.joinToString("/"), ref)
            }
            GitSource.Kind.REPOSITORY -> {
                if (segments.size > 2) throw CodeException("gx1 must be owner/repo[@ref]")
                GitSource(kind, owner, repo, null, ref)
            }
        }
    }

    private fun splitRef(
        rest: String,
        prefix: String,
    ): Pair<String, String?> {
        val at = rest.indexOf('@')
        return if (at < 0) {
            rest to null
        } else {
            val ref = rest.substring(at + 1)
            if (ref.isBlank()) throw CodeException("$prefix empty ref after '@'")
            rest.substring(0, at) to ref
        }
    }
}

object CodeDetector {
    private val codeRegex =
        Regex(
            "(unix1:[A-Za-z0-9_-]+\\.[0-9a-fA-F]{8}|gc1:[A-Za-z0-9._@/-]+|gx1:[A-Za-z0-9._@/-]+)",
        )

    /** Finds the first extension code embedded in arbitrary shared text. */
    fun findInText(text: String): String? {
        val match = codeRegex.find(text) ?: return null
        return match.value.trimEnd('.', ',', ')', ']', '>', '"', '\'')
    }

    fun isCode(text: String): Boolean =
        text.trim().startsWith(UnixCodec.PREFIX) ||
            text.trim().startsWith(SourceCodes.SINGLE_PREFIX) ||
            text.trim().startsWith(SourceCodes.REPO_PREFIX)
}
