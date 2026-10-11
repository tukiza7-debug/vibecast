package io.github.tukiza7_debug.vibecast.addonx.ui.common

import io.github.tukiza7_debug.vibecast.addonx.analyzer.ExtensionBuilder

/**
 * Step completeness for the export checklist. In a builder draft the required
 * steps always exist structurally - what varies is whether their selectors were
 * actually assigned (PRESENT) or left empty (PARTIAL, needs the visual picker).
 * `latest` and `popular` are optional in schemaVersion 1, so a plain MISSING is
 * normal for them and never flagged as a problem.
 */
object StepCoverage {
    enum class Level { PRESENT, PARTIAL, MISSING }

    data class StepState(
        val name: String,
        val level: Level,
        val required: Boolean,
        val note: String,
    )

    fun analyze(draft: ExtensionBuilder.Draft?): List<StepState> {
        if (draft == null) return emptyList()
        return listOf(
            listStep(
                "Search",
                draft.search,
                required = true,
                configured = { it.itemSelector != null || it.jsonPath != null },
            ),
            stepState(
                "Detail",
                required = true,
                present = true,
                configured =
                    (draft.detail.fields["title"]?.selector != null) ||
                        (draft.detail.fields["title"]?.path != null) ||
                        (draft.detail.fields["title"]?.template != null),
            ),
            listStep(
                "Episodes",
                draft.episodes,
                required = true,
                configured = { it.itemSelector != null || it.jsonPath != null },
            ),
            StepState(
                "Video",
                level =
                    when {
                        draft.video.strategy == "regex" && draft.video.pattern != null -> Level.PRESENT
                        draft.video.strategy == "iframe" && draft.video.selector != null -> Level.PRESENT
                        draft.video.strategy in setOf("direct", "webviewSniff") -> Level.PRESENT
                        else -> Level.PARTIAL
                    },
                required = true,
                note = "strategy: ${draft.video.strategy}",
            ),
            optional(draft.latest, "Latest"),
            optional(null, "Popular"),
        )
    }

    private fun listStep(
        name: String,
        draftStep: ExtensionBuilder.StepDraft,
        required: Boolean,
        configured: (ExtensionBuilder.StepDraft) -> Boolean,
    ): StepState {
        val isConfigured = configured(draftStep)
        return StepState(
            name = name,
            level = if (isConfigured) Level.PRESENT else Level.PARTIAL,
            required = required,
            note =
                when {
                    isConfigured -> "ready to test"
                    else -> "present but needs a selector - use the visual picker"
                },
        )
    }

    private fun stepState(
        name: String,
        required: Boolean,
        present: Boolean,
        configured: Boolean,
    ): StepState =
        StepState(
            name = name,
            level =
                when {
                    !present -> Level.MISSING
                    configured -> Level.PRESENT
                    else -> Level.PARTIAL
                },
            required = required,
            note =
                when {
                    !present -> "not built yet"
                    configured -> "ready to test"
                    else -> "present but needs fields assigned in the editor"
                },
        )

    private fun optional(
        draftStep: ExtensionBuilder.StepDraft?,
        name: String,
    ): StepState =
        when (draftStep) {
            null ->
                StepState(name, Level.MISSING, required = false, note = "optional - fine to skip")
            else ->
                StepState(name, Level.PRESENT, required = false, note = "optional - included")
        }

    /** Human sentence for the soft warning; null when everything required is usable. */
    fun warning(states: List<StepState>): String? {
        val incomplete = states.filter { it.required && it.level != Level.PRESENT }
        if (incomplete.isEmpty()) return null
        return "Without fully configured " + incomplete.joinToString(" and ") { it.name.lowercase() } +
            " steps, VibeCast will hide the parts that depend on them. You can still export and fix it later."
    }
}

/**
 * Maps engine/test failures to plain sentences with a next step. The technical
 * text stays available for power users through the original message.
 */
fun friendlyStepError(
    step: String,
    technical: String?,
): String? {
    val raw = technical ?: return null
    val lower = raw.lowercase()
    return when {
        "webviewsniff" in lower || "sniffer" in lower ->
            "This site hides its video inside a web player, so the engine cannot see it from plain HTML. " +
                "Open the Page tab, tap Record while an episode page plays, then test again - " +
                "or switch the video step to the html or regex strategy if the page embeds the stream."
        "step is not defined" in lower ->
            "This extension does not define the $step step. Optional steps can stay missing; required ones " +
                "need to be built in the editor before exporting."
        "http 403" in lower ->
            "The site refused the request (403). It often helps to add a browser User-Agent or Referer " +
                "header to the extension, then test again."
        "http 404" in lower ->
            "The page was not found (404). Check the request URL template for this step."
        "http 429" in lower ->
            "The site is rate limiting the requests (429). Wait a moment and test again."
        "timeout" in lower || "timed out" in lower ->
            "The site took too long to answer. Test again, or raise timeoutMs on this step."
        "field mapping produced no value" in lower ->
            "The selector for a required field matched nothing on the page. Re-check it with the visual picker."
        "no episodes found" in lower ->
            "No episode entries matched the episodes selector on this page. Mark one with the visual picker."
        else -> raw
    }
}
