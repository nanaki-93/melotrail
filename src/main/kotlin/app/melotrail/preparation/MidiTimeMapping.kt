package app.melotrail.preparation

import app.melotrail.arrangement.ArtifactRef
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import javax.sound.midi.MetaMessage
import javax.sound.midi.MidiEvent
import javax.sound.midi.MidiMessage
import javax.sound.midi.MidiSystem
import javax.sound.midi.Sequence
import javax.sound.midi.ShortMessage
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** A source beat from QP-002 paired with its corresponding unmodified MIDI tick. */
@Serializable
data class SourceBeatTickAnchor(val sourceBeatIndex: Int, val sourceMidiTick: Long) {
    /** Rejects source anchors that cannot safely participate in a monotonic mapping. */
    fun requireValid() {
        require(sourceBeatIndex >= 0 && sourceMidiTick >= 0) { "Source beat anchor is invalid" }
    }
}

/** An explicit source pickup or tail whose duration is kept outside ordinary whole bars. */
@Serializable
data class ExplicitTimingWindow(
    val sourceStartTick: Long,
    val sourceEndTick: Long,
    val targetDurationTicks: Long
) {
    /** Ensures a typed non-empty window can never silently move a body boundary. */
    fun requireValid() {
        require(sourceStartTick >= 0 && sourceEndTick > sourceStartTick && targetDurationTicks > 0) {
            "Explicit timing window is invalid"
        }
    }
}

/** Bounded, versioned policy for structural mapping and retained within-subdivision feel. */
@Serializable
data class MidiTimeMappingPolicy(
    val version: Int = CURRENT_VERSION,
    val minimumBeatConfidence: Double = 0.50,
    val maximumDurationChangeFraction: Double = 0.25,
    val expressiveSubdivisionsPerBeat: Int = 4,
    val maximumExpressiveOffsetFraction: Double = 0.50
) {
    /** Validates the fixed safety bounds instead of allowing per-call timing weakening. */
    fun requireValid() {
        require(version == CURRENT_VERSION) { "Unsupported MIDI time-mapping policy version: $version" }
        require(minimumBeatConfidence.isFinite() && minimumBeatConfidence in 0.0..1.0) { "Minimum beat confidence is invalid" }
        require(maximumDurationChangeFraction.isFinite() && maximumDurationChangeFraction in 0.0..0.50) { "Maximum duration change is invalid" }
        require(expressiveSubdivisionsPerBeat in 1..16) { "Expressive subdivisions are invalid" }
        require(maximumExpressiveOffsetFraction.isFinite() && maximumExpressiveOffsetFraction in 0.0..0.50) {
            "Maximum expressive offset is invalid"
        }
    }

    companion object { const val CURRENT_VERSION = 1 }
}

/** A typed human decision is required before a review-required mapping can publish a candidate. */
@Serializable
enum class MidiTimeMappingReviewState { PENDING, APPROVED }

/** Minimal durable human-review evidence without persisting UI state or free-form worker output. */
@Serializable
data class MidiTimeMappingReview(
    val state: MidiTimeMappingReviewState = MidiTimeMappingReviewState.PENDING,
    val reviewer: String? = null,
    val reviewedAt: String? = null
) {
    /** Rejects incomplete approval assertions while leaving a pending decision representable. */
    fun requireValid() {
        when (state) {
            MidiTimeMappingReviewState.PENDING -> require(reviewer == null && reviewedAt == null) { "Pending timing review cannot contain approval evidence" }
            MidiTimeMappingReviewState.APPROVED -> {
                require(reviewer != null && REVIEWER.matches(reviewer) && reviewedAt != null) { "Approved timing review is incomplete" }
                runCatching { Instant.parse(reviewedAt) }.getOrElse { throw IllegalArgumentException("Timing review timestamp is invalid", it) }
            }
        }
    }

    private companion object { val REVIEWER = Regex("[A-Za-z0-9 _.-]{1,120}") }
}

/** Reasons that force an explicit human timing decision before derived MIDI may be published. */
@Serializable
enum class MidiTimeMappingReviewReason {
    DOWNBEAT_UNKNOWN,
    DOWNBEAT_REVIEW_REQUIRED,
    LOW_BEAT_CONFIDENCE,
    LARGE_DURATION_CHANGE,
    AMBIGUOUS_TARGET_BAR_COUNT
}

