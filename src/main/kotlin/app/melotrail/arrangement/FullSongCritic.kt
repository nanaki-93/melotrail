package app.melotrail.arrangement

import app.melotrail.application.WholeSongAnalysisProjection
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import javax.sound.midi.MidiEvent
import javax.sound.midi.MidiSystem
import javax.sound.midi.ShortMessage
import kotlin.math.abs

/** Post-Cohesion, code-owned critic. This is distinct from [ArrangementCritic], which reviews plans before MIDI exists. */
@Serializable enum class FullSongIssueCategory {
    INVARIANT, HARMONIC_CLASH, VOICE_COLLISION, BASS_LEAP, DENSITY_MISMATCH,
    CONTRAST_DISCONTINUITY, TRANSITION_ABRUPTNESS, GROOVE_INCOHERENCE,
    BASS_MELODY_DEPENDENCE, MASKING, REPEATED_SECTION_STAGNATION, RECOGNIZABILITY_REGRESSION
}
@Serializable enum class FullSongIssueSeverity { BLOCKING, ACTIONABLE }
@Serializable enum class FullSongCorrectionFamily { CHORD_REVOICING, BASS_LEAP_SIMPLIFICATION, DENSITY_REDUCTION, COLLISION_REMOVAL, LOCAL_EXPRESSION_ADJUSTMENT, CHORD_CLASH_CORRECTION, TRANSITION_NOTE_ADJUSTMENT }

@Serializable data class FullSongMetric(val name: String, val value: Double) {
    init { require(NAME.matches(name) && value.isFinite()) { "Critic metric is invalid" } }
    companion object { private val NAME = Regex("[a-zA-Z][A-Za-z0-9_-]{0,63}") }
}

@Serializable data class FullSongWindow(val startTick: Long, val endTick: Long, val startBar: Long, val endBar: Long) {
    init { require(startTick >= 0 && endTick > startTick && startBar >= 0 && endBar >= startBar) { "Critic window must be a positive half-open range" } }
}

@Serializable data class FullSongIssue(
    val id: String,
    val category: FullSongIssueCategory,
    val severity: FullSongIssueSeverity,
    val targetRole: String,
    val occurrenceId: String? = null,
    val window: FullSongWindow,
    val observed: List<FullSongMetric>,
    val expected: List<FullSongMetric>,
    val reasonCode: String,
    val suggestedCorrections: List<FullSongCorrectionFamily>
) {
    init {
        require(ID.matches(id) && ROLE.matches(targetRole) && (occurrenceId == null || ID.matches(occurrenceId)) && REASON.matches(reasonCode)) { "Critic issue identity is invalid" }
        require(observed == observed.sortedBy(FullSongMetric::name) && expected == expected.sortedBy(FullSongMetric::name) &&
            observed.map(FullSongMetric::name).distinct().size == observed.size && expected.map(FullSongMetric::name).distinct().size == expected.size &&
            suggestedCorrections.distinct().size == suggestedCorrections.size) { "Critic issue evidence is not canonical" }
    }
    companion object { private val ID = Regex("[A-Za-z0-9_-]{1,80}"); private val ROLE = Regex("[a-z][a-z0-9_-]{0,63}"); private val REASON = Regex("[a-z][a-z0-9_-]{0,63}") }
}

@Serializable data class FullSongAggregateMetric(val name: String, val value: Double) {
    init { require(name.matches(Regex("[a-zA-Z][A-Za-z0-9_-]{0,63}")) && value.isFinite()) }
}

/** Optional producer-facing observations; deterministic metrics and issues remain the acceptance authority. */
@Serializable data class FullSongCriticAdvice(val modelIdentity: String, val observations: List<String>) {
    init {
        require(modelIdentity.matches(Regex("[A-Za-z0-9._:-]{1,120}")) && observations.size in 1..8 &&
            observations == observations.sorted() && observations.distinct().size == observations.size &&
            observations.all { it.matches(Regex("[A-Za-z0-9 ,.:';!?()/-]{1,240}")) }) { "Critic advice is invalid" }
    }
}

