package app.melotrail.application

import app.melotrail.arrangement.CriticWorkflowReferences
import app.melotrail.arrangement.FullSongCriticReport
import app.melotrail.arrangement.FullSongEnhancementApplicationReport
import app.melotrail.arrangement.FullSongEnhancementArtifactPaths
import app.melotrail.arrangement.FullSongEnhancementArtifactReference
import app.melotrail.arrangement.FullSongEnhancementCandidateStatus
import app.melotrail.arrangement.FullSongEnhancementInput
import app.melotrail.arrangement.FullSongEnhancementNote
import app.melotrail.arrangement.FullSongEnhancementOperationKind
import app.melotrail.arrangement.FullSongEnhancementPlan
import app.melotrail.arrangement.FullSongEnhancementPlanParser
import app.melotrail.arrangement.FullSongEnhancementPlanner
import app.melotrail.arrangement.FullSongEnhancementReferences
import app.melotrail.arrangement.FullSongEnhancementSelection
import app.melotrail.arrangement.FullSongEnhancementTarget
import app.melotrail.arrangement.FullSongIssue
import app.melotrail.arrangement.FullSongIssueSeverity
import app.melotrail.arrangement.MelodyIdentityBuilder
import app.melotrail.arrangement.Project
import app.melotrail.arrangement.ProjectStore
import app.melotrail.arrangement.WorkflowArtifact
import app.melotrail.arrangement.WorkflowArtifactReference
import app.melotrail.arrangement.WorkflowChange
import app.melotrail.arrangement.sha256
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import javax.sound.midi.MidiEvent
import javax.sound.midi.MidiMessage
import javax.sound.midi.MidiSystem
import javax.sound.midi.Sequence
import javax.sound.midi.ShortMessage

data class FullSongEnhancementSnapshot(
    val selection: FullSongEnhancementSelection,
    val candidateAvailable: Boolean,
    val approved: Boolean,
    val actionableIssues: Int,
    val addressedIssues: Int,
    val changedNotes: Int,
    val warnings: List<String>
)

interface FullSongEnhancementApplicationService {
    fun generateCandidate(root: Path): FullSongEnhancementSnapshot
    fun load(root: Path): FullSongEnhancementSnapshot
    fun approve(root: Path): FullSongEnhancementSnapshot
    fun selectBypass(root: Path): FullSongEnhancementSnapshot
    fun resolveInputs(root: Path): List<FullSongEnhancementTarget>
}

/**
 * The model is only a planner. This boundary parses one strict JSON document,
 * validates every intent against immutable critic/authority evidence, and
 * atomically publishes an all-role candidate only after every MIDI file passes.
 */
