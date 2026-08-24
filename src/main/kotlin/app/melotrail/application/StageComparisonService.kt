package app.melotrail.application

import app.melotrail.arrangement.FullSongCriticReport
import app.melotrail.arrangement.ApprovedFullMelodyOccurrencePaths
import app.melotrail.arrangement.MidiMutationBudget
import app.melotrail.arrangement.MidiMutationReport
import app.melotrail.arrangement.ProjectStore
import app.melotrail.arrangement.RoleValidationReport
import app.melotrail.arrangement.WorkflowArtifactReference
import app.melotrail.arrangement.sha256
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import javax.sound.midi.MidiSystem
import javax.sound.midi.Sequence
import javax.sound.midi.ShortMessage
import kotlin.math.abs

private fun StageComparisonArtifact.toEvidence() = StageComparisonArtifactEvidence(
    stage, artifact.sha256, contextSha256, status, role, occurrenceId
)

/** Stable, code-owned identifiers; these are intentionally not desktop copy. */
@Serializable
enum class StageComparisonStage { AI_FIX, ENHANCE, COHESION, CRITIC, FULL_SONG_ENHANCE, HUMANIZATION }

/** The desktop maps these typed states to copy and colour; no copy is persisted. */
@Serializable
enum class StageEvidenceStatus { CURRENT, STALE_HISTORICAL, FAILED, BYPASSED, NO_OP, DRAFT, APPROVED }

@Serializable
enum class StageComparisonChange { ADDITION, DELETION, MODIFICATION }

@Serializable
enum class StageComparisonAnchorStatus { NOT_REPORTED, PRESERVED, VIOLATED }

@Serializable
enum class StageComparisonChordFitStatus { NOT_REPORTED, PASSED, FAILED }

@Serializable
enum class StageComparisonWarningCode { HISTORICAL_EVIDENCE, CONTEXT_HASH_CHANGED, DETAIL_ROWS_TRUNCATED }

/**
 * One hash-bound MIDI input to a comparison. Historical evidence must be
 * selected explicitly through [status]; it can never be mistaken for current
 * workflow evidence by a caller.
 */
@Serializable
data class StageComparisonArtifact(
    val stage: StageComparisonStage,
    val artifact: WorkflowArtifactReference,
    val contextSha256: String,
    val status: StageEvidenceStatus = StageEvidenceStatus.CURRENT,
    val role: String? = null,
    val occurrenceId: String? = null,
    val mutationReport: MidiMutationReport? = null,
    val roleReport: RoleValidationReport? = null,
    val criticReport: FullSongCriticReport? = null,
    val anchorsRetained: Boolean? = null,
    val chordFit: StageComparisonChordFitStatus = StageComparisonChordFitStatus.NOT_REPORTED
) {
    init {
        require(HASH.matches(contextSha256)) { "Comparison context fingerprint is invalid" }
        require(role == null || ID.matches(role)) { "Comparison role is invalid" }
        require(occurrenceId == null || ID.matches(occurrenceId)) { "Comparison occurrence is invalid" }
        require(status in COMPARABLE_STATUSES) { "Only current, draft, approved, or explicitly requested historical MIDI evidence can be compared" }
    }

    companion object {
        private val HASH = Regex("[0-9a-f]{64}")
        private val ID = Regex("[A-Za-z0-9_-]{1,80}")
        private val COMPARABLE_STATUSES = setOf(
            StageEvidenceStatus.CURRENT, StageEvidenceStatus.STALE_HISTORICAL,
            StageEvidenceStatus.DRAFT, StageEvidenceStatus.APPROVED
        )
    }
}

@Serializable
data class StageComparisonMidiStats(
    val noteCount: Int,
    val minimumPitch: Int? = null,
    val maximumPitch: Int? = null,
    val averageVelocity: Int = 0,
    val averageDurationTicks: Long = 0,
    val densityPerThousandTicks: Int = 0
)

@Serializable
data class StageComparisonTimingStats(
    val changedNotes: Int,
    val maximumStartTickDelta: Long,
    val maximumVelocityDelta: Int,
    val maximumDurationTickDelta: Long
)

