package app.melotrail.arrangement

import kotlinx.serialization.Serializable
import kotlin.math.abs

/**
 * A musician-selected source phrase whose recognizability must survive later
 * MIDI stages. The choice is intentionally hash-bound to the selected source,
 * rather than inferred again from a derived MIDI file.
 */
@Serializable
data class SignatureMotif(
    val version: Int = VERSION,
    val partId: String,
    val sourceSha256: String,
    val phraseId: String,
    val sourceNoteIds: List<MelodyNoteId>,
    val confirmed: Boolean = false
) {
    init {
        require(version == VERSION && SAFE_ID.matches(partId) && HASH.matches(sourceSha256) &&
            PHRASE.matches(phraseId) && sourceNoteIds.size >= 2 &&
            sourceNoteIds == sourceNoteIds.distinct()) {
            "Signature motif is invalid"
        }
    }

    fun confirm(): SignatureMotif = copy(confirmed = true)

    companion object { const val VERSION = 1 }
}

/** Code-owned commercial-release policy. All inputs are ratios so it is portable across PPQs. */
@Serializable
data class SignatureMotifThresholds(
    val minimumIntervalContourSimilarity: Double = 0.75,
    val minimumRhythmSimilarity: Double = 0.75,
    val minimumAnchorRetention: Double = 1.0,
    val minimumMatchedNoteCoverage: Double = 0.75,
    val minimumRecognizabilityScore: Double = 0.80,
    val minimumClearOccurrences: Int = 1
) {
    init {
        listOf(minimumIntervalContourSimilarity, minimumRhythmSimilarity, minimumAnchorRetention,
            minimumMatchedNoteCoverage, minimumRecognizabilityScore).forEach { value ->
            require(value.isFinite() && value in 0.0..1.0) { "Signature motif threshold is invalid" }
        }
        require(minimumClearOccurrences in 1..64) { "Signature motif occurrence threshold is invalid" }
    }
}

@Serializable
data class SignatureMotifCandidateNote(
    val id: String,
    val pitch: Int,
    val startTick: Long,
    val endTick: Long
) {
    init { require(CANDIDATE_ID.matches(id) && pitch in 0..127 && startTick >= 0 && endTick > startTick) { "Signature motif candidate note is invalid" } }
}

@Serializable
data class SignatureMotifCandidateOccurrence(
    val occurrenceId: String,
    val offsetTicks: Long,
    val notes: List<SignatureMotifCandidateNote>
) {
    init {
        require(SAFE_ID.matches(occurrenceId) && offsetTicks >= 0 && notes.isNotEmpty() &&
            notes.map(SignatureMotifCandidateNote::id).distinct().size == notes.size) { "Signature motif occurrence is invalid" }
    }
}

@Serializable
enum class SignatureMotifLineageStatus { MATCHED, PITCH_SHIFTED, MISSING }

/** One explicit source-note to output-note lineage decision; no output note may satisfy two source notes. */
@Serializable
data class SignatureMotifNoteLineage(
    val sourceNoteId: MelodyNoteId,
    val outputNoteId: String? = null,
    val status: SignatureMotifLineageStatus
) {
    init {
        require((status == SignatureMotifLineageStatus.MISSING) == (outputNoteId == null) &&
            (outputNoteId == null || CANDIDATE_ID.matches(outputNoteId))) { "Signature motif note lineage is invalid" }
    }
}

@Serializable
data class SignatureMotifOccurrenceReport(
    val occurrenceId: String,
    val intervalContourSimilarity: Double,
    val rhythmSimilarity: Double,
    val anchorRetention: Double,
    val matchedNoteCoverage: Double,
    val recognizabilityScore: Double,
    val clear: Boolean,
    val lineage: List<SignatureMotifNoteLineage>
) {
    init {
        require(SAFE_ID.matches(occurrenceId) && listOf(intervalContourSimilarity, rhythmSimilarity, anchorRetention,
            matchedNoteCoverage, recognizabilityScore).all { it.isFinite() && it in 0.0..1.0 } &&
            lineage.isNotEmpty() && lineage.map(SignatureMotifNoteLineage::sourceNoteId).distinct().size == lineage.size) {
            "Signature motif occurrence report is invalid"
        }
    }
}

