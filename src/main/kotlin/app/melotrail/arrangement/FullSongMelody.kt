package app.melotrail.arrangement

import app.melotrail.preparation.SourceGrooveBin
import app.melotrail.preparation.SourceGrooveTemplate
import app.melotrail.preparation.SourceGrooveTemplateStatus
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.roundToLong

/** A source-bound groove template accepted for one source-song occurrence, or an explicit grid fallback. */
@Serializable
data class SourceSongGrooveEvidence(
    val status: SourceSongGrooveStatus,
    val sourceTimingReport: WorkflowArtifactReference? = null,
    val template: SourceGrooveTemplate? = null
) {
    /** Ensure measured feel has its immutable report while fallback never invents a source groove. */
    fun requireValid() {
        when (status) {
            SourceSongGrooveStatus.MEASURED -> {
                require(sourceTimingReport != null && template != null && template.status == SourceGrooveTemplateStatus.MEASURED) {
                    "Measured source-song groove lacks accepted timing evidence"
                }
                template.requireValid(template.sourceSha256)
            }
            SourceSongGrooveStatus.GRID_FALLBACK -> require(sourceTimingReport == null && template == null) {
                "Grid fallback must not claim source-groove evidence"
            }
        }
    }

    companion object {
        /** Explicitly record a neutral grid when no reviewed source groove is accepted. */
        fun gridFallback(): SourceSongGrooveEvidence = SourceSongGrooveEvidence(SourceSongGrooveStatus.GRID_FALLBACK)
    }
}

/** Distinguishes accepted measured feel from an explicit authoritative-grid fallback. */
@Serializable
enum class SourceSongGrooveStatus { MEASURED, GRID_FALLBACK }

/** One occurrence window, marker, and source lineage binding on the canonical full melody. */
@Serializable
data class FullMelodyOccurrenceWindow(
    val occurrenceId: String,
    val sectionRole: SectionTypeId,
    val sourcePartId: String,
    val startBar: Long,
    val endBar: Long,
    val startTick: Long,
    val endTick: Long,
    val pickupStartTick: Long,
    val pickupEndTick: Long,
    val bodyStartTick: Long,
    val bodyEndTick: Long,
    val tailStartTick: Long,
    val tailEndTick: Long,
    val markerText: String,
    val sourceMidiSha256: String,
    val monophonicPreparationReport: WorkflowArtifactReference?,
    val harmonyFitReport: WorkflowArtifactReference?,
    val groove: SourceSongGrooveEvidence
) {
    /** Validate half-open occurrence and pickup/body/tail windows without accepting an escaping or unbound source. */
    fun requireValid() {
        require(IDENTIFIER.matches(occurrenceId) && sourcePartId.isNotBlank() && startBar >= 0 && endBar > startBar && startTick >= 0 && endTick > startTick &&
            pickupStartTick == startTick && pickupEndTick in startTick..endTick && bodyStartTick == pickupEndTick && bodyEndTick in bodyStartTick..endTick &&
            tailStartTick == bodyEndTick && tailEndTick == endTick && markerText.isNotBlank() && HASH.matches(sourceMidiSha256)) {
            "Full-melody occurrence window is invalid"
        }
        groove.requireValid()
    }
}

/** One full-melody note with deterministic occurrence/source lineage and post-fit anchor evidence. */
@Serializable
data class FullMelodyNoteLineage(
    val id: String,
    val occurrenceId: String,
    val sourcePartId: String,
    val sourceNoteId: String,
    val startTick: Long,
    val endTick: Long,
    val pitch: Int,
    val velocity: Int,
    val protectedAnchor: Boolean
) {
    /** Validate canonical note range, timing, and one stable source/occurrence lineage. */
    fun requireValid() {
        require(LINEAGE_ID.matches(id) && IDENTIFIER.matches(occurrenceId) && sourcePartId.isNotBlank() && sourceNoteId.isNotBlank() &&
            startTick >= 0 && endTick > startTick && pitch in 0..127 && velocity in 1..127) { "Full-melody note lineage is invalid" }
    }
}

/** A bounded global grid point carrying one occurrence's accepted local groove deviation. */
@Serializable
data class FullSongGroovePoint(
    val occurrenceId: String,
    val localBeatIndex: Long,
    val subdivision: Int,
    val globalTick: Long,
    val deviationTicks: Long
) {
    /** Keep each point on a non-negative global grid with a bounded subdivision index. */
    fun requireValid(subdivisionsPerBeat: Int) {
        require(IDENTIFIER.matches(occurrenceId) && localBeatIndex >= 0 && subdivision in 0 until subdivisionsPerBeat && globalTick >= 0) {
            "Full-song groove point is invalid"
        }
    }
}

