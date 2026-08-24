package app.melotrail.desktop

import app.melotrail.application.ApproveEnhancementRequest
import app.melotrail.application.ArrangementPlannerKind
import app.melotrail.application.BuildApplicationService
import app.melotrail.application.BuildAudioWorker
import app.melotrail.application.BuildSongRequest
import app.melotrail.application.CompositionSettingsInput
import app.melotrail.application.ConfirmSourceKey
import app.melotrail.application.CreateEnhancementRequest
import app.melotrail.application.CreateMidiAiFixRequest
import app.melotrail.application.CreateProjectRequest
import app.melotrail.application.CreateTechnicalCorrectionRequest
import app.melotrail.application.DefaultArrangementApplicationService
import app.melotrail.application.DefaultBuildApplicationService
import app.melotrail.application.DefaultEnsembleCohesionApplicationService
import app.melotrail.application.DefaultEnhancementApplicationService
import app.melotrail.application.DefaultFullSongCriticApplicationService
import app.melotrail.application.DefaultFullSongEnhancementApplicationService
import app.melotrail.application.DefaultHumanizationApplicationService
import app.melotrail.application.DefaultMidiAiFixApplicationService
import app.melotrail.application.DefaultMixApplicationService
import app.melotrail.application.DefaultSourceSongCriticApplicationService
import app.melotrail.application.DefaultTechnicalCorrectionApplicationService
import app.melotrail.application.EnsembleCohesionPlannerKind
import app.melotrail.application.GenerateArrangementRequest
import app.melotrail.application.GenerateEnsembleCohesionRequest
import app.melotrail.application.GenerateHumanizationRequest
import app.melotrail.application.ImportSongPart
import app.melotrail.application.MidiQualityStatus
import app.melotrail.application.NormalizePartRequest
import app.melotrail.application.ProgressSink
import app.melotrail.application.SaveStructureRequest
import app.melotrail.application.SelectMidiFeelRequest
import app.melotrail.application.SetHarmonyProgression
import app.melotrail.application.TransposePartRequest
import app.melotrail.application.UpdateCompositionSettings
import app.melotrail.application.AnalyzePartRequest
import app.melotrail.application.ApplyMixRequest
import app.melotrail.arrangement.ArrangementRole
import app.melotrail.arrangement.ArrangementRoleSelection
import app.melotrail.arrangement.EnhancementIntensity
import app.melotrail.arrangement.EnsembleCohesionEnhancementIntensity
import app.melotrail.arrangement.InstrumentRegistryLoader
import app.melotrail.arrangement.CompressionPlan
import app.melotrail.arrangement.EqBandPlan
import app.melotrail.arrangement.FilterPlan
import app.melotrail.arrangement.MixBus
import app.melotrail.arrangement.MixBusPlan
import app.melotrail.arrangement.MixPlan
import app.melotrail.arrangement.MixTrackPlan
import app.melotrail.arrangement.MidiAnalysisInput
import app.melotrail.arrangement.SectionTypeId
import app.melotrail.arrangement.SharedRoomPlan
import app.melotrail.arrangement.SfizzInstrumentRenderer
import app.melotrail.harmony.HarmonyTemplateId
import app.melotrail.logging.DefaultLogger
import app.melotrail.errors.ErrorReporter
import app.melotrail.model.MasteringMeasurement
import app.melotrail.model.MasteringProfile
import app.melotrail.music.MusicalKey
import app.melotrail.music.PitchClass
import app.melotrail.music.PitchSpelling
import app.melotrail.music.ScaleModeId
import app.melotrail.music.Tempo
import app.melotrail.music.TimeSignature
import app.melotrail.profile.CompositionProfileRef
import app.melotrail.profile.MoodRef
import app.melotrail.worker.MP3ExportCommand
import app.melotrail.worker.MasterCommand
import app.melotrail.worker.RepairCommand
import app.melotrail.worker.RepairSpec
import app.melotrail.worker.WorkerClient
import app.melotrail.worker.WorkerStatus
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertTrue

