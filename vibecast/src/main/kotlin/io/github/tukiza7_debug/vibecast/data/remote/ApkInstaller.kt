package io.github.tukiza7_debug.vibecast.data.remote

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Runs the PackageInstaller flow: verifies the "install unknown apps" toggle, streams
 * the APK into a session and reports the session status back to the caller.
 */
@Singleton
class ApkInstaller @Inject constructor(
    @ApplicationContext private val context: Context,
    private val updateRepository: UpdateRepository,
) {

    sealed class InstallOutcome {
        data class Success(val message: String) : InstallOutcome()
        data class PendingUserAction(val message: String) : InstallOutcome()
        data class Failure(val reason: String) : InstallOutcome()
    }

    suspend fun install(activity: Activity, release: UpdateRepository.Release, onProgress: (String) -> Unit): InstallOutcome {
        val pm = context.packageManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !pm.canRequestPackageInstalls()) {
            activity.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")),
            )
            return InstallOutcome.PendingUserAction(
                "Allow installing unknown apps for this app, then tap Install again",
            )
        }
        onProgress("Downloading ${release.apkName}…")
        val apk = try {
            val checksums = updateRepository.fetchChecksums(release.checksumsUrl)
            updateRepository.download(release, checksums)
        } catch (e: Exception) {
            return InstallOutcome.Failure(e.message ?: "download failed")
        }
        onProgress("Installing…")
        return awaitSessionStatus { commit(apk) }
    }

    private suspend fun awaitSessionStatus(commit: () -> Unit): InstallOutcome =
        suspendCancellableCoroutine { continuation ->
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(receiverContext: Context, intent: Intent) {
                    val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
                    val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                    val outcome = when (status) {
                        PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                            @Suppress("DEPRECATION")
                            val confirm: Intent? = intent.getParcelableExtra(Intent.EXTRA_INTENT)
                            confirm?.let { toConfirm ->
                                context.startActivity(toConfirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }
                            InstallOutcome.PendingUserAction("Confirm the install dialog to finish")
                        }
                        PackageInstaller.STATUS_SUCCESS -> InstallOutcome.Success("Update installed")
                        else -> InstallOutcome.Failure(message ?: "installer returned status $status")
                    }
                    runCatching { context.unregisterReceiver(this) }
                    if (continuation.isActive) continuation.resumeWith(Result.success(outcome))
                }
            }
            val filter = IntentFilter(ACTION_INSTALL_STATUS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                context.registerReceiver(receiver, filter)
            }
            runCatching { commit() }.onFailure { e ->
                runCatching { context.unregisterReceiver(receiver) }
                if (continuation.isActive) {
                    continuation.resumeWith(Result.success(InstallOutcome.Failure(e.message ?: "commit failed")))
                }
            }
        }

    private fun commit(apk: File) {
        val installer = context.packageManager.packageInstaller
        val sessionId = installer.createSession(
            PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL),
        )
        installer.openSession(sessionId).use { session ->
            session.openWrite(apk.name, 0, apk.length()).use { out ->
                apk.inputStream().use { input -> input.copyTo(out) }
                session.fsync(out)
            }
            val statusIntent = PendingIntent.getBroadcast(
                context,
                0,
                Intent(ACTION_INSTALL_STATUS).setPackage(context.packageName),
                PendingIntent.FLAG_IMMUTABLE,
            )
            session.commit(statusIntent.intentSender)
        }
    }

    companion object {
        const val ACTION_INSTALL_STATUS = "io.github.tukiza7_debug.vibecast.INSTALL_STATUS"
    }
}