/** Immutable mapping authority from source timing evidence into one local MIDI candidate and global occurrence bounds. */
@Serializable
data class SourceTimingDecision(
    val version: Int = CURRENT_VERSION,
    val partId: String,
    val occurrenceId: String,
    val sourceTimingReport: ArtifactRef,
    val sourceMidi: ArtifactRef,
    val sourcePpq: Int,
    val targetPpq: Int,
    val targetTempoBpm: Int,
    val targetMeterNumerator: Int,
    val targetMeterDenominator: Int,
    val sourceDownbeatBeatIndex: Int,
    val sourceBeats: List<SourceBeatTickAnchor>,
    val targetStartBar: Long,
    val targetBarCount: Int,
    val targetBarCountAmbiguous: Boolean = false,
    val pickup: ExplicitTimingWindow? = null,
    val tail: ExplicitTimingWindow? = null,
    /** False means an explicit grid fallback; the source-groove template remains separate evidence. */
    val acceptSourceGroove: Boolean = false,
    val review: MidiTimeMappingReview = MidiTimeMappingReview(),
    val policy: MidiTimeMappingPolicy = MidiTimeMappingPolicy()
) {
    /** Validates standalone, hash-bound decision facts before evidence-specific checks. */
    fun requireValid() {
        require(version == CURRENT_VERSION) { "Unsupported source timing decision version: $version" }
        SourceTimingPaths.requirePartId(partId)
        require(SAFE_OCCURRENCE_ID.matches(occurrenceId)) { "Timing occurrence ID is invalid" }
        require(sourcePpq in 24..9_600 && targetPpq in 24..9_600) { "Timing PPQ is invalid" }
        require(targetTempoBpm in 30..240 && targetMeterNumerator in 1..12 && targetMeterDenominator in setOf(1, 2, 4, 8, 16)) {
            "Target tempo or meter is invalid"
        }
        require(targetStartBar >= 0 && targetBarCount in 1..128) { "Target bar placement is invalid" }
        require(sourceBeats.size == bodyBeatCount() + 1) { "Source timing decision requires one anchor for every body boundary" }
        require(sourceBeats.map(SourceBeatTickAnchor::sourceBeatIndex) == (sourceDownbeatBeatIndex..sourceDownbeatBeatIndex + bodyBeatCount()).toList()) {
            "Source beat indexes must cover one contiguous body"
        }
        sourceBeats.forEach(SourceBeatTickAnchor::requireValid)
        require(sourceBeats.zipWithNext().all { (earlier, later) -> earlier.sourceMidiTick < later.sourceMidiTick }) {
            "Source MIDI beat ticks must be strictly increasing"
        }
        pickup?.requireValid(); tail?.requireValid(); review.requireValid(); policy.requireValid()
        pickup?.let {
            require(targetStartBar > 0 && it.sourceEndTick <= sourceBeats.first().sourceMidiTick && it.targetDurationTicks <= targetStartBar * barTicks()) {
                "Pickup cannot move the first canonical body bar"
            }
        }
        tail?.let {
            require(it.sourceStartTick >= sourceBeats.last().sourceMidiTick) { "Tail cannot overlap the canonical body" }
        }
    }

    /** Verifies measured anchors when source groove is accepted; an explicit grid fallback is intentionally independent of sparse onset evidence. */
    fun requireMatches(evidence: SourceTimingEvidence) {
        requireValid(); evidence.requireValid()
        require(evidence.partId == partId) { "Source timing evidence does not match this mapping decision" }
        if (acceptSourceGroove) {
            require(sourceBeats.all { anchor -> anchor.sourceBeatIndex in evidence.beats.indices }) { "Source beat anchor is absent from timing evidence" }
            require(evidence.groove.status == SourceGrooveTemplateStatus.MEASURED) {
                "A review-required source groove must explicitly fall back to the grid"
            }
        }
    }

    /** Returns all policy-derived review reasons without allowing a caller to suppress them. */
    fun reviewReasons(evidence: SourceTimingEvidence): List<MidiTimeMappingReviewReason> {
        requireMatches(evidence)
        val reasons = linkedSetOf<MidiTimeMappingReviewReason>()
        when (evidence.downbeat.status) {
            DownbeatEvidenceStatus.UNKNOWN -> reasons += MidiTimeMappingReviewReason.DOWNBEAT_UNKNOWN
            DownbeatEvidenceStatus.REVIEW_REQUIRED -> {
                reasons += MidiTimeMappingReviewReason.DOWNBEAT_REVIEW_REQUIRED
                if (evidence.downbeat.candidateBeatIndex != sourceDownbeatBeatIndex) reasons += MidiTimeMappingReviewReason.DOWNBEAT_UNKNOWN
            }
        }
        if (sourceBeats.all { it.sourceBeatIndex in evidence.beats.indices }) {
            if (sourceBeats.any { evidence.beats[it.sourceBeatIndex].confidence.orEmptyConfidence() < policy.minimumBeatConfidence }) {
                reasons += MidiTimeMappingReviewReason.LOW_BEAT_CONFIDENCE
            }
            val sourceDuration = evidence.beats[sourceBeats.last().sourceBeatIndex].timeSeconds - evidence.beats[sourceBeats.first().sourceBeatIndex].timeSeconds
            val targetDuration = bodyBeatCount() * 60.0 / targetTempoBpm
            if (sourceDuration <= 0.0 || abs(sourceDuration / targetDuration - 1.0) > policy.maximumDurationChangeFraction) {
                reasons += MidiTimeMappingReviewReason.LARGE_DURATION_CHANGE
            }
        }
        if (targetBarCountAmbiguous) reasons += MidiTimeMappingReviewReason.AMBIGUOUS_TARGET_BAR_COUNT
        return reasons.toList()
    }

    /** Refuses to publish a candidate until every required human review is explicitly approved. */
    fun requireReadyForMapping(evidence: SourceTimingEvidence) {
        val reasons = reviewReasons(evidence)
        require(reasons.isEmpty() || review.state == MidiTimeMappingReviewState.APPROVED) {
            "Timing mapping requires review: ${reasons.joinToString(",")}"
        }
    }

    /** Produces source-indexed local and global target beat evidence without changing the local candidate timing. */
    fun targetBeats(): List<MidiTimeTargetBeat> {
        requireValid()
        val localBodyStart = localBodyStartTick()
        val globalOffset = targetStartBar * barTicks() - localBodyStart
        return sourceBeats.mapIndexed { offset, anchor ->
            val local = localBodyStart + offset * targetPpq.toLong()
            MidiTimeTargetBeat(anchor.sourceBeatIndex, local, local + globalOffset, targetStartBar + offset / targetMeterNumerator, offset % targetMeterNumerator)
        }
    }

    /** Builds explicit pickup/body/tail evidence; only the body represents ordinary whole canonical bars. */
    fun windows(): List<MidiTimeMappingWindow> {
        requireValid()
        val bodyLocalStart = localBodyStartTick()
        val bodyLocalEnd = bodyLocalStart + bodyBeatCount() * targetPpq.toLong()
        val globalOffset = targetStartBar * barTicks() - bodyLocalStart
        return buildList {
            pickup?.let { add(MidiTimeMappingWindow(TimingWindowKind.PICKUP, it.sourceStartTick, it.sourceEndTick, 0, bodyLocalStart, globalOffset, bodyLocalStart + globalOffset)) }
            add(MidiTimeMappingWindow(TimingWindowKind.BODY, sourceBeats.first().sourceMidiTick, sourceBeats.last().sourceMidiTick,
                bodyLocalStart, bodyLocalEnd, bodyLocalStart + globalOffset, bodyLocalEnd + globalOffset))
            tail?.let { add(MidiTimeMappingWindow(TimingWindowKind.TAIL, it.sourceStartTick, it.sourceEndTick, bodyLocalEnd,
                bodyLocalEnd + it.targetDurationTicks, bodyLocalEnd + globalOffset, bodyLocalEnd + it.targetDurationTicks + globalOffset)) }
        }
    }

    /** Returns the whole-body beat count implied by the declared target meter and bar count. */
    fun bodyBeatCount(): Int = Math.multiplyExact(targetBarCount, targetMeterNumerator)

    /** Returns the fixed whole-bar size on the canonical target grid. */
    fun barTicks(): Long = Math.multiplyExact(targetPpq.toLong(), targetMeterNumerator.toLong())

    /** Returns the candidate-local start tick of the ordinary body after an explicit pickup. */
    fun localBodyStartTick(): Long = pickup?.targetDurationTicks ?: 0L

    /** Hashes the complete decision authority used to derive a candidate and report. */
    fun sha256(): String = sha256Bytes(JSON_COMPACT.encodeToString(this).toByteArray(StandardCharsets.UTF_8))

    companion object {
        const val CURRENT_VERSION = 1
        private val SAFE_OCCURRENCE_ID = Regex("[A-Za-z0-9_-]{1,96}")
        private val JSON_COMPACT = Json { encodeDefaults = true }
    }
}

