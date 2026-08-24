package app.melotrail.application

import app.melotrail.arrangement.ProjectStore
import app.melotrail.arrangement.ReleaseSimilarityReport
import app.melotrail.commercial.CommercialProvenanceManifest
import app.melotrail.commercial.CommercialProvenanceService
import app.melotrail.model.MasteringProfile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/** Read-only, presentation-safe release evidence. The UI never reads release files directly. */
data class ReleaseReviewSnapshot(
    val mastering: ReleaseMasteringSummary? = null,
    val commercial: ReleaseCommercialSummary? = null,
    val recognizability: ReleaseRecognizabilitySummary? = null,
    val similarity: ReleaseSimilarityReport? = null,
    /** Local AAC/MP3 encode-decode measurements; unverified is explicit and never a pass. */
    val codecPreviews: List<CodecPreviewEvidence> = emptyList(),
    val blockers: List<String> = emptyList()
)

data class ReleaseMasteringSummary(
    val integratedLufs: Double,
    val truePeakDbtp: Double,
    val loudnessRangeLu: Double,
    val crestDb: Double,
    val loudnessReference: String,
    val dynamicsPreserved: Boolean,
    val dynamicsIssues: List<String>,
    /** Null is legacy/stale evidence, not proof that any current policy passed. */
    val policy: MasteringProfile? = null
)

data class ReleaseCommercialSummary(
    val ready: Boolean,
    val reasons: List<String>,
    val requiredAttribution: List<String>,
    val aiDisclosureRecommended: Boolean,
    /** Project-relative only. */
    val reportReference: String,
    /** Project-relative only. */
    val youtubeMetadataReference: String
)

data class ReleaseRecognizabilitySummary(
    val passed: Boolean,
    val clearOccurrenceCount: Int,
    val reasons: List<String>
)

interface ReleaseReviewApplicationService {
    fun load(root: Path): ReleaseReviewSnapshot
}

/**
 * Reads only canonical, selected release evidence. It performs no audio analysis,
 * generation, mutation, or directory scanning.
 */