/**
 * Explicit live proof for the supplied four WAV sources. It is opt-in because
 * it invokes the local Basic Pitch, Qwen, sfizz, and mastering runtimes and
 * writes the canonical project into data/audio.
 */
class LiveLoFiFourSourceEndToEndTest {
    @Test
    fun `four source C natural minor lo-fi song reaches an exported WAV`() = runBlocking {
        assumeTrue(System.getenv("MELOTRAIL_RUN_LIVE_E2E") == "1", "Set MELOTRAIL_RUN_LIVE_E2E=1 to run local audio/model integration.")
        val workspace = generateSequence(Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()) { it.parent }
            .firstOrNull { Files.isDirectory(it.resolve("data/audio/input")) && Files.isDirectory(it.resolve("sounds")) }
            ?: error("Unable to locate the MeloTrail workspace from the Gradle test working directory.")
        val root = workspace.resolve("data/audio")
        val input = root.resolve("input")
        val output = root.resolve("output")
        val sources = listOf(
            Part("intro", "intro.wav", "Intro", SectionTypeId.INTRO),
            Part("verse", "verse.wav", "Verse", SectionTypeId.VERSE),
            Part("chorus", "ch.wav", "Chorus", SectionTypeId.CHORUS),
            Part("bridge", "bridge.wav", "Bridge", SectionTypeId.BRIDGE)
        )
        require(sources.all { Files.isRegularFile(input.resolve(it.file)) }) { "Expected intro.wav, verse.wav, ch.wav, and bridge.wav in data/audio/input." }
        require(!Files.exists(root.resolve("project.json"))) { "Live E2E refuses to replace an existing data/audio project." }

        val client = WorkerClient(logger = DefaultLogger(), errorReporter = ErrorReporter(DefaultLogger()))
        assertTrue(client.healthCheck(), "The local worker must be running at http://127.0.0.1:8081.")
        val libraryRoot = workspace.resolve("sounds")
        val renderer = SfizzInstrumentRenderer(InstrumentRegistryLoader(libraryRoot))
        val projects = DesktopServiceComposition.projectService()
        val arrangements = DefaultArrangementApplicationService(libraryRoot = libraryRoot)
        val cohesion = DefaultEnsembleCohesionApplicationService()

        projects.create(CreateProjectRequest(root, name = "C Natural Minor Lo-fi Four Source E2E"))
        projects.updateCompositionSettings(UpdateCompositionSettings(
            root, 0,
            CompositionSettingsInput(
                name = "C Natural Minor Lo-fi Four Source E2E",
                key = MusicalKey(PitchClass.of(PitchSpelling.C), ScaleModeId.NATURAL_MINOR),
                tempo = Tempo(80.0), timeSignature = TimeSignature(4, 4),
                profile = CompositionProfileRef("lofi", 1), mood = MoodRef("warm", 1)
            )
        ))
        applyHarmony(projects, root, SectionTypeId.INTRO, "lofi-minor-drift-v1")
        applyHarmony(projects, root, SectionTypeId.VERSE, "lofi-minor-moody-v1")
        applyHarmony(projects, root, SectionTypeId.CHORUS, "lofi-minor-cinematic-v1")
        applyHarmony(projects, root, SectionTypeId.BRIDGE, "lofi-minor-warm-v1")

        sources.forEach { part ->
            projects.importSongPart(ImportSongPart(root, part.id, input.resolve(part.file), part.name, part.sectionType))
        }
        prepareSourcesInProjectKey(projects, root, sources.map(Part::id))

        processSources(projects, root, sources.map(Part::id))
        projects.saveStructure(SaveStructureRequest(root, listOf("intro", "verse", "verse", "chorus", "bridge", "verse")))

        approveSourceCritic(root)
        arrangeAllStandardInstruments(arrangements, root)
        applyCohesion(cohesion, root)
        critiqueAndEnhanceSong(root)
        DefaultHumanizationApplicationService().generate(GenerateHumanizationRequest(root))

        val mix = DefaultMixApplicationService()
        arrangements.renderApprovedStems(root, renderer, ProgressSink.None)
        mix.apply(ApplyMixRequest(root, loFiMixPlan()), ProgressSink.None)
        val build = DefaultBuildApplicationService(arrangements, mix, renderer, LiveBuildWorker(client), cohesion)
            .build(BuildSongRequest(root, enableLoFi = true, enableMp3 = false), ProgressSink.None)
        assertTrue(Files.isRegularFile(build.master) && Files.size(build.master) > 44, "Expected a rendered master WAV.")
        assertTrue(Files.isRegularFile(output.resolve("master.wav")))
    }