@Serializable data class FullSongCriticReport(
    val schemaVersion: Int = SCHEMA_VERSION,
    val inputSha256: String,
    val contextSha256: String,
    val processorIdentity: String = PROCESSOR_IDENTITY,
    val aggregateMetrics: List<FullSongAggregateMetric>,
    val issues: List<FullSongIssue>,
    val warnings: List<String>,
    val advice: FullSongCriticAdvice? = null,
    val reportSha256: String
) {
    init {
        require(schemaVersion == SCHEMA_VERSION && HASH.matches(inputSha256) && HASH.matches(contextSha256) && processorIdentity == PROCESSOR_IDENTITY && HASH.matches(reportSha256)) { "Critic report identity is invalid" }
        require(aggregateMetrics == aggregateMetrics.sortedBy(FullSongAggregateMetric::name) && aggregateMetrics.map(FullSongAggregateMetric::name).distinct().size == aggregateMetrics.size &&
            issues == issues.sortedWith(ISSUE_ORDER) && issues.size <= MAX_ISSUES && warnings == warnings.sorted() && warnings.size <= 1) { "Critic report is not canonical" }
        require(reportSha256 == hash(json.encodeToString(ReportHashPayload(schemaVersion, inputSha256, contextSha256, processorIdentity, aggregateMetrics, issues, warnings, advice)))) { "Critic report hash does not match its evidence" }
    }
    companion object {
        const val SCHEMA_VERSION = 1
        const val PROCESSOR_IDENTITY = "deterministic-full-song-critic-v1"
        const val MAX_ISSUES = 64
        private val HASH = Regex("[0-9a-f]{64}")
        internal val ISSUE_ORDER = compareBy<FullSongIssue> { it.severity.ordinal }.thenBy { it.window.startTick }.thenBy { it.targetRole }.thenBy { it.category.ordinal }.thenBy { it.id }
        fun create(inputHash: String, contextHash: String, metrics: List<FullSongAggregateMetric>, issues: List<FullSongIssue>, warnings: List<String>, advice: FullSongCriticAdvice? = null): FullSongCriticReport {
            val canonicalMetrics = metrics.sortedBy(FullSongAggregateMetric::name); val canonicalIssues = issues.sortedWith(ISSUE_ORDER); val canonicalWarnings = warnings.sorted()
            val hash = hash(json.encodeToString(ReportHashPayload(SCHEMA_VERSION, inputHash, contextHash, PROCESSOR_IDENTITY, canonicalMetrics, canonicalIssues, canonicalWarnings, advice)))
            return FullSongCriticReport(inputSha256 = inputHash, contextSha256 = contextHash, aggregateMetrics = canonicalMetrics, issues = canonicalIssues, warnings = canonicalWarnings, advice = advice, reportSha256 = hash)
        }
        @Serializable private data class ReportHashPayload(val schemaVersion: Int, val inputSha256: String, val contextSha256: String, val processorIdentity: String, val aggregateMetrics: List<FullSongAggregateMetric>, val issues: List<FullSongIssue>, val warnings: List<String>, val advice: FullSongCriticAdvice?)
        private val json = Json { encodeDefaults = true; explicitNulls = false }
        private fun hash(value: String) = sha256(value.toByteArray())
    }
}

/** An optional local model can summarize deterministic evidence but cannot create, suppress, or rank issues. */
fun interface FullSongCriticAdvisor { fun advise(report: FullSongCriticReport): FullSongCriticAdvice }

/** A path is deliberately in-memory only; persisted reports retain the validated relative reference and hash instead. */
data class FullSongCriticMidiArtifact(val role: String, val occurrenceId: String? = null, val path: Path, val reference: WorkflowArtifactReference, val offsetTicks: Long = 0) {
    init { require(role.matches(Regex("[a-z][a-z0-9_-]{0,63}")) && offsetTicks >= 0) }
}

