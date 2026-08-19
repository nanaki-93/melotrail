package app.melotrail.arrangement

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import javax.sound.midi.MetaMessage
import javax.sound.midi.MidiEvent
import javax.sound.midi.MidiSystem
import javax.sound.midi.Sequence

/**
 * The only boundary for the MIDI artifact selected by a MIDI-first part.
 *
 * Cleanup/report code intentionally addresses raw and cleaned evidence directly. Every
 * semantic consumer of the user's current source must use this type instead, so a Lo-fi
 * selection cannot silently fall back to cleaned MIDI.
 */
class SelectedMidiArtifactResolver {
    fun resolve(projectRoot: Path, project: Project, partId: String): SelectedMidiArtifact =
        resolve(projectRoot, project, project.parts.singleOrNull { it.id == partId }
            ?: throw IllegalArgumentException("Unknown MIDI part '$partId'."))

    fun resolve(projectRoot: Path, project: Project, part: SongPart): SelectedMidiArtifact {
        require(project.version >= Project.MIDI_FIRST_VERSION) {
            "Project uses legacy v1 source audio; it has no selected MIDI artifact."
        }
        val root = projectRoot.toAbsolutePath().normalize()
        val rootReal = root.toRealPath()
        val midi = requireNotNull(part.midi) { "Part '${part.id}' has no MIDI references." }
        val cleanedReference = requireNotNull(midi.clean) {
            "Part '${part.id}' has not been cleaned. Run Clean MIDI before continuing."
        }
        val cleaned = resolveFile(root, rootReal, cleanedReference, "cleaned MIDI")
        val cleanupFreshness = cleanupFreshness(root, part, midi, cleanedReference)
        if (cleanupFreshness == MidiCleanupFreshness.LEGACY_UNKNOWN) {
            require(midi.aiFixSelection == MidiAiFixSelection.SKIP && midi.analysisInput == MidiAnalysisInput.CURRENT) {
                "Part '${part.id}' has legacy cleaned MIDI without current approval; re-import and run Clean MIDI before choosing an AI fix or Lo-fi Feel."
            }
        }
        val cleanedSha256 = sha256(cleaned)
        val base = when (midi.aiFixSelection) {
            MidiAiFixSelection.SKIP -> BaseCandidate(
                cleanedReference,
                cleaned,
                cleanedSha256,
                SelectedMidiBaseKind.CLEANED
            )
            MidiAiFixSelection.APPROVED -> {
                val references = requireNotNull(midi.aiFix) { "Part '${part.id}' has no approved AI-fix references." }
                references.requireCanonical(part.id)
                require(references.inputSha256 == cleanedSha256) {
                    "Approved AI fix is stale for part '${part.id}'; keep cleaned MIDI or regenerate the AI fix."
                }
                val approved = requireNotNull(references.approved) { "Part '${part.id}' has no approved AI-fix artifact." }
                val path = resolveFile(root, rootReal, approved.file, "approved AI-fix MIDI")
                val hash = sha256(path)
                require(hash == approved.sha256) {
                    "Approved AI fix is stale for part '${part.id}'; keep cleaned MIDI or regenerate the AI fix."
                }
                readMidi(path, part.id)
                BaseCandidate(approved.file, path, hash, SelectedMidiBaseKind.APPROVED_AI_FIX)
            }
        }
        val selected = when (midi.analysisInput) {
            MidiAnalysisInput.CURRENT -> Candidate(
                base.reference,
                base.path,
                if (base.kind == SelectedMidiBaseKind.CLEANED) SelectedMidiArtifactKind.CLEANED else SelectedMidiArtifactKind.APPROVED_AI_FIX,
                null,
                null
            )
            MidiAnalysisInput.LOFI_FEEL -> {
                require(cleanupFreshness != MidiCleanupFreshness.STALE) {
                    "Part '${part.id}' has stale cleaned MIDI evidence. Run Clean MIDI again."
                }
                val feel = requireNotNull(midi.feel) { "Part '${part.id}' has no current Lo-fi MIDI Feel artifact." }
                val derived = resolveFile(root, rootReal, feel.derived, "Lo-fi MIDI Feel")
                require(MidiFeelReportStore.isCurrent(root, part.id, base.reference, feel)) {
                    "Lo-fi MIDI Feel artifact is missing, malformed, or stale for part '${part.id}'. Choose Original or regenerate Lo-fi MIDI Feel."
                }
                Candidate(feel.derived, derived, SelectedMidiArtifactKind.LOFI_FEEL, feel.profile, MidiFeelReportStore.read(root, feel.report).version)
            }
        }
        val sequence = readMidi(selected.path, part.id)
        return SelectedMidiArtifact(
            projectRelativePath = selected.reference,
            path = selected.path,
            partId = part.id,
            kind = selected.kind,
            profile = selected.profile,
            profileVersion = selected.profileVersion,
            sha256 = sha256(selected.path),
            ppq = sequence.resolution,
            timing = MidiTimingSummary(tempoMap(sequence), timeSignatures(sequence)),
            cleanupFreshness = cleanupFreshness,
            baseKind = base.kind,
            loFiFreshness = if (selected.kind == SelectedMidiArtifactKind.LOFI_FEEL) MidiLoFiFreshness.CURRENT else MidiLoFiFreshness.NOT_SELECTED
        )
    }