@Serializable
data class StageComparisonIssueDelta(val category: String, val before: Int, val after: Int) {
    init { require(category.matches(Regex("[A-Z_]{1,64}")) && before >= 0 && after >= 0) { "Comparison issue delta is invalid" } }
}

@Serializable
data class StageComparisonMetric(val name: String, val before: Long, val after: Long) {
    init { require(name.matches(Regex("[A-Za-z][A-Za-z0-9_-]{0,63}"))) { "Comparison metric name is invalid" } }
}

@Serializable
data class StageComparisonMetrics(
    val additions: Int,
    val deletions: Int,
    val modifications: Int,
    val before: StageComparisonMidiStats,
    val after: StageComparisonMidiStats,
    val timing: StageComparisonTimingStats,
    val chordFit: StageComparisonChordFitStatus,
    val anchorPreservation: StageComparisonAnchorStatus,
    val editBudget: MidiMutationBudget? = null,
    val roleMetrics: List<StageComparisonMetric> = emptyList(),
    val occurrenceMetrics: List<StageComparisonMetric> = emptyList(),
    val criticIssueDeltas: List<StageComparisonIssueDelta> = emptyList()
) {
    init {
        require(additions >= 0 && deletions >= 0 && modifications >= 0 &&
            roleMetrics == roleMetrics.sortedBy(StageComparisonMetric::name) &&
            occurrenceMetrics == occurrenceMetrics.sortedBy(StageComparisonMetric::name) &&
            criticIssueDeltas == criticIssueDeltas.sortedBy(StageComparisonIssueDelta::category)) {
            "Comparison metrics are not canonical"
        }
    }
}

@Serializable
data class StageComparisonDetail(
    val change: StageComparisonChange,
    val role: String? = null,
    val occurrenceId: String? = null,
    val tick: Long,
    val noteId: String,
    val beforePitch: Int? = null,
    val afterPitch: Int? = null,
    val beforeVelocity: Int? = null,
    val afterVelocity: Int? = null,
    val beforeStartTick: Long? = null,
    val afterStartTick: Long? = null,
    val beforeEndTick: Long? = null,
    val afterEndTick: Long? = null
) {
    init { require(tick >= 0 && noteId.matches(Regex("n-[0-9a-f]{64}"))) { "Comparison detail is invalid" } }
}

@Serializable
data class StageComparisonArtifactEvidence(
    val stage: StageComparisonStage,
    val sha256: String,
    val contextSha256: String,
    val status: StageEvidenceStatus,
    val role: String? = null,
    val occurrenceId: String? = null
) {
    init {
        require(Regex("[0-9a-f]{64}").matches(sha256) && Regex("[0-9a-f]{64}").matches(contextSha256)) {
            "Comparison artifact evidence hashes are invalid"
        }
    }
}

@Serializable
data class StageComparisonReport(
    val version: Int = VERSION,
    val before: StageComparisonArtifactEvidence,
    val after: StageComparisonArtifactEvidence,
    val evidenceStatus: StageEvidenceStatus,
    val metrics: StageComparisonMetrics,
    val details: List<StageComparisonDetail>,
    val totalDetailRows: Int,
    val truncated: Boolean,
    val warnings: List<StageComparisonWarningCode> = emptyList(),
    val reportSha256: String
) {
    init {
        require(version == VERSION && details.size <= MAX_DETAIL_ROWS && totalDetailRows >= details.size &&
            details == details.sortedWith(DETAIL_ORDER) && warnings.size <= 16 && HASH.matches(reportSha256)) {
            "Comparison report is invalid"
        }
    }

    companion object {
        const val VERSION = 1
        const val MAX_DETAIL_ROWS = 500
        private val HASH = Regex("[0-9a-f]{64}")
        internal val DETAIL_ORDER = compareBy<StageComparisonDetail> { it.occurrenceId.orEmpty() }
            .thenBy { it.role.orEmpty() }.thenBy { it.tick }.thenBy { it.noteId }.thenBy { it.change.ordinal }
    }
}

