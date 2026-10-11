package io.github.tukiza7_debug.vibecast.data.repo

import io.github.tukiza7_debug.vibecast.core.codes.CodeDetector
import io.github.tukiza7_debug.vibecast.core.codes.CodeException
import io.github.tukiza7_debug.vibecast.core.codes.ExtensionIndex
import io.github.tukiza7_debug.vibecast.core.codes.GitSource
import io.github.tukiza7_debug.vibecast.core.codes.IndexJson
import io.github.tukiza7_debug.vibecast.core.codes.SourceCodes
import io.github.tukiza7_debug.vibecast.core.codes.UnixCodec
import io.github.tukiza7_debug.vibecast.core.http.HttpClient
import io.github.tukiza7_debug.vibecast.core.http.HttpRequest
import io.github.tukiza7_debug.vibecast.core.model.Extension
import io.github.tukiza7_debug.vibecast.core.validate.SchemaValidator
import io.github.tukiza7_debug.vibecast.data.db.ExtensionDao
import io.github.tukiza7_debug.vibecast.data.db.ExtensionEntity
import io.github.tukiza7_debug.vibecast.data.prefs.SettingsStore
import io.github.tukiza7_debug.vibecast.util.SemVer
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Stores extensions after strict validation and applies the version rules:
 * a higher version replaces the same id, a lower or equal version never overwrites.
 */
