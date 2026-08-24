package app.melotrail.arrangement

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import app.melotrail.preparation.SourceTimingEvidence
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import javax.sound.midi.MidiEvent
import javax.sound.midi.MidiSystem
import javax.sound.midi.Sequence
import javax.sound.midi.ShortMessage
import kotlin.math.abs

/** Deterministic severities used by the pre-arrangement source-song gate. */
@Serializable
enum class SourceSongIssueSeverity { WARNING, BLOCKING, HARD_BLOCKER }

/** Objective checks applied to the connected source melody before arrangement. */
@Serializable
enum class SourceSongIssueCategory {
    BOUNDARY_TIMING,
    UNEXPECTED_GAP,
    UNEXPECTED_OVERLAP,
    EXTREME_JUMP,
    PHRASE_LENGTH,
    TIMING_MAPPING,
    OCCURRENCE_WINDOW,
    GLOBAL_MONOPHONY,
    KEY_ELIGIBILITY,
    EXPOSED_CHORD_FIT,
    STRUCTURE_COVERAGE,
    PROTECTED_ANCHOR,
    SUSTAIN_BOUNDARY_TAIL,
    SOURCE_GROOVE,
    CANONICAL_LINEAGE
}

/** A tick- and bar-addressable source-song issue location. */
@Serializable
data class SourceSongIssueLocation(
    val boundaryId: String,
    val bar: Long,
    val startTick: Long,
    val endTick: Long
) {
    init {
        require(IDENTIFIER.matches(boundaryId) && bar >= 0 && startTick >= 0 && endTick > startTick) {
            "Source-song issue location is invalid"
        }
    }
}

/** Structured evidence for one deterministic source-song check. */
@Serializable
data class SourceSongIssue(
    val id: String,
    val category: SourceSongIssueCategory,
    val severity: SourceSongIssueSeverity,
    val location: SourceSongIssueLocation,
    val message: String,
    val observed: Double,
    val threshold: Double
) {
    init {
        require(IDENTIFIER.matches(id) && message.length in 1..180 && message.none { it.isISOControl() } &&
            observed.isFinite() && threshold.isFinite()) { "Source-song issue is invalid" }
    }
}

/** Complete severity totals remain durable even when a UI chooses to cap displayed issue details. */
@Serializable
data class SourceSongCriticCounts(
    val total: Int,
    val warnings: Int,
    val blocking: Int,
    val hardBlockers: Int
) {
    init {
        require(total >= 0 && warnings >= 0 && blocking >= 0 && hardBlockers >= 0 && total == warnings + blocking + hardBlockers) {
            "Source-song critic counts are invalid"
        }
    }
}

/** Immutable report for one assembled source-song and connected-MIDI candidate. */
@Serializable
data class SourceSongCriticReport(
    val version: Int = VERSION,
    val sourceSongContextSha256: String,
    val sourceMidiSha256: String,
    val connectedMidi: WorkflowArtifactReference,
    val issues: List<SourceSongIssue>,
    val counts: SourceSongCriticCounts
) {
    init {
        require(version == VERSION && HASH.matches(sourceSongContextSha256) && HASH.matches(sourceMidiSha256) &&
            issues.map(SourceSongIssue::id).distinct().size == issues.size && issues == issues.sortedWith(ISSUE_ORDER) &&
            counts == SourceSongCriticCounts(
                total = issues.size,
                warnings = issues.count { it.severity == SourceSongIssueSeverity.WARNING },
                blocking = issues.count { it.severity == SourceSongIssueSeverity.BLOCKING },
                hardBlockers = issues.count { it.severity == SourceSongIssueSeverity.HARD_BLOCKER }
            )) {
            "Source-song critic report is invalid"
        }
    }

    /** True when a source candidate cannot receive a quality-certified approval. */
    val hasBlockingIssues: Boolean get() = counts.blocking > 0 || counts.hardBlockers > 0

    /** True when malformed, stale, contradictory, or musically invalid canonical state cannot be overridden. */
    val hasHardBlockers: Boolean get() = counts.hardBlockers > 0

    companion object {
        const val VERSION = 2
        internal val ISSUE_ORDER = compareBy<SourceSongIssue> { it.location.startTick }
            .thenBy { it.category.ordinal }.thenBy(SourceSongIssue::id)
    }
}

/** The review path determines whether downstream consumers may call a source result quality-certified. */
@Serializable
enum class SourceSongApprovalMode { QUALITY_CERTIFIED, PRIVATE_AUDITION }