/** Persisted debug/release evidence. `passed` is the only value commercial readiness consumes. */
@Serializable
data class SignatureMotifReleaseGateResult(
    val version: Int = VERSION,
    val sourceSha256: String,
    val motifPhraseId: String,
    val thresholds: SignatureMotifThresholds,
    val occurrenceReports: List<SignatureMotifOccurrenceReport>,
    val clearOccurrenceCount: Int,
    val passed: Boolean,
    val reasons: List<String>
) {
    init {
        require(version == VERSION && HASH.matches(sourceSha256) && PHRASE.matches(motifPhraseId) &&
            occurrenceReports.isNotEmpty() && occurrenceReports.map(SignatureMotifOccurrenceReport::occurrenceId).distinct().size == occurrenceReports.size &&
            clearOccurrenceCount == occurrenceReports.count(SignatureMotifOccurrenceReport::clear) &&
            reasons == reasons.distinct().sorted() && reasons.all { it.matches(Regex("[a-z0-9-]{1,80}")) } &&
            passed == reasons.isEmpty()) { "Signature motif release gate is invalid" }
    }

    companion object { const val VERSION = 1 }
}

/**
 * Deterministic recognizability comparison. It produces lineage for every
 * selected source note before aggregating contour, rhythm, anchors and coverage.
 */
object SignatureMotifRecognizer {
    fun evaluate(
        identity: MelodyIdentity,
        motif: SignatureMotif,
        occurrences: List<SignatureMotifCandidateOccurrence>,
        thresholds: SignatureMotifThresholds = SignatureMotifThresholds()
    ): SignatureMotifReleaseGateResult {
        require(motif.confirmed) { "Signature motif must be confirmed before recognizability can be evaluated" }
        require(motif.sourceSha256 == identity.sourceSha256) { "Signature motif source MIDI is stale" }
        val phrase = requireNotNull(identity.phrases.singleOrNull { it.id == motif.phraseId }) { "Signature motif phrase is no longer available" }
        require(phrase.noteIds == motif.sourceNoteIds) { "Signature motif note selection is stale" }
        require(occurrences.isNotEmpty() && occurrences.map(SignatureMotifCandidateOccurrence::occurrenceId).distinct().size == occurrences.size) {
            "Signature motif requires at least one candidate occurrence"
        }
        val source = motif.sourceNoteIds.map(identity::note).sortedWith(NOTE_ORDER)
        val reports = occurrences.sortedBy(SignatureMotifCandidateOccurrence::occurrenceId).map { occurrence ->
            report(identity, source, occurrence, thresholds)
        }
        val reasons = buildList {
            if (reports.count(SignatureMotifOccurrenceReport::clear) < thresholds.minimumClearOccurrences) add("no-clear-surviving-occurrence")
            if (reports.none { it.anchorRetention >= thresholds.minimumAnchorRetention }) add("motif-anchor-loss")
            if (reports.none { it.matchedNoteCoverage >= thresholds.minimumMatchedNoteCoverage }) add("motif-note-coverage-low")
        }.distinct().sorted()
        return SignatureMotifReleaseGateResult(
            sourceSha256 = identity.sourceSha256,
            motifPhraseId = motif.phraseId,
            thresholds = thresholds,
            occurrenceReports = reports,
            clearOccurrenceCount = reports.count(SignatureMotifOccurrenceReport::clear),
            passed = reasons.isEmpty(),
            reasons = reasons
        )
    }