class DefaultReleaseReviewApplicationService(
    private val provenance: CommercialProvenanceService = CommercialProvenanceService()
) : ReleaseReviewApplicationService {
    override fun load(root: Path): ReleaseReviewSnapshot {
        val projectRoot = root.toAbsolutePath().normalize()
        val project = ProjectStore.read(projectRoot)
        val blockers = mutableListOf<String>()
        val metadata = readReleaseMetadata(projectRoot, blockers)
        val recognizability = project.workflow.signatureMotif?.releaseGateResult?.let {
            ReleaseRecognizabilitySummary(it.passed, it.clearOccurrenceCount, it.reasons)
        } ?: run {
            blockers += "Signature motif recognizability has not been evaluated."
            null
        }
        recognizability?.takeUnless(ReleaseRecognizabilitySummary::passed)?.reasons?.forEach { reason ->
            blockers += "Signature motif recognizability failed: $reason"
        }

        val commercial = project.workflow.commercialProvenance?.manifest?.let { reference ->
            readCommercial(projectRoot, reference.file, reference.sha256, blockers)
        } ?: run {
            blockers += "Commercial provenance has not been created for this release."
            null
        }
        return ReleaseReviewSnapshot(
            mastering = metadata?.mastering,
            commercial = commercial,
            recognizability = recognizability,
            similarity = metadata?.similarity,
            codecPreviews = metadata?.codecPreviews.orEmpty(),
            blockers = blockers.distinct()
        )
    }

    private fun readReleaseMetadata(root: Path, blockers: MutableList<String>): ParsedReleaseMetadata? {
        val path = root.resolve("output/release.json").normalize()
        if (!path.startsWith(root) || !Files.isRegularFile(path)) {
            blockers += "Build the current project to create validated release metadata."
            return null
        }
        return runCatching {
            val objectValue = json.parseToJsonElement(Files.readString(path, StandardCharsets.UTF_8)).jsonObject
            fun number(name: String) = requireNotNull(objectValue[name]?.jsonPrimitive?.doubleOrNull) { "release metadata is missing $name" }
            fun text(name: String) = requireNotNull(objectValue[name]?.jsonPrimitive?.contentOrNull) { "release metadata is missing $name" }
            val master = root.resolve("output").resolve(text("master")).normalize()
            val masterFingerprint = text("masterFingerprint")
            require(master.startsWith(root.resolve("output")) && Files.isRegularFile(master) && digest(master) == masterFingerprint) {
                "release metadata does not match the selected lossless master"
            }
            val issues = objectValue["masteringQualityIssues"]?.let { json.decodeFromJsonElement(ListSerializer, it) }
                ?: error("release metadata is missing masteringQualityIssues")
            val similarity = objectValue["similarityReview"]?.let { json.decodeFromJsonElement(ReleaseSimilarityReport.serializer(), it) }
                ?: error("release metadata is missing similarityReview")
            val codecPreviews = objectValue["codecPreviews"]?.let { json.decodeFromJsonElement(CodecPreviewListSerializer, it) }.orEmpty()
            require(codecPreviews.all { it.masterSha256 == masterFingerprint }) { "codec-preview evidence belongs to another master" }
            val policy = objectValue["masteringPolicy"]?.let { json.decodeFromJsonElement(MasteringProfile.serializer(), it) }
            codecPreviews.filter { it.status == CodecPreviewStatus.BLOCKED }.forEach { preview ->
                blockers += "Local ${preview.codec} codec preview exceeds delivery policy: ${preview.detail}"
            }
            ParsedReleaseMetadata(
                ReleaseMasteringSummary(
                    number("integratedLufs"), number("truePeakDbtp"), number("loudnessRangeLu"), number("crestDb"),
                    text("loudnessReference"), objectValue["dynamicsPreserved"]?.jsonPrimitive?.content == "true", issues, policy
                ),
                similarity,
                codecPreviews
            )
        }.getOrElse { failure ->
            blockers += "Release metadata is unavailable or invalid: ${failure.message ?: "unknown error"}"
            null
        }
    }

    private fun readCommercial(root: Path, relative: String, expectedHash: String, blockers: MutableList<String>): ReleaseCommercialSummary? {
        val path = root.resolve(relative).normalize()
        if (!path.startsWith(root) || !Files.isRegularFile(path) || digest(path) != expectedHash) {
            blockers += "Selected commercial provenance is missing or stale. Create it again."
            return null
        }
        return runCatching {
            val manifest = json.decodeFromString(CommercialProvenanceManifest.serializer(), Files.readString(path, StandardCharsets.UTF_8))
            val verification = provenance.verifyReleaseLineage(root, manifest.releaseId)
            val ready = manifest.commercialReady && verification.closed && verification.commercialReady
            val reasons = (manifest.reasons + verification.missingDependencies + verification.tamperedDependencies + verification.unresolvedEvidence).distinct().sorted()
            if (!ready) reasons.forEach { blockers += "Commercial gate: $it" }
            ReleaseCommercialSummary(
                ready, reasons, manifest.attribution, manifest.aiDisclosureRecommended,
                manifest.reports.report, manifest.reports.checklist
            )
        }.getOrElse { failure ->
            blockers += "Selected commercial provenance is invalid: ${failure.message ?: "unknown error"}"
            null
        }
    }

    private data class ParsedReleaseMetadata(val mastering: ReleaseMasteringSummary, val similarity: ReleaseSimilarityReport, val codecPreviews: List<CodecPreviewEvidence>)

    private fun digest(path: Path): String = MessageDigest.getInstance("SHA-256")
        .digest(Files.readAllBytes(path)).joinToString("") { "%02x".format(it) }

    private companion object {
        val json = Json { ignoreUnknownKeys = false }
        val ListSerializer = kotlinx.serialization.builtins.ListSerializer(kotlinx.serialization.serializer<String>())
        val CodecPreviewListSerializer = kotlinx.serialization.builtins.ListSerializer(CodecPreviewEvidence.serializer())
    }
}