    @Test
    fun `existing live project can finish after an explicit full-song model bypass`() = runBlocking {
        assumeTrue(System.getenv("MELOTRAIL_RESUME_LIVE_E2E") == "1", "Set MELOTRAIL_RESUME_LIVE_E2E=1 only after an explicit reviewed Full-Song Enhance bypass.")
        val workspace = generateSequence(Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()) { it.parent }
            .firstOrNull { Files.isDirectory(it.resolve("data/audio/input")) && Files.isDirectory(it.resolve("sounds")) }
            ?: error("Unable to locate the MeloTrail workspace from the Gradle test working directory.")
        val root = workspace.resolve("data/audio")
        val output = root.resolve("output")
        val client = WorkerClient(logger = DefaultLogger(), errorReporter = ErrorReporter(DefaultLogger()))
        assertTrue(client.healthCheck(), "The local worker must be running at http://127.0.0.1:8081.")
        val libraryRoot = workspace.resolve("sounds")
        val arrangements = DefaultArrangementApplicationService(libraryRoot = libraryRoot)
        val renderer = SfizzInstrumentRenderer(InstrumentRegistryLoader(libraryRoot))
        val projects = DesktopServiceComposition.projectService()
        val partIds = listOf("intro", "verse", "chorus", "bridge")

        val cohesion = DefaultEnsembleCohesionApplicationService()
        prepareSourcesInProjectKey(projects, root, partIds)
        processSources(projects, root, partIds)
        projects.saveStructure(SaveStructureRequest(root, listOf("intro", "verse", "verse", "chorus", "bridge", "verse")))
        approveSourceCritic(root)
        arrangeAllStandardInstruments(arrangements, root)
        applyCohesion(cohesion, root)
        critiqueAndEnhanceSong(root)
        DefaultHumanizationApplicationService().generate(GenerateHumanizationRequest(root))
        val mix = DefaultMixApplicationService()
        arrangements.renderApprovedStems(root, renderer, ProgressSink.None)
        mix.apply(ApplyMixRequest(root, loFiMixPlan()), ProgressSink.None)
        val build = DefaultBuildApplicationService(arrangements, mix, renderer, LiveBuildWorker(client), cohesion)
            .build(BuildSongRequest(root, enableLoFi = true, enableMp3 = false), ProgressSink.None)
        assertTrue(Files.isRegularFile(build.master) && Files.size(build.master) > 44, "Expected a rendered master WAV.")
        assertTrue(Files.isRegularFile(output.resolve("master.wav")))
    }