/** The typed mapping window category prevents a pickup or tail from masquerading as an ordinary occurrence. */
@Serializable
enum class TimingWindowKind { PICKUP, BODY, TAIL }

/** A source/local/song coordinate triple for one typed mapping window. */
@Serializable
data class MidiTimeMappingWindow(
    val kind: TimingWindowKind,
    val sourceStartTick: Long,
    val sourceEndTick: Long,
    val localStartTick: Long,
    val localEndTick: Long,
    val songStartTick: Long,
    val songEndTick: Long
) {
    /** Verifies each reported window has positive source, local, and song ranges. */
    fun requireValid() {
        require(sourceStartTick >= 0 && sourceEndTick > sourceStartTick && localStartTick >= 0 && localEndTick > localStartTick &&
            songStartTick >= 0 && songEndTick > songStartTick) {
            "MIDI time-mapping window is invalid"
        }
    }
}

/** A source beat's exact local candidate tick and its distinct global song position. */
@Serializable
data class MidiTimeTargetBeat(
    val sourceBeatIndex: Int,
    val localTick: Long,
    val songTick: Long,
    val targetBar: Long,
    val beatInBar: Int
) {
    /** Rejects target-beat records that could hide phase drift or invalid bar coordinates. */
    fun requireValid(meterNumerator: Int) {
        require(sourceBeatIndex >= 0 && localTick >= 0 && songTick >= 0 && targetBar >= 0 && beatInBar in 0 until meterNumerator) {
            "Target beat evidence is invalid"
        }
    }
}