/** A persisted user decision granting the source melody access to arrangement. */
@Serializable
data class SourceSongApproval(
    val version: Int = VERSION,
    val sourceSongContextSha256: String,
    val sourceMidiSha256: String,
    val connectedMidiSha256: String,
    val criticReport: WorkflowArtifactReference,
    val mode: SourceSongApprovalMode = SourceSongApprovalMode.QUALITY_CERTIFIED,
    val overriddenBlockingIssueIds: List<String> = emptyList(),
    val overrideReason: String? = null
) {
    init {
        require(version == VERSION && HASH.matches(sourceSongContextSha256) && HASH.matches(sourceMidiSha256) &&
            HASH.matches(connectedMidiSha256) && overriddenBlockingIssueIds.distinct().size == overriddenBlockingIssueIds.size &&
            overriddenBlockingIssueIds.all(IDENTIFIER::matches) &&
            ((mode == SourceSongApprovalMode.QUALITY_CERTIFIED && overriddenBlockingIssueIds.isEmpty() && overrideReason == null) ||
                (mode == SourceSongApprovalMode.PRIVATE_AUDITION && overriddenBlockingIssueIds.isNotEmpty() && overrideReason != null && overrideReason.length in 1..180 && overrideReason.none { it.isISOControl() }))) {
            "Source-song approval is invalid"
        }
    }

    companion object { const val VERSION = 2 }
}

/** Stable source-song critic evidence locations, all rooted below the project directory. */
object SourceSongCriticArtifactPaths {
    /** Return the deterministic report path for one connected source candidate. */
    fun report(contextSha256: String, connectedMidiSha256: String): String = base(contextSha256, connectedMidiSha256) + "/report.json"

    /** Return the persisted explicit approval location for one reviewed candidate. */
    fun approval(contextSha256: String, connectedMidiSha256: String): String = base(contextSha256, connectedMidiSha256) + "/approval.json"

    /** Validate and combine the two content fingerprints that scope critic evidence. */
    private fun base(contextSha256: String, connectedMidiSha256: String): String {
        require(HASH.matches(contextSha256) && HASH.matches(connectedMidiSha256)) { "Source-song critic artifact path is invalid" }
        return "source-song/$contextSha256/critic/$connectedMidiSha256"
    }
}

/** Fully resolved input for the deterministic source-song critic. */
data class SourceSongCriticInput(
    val root: Path,
    val sourceSong: SourceSong,
    val connection: MelodyConnection,
    val projectScalePitchClasses: Set<Int>,
    /** Parts whose detected source key remains below the explicit confirmation threshold. */
    val unconfirmedSourceKeyPartIds: Set<String> = emptySet()
) {
    /** Reject non-canonical inputs before objective musical checks run. */
    fun requireValid() {
        require(projectScalePitchClasses.isNotEmpty() && projectScalePitchClasses.all { it in 0..11 }) {
            "Source-song critic requires project-scale pitch classes"
        }
        require(connection.sourceSongContextSha256 == sourceSong.contextSha256 &&
            connection.inputMidiSha256 == sourceSong.assembledMidi.sha256) { "Melody connection does not match source song" }
        require(unconfirmedSourceKeyPartIds.all(IDENTIFIER::matches)) { "Source-song critic key evidence is invalid" }
    }
}

/** Performs deterministic source-only quality checks without modifying any MIDI. */
class SourceSongCritic {
    /** Return a sorted, reproducible report for the exact connected source candidate. */
    fun criticize(input: SourceSongCriticInput): SourceSongCriticReport {
        input.requireValid()
        val root = input.root.toAbsolutePath().normalize()
        val source = verified(root, input.sourceSong.assembledMidi, "Assembled source-song MIDI")
        val connected = verified(root, input.connection.outputMidi, "Connected source-song MIDI")
        val sourceSequence = parse(source, "Assembled source-song MIDI")
        val connectedSequence = parse(connected, "Connected source-song MIDI")
        require(sourceSequence.divisionType == Sequence.PPQ && connectedSequence.divisionType == Sequence.PPQ &&
            sourceSequence.resolution == input.sourceSong.canonicalPpq && connectedSequence.resolution == input.sourceSong.canonicalPpq) {
            "Source-song critic requires matching PPQ MIDI"
        }
        val sourceNotes = notes(sourceSequence)
        val notes = notes(connectedSequence)
        val beat = canonicalBeatTicks(input.sourceSong)
        val bar = beat * meterNumerator(sourceSequence)
        val canonicalIssues = canonical(input.sourceSong, sourceSequence, connectedSequence, sourceNotes, notes, beat, input.unconfirmedSourceKeyPartIds)
        val evidence = harmonyEvidence(root, input.sourceSong, bar)
        val fullIdentity = runCatching { MelodyIdentityBuilder.build(source, beat, input.sourceSong.fullMelody.occurrences.map { window ->
            MelodyOccurrenceWindow(window.occurrenceId, window.startTick, window.endTick)
        }) }.getOrNull()
        val anchorIdentity = fullIdentity?.let { identity ->
            identity.copy(anchorIds = (identity.anchorIds + input.sourceSong.fullMelody.protectedAnchorIdentityIds(identity)).distinct().sortedBy(MelodyNoteId::value))
        }
        val issues = buildList {
            addAll(canonicalIssues)
            addAll(evidence.issues)
            addAll(sourceGrooveEvidence(root, input.sourceSong))
            addAll(boundaries(input.sourceSong, connectedSequence, notes, beat, bar))
            addAll(phrases(input.sourceSong, notes, beat, bar))
            addAll(chords(input.sourceSong, notes, input.projectScalePitchClasses, evidence.eligibilityByLineage, beat, bar))
            if (anchorIdentity == null) {
                add(issue(SourceSongIssueCategory.CANONICAL_LINEAGE, SourceSongIssueSeverity.HARD_BLOCKER, boundaryId(0), 0, 0, 1,
                    "canonical full-melody identity cannot be reconstructed from its persisted lineage", 0.0, 1.0))
            } else addAll(identity(input.sourceSong, input.connection, anchorIdentity, bar))
        }.sortedWith(SourceSongCriticReport.ISSUE_ORDER)
        return SourceSongCriticReport(
            sourceSongContextSha256 = input.sourceSong.contextSha256,
            sourceMidiSha256 = input.sourceSong.assembledMidi.sha256,
            connectedMidi = input.connection.outputMidi,
            issues = issues,
            counts = SourceSongCriticCounts(
                total = issues.size,
                warnings = issues.count { it.severity == SourceSongIssueSeverity.WARNING },
                blocking = issues.count { it.severity == SourceSongIssueSeverity.BLOCKING },
                hardBlockers = issues.count { it.severity == SourceSongIssueSeverity.HARD_BLOCKER }
            )
        )
    }