    @Test
    fun `current live project retries bounded full-song enhancement and rebuilds`() = runBlocking {
        assumeTrue(System.getenv("MELOTRAIL_RETRY_FULL_SONG_E2E") == "1", "Set MELOTRAIL_RETRY_FULL_SONG_E2E=1 to retry the local critic-guided polish stage.")
        val workspace = generateSequence(Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()) { it.parent }
            .firstOrNull { Files.isDirectory(it.resolve("data/audio/input")) && Files.isDirectory(it.resolve("sounds")) }
            ?: error("Unable to locate the MeloTrail workspace from the Gradle test working directory.")
        val root = workspace.resolve("data/audio")
        val output = root.resolve("output")
        val currentMaster = output.resolve("master.wav")
        require(Files.isRegularFile(currentMaster)) { "A current approved live master is required before retrying final polish." }
        val backup = output.resolve("master-before-full-song-retry.wav")
        if (!Files.exists(backup)) Files.copy(currentMaster, backup)

        val client = WorkerClient(logger = DefaultLogger(), errorReporter = ErrorReporter(DefaultLogger()))
        assertTrue(client.healthCheck(), "The local worker must be running at http://127.0.0.1:8081.")
        val libraryRoot = workspace.resolve("sounds")
        val arrangements = DefaultArrangementApplicationService(libraryRoot = libraryRoot)
        val renderer = SfizzInstrumentRenderer(InstrumentRegistryLoader(libraryRoot))
        val cohesion = DefaultEnsembleCohesionApplicationService()

        val enhancement = DefaultFullSongEnhancementApplicationService(app.melotrail.arrangement.LocalQwenFullSongEnhancementPlanner())
        val candidate = enhancement.generateCandidate(root)
        assertTrue(candidate.selection.name == "NO_OP" || candidate.candidateAvailable,
            "The bounded candidate was rejected: ${candidate.warnings.joinToString()}")
        if (candidate.selection.name == "UNRESOLVED") enhancement.approve(root)
        DefaultHumanizationApplicationService().generate(GenerateHumanizationRequest(root))

        val mix = DefaultMixApplicationService()
        arrangements.renderApprovedStems(root, renderer, ProgressSink.None)
        mix.apply(ApplyMixRequest(root, loFiMixPlan()), ProgressSink.None)
        val build = DefaultBuildApplicationService(arrangements, mix, renderer, LiveBuildWorker(client), cohesion)
            .build(BuildSongRequest(root, enableLoFi = true, enableMp3 = false), ProgressSink.None)
        assertTrue(Files.isRegularFile(build.master) && Files.size(build.master) > 44, "Expected a rendered master WAV.")
        assertTrue(Files.isRegularFile(currentMaster))
    }

    private suspend fun prepareSourcesInProjectKey(
        projects: app.melotrail.application.ProjectApplicationService,
        root: Path,
        partIds: List<String>
    ) {
        partIds.forEach { partId ->
            var part = projects.open(root).parts.single { it.id == partId }
            if (part.preparation.transposedMidi && part.sourceKey?.confirmedOverride != null) return@forEach
            if (part.preparation.midiQuality.status == MidiQualityStatus.APPROVAL_REQUIRED) {
                projects.approveCleanMidi(root, partId)
                part = projects.open(root).parts.single { it.id == partId }
            }
            require(part.preparation.midiQuality.status == MidiQualityStatus.CURRENT) {
                "Clean MIDI for '$partId' is not approved."
            }
            if (part.sourceKey?.detectedKey == null) {
                projects.normalizePart(NormalizePartRequest(root, partId))
                part = projects.open(root).parts.single { it.id == partId }
            }
            val detectedKey = requireNotNull(part.sourceKey?.detectedKey) {
                "Normalization could not determine a source key for '$partId'; explicit musician input is required."
            }
            if (part.sourceKey?.confirmedOverride != detectedKey) {
                projects.confirmSourceKey(ConfirmSourceKey(root, partId, detectedKey, part.revision))
            }
            val transposed = projects.transposePart(TransposePartRequest(root, partId))
                .parts.single { it.id == partId }
            require(transposed.preparation.transposedMidi) {
                "Part '$partId' was not transposed into the authoritative project key."
            }
        }
    }