/** Hash, PPQ, and event evidence for a preserved input or independently published mapping candidate. */
@Serializable
data class MidiTimeMappingArtifact(val sha256: String, val ppq: Int, val eventCount: Int, val noteCount: Int) {
    /** Validates the compact artifact facts used by a mapping report. */
    fun requireValid(label: String) {
        require(SHA_256.matches(sha256) && ppq in 24..9_600 && eventCount >= 0 && noteCount >= 0) { "MIDI time-mapping $label artifact is invalid" }
    }
}

/** Deterministic residual and preservation accounting for one piecewise MIDI timing map. */
@Serializable
data class MidiTimeMappingResiduals(
    val anchorCount: Int,
    val maximumAnchorResidualTicks: Long,
    val accumulatedAnchorPhaseTicks: Long,
    val mappedEvents: Int,
    val positiveDurationFixes: Int,
    val expressiveEvents: Int,
    val maximumExpressiveOffsetTicks: Long,
    val clippedExpressiveEvents: Int
) {
    /** Ensures zero-phase evidence and change accounting cannot contain negative counters. */
    fun requireValid() {
        require(anchorCount >= 2 && maximumAnchorResidualTicks == 0L && accumulatedAnchorPhaseTicks == 0L && mappedEvents >= 0 &&
            positiveDurationFixes >= 0 && expressiveEvents >= 0 && maximumExpressiveOffsetTicks >= 0 && clippedExpressiveEvents >= 0) {
            "MIDI time-mapping residual evidence is invalid"
        }
    }
}

/** Immutable output report for a reviewed piecewise source-to-project timing candidate. */
@Serializable
data class MidiTimeMappingReport(
    val version: Int = CURRENT_VERSION,
    val processorVersion: String = PROCESSOR_VERSION,
    val partId: String,
    val occurrenceId: String,
    val sourceTimingReport: ArtifactRef,
    val sourceMidi: ArtifactRef,
    val sourceSha256: String,
    val decisionSha256: String,
    val sourceDownbeatBeatIndex: Int,
    val sourceBeats: List<SourceBeatTickAnchor>,
    val input: MidiTimeMappingArtifact,
    val output: MidiTimeMappingArtifact,
    val targetTempoBpm: Int,
    val targetMeterNumerator: Int,
    val targetMeterDenominator: Int,
    val targetStartBar: Long,
    val targetBarCount: Int,
    val mappingConfidence: Double,
    val policy: MidiTimeMappingPolicy,
    val review: MidiTimeMappingReview,
    val reviewReasons: List<MidiTimeMappingReviewReason>,
    val targetBeats: List<MidiTimeTargetBeat>,
    val windows: List<MidiTimeMappingWindow>,
    val acceptedSourceGroove: Boolean,
    val residuals: MidiTimeMappingResiduals
) {
    /** Validates hash lineage, explicit windows, review state, and zero uncontrolled anchor phase. */
    fun requireValid() {
        require(version == CURRENT_VERSION && processorVersion == PROCESSOR_VERSION) { "MIDI time-mapping report version is invalid" }
        SourceTimingPaths.requirePartId(partId)
        require(occurrenceId.isNotBlank() && SHA_256.matches(sourceSha256) && SHA_256.matches(decisionSha256)) { "MIDI time-mapping report lineage is invalid" }
        require(sourceDownbeatBeatIndex >= 0 && sourceBeats.size >= 2 && targetStartBar >= 0 && targetBarCount > 0) {
            "MIDI time-mapping report beat/bar evidence is invalid"
        }
        require(mappingConfidence.isFinite() && mappingConfidence in 0.0..1.0) { "MIDI time-mapping confidence is invalid" }
        sourceBeats.forEach(SourceBeatTickAnchor::requireValid)
        require(sourceBeats.first().sourceBeatIndex == sourceDownbeatBeatIndex &&
            sourceBeats.zipWithNext().all { (earlier, later) -> earlier.sourceBeatIndex + 1 == later.sourceBeatIndex && earlier.sourceMidiTick < later.sourceMidiTick }) {
            "MIDI time-mapping report source beats are invalid"
        }
        input.requireValid("input"); output.requireValid("output"); policy.requireValid(); review.requireValid(); residuals.requireValid()
        require(targetTempoBpm in 30..240 && targetMeterNumerator in 1..12 && targetMeterDenominator in setOf(1, 2, 4, 8, 16)) {
            "MIDI time-mapping target tempo or meter is invalid"
        }
        require(targetBeats.size >= 2 && targetBeats.zipWithNext().all { (earlier, later) -> earlier.localTick < later.localTick && earlier.songTick < later.songTick }) {
            "MIDI time-mapping target beats are invalid"
        }
        require(targetBeats.map(MidiTimeTargetBeat::sourceBeatIndex) == sourceBeats.map(SourceBeatTickAnchor::sourceBeatIndex)) {
            "MIDI time-mapping source and target beat evidence is inconsistent"
        }
        targetBeats.forEach { it.requireValid(targetMeterNumerator) }
        require(windows.map(MidiTimeMappingWindow::kind).distinct().size == windows.size && windows.any { it.kind == TimingWindowKind.BODY }) {
            "MIDI time-mapping windows are invalid"
        }
        windows.forEach(MidiTimeMappingWindow::requireValid)
        require(reviewReasons.distinct().size == reviewReasons.size) { "MIDI time-mapping review reasons are invalid" }
        require(reviewReasons.isEmpty() || review.state == MidiTimeMappingReviewState.APPROVED) { "MIDI time-mapping report lacks required approval" }
    }

    companion object {
        const val CURRENT_VERSION = 1
        const val PROCESSOR_VERSION = "1"
    }
}

