package io.github.tukiza7_debug.vibecast.addonx.ui.common

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

private val jsonTokenRegex =
    Regex("""("(?:\\.|[^"\\])*")(\s*:)?|\b(true|false|null)\b|(-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?)""")

/**
 * Colorizes JSON keys, strings, numbers and literals for the export/code preview.
 * Pure tokenization, no parsing: any input is rendered as-is, so invalid JSON
 * still shows verbatim in the default color.
 */
fun highlightJson(json: String): AnnotatedString =
    buildAnnotatedString {
        append(json)
        for (match in jsonTokenRegex.findAll(json)) {
            val start = match.range.first
            val end = match.range.last + 1
            when {
                match.groups[2] != null -> {
                    // "key":  - color the key and the colon
                    addStyle(SpanStyle(color = KeyColor), start, end)
                }
                match.groups[1] != null -> addStyle(SpanStyle(color = StringColor), start, end)
                match.groups[3] != null -> addStyle(SpanStyle(color = LiteralColor), start, end)
                match.groups[4] != null -> addStyle(SpanStyle(color = NumberColor), start, end)
            }
        }
    }

private val KeyColor = Color(0xFF7DD3FC)
private val StringColor = Color(0xFFA3E635)
private val NumberColor = Color(0xFFF5C518)
private val LiteralColor = Color(0xFFF87171)

/** Dark code panel with the highlighted JSON, horizontally scrollable. */
@Composable
fun JsonPreview(
    json: String,
    modifier: Modifier = Modifier,
    maxLines: Int = 16,
) {
    val highlighted = remember(json) { highlightJson(json) }
    val lineLimit =
        remember(json, maxLines) {
            if (json.lines().size <= maxLines) {
                highlighted
            } else {
                val cutoff = json.lines().take(maxLines).joinToString("\n")
                highlightJson("$cutoff\n... (${json.lines().size - maxLines} more lines)")
            }
        }
    Surface(
        color = Color(0xFF0C1220),
        contentColor = Color(0xFFE7EDF6),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(12.dp),
        ) {
            Text(lineLimit, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
        }
    }
}