    private fun cleanupFreshness(root: Path, part: SongPart, midi: MidiReferences, cleanedReference: String): MidiCleanupFreshness {
        // V2 projects predating explicit cleanup evidence remain readable. They are never
        // accepted as current Lo-fi input because that branch requires a current report.
        if (midi.raw == null && midi.cleanup == null && midi.quality == null) return MidiCleanupFreshness.LEGACY_UNKNOWN
        require(midi.raw != null && midi.cleanup != null && midi.quality != null) {
            "Part '${part.id}' has incomplete MIDI cleanup provenance."
        }
        require(MidiQualityReportStore.isCurrent(root, part.id, midi.raw, cleanedReference, midi.cleanup, midi.quality)) {
            "Part '${part.id}' has stale cleaned MIDI evidence. Run Clean MIDI again."
        }
        require(MidiQualityReportStore.isApproved(root, midi.quality, midi.cleanApproval)) {
            "Part '${part.id}' needs current approval of its Clean MIDI evidence."
        }
        return MidiCleanupFreshness.CURRENT
    }

    private fun resolveFile(root: Path, rootReal: Path, reference: String, label: String): Path {
        val relative = try { Path.of(reference) } catch (error: Exception) {
            throw IllegalArgumentException("$label path is invalid: $reference", error)
        }
        require(reference.isNotBlank() && !relative.isAbsolute) { "$label path must be project-relative." }
        val path = root.resolve(relative).normalize()
        require(path.startsWith(root) && Files.isRegularFile(path)) { "$label is missing: $reference" }
        require(path.toRealPath().startsWith(rootReal)) { "$label path escapes the project root: $reference" }
        return path
    }

    private fun readMidi(path: Path, partId: String): Sequence = try {
        require(Files.size(path) >= 14) { "Selected MIDI for '$partId' is malformed." }
        Files.newInputStream(path).use { require(it.readNBytes(4).decodeToString() == "MThd") { "Selected MIDI for '$partId' is malformed." } }
        MidiSystem.getSequence(path.toFile()).also { sequence ->
            require(sequence.divisionType == Sequence.PPQ && sequence.resolution > 0) { "Selected MIDI for '$partId' must use positive PPQ timing." }
        }
    } catch (error: IllegalArgumentException) {
        throw error
    } catch (error: Exception) {
        throw IllegalArgumentException("Selected MIDI for '$partId' is malformed.", error)
    }

    private fun tempoMap(sequence: Sequence): List<MidiTempoChange> = sequence.events().mapNotNull { event ->
        val message = event.message as? MetaMessage ?: return@mapNotNull null
        if (message.type != 0x51 || message.data.size != 3) return@mapNotNull null
        val micros = ((message.data[0].toInt() and 0xff) shl 16) or ((message.data[1].toInt() and 0xff) shl 8) or (message.data[2].toInt() and 0xff)
        require(micros > 0) { "Selected MIDI has an invalid tempo event." }
        MidiTempoChange(event.tick, 60_000_000.0 / micros)
    }.sortedBy { it.tick }.distinctBy { it.tick }.toList().let { map -> if (map.firstOrNull()?.tick == 0L) map else listOf(MidiTempoChange(0, 120.0, true)) + map }

    private fun timeSignatures(sequence: Sequence): List<MidiTimeSignature> = sequence.events().mapNotNull { event ->
        val message = event.message as? MetaMessage ?: return@mapNotNull null
        if (message.type != 0x58 || message.data.size < 2) return@mapNotNull null
        val numerator = message.data[0].toInt() and 0xff
        val exponent = message.data[1].toInt() and 0xff
        require(numerator > 0 && exponent in 0..5) { "Selected MIDI has an unsupported time signature." }
        MidiTimeSignature(event.tick, numerator, 1 shl exponent)
    }.sortedBy { it.tick }.distinctBy { it.tick }.toList().let { map -> if (map.firstOrNull()?.tick == 0L) map else listOf(MidiTimeSignature(0, 4, 4, true)) + map }

    private fun Sequence.events(): kotlin.sequences.Sequence<MidiEvent> = tracks.asSequence().flatMap { track -> (0 until track.size()).asSequence().map(track::get) }
    private fun sha256(path: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    private data class BaseCandidate(val reference: String, val path: Path, val sha256: String, val kind: SelectedMidiBaseKind)
    private data class Candidate(val reference: String, val path: Path, val kind: SelectedMidiArtifactKind, val profile: MidiFeelProfile?, val profileVersion: Int?)
}

enum class SelectedMidiBaseKind { CLEANED, APPROVED_AI_FIX }
enum class SelectedMidiArtifactKind { CLEANED, APPROVED_AI_FIX, LOFI_FEEL }
enum class MidiCleanupFreshness { CURRENT, LEGACY_UNKNOWN, STALE }
enum class MidiLoFiFreshness { CURRENT, NOT_SELECTED }
data class MidiTimingSummary(val tempoMap: List<MidiTempoChange>, val timeSignatures: List<MidiTimeSignature>)
data class SelectedMidiArtifact(
    val projectRelativePath: String,
    val path: Path,
    val partId: String,
    val kind: SelectedMidiArtifactKind,
    val profile: MidiFeelProfile?,
    val profileVersion: Int?,
    val sha256: String,
    val ppq: Int,
    val timing: MidiTimingSummary,
    val cleanupFreshness: MidiCleanupFreshness,
    val baseKind: SelectedMidiBaseKind,
    val loFiFreshness: MidiLoFiFreshness
)
