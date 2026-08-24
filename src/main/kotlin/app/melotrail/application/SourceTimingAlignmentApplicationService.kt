package app.melotrail.application

import app.melotrail.arrangement.ArtifactRef
import app.melotrail.arrangement.ProjectStore
import app.melotrail.arrangement.SongPart
import app.melotrail.arrangement.sha256
import app.melotrail.preparation.MidiTimeMapper
import app.melotrail.preparation.MidiTimeMappingPaths
import app.melotrail.preparation.MidiTimeMappingReference
import app.melotrail.preparation.MidiTimeMappingReport
import app.melotrail.preparation.MidiTimeMappingStore
import app.melotrail.preparation.SourceTimingDecision
import app.melotrail.preparation.SourceTimingEvidenceStore
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

/** Request to publish one reviewed, derived timing candidate for an existing MIDI-first project part. */
data class AlignSourceTimingRequest(val root: Path, val partId: String, val decision: SourceTimingDecision)

/** Hash-bound result of a successful source-time alignment; the input remains a separate immutable artifact. */
data class SourceTimingAlignmentSnapshot(
    val reference: MidiTimeMappingReference,
    val report: MidiTimeMappingReport
)

/** Kotlin-owned publisher for reviewed timing candidates; it never changes normalized or transposed MIDI in place. */
class SourceTimingAlignmentApplicationService(private val mapper: MidiTimeMapper = MidiTimeMapper()) {
    /** Validates timing/source lineage, maps to a temporary candidate, then atomically records immutable project evidence. */
    fun align(request: AlignSourceTimingRequest): SourceTimingAlignmentSnapshot {
        val root = request.root.toAbsolutePath().normalize()
        val project = ProjectStore.read(root)
        val part = requireNotNull(project.parts.singleOrNull { it.id == request.partId }) { "Part not found: ${request.partId}" }
        val timingReference = requireNotNull(part.sourceTimingEvidence) { "Measure source timing before alignment." }
        require(request.decision.partId == part.id && request.decision.sourceTimingReport == timingReference.report) {
            "Timing decision does not match the persisted source timing report."
        }
        val evidence = SourceTimingEvidenceStore.read(root, timingReference.report)
        require(evidence.source.sha256 == timingReference.sourceSha256) { "Source timing evidence is stale." }
        val input = selectedTimingInput(root, part, request.decision.sourceMidi)
        val inputHash = sha256(input)
        val candidateDirectory = MidiTimeMappingPaths.candidateDirectory(root, part.id)
        Files.createDirectories(candidateDirectory)
        val temporary = Files.createTempFile(candidateDirectory, ".timing-${UUID.randomUUID()}-", ".mid")
        try {
            val report = mapper.map(input, temporary, request.decision, evidence)
            require(sha256(input) == inputHash) { "Timing alignment changed its input MIDI." }
            val candidate = MidiTimeMappingStore.publishCandidate(root, part.id, temporary)
            val reportReference = MidiTimeMappingStore.writeReport(root, report)
            val reference = MidiTimeMappingReference(candidate, reportReference, timingReference.report, request.decision.sourceMidi)
            val latest = ProjectStore.read(root)
            val latestPart = requireNotNull(latest.parts.singleOrNull { it.id == part.id }) { "Part changed during timing alignment; retry it." }
            require(latestPart.sourceTimingEvidence == timingReference && sha256(selectedTimingInput(root, latestPart, request.decision.sourceMidi)) == inputHash) {
                "Timing inputs changed during alignment; retry it."
            }
            ProjectStore.write(root, latest.copy(parts = latest.parts.map { candidatePart ->
                if (candidatePart.id == part.id) candidatePart.copy(timingMappingEvidence = reference) else candidatePart
            }))
            return SourceTimingAlignmentSnapshot(reference, report)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    /** Resolves only the exact normalized/transposed evidence named by the decision and rejects escaping aliases. */
    private fun selectedTimingInput(root: Path, part: SongPart, requested: ArtifactRef): Path {
        val available = listOfNotNull(part.midi?.normalized, part.midi?.transposed)
        require(requested.path in available) { "Timing alignment must use current normalized or transposed MIDI evidence." }
        val path = root.resolve(requested.path).normalize()
        require(path.startsWith(root) && Files.isRegularFile(path) && !Files.isSymbolicLink(path) && path.toRealPath().startsWith(root.toRealPath()) && sha256(path) == requested.sha256) {
            "Timing alignment input MIDI is missing or stale."
        }
        return path
    }
}