/** Read-only comparison boundary. It never writes project.json or workflow state. */
class StageComparisonService {
    fun compare(root: Path, before: StageComparisonArtifact, after: StageComparisonArtifact): StageComparisonReport {
        val normalized = root.toAbsolutePath().normalize()
        require(Files.isDirectory(normalized)) { "Project root does not exist" }
        verifyEvidence(normalized, before)
        verifyEvidence(normalized, after)
        verifyReports(before, after)
        val beforeNotes = readMidi(normalized.resolve(before.artifact.file), before.artifact.sha256)
        val afterNotes = readMidi(normalized.resolve(after.artifact.file), after.artifact.sha256)
        val differences = differences(beforeNotes, afterNotes)
        val details = differences.map { difference -> detail(difference, before, after) }.sortedWith(StageComparisonReport.DETAIL_ORDER)
        val warnings = buildList {
            if (before.status == StageEvidenceStatus.STALE_HISTORICAL || after.status == StageEvidenceStatus.STALE_HISTORICAL) add(StageComparisonWarningCode.HISTORICAL_EVIDENCE)
            if (before.contextSha256 != after.contextSha256) add(StageComparisonWarningCode.CONTEXT_HASH_CHANGED)
            if (details.size > StageComparisonReport.MAX_DETAIL_ROWS) add(StageComparisonWarningCode.DETAIL_ROWS_TRUNCATED)
        }
        val metrics = StageComparisonMetrics(
            additions = differences.count { it.change == StageComparisonChange.ADDITION },
            deletions = differences.count { it.change == StageComparisonChange.DELETION },
            modifications = differences.count { it.change == StageComparisonChange.MODIFICATION },
            before = stats(beforeNotes), after = stats(afterNotes), timing = timing(differences),
            chordFit = chordFit(after),
            anchorPreservation = when (after.anchorsRetained) { true -> StageComparisonAnchorStatus.PRESERVED; false -> StageComparisonAnchorStatus.VIOLATED; null -> StageComparisonAnchorStatus.NOT_REPORTED },
            editBudget = after.mutationReport?.budget,
            roleMetrics = roleMetrics(before, after, beforeNotes.size, afterNotes.size),
            occurrenceMetrics = occurrenceMetrics(before, after, beforeNotes.size, afterNotes.size),
            criticIssueDeltas = issueDeltas(before.criticReport, after.criticReport)
        )
        val retained = details.take(StageComparisonReport.MAX_DETAIL_ROWS)
        val status = if (before.status == StageEvidenceStatus.STALE_HISTORICAL || after.status == StageEvidenceStatus.STALE_HISTORICAL) {
            StageEvidenceStatus.STALE_HISTORICAL
        } else after.status
        val beforeEvidence = before.toEvidence(); val afterEvidence = after.toEvidence()
        val payload = ReportHashPayload(beforeEvidence, afterEvidence, status, metrics, retained, details.size, details.size > retained.size, warnings)
        return StageComparisonReport(before = beforeEvidence, after = afterEvidence, evidenceStatus = status, metrics = metrics,
            details = retained, totalDetailRows = details.size, truncated = details.size > retained.size, warnings = warnings,
            reportSha256 = digest(json.encodeToString(payload))).also { it }
    }

    private fun verifyEvidence(root: Path, artifact: StageComparisonArtifact) {
        val path = root.resolve(artifact.artifact.file).normalize()
        require(path.startsWith(root) && Files.isRegularFile(path) && path.toRealPath().startsWith(root.toRealPath())) { "Comparison artifact is outside the project or missing" }
        require(sha256(path) == artifact.artifact.sha256) { "Comparison artifact fingerprint does not match" }
        if (artifact.status != StageEvidenceStatus.STALE_HISTORICAL) {
            val project = ProjectStore.read(root)
            require(currentReferences(root, project).any { it == artifact.artifact }) { "Comparison artifact is not current canonical project evidence" }
        }
    }

    private fun verifyReports(before: StageComparisonArtifact, after: StageComparisonArtifact) {
        after.mutationReport?.let { report ->
            report.requireValid()
            require(report.inputSha256 == before.artifact.sha256 && report.outputSha256 == after.artifact.sha256 && report.contextSha256 == after.contextSha256) {
                "Mutation report does not bind the compared artifacts"
            }
        }
        after.roleReport?.let { require(it.outputSha256 == after.artifact.sha256) { "Role report does not bind the compared output" } }
    }

