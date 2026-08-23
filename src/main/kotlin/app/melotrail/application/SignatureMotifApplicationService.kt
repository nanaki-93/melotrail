package app.melotrail.application

import app.melotrail.arrangement.HumanizationRole
import app.melotrail.arrangement.HumanizationSelection
import app.melotrail.arrangement.MelodyIdentityBuilder
import app.melotrail.arrangement.MidiPartAnalyzer
import app.melotrail.arrangement.ProjectStore
import app.melotrail.arrangement.ProjectWorkflowStore
import app.melotrail.arrangement.SelectedMidiArtifactResolver
import app.melotrail.arrangement.SignatureMotif
import app.melotrail.arrangement.SignatureMotifArtifactPaths
import app.melotrail.arrangement.SignatureMotifCandidateNote
import app.melotrail.arrangement.SignatureMotifCandidateOccurrence
import app.melotrail.arrangement.SignatureMotifRecognizer
import app.melotrail.arrangement.SignatureMotifReleaseGateResult
import app.melotrail.arrangement.SignatureMotifThresholds
import app.melotrail.arrangement.SignatureMotifWorkflowReferences
import app.melotrail.arrangement.WorkflowArtifact
import app.melotrail.arrangement.WorkflowArtifactReference
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import javax.sound.midi.MidiSystem
import javax.sound.midi.ShortMessage

data class SignatureMotifPhraseOption(val id: String, val noteCount: Int, val anchorCount: Int)

/** Typed desktop-facing boundary for selecting/confirming a source phrase and publishing its release evidence. */
interface SignatureMotifApplicationService {
    fun availablePhrases(root: Path, partId: String): List<SignatureMotifPhraseOption>
    fun select(root: Path, partId: String, phraseId: String): SignatureMotif
    fun confirm(root: Path): SignatureMotif
    fun evaluateReleaseGate(root: Path, thresholds: SignatureMotifThresholds = SignatureMotifThresholds()): SignatureMotifReleaseGateResult
}

class DefaultSignatureMotifApplicationService : SignatureMotifApplicationService {
    override fun availablePhrases(root: Path, partId: String): List<SignatureMotifPhraseOption> = locked(root) { normalized ->
        val (identity, _) = identity(normalized, partId)
        identity.phrases.map { phrase ->
            SignatureMotifPhraseOption(phrase.id, phrase.noteIds.size, phrase.noteIds.count(identity::isAnchor))
        }
    }

    override fun select(root: Path, partId: String, phraseId: String): SignatureMotif = locked(root) { normalized ->
        val (identity, _) = identity(normalized, partId)
        val phrase = requireNotNull(identity.phrases.singleOrNull { it.id == phraseId }) { "Signature motif phrase '$phraseId' is unavailable." }
        val motif = SignatureMotif(partId = partId, sourceSha256 = identity.sourceSha256, phraseId = phrase.id, sourceNoteIds = phrase.noteIds)
        ProjectWorkflowStore.update(normalized) { it.copy(signatureMotif = SignatureMotifWorkflowReferences(motif)) }
        motif
    }

    override fun confirm(root: Path): SignatureMotif = locked(root) { normalized ->
        val project = ProjectStore.read(normalized)
        val stored = requireNotNull(project.workflow.signatureMotif) { "Select a signature motif before confirming it." }
        val (identity, _) = identity(normalized, stored.motif.partId)
        require(stored.motif.sourceSha256 == identity.sourceSha256 && identity.phrases.any { it.id == stored.motif.phraseId && it.noteIds == stored.motif.sourceNoteIds }) {
            "Signature motif selection is stale; select it again."
        }
        val confirmed = stored.motif.confirm()
        ProjectWorkflowStore.update(normalized) { it.copy(signatureMotif = SignatureMotifWorkflowReferences(confirmed)) }
        confirmed
    }

    override fun evaluateReleaseGate(root: Path, thresholds: SignatureMotifThresholds): SignatureMotifReleaseGateResult = locked(root) { normalized ->
        val project = ProjectStore.read(normalized).also { it.requireValid(normalized) }
        val stored = requireNotNull(project.workflow.signatureMotif) { "Select and confirm a signature motif before evaluating recognizability." }
        val motif = stored.motif
        require(motif.confirmed) { "Confirm the selected signature motif before evaluating recognizability." }
        val (identity, _) = identity(normalized, motif.partId)
        val candidates = candidates(normalized, project, motif.partId)
        val result = SignatureMotifRecognizer.evaluate(identity, motif, candidates, thresholds)
        val inputHash = digest(json.encodeToString(SignatureMotifGateInput(
            motif, candidates.map { CandidateHash(it.occurrenceId, it.offsetTicks, it.notes.map(SignatureMotifCandidateNote::id)) }, thresholds
        )).toByteArray(StandardCharsets.UTF_8))
        val relative = SignatureMotifArtifactPaths.report(motif.sourceSha256, inputHash)
        val path = normalized.resolve(relative)
        atomicWrite(path, json.encodeToString(SignatureMotifReleaseGateResult.serializer(), result))
        val reference = WorkflowArtifactReference(relative, digest(path))
        ProjectWorkflowStore.update(normalized) { workflow ->
            workflow.copy(signatureMotif = SignatureMotifWorkflowReferences(motif, reference, result))
        }
        result
    }

    private fun identity(root: Path, partId: String): Pair<app.melotrail.arrangement.MelodyIdentity, Path> {
        val project = ProjectStore.read(root).also { it.requireValid(root) }
        val part = requireNotNull(project.parts.singleOrNull { it.id == partId }) { "Unknown signature motif part '$partId'." }
        val selected = SelectedMidiArtifactResolver().resolve(root, project, part)
        requireNotNull(part.analysis) { "Analyze the signature motif part before selecting a phrase." }
        val analysis = MidiPartAnalyzer().analyze(selected.path, partId)
        val beat = analysis.ppq.toLong() * 4 / analysis.timeSignatures.first().denominator
        return MelodyIdentityBuilder.build(selected.path, beat) to selected.path
    }