/** Project-model pointer to an immutable mapped candidate and the report that binds it to exact inputs. */
@Serializable
data class MidiTimeMappingReference(
    val candidate: ArtifactRef,
    val report: ArtifactRef,
    val sourceTimingReport: ArtifactRef,
    val sourceMidi: ArtifactRef
) {
    /** Verifies only canonical artifact references are persisted in a project. */
    fun requireValid() {
        require(candidate.path.startsWith("midi/timing/") && report.path.startsWith("analysis/timing-mapping/") &&
            sourceTimingReport.path.startsWith("analysis/timing/") && sourceMidi.path.startsWith("midi/")) {
            "MIDI time-mapping reference paths are not canonical"
        }
    }
}

/** Canonical, immutable locations for QP-003 MIDI candidates and their reports. */
object MidiTimeMappingPaths {
    /** Resolves the directory for one part's content-addressed timing candidates. */
    fun candidateDirectory(projectRoot: Path, partId: String): Path = projectPath(projectRoot, "midi/timing/$partId")

    /** Resolves the content-addressed MIDI candidate path for one byte hash. */
    fun candidate(projectRoot: Path, partId: String, contentSha256: String): Path {
        SourceTimingPaths.requirePartId(partId); require(SHA_256.matches(contentSha256)) { "Candidate MIDI hash is invalid" }
        return candidateDirectory(projectRoot, partId).resolve("$contentSha256.mid")
    }

    /** Resolves the content-addressed JSON report path for one report byte hash. */
    fun report(projectRoot: Path, partId: String, contentSha256: String): Path {
        SourceTimingPaths.requirePartId(partId); require(SHA_256.matches(contentSha256)) { "Timing report hash is invalid" }
        return projectPath(projectRoot, "analysis/timing-mapping/$partId/$contentSha256.json")
    }

    /** Constrains a generated mapping path below its project root. */
    private fun projectPath(projectRoot: Path, relative: String): Path {
        val root = projectRoot.toAbsolutePath().normalize()
        val path = root.resolve(relative).normalize()
        require(path.startsWith(root)) { "MIDI time mapping path escapes its project" }
        return path
    }
}