    private fun currentReferences(root: Path, project: app.melotrail.arrangement.Project): Set<WorkflowArtifactReference> = buildSet {
        fun addPath(file: String?) {
            if (file == null) return
            val path = root.resolve(file).normalize()
            if (path.startsWith(root) && Files.isRegularFile(path)) add(WorkflowArtifactReference(file, sha256(path)))
        }
        project.parts.mapNotNull { it.midi }.forEach { midi ->
            addPath(midi.raw); addPath(midi.clean); addPath(midi.normalized); addPath(midi.transposed)
            midi.feel?.let { addPath(it.derived); addPath(it.report) }
            midi.technicalCorrection?.let { add(it.input); add(it.output); add(it.report) }
            midi.enhancement?.let { add(it.input); add(it.output); add(it.report); it.plan?.let(::add); it.provenance?.let(::add) }
            midi.aiFix?.let { it.draft?.let(::add); it.approved?.let(::add) }
        }
        project.workflow.cohesion?.let { cohesion ->
            add(cohesion.plan); cohesion.occurrences.forEach { add(it.result) }; cohesion.roles.forEach { add(it.result) }
        }
        project.workflow.generatedMidi?.artifacts?.forEach { add(it.artifact); add(it.validationReport) }
        project.workflow.critic?.let { add(it.report) }
        project.workflow.fullSongEnhancement?.let { run -> run.artifacts.forEach { add(it.input); add(it.output) }; run.plan?.let(::add); run.report?.let(::add) }
        project.workflow.humanization?.let { run -> run.artifacts.forEach { add(it.input); add(it.output) }; add(run.report) }
        runCatching { DefaultSourceSongCriticApplicationService().requireApprovedMelody(root) }.getOrNull()?.let { approved ->
            add(approved.sourceSongSidecar); add(approved.connectionSidecar); add(approved.connectedMidi)
            add(approved.criticReport); add(approved.approvalSidecar)
            approved.sourceSong.fullMelody.occurrences.forEach { window ->
                addPath(ApprovedFullMelodyOccurrencePaths.midi(approved.sourceSong.contextSha256, approved.connectedMidi.sha256, window.occurrenceId))
            }
        }
    }

    private fun readMidi(path: Path, expectedHash: String): List<MidiNote> {
        require(sha256(path) == expectedHash) { "Comparison artifact changed while being read" }
        val sequence = runCatching { MidiSystem.getSequence(path.toFile()) }.getOrElse { throw IllegalArgumentException("Comparison artifact is not valid MIDI", it) }
        require(sequence.divisionType == Sequence.PPQ && sequence.resolution > 0) { "Comparison MIDI must use PPQ timing" }
        val active = mutableMapOf<Triple<Int, Int, Int>, ArrayDeque<NoteStart>>()
        val ordinals = mutableMapOf<Pair<Int, Int>, Int>()
        val notes = mutableListOf<MidiNote>()
        sequence.tracks.forEachIndexed { trackIndex, track ->
            (0 until track.size()).forEach { index ->
                val event = track[index]; val message = event.message as? ShortMessage ?: return@forEach
                val key = Triple(trackIndex, message.channel, message.data1)
                when {
                    message.command == ShortMessage.NOTE_ON && message.data2 > 0 -> {
                        val ordinalKey = trackIndex to message.channel
                        val ordinal = ordinals.getOrDefault(ordinalKey, 0); ordinals[ordinalKey] = ordinal + 1
                        active.getOrPut(key) { ArrayDeque() }.addLast(NoteStart(event.tick, message.data2, ordinal))
                    }
                    message.command == ShortMessage.NOTE_OFF || (message.command == ShortMessage.NOTE_ON && message.data2 == 0) -> {
                        val start = active[key]?.removeFirstOrNull() ?: throw IllegalArgumentException("Comparison MIDI has an unmatched note-off")
                        require(event.tick > start.tick) { "Comparison MIDI has a non-positive note" }
                        notes += MidiNote(trackIndex, message.channel, start.ordinal, message.data1, start.velocity, start.tick, event.tick)
                    }
                }
            }
        }
        require(active.values.all { it.isEmpty() }) { "Comparison MIDI has unclosed notes" }
        return notes.sortedWith(NOTE_ORDER)
    }