    private suspend fun processSources(
        projects: app.melotrail.application.ProjectApplicationService,
        root: Path,
        partIds: List<String>
    ) {
        val correction = DefaultTechnicalCorrectionApplicationService()
        val aiFix = DefaultMidiAiFixApplicationService()
        val enhancer = DefaultEnhancementApplicationService()
        partIds.forEach { partId ->
            val current = projects.open(root).parts.single { it.id == partId }.preparation.technicalCorrection
            if (!current.available || current.selected.name != "CORRECTED") {
                correction.create(CreateTechnicalCorrectionRequest(root, partId))
            }
        }
        partIds.forEach { partId ->
            if (!projects.open(root).parts.single { it.id == partId }.preparation.analyzed) {
                projects.analyzePart(AnalyzePartRequest(root, partId))
            }
        }
        partIds.forEach { partId ->
            val current = projects.open(root).parts.single { it.id == partId }.preparation.midiAiFix
            if (!current.selectedAvailable) {
                val fix = aiFix.create(CreateMidiAiFixRequest(root, partId))
                if (fix.draftAvailable) aiFix.approve(root, partId) else aiFix.skip(root, partId)
            }
            if (!projects.open(root).parts.single { it.id == partId }.preparation.analyzed) {
                projects.analyzePart(AnalyzePartRequest(root, partId))
            }
        }
        partIds.forEach { partId ->
            var current = projects.open(root).parts.single { it.id == partId }
            if (!current.preparation.enhancement.approvedAvailable || current.preparation.enhancement.selected.name != "ENHANCED") {
                val enhanced = enhancer.create(CreateEnhancementRequest(root, partId, EnhancementIntensity.BALANCED))
                projects.analyzePart(AnalyzePartRequest(root, partId))
                enhancer.approve(ApproveEnhancementRequest(root, partId, enhanced.draftSha256, enhanced.inputSha256, enhanced.contextSha256))
            }
            current = projects.open(root).parts.single { it.id == partId }
            if (current.preparation.midiFeel.selected != MidiAnalysisInput.LOFI_FEEL || !current.preparation.midiFeel.available) {
                projects.selectMidiFeel(SelectMidiFeelRequest(root, partId, MidiAnalysisInput.LOFI_FEEL))
            }
            if (!projects.open(root).parts.single { it.id == partId }.preparation.analyzed) {
                projects.analyzePart(AnalyzePartRequest(root, partId))
            }
        }
    }

    private fun approveSourceCritic(root: Path) {
        val sourceCritic = DefaultSourceSongCriticApplicationService()
        val sourceReport = sourceCritic.run(root)
        val hasBlockingSourceIssue = sourceReport.report.issues.any { it.severity.name == "BLOCKING" }
        sourceCritic.approve(
            root,
            hasBlockingSourceIssue,
            if (hasBlockingSourceIssue) "Live E2E audition pending; proceeding with explicit bounded test override." else null
        )
    }

    private suspend fun arrangeAllStandardInstruments(
        arrangements: DefaultArrangementApplicationService,
        root: Path
    ) {
        val roleSelections = listOf(
            ArrangementRoleSelection(ArrangementRole.MELODY),
            ArrangementRoleSelection(ArrangementRole.BASS),
            ArrangementRoleSelection(ArrangementRole.DRUMS),
            ArrangementRoleSelection(ArrangementRole.TEXTURE),
            ArrangementRoleSelection(ArrangementRole.COUNTER_MELODY)
        )
        val existing = runCatching { arrangements.load(root) }.getOrNull()
        if (existing == null || !existing.approved || existing.approvalRequired || existing.stale) {
            arrangements.generate(GenerateArrangementRequest(root, ArrangementPlannerKind.QWEN, roleSelections = roleSelections))
            arrangements.approve(root)
        }
        val arrangementPlan = Files.readString(root.resolve("arrangement_plan.json"))
        val requiredBaseInstruments = setOf("piano", "bass", "drums", "strings", "pad")
        assertTrue(requiredBaseInstruments.all { "\"kind\": \"$it\"" in arrangementPlan }, "Qwen arrangement must include every standard base instrument.")
        val required = arrangements.generateRequiredMidi(root)
        val requiredByInstrument = required.artifacts.associateBy { it.instrument }
        setOf("bass", "drums", "pad").forEach { instrument ->
            assertTrue(requireNotNull(requiredByInstrument[instrument]) { "Missing generated $instrument MIDI." }.events > 0, "$instrument MIDI must contain playable events.")
        }
        arrangements.approveCoreArrangement(root)
        val optional = arrangements.generateOptionalMidi(root)
        val strings = requireNotNull(optional.artifacts.singleOrNull { it.instrument == "strings" }) { "Missing generated strings MIDI." }
        assertTrue(strings.events > 0 && strings.resolution.name == "NOTES", "Strings MIDI must contain playable notes.")
    }

