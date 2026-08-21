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

enum class OccurrenceMidiSource { SELECTED_PART, COHESION }

/**
 * An approved Cohesion plan may publish a hash-bound, occurrence-local melody
 * derivative. The selected part remains immutable and is the fallback source.
 */
class OccurrenceMidiArtifactResolver(
    private val selectedResolver: SelectedMidiArtifactResolver = SelectedMidiArtifactResolver()
) {
    fun resolve(root: Path, project: Project, occurrences: List<SectionInstance>): List<OccurrenceMidiArtifact> {
        val normalized = root.toAbsolutePath().normalize()
        require(project.version == Project.CURRENT_VERSION) { "Occurrence MIDI requires a MIDI-first v4 project." }
        return occurrences.map { occurrence ->
            val selected = selectedResolver.resolve(normalized, project, occurrence.partId)
            val cohesive = project.workflow.cohesion?.takeIf { it.approved && WorkflowArtifact.COHESION !in project.workflow.stale }
                ?.occurrences?.singleOrNull { it.instanceId == occurrence.instanceId }
            val resolved = cohesive?.let { reference ->
                require(reference.approved && reference.sourceSha256 == selected.sha256) { "Approved Cohesion occurrence '${occurrence.instanceId}' is stale" }
                val path = normalized.resolve(reference.result.file).normalize()
                require(path.startsWith(normalized) && java.nio.file.Files.isRegularFile(path) && sha256(path) == reference.result.sha256) {
                    "Approved Cohesion occurrence '${occurrence.instanceId}' is missing or changed"
                }
                Triple(path, reference.result.file, reference.result.sha256)
            }
            OccurrenceMidiArtifact(
                occurrence.instanceId, occurrence.partId, resolved?.first ?: selected.path, resolved?.second ?: selected.projectRelativePath,
                resolved?.third ?: selected.sha256, selected.ppq, selected.timing, if (resolved == null) OccurrenceMidiSource.SELECTED_PART else OccurrenceMidiSource.COHESION
            )
        }
    }
}