    /** Verify explicit windows, lineages, mono sounding intervals, key review, and groove coverage before subjective review. */
    private fun canonical(
        song: SourceSong,
        source: Sequence,
        connected: Sequence,
        sourceNotes: List<Note>,
        connectedNotes: List<Note>,
        beat: Long,
        unconfirmedKeys: Set<String>
    ): List<SourceSongIssue> = buildList {
        /** Verify the fixed conductor plus one controller-free full-melody MIDI representation. */
        fun canonicalTracks(sequence: Sequence, label: String) {
            val melody = sequence.tracks.filter { trackName(it) == song.fullMelody.melodyTrackName }
            val melodyTrack = melody.singleOrNull()
            if (sequence.tracks.size != 2 || melodyTrack == null || (0 until melodyTrack.size()).any { index ->
                    (melodyTrack[index].message as? ShortMessage)?.command == ShortMessage.CONTROL_CHANGE
                }) {
                add(issue(SourceSongIssueCategory.CANONICAL_LINEAGE, SourceSongIssueSeverity.HARD_BLOCKER, boundaryId(0), 0, 0, 1,
                    "$label does not satisfy the canonical conductor plus controller-free full-melody contract", sequence.tracks.size.toDouble(), 2.0))
            }
        }
        canonicalTracks(source, "assembled source-song MIDI")
        canonicalTracks(connected, "connected source-song MIDI")
        if (source.tickLength != song.sections.last().endTick || connected.tickLength != song.sections.last().endTick) {
            add(issue(SourceSongIssueCategory.TIMING_MAPPING, SourceSongIssueSeverity.HARD_BLOCKER, boundaryId(0), 0, 0, 1,
                "canonical MIDI duration does not match the declared occurrence timeline", connected.tickLength.toDouble(), song.sections.last().endTick.toDouble()))
        }
        song.sections.forEachIndexed { index, section ->
            val window = song.fullMelody.occurrences.getOrNull(index)
            if (window == null || window.occurrenceId != section.instance.instanceId || window.sourcePartId != section.sourcePartId ||
                window.startBar != section.startBar || window.endBar != section.endBar || window.startTick != section.startTick || window.endTick != section.endTick ||
                window.pickupStartTick != section.startTick ||
                window.pickupEndTick !in section.startTick..section.endTick || window.bodyStartTick != window.pickupEndTick ||
                window.bodyEndTick !in window.bodyStartTick..section.endTick || window.tailStartTick != window.bodyEndTick || window.tailEndTick != section.endTick) {
                add(issue(SourceSongIssueCategory.OCCURRENCE_WINDOW, SourceSongIssueSeverity.HARD_BLOCKER, boundaryId(index), section.startBar,
                    section.startTick, section.endTick, "occurrence pickup/body/tail windows do not match canonical timing", 0.0, 1.0))
            }
            val bar = beat * meterNumerator(source)
            if (section.startTick != section.startBar * bar || section.endTick != section.endBar * bar) add(issue(
                SourceSongIssueCategory.TIMING_MAPPING, SourceSongIssueSeverity.HARD_BLOCKER, boundaryId(index), section.startBar,
                section.startTick, section.endTick, "canonical occurrence boundaries do not align to their declared bar mapping",
                (section.endTick - section.startTick).toDouble(), ((section.endBar - section.startBar) * bar).toDouble()
            ))
            val sectionNotes = connectedNotes.filter { it.startTick in section.startTick until section.endTick }
            if (sectionNotes.isEmpty()) add(issue(SourceSongIssueCategory.STRUCTURE_COVERAGE, SourceSongIssueSeverity.HARD_BLOCKER, boundaryId(index), section.startBar,
                section.startTick, section.endTick, "canonical occurrence has no connected melody coverage", 0.0, 1.0))
            val tails = connectedNotes.filter { it.startTick < section.endTick && it.endTick > section.endTick }
            if (tails.isNotEmpty()) add(issue(SourceSongIssueCategory.SUSTAIN_BOUNDARY_TAIL, SourceSongIssueSeverity.HARD_BLOCKER, boundaryId(index), section.endBar,
                section.endTick - 1, tails.maxOf(Note::endTick), "effective sounding melody tail crosses an occurrence boundary", (tails.maxOf(Note::endTick) - section.endTick).toDouble(), 0.0))
        }
        if (connectedNotes.any { note -> song.sections.none { note.startTick >= it.startTick && note.endTick <= it.endTick } }) {
            add(issue(SourceSongIssueCategory.STRUCTURE_COVERAGE, SourceSongIssueSeverity.HARD_BLOCKER, boundaryId(0), 0, 0, 1,
                "connected melody contains notes outside explicit canonical occurrence windows", connectedNotes.size.toDouble(), song.sections.size.toDouble()))
        }
        val sourceLineage = song.fullMelody.noteLineage.map { Note(it.pitch, it.startTick, it.endTick) }
        if (sourceNotes != sourceLineage) add(issue(SourceSongIssueCategory.CANONICAL_LINEAGE, SourceSongIssueSeverity.HARD_BLOCKER, boundaryId(0), 0, 0, 1,
            "assembled MIDI notes do not match persisted full-melody lineage", sourceNotes.size.toDouble(), sourceLineage.size.toDouble()))
        if (maximumPolyphony(connectedNotes) > 1) add(issue(SourceSongIssueCategory.GLOBAL_MONOPHONY, SourceSongIssueSeverity.HARD_BLOCKER, boundaryId(0), 0, 0, 1,
            "connected canonical melody has more than one effective sounding note", maximumPolyphony(connectedNotes).toDouble(), 1.0))
        if (unconfirmedKeys.isNotEmpty()) add(issue(SourceSongIssueCategory.KEY_ELIGIBILITY, SourceSongIssueSeverity.HARD_BLOCKER, boundaryId(0), 0, 0, 1,
            "source-key confirmation is required before quality-certified source approval", unconfirmedKeys.size.toDouble(), 0.0))
        if (song.fullMelody.grooveMap.boundaries.any { it.status == FullSongGrooveBoundaryStatus.REVIEW_REQUIRED }) add(issue(
            SourceSongIssueCategory.SOURCE_GROOVE, SourceSongIssueSeverity.BLOCKING, boundaryId(0), 0, 0, maxOf(1, beat),
            "source groove contains an unreviewed occurrence-boundary discontinuity", song.fullMelody.grooveMap.boundaries.count { it.status == FullSongGrooveBoundaryStatus.REVIEW_REQUIRED }.toDouble(), 0.0
        ))
    }

