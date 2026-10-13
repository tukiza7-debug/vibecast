package io.github.tukiza7_debug.vibecast.crash

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.github.tukiza7_debug.vibecast.BuildConfig
import io.github.tukiza7_debug.vibecast.MainActivity
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Captures uncaught crashes to a local file so the user can share them straight
 * from the app (a dialog on the next launch plus a Settings > Diagnostics row)
 * without needing adb or a PC.
 *
 * The handler chains to the previous handler so the process still dies the way
 * Android expects; the report is only a best-effort file write on the crashing
 * thread (a few KB, wrapped in runCatching so it can never mask the crash).
 */
object CrashReporter {
    const val FILE_NAME = "last-crash.txt"
    private const val PREFS = "crash_reporter"
    private const val KEY_LAST_SEEN = "last_seen_ms"
    private const val MAX_REPORT_CHARS = 64_000

    // Notification constants
    private const val CHANNEL_ID = "vibecast_crashes"
    private const val CHANNEL_NAME = "VibeCast Crashes"
    private const val NOTIFICATION_ID = 1001

    /** Breadcrumb of the last route the app tried to open (set on detail clicks). */
    @Volatile
    var lastRoute: String? = null
        private set

    fun install(context: Context) {
        val appContext = context.applicationContext
        createNotificationChannel(appContext)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // Best-effort: write the crash report and post a notification for user.
            runCatching { write(appContext, thread, throwable) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun recordRoute(route: String?) {
        lastRoute = route
    }

    /** True when a crash report exists that the user has not seen or dismissed yet. */
    fun hasUnseenCrash(context: Context): Boolean {
        val file = File(context.filesDir, FILE_NAME)
        return file.exists() && file.lastModified() > prefs(context).getLong(KEY_LAST_SEEN, 0L)
    }

    fun readCrash(context: Context): String? {
        val file = File(context.filesDir, FILE_NAME)
        return if (file.exists()) runCatching { file.readText() }.getOrNull() else null
    }

    fun markSeen(context: Context) {
        prefs(context).edit().putLong(KEY_LAST_SEEN, System.currentTimeMillis()).apply()
    }

    fun lastCrashDate(context: Context): Long {
        val file = File(context.filesDir, FILE_NAME)
        return if (file.exists()) file.lastModified() else 0L
    }

    private fun prefs(context: Context): SharedPreferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun write(
        context: Context,
        thread: Thread,
        throwable: Throwable,
    ) {
        val report =
            buildReport(
                versionName = BuildConfig.VERSION_NAME,
                versionCode = BuildConfig.VERSION_CODE.toString(),
                androidRelease = Build.VERSION.RELEASE,
                sdkInt = Build.VERSION.SDK_INT,
                manufacturer = Build.MANUFACTURER,
                model = Build.MODEL,
                lastRoute = lastRoute,
                threadName = thread.name,
                stackTrace = stackTraceOf(throwable),
            )
        File(context.filesDir, FILE_NAME).writeText(report.take(MAX_REPORT_CHARS), Charsets.UTF_8)
        // Notify the user that a crash occurred and that they can view/share the log
        runCatching { notifyCrash(context, report) }
    }

    internal fun stackTraceOf(throwable: Throwable): String {
        val writer = StringWriter()
        throwable.printStackTrace(PrintWriter(writer))
        return writer.toString()
    }

    internal fun buildReport(
        versionName: String,
        versionCode: String,
        androidRelease: String,
        sdkInt: Int,
        manufacturer: String,
        model: String,
        lastRoute: String?,
        threadName: String,
        stackTrace: String,
    ): String =
        buildString {
            append("VibeCast ").append(versionName).append(" (").append(versionCode).append(")\n")
            append("Android ").append(androidRelease).append(" (SDK ").append(sdkInt).append("), ")
                .append(manufacturer).append(" ").append(model).append("\n")
            append("Last route: ").append(lastRoute ?: "unknown").append("\n")
            append("Thread: ").append(threadName).append("\n\n")
            append(stackTrace)
        }

    /** Short monospace preview for the crash dialog: header + the exception header lines. */
    internal fun preview(
        report: String,
        lines: Int = 12,
    ): String = report.lines().take(lines).joinToString("\n")

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Notifications when the app crashes so users can view diagnostics"
                }
                val nm = context.getSystemService(NotificationManager::class.java)
                nm?.createNotificationChannel(channel)
            } catch (_: Exception) {
                // ignore channel creation failures
            }
        }
    }

    private fun notifyCrash(context: Context, report: String) {
        // Build an intent that opens the app (MainActivity). The app's launch will show the crash dialog.
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_UPDATE_CURRENT
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, pendingFlags)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("VibeCast crashed")
            .setContentText("Tap to view crash details")
            .setStyle(NotificationCompat.BigTextStyle().bigText(preview(report)))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
    }
}
