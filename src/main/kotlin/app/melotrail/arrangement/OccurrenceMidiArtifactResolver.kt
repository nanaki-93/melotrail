package app.melotrail.arrangement

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import javax.sound.midi.MidiSystem
import javax.sound.midi.Sequence

/** MIDI identity for one stable structure occurrence, never a shared-part fallback after approval. */
data class OccurrenceMidiArtifact(
    val occurrenceId: String,
    val partId: String,
    val path: Path,
    val projectRelativePath: String,
    val sha256: String,
    val ppq: Int,
    val timing: MidiTimingSummary,
    val source: OccurrenceMidiSource
)

enum class OccurrenceMidiSource { SELECTED_PART, APPROVED_COHESION }

/**
 * Resolves the one MIDI input for each saved structure occurrence. Before
 * approval it delegates to the selected-part boundary. Once cohesion is
 * approved, every occurrence must have its own current, hash-matched result.
 */
class OccurrenceMidiArtifactResolver(
    private val selectedResolver: SelectedMidiArtifactResolver = SelectedMidiArtifactResolver()
) {
    fun resolve(
        root: Path,
        project: Project,
        input: MelodyCohesionInput
    ): List<OccurrenceMidiArtifact> {
        val normalized = root.toAbsolutePath().normalize()
        require(project.version == Project.CURRENT_VERSION) { "Occurrence MIDI requires a MIDI-first v3 project." }
        // Cohesion is now downstream of Arrangement and contributes boundary MIDI
        // at render time. Source occurrences remain immutable planning evidence.
        return input.occurrences.map { occurrence -> fallback(normalized, project, occurrence) }
    }

    private fun fallback(root: Path, project: Project, occurrence: MelodyOccurrenceInput): OccurrenceMidiArtifact {
        val selected = selectedResolver.resolve(root, project, occurrence.partId)
        require(selected.sha256 == occurrence.sourceHash) { "Selected MIDI for '${occurrence.partId}' changed; regenerate cohesion before arranging." }
        return OccurrenceMidiArtifact(
            occurrence.instanceId, occurrence.partId, selected.path, selected.projectRelativePath, selected.sha256,
            selected.ppq, selected.timing, OccurrenceMidiSource.SELECTED_PART
        )
    }

    private fun confinedFile(root: Path, reference: String, occurrenceId: String): Path {
        val relative = runCatching { Path.of(reference) }.getOrElse { throw IllegalArgumentException("Cohesion MIDI path is invalid for '$occurrenceId'.", it) }
        require(reference.isNotBlank() && !relative.isAbsolute && !reference.contains("..")) { "Cohesion MIDI path must be project-relative for '$occurrenceId'." }
        val path = root.resolve(relative).normalize()
        require(path.startsWith(root) && Files.isRegularFile(path) && path.toRealPath().startsWith(root.toRealPath())) {
            "Cohesion MIDI is missing or escapes the project for '$occurrenceId'."
        }
        return path
    }

    private fun readMidi(path: Path, occurrenceId: String): Sequence = try {
        require(Files.size(path) >= 14) { "Cohesion MIDI is malformed for '$occurrenceId'." }
        MidiSystem.getSequence(path.toFile()).also { require(it.divisionType == Sequence.PPQ && it.resolution > 0) { "Cohesion MIDI has invalid PPQ for '$occurrenceId'." } }
    } catch (error: IllegalArgumentException) { throw error
    } catch (error: Exception) { throw IllegalArgumentException("Cohesion MIDI is malformed for '$occurrenceId'.", error) }

    private fun timing(sequence: Sequence): MidiTimingSummary {
        val events = sequence.tracks.asSequence().flatMap { track -> (0 until track.size()).asSequence().map(track::get) }
        val tempos = events.mapNotNull { event ->
            val meta = event.message as? javax.sound.midi.MetaMessage ?: return@mapNotNull null
            if (meta.type != 0x51 || meta.data.size != 3) null else {
                val micros = ((meta.data[0].toInt() and 255) shl 16) or ((meta.data[1].toInt() and 255) shl 8) or (meta.data[2].toInt() and 255)
                require(micros > 0); MidiTempoChange(event.tick, 60_000_000.0 / micros)
            }
        }.sortedBy { it.tick }.distinctBy { it.tick }.toList().let { if (it.firstOrNull()?.tick == 0L) it else listOf(MidiTempoChange(0, 120.0, true)) + it }
        val meters = sequence.tracks.asSequence().flatMap { track -> (0 until track.size()).asSequence().map(track::get) }.mapNotNull { event ->
            val meta = event.message as? javax.sound.midi.MetaMessage ?: return@mapNotNull null
            if (meta.type != 0x58 || meta.data.size < 2) null else MidiTimeSignature(event.tick, meta.data[0].toInt() and 255, 1 shl (meta.data[1].toInt() and 255))
        }.sortedBy { it.tick }.distinctBy { it.tick }.toList().let { if (it.firstOrNull()?.tick == 0L) it else listOf(MidiTimeSignature(0, 4, 4, true)) + it }
        return MidiTimingSummary(tempos, meters)
    }

    private fun digest(path: Path): String = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)).joinToString("") { "%02x".format(it) }
}