    /** Re-read every QP-006 report and bind each assembled source note to its exposure-aware eligibility decision. */
    private fun harmonyEvidence(root: Path, song: SourceSong, bar: Long): HarmonyEvidence {
        val issues = mutableListOf<SourceSongIssue>()
        val eligibility = mutableMapOf<LineageKey, MelodyHarmonyEligibility>()
        song.sections.forEachIndexed { index, section ->
            val reportReference = section.sourceMidi.harmonyFitReport
            if (reportReference == null) {
                issues += issue(SourceSongIssueCategory.CANONICAL_LINEAGE, SourceSongIssueSeverity.HARD_BLOCKER, boundaryId(index), section.startBar,
                    section.startTick, section.endTick, "canonical occurrence lacks its QP-006 harmony-fit report", 0.0, 1.0)
                return@forEachIndexed
            }
            val report = runCatching {
                val path = verified(root, reportReference, "Harmony-fit report")
                json.decodeFromString(MelodyHarmonyFitReport.serializer(), Files.readString(path)).also(MelodyHarmonyFitReport::requireValid)
            }.getOrElse {
                issues += issue(SourceSongIssueCategory.CANONICAL_LINEAGE, SourceSongIssueSeverity.HARD_BLOCKER, boundaryId(index), section.startBar,
                    section.startTick, section.endTick, "canonical occurrence has missing, stale, or malformed QP-006 evidence", 0.0, 1.0)
                return@forEachIndexed
            }
            val output = report.output
            if (report.status != MelodyHarmonyFitStatus.COMPLETED || output == null || report.context.partId != section.sourcePartId ||
                report.context.occurrenceId != section.instance.instanceId || output.sha256 != section.sourceMidi.sha256 ||
                output.path != section.sourceMidi.projectRelativePath) {
                issues += issue(SourceSongIssueCategory.CANONICAL_LINEAGE, SourceSongIssueSeverity.HARD_BLOCKER, boundaryId(index), section.startBar,
                    section.startTick, section.endTick, "canonical occurrence does not match its completed QP-006 output lineage", 0.0, 1.0)
                return@forEachIndexed
            }
            val sourceNotes = song.fullMelody.noteLineage.filter { it.occurrenceId == section.instance.instanceId }
            val decisions = report.outputNotes.associateBy(HarmonyFittedMelodyNote::noteId)
            sourceNotes.forEach { note ->
                val fitted = decisions[note.sourceNoteId]
                if (fitted == null || fitted.pitch != note.pitch || fitted.startTick + section.startTick != note.startTick || fitted.endTick + section.startTick != note.endTick) {
                    issues += issue(SourceSongIssueCategory.CANONICAL_LINEAGE, SourceSongIssueSeverity.HARD_BLOCKER, boundaryId(index), note.startTick / bar,
                        note.startTick, note.endTick, "assembled note does not match its QP-006 post-fit lineage", note.pitch.toDouble(), 0.0)
                } else eligibility[LineageKey(note.pitch, note.startTick, note.endTick)] = fitted.eligibility
            }
            if (report.boundaries.none { it.tick == report.context.harmonicSpans.last().localEndTick && MelodyHarmonyBoundaryKind.OCCURRENCE_END in it.kinds }) {
                issues += issue(SourceSongIssueCategory.SUSTAIN_BOUNDARY_TAIL, SourceSongIssueSeverity.HARD_BLOCKER, boundaryId(index), section.endBar,
                    section.endTick - 1, section.endTick, "QP-006 evidence omits the sustain-aware occurrence-end decision", 0.0, 1.0)
            }
        }
        return HarmonyEvidence(issues, eligibility)
    }