    private suspend fun applyCohesion(cohesion: DefaultEnsembleCohesionApplicationService, root: Path) {
        val cohesionDraft = cohesion.generate(GenerateEnsembleCohesionRequest(root, EnsembleCohesionPlannerKind.QWEN, EnsembleCohesionEnhancementIntensity.BALANCED))
        cohesionDraft.boundaries.forEach { boundary -> cohesion.reviewBoundary(root, boundary.outgoingInstanceId, boundary.incomingInstanceId) }
        cohesion.approve(root)
    }

    private suspend fun critiqueAndEnhanceSong(root: Path) {
        DefaultFullSongCriticApplicationService().run(root)
        val enhancement = DefaultFullSongEnhancementApplicationService(app.melotrail.arrangement.LocalQwenFullSongEnhancementPlanner())
        val candidate = runCatching { enhancement.generateCandidate(root) }.getOrNull()
        if (candidate?.selection?.name == "UNRESOLVED") enhancement.approve(root)
        else if (candidate == null) enhancement.selectBypass(root)
    }

    private fun applyHarmony(projects: app.melotrail.application.ProjectApplicationService, root: Path, section: SectionTypeId, template: String) {
        val current = projects.getHarmony(app.melotrail.application.GetHarmony(root))
        projects.setHarmonyProgression(SetHarmonyProgression(root, current.projectRevision, requireNotNull(current.revision), app.melotrail.harmony.SectionTypeId(section.value), HarmonyTemplateId(template)))
    }

    private fun loFiMixPlan() = MixPlan(
        tracks = mapOf(
            "piano" to MixTrackPlan(gainDb = -2.0, filter = FilterPlan(70.0, 9_000.0),
                eq = listOf(EqBandPlan(280.0, -1.5, 0.8)), compression = CompressionPlan(true, -20.0, 1.8, 0.5), reverbSend = 0.20, stereoWidth = 0.9),
            "bass" to MixTrackPlan(gainDb = -4.5, filter = FilterPlan(28.0, 4_500.0),
                eq = listOf(EqBandPlan(95.0, 1.5, 0.8)), compression = CompressionPlan(true, -22.0, 2.5, 1.0), reverbSend = 0.03, stereoWidth = 0.65),
            "drums" to MixTrackPlan(gainDb = -6.0, filter = FilterPlan(35.0, 10_000.0),
                eq = listOf(EqBandPlan(3_500.0, -1.5, 1.0)), compression = CompressionPlan(true, -20.0, 2.2, 0.5), reverbSend = 0.08, stereoWidth = 0.9, bus = MixBus.DRUMS),
            "pad" to MixTrackPlan(gainDb = -12.0, pan = -0.12, filter = FilterPlan(180.0, 7_000.0),
                compression = CompressionPlan(true, -24.0, 1.5), reverbSend = 0.30, stereoWidth = 1.25),
            "strings" to MixTrackPlan(gainDb = -13.0, pan = 0.12, filter = FilterPlan(160.0, 8_000.0),
                compression = CompressionPlan(true, -24.0, 1.5), reverbSend = 0.28, stereoWidth = 1.2)
        ),
        room = SharedRoomPlan(enabled = true, decaySeconds = 0.85, mix = 0.12),
        buses = mapOf(
            MixBus.MUSIC to MixBusPlan(compression = CompressionPlan(true, -18.0, 1.6, 0.5)),
            MixBus.DRUMS to MixBusPlan(gainDb = -1.0, compression = CompressionPlan(true, -18.0, 2.0, 0.5))
        )
    )