data class FullSongCriticInput(
    val authority: WholeSongAnalysisProjection,
    val cohesionOccurrences: List<FullSongCriticMidiArtifact>,
    val cohesionRoles: List<FullSongCriticMidiArtifact>,
    val approvedArrangement: DetailedArrangement,
    val approvedArrangementSha256: String,
    val melodyIdentity: MelodyIdentity? = null,
    val roleReports: List<RoleValidationReport>,
    val criticPolicyVersion: Int = POLICY_VERSION,
    val inputSha256: String
) {
    init {
        require(criticPolicyVersion == POLICY_VERSION && HASH.matches(approvedArrangementSha256) && HASH.matches(inputSha256) &&
            cohesionOccurrences.all { it.occurrenceId != null } && cohesionOccurrences.mapNotNull(FullSongCriticMidiArtifact::occurrenceId).distinct().size == cohesionOccurrences.size &&
            cohesionRoles.map(FullSongCriticMidiArtifact::role).distinct().size == cohesionRoles.size) { "Full-song critic input is invalid" }
    }
    companion object { const val POLICY_VERSION = 1; private val HASH = Regex("[0-9a-f]{64}") }
}

/** Pure, offline analysis of validated Cohesion MIDI. It never writes an input artifact. */
class DeterministicFullSongCritic {
    fun criticize(input: FullSongCriticInput): FullSongCriticReport {
        val authority = input.authority
        require(authority.harmony.isNotEmpty() && authority.harmonyPpq > 0) { "Critic requires canonical harmony" }
        val beat = authority.harmonyPpq * 4L / authority.meter.denominator
        val reads = (input.cohesionOccurrences + input.cohesionRoles).map { artifact -> read(artifact, authority.harmonyPpq) }
        val notes = reads.flatMap(ReadResult::notes)
        val raw = mutableListOf<FullSongIssue>()
        raw += reads.flatMap { result -> result.violations.map { violation -> issue(FullSongIssueCategory.INVARIANT, FullSongIssueSeverity.BLOCKING, violation.role, violation.occurrenceId, violation.tick, violation.tick + 1, violation.reasonCode, emptyList(), emptyList(), emptyList(), beat * authority.meter.numerator) } }
        raw += invariantIssues(input, notes, beat)
        raw += harmonicClashes(notes, authority, beat)
        raw += collisions(notes, authority, beat)
        raw += bassLeaps(notes, authority, beat)
        raw += density(notes, input, beat)
        raw += contrast(notes, input, beat)
        raw += transitions(notes, input, beat)
        raw += groove(notes, input, beat)
        raw += bassMelodyIndependence(notes, authority, beat)
        raw += masking(notes, authority, beat)
        raw += repeatedSectionEvolution(notes, authority, beat)
        val consolidated = consolidate(raw).sortedWith(FullSongCriticReport.ISSUE_ORDER)
        val kept = consolidated.take(FullSongCriticReport.MAX_ISSUES)
        val warnings = if (consolidated.size > kept.size) listOf("issue-truncated-${consolidated.size - kept.size}") else emptyList()
        return FullSongCriticReport.create(input.inputSha256, authority.contextSha256,
            listOf(
                FullSongAggregateMetric("actionableIssueCount", kept.count { it.severity == FullSongIssueSeverity.ACTIONABLE }.toDouble()),
                FullSongAggregateMetric("blockingIssueCount", kept.count { it.severity == FullSongIssueSeverity.BLOCKING }.toDouble()),
                FullSongAggregateMetric("criticalIssueCount", kept.count(::isCritical).toDouble()),
                FullSongAggregateMetric("issueCount", kept.size.toDouble()),
                FullSongAggregateMetric("noteCount", notes.size.toDouble()),
                FullSongAggregateMetric("recognizabilityIssueCount", kept.count { it.category == FullSongIssueCategory.RECOGNIZABILITY_REGRESSION }.toDouble())
            ), kept, warnings)
    }

