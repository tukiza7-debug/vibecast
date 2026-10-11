package io.github.tukiza7_debug.vibecast.core.http

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class OkHttpFetcher(private val client: OkHttpClient) : HttpClient {
    override fun fetch(request: HttpRequest): HttpResponse {
        val isHead = request.method.equals("HEAD", ignoreCase = true)
        val hasBody = request.method.equals("POST", ignoreCase = true) || request.method.equals("PUT", ignoreCase = true)
        val contentType = (request.headers["Content-Type"] ?: "application/json").toMediaTypeOrNull()
        val body =
            if (hasBody) {
                request.body.toRequestBody(contentType)
            } else {
                null
            }
        val builder = Request.Builder().url(request.url).method(request.method.uppercase(), body)
        request.headers.forEach { (name, value) -> builder.header(name, value) }
        val call = client.newCall(builder.build())
        call.timeout().timeout(request.timeoutMs, TimeUnit.MILLISECONDS)
        call.execute().use { response ->
            val bytes = if (isHead) ByteArray(0) else response.body?.bytes() ?: ByteArray(0)
            return HttpResponse(
                code = response.code,
                body = bytes,
                contentType = response.header("Content-Type"),
            )
        }
    }
}