    private fun report(identity: MelodyIdentity, source: List<MelodyIdentityNote>, occurrence: SignatureMotifCandidateOccurrence, thresholds: SignatureMotifThresholds): SignatureMotifOccurrenceReport {
        val timingTolerance = (identity.canonicalBeatTicks / 8).coerceAtLeast(1)
        val available = occurrence.notes.sortedWith(compareBy<SignatureMotifCandidateNote> { it.startTick }.thenBy { it.pitch }.thenBy { it.id }).toMutableList()
        val pairs = source.map { note ->
            val expectedStart = note.originalStartTick + occurrence.offsetTicks
            val candidate = available.filter { abs(it.startTick - expectedStart) <= timingTolerance && abs(it.pitch - note.pitch) <= 2 }
                .minWithOrNull(compareBy<SignatureMotifCandidateNote> { abs(it.pitch - note.pitch) }.thenBy { abs(it.startTick - expectedStart) }.thenBy { it.id })
            candidate?.also(available::remove)?.let { note to it }
        }
        val lineage = source.zip(pairs).map { (note, pair) ->
            when {
                pair == null -> SignatureMotifNoteLineage(note.id, status = SignatureMotifLineageStatus.MISSING)
                pair.second.pitch == note.pitch -> SignatureMotifNoteLineage(note.id, pair.second.id, SignatureMotifLineageStatus.MATCHED)
                else -> SignatureMotifNoteLineage(note.id, pair.second.id, SignatureMotifLineageStatus.PITCH_SHIFTED)
            }
        }
        val matched = pairs.filterNotNull()
        val coverage = matched.size.toDouble() / source.size
        val anchors = source.filter { identity.isAnchor(it.id) }
        val anchorRetention = if (anchors.isEmpty()) 1.0 else anchors.count { anchor -> pairs[source.indexOf(anchor)]?.second?.pitch == anchor.pitch }.toDouble() / anchors.size
        val contour = contourSimilarity(source, pairs)
        val rhythm = rhythmSimilarity(source, pairs)
        val score = (contour * 0.30 + rhythm * 0.25 + anchorRetention * 0.25 + coverage * 0.20).coerceIn(0.0, 1.0)
        val clear = contour >= thresholds.minimumIntervalContourSimilarity && rhythm >= thresholds.minimumRhythmSimilarity &&
            anchorRetention >= thresholds.minimumAnchorRetention && coverage >= thresholds.minimumMatchedNoteCoverage && score >= thresholds.minimumRecognizabilityScore
        return SignatureMotifOccurrenceReport(occurrence.occurrenceId, contour, rhythm, anchorRetention, coverage, score, clear, lineage)
    }

    private fun contourSimilarity(source: List<MelodyIdentityNote>, pairs: List<Pair<MelodyIdentityNote, SignatureMotifCandidateNote>?>): Double {
        val matched = pairs.filterNotNull()
        if (matched.size < 2) return 0.0
        return matched.zipWithNext().count { (left, right) -> sign(right.second.pitch - left.second.pitch) == sign(right.first.pitch - left.first.pitch) }.toDouble() / (matched.size - 1)
    }

    private fun rhythmSimilarity(source: List<MelodyIdentityNote>, pairs: List<Pair<MelodyIdentityNote, SignatureMotifCandidateNote>?>): Double {
        val matched = pairs.filterNotNull()
        if (matched.size < 2) return 0.0
        return matched.zipWithNext().map { (left, right) ->
            similarity(right.first.originalStartTick - left.first.originalStartTick, right.second.startTick - left.second.startTick)
        }.average()
    }

    private fun similarity(left: Long, right: Long): Double = if (left <= 0 || right <= 0) 0.0 else 1.0 - abs(left - right).toDouble() / maxOf(left, right)
    private fun sign(value: Int): Int = value.compareTo(0)
    private val NOTE_ORDER = compareBy<MelodyIdentityNote> { it.originalStartTick }.thenBy { it.track }.thenBy { it.channel }.thenBy { it.noteOnOrdinal }.thenBy { it.pitch }
}

private val SAFE_ID = Regex("[A-Za-z0-9_-]{1,80}")
private val HASH = Regex("[0-9a-f]{64}")
private val PHRASE = Regex("p-[0-9]{5}")
private val CANDIDATE_ID = Regex("c-[0-9a-f]{64}")