    private fun invariantIssues(input: FullSongCriticInput, notes: List<Note>, beat: Long): List<FullSongIssue> {
        val issues = mutableListOf<FullSongIssue>(); val barTicks = beat * input.authority.meter.numerator
        input.roleReports.filterNot(RoleValidationReport::passed).forEach { report -> issues += issue(FullSongIssueCategory.INVARIANT, FullSongIssueSeverity.BLOCKING, report.role, null, 0, beat, "role-invariant-failed", listOf(metric("violationCount", report.violations.size)), emptyList(), emptyList(), barTicks) }
        input.melodyIdentity?.let { identity ->
            input.cohesionOccurrences.firstOrNull()?.let { occurrence ->
                val offset = occurrence.offsetTicks
                val anchors = identity.anchorIds.map { identity.note(it) }
                val current = notes.filter { it.role == "piano" && it.occurrenceId == occurrence.occurrenceId }
                if (anchors.any { anchor -> current.none { it.pitch == anchor.pitch && it.start == anchor.originalStartTick + offset && it.end == anchor.originalEndTick + offset } }) {
                    issues += issue(FullSongIssueCategory.RECOGNIZABILITY_REGRESSION, FullSongIssueSeverity.BLOCKING, "piano", occurrence.occurrenceId,
                        offset, offset + beat, "melody-anchor-mismatch", listOf(metric("missingAnchorCount", anchors.count { anchor -> current.none { it.pitch == anchor.pitch && it.start == anchor.originalStartTick + offset && it.end == anchor.originalEndTick + offset } })),
                        listOf(metric("requiredAnchorCount", anchors.size)), emptyList(), barTicks)
                }
            }
        }
        notes.filter { it.end <= it.start || it.velocity !in 1..127 }.forEach { note -> issues += issue(FullSongIssueCategory.INVARIANT, FullSongIssueSeverity.BLOCKING, note.role, note.occurrenceId, note.start, maxOf(note.end, note.start + 1), "midi-invariant-failed", emptyList(), emptyList(), emptyList(), barTicks) }
        return issues
    }

    private fun harmonicClashes(notes: List<Note>, authority: WholeSongAnalysisProjection, beat: Long) = notes.filter { it.role != "drums" && it.end - it.start >= beat / 2 }.mapNotNull { note ->
        val barTicks = beat * authority.meter.numerator
        val chord = authority.harmony.singleOrNull { note.start >= it.startTick && note.start < it.endTick } ?: return@mapNotNull issue(FullSongIssueCategory.INVARIANT, FullSongIssueSeverity.BLOCKING, note.role, note.occurrenceId, note.start, note.end, "missing-harmony", emptyList(), emptyList(), emptyList(), barTicks)
        if (note.pitch % 12 in chordTones(chord) || passing(note, notes, authority, beat)) null
        else issue(FullSongIssueCategory.HARMONIC_CLASH, FullSongIssueSeverity.ACTIONABLE, note.role, note.occurrenceId, note.start, note.end, "sustained-nonchord", listOf(metric("pitchClass", (note.pitch % 12).toDouble()), metric("durationTicks", (note.end - note.start).toDouble())), listOf(metric("minimumDurationTicks", (beat / 2).toDouble())), listOf(FullSongCorrectionFamily.CHORD_CLASH_CORRECTION, FullSongCorrectionFamily.CHORD_REVOICING), barTicks)
    }

    private fun collisions(notes: List<Note>, authority: WholeSongAnalysisProjection, beat: Long): List<FullSongIssue> = notes.filter { it.role != "drums" }.flatMap { left -> notes.filter { right -> right !== left && right.role != "drums" && left.role < right.role && left.pitch == right.pitch }.mapNotNull { right ->
        val start = maxOf(left.start, right.start); val end = minOf(left.end, right.end)
        if (end - start < beat / 2) null else issue(FullSongIssueCategory.VOICE_COLLISION, FullSongIssueSeverity.ACTIONABLE, right.role, right.occurrenceId ?: left.occurrenceId, start, end, "same-pitch-overlap", listOf(metric("overlapTicks", (end - start).toDouble())), listOf(metric("minimumOverlapTicks", (beat / 2).toDouble())), listOf(FullSongCorrectionFamily.COLLISION_REMOVAL, FullSongCorrectionFamily.CHORD_REVOICING), beat * authority.meter.numerator)
    } }

