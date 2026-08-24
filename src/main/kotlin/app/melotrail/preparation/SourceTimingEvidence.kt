package app.melotrail.preparation

import app.melotrail.arrangement.ArtifactRef
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Immutable, worker-measured time evidence for one project-confined source. */
@Serializable
data class SourceTimingEvidence(
    val version: Int = CURRENT_VERSION,
    val partId: String,
    val source: InspectionSourceIdentity,
    val workerContractVersion: Int,
    val beats: List<SourceTimingPoint>,
    val onsets: List<SourceTimingPoint>,
    val tempoCandidates: List<TempoCandidate>,
    val leadingActivity: SourceTimingActivity? = null,
    val downbeat: DownbeatEvidence,
    val groove: SourceGrooveTemplate
) {
    /** Verifies that timing evidence remains bounded, finite, and source-bound. */
    fun requireValid() {
        require(version == CURRENT_VERSION) { "Unsupported source timing evidence version: $version" }
        SourceTimingPaths.requirePartId(partId)
        source.requireValid()
        require(workerContractVersion == WORKER_CONTRACT_VERSION) { "Unsupported worker timing contract version: $workerContractVersion" }
        require(beats.size <= MAX_POINTS && onsets.size <= MAX_POINTS && tempoCandidates.size <= MAX_TEMPO_CANDIDATES) {
            "Source timing evidence exceeds its bounded protocol size"
        }
        require(beats == beats.distinctBy(SourceTimingPoint::frame).sortedBy(SourceTimingPoint::frame)) { "Beat evidence must be unique and ordered" }
        require(onsets == onsets.distinctBy(SourceTimingPoint::frame).sortedBy(SourceTimingPoint::frame)) { "Onset evidence must be unique and ordered" }
        require(beats.zipWithNext().all { (earlier, later) -> earlier.timeSeconds < later.timeSeconds }) { "Beat evidence times must be strictly increasing" }
        require(onsets.zipWithNext().all { (earlier, later) -> earlier.timeSeconds < later.timeSeconds }) { "Onset evidence times must be strictly increasing" }
        beats.forEach { it.requireBeat() }
        onsets.forEach { it.requireOnset() }
        tempoCandidates.forEach(TempoCandidate::requireValid)
        require(tempoCandidates.map(TempoCandidate::bpm).distinct().size == tempoCandidates.size) { "Tempo candidates must be unique" }
        leadingActivity?.requireValid()
        downbeat.requireValid(beats)
        groove.requireValid(source.sha256)
    }

    companion object {
        const val CURRENT_VERSION = 1
        const val WORKER_CONTRACT_VERSION = 2
        const val MAX_POINTS = 256
        const val MAX_TEMPO_CANDIDATES = 8
    }
}

/** Project-model pointer to one immutable timing report and its exact source bytes. */
@Serializable
data class SourceTimingEvidenceReference(val report: ArtifactRef, val sourceSha256: String) {
    /** Verifies that a persisted pointer has no path alias or unbound source hash. */
    fun requireValid() {
        require(SHA_256.matches(sourceSha256)) { "Source timing evidence reference source fingerprint is invalid" }
    }
}

/** One measured frame/time point; beats carry confidence and onsets carry strength. */
@Serializable
data class SourceTimingPoint(
    val frame: Int,
    val timeSeconds: Double,
    val confidence: Double? = null,
    val strength: Double? = null
) {
    /** Validates a measured beat point. */
    fun requireBeat() {
        requireBase()
        require(confidence != null && strength == null) { "Beat evidence requires confidence only" }
    }

    /** Validates a measured onset point. */
    fun requireOnset() {
        requireBase()
        require(strength != null && confidence == null) { "Onset evidence requires strength only" }
    }

    /** Validates shared finite timing-point fields. */
    private fun requireBase() {
        require(frame >= 0 && timeSeconds.isFinite() && timeSeconds >= 0.0) { "Timing point is invalid" }
        confidence?.let { require(it.isFinite() && it in 0.0..1.0) { "Timing confidence is invalid" } }
        strength?.let { require(it.isFinite() && it in 0.0..1.0) { "Onset strength is invalid" } }
    }
}

/** A confidence-scored tempo inferred only from accepted beat intervals. */
@Serializable
data class TempoCandidate(val bpm: Double, val confidence: Double, val supportingIntervals: Int) {
    /** Validates bounded tempo-candidate facts. */
    fun requireValid() {
        require(bpm.isFinite() && bpm in 30.0..300.0 && confidence.isFinite() && confidence in 0.0..1.0 && supportingIntervals > 0) {
            "Tempo candidate is invalid"
        }
    }
}