@Singleton
class ExtensionRepository @Inject constructor(
    private val dao: ExtensionDao,
    private val http: HttpClient,
    private val settings: SettingsStore,
) {

    data class ImportResult(
        val extension: Extension,
        val replaced: Boolean,
    )

    sealed class ImportError {
        data class Invalid(val issues: List<String>) : ImportError()
        data class Outdated(val installedVersion: Int, val incomingVersion: Int) : ImportError()
        data class Fetch(val reason: String) : ImportError()
    }

    fun observeAll(): Flow<List<ExtensionEntity>> = dao.observeAll()

    suspend fun get(id: String): ExtensionEntity? = dao.byId(id)

    suspend fun enabled(): List<ExtensionEntity> = dao.enabledList()

    suspend fun parse(json: String): Extension = withContext(Dispatchers.Default) {
        SchemaValidator.decodeAndValidate(json)
    }

    suspend fun importJson(json: String, source: String): ImportResult = withContext(Dispatchers.Default) {
        val extension = try {
            SchemaValidator.decodeAndValidate(json)
        } catch (e: io.github.tukiza7_debug.vibecast.core.codes.ExtensionValidationException) {
            throw ImportErrorException(ImportError.Invalid(e.issues))
        }
        importValidated(extension, json, source, sourceRef = null, gitPath = null, repoOwner = null, repoName = null)
    }

    suspend fun importValidated(
        extension: Extension,
        json: String,
        source: String,
        sourceRef: String?,
        gitPath: String?,
        repoOwner: String?,
        repoName: String?,
    ): ImportResult = withContext(Dispatchers.Default) {
        val existing = dao.byId(extension.id)
        if (existing != null && extension.version <= SemVer.extensionVersionValue(existing.version)) {
            throw ImportErrorException(
                ImportError.Outdated(installedVersion = existing.version, incomingVersion = extension.version),
            )
        }
        val entity = ExtensionEntity(
            id = extension.id,
            name = extension.name,
            version = extension.version,
            json = json,
            enabled = existing?.enabled ?: true,
            source = source,
            sourceRef = sourceRef,
            gitPath = gitPath,
            repoOwner = repoOwner,
            repoName = repoName,
            addedAt = existing?.addedAt ?: System.currentTimeMillis(),
            lastError = null,
        )
        dao.upsert(entity)
        ImportResult(extension, replaced = existing != null)
    }

    suspend fun installBundled(json: String) {
        val result = runCatching { importJson(json, "bundled") }
        result.getOrNull()?.let { imported ->
            settings.setDefaultExtensionId(imported.extension.id)
        }
    }

    suspend fun setEnabled(id: String, enabled: Boolean) = dao.setEnabled(id, enabled)

    suspend fun remove(id: String) = dao.delete(id)

    suspend fun markError(id: String, message: String) {
        val existing = dao.byId(id) ?: return
        dao.upsert(existing.copy(lastError = message.take(300)))
    }

    suspend fun fetchFromGit(source: GitSource): Pair<Extension, String> = withContext(Dispatchers.IO) {
        val response = try {
            http.fetch(HttpRequest(source.rawUrl(), headers = mapOf("User-Agent" to "VibeCast/1.0")))
        } catch (e: Exception) {
            throw ImportErrorException(ImportError.Fetch("network error: ${e.message}"))
        }
        if (response.code !in 200..299) {
            throw ImportErrorException(ImportError.Fetch("HTTP ${response.code} for ${source.rawUrl()}"))
        }
        val text = response.bodyText()
        when (source.kind) {
            GitSource.Kind.SINGLE_FILE -> parse(text) to text
            GitSource.Kind.REPOSITORY -> {
                val index = try {
                    IndexJson.parse(text)
                } catch (e: CodeException) {
                    throw ImportErrorException(ImportError.Fetch(e.message ?: "malformed index.json"))
                }
                // A repository source imports the first valid extension in the index;
                // the picker screen lists all entries for manual choice.
                val entry = index.extensions.firstOrNull()
                    ?: throw ImportErrorException(ImportError.Fetch("index.json lists no extensions"))
                val entryUrl = io.github.tukiza7_debug.vibecast.core.engine.UrlResolver.absolutize(
                    entry.file,
                    "https://raw.githubusercontent.com/${source.owner}/${source.repo}/${source.ref ?: "HEAD"}/",
                )
                val entryResponse = try {
                    http.fetch(HttpRequest(entryUrl, headers = mapOf("User-Agent" to "VibeCast/1.0")))
                } catch (e: Exception) {
                    throw ImportErrorException(ImportError.Fetch("network error: ${e.message}"))
                }
                if (entryResponse.code !in 200..299) {
                    throw ImportErrorException(ImportError.Fetch("HTTP ${entryResponse.code} for $entryUrl"))
                }
                parse(entryResponse.bodyText()) to entryResponse.bodyText()
            }
        }
    }

    suspend fun listRepoExtensions(source: GitSource): List<Pair<IndexEntryInfo, String>> = withContext(Dispatchers.IO) {
        val response = try {
            http.fetch(HttpRequest(source.rawUrl(), headers = mapOf("User-Agent" to "VibeCast/1.0")))
        } catch (e: Exception) {
            throw ImportErrorException(ImportError.Fetch("network error: ${e.message}"))
        }
        if (response.code !in 200..299) throw ImportErrorException(ImportError.Fetch("HTTP ${response.code}"))
        val index = try {
            IndexJson.parse(response.bodyText())
        } catch (e: CodeException) {
            throw ImportErrorException(ImportError.Fetch(e.message ?: "malformed index.json"))
        }
        index.extensions.map { entry ->
            IndexEntryInfo(file = entry.file, sha256 = entry.sha256) to entry.file
        }
    }

    data class IndexEntryInfo(val file: String, val sha256: String?)

    suspend fun importByCode(code: String, origin: String): ImportResult = withContext(Dispatchers.IO) {
        when {
            code.startsWith(UnixCodec.PREFIX) -> {
                val json = try {
                    UnixCodec.decode(code)
                } catch (e: CodeException) {
                    throw ImportErrorException(ImportError.Fetch(e.message ?: "invalid code"))
                }
                importJson(json, origin)
            }
            code.startsWith(SourceCodes.SINGLE_PREFIX) || code.startsWith(SourceCodes.REPO_PREFIX) -> {
                val source = try {
                    SourceCodes.parse(code)
                } catch (e: CodeException) {
                    throw ImportErrorException(ImportError.Fetch(e.message ?: "invalid code"))
                }
                val (extension, json) = fetchFromGit(source)
                importValidated(
                    extension = extension,
                    json = json,
                    source = origin,
                    sourceRef = source.ref,
                    gitPath = source.path,
                    repoOwner = source.owner,
                    repoName = source.repo,
                )
            }
            else -> throw ImportErrorException(ImportError.Fetch("unknown code prefix"))
        }
    }

    suspend fun codeDetails(code: String): PendingImport = withContext(Dispatchers.IO) {
        when {
            code.startsWith(UnixCodec.PREFIX) -> {
                val json = try {
                    UnixCodec.decode(code)
                } catch (e: CodeException) {
                    throw ImportErrorException(ImportError.Fetch(e.message ?: "invalid code"))
                }
                val extension = parse(json)
                PendingImport(
                    code = code,
                    name = extension.name,
                    id = extension.id,
                    version = extension.version,
                    domains = io.github.tukiza7_debug.vibecast.core.util.ExtensionDomains.of(extension),
                    origin = "code",
                )
            }
            else -> {
                val source = SourceCodes.parse(code)
                val (extension, _) = fetchFromGit(source)
                PendingImport(
                    code = code,
                    name = extension.name,
                    id = extension.id,
                    version = extension.version,
                    domains = io.github.tukiza7_debug.vibecast.core.util.ExtensionDomains.of(extension),
                    origin = "github:${source.owner}/${source.repo}",
                )
            }
        }
    }

    data class PendingImport(
        val code: String,
        val name: String,
        val id: String,
        val version: Int,
        val domains: List<String>,
        val origin: String,
    )

    suspend fun checkForUpdates(): List<String> = withContext(Dispatchers.IO) {
        val updated = mutableListOf<String>()
        dao.enabledList().forEach { entity ->
            if (entity.repoOwner == null || entity.repoName == null) return@forEach
            val code = if (entity.gitPath != null) {
                "gc1:${entity.repoOwner}/${entity.repoName}/${entity.gitPath}${entity.sourceRef?.let { "@$it" } ?: ""}"
            } else {
                "gx1:${entity.repoOwner}/${entity.repoName}${entity.sourceRef?.let { "@$it" } ?: ""}"
            }
            val source = runCatching { SourceCodes.parse(code) }.getOrNull() ?: return@forEach
            runCatching {
                val (extension, json) = fetchFromGit(source)
                if (extension.version > entity.version) {
                    importValidated(
                        extension = extension,
                        json = json,
                        source = entity.source,
                        sourceRef = entity.sourceRef,
                        gitPath = entity.gitPath,
                        repoOwner = entity.repoOwner,
                        repoName = entity.repoName,
                    )
                    updated += extension.name
                }
            }
        }
        updated
    }

    suspend fun importIndexEntry(source: GitSource, file: String, sha256: String?): ImportResult = withContext(Dispatchers.IO) {
        val entryUrl = io.github.tukiza7_debug.vibecast.core.engine.UrlResolver.absolutize(
            file,
            "https://raw.githubusercontent.com/${source.owner}/${source.repo}/${source.ref ?: "HEAD"}/",
        )
        val response = try {
            http.fetch(HttpRequest(entryUrl, headers = mapOf("User-Agent" to "VibeCast/1.0")))
        } catch (e: Exception) {
            throw ImportErrorException(ImportError.Fetch("network error: ${e.message}"))
        }
        if (response.code !in 200..299) throw ImportErrorException(ImportError.Fetch("HTTP ${response.code} for $entryUrl"))
        val text = response.bodyText()
        if (sha256 != null && sha256Of(text) != sha256.lowercase()) {
            throw ImportErrorException(ImportError.Fetch("sha256 mismatch for $file"))
        }
        val extension = parse(text)
        importValidated(extension, text, "gx", source.ref, file, source.owner, source.repo)
    }

    companion object {
        fun sha256Of(text: String): String =
            MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }

        fun firstCodeIn(text: String): String? = CodeDetector.findInText(text)
    }
}

class ImportErrorException(val error: ExtensionRepository.ImportError) : Exception() {
    override val message: String
        get() = when (val e = error) {
            is ExtensionRepository.ImportError.Invalid -> e.issues.joinToString("; ")
            is ExtensionRepository.ImportError.Outdated ->
                "installed version ${e.installedVersion} is newer than incoming ${e.incomingVersion}"
            is ExtensionRepository.ImportError.Fetch -> e.reason
        }
}
