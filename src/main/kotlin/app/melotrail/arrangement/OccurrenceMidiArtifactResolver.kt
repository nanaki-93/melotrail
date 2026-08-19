package app.melotrail.arrangement

import java.nio.file.Path

/** Immutable selected MIDI identity for one stable structure occurrence. */
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

enum class OccurrenceMidiSource { SELECTED_PART }

/**
 * Cohesion never becomes a melody source. It emits separate, hash-bound
 * transition MIDI, while every occurrence resolves the immutable selected part.
 */
class OccurrenceMidiArtifactResolver(
    private val selectedResolver: SelectedMidiArtifactResolver = SelectedMidiArtifactResolver()
) {
    fun resolve(root: Path, project: Project, occurrences: List<SectionInstance>): List<OccurrenceMidiArtifact> {
        val normalized = root.toAbsolutePath().normalize()
        require(project.version == Project.CURRENT_VERSION) { "Occurrence MIDI requires a MIDI-first v4 project." }
        return occurrences.map { occurrence ->
            val selected = selectedResolver.resolve(normalized, project, occurrence.partId)
            OccurrenceMidiArtifact(
                occurrence.instanceId, occurrence.partId, selected.path, selected.projectRelativePath,
                selected.sha256, selected.ppq, selected.timing, OccurrenceMidiSource.SELECTED_PART
            )
        }
    }
}
