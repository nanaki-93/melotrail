package app.melotrail.application

import app.melotrail.arrangement.EnhancementArtifactPaths
import app.melotrail.arrangement.EnhancementExecutionService
import app.melotrail.arrangement.EnhancementIntensity
import app.melotrail.arrangement.MusicalProcessingContextFactory
import app.melotrail.arrangement.ProjectStore
import app.melotrail.arrangement.StageId
import app.melotrail.arrangement.StageSubject
import app.melotrail.arrangement.EnhancementPlanner
import app.melotrail.arrangement.EnhancementPlanApplier
import app.melotrail.arrangement.LocalQwenEnhancementPlanner
import app.melotrail.arrangement.EnhancementModelIdentity
import app.melotrail.arrangement.ValidatedEnhancementMidiApplier
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files

/**
 * Injectable StageRunner adapter for the deterministic MVP.  It uses only
 * canonical input artifacts and writes output/report files below the project;
 * production model adapters can replace the planner/applier without changing
 * the StageRunner contract.
 */
class EnhancementStageProcessor(
    private val intensity: EnhancementIntensity = EnhancementIntensity.SUBTLE,
    private val seed: Long = 0L,
    private val contextFactory: (java.nio.file.Path, String, java.nio.file.Path, EnhancementIntensity, Long) -> app.melotrail.arrangement.MusicalProcessingContext =
        { root, partId, input, selectedIntensity, selectedSeed ->
            MusicalProcessingContextFactory.build(ProjectStore.read(root), partId, input, selectedIntensity, selectedSeed,
                profiles = app.melotrail.profile.BundledCompositionProfileCatalog.load())
        },
    private val planner: EnhancementPlanner = LocalQwenEnhancementPlanner(
        identity = EnhancementModelIdentity("qwen", "local", System.getenv("QWEN_ENHANCEMENT_VERSION") ?: "1", System.getenv("QWEN_ENHANCEMENT_LICENSE") ?: "unknown")
    ),
    private val applier: EnhancementPlanApplier = ValidatedEnhancementMidiApplier()
) : StageProcessor {
    override val definition = StageDefinition(StageId.ENHANCED, StageSubjectKind.PART, dependencies = setOf(StageId.CORRECTED))

    override suspend fun process(request: StageProcessingRequest): StageProcessorResult {
        val partId = (request.subject as? StageSubject.Part)?.partId ?: throw IllegalArgumentException("Enhancement requires a part subject")
        require(request.inputArtifacts.size == 1) { "Enhancement requires exactly one corrected MIDI artifact" }
        val input = request.root.resolve(request.inputArtifacts.single().path).normalize()
        require(input.startsWith(request.root) && Files.isRegularFile(input)) { "Enhancement input is unavailable" }
        val context = contextFactory(request.root, partId, input, intensity, seed)
        val outputDestination = EnhancementArtifactPaths.output(partId, context.contextSha256)
        val reportDestination = EnhancementArtifactPaths.report(partId, context.contextSha256)
        val output = request.temporaryRoot.resolve("enhanced.mid")
        val report = request.temporaryRoot.resolve("report.json")
        val result = EnhancementExecutionService(planner, applier).enhance(input, output, context)
        Files.writeString(report, JSON.encodeToString(result))
        request.reportProgress(100)
        return StageProcessorResult(listOf(TemporaryStageArtifact(output, outputDestination)), listOf(TemporaryStageArtifact(report, reportDestination)))
    }

    override fun validate(result: StageProcessorResult) {
        super.validate(result)
        result.outputs.forEach { output ->
            Files.newInputStream(output.temporaryPath).use { require(it.readNBytes(4).decodeToString() == "MThd") { "Enhancement output is not MIDI" } }
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private companion object { val JSON = Json { encodeDefaults = true; explicitNulls = false; ignoreUnknownKeys = false } }
}