    private fun bassLeaps(notes: List<Note>, authority: WholeSongAnalysisProjection, beat: Long): List<FullSongIssue> {
        val bass = notes.filter { it.role == "bass" }.sortedWith(compareBy<Note> { it.start }.thenBy { it.pitch }); return bass.zipWithNext().mapIndexedNotNull { index, (left, right) ->
            val leap = right.pitch - left.pitch; val next = bass.getOrNull(index + 2); val resolves = next != null && next.start >= right.end && abs(next.pitch - right.pitch) in 1..2 && (next.pitch - right.pitch) * leap < 0
            if (abs(leap) <= 19 || resolves) null else issue(FullSongIssueCategory.BASS_LEAP, FullSongIssueSeverity.ACTIONABLE, "bass", right.occurrenceId, left.start, right.end, "unresolved-bass-leap", listOf(metric("leapSemitones", abs(leap).toDouble())), listOf(metric("maximumSemitones", 19.0)), listOf(FullSongCorrectionFamily.BASS_LEAP_SIMPLIFICATION), beat * authority.meter.numerator)
        }
    }

    private fun density(notes: List<Note>, input: FullSongCriticInput, beat: Long): List<FullSongIssue> = input.approvedArrangement.sections.flatMap { section -> section.instruments.filter { it.mode == InstrumentMode.GENERATED }.mapNotNull { instrument ->
        val target = when (instrument) { is BassInstrumentPlan -> instrument.density; is DrumsInstrumentPlan -> instrument.density; is PadInstrumentPlan -> instrument.density; is StringsInstrumentPlan -> instrument.density; else -> null } ?: return@mapNotNull null
        val occurrence = input.authority.occurrences.singleOrNull { it.occurrenceId == section.instanceId } ?: return@mapNotNull null
        val actual = notes.count { it.role == instrument.name && it.start in occurrence.startTick until occurrence.endTick }.toDouble() / ((occurrence.endTick - occurrence.startTick).toDouble() / beat) / 16.0
        if (abs(actual - target) <= .25) null else issue(FullSongIssueCategory.DENSITY_MISMATCH, FullSongIssueSeverity.ACTIONABLE, instrument.name, occurrence.occurrenceId, occurrence.startTick, occurrence.endTick, "normalized-density-delta", listOf(metric("normalizedDensity", actual)), listOf(metric("arrangementTarget", target)), listOf(FullSongCorrectionFamily.DENSITY_REDUCTION), beat * input.authority.meter.numerator)
    } }

    private fun contrast(notes: List<Note>, input: FullSongCriticInput, beat: Long): List<FullSongIssue> = input.authority.occurrences.zipWithNext().mapNotNull { (left, right) ->
        val a = notes.count { it.start in left.startTick until left.endTick }.toDouble() / ((left.endTick - left.startTick).toDouble() / beat)
        val b = notes.count { it.start in right.startTick until right.endTick }.toDouble() / ((right.endTick - right.startTick).toDouble() / beat)
        val ratio = if (minOf(a, b) == 0.0) if (maxOf(a, b) == 0.0) 1.0 else Double.POSITIVE_INFINITY else maxOf(a, b) / minOf(a, b)
        val ea = input.approvedArrangement.sections.single { it.instanceId == left.occurrenceId }.energy; val eb = input.approvedArrangement.sections.single { it.instanceId == right.occurrenceId }.energy
        if (ratio <= 2.5 || abs(ea - eb) >= .2) null else issue(FullSongIssueCategory.CONTRAST_DISCONTINUITY, FullSongIssueSeverity.ACTIONABLE, "ensemble", right.occurrenceId, left.startTick, right.endTick, "density-ratio-with-low-energy-delta", listOf(metric("densityRatio", ratio), metric("energyDelta", abs(ea - eb))), listOf(metric("maximumDensityRatio", 2.5), metric("minimumEnergyDelta", .2)), listOf(FullSongCorrectionFamily.DENSITY_REDUCTION), beat * input.authority.meter.numerator)
    }