/** Deterministic piecewise mapper that writes a new MIDI candidate while retaining the original input bytes. */
class MidiTimeMapper {
    /** Maps notes, controllers, and retained meta events to the project grid, then records immutable report evidence. */
    fun map(input: Path, output: Path, decision: SourceTimingDecision, evidence: SourceTimingEvidence): MidiTimeMappingReport {
        decision.requireMatches(evidence); decision.requireReadyForMapping(evidence)
        val inputPath = input.toAbsolutePath().normalize()
        val outputPath = output.toAbsolutePath().normalize()
        require(Files.isRegularFile(inputPath) && inputPath != outputPath) { "MIDI time-mapping input/output is invalid" }
        require(sha256File(inputPath) == decision.sourceMidi.sha256) { "MIDI time-mapping input does not match its decision" }
        val before = readMidi(inputPath)
        require(before.resolution == decision.sourcePpq) { "MIDI time-mapping source PPQ does not match its decision" }
        val inputEvents = events(before)
        val map = PiecewiseTimingMap(decision)
        val mapped = inputEvents.filterNot(::isReplacedTempoOrMeter).map { event ->
            MutableMidiEvent(event.track, event.index, map.tick(event.tick), event.message.clone() as MidiMessage)
        }.toMutableList()
        val bounded = boundExpressiveOffsets(mapped, decision)
        val positiveDurationFixes = enforcePositiveDurations(mapped)
        mapped += MutableMidiEvent(0, -2, 0, tempoMessage(decision.targetTempoBpm))
        mapped += MutableMidiEvent(0, -1, 0, meterMessage(decision.targetMeterNumerator, decision.targetMeterDenominator))
        val sequence = Sequence(Sequence.PPQ, decision.targetPpq)
        mapped.groupBy(MutableMidiEvent::track).toSortedMap().forEach { (_, trackEvents) ->
            val track = sequence.createTrack()
            trackEvents.sortedWith(EVENT_ORDER).forEach { event -> track.add(MidiEvent(event.message, event.tick)) }
        }
        Files.createDirectories(requireNotNull(outputPath.parent))
        MidiSystem.write(sequence, 1, outputPath.toFile())
        require(sha256File(inputPath) == decision.sourceMidi.sha256) { "MIDI time mapping changed its source input" }
        val after = readMidi(outputPath)
        val outputEvents = events(after)
        require(noteIdentities(inputEvents) == noteIdentities(outputEvents)) { "MIDI time mapping changed note identity or cardinality" }
        val targetBeats = decision.targetBeats()
        val residuals = targetBeats.map { target -> abs(map.tick(decision.sourceBeats.first { it.sourceBeatIndex == target.sourceBeatIndex }.sourceMidiTick) - target.localTick) }
        val report = MidiTimeMappingReport(
            partId = decision.partId,
            occurrenceId = decision.occurrenceId,
            sourceTimingReport = decision.sourceTimingReport,
            sourceMidi = decision.sourceMidi,
            sourceSha256 = evidence.source.sha256,
            decisionSha256 = decision.sha256(),
            sourceDownbeatBeatIndex = decision.sourceDownbeatBeatIndex,
            sourceBeats = decision.sourceBeats,
            input = MidiTimeMappingArtifact(sha256File(inputPath), before.resolution, inputEvents.size, noteIdentities(inputEvents).size),
            output = MidiTimeMappingArtifact(sha256File(outputPath), after.resolution, outputEvents.size, noteIdentities(outputEvents).size),
            targetTempoBpm = decision.targetTempoBpm,
            targetMeterNumerator = decision.targetMeterNumerator,
            targetMeterDenominator = decision.targetMeterDenominator,
            targetStartBar = decision.targetStartBar,
            targetBarCount = decision.targetBarCount,
            mappingConfidence = if (decision.sourceBeats.all { it.sourceBeatIndex in evidence.beats.indices }) {
                decision.sourceBeats.minOf { evidence.beats[it.sourceBeatIndex].confidence.orEmptyConfidence() }
            } else {
                0.0
            },
            policy = decision.policy,
            review = decision.review,
            reviewReasons = decision.reviewReasons(evidence),
            targetBeats = targetBeats,
            windows = decision.windows(),
            acceptedSourceGroove = decision.acceptSourceGroove,
            residuals = MidiTimeMappingResiduals(
                anchorCount = targetBeats.size,
                maximumAnchorResidualTicks = residuals.maxOrNull() ?: 0,
                accumulatedAnchorPhaseTicks = residuals.sum(),
                mappedEvents = mapped.size,
                positiveDurationFixes = positiveDurationFixes,
                expressiveEvents = bounded.eventCount,
                maximumExpressiveOffsetTicks = bounded.maximumOffsetTicks,
                clippedExpressiveEvents = bounded.clippedEvents
            )
        )
        return report.also(MidiTimeMappingReport::requireValid)
    }

    /** Loads only positive-PPQ MIDI and turns malformed input into a safe local failure. */
    private fun readMidi(path: Path): Sequence = try {
        MidiSystem.getSequence(path.toFile()).also { require(it.divisionType == Sequence.PPQ && it.resolution > 0) { "MIDI time mapping requires positive PPQ MIDI" } }
    } catch (error: Exception) {
        throw IllegalArgumentException("MIDI time-mapping input is malformed", error)
    }

    /** Flattens every meaningful source event while deliberately ignoring synthetic end-of-track messages. */
    private fun events(sequence: Sequence): List<ImmutableMidiEvent> = sequence.tracks.flatMapIndexed { trackIndex, track ->
        (0 until track.size()).map { index -> ImmutableMidiEvent(trackIndex, index, track[index].tick, track[index].message) }
    }.filterNot { event -> (event.message as? MetaMessage)?.type == END_OF_TRACK }

    /** Removes only source tempo/meter metadata because the output receives the declared project values at tick zero. */
    private fun isReplacedTempoOrMeter(event: ImmutableMidiEvent): Boolean = (event.message as? MetaMessage)?.type in setOf(TEMPO, TIME_SIGNATURE)

    /** Bounds offsets from the nearest approved subdivision without reintroducing tempo drift into structural beat anchors. */
    private fun boundExpressiveOffsets(events: MutableList<MutableMidiEvent>, decision: SourceTimingDecision): ExpressiveBounds {
        val subdivision = decision.targetPpq.toLong() / decision.policy.expressiveSubdivisionsPerBeat
        require(subdivision > 0) { "Expressive subdivision is invalid for target PPQ" }
        val maximum = (subdivision * decision.policy.maximumExpressiveOffsetFraction).roundToLong()
        var count = 0; var max = 0L; var clipped = 0
        events.filter { it.message !is MetaMessage }.forEach { event ->
            val nearest = ((event.tick + subdivision / 2) / subdivision) * subdivision
            val offset = event.tick - nearest
            val bounded = offset.coerceIn(-maximum, maximum)
            if (bounded != offset) { event.tick = nearest + bounded; clipped++ }
            count++; max = maxOf(max, abs(bounded))
        }
        return ExpressiveBounds(count, max, clipped)
    }