/** The first audible source-sample frame, which is timing evidence but never musical authority. */
@Serializable
data class SourceTimingActivity(val frame: Int, val timeSeconds: Double, val confidence: Double) {
    /** Validates activity evidence. */
    fun requireValid() {
        require(frame >= 0 && timeSeconds.isFinite() && timeSeconds >= 0.0 && confidence.isFinite() && confidence in 0.0..1.0) {
            "Leading activity evidence is invalid"
        }
    }
}

/** The safely bounded outcomes for an audio-only downbeat proposal. */
@Serializable
enum class DownbeatEvidenceStatus { UNKNOWN, REVIEW_REQUIRED }

/** Allow-listed reasons avoid persisting worker paths or arbitrary diagnostic text. */
@Serializable
enum class DownbeatEvidenceReason { INSUFFICIENT_BEAT_EVIDENCE, AUDIO_ONLY_PHASE_IS_NOT_AUTHORITATIVE, ANALYSIS_UNAVAILABLE }

/** A worker proposal that deliberately remains non-authoritative until reviewed in a later timing decision. */
@Serializable
data class DownbeatEvidence(
    val status: DownbeatEvidenceStatus,
    val reason: DownbeatEvidenceReason,
    val candidateBeatIndex: Int? = null,
    val frame: Int? = null,
    val timeSeconds: Double? = null,
    val confidence: Double? = null
) {
    /** Validates the explicit unknown/review-required downbeat state. */
    fun requireValid(beats: List<SourceTimingPoint>) {
        when (status) {
            DownbeatEvidenceStatus.UNKNOWN -> require(candidateBeatIndex == null && frame == null && timeSeconds == null && confidence == null) {
                "Unknown downbeat evidence cannot contain a candidate"
            }
            DownbeatEvidenceStatus.REVIEW_REQUIRED -> {
                require(reason == DownbeatEvidenceReason.AUDIO_ONLY_PHASE_IS_NOT_AUTHORITATIVE) { "Review-required downbeat reason is invalid" }
                require(candidateBeatIndex != null && candidateBeatIndex in beats.indices && frame != null && timeSeconds != null && confidence != null) {
                    "Review-required downbeat evidence requires a measured beat candidate"
                }
                val beat = beats[candidateBeatIndex]
                require(beat.frame == frame && beat.timeSeconds == timeSeconds && confidence.isFinite() && confidence in 0.0..1.0) {
                    "Downbeat candidate does not match measured beat evidence"
                }
            }
        }
    }
}

/** A source-relative micro-timing vector; no project grid or timing decision is implied. */
@Serializable
data class SourceGrooveTemplate(
    val version: Int = CURRENT_VERSION,
    val sourceSha256: String,
    val subdivisionsPerBeat: Int = SUBDIVISIONS_PER_BEAT,
    val bins: List<SourceGrooveBin>,
    val confidence: Double,
    val status: SourceGrooveTemplateStatus,
    val excludedPickupOnsets: Int,
    val excludedTempoDriftIntervals: Int,
    val excludedMissingOnsetIntervals: Int,
    val excludedOutlierOnsets: Int
) {
    /** Verifies explicit neutral bins and bounded measured deviation facts. */
    fun requireValid(expectedSourceSha256: String) {
        require(version == CURRENT_VERSION && sourceSha256 == expectedSourceSha256 && SHA_256.matches(sourceSha256)) {
            "Source groove template source binding is invalid"
        }
        require(subdivisionsPerBeat == SUBDIVISIONS_PER_BEAT && bins.map(SourceGrooveBin::subdivision).sorted() == (0 until subdivisionsPerBeat).toList()) {
            "Source groove template subdivision bins are invalid"
        }
        bins.forEach(SourceGrooveBin::requireValid)
        require(confidence.isFinite() && confidence in 0.0..1.0) { "Source groove confidence is invalid" }
        when (status) {
            SourceGrooveTemplateStatus.MEASURED -> require(confidence >= REVIEW_CONFIDENCE && bins.any { it.status == SourceGrooveBinStatus.MEASURED }) {
                "Measured source groove requires sufficient support"
            }
            SourceGrooveTemplateStatus.REVIEW_REQUIRED -> require(confidence < REVIEW_CONFIDENCE) {
                "Low-confidence source groove must remain review-required"
            }
        }
        require(listOf(excludedPickupOnsets, excludedTempoDriftIntervals, excludedMissingOnsetIntervals, excludedOutlierOnsets).all { it >= 0 }) {
            "Source groove exclusions are invalid"
        }
    }

    companion object {
        const val CURRENT_VERSION = 1
        const val SUBDIVISIONS_PER_BEAT = 4
        const val REVIEW_CONFIDENCE = 0.5
    }
}

