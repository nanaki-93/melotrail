package app.melotrail.application

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal fun evaluationScoreForm(
    frozen: MidiCoreFrozenEvaluationSet,
    frozenHash: String,
    case: MidiCoreFrozenEvaluationCase,
    result: MidiCoreEvaluationCaseResult,
): String = evaluationJson.encodeToString(buildJsonObject {
    put("schemaVersion", 1)
    put("evaluationId", frozen.evaluationId)
    put("frozenSetSha256", frozenHash)
    put("buildIdentity", frozen.buildIdentity)
    put("versionsSha256", frozen.versionsSha256)
    put("sourceSha256", case.sourceSha256)
    put("authoritySha256", case.authoritySha256)
    put("settingsSha256", case.settingsSha256)
    put("case", evaluationJson.encodeToJsonElement(MidiCoreEvaluationCase.serializer(), case.case))
    put("output", evaluationJson.encodeToJsonElement(MidiCoreEvaluationCaseResult.serializer(), result))
    put("listeningStatus", "UNSCORED")
    put("scoreTarget", "current")
    put("firstListenOrder", if (frozen.cases.indexOf(case) % 2 == 0) "current then baseline (if available)" else "baseline (if available) then current")
    put("scores", blankEvaluationScores())
    put("baselineScores", if (result.baselineAvailable) blankEvaluationScores() else JsonNull)
    listOf(
        "reviewer", "reviewedAt", "instrumentSetupConfirmed", "baselinePreferenceAndReason",
        "repairCount", "firstDraftToUseMinutes", "excludedAuthorityEntryMinutes", "excludedLogicInstrumentMinutes",
        "severeClashesTransitionsOrTimingFaults", "unresolvedStructuralBlockers", "decision",
    ).forEach { put(it, JsonNull) }
    put("instructions", JsonPrimitive("Listen with the recorded identical patches/levels at the same bars. scores rates current; baselineScores rates the supplied baseline when available. Score 1–10; explain low scores with exact bars. Measure review/repair time during real use; preparation runtime is not first-draft-to-Use time. Leave unknowns null. Tuning a final case makes it development; replenish three unseen songs before the next final set."))
})

private fun blankEvaluationScores() = JsonObject(listOf(
    "pianoMelodyFit", "bassSupport", "drumsGroove", "roleInteraction",
    "sectionDevelopmentTransitions", "repairUsefulness", "overallReadiness",
).associateWith { buildJsonObject { put("score1To10", JsonNull); put("reason", JsonNull); put("bars", JsonNull) } })

internal fun evaluationReview(
    frozen: MidiCoreFrozenEvaluationSet,
    hash: String,
    results: List<MidiCoreEvaluationCaseResult>,
): String = buildString {
    appendLine("# Musical evaluation ${frozen.evaluationId}")
    appendLine()
    appendLine("**UNSCORED. Missing final full songs: ${frozen.missingFinalSongs}/5. Missing unseen songs: ${frozen.missingUnseenSongs}/3.**")
    appendLine("Only supplied owned cases are frozen. Synthetic/development cases do not count toward these totals. Ownership, prior exposure and variety are supplied declarations requiring human review.")
    appendLine()
    appendLine("Frozen set SHA-256: `$hash`  ")
    appendLine("Build: ${frozen.buildIdentity.markdownText()}; versions SHA-256: `${frozen.versionsSha256}`. Exact compiled engine/runtime digests are in `frozen-set.json`.")
    appendLine()
    appendLine("Listen to piano + melody first, then the full arrangement at the recorded bars. Import complete-song.mid at song start in Logic; for piano + melody, mute Bass/Drums or import melody.mid and chords.mid together at the same origin. Keep identical patches, levels and bar positions between both sides. Alternate the first side using each score form; the mapping is disclosed, so this is not a blinded trial.")
    appendLine()
    appendLine("| Case | Set | Package / preparation | Piano + melody | Full arrangement | Score form |")
    appendLine("| --- | --- | --- | --- | --- | --- |")
    frozen.cases.forEach { entry ->
        val case = entry.case
        val result = results.singleOrNull { it.caseId == case.id }
        val state = result?.status ?: "FROZEN; not generated"
        val current = if (result?.status == "PREPARED") "[current](${case.directory}/current/manifest.json)" else state
        val baseline = if (case.baselineSnapshotId != null) "[baseline](${case.directory}/baseline/manifest.json)" else "baseline unavailable"
        appendLine("| ${case.id} | ${case.classification} | $current; $baseline | ${case.pianoMelodyLoop.startBar}–${case.pianoMelodyLoop.endBar} | ${case.fullArrangementLoop.startBar}–${case.fullArrangementLoop.endBar} | ${if (result != null) "[blank scores](${case.directory}/scores.json)" else "after export"} |")
        result?.problem?.let { appendLine("\n${case.id} failed: ${it.markdownText()}. The attempt is retained; it cannot count as prepared or rated.\n") }
    }
    appendLine()
    appendLine("Complete each scores.json with all seven 1–10 ratings, reasons/bars, reviewer/date, repair count and measured first-draft-to-Use minutes. Record authority-entry and Logic instrument-selection time separately. No scores or Logic playback/reopen results are inferred by this command. Its fixed export timestamp is reproducibility metadata, not a review date.")
    appendLine()
    appendLine("Targets (not achieved results): median overall and piano/melody fit ≥8, each song ≥7; no core-role/interaction/structure score <6; zero changed protected events, severe unresolved clashes/transitions/timing faults or structural blockers; median first-draft-to-Use ≤10 minutes. The same final engines must cover all five songs.")
    appendLine("Retain failed outputs. Any tuning based on a final song reclassifies it as development; freeze a new set with at least three unseen songs before evaluating changed engines. M01 comparisons remain separate development evidence. The supplied 5/10 average is historical feedback, not a new rating.")
}

private fun String.markdownText(): String = replace("|", "\\|").replace("\n", " ").replace("\r", " ").replace("<", "&lt;").replace(">", "&gt;")