    /** Repairs only mapped note-off ordering, preserving all note identities and never deleting a note. */
    private fun enforcePositiveDurations(events: List<MutableMidiEvent>): Int {
        val active = mutableMapOf<Triple<Int, Int, Int>, ArrayDeque<MutableMidiEvent>>()
        var fixes = 0
        events.sortedWith(compareBy<MutableMidiEvent> { it.track }.thenBy { it.tick }.thenBy { it.index }).forEach { event ->
            val message = event.message as? ShortMessage ?: return@forEach
            val key = Triple(event.track, message.channel, message.data1)
            when {
                message.isNoteOn() -> active.getOrPut(key) { ArrayDeque() }.addLast(event)
                message.isNoteOff() -> active[key]?.removeFirstOrNull()?.let { start ->
                    if (event.tick <= start.tick) { event.tick = start.tick + 1; fixes++ }
                }
            }
        }
        return fixes
    }

    /** Returns channel/pitch note identities so mapping cannot create, delete, or repitch musical material. */
    private fun noteIdentities(events: List<Any>): List<Pair<Int, Int>> = events.mapNotNull { event ->
        val message = when (event) {
            is ImmutableMidiEvent -> event.message
            is MutableMidiEvent -> event.message
            else -> null
        } as? ShortMessage
        if (message?.isNoteOn() == true) message.channel to message.data1 else null
    }.sortedWith(compareBy<Pair<Int, Int>> { it.first }.thenBy { it.second })

    /** Creates one canonical target tempo event rather than merely relabeling the source tempo map. */
    private fun tempoMessage(bpm: Int): MetaMessage = MetaMessage().also { message ->
        val micros = 60_000_000 / bpm
        message.setMessage(TEMPO, byteArrayOf((micros shr 16).toByte(), (micros shr 8).toByte(), micros.toByte()), 3)
    }

    /** Creates one canonical target time signature event at tick zero. */
    private fun meterMessage(numerator: Int, denominator: Int): MetaMessage = MetaMessage().also { message ->
        message.setMessage(TIME_SIGNATURE, byteArrayOf(numerator.toByte(), Integer.numberOfTrailingZeros(denominator).toByte(), 24, 8), 4)
    }

    private data class ImmutableMidiEvent(val track: Int, val index: Int, val tick: Long, val message: MidiMessage)
    private data class MutableMidiEvent(val track: Int, val index: Int, var tick: Long, val message: MidiMessage)
    private data class ExpressiveBounds(val eventCount: Int, val maximumOffsetTicks: Long, val clippedEvents: Int)

    /** Maps a source tick through an explicit pickup/body/tail segment without extrapolation. */
    private class PiecewiseTimingMap(decision: SourceTimingDecision) {
        private val segments: List<Segment> = buildList {
            decision.pickup?.let { add(Segment(it.sourceStartTick, it.sourceEndTick, 0, decision.localBodyStartTick())) }
            decision.sourceBeats.zipWithNext().forEachIndexed { index, (start, end) ->
                val targetStart = decision.localBodyStartTick() + index * decision.targetPpq.toLong()
                add(Segment(start.sourceMidiTick, end.sourceMidiTick, targetStart, targetStart + decision.targetPpq))
            }
            decision.tail?.let { add(Segment(it.sourceStartTick, it.sourceEndTick, decision.localBodyStartTick() + decision.bodyBeatCount() * decision.targetPpq.toLong(),
                decision.localBodyStartTick() + decision.bodyBeatCount() * decision.targetPpq.toLong() + it.targetDurationTicks)) }
        }

        /** Maps one source tick only within a declared typed window or source beat segment. */
        fun tick(sourceTick: Long): Long {
            val segment = segments.firstOrNull { sourceTick in it.sourceStart..it.sourceEnd }
                ?: throw IllegalArgumentException("MIDI event lies outside declared pickup/body/tail timing windows")
            if (sourceTick == segment.sourceStart) return segment.targetStart
            if (sourceTick == segment.sourceEnd) return segment.targetEnd
            val ratio = (sourceTick - segment.sourceStart).toDouble() / (segment.sourceEnd - segment.sourceStart).toDouble()
            return (segment.targetStart + ratio * (segment.targetEnd - segment.targetStart)).roundToLong()
        }

        private data class Segment(val sourceStart: Long, val sourceEnd: Long, val targetStart: Long, val targetEnd: Long)
    }

    private companion object {
        const val TEMPO = 0x51; const val TIME_SIGNATURE = 0x58; const val END_OF_TRACK = 0x2f
        val EVENT_ORDER = compareBy<MutableMidiEvent> { it.tick }.thenBy {
            val message = it.message as? ShortMessage
            when {
                message == null -> 1
                message.isNoteOff() -> 0
                message.isNoteOn() -> 3
                else -> 2
            }
        }.thenBy { it.index }
    }
}