    private fun differences(before: List<MidiNote>, after: List<MidiNote>): List<NoteDifference> {
        val unmatchedAfter = after.toMutableList()
        val paired = mutableListOf<Pair<MidiNote, MidiNote>>()
        before.forEach { source -> unmatchedAfter.indexOfFirst { it.signature == source.signature }.takeIf { it >= 0 }?.let { paired += source to unmatchedAfter.removeAt(it) } }
        val remainingBefore = before.filter { source -> paired.none { it.first == source } }.sortedWith(NOTE_ORDER)
        val differences = mutableListOf<NoteDifference>()
        remainingBefore.forEach { source ->
            val candidate = unmatchedAfter.filter { it.track == source.track && it.channel == source.channel }.minWithOrNull(
                compareBy<MidiNote> { distance(source, it) }.then(NOTE_ORDER)
            )
            if (candidate == null) differences += NoteDifference(StageComparisonChange.DELETION, source, null)
            else {
                unmatchedAfter.remove(candidate)
                differences += NoteDifference(StageComparisonChange.MODIFICATION, source, candidate)
            }
        }
        unmatchedAfter.forEach { differences += NoteDifference(StageComparisonChange.ADDITION, null, it) }
        return differences
    }

    private fun detail(difference: NoteDifference, before: StageComparisonArtifact, after: StageComparisonArtifact): StageComparisonDetail {
        val source = difference.before; val target = difference.after; val note = target ?: source!!
        return StageComparisonDetail(difference.change, after.role ?: before.role, after.occurrenceId ?: before.occurrenceId, note.start,
            note.id, source?.pitch, target?.pitch, source?.velocity, target?.velocity, source?.start, target?.start, source?.end, target?.end)
    }

    private fun stats(notes: List<MidiNote>): StageComparisonMidiStats = StageComparisonMidiStats(
        noteCount = notes.size, minimumPitch = notes.minOfOrNull(MidiNote::pitch), maximumPitch = notes.maxOfOrNull(MidiNote::pitch),
        averageVelocity = notes.map(MidiNote::velocity).average().toInt(), averageDurationTicks = notes.map { it.end - it.start }.average().toLong(),
        densityPerThousandTicks = if (notes.isEmpty()) 0 else (notes.size * 1_000L / notes.maxOf { it.end }.coerceAtLeast(1L)).toInt()
    )

    private fun timing(differences: List<NoteDifference>): StageComparisonTimingStats {
        val modified = differences.filter { it.change == StageComparisonChange.MODIFICATION }
        return StageComparisonTimingStats(modified.size,
            modified.maxOfOrNull { abs(it.after!!.start - it.before!!.start) } ?: 0,
            modified.maxOfOrNull { abs(it.after!!.velocity - it.before!!.velocity) } ?: 0,
            modified.maxOfOrNull { abs((it.after!!.end - it.after.start) - (it.before!!.end - it.before.start)) } ?: 0)
    }

    private fun chordFit(after: StageComparisonArtifact): StageComparisonChordFitStatus = when {
        after.chordFit != StageComparisonChordFitStatus.NOT_REPORTED -> after.chordFit
        after.roleReport == null -> StageComparisonChordFitStatus.NOT_REPORTED
        after.roleReport.violations.any { it.contains("chord", ignoreCase = true) || it.contains("harmony", ignoreCase = true) } -> StageComparisonChordFitStatus.FAILED
        else -> StageComparisonChordFitStatus.PASSED
    }

    private fun roleMetrics(before: StageComparisonArtifact, after: StageComparisonArtifact, beforeNotes: Int, afterNotes: Int): List<StageComparisonMetric> {
        val role = after.role ?: before.role ?: return emptyList()
        val beforeMetrics = before.roleReport?.metrics.orEmpty().associate { it.name to it.value }
        val afterMetrics = after.roleReport?.metrics.orEmpty().associate { it.name to it.value }
        return (listOf(StageComparisonMetric("role-$role-noteCount", beforeNotes.toLong(), afterNotes.toLong())) +
            (beforeMetrics.keys + afterMetrics.keys).sorted().map { name -> StageComparisonMetric("role-$role-$name", beforeMetrics[name] ?: 0, afterMetrics[name] ?: 0) })
            .distinctBy(StageComparisonMetric::name).sortedBy(StageComparisonMetric::name)
    }