/** The reviewable result for one local-feel discontinuity at an occurrence boundary. */
@Serializable
data class FullSongGrooveBoundary(
    val boundaryId: String,
    val tick: Long,
    val outgoingOccurrenceId: String,
    val incomingOccurrenceId: String,
    val outgoingDeviationTicks: Long,
    val incomingDeviationTicks: Long,
    val status: FullSongGrooveBoundaryStatus
) {
    /** Require a stable boundary identity and either a bounded seam or an explicit review requirement. */
    fun requireValid(maximumUnreviewedDiscontinuityTicks: Long) {
        require(IDENTIFIER.matches(boundaryId) && tick > 0 && IDENTIFIER.matches(outgoingOccurrenceId) && IDENTIFIER.matches(incomingOccurrenceId) &&
            ((status == FullSongGrooveBoundaryStatus.CONTINUOUS && abs(outgoingDeviationTicks - incomingDeviationTicks) <= maximumUnreviewedDiscontinuityTicks) ||
                (status == FullSongGrooveBoundaryStatus.REVIEW_REQUIRED && abs(outgoingDeviationTicks - incomingDeviationTicks) > maximumUnreviewedDiscontinuityTicks))) {
            "Full-song groove boundary is invalid"
        }
    }
}

/** Whether a boundary continues within policy or remains visible as a review-required feel discontinuity. */
@Serializable
enum class FullSongGrooveBoundaryStatus { CONTINUOUS, REVIEW_REQUIRED }

/** One occurrence-indexed projection of accepted source feel on the authoritative global beat/subdivision grid. */
@Serializable
data class FullSongGrooveMap(
    val version: Int = CURRENT_VERSION,
    val ppq: Int,
    val meterDenominator: Int,
    val subdivisionsPerBeat: Int,
    val points: List<FullSongGroovePoint>,
    val occurrenceTemplateFingerprints: List<FullSongGrooveOccurrenceTemplate>,
    val boundaries: List<FullSongGrooveBoundary>,
    val maximumUnreviewedDiscontinuityTicks: Long
) {
    /** Validate occurrence coverage, repeatable local feel, and no hidden discontinuity at a section boundary. */
    fun requireValid(occurrences: List<FullMelodyOccurrenceWindow>) {
        require(version == CURRENT_VERSION && ppq > 0 && meterDenominator in setOf(1, 2, 4, 8, 16, 32) && subdivisionsPerBeat == SourceGrooveTemplate.SUBDIVISIONS_PER_BEAT &&
            maximumUnreviewedDiscontinuityTicks > 0 && occurrenceTemplateFingerprints.map(FullSongGrooveOccurrenceTemplate::occurrenceId).toSet() == occurrences.map(FullMelodyOccurrenceWindow::occurrenceId).toSet()) {
            "Full-song groove map is invalid"
        }
        points.forEach { it.requireValid(subdivisionsPerBeat) }
        require(points == points.sortedWith(compareBy<FullSongGroovePoint> { it.globalTick }.thenBy { it.occurrenceId }.thenBy { it.subdivision }) &&
            boundaries == boundaries.sortedBy(FullSongGrooveBoundary::tick)) { "Full-song groove map ordering is invalid" }
        boundaries.forEach { it.requireValid(maximumUnreviewedDiscontinuityTicks) }
        val templates = occurrenceTemplateFingerprints.associateBy(FullSongGrooveOccurrenceTemplate::occurrenceId)
        require(occurrences.all { occurrence -> points.any { it.occurrenceId == occurrence.occurrenceId } }) {
            "Full-song groove map omits an occurrence"
        }
        occurrences.groupBy(FullMelodyOccurrenceWindow::sourcePartId).values.forEach { repeated ->
            val fingerprints = repeated.map { templates.getValue(it.occurrenceId).fingerprint }.distinct()
            require(fingerprints.size == 1) { "Repeated source use must retain identical local groove evidence" }
        }
    }

    companion object { const val CURRENT_VERSION = 1 }
}

/** Resolve approved source-feel points without turning the canonical grid into a timing warp. */
object FullSongGrooveMapTiming {
    /** Return the exact approved expressive tick for one global grid point, or null when the span has no evidence. */
    fun expectedTick(map: FullSongGrooveMap, globalGridTick: Long): Long? =
        map.points.singleOrNull { it.globalTick == globalGridTick }?.let { point -> point.globalTick + point.deviationTicks }

