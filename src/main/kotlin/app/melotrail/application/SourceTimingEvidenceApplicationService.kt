package app.melotrail.application

import app.melotrail.arrangement.ProjectStore
import app.melotrail.arrangement.SongPart
import app.melotrail.arrangement.sha256
import app.melotrail.preparation.InspectionSourceIdentity
import app.melotrail.preparation.DownbeatEvidenceStatus
import app.melotrail.preparation.SourceGrooveTemplateStatus
import app.melotrail.preparation.SourceTimingBoundary
import app.melotrail.preparation.SourceTimingEvidence
import app.melotrail.preparation.SourceTimingEvidenceReference
import app.melotrail.preparation.SourceTimingEvidenceStore
import app.melotrail.preparation.SourceTimingMeasurementRequest
import app.melotrail.preparation.SourceTimingMeasurementResult
import java.nio.file.Files
import java.nio.file.Path

/** Request to measure one preserved source without changing its source or MIDI artifacts. */
data class MeasureSourceTimingRequest(val root: Path, val partId: String)

/** Persisted timing-evidence result, including whether a human timing review remains required. */
data class SourceTimingEvidenceSnapshot(
    val report: SourceTimingEvidence,
    val reference: SourceTimingEvidenceReference,
    val reviewRequired: Boolean
)

/** Kotlin-owned orchestration for worker timing evidence and immutable project persistence. */
class SourceTimingEvidenceApplicationService(private val boundary: SourceTimingBoundary) {
    /** Measures and atomically records source timing facts while preserving original source bytes. */
    suspend fun measure(request: MeasureSourceTimingRequest): SourceTimingEvidenceSnapshot {
        val root = request.root.toAbsolutePath().normalize()
        val project = ProjectStore.read(root)
        val part = requireNotNull(project.parts.singleOrNull { it.id == request.partId }) { "Part not found: ${request.partId}" }
        val source = projectSource(root, part)
        val sourceHash = sha256(source)
        part.importEvidence?.let { require(it.sourceSha256 == sourceHash) { "Preserved source changed; re-import it before timing analysis." } }
        val identity = InspectionSourceIdentity(part.file, sourceHash).also(InspectionSourceIdentity::requireValid)
        val result = boundary.measure(SourceTimingMeasurementRequest(root, part.id, identity))
        val observation = when (result) {
            is SourceTimingMeasurementResult.Measured -> result.observation
            is SourceTimingMeasurementResult.Rejected -> throw IllegalStateException("Source timing evidence failed (${result.error.code}): ${result.error.message}")
        }
        require(sha256(source) == sourceHash) { "Preserved source changed during timing analysis." }
        val report = observation.toEvidence(part.id, identity)
        val artifact = SourceTimingEvidenceStore.write(root, report)
        val reference = SourceTimingEvidenceReference(artifact, sourceHash).also(SourceTimingEvidenceReference::requireValid)
        val latest = ProjectStore.read(root)
        val latestPart = requireNotNull(latest.parts.singleOrNull { it.id == part.id }) { "Part changed during timing analysis; retry it." }
        require(sha256(projectSource(root, latestPart)) == sourceHash) { "Preserved source changed during timing analysis." }
        ProjectStore.write(root, latest.copy(parts = latest.parts.map { candidate ->
            if (candidate.id == part.id) candidate.copy(sourceTimingEvidence = reference) else candidate
        }))
        return SourceTimingEvidenceSnapshot(
            report,
            reference,
            report.downbeat.status == DownbeatEvidenceStatus.REVIEW_REQUIRED || report.groove.status == SourceGrooveTemplateStatus.REVIEW_REQUIRED
        )
    }

    /** Resolves a verified source file while rejecting traversal and symlink escapes. */
    private fun projectSource(root: Path, part: SongPart): Path {
        val source = root.resolve(part.file).normalize()
        require(source.startsWith(root) && Files.isRegularFile(source) && source.toRealPath().startsWith(root.toRealPath())) {
            "Timing source is not a project-local file."
        }
        return source
    }
}