    private fun transitions(notes: List<Note>, input: FullSongCriticInput, beat: Long): List<FullSongIssue> = input.authority.occurrences.zipWithNext().flatMap { (left, right) ->
        val boundary = right.startTick; val active = notes.filter { it.start < boundary && it.end > boundary }; val before = notes.filter { it.end <= boundary }.maxOfOrNull { it.end } ?: left.startTick; val after = notes.filter { it.start >= boundary }.minOfOrNull { it.start } ?: right.endTick
        val silence = if (active.isNotEmpty()) 0 else after - before
        val beatStart = boundary; val onset = notes.count { it.start in beatStart until beatStart + beat }; val neighbor = notes.filter { it.start in left.startTick until right.endTick }.groupBy { it.start / beat }.values.map { it.size.toDouble() }.sorted(); val median = neighbor.getOrElse(neighbor.size / 2) { 0.0 }
        buildList { if (silence > beat) add(issue(FullSongIssueCategory.TRANSITION_ABRUPTNESS, FullSongIssueSeverity.ACTIONABLE, "ensemble", right.occurrenceId, before, after, "unplanned-boundary-silence", listOf(metric("silenceTicks", silence.toDouble())), listOf(metric("maximumSilenceTicks", beat.toDouble())), listOf(FullSongCorrectionFamily.TRANSITION_NOTE_ADJUSTMENT), beat * input.authority.meter.numerator)); if (onset > median * 2) add(issue(FullSongIssueCategory.TRANSITION_ABRUPTNESS, FullSongIssueSeverity.ACTIONABLE, "ensemble", right.occurrenceId, boundary, boundary + beat, "boundary-onset-spike", listOf(metric("onsets", onset.toDouble())), listOf(metric("maximumOnsets", median * 2)), listOf(FullSongCorrectionFamily.TRANSITION_NOTE_ADJUSTMENT, FullSongCorrectionFamily.DENSITY_REDUCTION), beat * input.authority.meter.numerator)) }
    }

    /** Flags a large rhythmic phase shift between adjacent populated occurrences. */
    private fun groove(notes: List<Note>, input: FullSongCriticInput, beat: Long): List<FullSongIssue> = input.authority.occurrences.zipWithNext().mapNotNull { (left, right) ->
        fun phase(occurrence: app.melotrail.application.MusicalOccurrence): Double? = notes.filter { it.role in setOf("drums", "bass") && it.start in occurrence.startTick until occurrence.endTick }
            .map { (it.start - occurrence.startTick).mod(beat).toDouble() }.takeIf { it.size >= 3 }?.average()
        val leftPhase = phase(left) ?: return@mapNotNull null; val rightPhase = phase(right) ?: return@mapNotNull null
        if (abs(leftPhase - rightPhase) <= beat / 4.0) null else issue(FullSongIssueCategory.GROOVE_INCOHERENCE, FullSongIssueSeverity.ACTIONABLE, "ensemble", right.occurrenceId,
            left.endTick - beat, right.startTick + beat, "rhythmic-phase-discontinuity", listOf(metric("phaseDeltaTicks", abs(leftPhase - rightPhase))),
            listOf(metric("maximumPhaseDeltaTicks", beat / 4.0)), listOf(FullSongCorrectionFamily.LOCAL_EXPRESSION_ADJUSTMENT, FullSongCorrectionFamily.TRANSITION_NOTE_ADJUSTMENT), beat * input.authority.meter.numerator)
    }

    /** Detects bass doubling melody notes instead of supplying an independent foundation. */
    private fun bassMelodyIndependence(notes: List<Note>, authority: WholeSongAnalysisProjection, beat: Long): List<FullSongIssue> = authority.occurrences.mapNotNull { occurrence ->
        val bass = notes.filter { it.role == "bass" && it.start in occurrence.startTick until occurrence.endTick }
        val melody = notes.filter { it.role == "piano" && it.occurrenceId == occurrence.occurrenceId }
        val doubled = bass.count { low -> melody.any { high -> low.start == high.start && low.pitch % 12 == high.pitch % 12 } }
        if (bass.size < 3 || doubled * 100 < bass.size * 80) null else issue(FullSongIssueCategory.BASS_MELODY_DEPENDENCE, FullSongIssueSeverity.ACTIONABLE, "bass", occurrence.occurrenceId,
            occurrence.startTick, occurrence.endTick, "bass-melody-pitch-class-doubling", listOf(metric("doubledBassNotes", doubled), metric("bassNoteCount", bass.size)),
            listOf(metric("maximumDoublingPercent", 80.0)), listOf(FullSongCorrectionFamily.BASS_LEAP_SIMPLIFICATION, FullSongCorrectionFamily.CHORD_REVOICING), beat * authority.meter.numerator)
    }

