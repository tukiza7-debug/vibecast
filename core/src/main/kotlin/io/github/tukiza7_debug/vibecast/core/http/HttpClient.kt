package io.github.tukiza7_debug.vibecast.core.http

data class HttpRequest(
    val url: String,
    val method: String = "GET",
    val headers: Map<String, String> = emptyMap(),
    val timeoutMs: Long = 15_000,
)

data class HttpResponse(
    val code: Int,
    val body: ByteArray,
    val contentType: String?,
) {
    fun bodyText(): String = String(body, Charsets.UTF_8)
}

/** Blocking HTTP port; callers run it off the main thread. */
interface HttpClient {
    fun fetch(request: HttpRequest): HttpResponse
}

/** Lets the engine (and the analyzer) skip requests that ad rules block. */
interface HostFilter {
    fun isBlocked(url: String): Boolean
}

object NoopHostFilter : HostFilter {
    override fun isBlocked(url: String): Boolean = false
}