/** Atomic immutable publication and verification for mapped candidates and their hash-bound reports. */
object MidiTimeMappingStore {
    private val json = Json { prettyPrint = true; encodeDefaults = true; ignoreUnknownKeys = false }

    /** Publishes a temporary candidate into its content-addressed project path without replacing another candidate. */
    fun publishCandidate(projectRoot: Path, partId: String, temporaryCandidate: Path): ArtifactRef {
        val temporary = temporaryCandidate.toAbsolutePath().normalize()
        require(Files.isRegularFile(temporary)) { "Timing candidate is missing" }
        val digest = sha256File(temporary)
        val target = MidiTimeMappingPaths.candidate(projectRoot, partId, digest)
        publishFile(projectRoot, temporary, target, digest)
        return artifactRef(projectRoot, target, digest)
    }

    /** Serializes and atomically publishes a report whose filename is its complete byte hash. */
    fun writeReport(projectRoot: Path, report: MidiTimeMappingReport): ArtifactRef {
        report.requireValid()
        val bytes = json.encodeToString(report).toByteArray(StandardCharsets.UTF_8)
        val digest = sha256Bytes(bytes)
        val target = MidiTimeMappingPaths.report(projectRoot, report.partId, digest)
        Files.createDirectories(requireNotNull(target.parent))
        val temporary = target.resolveSibling(".${target.fileName}.${UUID.randomUUID()}.tmp")
        try {
            Files.write(temporary, bytes, StandardOpenOption.CREATE_NEW)
            publishFile(projectRoot, temporary, target, digest)
        } finally {
            Files.deleteIfExists(temporary)
        }
        return artifactRef(projectRoot, target, digest)
    }

    /** Reads a canonical report only after its immutable byte hash and project confinement are verified. */
    fun readReport(projectRoot: Path, reference: ArtifactRef): MidiTimeMappingReport {
        require(reference.path.startsWith("analysis/timing-mapping/")) { "MIDI time-mapping report path is not canonical" }
        val path = confinedFile(projectRoot, reference.path)
        require(sha256File(path) == reference.sha256) { "MIDI time-mapping report is stale" }
        return json.decodeFromString<MidiTimeMappingReport>(Files.readString(path, StandardCharsets.UTF_8)).also(MidiTimeMappingReport::requireValid)
    }

    /** Moves a temporary file only into a real project-local parent and never replaces different existing bytes. */
    private fun publishFile(projectRoot: Path, temporary: Path, target: Path, digest: String) {
        Files.createDirectories(requireNotNull(target.parent))
        val rootReal = projectRoot.toAbsolutePath().normalize().toRealPath()
        require(target.parent.toRealPath().startsWith(rootReal) && !Files.isSymbolicLink(target)) { "Timing mapping target is not project-confined" }
        if (Files.exists(target)) {
            require(sha256File(target) == digest) { "Timing mapping target already contains different content" }
            Files.deleteIfExists(temporary)
            return
        }
        try {
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, target)
            }
        } catch (_: FileAlreadyExistsException) {
            require(Files.isRegularFile(target) && sha256File(target) == digest) { "Timing mapping target already contains different content" }
            Files.deleteIfExists(temporary)
        }
    }

    /** Resolves one referenced file below a real project root while rejecting traversal and symlink escapes. */
    private fun confinedFile(projectRoot: Path, relative: String): Path {
        val root = projectRoot.toAbsolutePath().normalize()
        val path = root.resolve(relative).normalize()
        require(path.startsWith(root) && Files.isRegularFile(path) && !Files.isSymbolicLink(path) && path.toRealPath().startsWith(root.toRealPath())) {
            "Timing mapping artifact is missing"
        }
        return path
    }

    /** Creates a canonical artifact reference from a verified project-local target. */
    private fun artifactRef(projectRoot: Path, target: Path, digest: String): ArtifactRef = ArtifactRef(
        projectRoot.toAbsolutePath().normalize().relativize(target).toString().replace('\\', '/'), digest
    )
}

/** Treats MIDI note-on velocity zero as the standard note-off form. */
private fun ShortMessage.isNoteOn(): Boolean = command == ShortMessage.NOTE_ON && data2 > 0

/** Recognizes both ordinary note-off messages and note-on velocity zero messages. */
private fun ShortMessage.isNoteOff(): Boolean = command == ShortMessage.NOTE_OFF || (command == ShortMessage.NOTE_ON && data2 == 0)

/** Reads absent confidence as zero so uncertainty can never become an automatic approval. */
private fun Double?.orEmptyConfidence(): Double = this ?: 0.0

/** Calculates one lowercase SHA-256 digest over bytes without using a filesystem path as evidence. */
private fun sha256Bytes(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

/** Calculates a lowercase SHA-256 digest over one already confined file. */
private fun sha256File(path: Path): String = sha256Bytes(Files.readAllBytes(path))

private val SHA_256 = Regex("[0-9a-f]{64}")
