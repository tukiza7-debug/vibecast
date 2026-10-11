package io.github.tukiza7_debug.vibecast.data.remote

import android.content.Context
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.tukiza7_debug.vibecast.BuildConfig
import io.github.tukiza7_debug.vibecast.R
import io.github.tukiza7_debug.vibecast.util.SemVer
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Checks the GitHub Releases API with conditional requests, picks the APK matching
 * this device ABI (universal fallback), verifies SHA-256 against the checksums file
 * and the signing certificate against the installed app, then installs through
 * PackageInstaller.
 */
@Singleton
class UpdateRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val settings: io.github.tukiza7_debug.vibecast.data.prefs.SettingsStore,
) {

    data class Release(
        val versionName: String,
        val notes: String,
        val apkUrl: String,
        val apkName: String,
        val checksumsUrl: String?,
    )

    sealed class CheckResult {
        object UpToDate : CheckResult()
        data class Available(val release: Release) : CheckResult()
        data class Failed(val reason: String) : CheckResult()
    }

    private val _installProgress = MutableStateFlow<Int?>(null)
    val installProgress: StateFlow<Int?> = _installProgress.asStateFlow()

    fun releasesUrl(): String =
        "https://api.github.com/repos/${context.getString(R.string.repo_owner)}/vibecast/releases/latest"

    suspend fun check(force: Boolean = false): CheckResult = withContext(Dispatchers.IO) {
        val lastCheck = settings.lastUpdateCheck.first()
        if (!force && System.currentTimeMillis() - lastCheck < TimeUnit.HOURS.toMillis(3)) {
            return@withContext CheckResult.UpToDate
        }
        val request = Request.Builder()
            .url(releasesUrl())
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "VibeCast-Updater/1.0")
            .apply { settings.updateEtag.first()?.let { header("If-None-Match", it) } }
            .build()
        val response = try {
            okHttpClient.newCall(request).execute()
        } catch (e: java.io.IOException) {
            return@withContext CheckResult.Failed("network error: ${e.message}")
        }
        response.use { resp ->
            when {
                resp.code == 304 -> return@withContext CheckResult.UpToDate
                !resp.isSuccessful -> return@withContext CheckResult.Failed("HTTP ${resp.code} from releases API")
            }
            settings.setUpdateEtag(resp.header("ETag"))
            settings.setLastUpdateCheck(System.currentTimeMillis())
            val body = resp.body?.string() ?: return@withContext CheckResult.Failed("empty response")
            val release = parseLatestRelease(body, preferredAbi())
                ?: return@withContext CheckResult.Failed("no installable APK in the latest release")
            if (isNewer(release.versionName)) CheckResult.Available(release) else CheckResult.UpToDate
        }
    }

    fun appNamePrefix(): String = "VibeCast"

    internal fun parseLatestRelease(json: String, deviceAbi: String): Release? {
        val parsed = ReleaseParser.parse(json, deviceAbi, appNamePrefix()) ?: return null
        return Release(
            versionName = parsed.versionName,
            notes = parsed.notes,
            apkUrl = parsed.apkUrl,
            apkName = parsed.apkName,
            checksumsUrl = parsed.checksumsUrl,
        )
    }

    internal suspend fun isNewer(releaseVersion: String): Boolean {
        if (releaseVersion == settings.skippedVersion.first()) return false
        return SemVer.compare(releaseVersion, BuildConfig.VERSION_NAME) > 0
    }

    fun preferredAbi(): String = Build.SUPPORTED_ABIS.firstOrNull() ?: "universal"

    suspend fun fetchChecksums(url: String?): String? = withContext(Dispatchers.IO) {
        url ?: return@withContext null
        val response = try {
            okHttpClient.newCall(Request.Builder().url(url).build()).execute()
        } catch (e: java.io.IOException) {
            return@withContext null
        }
        response.use { it.body?.string() }
    }

    suspend fun download(release: Release, checksums: String?): File = withContext(Dispatchers.IO) {
        val response = okHttpClient.newCall(Request.Builder().url(release.apkUrl).build()).execute()
        response.use { resp ->
            if (!resp.isSuccessful) throw java.io.IOException("HTTP ${resp.code} while downloading APK")
            val target = File(context.cacheDir, "update/${release.apkName}")
            target.parentFile?.mkdirs()
            resp.body?.byteStream()?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: throw java.io.IOException("empty download body")
            if (checksums != null) {
                val expected = checksums.lineSequence()
                    .map { it.trim().split(Regex("\\s+")) }
                    .firstOrNull { it.size == 2 && it[1].equals(release.apkName, ignoreCase = true) }
                    ?.firstOrNull()
                if (expected != null && sha256Of(target) != expected.lowercase()) {
                    target.delete()
                    throw java.io.IOException("SHA-256 mismatch for ${release.apkName}")
                }
            }
            verifySignatureMatchesInstalled(target)
            target
        }
    }

    fun sha256Of(file: File): String = sha256OfBytes(file.inputStream().use { it.readBytes() })

    fun sha256OfBytes(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    fun verifySignatureMatchesInstalled(apk: File) {
        val pm = context.packageManager
        val installedInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
        }
        val installedCerts = installedInfo.signatures ?: emptyArray()
        val apkInfo = pm.getPackageArchiveInfo(apk.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
            ?: throw java.io.IOException("downloaded APK is not readable")
        val apkCerts = apkInfo.signingInfo?.apkContentsSigners ?: throw java.io.IOException("downloaded APK is unsigned")
        val installedDigests = installedCerts.map { sha256OfBytes(it.toByteArray()) }.toSet()
        val apkDigests = apkCerts.map { sha256OfBytes(it.toByteArray()) }.toSet()
        if (installedDigests.intersect(apkDigests).isEmpty()) {
            throw java.io.IOException("signing certificate does not match the installed app")
        }
        if (apkInfo.packageName != context.packageName) {
            throw java.io.IOException(
                "downloaded APK is for package ${apkInfo.packageName}, not ${context.packageName}",
            )
        }
    }

    fun install(apk: File, statusIntentSender: android.content.IntentSender) {
        val installer = context.packageManager.packageInstaller
        val sessionId = installer.createSession(
            PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL),
        )
        installer.openSession(sessionId).use { session ->
            session.openWrite(apk.name, 0, apk.length()).use { out ->
                apk.inputStream().use { input -> input.copyTo(out) }
                session.fsync(out)
            }
            session.commit(statusIntentSender)
        }
        _installProgress.value = 100
    }

    companion object {
        val APK_NAME_REGEX = Regex("-(universal|arm64-v8a|armeabi-v7a|x86_64|x86)\\.apk$")
    }
}