    private fun occurrenceMetrics(before: StageComparisonArtifact, after: StageComparisonArtifact, beforeNotes: Int, afterNotes: Int): List<StageComparisonMetric> =
        listOfNotNull((after.occurrenceId ?: before.occurrenceId)?.let { StageComparisonMetric("occurrence-$it-noteCount", beforeNotes.toLong(), afterNotes.toLong()) }).sortedBy(StageComparisonMetric::name)

    private fun issueDeltas(before: FullSongCriticReport?, after: FullSongCriticReport?): List<StageComparisonIssueDelta> {
        val categories = (before?.issues.orEmpty().map { it.category.name } + after?.issues.orEmpty().map { it.category.name }).distinct().sorted()
        return categories.map { category -> StageComparisonIssueDelta(category, before?.issues?.count { it.category.name == category } ?: 0, after?.issues?.count { it.category.name == category } ?: 0) }
    }

    @Serializable private data class ReportHashPayload(
        val before: StageComparisonArtifactEvidence, val after: StageComparisonArtifactEvidence, val status: StageEvidenceStatus,
        val metrics: StageComparisonMetrics, val details: List<StageComparisonDetail>, val total: Int, val truncated: Boolean, val warnings: List<StageComparisonWarningCode>
    )
    private data class NoteStart(val tick: Long, val velocity: Int, val ordinal: Int)
    private data class MidiNote(val track: Int, val channel: Int, val ordinal: Int, val pitch: Int, val velocity: Int, val start: Long, val end: Long) {
        val id: String get() = "n-" + digest("stage-comparison-note-v1|$track|$channel|$ordinal|$pitch|$start|$end")
        val signature: String get() = "$track|$channel|$pitch|$velocity|$start|$end"
    }
    private data class NoteDifference(val change: StageComparisonChange, val before: MidiNote?, val after: MidiNote?)
    private companion object {
        val NOTE_ORDER = compareBy<MidiNote> { it.start }.thenBy { it.track }.thenBy { it.channel }.thenBy { it.ordinal }.thenBy { it.pitch }.thenBy { it.end }
        fun distance(left: MidiNote, right: MidiNote): Long = abs(left.start - right.start) + abs(left.end - right.end) + abs(left.pitch - right.pitch).toLong() * 480L + abs(left.velocity - right.velocity).toLong() * 8L
        fun digest(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }
        val json = Json { encodeDefaults = true; explicitNulls = false; prettyPrint = false }
    }
}

/** Persistence is deliberately separate from [StageComparisonService] so comparison never changes workflow truth. */
object StageComparisonReportStore {
    fun comparisonPath(after: StageComparisonArtifact): String {
        val path = Path.of(after.artifact.file); val parent = path.parent
        val name = path.fileName.toString().substringBeforeLast('.', path.fileName.toString())
        return (parent?.resolve("$name.comparison.json") ?: Path.of("$name.comparison.json")).toString().replace('\\', '/')
    }

    fun write(root: Path, after: StageComparisonArtifact, report: StageComparisonReport): WorkflowArtifactReference {
        val normalized = root.toAbsolutePath().normalize(); val relative = comparisonPath(after)
        val target = normalized.resolve(relative).normalize()
        require(target.startsWith(normalized)) { "Comparison report path is unsafe" }
        Files.createDirectories(requireNotNull(target.parent)); val temporary = target.resolveSibling(".${target.fileName}.tmp")
        try {
            Files.writeString(temporary, json.encodeToString(StageComparisonReport.serializer(), report), StandardCharsets.UTF_8)
            try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
            catch (_: AtomicMoveNotSupportedException) { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING) }
        } finally { Files.deleteIfExists(temporary) }
        return WorkflowArtifactReference(relative, sha256(target))
    }

    private val json = Json { encodeDefaults = true; explicitNulls = false; prettyPrint = true }
}
