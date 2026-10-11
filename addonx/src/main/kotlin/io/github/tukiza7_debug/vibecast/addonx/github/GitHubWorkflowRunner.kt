package io.github.tukiza7_debug.vibecast.addonx.github

import io.github.tukiza7_debug.vibecast.core.codes.UnixCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Triggers the build-site-apk workflow and downloads the produced APK. A unix code is
 * accepted directly; gc1 codes are resolved to the file content first.
 */
@Singleton
class GitHubWorkflowRunner
    @Inject
    constructor(private val api: GitHubApi) {
        sealed class SiteBuildResult {
            data class Apk(val bytes: ByteArray) : SiteBuildResult()

            data class Failed(val reason: String) : SiteBuildResult()
        }

        suspend fun buildSiteApk(
            token: String,
            owner: String,
            repo: String,
            extensionCode: String,
            appName: String,
            packageId: String,
        ): SiteBuildResult =
            withContext(Dispatchers.IO) {
                val resolvedCode =
                    resolveToUnix(token, owner, repo, extensionCode)
                        ?: return@withContext SiteBuildResult.Failed("could not resolve $extensionCode to an extension payload")
                api.dispatchWorkflow(
                    token = token,
                    owner = owner,
                    repo = repo,
                    workflowFile = WORKFLOW_FILE,
                    inputs =
                        mapOf(
                            "extension_code" to resolvedCode,
                            "app_name" to appName,
                            "package_id" to packageId,
                        ),
                    ref = "main",
                )
                val deadline = System.currentTimeMillis() + 60 * 60 * 1000L
                var run = api.latestRun(token, owner, repo, WORKFLOW_FILE)
                while (run == null || run.status !in setOf("completed")) {
                    if (System.currentTimeMillis() > deadline) {
                        return@withContext SiteBuildResult.Failed("timed out waiting for the workflow run")
                    }
                    kotlinx.coroutines.delay(15_000)
                    run = api.latestRun(token, owner, repo, WORKFLOW_FILE)
                }
                if (run.conclusion != "success") {
                    return@withContext SiteBuildResult.Failed("workflow run ${run.id} ended with ${run.conclusion}")
                }
                val artifactsUrl =
                    run.artifactsUrl
                        ?: return@withContext SiteBuildResult.Failed("run has no artifacts")
                val artifacts = api.artifacts(token, artifactsUrl)
                val apkArtifact =
                    artifacts.firstOrNull { it.name.endsWith("-apk") || it.name.contains("apk") }
                        ?: return@withContext SiteBuildResult.Failed("no APK artifact in run ${run.id}")
                val zip = api.downloadBytes(token, apkArtifact.downloadUrl)
                val apk =
                    extractApkFromZip(zip)
                        ?: return@withContext SiteBuildResult.Failed("artifact archive contains no APK")
                SiteBuildResult.Apk(apk)
            }

        private suspend fun resolveToUnix(
            token: String,
            owner: String,
            repo: String,
            code: String,
        ): String? {
            if (code.startsWith(UnixCodec.PREFIX)) return code
            if (code.startsWith("gc1:")) {
                val source = io.github.tukiza7_debug.vibecast.core.codes.SourceCodes.parse(code)
                val url = source.rawUrl()
                val bytes =
                    try {
                        api.downloadBytes(token, url)
                    } catch (_: GitHubException) {
                        return null
                    }
                return UnixCodec.encode(String(bytes, Charsets.UTF_8))
            }
            return null
        }

        private fun extractApkFromZip(zipBytes: ByteArray): ByteArray? {
            ZipInputStream(zipBytes.inputStream().buffered()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (entry.name.endsWith(".apk")) {
                        val out = ByteArrayOutputStream()
                        zis.copyTo(out)
                        return out.toByteArray()
                    }
                    entry = zis.nextEntry
                }
            }
            return null
        }

        companion object {
            const val WORKFLOW_FILE = "build-site-apk.yml"
        }
    }