    /** Reports a sustained foreground role that obscures the melody at the same pitch class. */
    private fun masking(notes: List<Note>, authority: WholeSongAnalysisProjection, beat: Long): List<FullSongIssue> = notes.filter { it.role == "piano" && it.occurrenceId != null }.flatMap { melody ->
        notes.filter { it.role !in setOf("piano", "drums") && it.pitch % 12 == melody.pitch % 12 && it.velocity >= melody.velocity }.mapNotNull { accompaniment ->
            val start = maxOf(melody.start, accompaniment.start); val end = minOf(melody.end, accompaniment.end)
            if (end - start < beat / 2) null else issue(FullSongIssueCategory.MASKING, FullSongIssueSeverity.ACTIONABLE, accompaniment.role, melody.occurrenceId,
                start, end, "melody-pitch-class-masking", listOf(metric("overlapTicks", end - start), metric("velocityDelta", accompaniment.velocity - melody.velocity)),
                listOf(metric("maximumOverlapTicks", beat / 2.0)), listOf(FullSongCorrectionFamily.DENSITY_REDUCTION, FullSongCorrectionFamily.LOCAL_EXPRESSION_ADJUSTMENT), beat * authority.meter.numerator)
        }
    }

    /** Detects a repeated section whose ensemble onset/pitch-class signature did not evolve at all. */
    private fun repeatedSectionEvolution(notes: List<Note>, authority: WholeSongAnalysisProjection, beat: Long): List<FullSongIssue> = authority.occurrences.groupBy { it.sectionType }.values.flatMap { repeated ->
        repeated.sortedBy { it.startTick }.zipWithNext().mapNotNull { (left, right) ->
            fun signature(occurrence: app.melotrail.application.MusicalOccurrence) = notes.filter { it.start in occurrence.startTick until occurrence.endTick }
                .map { "${it.role}|${(it.start - occurrence.startTick) / beat}|${it.pitch % 12}" }.sorted()
            val leftSignature = signature(left); val rightSignature = signature(right)
            if (leftSignature.size < 4 || leftSignature != rightSignature) null else issue(FullSongIssueCategory.REPEATED_SECTION_STAGNATION, FullSongIssueSeverity.ACTIONABLE, "ensemble", right.occurrenceId,
                right.startTick, right.endTick, "unchanged-repeated-section-signature", listOf(metric("signatureEvents", rightSignature.size)),
                listOf(metric("minimumChangedEvents", 1.0)), listOf(FullSongCorrectionFamily.LOCAL_EXPRESSION_ADJUSTMENT, FullSongCorrectionFamily.DENSITY_REDUCTION), beat * authority.meter.numerator)
        }
    }

    private fun passing(note: Note, notes: List<Note>, authority: WholeSongAnalysisProjection, beat: Long): Boolean {
        if (note.pitch % 12 !in authority.projectKey.scalePitchClasses().map { it.chromatic }) return false
        val deadline = ((note.start / beat) + 1) * beat
        return notes.any { candidate -> candidate.role == note.role && candidate.start in note.end..deadline && abs(candidate.pitch - note.pitch) in 1..2 && authority.harmony.any { candidate.start in it.startTick until it.endTick && candidate.pitch % 12 in chordTones(it) } }
    }

    private fun consolidate(issues: List<FullSongIssue>): List<FullSongIssue> = issues.sortedWith(FullSongCriticReport.ISSUE_ORDER).fold(mutableListOf()) { out, issue ->
        val last = out.lastOrNull(); if (last != null && last.category == issue.category && last.targetRole == issue.targetRole && last.occurrenceId == issue.occurrenceId && issue.window.startTick <= last.window.endTick) out[out.lastIndex] = last.copy(window = last.window.copy(endTick = maxOf(last.window.endTick, issue.window.endTick), endBar = maxOf(last.window.endBar, issue.window.endBar))) else out += issue; out
    }