    /** Return the closest approved expressive tick inside one occurrence's active groove-map span. */
    fun nearestExpectedTick(map: FullSongGrooveMap, occurrenceId: String, actualTick: Long): Long? =
        map.points.asSequence().filter { it.occurrenceId == occurrenceId }
            .map { point -> point.globalTick + point.deviationTicks }
            .minWithOrNull(compareBy<Long> { kotlin.math.abs(it - actualTick) }.thenBy { it })
}

/** Stable per-occurrence source-groove fingerprint retained to prove repeated local feel. */
@Serializable
data class FullSongGrooveOccurrenceTemplate(val occurrenceId: String, val sourcePartId: String, val fingerprint: String) {
    init { require(IDENTIFIER.matches(occurrenceId) && sourcePartId.isNotBlank() && HASH.matches(fingerprint)) { "Full-song groove template fingerprint is invalid" } }
}

/** Canonical controller policy for the one-track full melody; no implicit sustain can cross a boundary. */
@Serializable
enum class FullMelodyControllerPolicy { CONTROLLER_FREE_CANONICAL_OUTPUT }

/** Versioned full-melody sidecar stored with the source-song artifact. */
@Serializable
data class SourceSongFullMelody(
    val version: Int = CURRENT_VERSION,
    val processorVersion: String = PROCESSOR_VERSION,
    val melodyTrackName: String,
    val occurrences: List<FullMelodyOccurrenceWindow>,
    val noteLineage: List<FullMelodyNoteLineage>,
    val maximumPolyphony: Int,
    val controllerPolicy: FullMelodyControllerPolicy,
    val grooveMap: FullSongGrooveMap
) {
    /** Validate the complete canonical melody sidecar before it is written beside source-song MIDI. */
    fun requireValid() {
        require(version == CURRENT_VERSION && processorVersion == PROCESSOR_VERSION && melodyTrackName == "full-melody" &&
            occurrences.isNotEmpty() && occurrences.map(FullMelodyOccurrenceWindow::occurrenceId).distinct().size == occurrences.size &&
            noteLineage.isNotEmpty() && noteLineage.map(FullMelodyNoteLineage::id).distinct().size == noteLineage.size && maximumPolyphony == 1 &&
            noteLineage == noteLineage.sortedWith(compareBy<FullMelodyNoteLineage> { it.startTick }.thenBy { it.endTick }.thenBy { it.id }) &&
            noteLineage.zipWithNext().all { (left, right) -> left.endTick <= right.startTick }) { "Canonical full-melody sidecar is invalid" }
        occurrences.forEach(FullMelodyOccurrenceWindow::requireValid)
        noteLineage.forEach(FullMelodyNoteLineage::requireValid)
        require(noteLineage.all { note -> occurrences.any { window -> note.occurrenceId == window.occurrenceId && note.startTick >= window.startTick && note.endTick <= window.endTick && note.sourcePartId == window.sourcePartId } }) {
            "Full-melody note lineage falls outside its occurrence"
        }
        grooveMap.requireValid(occurrences)
    }

    /** Resolve post-fit protected anchors onto the assembled MIDI identities without reconstructing source identities. */
    fun protectedAnchorIdentityIds(identity: MelodyIdentity): Set<MelodyNoteId> {
        require(identity.notes.size == noteLineage.size) { "Full-melody identity note count does not match its lineage" }
        val lineages = noteLineage.associateBy { note ->
            FullMelodyIdentityKey(note.occurrenceId, note.startTick, note.endTick, note.pitch, note.velocity)
        }
        require(lineages.size == noteLineage.size) { "Full-melody note lineage has ambiguous assembled identities" }
        return identity.notes.mapNotNull { note ->
            val lineage = requireNotNull(lineages[FullMelodyIdentityKey(requireNotNull(note.occurrenceId), note.originalStartTick, note.originalEndTick, note.pitch, note.velocity)]) {
                "Full-melody identity does not match its persisted lineage"
            }
            note.id.takeIf { lineage.protectedAnchor }
        }.toSet()
    }

    companion object {
        const val CURRENT_VERSION = 1
        const val PROCESSOR_VERSION = "1"
    }
}