/** A measured deviation or an explicitly neutral subdivision with no measured support. */
@Serializable
data class SourceGrooveBin(
    val subdivision: Int,
    val status: SourceGrooveBinStatus,
    val deviationFractionOfBeat: Double = 0.0,
    val confidence: Double = 0.0,
    val supportingOnsets: Int = 0
) {
    /** Validates that unsupported bins stay neutral and measured bins remain bounded. */
    fun requireValid() {
        require(subdivision in 0 until SourceGrooveTemplate.SUBDIVISIONS_PER_BEAT && deviationFractionOfBeat.isFinite() && confidence.isFinite()) {
            "Source groove bin is invalid"
        }
        when (status) {
            SourceGrooveBinStatus.NEUTRAL_UNKNOWN -> require(deviationFractionOfBeat == 0.0 && confidence == 0.0 && supportingOnsets == 0) {
                "Neutral source groove bins cannot imply measured feel"
            }
            SourceGrooveBinStatus.MEASURED -> require(abs(deviationFractionOfBeat) <= MAX_DEVIATION_FRACTION && confidence in 0.0..1.0 && supportingOnsets > 0) {
                "Measured source groove bin is outside policy bounds"
            }
        }
    }

    companion object { const val MAX_DEVIATION_FRACTION = 0.125 }
}

/** Neutral bins communicate missing evidence rather than silence or a zero-timing claim. */
@Serializable
enum class SourceGrooveBinStatus { MEASURED, NEUTRAL_UNKNOWN }

/** Distinguishes a sufficiently supported source feel from one that needs review or grid fallback. */
@Serializable
enum class SourceGrooveTemplateStatus { MEASURED, REVIEW_REQUIRED }

/** Derives a conservative source-relative groove template while retaining explicit excluded evidence. */
object SourceGrooveTemplateDeriver {
    /** Builds one bounded template from worker timing facts without applying a project-grid warp. */
    fun derive(sourceSha256: String, beats: List<SourceTimingPoint>, onsets: List<SourceTimingPoint>): SourceGrooveTemplate {
        val intervals = beats.zipWithNext().map { (start, end) -> BeatInterval(start, end, end.timeSeconds - start.timeSeconds) }
        val positive = intervals.map(BeatInterval::durationSeconds).filter { it.isFinite() && it > 0.0 }
        val median = positive.sorted().let { values -> values.getOrNull(values.size / 2) }
        val valid = intervals.filter { interval -> median != null && abs(interval.durationSeconds - median) <= median * TEMPO_DRIFT_TOLERANCE }
        val pickup = onsets.count { onset -> beats.firstOrNull()?.let { onset.timeSeconds < it.timeSeconds } == true }
        val values = Array(SourceGrooveTemplate.SUBDIVISIONS_PER_BEAT) { mutableListOf<Double>() }
        var outliers = 0
        var missingOnsetIntervals = 0
        valid.forEach { interval ->
            val intervalOnsets = onsets.filter { it.timeSeconds >= interval.start.timeSeconds && it.timeSeconds < interval.end.timeSeconds }
            if (intervalOnsets.isEmpty()) missingOnsetIntervals++
            intervalOnsets.forEach { onset ->
                val relative = (onset.timeSeconds - interval.start.timeSeconds) / interval.durationSeconds
                val subdivision = (relative * SourceGrooveTemplate.SUBDIVISIONS_PER_BEAT).roundToInt()
                    .coerceIn(0, SourceGrooveTemplate.SUBDIVISIONS_PER_BEAT - 1)
                val deviation = relative - subdivision.toDouble() / SourceGrooveTemplate.SUBDIVISIONS_PER_BEAT
                if (abs(deviation) > SourceGrooveBin.MAX_DEVIATION_FRACTION) outliers++ else values[subdivision] += deviation
            }
        }
        val bins = values.mapIndexed(::bin)
        val measured = bins.filter { it.status == SourceGrooveBinStatus.MEASURED }
        val confidence = if (measured.isEmpty()) 0.0 else {
            measured.map(SourceGrooveBin::confidence).average() * measured.size / SourceGrooveTemplate.SUBDIVISIONS_PER_BEAT
        }
        return SourceGrooveTemplate(
            sourceSha256 = sourceSha256,
            bins = bins,
            confidence = confidence,
            status = if (confidence >= SourceGrooveTemplate.REVIEW_CONFIDENCE) SourceGrooveTemplateStatus.MEASURED else SourceGrooveTemplateStatus.REVIEW_REQUIRED,
            excludedPickupOnsets = pickup,
            excludedTempoDriftIntervals = intervals.size - valid.size,
            excludedMissingOnsetIntervals = missingOnsetIntervals,
            excludedOutlierOnsets = outliers
        ).also { it.requireValid(sourceSha256) }
    }

