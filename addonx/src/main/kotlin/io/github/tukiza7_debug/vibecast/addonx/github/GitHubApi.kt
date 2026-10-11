package io.github.tukiza7_debug.vibecast.addonx.github

import io.github.tukiza7_debug.vibecast.core.codes.ExtensionIndex
import io.github.tukiza7_debug.vibecast.core.codes.IndexEntry
import io.github.tukiza7_debug.vibecast.core.codes.IndexJson
import io.github.tukiza7_debug.vibecast.core.http.HttpClient
import io.github.tukiza7_debug.vibecast.core.http.HttpRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/** Minimal GitHub REST client: content publish, index maintenance, workflow dispatch, artifacts. */
@Singleton
class GitHubApi
    @Inject
    constructor(private val http: HttpClient) {
        private val json = Json { ignoreUnknownKeys = true }

        data class PublishResult(val gcCode: String, val gxCode: String, val htmlUrl: String)

        data class Run(val id: Long, val status: String, val conclusion: String?, val artifactsUrl: String?)

        data class Artifact(val id: Long, val name: String, val downloadUrl: String)

        suspend fun publishExtension(
            token: String,
            owner: String,
            repo: String,
            path: String,
            extensionJson: String,
            message: String,
        ): PublishResult =
            withContext(Dispatchers.IO) {
                val branch = defaultBranch(token, owner, repo)
                putFile(token, owner, repo, path, extensionJson, message)
                val indexPath = "index.json"
                val existing = runCatching { getFile(token, owner, repo, indexPath) }.getOrNull()
                val entries =
                    existing?.second
                        ?.let { text -> runCatching { IndexJson.parse(text).extensions.toMutableList() }.getOrNull() }
                        ?: mutableListOf()
                entries.removeAll { it.file == path }
                entries.add(IndexEntry(file = path))
                val indexJsonText = IndexJson.encode(ExtensionIndex(schemaVersion = 1, name = repo, extensions = entries))
                putFile(token, owner, repo, indexPath, indexJsonText, "Update index.json for $path")
                PublishResult(
                    gcCode = "gc1:$owner/$repo/$path@$branch",
                    gxCode = "gx1:$owner/$repo@$branch",
                    htmlUrl = "https://github.com/$owner/$repo/blob/$branch/$path",
                )
            }

        suspend fun defaultBranch(
            token: String,
            owner: String,
            repo: String,
        ): String =
            withContext(Dispatchers.IO) {
                val body = getJson(token, "https://api.github.com/repos/$owner/$repo")
                body.string("default_branch") ?: "main"
            }

        /** Returns (sha, content) when the file exists, null otherwise. */
        suspend fun getFile(
            token: String,
            owner: String,
            repo: String,
            path: String,
        ): Pair<String, String>? =
            withContext(Dispatchers.IO) {
                val response =
                    send(
                        token,
                        HttpRequest("https://api.github.com/repos/$owner/$repo/contents/$path"),
                    )
                if (response.code == 404) return@withContext null
                if (response.code !in 200..299) throw GitHubException("GitHub returned HTTP ${response.code} for $path")
                val body = json.parseToJsonElement(response.bodyText()).jsonObject
                val sha = body.string("sha") ?: return@withContext null
                val content = body.string("content") ?: return@withContext null
                sha to String(Base64.getMimeDecoder().decode(content), Charsets.UTF_8)
            }

        private suspend fun putFile(
            token: String,
            owner: String,
            repo: String,
            path: String,
            content: String,
            message: String,
        ) {
            val payload =
                buildJsonObject {
                    put("message", message)
                    put("content", Base64.getEncoder().encodeToString(content.toByteArray(Charsets.UTF_8)))
                    runCatching { getFile(token, owner, repo, path) }.getOrNull()?.let { put("sha", it.first) }
                }
            val response =
                send(
                    token,
                    HttpRequest(
                        url = "https://api.github.com/repos/$owner/$repo/contents/$path",
                        method = "PUT",
                        headers = mapOf("Accept" to "application/vnd.github+json"),
                    ),
                    payload.toString().toByteArray(),
                )
            if (response.code !in 200..299) {
                throw GitHubException("GitHub returned HTTP ${response.code} for PUT $path")
            }
        }

        suspend fun dispatchWorkflow(
            token: String,
            owner: String,
            repo: String,
            workflowFile: String,
            inputs: Map<String, String>,
            ref: String,
        ): Unit =
            withContext(Dispatchers.IO) {
                val payload =
                    buildJsonObject {
                        put("ref", ref)
                        put("inputs", JsonObject(inputs.mapValues { (_, v) -> JsonPrimitive(v) }))
                    }
                val response =
                    send(
                        token,
                        HttpRequest(
                            url = "https://api.github.com/repos/$owner/$repo/actions/workflows/$workflowFile/dispatches",
                            method = "POST",
                            headers = mapOf("Accept" to "application/vnd.github+json"),
                        ),
                        payload.toString().toByteArray(),
                    )
                if (response.code != 204) throw GitHubException("workflow dispatch returned HTTP ${response.code}")
            }

        suspend fun latestRun(
            token: String,
            owner: String,
            repo: String,
            workflowFile: String,
        ): Run? =
            withContext(Dispatchers.IO) {
                val body =
                    getJson(
                        token,
                        "https://api.github.com/repos/$owner/$repo/actions/workflows/$workflowFile/runs?per_page=1",
                    )
                val runs = body["workflow_runs"] as? JsonArray ?: return@withContext null
                val first = runs.firstOrNull() as? JsonObject ?: return@withContext null
                Run(
                    id = first.string("id")?.toLongOrNull() ?: return@withContext null,
                    status = first.string("status") ?: "unknown",
                    conclusion = first.string("conclusion"),
                    artifactsUrl = first.string("artifacts_url"),
                )
            }

        suspend fun artifacts(
            token: String,
            artifactsUrl: String,
        ): List<Artifact> =
            withContext(Dispatchers.IO) {
                val body = getJson(token, artifactsUrl)
                val list = body["artifacts"] as? JsonArray ?: return@withContext emptyList()
                list.mapNotNull { item ->
                    item as? JsonObject ?: return@mapNotNull null
                    Artifact(
                        id = item.string("id")?.toLongOrNull() ?: return@mapNotNull null,
                        name = item.string("name") ?: return@mapNotNull null,
                        downloadUrl = item.string("archive_download_url") ?: return@mapNotNull null,
                    )
                }
            }

        suspend fun downloadBytes(
            token: String,
            url: String,
        ): ByteArray =
            withContext(Dispatchers.IO) {
                val response = send(token, HttpRequest(url))
                if (response.code !in 200..299) throw GitHubException("HTTP ${response.code} for $url")
                response.body
            }

        private suspend fun getJson(
            token: String,
            url: String,
        ): JsonObject {
            val response = send(token, HttpRequest(url, headers = mapOf("Accept" to "application/vnd.github+json")))
            if (response.code !in 200..299) throw GitHubException("GitHub returned HTTP ${response.code} for $url")
            return json.parseToJsonElement(response.bodyText()).jsonObject
        }

        private suspend fun send(
            token: String,
            request: HttpRequest,
            body: ByteArray? = null,
        ): io.github.tukiza7_debug.vibecast.core.http.HttpResponse {
            val withBody = if (body != null) request.copy(body = body) else request
            return http.fetch(
                withBody.copy(
                    headers =
                        withBody.headers +
                            mapOf(
                                "Authorization" to "Bearer $token",
                                "User-Agent" to "AddOnX/1.0",
                            ),
                ),
            )
        }

        private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.content
    }