    private fun read(artifact: FullSongCriticMidiArtifact, ppq: Int): ReadResult {
        require(Files.isRegularFile(artifact.path) && sha256(Files.readAllBytes(artifact.path)) == artifact.reference.sha256) { "Critic MIDI '${artifact.role}' is missing or stale" }
        val sequence = runCatching { MidiSystem.getSequence(artifact.path.toFile()) }.getOrElse { return ReadResult(emptyList(), listOf(MidiViolation(artifact.role, artifact.occurrenceId, 0, "midi-unreadable"))) }
        if (sequence.resolution != ppq) return ReadResult(emptyList(), listOf(MidiViolation(artifact.role, artifact.occurrenceId, 0, "midi-ppq-mismatch")))
        val active = mutableMapOf<Pair<Int, Int>, ArrayDeque<Pair<Long, Int>>>(); val result = mutableListOf<Note>()
        val violations = mutableListOf<MidiViolation>()
        sequence.tracks.flatMapIndexed { track, events -> (0 until events.size()).map { index -> Indexed(events[index], track, index) } }.sortedWith(compareBy<Indexed> { it.event.tick }.thenBy { it.track }.thenBy { it.index }).forEach { indexed ->
            val message = indexed.event.message as? ShortMessage ?: return@forEach; val key = message.channel to message.data1
            when { message.command == ShortMessage.NOTE_ON && message.data2 > 0 -> active.getOrPut(key) { ArrayDeque() }.addLast(indexed.event.tick to message.data2)
                message.command == ShortMessage.NOTE_OFF || message.command == ShortMessage.NOTE_ON && message.data2 == 0 -> active[key]?.removeFirstOrNull()?.let { start -> result += Note(artifact.role, artifact.occurrenceId, message.data1, start.second, start.first + artifact.offsetTicks, indexed.event.tick + artifact.offsetTicks) } ?: violations.add(MidiViolation(artifact.role, artifact.occurrenceId, indexed.event.tick + artifact.offsetTicks, "midi-unmatched-note-off"))
            }
        }
        active.values.flatten().forEach { start -> violations += MidiViolation(artifact.role, artifact.occurrenceId, start.first + artifact.offsetTicks, "midi-unmatched-note-on") }
        return ReadResult(result, violations)
    }
    private fun issue(category: FullSongIssueCategory, severity: FullSongIssueSeverity, role: String, occurrence: String?, start: Long, end: Long, reason: String, observed: List<FullSongMetric>, expected: List<FullSongMetric>, corrections: List<FullSongCorrectionFamily>, barTicks: Long): FullSongIssue {
        require(barTicks > 0); val safeEnd = maxOf(start + 1, end); val window = FullSongWindow(start, safeEnd, start / barTicks, (safeEnd - 1) / barTicks + 1); val id = sha256("$category|$role|$occurrence|${window.startTick}|${window.endTick}|$reason".toByteArray()).take(32)
        return FullSongIssue(id, category, severity, role, occurrence, window, observed.sortedBy(FullSongMetric::name), expected.sortedBy(FullSongMetric::name), reason, corrections)
    }
    private fun metric(name: String, value: Number) = FullSongMetric(name, value.toDouble())
    private fun isCritical(issue: FullSongIssue) = issue.severity == FullSongIssueSeverity.BLOCKING || issue.category in setOf(
        FullSongIssueCategory.HARMONIC_CLASH, FullSongIssueCategory.VOICE_COLLISION, FullSongIssueCategory.MASKING,
        FullSongIssueCategory.RECOGNIZABILITY_REGRESSION
    )
    private fun chordTones(chord: app.melotrail.application.HarmonicTimelineEntry) = chord.chord.quality.intervals.map { (it + chord.chord.rootChromatic) % 12 }.toSet()
    private data class Note(val role: String, val occurrenceId: String?, val pitch: Int, val velocity: Int, val start: Long, val end: Long)
    private data class Indexed(val event: MidiEvent, val track: Int, val index: Int)
    private data class MidiViolation(val role: String, val occurrenceId: String?, val tick: Long, val reasonCode: String)
    private data class ReadResult(val notes: List<Note>, val violations: List<MidiViolation>)
}

private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