    /** Select the actual downstream piano artifact: Cohesion, then approved polish, then selected humanization. */
    private fun candidates(root: Path, project: app.melotrail.arrangement.Project, partId: String): List<SignatureMotifCandidateOccurrence> {
        val cohesion = requireNotNull(project.workflow.cohesion) { "Recognizability requires approved Cohesion." }
        require(cohesion.approved && WorkflowArtifact.COHESION !in project.workflow.stale) { "Recognizability requires current approved Cohesion." }
        val selectedOutputs = project.workflow.humanization?.takeIf { project.workflow.humanizationSelection == HumanizationSelection.HUMANIZED && WorkflowArtifact.HUMANIZATION !in project.workflow.stale }
            ?.artifacts?.filter { it.role == HumanizationRole.PIANO }?.associate { it.id to it.output }.orEmpty()
        val enhancedOutputs = project.workflow.fullSongEnhancement?.takeIf { project.workflow.fullSongEnhancementSelection.name == "APPROVED" && WorkflowArtifact.FULL_SONG_ENHANCEMENT !in project.workflow.stale }
            ?.artifacts?.associate { it.id to it.output }.orEmpty()
        val authority = MusicalAuthorityBuilder().wholeSongAnalysis(root)
        return project.envelope.structureOccurrences.filter { it.partId == partId }.sortedBy { it.instanceId }.map { occurrence ->
            val cohesionOutput = requireNotNull(cohesion.occurrences.singleOrNull { it.instanceId == occurrence.instanceId }) { "Cohesion is missing occurrence '${occurrence.instanceId}'." }.result
            val id = "piano-${occurrence.instanceId}"
            val reference = selectedOutputs[id] ?: enhancedOutputs[id] ?: cohesionOutput
            val path = verified(root, reference, "Signature motif output '$id'")
            val offset = requireNotNull(authority.occurrences.singleOrNull { it.occurrenceId == occurrence.instanceId }) { "Signature motif occurrence is absent from the canonical timeline." }.startTick
            SignatureMotifCandidateOccurrence(occurrence.instanceId, offset, readCandidateNotes(path, reference.sha256))
        }
    }

    private fun readCandidateNotes(path: Path, sourceHash: String): List<SignatureMotifCandidateNote> {
        val sequence = try { MidiSystem.getSequence(path.toFile()) } catch (error: Exception) { throw IllegalArgumentException("Signature motif output MIDI is malformed", error) }
        val active = mutableMapOf<Triple<Int, Int, Int>, ArrayDeque<Pair<Long, Int>>>()
        val ordinals = mutableMapOf<Pair<Int, Int>, Int>(); val result = mutableListOf<SignatureMotifCandidateNote>()
        sequence.tracks.forEachIndexed { track, events -> (0 until events.size()).forEach { index ->
            val event = events[index]; val message = event.message as? ShortMessage ?: return@forEach; val key = Triple(track, message.channel, message.data1)
            when {
                message.command == ShortMessage.NOTE_ON && message.data2 > 0 -> active.getOrPut(key) { ArrayDeque() }.addLast(event.tick to message.data2)
                message.command == ShortMessage.NOTE_OFF || message.command == ShortMessage.NOTE_ON && message.data2 == 0 -> {
                    val start = active[key]?.removeFirstOrNull() ?: throw IllegalArgumentException("Signature motif output has an unmatched note-off")
                    require(event.tick > start.first) { "Signature motif output has a non-positive note" }
                    val ordinal = ordinals.merge(track to message.channel, 1, Int::plus)!! - 1
                    val id = "c-" + digest("signature-motif-candidate-v1|$sourceHash|$track|${message.channel}|$ordinal|${message.data1}|${start.first}|${event.tick}".toByteArray(StandardCharsets.UTF_8))
                    result += SignatureMotifCandidateNote(id, message.data1, start.first, event.tick)
                }
            }
        } }
        require(active.values.all { it.isEmpty() }) { "Signature motif output has an unmatched note-on" }
        return result
    }

    private fun verified(root: Path, reference: WorkflowArtifactReference, label: String): Path {
        val path = root.resolve(reference.file).normalize()
        require(path.startsWith(root) && Files.isRegularFile(path) && digest(path) == reference.sha256) { "$label is missing or stale." }
        return path
    }

    private fun atomicWrite(path: Path, text: String) {
        Files.createDirectories(requireNotNull(path.parent)); val temporary = path.resolveSibling(".${path.fileName}.tmp")
        try { Files.writeString(temporary, text, StandardCharsets.UTF_8); try { Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
        catch (_: AtomicMoveNotSupportedException) { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING) } } finally { Files.deleteIfExists(temporary) }
    }

    private fun digest(path: Path): String = digest(Files.readAllBytes(path))
    private fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun <T> locked(root: Path, block: (Path) -> T): T { val normalized = root.toAbsolutePath().normalize(); val lock = ProjectMutationCoordinator.lock(normalized); check(lock.tryLock()) { "Another project mutation is already running." }; return try { block(normalized) } finally { lock.unlock() } }

    @kotlinx.serialization.Serializable private data class SignatureMotifGateInput(val motif: SignatureMotif, val candidates: List<CandidateHash>, val thresholds: SignatureMotifThresholds)
    @kotlinx.serialization.Serializable private data class CandidateHash(val occurrenceId: String, val offsetTicks: Long, val noteIds: List<String>)
    private companion object { val json = Json { prettyPrint = true; encodeDefaults = true; explicitNulls = false } }
}