class DefaultFullSongEnhancementApplicationService(
    private val planner: FullSongEnhancementPlanner = FullSongEnhancementPlanner {
        throw IllegalStateException("No Full-Song Enhance model is configured. Choose Bypass, or configure the approved local planner.")
    },
    private val authorityBuilder: MusicalAuthorityBuilder = MusicalAuthorityBuilder(),
    private val criticService: FullSongCriticApplicationService = DefaultFullSongCriticApplicationService(),
    private val sourceSongCritic: SourceSongCriticApplicationService = DefaultSourceSongCriticApplicationService()
) : FullSongEnhancementApplicationService {
    override fun generateCandidate(root: Path): FullSongEnhancementSnapshot = locked(root) { normalized ->
        val current = current(normalized)
        if (current.issues.isEmpty()) {
            val noOp = FullSongEnhancementReferences(current.critic.inputSha256, current.report.reportSha256, current.cohesionInputSha256)
            saveSelection(normalized, FullSongEnhancementSelection.NO_OP, noOp)
            return@locked FullSongEnhancementSnapshot(FullSongEnhancementSelection.NO_OP, false, false, 0, 0, 0, listOf("Critic found no actionable issues; Cohesion MIDI remains selected."))
        }
        val input = input(current)
        val plan = FullSongEnhancementPlanParser.parse(planner.plan(input))
        require(plan.inputSha256 == input.inputSha256 && plan.contextSha256 == input.contextSha256 &&
            plan.criticInputSha256 == input.criticInputSha256 && plan.criticReportSha256 == input.criticReportSha256) {
            "Full-song enhancement plan belongs to a different current input, context, or Critic report."
        }
        val revision = nextRevision(normalized, current.critic.inputSha256)
        val applied = apply(normalized, input, plan)
        val references = publish(normalized, current, input, plan, applied, revision)
        val project = ProjectStore.read(normalized)
        ProjectStore.write(normalized, project.copy(workflow = project.workflow.copy(
            fullSongEnhancementSelection = FullSongEnhancementSelection.UNRESOLVED,
            fullSongEnhancement = references
        ).markCurrent(WorkflowArtifact.FULL_SONG_ENHANCEMENT)))
        persistComparisons(normalized, input, references)
        snapshot(FullSongEnhancementSelection.UNRESOLVED, references,
            readReport(normalized.resolve(requireNotNull(references.report).file)), input.issues.size)
    }

    override fun load(root: Path): FullSongEnhancementSnapshot = locked(root) { normalized ->
        val project = ProjectStore.read(normalized).also { it.requireValid(normalized) }
        val current = current(normalized)
        val refs = project.workflow.fullSongEnhancement
        when (project.workflow.fullSongEnhancementSelection) {
            FullSongEnhancementSelection.UNRESOLVED -> {
                if (refs?.status !in setOf(FullSongEnhancementCandidateStatus.DRAFT, FullSongEnhancementCandidateStatus.REJECTED)) return@locked FullSongEnhancementSnapshot(FullSongEnhancementSelection.UNRESOLVED, false, false, current.issues.size, 0, 0, emptyList())
                val candidate = requireNotNull(refs)
                verifyReferences(normalized, current, candidate)
                snapshot(FullSongEnhancementSelection.UNRESOLVED, candidate, readReport(normalized.resolve(requireNotNull(candidate.report).file)), current.issues.size)
            }
            FullSongEnhancementSelection.APPROVED -> {
                val candidate = requireNotNull(refs) { "Approved Full-Song Enhance has no evidence." }; verifyReferences(normalized, current, candidate)
                snapshot(FullSongEnhancementSelection.APPROVED, candidate, readReport(normalized.resolve(requireNotNull(candidate.report).file)), current.issues.size)
            }
            FullSongEnhancementSelection.NO_OP -> {
                require(refs?.criticReportSha256 == current.report.reportSha256 && refs.criticInputSha256 == current.critic.inputSha256) { "Full-Song Enhance no-op is stale. Rerun Critic." }
                FullSongEnhancementSnapshot(FullSongEnhancementSelection.NO_OP, false, false, current.issues.size, 0, 0, listOf("No-op selected; Cohesion MIDI remains selected."))
            }
            FullSongEnhancementSelection.BYPASS -> {
                require(refs?.cohesionInputSha256 == current.cohesionInputSha256) { "Full-Song Enhance bypass is stale. Select Bypass again." }
                FullSongEnhancementSnapshot(FullSongEnhancementSelection.BYPASS, false, false, current.issues.size, 0, 0, listOf("Bypass selected; Cohesion MIDI remains selected."))
            }
        }
    }

    override fun approve(root: Path): FullSongEnhancementSnapshot = locked(root) { normalized ->
        val current = current(normalized); val project = ProjectStore.read(normalized)
        val refs = requireNotNull(project.workflow.fullSongEnhancement) { "No Full-Song Enhance candidate exists. Generate a candidate first." }
        require(project.workflow.fullSongEnhancementSelection == FullSongEnhancementSelection.UNRESOLVED && refs.status == FullSongEnhancementCandidateStatus.DRAFT) {
            "Only the current Full-Song Enhance draft can be approved."
        }
        verifyReferences(normalized, current, refs)
        val approved = refs.copy(status = FullSongEnhancementCandidateStatus.APPROVED)
        saveSelection(normalized, FullSongEnhancementSelection.APPROVED, approved)
        snapshot(FullSongEnhancementSelection.APPROVED, approved, readReport(normalized.resolve(requireNotNull(approved.report).file)), current.issues.size)
    }

    override fun selectBypass(root: Path): FullSongEnhancementSnapshot = locked(root) { normalized ->
        val current = current(normalized)
        saveSelection(normalized, FullSongEnhancementSelection.BYPASS,
            FullSongEnhancementReferences(current.critic.inputSha256, null, current.cohesionInputSha256))
        FullSongEnhancementSnapshot(FullSongEnhancementSelection.BYPASS, false, false, current.issues.size, 0, 0, listOf("Bypass selected; Cohesion MIDI remains selected."))
    }

    override fun resolveInputs(root: Path): List<FullSongEnhancementTarget> = locked(root) { current(root.toAbsolutePath().normalize()).targets }

    private fun saveSelection(root: Path, selection: FullSongEnhancementSelection, refs: FullSongEnhancementReferences) {
        val project = ProjectStore.read(root)
        ProjectStore.write(root, project.copy(workflow = project.workflow.invalidate(WorkflowChange.FULL_SONG_ENHANCEMENT_SELECTION)
            .markCurrent(WorkflowArtifact.FULL_SONG_ENHANCEMENT)
            .copy(fullSongEnhancementSelection = selection, fullSongEnhancement = refs)))
    }

    private data class Current(val project: Project, val critic: CriticWorkflowReferences, val report: FullSongCriticReport,
        val authority: WholeSongAnalysisProjection, val cohesionInputSha256: String, val targets: List<FullSongEnhancementTarget>, val issues: List<FullSongIssue>)

    private fun current(root: Path): Current {
        val project = ProjectStore.read(root).also { it.requireValid(root) }
        val cohesion = requireNotNull(project.workflow.cohesion) { "Full-Song Enhance requires approved Cohesion." }
        require(cohesion.approved && WorkflowArtifact.COHESION !in project.workflow.stale) { "Full-Song Enhance requires current approved Cohesion." }
        val approvedMelody = sourceSongCritic.requireApprovedMelody(root)
        val critic = requireNotNull(project.workflow.critic) { "Full-Song Enhance requires a current Critic report." }
        require(WorkflowArtifact.CRITIC !in project.workflow.stale) { "Critic report is stale. Rerun Critic after Cohesion." }
        val criticPath = verified(root, critic.report, "Critic report")
        val report = try { json.decodeFromString(FullSongCriticReport.serializer(), Files.readString(criticPath)) }
        catch (error: Exception) { throw IllegalArgumentException("Critic report is malformed.", error) }
        require(report.inputSha256 == critic.inputSha256) { "Critic report does not match its workflow input." }
        val authority = authorityBuilder.wholeSongAnalysis(root)
        require(report.contextSha256 == authority.contextSha256) { "Critic report is stale for the canonical musical context." }
        val targets = buildList {
            cohesion.occurrences.sortedBy { it.instanceId }.forEach { occurrence ->
                require(occurrence.approved && occurrence.cohesionInputSha256 == cohesion.inputSha256 && occurrence.sourceSha256 == approvedMelody.connectedMidi.sha256) {
                    "Cohesion occurrence '${occurrence.instanceId}' is stale for the approved connected full melody. Regenerate Cohesion."
                }
            }
            cohesion.roles.sortedBy { it.role }.forEach { role ->
                require(role.approved && role.cohesionInputSha256 == cohesion.inputSha256) { "Cohesion role '${role.role}' is stale." }
                add(target(role.role, role.role, null, 0, role.result, root))
            }
        }
        val actionable = report.issues.filter { it.severity == FullSongIssueSeverity.ACTIONABLE }.take(FullSongEnhancementInput.MAX_ACTIONABLE_ISSUES)
        return Current(project, critic, report, authority, cohesion.inputSha256, targets, actionable)
    }

    private fun input(current: Current): FullSongEnhancementInput {
        val payload = json.encodeToString(EnhancementInputHash(current.critic.inputSha256, current.report.reportSha256, current.authority.contextSha256,
            current.targets.map { TargetHash(it.id, it.input.sha256) }, current.issues.map(FullSongIssue::id)))
        return FullSongEnhancementInput(inputSha256 = digest(payload.toByteArray(StandardCharsets.UTF_8)), contextSha256 = current.authority.contextSha256,
            criticInputSha256 = current.critic.inputSha256, criticReportSha256 = current.report.reportSha256, authority = current.authority, issues = current.issues, targets = current.targets)
    }

    private data class Applied(val files: Map<String, EditableTarget>, val report: FullSongEnhancementApplicationReport)

    private fun apply(root: Path, input: FullSongEnhancementInput, plan: FullSongEnhancementPlan): Applied {
        val targets = input.targets.associateBy(FullSongEnhancementTarget::id)
        val issues = input.issues.associateBy(FullSongIssue::id)
        val notesByTarget = input.targets.associate { it.id to it.notes.associateBy(FullSongEnhancementNote::id) }
        val changed = mutableMapOf<String, MutableSet<String>>(); val deletions = mutableMapOf<String, Int>(); val additions = mutableMapOf<String, Int>()
        val editable = input.targets.associate { target -> target.id to readEditable(root, target) }.toMutableMap()
        plan.operations.sortedWith(compareBy<app.melotrail.arrangement.FullSongEnhancementOperation> { it.targetId }
            .thenBy { notesByTarget[it.targetId]?.get(it.noteId)?.startTick ?: Long.MAX_VALUE }.thenBy { it.noteId }).forEach { operation ->
            val target = requireNotNull(targets[operation.targetId]) { "Plan references unknown target '${operation.targetId}'." }
            val issue = requireNotNull(issues[operation.issueId]) { "Plan references a non-actionable or unknown Critic issue '${operation.issueId}'." }
            require(issue.targetRole == target.role || issue.targetRole == "ensemble") { "Plan target is not named by its Critic issue." }
            require(issue.occurrenceId == null || issue.occurrenceId == target.occurrenceId) { "Plan target occurrence is not named by its Critic issue." }
            val note = requireNotNull(notesByTarget.getValue(target.id)[operation.noteId]) { "Plan references unknown note '${operation.noteId}'." }
            require(note.startTick >= issue.window.startTick && note.endTick <= issue.window.endTick) { "Plan changes a note outside its Critic window." }
            val perTarget = changed.getOrPut(target.id) { linkedSetOf() }
            require(operation.noteId !in perTarget) { "Plan contains duplicate note operations." }
            val total = input.policy.totalBudget(target.notes.size); val addDelete = input.policy.additionDeletionBudget(target.notes.size)
            require(total > 0) { "Target '${target.id}' has a zero edit budget." }
            require(perTarget.size < total) { "Plan exceeds the 5% edit budget for '${target.id}'." }
            if (operation.kind in setOf(FullSongEnhancementOperationKind.REDUCE_DENSITY, FullSongEnhancementOperationKind.REMOVE_COLLISION)) {
                require(addDelete > 0 && (deletions[target.id] ?: 0) < addDelete) { "Plan exceeds the 2% deletion budget for '${target.id}'." }
                require(!isMelodyAnchor(root, target, note, input.authority.harmonyPpq, input.authority.meter.denominator)) { "Melody anchors cannot be deleted." }
                editable.getValue(target.id).notes.remove(operation.noteId); deletions[target.id] = (deletions[target.id] ?: 0) + 1
            } else {
                val edited = requireNotNull(editable.getValue(target.id).notes[operation.noteId]) { "Plan changes a deleted note." }
                if (operation.pitch != null) {
                    if (target.role == "piano") {
                        require(!isMelodyAnchor(root, target, note, input.authority.harmonyPpq, input.authority.meter.denominator)) { "Melody anchors cannot be pitch-shifted." }
                        require(kotlin.math.abs(operation.pitch - note.pitch) <= 2) { "Melody pitch changes are limited to two semitones." }
                    }
                    require(chordAllows(input.authority, note.startTick, operation.pitch)) { "Pitch change is not valid for the active canonical chord." }
                    edited.pitch = operation.pitch
                }
                operation.tickDelta?.let { delta ->
                    require(note.startTick + delta >= target.offsetTicks && note.startTick + delta >= issue.window.startTick && note.endTick + delta <= issue.window.endTick) { "Timing change leaves its Critic window." }
                    edited.start += delta; edited.end += delta
                }
                operation.durationDelta?.let { delta -> require(edited.end + delta > edited.start) { "Duration change creates an invalid MIDI note." }; edited.end += delta }
                operation.velocityDelta?.let { delta -> require(edited.velocity + delta in 1..127) { "Velocity change is outside MIDI range." }; edited.velocity += delta }
                require(operation.kind !in setOf(FullSongEnhancementOperationKind.REVOICE_CHORD, FullSongEnhancementOperationKind.SIMPLIFY_BASS_LEAP, FullSongEnhancementOperationKind.CORRECT_CHORD_CLASH) || operation.pitch != null) { "Pitch operation has no pitch." }
            }
            perTarget += operation.noteId
        }
        val files = editable
        val addressed = plan.operations.map { it.issueId }.distinct().sorted()
        val report = FullSongEnhancementApplicationReport(input.inputSha256, input.contextSha256, input.criticReportSha256, digest(json.encodeToString(plan).toByteArray()), addressed,
            input.issues.map(FullSongIssue::id).filterNot { it in addressed }, changed.values.sumOf { it.size }, additions.values.sum(), deletions.values.sum())
        return Applied(files, report)
    }

    private fun publish(root: Path, current: Current, input: FullSongEnhancementInput, plan: FullSongEnhancementPlan, applied: Applied, revision: String): FullSongEnhancementReferences {
        val refs = current.targets.sortedBy { it.id }.map { target ->
            val relative = FullSongEnhancementArtifactPaths.output(current.critic.inputSha256, revision, target.id)
            val path = root.resolve(relative); writeMidi(path, writeSequence(applied.files.getValue(target.id))); FullSongEnhancementArtifactReference(target.id, target.input, WorkflowArtifactReference(relative, sha256(path)))
        }
        val planRelative = FullSongEnhancementArtifactPaths.plan(current.critic.inputSha256, revision)
        val reportRelative = FullSongEnhancementArtifactPaths.report(current.critic.inputSha256, revision)
        atomicWrite(root.resolve(planRelative), json.encodeToString(plan))
        val after = criticService.analyzeCandidate(root, refs.associate { it.id to it.output })
        val afterRelative = FullSongEnhancementArtifactPaths.afterCriticReport(current.critic.inputSha256, revision)
        atomicWrite(root.resolve(afterRelative), json.encodeToString(FullSongCriticReport.serializer(), after))
        val acceptance = candidateAcceptance(current.report, after)
        val report = applied.report.copy(
            beforeCriticalIssueCount = acceptance.beforeCriticalIssueCount,
            afterCriticalIssueCount = acceptance.afterCriticalIssueCount,
            recognizabilityPreserved = acceptance.recognizabilityPreserved,
            automaticallyAccepted = acceptance.accepted,
            warnings = acceptance.reasons
        )
        atomicWrite(root.resolve(reportRelative), json.encodeToString(report))
        return FullSongEnhancementReferences(current.critic.inputSha256, current.report.reportSha256, current.cohesionInputSha256,
            if (acceptance.accepted) FullSongEnhancementCandidateStatus.DRAFT else FullSongEnhancementCandidateStatus.REJECTED,
            refs, WorkflowArtifactReference(planRelative, sha256(root.resolve(planRelative))), WorkflowArtifactReference(reportRelative, sha256(root.resolve(reportRelative))),
            WorkflowArtifactReference(afterRelative, sha256(root.resolve(afterRelative))))
    }

    private fun verifyReferences(root: Path, current: Current, refs: FullSongEnhancementReferences) {
        require(refs.criticInputSha256 == current.critic.inputSha256 && refs.criticReportSha256 == current.report.reportSha256 && refs.cohesionInputSha256 == current.cohesionInputSha256 && refs.status != null) { "Full-Song Enhance evidence is stale." }
        require(refs.artifacts.map { it.id }.toSet() == current.targets.map { it.id }.toSet()) { "Full-Song Enhance candidate does not cover the complete ensemble." }
        current.targets.forEach { target ->
            val artifact = refs.artifacts.single { it.id == target.id }; require(artifact.input == target.input); verified(root, artifact.output, "Full-Song Enhance MIDI '${target.id}'")
        }
        val planPath = verified(root, requireNotNull(refs.plan), "Full-Song Enhance plan")
        val reportPath = verified(root, requireNotNull(refs.report), "Full-Song Enhance report")
        val afterPath = verified(root, requireNotNull(refs.afterCriticReport), "post-polish Critic report")
        val input = input(current)
        val plan = FullSongEnhancementPlanParser.parse(Files.readString(planPath))
        val report = readReport(reportPath)
        val after = try { json.decodeFromString(FullSongCriticReport.serializer(), Files.readString(afterPath)) }
        catch (error: Exception) { throw IllegalArgumentException("Post-polish Critic report is malformed.", error) }
        require(plan.inputSha256 == input.inputSha256 && plan.contextSha256 == input.contextSha256 && plan.criticInputSha256 == current.critic.inputSha256 && plan.criticReportSha256 == current.report.reportSha256 &&
            report.inputSha256 == input.inputSha256 && report.contextSha256 == input.contextSha256 && report.criticReportSha256 == current.report.reportSha256 && report.planSha256 == sha256(planPath)) {
            "Full-Song Enhance candidate evidence is not hash-bound to current inputs."
        }
        val acceptance = candidateAcceptance(current.report, after)
        require(report.beforeCriticalIssueCount == acceptance.beforeCriticalIssueCount && report.afterCriticalIssueCount == acceptance.afterCriticalIssueCount &&
            report.recognizabilityPreserved == acceptance.recognizabilityPreserved && report.automaticallyAccepted == acceptance.accepted &&
            (refs.status == FullSongEnhancementCandidateStatus.DRAFT) == acceptance.accepted) {
            "Full-Song Enhance candidate acceptance evidence is invalid."
        }
    }

    private fun snapshot(selection: FullSongEnhancementSelection, refs: FullSongEnhancementReferences, report: FullSongEnhancementApplicationReport, issues: Int) =
        FullSongEnhancementSnapshot(selection, refs.status == FullSongEnhancementCandidateStatus.DRAFT, refs.status == FullSongEnhancementCandidateStatus.APPROVED,
            issues, report.addressedIssueIds.size, report.changedNotes, report.warnings)

    private data class CandidateAcceptance(val accepted: Boolean, val beforeCriticalIssueCount: Int, val afterCriticalIssueCount: Int, val recognizabilityPreserved: Boolean, val reasons: List<String>)

    /** A candidate must improve the code-owned critical metric and keep all protected melody anchors intact. */
    private fun candidateAcceptance(before: FullSongCriticReport, after: FullSongCriticReport): CandidateAcceptance {
        fun metric(report: FullSongCriticReport, name: String) = report.aggregateMetrics.singleOrNull { it.name == name }?.value?.toInt()
            ?: error("Critic report does not contain '$name'.")
        val beforeCritical = metric(before, "criticalIssueCount")
        val afterCritical = metric(after, "criticalIssueCount")
        val recognizable = metric(after, "recognizabilityIssueCount") <= metric(before, "recognizabilityIssueCount")
        val reasons = buildList {
            if (afterCritical >= beforeCritical) add("Candidate rejected: critical Critic metrics did not improve.")
            if (!recognizable) add("Candidate rejected: melody recognizability regressed.")
        }
        return CandidateAcceptance(reasons.isEmpty(), beforeCritical, afterCritical, recognizable, reasons)
    }

    private fun persistComparisons(root: Path, input: FullSongEnhancementInput, refs: FullSongEnhancementReferences) {
        refs.artifacts.sortedBy { it.id }.forEach { artifact ->
            val target = input.targets.single { it.id == artifact.id }
            val before = StageComparisonArtifact(StageComparisonStage.FULL_SONG_ENHANCE, artifact.input, input.contextSha256,
                role = target.role, occurrenceId = target.occurrenceId)
            val after = StageComparisonArtifact(StageComparisonStage.FULL_SONG_ENHANCE, artifact.output, input.contextSha256,
                StageEvidenceStatus.DRAFT, target.role, target.occurrenceId)
            StageComparisonReportStore.write(root, after, StageComparisonService().compare(root, before, after))
        }
    }

    private fun target(id: String, role: String, occurrence: String?, offset: Long, ref: WorkflowArtifactReference, root: Path): FullSongEnhancementTarget {
        val path = verified(root, ref, "Cohesion MIDI '$id'"); return FullSongEnhancementTarget(id, role, occurrence, offset, ref, readNotes(path, ref.sha256, offset).map { it.note })
    }

    private data class Editable(val targetId: String, val note: FullSongEnhancementNote, var pitch: Int = note.pitch, var velocity: Int = note.velocity, var start: Long = note.startTick, var end: Long = note.endTick)
    private data class ReadNote(val note: FullSongEnhancementNote)
    private fun readNotes(path: Path, sourceHash: String, offset: Long): List<ReadNote> {
        val sequence = runCatching { MidiSystem.getSequence(path.toFile()) }.getOrElse { throw IllegalArgumentException("MIDI is malformed: $path", it) }
        require(sequence.divisionType == Sequence.PPQ)
        val active = mutableMapOf<Triple<Int, Int, Int>, ArrayDeque<Pair<Long, Int>>>(); val ordinal = mutableMapOf<Pair<Int, Int>, Int>(); val result = mutableListOf<ReadNote>()
        sequence.tracks.forEachIndexed { track, events -> (0 until events.size()).forEach { index ->
            val event = events[index]; val message = event.message as? ShortMessage ?: return@forEach; val key = Triple(track, message.channel, message.data1)
            when {
                message.command == ShortMessage.NOTE_ON && message.data2 > 0 -> active.getOrPut(key) { ArrayDeque() }.addLast(event.tick to message.data2)
                message.command == ShortMessage.NOTE_OFF || message.command == ShortMessage.NOTE_ON && message.data2 == 0 -> active[key]?.removeFirstOrNull()?.let { start ->
                    val no = ordinal.merge(track to message.channel, 1, Int::plus)!! - 1
                    val id = "n-" + digest("full-song-note-v1|$sourceHash|$track|${message.channel}|$no|${message.data1}|${start.first}|${event.tick}".toByteArray())
                    result += ReadNote(FullSongEnhancementNote(id, track, message.channel, message.data1, start.second, start.first + offset, event.tick + offset))
                } ?: error("MIDI has an unmatched note-off")
            }
        } }
        require(active.values.all { it.isEmpty() }) { "MIDI has an unmatched note-on" }; return result
    }
    private data class EditableTarget(val target: FullSongEnhancementTarget, val source: Sequence, val notes: MutableMap<String, Editable>)
    private fun readEditable(root: Path, target: FullSongEnhancementTarget): EditableTarget {
        val source = MidiSystem.getSequence(verified(root, target.input, "Cohesion MIDI '${target.id}'").toFile())
        val notes = target.notes.associate { note -> note.id to Editable(target.id, note.copy(startTick = note.startTick - target.offsetTicks, endTick = note.endTick - target.offsetTicks)) }.toMutableMap()
        return EditableTarget(target, source, notes)
    }
    private fun writeSequence(editable: EditableTarget): Sequence {
        val source = editable.source; val target = editable.target
        val output = Sequence(source.divisionType, source.resolution); val ordinal = mutableMapOf<Pair<Int, Int>, Int>()
        source.tracks.forEachIndexed { trackIndex, track ->
            val destination = output.createTrack(); val active = mutableMapOf<Triple<Int, Int, Int>, ArrayDeque<Pair<MidiEvent, Pair<Long, Int>>>>()
            (0 until track.size()).forEach { index ->
                val event = track[index]; val original = event.message; val copied = MidiEvent(original.clone() as MidiMessage, event.tick)
                val message = original as? ShortMessage
                if (message == null) { destination.add(copied); return@forEach }
                val key = Triple(trackIndex, message.channel, message.data1)
                when {
                    message.command == ShortMessage.NOTE_ON && message.data2 > 0 -> {
                        destination.add(copied); active.getOrPut(key) { ArrayDeque() }.addLast(copied to (event.tick to message.data2))
                    }
                    message.command == ShortMessage.NOTE_OFF || message.command == ShortMessage.NOTE_ON && message.data2 == 0 -> {
                        val start = active[key]?.removeFirstOrNull() ?: throw IllegalArgumentException("MIDI has an unmatched note-off")
                        val no = ordinal.merge(trackIndex to message.channel, 1, Int::plus)!! - 1
                        val id = "n-" + digest("full-song-note-v1|${target.input.sha256}|$trackIndex|${message.channel}|$no|${message.data1}|${start.second.first}|${event.tick}".toByteArray())
                        val edit = editable.notes[id]
                        if (edit == null) destination.remove(start.first) else {
                            val on = start.first.message as ShortMessage; on.setMessage(ShortMessage.NOTE_ON, message.channel, edit.pitch, edit.velocity); start.first.tick = edit.start
                            val off = copied.message as ShortMessage; off.setMessage(ShortMessage.NOTE_OFF, message.channel, edit.pitch, 0); copied.tick = edit.end; destination.add(copied)
                        }
                    }
                    else -> destination.add(copied)
                }
            }
            require(active.values.all { it.isEmpty() }) { "MIDI has an unmatched note-on" }
        }
        return output
    }
    private fun writeMidi(path: Path, sequence: Sequence) { Files.createDirectories(requireNotNull(path.parent)); val temporary = path.resolveSibling(".${path.fileName}.tmp"); try { require(MidiSystem.write(sequence, 1, temporary.toFile()) > 0); atomicMove(temporary, path) } finally { Files.deleteIfExists(temporary) } }
    private fun chordAllows(authority: WholeSongAnalysisProjection, tick: Long, pitch: Int): Boolean = authority.harmony.singleOrNull { tick in it.startTick until it.endTick }?.let { chord -> pitch % 12 in chord.chord.quality.intervals.map { (it + chord.chord.rootChromatic) % 12 } } == true
    private fun isMelodyAnchor(root: Path, target: FullSongEnhancementTarget, note: FullSongEnhancementNote, ppq: Int, denominator: Int): Boolean = if (target.role != "piano") false else runCatching {
        val identity = MelodyIdentityBuilder.build(verified(root, target.input, "Cohesion MIDI '${target.id}'"), ppq * 4L / denominator); identity.anchorIds.any { anchor -> identity.note(anchor).let { it.track == note.track && it.channel == note.channel && it.originalStartTick + target.offsetTicks == note.startTick && it.originalEndTick + target.offsetTicks == note.endTick } }
    }.getOrDefault(false)
    private fun nextRevision(root: Path, input: String): String { val directory = root.resolve("midi/full-song-enhance/$input"); val count = if (Files.isDirectory(directory)) Files.list(directory).use { it.count() } else 0; return "r${(count + 1).toString().padStart(4, '0')}" }
    private fun verified(root: Path, ref: WorkflowArtifactReference, label: String): Path { val path = root.resolve(ref.file).normalize(); require(path.startsWith(root) && Files.isRegularFile(path) && sha256(path) == ref.sha256) { "$label is missing or stale." }; return path }
    private fun readReport(path: Path) = try { json.decodeFromString(FullSongEnhancementApplicationReport.serializer(), Files.readString(path)) } catch (error: Exception) { throw IllegalArgumentException("Full-Song Enhance report is malformed.", error) }
    private fun atomicWrite(path: Path, text: String) { Files.createDirectories(requireNotNull(path.parent)); val temporary = path.resolveSibling(".${path.fileName}.tmp"); try { Files.writeString(temporary, text, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING); atomicMove(temporary, path) } finally { Files.deleteIfExists(temporary) } }
    private fun atomicMove(source: Path, target: Path) { try { Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) } catch (_: AtomicMoveNotSupportedException) { Files.move(source, target, StandardCopyOption.REPLACE_EXISTING) } }
    private fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun <T> locked(root: Path, block: (Path) -> T): T { val normalized = root.toAbsolutePath().normalize(); val lock = ProjectMutationCoordinator.lock(normalized); check(lock.tryLock()) { "Another project mutation is already running." }; return try { block(normalized) } finally { lock.unlock() } }
    @kotlinx.serialization.Serializable private data class EnhancementInputHash(val critic: String, val report: String, val context: String, val targets: List<TargetHash>, val issues: List<String>)
    @kotlinx.serialization.Serializable private data class TargetHash(val id: String, val sha256: String)
    private companion object { val json = Json { encodeDefaults = true; explicitNulls = false; ignoreUnknownKeys = false } }
}