    /** Require each measured source-groove reference and every projected occurrence/boundary to remain inspectable; grid fallback stays explicit. */
    private fun sourceGrooveEvidence(root: Path, song: SourceSong): List<SourceSongIssue> = buildList {
        song.fullMelody.occurrences.forEachIndexed { index, occurrence ->
            val groove = occurrence.groove
            if (groove.status == SourceSongGrooveStatus.MEASURED) {
                val reference = groove.sourceTimingReport
                val timing = runCatching {
                    require(reference != null && reference.file.startsWith("analysis/timing/")) { "Source-groove timing report is not canonical" }
                    val path = verified(root, reference, "Source-groove timing report")
                    json.decodeFromString(SourceTimingEvidence.serializer(), Files.readString(path)).also(SourceTimingEvidence::requireValid)
                }.getOrNull()
                if (timing == null || timing.partId != occurrence.sourcePartId || timing.source.sha256 != groove.template?.sourceSha256 || timing.groove != groove.template) add(issue(
                    SourceSongIssueCategory.SOURCE_GROOVE, SourceSongIssueSeverity.HARD_BLOCKER, boundaryId(index), occurrence.startBar,
                    occurrence.startTick, occurrence.endTick, "measured source groove is missing or stale timing evidence", 0.0, 1.0
                ))
            }
            if (song.fullMelody.grooveMap.points.none { it.occurrenceId == occurrence.occurrenceId }) add(issue(
                SourceSongIssueCategory.SOURCE_GROOVE, SourceSongIssueSeverity.HARD_BLOCKER, boundaryId(index), occurrence.startBar,
                occurrence.startTick, occurrence.endTick, "source groove map omits a canonical occurrence", 0.0, 1.0
            ))
        }
        val expected = song.fullMelody.occurrences.zipWithNext().mapIndexed { index, (outgoing, incoming) ->
            "groove-boundary-${index.toString().padStart(5, '0')}" to Triple(outgoing.occurrenceId, incoming.occurrenceId, incoming.startTick)
        }
        expected.forEachIndexed { index, (id, expectedBoundary) ->
            val actual = song.fullMelody.grooveMap.boundaries.singleOrNull { it.boundaryId == id }
            if (actual == null || actual.outgoingOccurrenceId != expectedBoundary.first || actual.incomingOccurrenceId != expectedBoundary.second || actual.tick != expectedBoundary.third) add(issue(
                SourceSongIssueCategory.SOURCE_GROOVE, SourceSongIssueSeverity.HARD_BLOCKER, boundaryId(index), song.fullMelody.occurrences[index + 1].startBar,
                expectedBoundary.third, expectedBoundary.third + 1, "source groove map does not bind the canonical occurrence boundary", 0.0, 1.0
            ))
        }
    }

    /** Confirm the referenced artifact is confined, present, and fingerprint-matched. */
    private fun verified(root: Path, reference: WorkflowArtifactReference, label: String): Path {
        val path = root.resolve(reference.file).normalize()
        require(path.startsWith(root) && Files.isRegularFile(path) && sourceSongCriticSha256(path) == reference.sha256) { "$label is missing or stale" }
        return path
    }

    /** Parse one standard MIDI file into a sequence or surface a bounded validation error. */
    private fun parse(path: Path, label: String): Sequence = try {
        MidiSystem.getSequence(path.toFile())
    } catch (error: Exception) {
        throw IllegalArgumentException("$label is malformed", error)
    }