/** Derives one hash-bound global groove map without changing the authoritative timing grid. */
object FullSongGrooveMapBuilder {
    /** Map each accepted local template onto its occurrence's global beat/subdivision coordinates. */
    fun build(ppq: Int, meterDenominator: Int, occurrences: List<FullMelodyOccurrenceWindow>): FullSongGrooveMap {
        val beat = ppq.toLong() * 4L / meterDenominator
        val subdivisionTicks = beat / SourceGrooveTemplate.SUBDIVISIONS_PER_BEAT
        require(beat > 0 && subdivisionTicks > 0) { "Full-song groove map cannot represent the canonical grid" }
        val points = occurrences.flatMap { occurrence -> points(occurrence, beat, subdivisionTicks) }
            .sortedWith(compareBy<FullSongGroovePoint> { it.globalTick }.thenBy { it.occurrenceId }.thenBy { it.subdivision })
        val templates = occurrences.map { occurrence ->
            FullSongGrooveOccurrenceTemplate(occurrence.occurrenceId, occurrence.sourcePartId, grooveFingerprint(occurrence.groove))
        }.sortedBy(FullSongGrooveOccurrenceTemplate::occurrenceId)
        val boundaryLimit = (beat / 16L).coerceAtLeast(1L)
        val boundaries = occurrences.zipWithNext().mapIndexed { index, (outgoing, incoming) ->
            val outgoingDeviation = points.filter { it.occurrenceId == outgoing.occurrenceId }.maxByOrNull(FullSongGroovePoint::globalTick)?.deviationTicks ?: 0L
            val incomingDeviation = points.firstOrNull { it.occurrenceId == incoming.occurrenceId }?.deviationTicks ?: 0L
            FullSongGrooveBoundary("groove-boundary-${index.toString().padStart(5, '0')}", incoming.startTick, outgoing.occurrenceId, incoming.occurrenceId,
                outgoingDeviation, incomingDeviation, if (abs(outgoingDeviation - incomingDeviation) <= boundaryLimit) FullSongGrooveBoundaryStatus.CONTINUOUS else FullSongGrooveBoundaryStatus.REVIEW_REQUIRED)
        }
        return FullSongGrooveMap(ppq = ppq, meterDenominator = meterDenominator, subdivisionsPerBeat = SourceGrooveTemplate.SUBDIVISIONS_PER_BEAT,
            points = points, occurrenceTemplateFingerprints = templates, boundaries = boundaries, maximumUnreviewedDiscontinuityTicks = boundaryLimit)
            .also { it.requireValid(occurrences) }
    }

    /** Build a fixed local feel sequence for one occurrence without modifying source timing or declaring a warp. */
    private fun points(occurrence: FullMelodyOccurrenceWindow, beat: Long, subdivisionTicks: Long): List<FullSongGroovePoint> {
        val bins = occurrence.groove.template?.bins?.associateBy(SourceGrooveBin::subdivision).orEmpty()
        return generateSequence(0L) { it + 1L }.takeWhile { index -> occurrence.startTick + index * beat < occurrence.endTick }.flatMap { beatIndex ->
            (0 until SourceGrooveTemplate.SUBDIVISIONS_PER_BEAT).asSequence().mapNotNull { subdivision ->
                val tick = occurrence.startTick + beatIndex * beat + subdivision * subdivisionTicks
                if (tick >= occurrence.endTick) null else {
                    val deviation = bins[subdivision]?.takeIf { occurrence.groove.status == SourceSongGrooveStatus.MEASURED }?.deviationFractionOfBeat
                        ?.times(beat)?.roundToLong() ?: 0L
                    FullSongGroovePoint(occurrence.occurrenceId, beatIndex, subdivision, tick, deviation)
                }
            }
        }.toList()
    }

    /** Fingerprint exactly the accepted template evidence or the explicit grid fallback. */
    private fun grooveFingerprint(evidence: SourceSongGrooveEvidence): String = sha256Hex(
        buildString {
            append(evidence.status.name).append('|').append(evidence.sourceTimingReport?.sha256.orEmpty()).append('|')
            evidence.template?.let { template ->
                append(template.sourceSha256).append('|').append(template.confidence).append('|').append(template.status.name)
                template.bins.forEach { bin -> append('|').append(bin.subdivision).append(':').append(bin.deviationFractionOfBeat).append(':').append(bin.confidence).append(':').append(bin.supportingOnsets) }
            }
        }
    )
}

private val HASH = Regex("[0-9a-f]{64}")
private val IDENTIFIER = Regex("[A-Za-z0-9_-]{1,80}")
private val LINEAGE_ID = Regex("fm-[A-Za-z0-9_-]{1,160}")
private data class FullMelodyIdentityKey(val occurrenceId: String, val startTick: Long, val endTick: Long, val pitch: Int, val velocity: Int)