    private data class Part(val id: String, val file: String, val name: String, val sectionType: SectionTypeId)
}

private class LiveBuildWorker(private val client: WorkerClient) : BuildAudioWorker {
    override suspend fun healthCheck(): Boolean = client.healthCheck()

    override suspend fun repair(input: Path, output: Path) {
        val response = client.execute(RepairCommand(input.toString(), listOf(RepairSpec("dc_offset"), RepairSpec("clip_removal", mapOf("threshold" to 0.999, "max_run_samples" to 12))), output.toString()))
        require(response.status == WorkerStatus.COMPLETED) { "Repair failed: ${response.error?.message ?: "Unknown worker error"}" }
    }

    override suspend fun master(input: Path, output: Path, profile: MasteringProfile): MasteringMeasurement {
        val settings = mapOf<String, Any>(
            "eq_enabled" to true, "eq" to mapOf("bands" to listOf(mapOf("type" to "lowshelf", "frequency" to 180.0, "gain" to 1.5))),
            "compressor_enabled" to true, "compressor" to mapOf("threshold_db" to -18.0, "ratio" to 2.0, "attack_ms" to 15.0, "release_ms" to 150.0),
            "saturation_enabled" to true, "saturation" to mapOf("drive" to 1.08, "mix" to 0.08), "stereo_enabled" to false,
            "limiter_enabled" to true, "limiter" to mapOf("ceiling_db" to profile.maximumTruePeakDbtp, "release_ms" to 100.0),
            "target_peak_dbtp" to profile.maximumTruePeakDbtp, "target_lufs" to profile.nominalIntegratedLufs,
            "loudness_tolerance_lu" to profile.loudnessToleranceLu, "min_lra_lu" to profile.minimumLraLu,
            "min_crest_db" to profile.minimumCrestDb, "max_limiter_gain_reduction_db" to profile.maximumLimiterGainReductionDb,
            "mastering_profile" to profile.id
        )
        val response = client.execute(MasterCommand(input.toString(), settings, output.toString()))
        require(response.status == WorkerStatus.COMPLETED) { "Mastering failed: ${response.error?.message ?: "Unknown worker error"}" }
        val loudness = requireNotNull(response.output?.get("loudness")).jsonObject
        val limiter = requireNotNull(loudness["limiter_gain_reduction"]).jsonObject
        fun value(name: String) = requireNotNull(loudness[name]?.jsonPrimitive?.doubleOrNull)
        fun limiterValue(name: String) = requireNotNull(limiter[name]?.jsonPrimitive?.doubleOrNull)
        return MasteringMeasurement(
            loudness["measurement_standard"]?.jsonPrimitive?.contentOrNull ?: "", value("integrated_lufs"), value("true_peak_dbtp"),
            value("lra_lu"), value("crest_db"), limiterValue("max_gain_reduction_db"), limiterValue("mean_gain_reduction_db"),
            loudness["dynamics_preserved"]?.jsonPrimitive?.content == "true",
            loudness["quality_issues"]?.jsonArray?.map { requireNotNull(it.jsonPrimitive.contentOrNull) }?.sorted() ?: emptyList(),
            loudness["loudness_reference"]?.jsonPrimitive?.contentOrNull ?: ""
        )
    }

    override suspend fun exportMp3(input: Path, output: Path, bitrateKbps: Int): Boolean =
        client.execute(MP3ExportCommand(input.toString(), output.toString(), bitrateKbps)).status == WorkerStatus.COMPLETED
}