    /** Check each adjacent occurrence for invalid timing, long silence, overlap, and large melodic jumps. */
    private fun boundaries(song: SourceSong, sequence: Sequence, notes: List<Note>, beat: Long, bar: Long): List<SourceSongIssue> = song.sections.zipWithNext().flatMapIndexed { index, (outgoing, incoming) ->
        val id = boundaryId(index)
        val boundary = incoming.startTick
        val outgoingNotes = notes.filter { it.startTick >= outgoing.startTick && it.startTick < outgoing.endTick }
        val incomingNotes = notes.filter { it.startTick >= incoming.startTick && it.startTick < incoming.endTick }
        buildList {
            if (outgoing.endTick != incoming.startTick || boundary <= 0 || sequence.tickLength != song.sections.last().endTick) {
                add(issue(SourceSongIssueCategory.BOUNDARY_TIMING, SourceSongIssueSeverity.BLOCKING, id, incoming.startBar, boundary, boundary + 1, "boundary timing does not match canonical source-song structure", sequence.tickLength.toDouble(), song.sections.last().endTick.toDouble()))
            }
            val crossing = outgoingNotes.filter { it.endTick > boundary }
            if (crossing.isNotEmpty()) {
                add(issue(SourceSongIssueCategory.UNEXPECTED_OVERLAP, SourceSongIssueSeverity.BLOCKING, id, incoming.startBar, boundary, crossing.maxOf(Note::endTick), "source notes overlap the next section boundary", (crossing.maxOf(Note::endTick) - boundary).toDouble(), 0.0))
            }
            val last = outgoingNotes.maxWithOrNull(compareBy<Note> { it.endTick }.thenBy(Note::pitch))
            val first = incomingNotes.minWithOrNull(compareBy<Note> { it.startTick }.thenByDescending(Note::pitch))
            if (last == null || first == null) {
                add(issue(SourceSongIssueCategory.BOUNDARY_TIMING, SourceSongIssueSeverity.BLOCKING, id, incoming.startBar, boundary, boundary + beat, "a source section has no playable melody near this boundary", 0.0, 1.0))
            } else {
                val gap = first.startTick - last.endTick
                if (gap > bar) add(issue(SourceSongIssueCategory.UNEXPECTED_GAP, SourceSongIssueSeverity.BLOCKING, id, incoming.startBar, last.endTick, first.startTick, "unexpected silence exceeds one bar at a source boundary", gap.toDouble(), bar.toDouble()))
                else if (gap > beat) add(issue(SourceSongIssueCategory.UNEXPECTED_GAP, SourceSongIssueSeverity.WARNING, id, incoming.startBar, last.endTick, first.startTick, "silence exceeds one beat at a source boundary", gap.toDouble(), beat.toDouble()))
                val leap = abs(first.pitch - last.pitch)
                if (leap > 24) add(issue(SourceSongIssueCategory.EXTREME_JUMP, SourceSongIssueSeverity.BLOCKING, id, incoming.startBar, maxOf(last.startTick, boundary - beat), minOf(first.endTick, boundary + beat), "melodic boundary jump exceeds two octaves", leap.toDouble(), 24.0))
                else if (leap > 19) add(issue(SourceSongIssueCategory.EXTREME_JUMP, SourceSongIssueSeverity.WARNING, id, incoming.startBar, maxOf(last.startTick, boundary - beat), minOf(first.endTick, boundary + beat), "melodic boundary jump exceeds a twelfth", leap.toDouble(), 19.0))
            }
        }
    }

    /** Flag empty, implausibly short, or unusually long phrase groups within each canonical occurrence. */
    private fun phrases(song: SourceSong, notes: List<Note>, beat: Long, bar: Long): List<SourceSongIssue> = song.sections.flatMapIndexed { sectionIndex, section ->
        val sectionNotes = notes.filter { it.startTick in section.startTick until section.endTick }.sortedBy(Note::startTick)
        if (sectionNotes.isEmpty()) return@flatMapIndexed listOf(issue(SourceSongIssueCategory.PHRASE_LENGTH, SourceSongIssueSeverity.BLOCKING, boundaryId(sectionIndex), section.startBar, section.startTick, section.endTick, "source section contains no playable notes", 0.0, 1.0))
        val groups = mutableListOf<MutableList<Note>>()
        sectionNotes.forEach { note ->
            val previousEnd = groups.lastOrNull()?.maxOfOrNull(Note::endTick)
            if (previousEnd == null || note.startTick - previousEnd >= beat) groups.add(mutableListOf())
            groups.last() += note
        }
        groups.mapNotNull { group ->
            val start = group.minOf(Note::startTick); val end = group.maxOf(Note::endTick); val duration = end - start
            when {
                duration < beat / 4 -> issue(SourceSongIssueCategory.PHRASE_LENGTH, SourceSongIssueSeverity.WARNING, boundaryId(sectionIndex), start / bar, start, end, "source phrase is shorter than a sixteenth-note beat fraction", duration.toDouble(), (beat / 4).toDouble())
                duration > bar * 16 -> issue(SourceSongIssueCategory.PHRASE_LENGTH, SourceSongIssueSeverity.BLOCKING, boundaryId(sectionIndex), start / bar, start, end, "source phrase exceeds sixteen bars without a phrase break", duration.toDouble(), (bar * 16).toDouble())
                else -> null
            }
        }
    }

