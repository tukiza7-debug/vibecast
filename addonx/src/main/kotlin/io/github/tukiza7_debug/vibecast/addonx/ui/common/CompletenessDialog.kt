package io.github.tukiza7_debug.vibecast.addonx.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.tukiza7_debug.vibecast.addonx.analyzer.ExtensionBuilder
import io.github.tukiza7_debug.vibecast.addonx.ui.theme.ConfidenceHigh
import io.github.tukiza7_debug.vibecast.addonx.ui.theme.ConfidenceLow
import io.github.tukiza7_debug.vibecast.addonx.ui.theme.ConfidenceMedium

/**
 * The pre-export checklist: what the draft contains and what is missing, with a
 * soft warning that never blocks. Export, Send to VibeCast, Publish and Build
 * APK all stay available after "Continue anyway".
 */
@Composable
fun CompletenessDialog(
    draft: ExtensionBuilder.Draft?,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val states = StepCoverage.analyze(draft)
    val warning = StepCoverage.warning(states)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Check your extension") },
        text = {
            Column {
                states.forEach { state ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 4.dp),
                    ) {
                        Icon(
                            when (state.level) {
                                StepCoverage.Level.PRESENT -> Icons.Filled.CheckCircle
                                StepCoverage.Level.PARTIAL -> Icons.Filled.RemoveCircleOutline
                                StepCoverage.Level.MISSING ->
                                    if (state.required) Icons.Filled.RemoveCircleOutline else Icons.Filled.CheckCircle
                            },
                            contentDescription = null,
                            tint =
                                when (state.level) {
                                    StepCoverage.Level.PRESENT -> ConfidenceHigh
                                    StepCoverage.Level.PARTIAL -> ConfidenceMedium
                                    StepCoverage.Level.MISSING ->
                                        if (state.required) ConfidenceLow else ConfidenceMedium
                                },
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                state.name + if (state.required) "" else " (optional)",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                state.note,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (warning != null) {
                    Spacer(Modifier.size(8.dp))
                    Text(
                        warning,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Back to editing") } },
    )
}
