package io.github.tukiza7_debug.vibecast.ui.crash

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.tukiza7_debug.vibecast.crash.CrashReporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Shows the last captured crash (if unseen) once per launch with Share / Copy /
 * Dismiss actions so non-technical users can send the log to the developer from
 * any chat app - no adb required.
 */
@Composable
fun CrashReportHost(snackbarHostState: SnackbarHostState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var report by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val text =
            withContext(Dispatchers.IO) {
                if (CrashReporter.hasUnseenCrash(context)) CrashReporter.readCrash(context) else null
            }
        if (text != null) report = text
    }

    val current = report ?: return
    AlertDialog(
        onDismissRequest = {
            CrashReporter.markSeen(context)
            report = null
        },
        title = { Text("Crash detected") },
        text = {
            Column {
                Text(
                    "VibeCast closed unexpectedly. Sharing the log below helps fix the exact cause - it contains no personal data.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = CrashReporter.preview(current),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                            .height(180.dp)
                            .verticalScroll(rememberScrollState())
                            .horizontalScroll(rememberScrollState())
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val send =
                        Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "VibeCast crash log")
                            putExtra(Intent.EXTRA_TEXT, current)
                        }
                    context.startActivity(Intent.createChooser(send, "Share crash log"))
                },
            ) { Text("Share log") }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("VibeCast crash log", current))
                    CrashReporter.markSeen(context)
                    report = null
                    scope.launch { snackbarHostState.showSnackbar("Crash log copied") }
                },
            ) { Text("Copy log") }
        },
    )
}