    /** Apply QP-006 eligibility to retained source notes and a bounded weak-passing-tone rule to connection-only notes. */
    private fun chords(
        song: SourceSong,
        notes: List<Note>,
        scale: Set<Int>,
        eligibility: Map<LineageKey, MelodyHarmonyEligibility>,
        beat: Long,
        bar: Long
    ): List<SourceSongIssue> = notes.mapIndexedNotNull { noteIndex, note ->
        val sectionIndex = song.sections.indexOfLast { note.startTick >= it.startTick && note.startTick < it.endTick }
        if (sectionIndex < 0) return@mapIndexedNotNull issue(SourceSongIssueCategory.STRUCTURE_COVERAGE, SourceSongIssueSeverity.HARD_BLOCKER, boundaryId(0), 0, note.startTick, note.endTick, "source note is outside canonical section timing", note.startTick.toDouble(), 0.0)
        val section = song.sections[sectionIndex]
        val spans = section.canonicalHarmony.filter { note.startTick < it.endTick && note.endTick > it.startTick }
        if (spans.isEmpty()) return@mapIndexedNotNull issue(SourceSongIssueCategory.EXPOSED_CHORD_FIT, SourceSongIssueSeverity.HARD_BLOCKER, boundaryId(sectionIndex), note.startTick / bar, note.startTick, note.endTick, "source note has no authoritative harmony span", note.pitch.toDouble(), 0.0)
        val pitchClass = note.pitch % 12
        val retainedEligibility = eligibility[LineageKey(note.pitch, note.startTick, note.endTick)]
        if (retainedEligibility != null) return@mapIndexedNotNull null
        val chordTones = spans.all { span -> pitchClass in span.quality.intervals.map { (span.rootChromatic + it) % 12 } }
        if (chordTones) return@mapIndexedNotNull null
        val weakPassing = pitchClass in scale && note.endTick - note.startTick <= beat / 2 && note.startTick % beat != 0L &&
            notes.getOrNull(noteIndex - 1)?.let { previous -> kotlin.math.abs(previous.pitch - note.pitch) in 1..2 } == true &&
            notes.getOrNull(noteIndex + 1)?.let { next -> kotlin.math.abs(next.pitch - note.pitch) in 1..2 } == true
        if (weakPassing) return@mapIndexedNotNull null
        val category = if (pitchClass !in scale) SourceSongIssueCategory.KEY_ELIGIBILITY else SourceSongIssueCategory.EXPOSED_CHORD_FIT
        issue(category, SourceSongIssueSeverity.HARD_BLOCKER, boundaryId(sectionIndex), note.startTick / bar, note.startTick, note.endTick,
            if (category == SourceSongIssueCategory.KEY_ELIGIBILITY) "source note is outside the declared project key and active chord" else "exposed scale note is not authorized by the QP-006 harmony policy",
            pitchClass.toDouble(), spans.first().rootChromatic.toDouble())
    }

    /** Verify persisted connection evidence still preserves every protected source melody anchor. */
    private fun identity(song: SourceSong, connection: MelodyConnection, fullIdentity: MelodyIdentity, bar: Long): List<SourceSongIssue> {
        val expected = song.sections.zipWithNext().mapIndexed { index, (outgoing, incoming) -> boundaryId(index) to (outgoing to incoming) }.toMap()
        val reports = connection.boundaries.associateBy { it.decision.boundaryId }
        val issues = mutableListOf<SourceSongIssue>()
        if (reports.keys != expected.keys) {
            issues += issue(SourceSongIssueCategory.CANONICAL_LINEAGE, SourceSongIssueSeverity.HARD_BLOCKER, boundaryId(0), 0, 0, 1, "connection evidence does not cover every canonical source boundary", reports.size.toDouble(), expected.size.toDouble())
        }
        expected.forEach { (id, pair) ->
            val report = reports[id] ?: return@forEach
            val outgoing = pair.first
            val invalid = report.report.mutations.filter { mutation ->
                mutation.operation != MidiMutationOperation.ADD && (mutation.noteId !in fullIdentity.notes.map(MelodyIdentityNote::id) ||
                    (fullIdentity.isAnchor(mutation.noteId) && (mutation.operation == MidiMutationOperation.REMOVE || mutation.after?.pitch != fullIdentity.note(mutation.noteId).pitch)))
            }
            val anchors = invalid.filter { mutation -> mutation.noteId in fullIdentity.anchorIds }
            if (anchors.isNotEmpty()) {
                issues += issue(SourceSongIssueCategory.PROTECTED_ANCHOR, SourceSongIssueSeverity.HARD_BLOCKER, id, outgoing.endTick / bar, outgoing.endTick - 1, outgoing.endTick,
                    "connection evidence changes or removes a protected source-melody anchor", anchors.size.toDouble(), 0.0)
            }
            if (invalid.any { it !in anchors } || report.report.inputSha256 != song.assembledMidi.sha256 || report.report.outputSha256 != connection.outputMidi.sha256) {
                issues += issue(SourceSongIssueCategory.CANONICAL_LINEAGE, SourceSongIssueSeverity.HARD_BLOCKER, id, outgoing.endTick / bar, outgoing.endTick - 1, outgoing.endTick,
                    "connection evidence does not match the canonical source-melody lineage", invalid.size.toDouble(), 0.0)
            }
        }
        return issues
    }

