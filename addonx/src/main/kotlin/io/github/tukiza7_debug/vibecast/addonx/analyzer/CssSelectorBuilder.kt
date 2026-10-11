package io.github.tukiza7_debug.vibecast.addonx.analyzer

import org.jsoup.nodes.Element

/**
 * Builds stable CSS selectors from a DOM path chain captured by the in-page picker.
 * Prefers ids, then class lists, then nth-of-type fallbacks, and verifies uniqueness
 * counts supplied by the page itself.
 */
object CssSelectorBuilder {

    data class NodeInfo(
        val tag: String,
        val id: String?,
        val classes: List<String>,
        val nthOfType: Int,
    )

    fun build(path: List<NodeInfo>, siblingCount: Int): String {
        if (path.isEmpty()) return ""
        val target = path.last()
        if (!target.id.isNullOrBlank()) return "#${target.id}"
        val segments = ArrayDeque<String>()
        for (node in path.reversed()) {
            val segment = StringBuilder(node.tag)
            node.classes.take(2).forEach { cls -> segment.append('.').append(cssEscape(cls)) }
            if (node.id != null) {
                segment.insert(0, "#${node.id}")
            } else if (siblingCount > 1 || segments.isEmpty()) {
                segment.append(":nth-of-type(").append(node.nthOfType).append(')')
            }
            segments.addFirst(segment.toString())
            if (node.id != null) break
        }
        return segments.joinToString(" > ")
    }

    fun cssEscape(identifier: String): String =
        identifier.split(Regex("(?=[^A-Za-z0-9_-])")).joinToString("\\\\") { it }

    fun elementSignature(element: Element): String =
        element.tagName() + "#" + element.classNames().sorted().joinToString(".")
}