    /** Converts one subdivision's accepted offsets into an explicit measurement or neutral unknown bin. */
    private fun bin(index: Int, candidates: List<Double>): SourceGrooveBin {
        if (candidates.isEmpty()) return SourceGrooveBin(index, SourceGrooveBinStatus.NEUTRAL_UNKNOWN)
        val median = candidates.sorted().let { it[it.size / 2] }
        val retained = candidates.filter { abs(it - median) <= WITHIN_BIN_OUTLIER_TOLERANCE }
        if (retained.isEmpty()) return SourceGrooveBin(index, SourceGrooveBinStatus.NEUTRAL_UNKNOWN)
        val deviation = retained.sorted().let { it[it.size / 2] }
        val confidence = (retained.size / SUPPORT_FOR_FULL_CONFIDENCE).coerceAtMost(1.0)
        return SourceGrooveBin(index, SourceGrooveBinStatus.MEASURED, deviation, confidence, retained.size)
    }

    private data class BeatInterval(val start: SourceTimingPoint, val end: SourceTimingPoint, val durationSeconds: Double)

    private const val TEMPO_DRIFT_TOLERANCE = 0.15
    private const val WITHIN_BIN_OUTLIER_TOLERANCE = 0.08
    private const val SUPPORT_FOR_FULL_CONFIDENCE = 4.0
}

/** Canonical project-relative locations for immutable source timing reports. */
object SourceTimingPaths {
    /** Resolves an immutable report target under the project without accepting caller path text. */
    fun report(projectRoot: Path, evidence: SourceTimingEvidence): Path = projectPath(projectRoot, "analysis/timing/${evidence.partId}/${contentSha256(evidence)}.json")

    /** Validates one part identifier before deriving a timing-report location. */
    fun requirePartId(partId: String) {
        require(PART_ID.matches(partId)) { "Part ID is invalid." }
    }

    /** Returns the canonical report fingerprint used as its immutable filename. */
    fun contentSha256(evidence: SourceTimingEvidence): String = sha256(JSON.encodeToString(evidence).toByteArray(StandardCharsets.UTF_8))

    /** Resolves one fixed project-relative path and rejects traversal. */
    private fun projectPath(projectRoot: Path, relative: String): Path {
        val root = projectRoot.toAbsolutePath().normalize()
        val path = root.resolve(relative).normalize()
        require(path.startsWith(root)) { "Timing evidence must remain inside the project." }
        return path
    }

    private val PART_ID = Regex("[A-Za-z0-9_-]{1,64}")
    private val JSON = Json { encodeDefaults = true }
}

/** Strict serializer and atomic publisher for immutable source timing evidence. */
object SourceTimingEvidenceStore {
    /** Publishes an evidence report without replacing a differently-contented candidate. */
    fun write(projectRoot: Path, evidence: SourceTimingEvidence): ArtifactRef {
        evidence.requireValid()
        val bytes = JSON.encodeToString(evidence).toByteArray(StandardCharsets.UTF_8)
        val target = SourceTimingPaths.report(projectRoot, evidence)
        Files.createDirectories(target.parent)
        val root = projectRoot.toAbsolutePath().normalize().toRealPath()
        require(target.parent.toRealPath().startsWith(root) && !Files.isSymbolicLink(target)) {
            "Source timing evidence target is not project-confined."
        }
        if (Files.exists(target)) {
            require(Files.readAllBytes(target).contentEquals(bytes)) { "Source timing evidence target already contains different content." }
        } else {
            val temporary = target.resolveSibling(".${target.fileName}.${UUID.randomUUID()}.tmp")
            try {
                Files.write(temporary, bytes, StandardOpenOption.CREATE_NEW)
                try {
                    Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE)
                } catch (_: AtomicMoveNotSupportedException) {
                    Files.move(temporary, target)
                }
            } finally {
                Files.deleteIfExists(temporary)
            }
        }
        return ArtifactRef(projectRoot.toAbsolutePath().normalize().relativize(target).toString().replace('\\', '/'), sha256(bytes))
    }

    /** Reads and verifies one referenced report below the canonical timing directory. */
    fun read(projectRoot: Path, reference: ArtifactRef): SourceTimingEvidence {
        require(reference.path.startsWith("analysis/timing/")) { "Source timing report path is not canonical." }
        val root = projectRoot.toAbsolutePath().normalize()
        val path = root.resolve(reference.path).normalize()
        require(path.startsWith(root) && Files.isRegularFile(path) && !Files.isSymbolicLink(path) && path.toRealPath().startsWith(root.toRealPath()) && sha256(Files.readAllBytes(path)) == reference.sha256) {
            "Source timing evidence is missing or stale."
        }
        return JSON.decodeFromString<SourceTimingEvidence>(Files.readString(path, StandardCharsets.UTF_8)).also(SourceTimingEvidence::requireValid)
    }

    private val JSON = Json { prettyPrint = true; encodeDefaults = true; ignoreUnknownKeys = false }
}

/** Calculates a lowercase SHA-256 digest for timing evidence without exposing a filesystem path. */
private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

private val SHA_256 = Regex("[0-9a-f]{64}")