    /** Pair note-on and note-off events without inferring or modifying source timing. */
    private fun notes(sequence: Sequence): List<Note> {
        val active = mutableMapOf<Triple<Int, Int, Int>, ArrayDeque<Pair<Long, Int>>>()
        val result = mutableListOf<Note>()
        sequence.tracks.forEachIndexed { trackIndex, track ->
            (0 until track.size()).forEach { eventIndex ->
                val event = track[eventIndex]; val message = event.message as? ShortMessage ?: return@forEach
                val key = Triple(trackIndex, message.channel, message.data1)
                when {
                    message.command == ShortMessage.NOTE_ON && message.data2 > 0 -> active.getOrPut(key) { ArrayDeque() }.addLast(event.tick to message.data2)
                    message.command == ShortMessage.NOTE_OFF || message.command == ShortMessage.NOTE_ON && message.data2 == 0 -> {
                        val start = active[key]?.removeFirstOrNull() ?: throw IllegalArgumentException("Connected source-song MIDI has an unmatched note-off")
                        require(event.tick > start.first) { "Connected source-song MIDI has a non-positive note" }
                        result += Note(message.data1, start.first, event.tick)
                    }
                }
            }
        }
        require(active.values.all { it.isEmpty() }) { "Connected source-song MIDI has an unclosed note" }
        return result.sortedWith(compareBy<Note> { it.startTick }.thenBy(Note::pitch).thenBy(Note::endTick))
    }

    /** Build a stable issue identifier and preserve one full tick of issue evidence. */
    private fun issue(category: SourceSongIssueCategory, severity: SourceSongIssueSeverity, boundaryId: String, bar: Long, start: Long, end: Long, message: String, observed: Double, threshold: Double): SourceSongIssue {
        val safeStart = maxOf(0, start); val safeEnd = maxOf(safeStart + 1, end)
        val id = "issue-" + digest("$category|$severity|$boundaryId|$safeStart|$safeEnd|$message").take(32)
        return SourceSongIssue(id, category, severity, SourceSongIssueLocation(boundaryId, maxOf(0, bar), safeStart, safeEnd), message, observed, threshold)
    }

    /** Convert the canonical meter denominator to the fixed PPQ beat unit. */
    private fun canonicalBeatTicks(song: SourceSong): Long = song.canonicalPpq * 4L / song.meterDenominator

    /** Read the first track-name meta event so the note-bearing full melody is unambiguous. */
    private fun trackName(track: javax.sound.midi.Track): String? = (0 until track.size()).map(track::get).firstNotNullOfOrNull { event ->
        (event.message as? javax.sound.midi.MetaMessage)?.takeIf { it.type == 0x03 }?.data?.toString(Charsets.UTF_8)
    }

    /** Read the source song's conductor meter numerator, which assembly always publishes at tick zero. */
    private fun meterNumerator(sequence: Sequence): Long = sequence.tracks.asSequence().flatMap { track -> (0 until track.size()).asSequence().map(track::get) }
        .mapNotNull { event -> (event.message as? javax.sound.midi.MetaMessage)?.takeIf { it.type == 0x58 }?.data?.firstOrNull()?.toInt()?.and(0xff)?.toLong() }
        .firstOrNull() ?: throw IllegalArgumentException("Source-song MIDI is missing its canonical meter")

    /** Count effective written sounding intervals after the canonical controller-free conversion. */
    private fun maximumPolyphony(notes: List<Note>): Int = notes.flatMap { note -> listOf(note.startTick to 1, note.endTick to -1) }
        .sortedWith(compareBy<Pair<Long, Int>> { it.first }.thenBy { it.second })
        .fold(0 to 0) { (active, maximum), (_, delta) ->
            val next = active + delta
            next to maxOf(maximum, next)
        }.second

    /** Build the canonical stable identifier for an adjacent-boundary position. */
    private fun boundaryId(index: Int): String = "boundary-${index.toString().padStart(5, '0')}"

    /** Compact parsed note representation used only by deterministic checks. */
    private data class LineageKey(val pitch: Int, val startTick: Long, val endTick: Long)
    private data class HarmonyEvidence(val issues: List<SourceSongIssue>, val eligibilityByLineage: Map<LineageKey, MelodyHarmonyEligibility>)
    private data class Note(val pitch: Int, val startTick: Long, val endTick: Long)

    private companion object { val json = Json { explicitNulls = false; ignoreUnknownKeys = false } }
}

/** Hash one local file without exposing its path in persisted evidence. */
private fun sourceSongCriticSha256(path: Path): String = Files.newInputStream(path).use { input ->
    val digest = MessageDigest.getInstance("SHA-256"); val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    generateSequence { input.read(buffer).takeIf { it > 0 } }.forEach { digest.update(buffer, 0, it) }
    digest.digest().joinToString("") { "%02x".format(it) }
}

/** Hash one deterministic issue identity input. */
private fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

private val HASH = Regex("[0-9a-f]{64}")
private val IDENTIFIER = Regex("[A-Za-z0-9_-]{1,80}")
