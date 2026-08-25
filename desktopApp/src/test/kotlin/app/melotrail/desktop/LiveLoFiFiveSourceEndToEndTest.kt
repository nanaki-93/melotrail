package app.melotrail.desktop

import app.melotrail.application.ApproveEnhancementRequest
import app.melotrail.application.ArrangementPlannerKind
import app.melotrail.application.BuildApplicationService
import app.melotrail.application.BuildAudioWorker
import app.melotrail.application.BuildSongRequest
import app.melotrail.application.CodecPreviewEvidence
import app.melotrail.application.CodecPreviewStatus
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
import app.melotrail.application.DeliveryCodec
import app.melotrail.application.EnsembleCohesionPlannerKind
import app.melotrail.application.GenerateArrangementRequest
import app.melotrail.application.GenerateEnsembleCohesionRequest
import app.melotrail.application.GenerateHumanizationRequest
import app.melotrail.application.ImportSongPart
import app.melotrail.application.MidiQualityStatus
import app.melotrail.application.MeasureSourceTimingRequest
import app.melotrail.application.NormalizePartRequest
import app.melotrail.application.ProgressSink
import app.melotrail.application.QualityDebugPair
import app.melotrail.application.QualityReviewArtifactKind
import app.melotrail.application.QualityReviewEvidenceService
import app.melotrail.application.SaveStructureRequest
import app.melotrail.application.SelectMidiFeelRequest
import app.melotrail.application.SetHarmonyProgression
import app.melotrail.application.SourceTimingAlignmentApplicationService
import app.melotrail.application.SourceTimingEvidenceApplicationService
import app.melotrail.application.TransposePartRequest
import app.melotrail.application.UpdateCompositionSettings
import app.melotrail.application.AnalyzePartRequest
import app.melotrail.application.AlignSourceTimingRequest
import app.melotrail.application.ApplyMixRequest
import app.melotrail.arrangement.ArrangementRole
import app.melotrail.arrangement.ArrangementRoleSelection
import app.melotrail.arrangement.ArtifactRef
import app.melotrail.arrangement.BridgeElement
import app.melotrail.arrangement.DetailedArrangement
import app.melotrail.arrangement.DetailedArrangementInput
import app.melotrail.arrangement.DetailedArrangementPlanner
import app.melotrail.arrangement.DrumFillPlacement
import app.melotrail.arrangement.DrumsInstrumentPlan
import app.melotrail.arrangement.EnhancementIntensity
import app.melotrail.arrangement.EnsembleCohesionEnhancementIntensity
import app.melotrail.arrangement.GlobalSongPlanner
import app.melotrail.arrangement.InstrumentRegistryLoader
import app.melotrail.arrangement.LocalQwenDetailedArrangementPlanner
import app.melotrail.arrangement.LocalQwenGlobalSongPlanner
import app.melotrail.arrangement.LoFiSectionPatternPolicy
import app.melotrail.arrangement.CompressionPlan
import app.melotrail.arrangement.EqBandPlan
import app.melotrail.arrangement.FilterPlan
import app.melotrail.arrangement.MixBus
import app.melotrail.arrangement.MixBusPlan
import app.melotrail.arrangement.MixPlan
import app.melotrail.arrangement.MixTrackPlan
import app.melotrail.arrangement.MidiAnalysisInput
import app.melotrail.arrangement.PadInstrumentPlan
import app.melotrail.arrangement.ProjectStore
import app.melotrail.arrangement.SectionTypeId
import app.melotrail.arrangement.SelectedMidiArtifactResolver
import app.melotrail.arrangement.SharedRoomPlan
import app.melotrail.arrangement.SfizzInstrumentRenderer
import app.melotrail.arrangement.SongPlan
import app.melotrail.arrangement.SongPlanningInput
import app.melotrail.arrangement.WorkflowArtifactReference
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
import app.melotrail.worker.CodecPreviewCommand
import app.melotrail.worker.MasterCommand
import app.melotrail.worker.RepairCommand
import app.melotrail.worker.RepairSpec
import app.melotrail.worker.WorkerClient
import app.melotrail.worker.WorkerStatus
import app.melotrail.preparation.ExplicitTimingWindow
import app.melotrail.preparation.MidiTimeMappingReview
import app.melotrail.preparation.MidiTimeMappingReviewState
import app.melotrail.preparation.SourceBeatTickAnchor
import app.melotrail.preparation.SourceGrooveTemplateStatus
import app.melotrail.preparation.SourceTimingDecision
import app.melotrail.preparation.WorkerSourceTimingBoundary
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Instant
import javax.sound.midi.MidiSystem
import javax.sound.sampled.AudioSystem
import kotlin.math.abs
import kotlin.test.assertTrue

/**
 * Explicit live proof for the supplied five WAV sources. It is opt-in because
 * it invokes the local Basic Pitch, Qwen, sfizz, and mastering runtimes and
 * writes the canonical project into data/audio.
 */
class LiveLoFiFiveSourceEndToEndTest {
    @Test
    fun `five source C major lo-fi song reaches an exported WAV`() = runBlocking {
        assumeTrue(System.getenv("MELOTRAIL_RUN_LIVE_E2E") == "1", "Set MELOTRAIL_RUN_LIVE_E2E=1 to run local audio/model integration.")
        val workspace = generateSequence(Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()) { it.parent }
            .firstOrNull { Files.isDirectory(it.resolve("data/audio/input")) && Files.isDirectory(it.resolve("sounds")) }
            ?: error("Unable to locate the MeloTrail workspace from the Gradle test working directory.")
        val root = workspace.resolve("data/audio")
        val input = root.resolve("input")
        val output = root.resolve("output")
        val resumePreparedProject = System.getenv("MELOTRAIL_RESUME_LIVE_E2E") == "1"
        val sources = listOf(
            Part("intro", "intro-C.wav", "Intro", SectionTypeId.INTRO, 4),
            Part("verse", "verse-c.wav", "Verse", SectionTypeId.VERSE, 4),
            Part("chorus", "chorus-C.wav", "Chorus", SectionTypeId.CHORUS, 8),
            Part("bridge", "bridge-C.wav", "Bridge", SectionTypeId.BRIDGE, 4),
            Part("outro", "outro-C.wav", "Outro", SectionTypeId.OUTRO, 4)
        )
        require(sources.all { Files.isRegularFile(input.resolve(it.file)) }) { "Expected intro-C.wav, verse-c.wav, chorus-C.wav, bridge-C.wav, and outro-C.wav in data/audio/input." }
        require(resumePreparedProject == Files.exists(root.resolve("project.json"))) {
            if (resumePreparedProject) "Live E2E resume requires an existing data/audio project."
            else "Live E2E refuses to replace an existing data/audio project."
        }

        val client = WorkerClient(logger = DefaultLogger(), errorReporter = ErrorReporter(DefaultLogger()))
        assertTrue(client.healthCheck(), "The local worker must be running at http://127.0.0.1:8081.")
        val libraryRoot = workspace.resolve("sounds")
        val renderer = SfizzInstrumentRenderer(InstrumentRegistryLoader(libraryRoot))
        val projects = DesktopServiceComposition.projectService()
        val arrangements = DefaultArrangementApplicationService(
            qwenGlobalPlanner = LiveLoFiRhythmGlobalPlanner(),
            qwenDetailedPlanner = LiveLoFiRhythmDetailedPlanner(),
            libraryRoot = libraryRoot
        )
        val cohesion = DefaultEnsembleCohesionApplicationService()

        if (!resumePreparedProject) {
            projects.create(CreateProjectRequest(root, name = "C Major Lo-fi Five Source E2E"))
            projects.updateCompositionSettings(UpdateCompositionSettings(
                root, 0,
                CompositionSettingsInput(
                    name = "C Major Lo-fi Five Source E2E",
                    key = MusicalKey(PitchClass.of(PitchSpelling.C), ScaleModeId.MAJOR),
                    tempo = Tempo(75.0), timeSignature = TimeSignature(4, 4),
                    profile = CompositionProfileRef("lofi", 1), mood = MoodRef("warm", 1)
                )
            ))
            applyHarmony(projects, root, SectionTypeId.INTRO, "lofi-major-warm-intro-v1")
            applyHarmony(projects, root, SectionTypeId.VERSE, "lofi-major-classic-v1")
            applyHarmony(projects, root, SectionTypeId.CHORUS, "lofi-major-open-chorus-v1")
            applyHarmony(projects, root, SectionTypeId.BRIDGE, "lofi-major-reflective-bridge-v1")
            applyHarmony(projects, root, SectionTypeId.OUTRO, "lofi-major-soft-outro-v1")

            sources.forEach { part ->
                projects.importSongPart(ImportSongPart(root, part.id, input.resolve(part.file), part.name, part.sectionType))
            }
            prepareSourcesInProjectKey(projects, root, sources.map(Part::id))
            alignSourcesToDeclaredBars(client, root, sources)

            processSources(projects, root, sources.map(Part::id))
            projects.saveStructure(SaveStructureRequest(root, listOf("intro", "verse", "verse", "chorus", "bridge", "verse", "outro")))

            approveSourceCritic(root)
        } else {
            DefaultSourceSongCriticApplicationService().requireApprovedMelody(root)
        }
        arrangeLoFiRhythmSection(arrangements, root)
        applyCohesion(cohesion, root)
        critiqueAndEnhanceSong(root)
        DefaultHumanizationApplicationService().generate(GenerateHumanizationRequest(root))

        val mix = DefaultMixApplicationService()
        arrangements.renderApprovedStems(root, renderer, ProgressSink.None)
        mix.apply(ApplyMixRequest(root, loFiMixPlan()), ProgressSink.None)
        mix.approve(root)
        val build = DefaultBuildApplicationService(arrangements, mix, renderer, LiveBuildWorker(client), cohesion)
            .build(BuildSongRequest(root, enableLoFi = true, enableMp3 = false), ProgressSink.None)
        assertTrue(Files.isRegularFile(build.master) && Files.size(build.master) > 44, "Expected a rendered master WAV.")
        assertTrue(Files.isRegularFile(output.resolve("master.wav")))
        assertStrictProductionEvidence(root, mix)
        publishPendingLiveListeningReview(root, sources.first().id)
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
        val backup = output.resolve("master-before-full-song-retry.wav")
        if (Files.isRegularFile(currentMaster) && !Files.exists(backup)) Files.copy(currentMaster, backup)

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
        mix.approve(root)
        val build = DefaultBuildApplicationService(arrangements, mix, renderer, LiveBuildWorker(client), cohesion)
            .build(BuildSongRequest(root, enableLoFi = true, enableMp3 = false), ProgressSink.None)
        assertTrue(Files.isRegularFile(build.master) && Files.size(build.master) > 44, "Expected a rendered master WAV.")
        assertTrue(Files.isRegularFile(currentMaster))
        assertStrictProductionEvidence(root, mix)
        publishPendingLiveListeningReview(root, "intro")
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

    /** The user-declared 4/8-bar lengths explicitly approve each source's beat phase and bounded pickup/tail mapping. */
    private suspend fun alignSourcesToDeclaredBars(client: WorkerClient, root: Path, sources: List<Part>) {
        val timing = SourceTimingEvidenceApplicationService(WorkerSourceTimingBoundary(client))
        val alignment = SourceTimingAlignmentApplicationService()
        sources.forEach { source ->
            val timingEvidence = timing.measure(MeasureSourceTimingRequest(root, source.id)).report
            val bodyBars = source.bars - 1
            val bodyBeats = Math.multiplyExact(bodyBars, 4)
            val measuredBeats = timingEvidence.beats.take(bodyBeats + 1)
            val project = ProjectStore.read(root)
            val part = project.parts.single { it.id == source.id }
            val transposedReference = requireNotNull(part.midi?.transposed) { "Source '${source.file}' was not transposed before timing alignment." }
            val transposed = root.resolve(transposedReference)
            val sequence = MidiSystem.getSequence(transposed.toFile())
            val durationSeconds = wavDurationSeconds(root.resolve(part.file))
            fun sourceTick(seconds: Double): Long = (seconds / durationSeconds * sequence.tickLength).toLong()
                .coerceIn(1, sequence.tickLength - 1)
            val expectedDurationSeconds = source.bars * 4 * 60.0 / 75.0
            val hasMeasuredGrid = measuredBeats.size == bodyBeats + 1
            val anchors = if (hasMeasuredGrid) {
                measuredBeats.mapIndexed { index, beat -> SourceBeatTickAnchor(index, sourceTick(beat.timeSeconds)) }
            } else {
                // Sparse intros can have a correct exported duration but too few
                // detectable onsets. The user's explicit bar declaration then
                // authorizes the same fixed 75 BPM grid, retaining a one-beat
                // pickup and three-beat tail without inventing a tempo change.
                require(abs(durationSeconds - expectedDurationSeconds) <= 0.02) {
                    "Source '${source.file}' has only ${measuredBeats.size} detected anchors and is ${"%.3f".format(durationSeconds)}s; " +
                        "export exactly ${"%.3f".format(expectedDurationSeconds)}s for its declared ${source.bars}-bar structure."
                }
                (0..bodyBeats).map { index ->
                    SourceBeatTickAnchor(index, sourceTick((index + 1) * 60.0 / 75.0))
                }
            }
            require(anchors.zipWithNext().all { (left, right) -> left.sourceMidiTick < right.sourceMidiTick }) {
                "Source '${source.file}' timing anchors are not strictly increasing."
            }
            val pickupTicks = sequence.resolution.toLong()
            val tailTicks = Math.multiplyExact(source.bars, 4).toLong() * sequence.resolution - pickupTicks - bodyBeats.toLong() * sequence.resolution
            require(tailTicks > 0 && anchors.first().sourceMidiTick > 0 && anchors.last().sourceMidiTick < sequence.tickLength) {
                "Source '${source.file}' cannot form the reviewed pickup/body/tail timing windows."
            }
            alignment.align(AlignSourceTimingRequest(root, source.id, SourceTimingDecision(
                partId = source.id,
                occurrenceId = "${source.id}-declared-length",
                sourceTimingReport = requireNotNull(part.sourceTimingEvidence).report,
                sourceMidi = ArtifactRef(transposedReference, digest(transposed)),
                sourcePpq = sequence.resolution,
                targetPpq = sequence.resolution,
                targetTempoBpm = 75,
                targetMeterNumerator = 4,
                targetMeterDenominator = 4,
                sourceDownbeatBeatIndex = 0,
                sourceBeats = anchors,
                targetStartBar = 1,
                targetBarCount = bodyBars,
                pickup = ExplicitTimingWindow(0, anchors.first().sourceMidiTick, pickupTicks),
                tail = ExplicitTimingWindow(anchors.last().sourceMidiTick, sequence.tickLength, tailTicks),
                acceptSourceGroove = hasMeasuredGrid && timingEvidence.groove.status == SourceGrooveTemplateStatus.MEASURED,
                review = MidiTimeMappingReview(MidiTimeMappingReviewState.APPROVED, "user", Instant.now().toString())
            )))
        }
    }

    private fun wavDurationSeconds(path: Path): Double = AudioSystem.getAudioInputStream(path.toFile()).use { stream ->
        stream.frameLength.toDouble() / stream.format.frameRate.toDouble()
    }.also { duration -> require(duration.isFinite() && duration > 0.0) { "Source WAV duration is invalid." } }

    private fun approveSourceCritic(root: Path) {
        val sourceCritic = DefaultSourceSongCriticApplicationService()
        val sourceReport = sourceCritic.run(root)
        require(!sourceReport.report.hasHardBlockers && sourceReport.report.issues.none { it.severity.name == "BLOCKING" }) {
            "Live E2E requires a quality-certified source; repair all current source critic blockers before arranging."
        }
        sourceCritic.approve(root)
    }

    private suspend fun arrangeLoFiRhythmSection(
        arrangements: DefaultArrangementApplicationService,
        root: Path
    ) {
        val roleSelections = listOf(
            ArrangementRoleSelection(ArrangementRole.MELODY),
            ArrangementRoleSelection(ArrangementRole.HARMONY, pinnedInstrumentId = "versilian-vcsl-keys-grand-piano-k"),
            ArrangementRoleSelection(ArrangementRole.DRUMS)
        )
        val existing = runCatching { arrangements.load(root) }.getOrNull()
        if (existing == null || !existing.approved || existing.approvalRequired || existing.stale) {
            arrangements.generate(GenerateArrangementRequest(root, ArrangementPlannerKind.QWEN, roleSelections = roleSelections))
            arrangements.approve(root)
        }
        val arrangementPlan = Files.readString(root.resolve("arrangement_plan.json"))
        val requiredInstruments = setOf("piano", "drums", "pad")
        assertTrue(requiredInstruments.all { "\"kind\": \"$it\"" in arrangementPlan }, "Every section must include melody, drums, and chord keys.")
        assertTrue("\"kind\": \"bass\"" !in arrangementPlan && "\"kind\": \"strings\"" !in arrangementPlan,
            "The reduced lo-fi arrangement must not contain bass or strings.")
        assertTrue(Regex("\"fillPlacement\"\\s*:\\s*\"last_bar\"").findAll(arrangementPlan).count() == 7,
            "Every song section must end with a selected drum fill.")
        val required = arrangements.generateRequiredMidi(root)
        val requiredByInstrument = required.artifacts.associateBy { it.instrument }
        setOf("drums", "pad").forEach { instrument ->
            assertTrue(requireNotNull(requiredByInstrument[instrument]) { "Missing generated $instrument MIDI." }.events > 0, "$instrument MIDI must contain playable events.")
        }
        assertTrue("bass" !in requiredByInstrument, "Bass MIDI must not be generated for this arrangement.")
        arrangements.approveCoreArrangement(root)
    }

    private suspend fun applyCohesion(cohesion: DefaultEnsembleCohesionApplicationService, root: Path) {
        val cohesionDraft = cohesion.generate(GenerateEnsembleCohesionRequest(root, EnsembleCohesionPlannerKind.QWEN, EnsembleCohesionEnhancementIntensity.BALANCED))
        cohesionDraft.boundaries.forEach { boundary -> cohesion.reviewBoundary(root, boundary.outgoingInstanceId, boundary.incomingInstanceId) }
        cohesion.approve(root)
    }

    private suspend fun critiqueAndEnhanceSong(root: Path) {
        DefaultFullSongCriticApplicationService().run(root)
        val enhancement = DefaultFullSongEnhancementApplicationService(app.melotrail.arrangement.LocalQwenFullSongEnhancementPlanner())
        val candidate = enhancement.generateCandidate(root)
        when (candidate.selection.name) {
            "UNRESOLVED" -> {
                require(candidate.candidateAvailable) { "Full-Song Enhance produced unresolved evidence; repair or retry instead of bypassing it." }
                enhancement.approve(root)
            }
            "NO_OP" -> Unit
            else -> error("Unexpected Full-Song Enhance selection: ${candidate.selection}")
        }
    }

    private fun applyHarmony(projects: app.melotrail.application.ProjectApplicationService, root: Path, section: SectionTypeId, template: String) {
        val current = projects.getHarmony(app.melotrail.application.GetHarmony(root))
        projects.setHarmonyProgression(SetHarmonyProgression(root, current.projectRevision, requireNotNull(current.revision), app.melotrail.harmony.SectionTypeId(section.value), HarmonyTemplateId(template)))
    }

    /** Assert only code-owned production evidence; commercial and human-listening decisions remain separate. */
    private fun assertStrictProductionEvidence(root: Path, mix: DefaultMixApplicationService) {
        val snapshot = mix.load(root)
        require(snapshot.approval != null && snapshot.report?.commercialReady == true) {
            "Live E2E must not continue with an unresolved production-mix blocker."
        }
        val lowEnd = requireNotNull(snapshot.report?.lowEndInteraction)
        require(!lowEnd.severeUnresolvedOverlap && !lowEnd.pumpingDetected && lowEnd.timingPreserved && lowEnd.durationPreserved) {
            "Live E2E low-end evidence is unresolved."
        }
        val release = app.melotrail.application.DefaultReleaseReviewApplicationService().load(root)
        require(release.mastering != null && release.codecPreviews.map { it.codec }.toSet() == DeliveryCodec.entries.toSet() &&
            release.codecPreviews.none { it.status == CodecPreviewStatus.BLOCKED }) {
            "Live E2E master or local codec preview evidence is incomplete."
        }
    }

    /** Publish a pending review form and immutable MIDI/WAV copies; this does not claim that anyone listened. */
    private fun publishPendingLiveListeningReview(root: Path, partId: String) {
        val project = ProjectStore.read(root)
        val selected = SelectedMidiArtifactResolver().resolve(root, project, partId)
        val approved = DefaultSourceSongCriticApplicationService().requireApprovedMelody(root)
        val reference = { path: Path -> WorkflowArtifactReference(root.relativize(path).toString().replace('\\', '/'), digest(path)) }
        QualityReviewEvidenceService().publishPending(root, listOf(
            QualityDebugPair("prepared-connected", QualityReviewArtifactKind.MIDI, reference(selected.path), approved.connectedMidi),
            QualityDebugPair("dry-master", QualityReviewArtifactKind.WAV, reference(root.resolve("mix/dry.wav")), reference(root.resolve("output/master.wav")))
        ), listOf("human listening session not recorded", "audio-device result unverified"))
    }

    private fun digest(path: Path): String = MessageDigest.getInstance("SHA-256")
        .digest(Files.readAllBytes(path)).joinToString("") { "%02x".format(it) }

    private fun loFiMixPlan() = MixPlan(
        tracks = mapOf(
            "piano" to MixTrackPlan(gainDb = -2.0, filter = FilterPlan(70.0, 9_000.0),
                eq = listOf(EqBandPlan(280.0, -1.5, 0.8)), compression = CompressionPlan(true, -20.0, 1.8, 0.5), reverbSend = 0.20, stereoWidth = 0.9),
            "drums" to MixTrackPlan(gainDb = -6.0, filter = FilterPlan(35.0, 10_000.0),
                eq = listOf(EqBandPlan(3_500.0, -1.5, 1.0)), compression = CompressionPlan(true, -20.0, 2.2, 0.5), reverbSend = 0.08, stereoWidth = 0.9, bus = MixBus.DRUMS),
            "pad" to MixTrackPlan(gainDb = -9.0, pan = -0.08, filter = FilterPlan(150.0, 6_500.0),
                eq = listOf(EqBandPlan(2_400.0, -1.5, 0.9)),
                compression = CompressionPlan(true, -23.0, 1.7), reverbSend = 0.22, stereoWidth = 1.08)
        ),
        room = SharedRoomPlan(enabled = true, decaySeconds = 0.85, mix = 0.12),
        buses = mapOf(
            MixBus.MUSIC to MixBusPlan(compression = CompressionPlan(true, -18.0, 1.6, 0.5)),
            MixBus.DRUMS to MixBusPlan(gainDb = -1.0, compression = CompressionPlan(true, -18.0, 2.0, 0.5))
        )
    )

    private data class Part(val id: String, val file: String, val name: String, val sectionType: SectionTypeId, val bars: Int) {
        init { require(bars > 1) }
    }
}

/** Keep both rhythm layers active in every occurrence while retaining Qwen's bounded energy and section arc. */
private class LiveLoFiRhythmGlobalPlanner(
    private val delegate: GlobalSongPlanner = LocalQwenGlobalSongPlanner()
) : GlobalSongPlanner {
    override fun plan(input: SongPlanningInput): SongPlan {
        val planned = delegate.plan(input)
        val alwaysActive = listOf("piano", "drums", "pad")
        require(alwaysActive.all { it in input.allowedInstruments }) { "Live lo-fi rhythm policy requires piano, drums, and pad." }
        return planned.copy(sections = planned.sections.map { section ->
            section.copy(instrumentProgression = alwaysActive)
        })
    }
}

/** Apply the reviewed beat-relative catalogs after Qwen selects the rest of each bounded detail plan. */
private class LiveLoFiRhythmDetailedPlanner(
    private val delegate: DetailedArrangementPlanner = LocalQwenDetailedArrangementPlanner()
) : DetailedArrangementPlanner {
    override fun plan(input: DetailedArrangementInput): DetailedArrangement {
        val planned = delegate.plan(input)
        return planned.copy(sections = planned.sections.map { section ->
            section.copy(
                instruments = section.instruments.map { instrument ->
                    when (instrument) {
                        is DrumsInstrumentPlan -> instrument.copy(
                            fillLastBar = true,
                            fillPlacement = DrumFillPlacement.LAST_BAR,
                            pattern = LoFiSectionPatternPolicy.drumGroove(section.role),
                            fillPattern = LoFiSectionPatternPolicy.drumFill(section.role)
                        )
                        is PadInstrumentPlan -> instrument.copy(
                            rhythmPattern = LoFiSectionPatternPolicy.chordRhythm(section.role)
                        )
                        else -> instrument
                    }
                },
                transitionOut = section.transitionOut.copy(
                    bridge = section.transitionOut.bridge?.let { bridge ->
                        bridge.copy(elements = bridge.elements.filterNot { it == BridgeElement.BASS_PICKUP }
                            .ifEmpty { listOf(BridgeElement.DRUM_FILL) })
                    }
                )
            )
        }).also { it.requireValid(input) }
    }
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

    /** Use the worker's actual local preview path during an opt-in integration run. */
    override suspend fun codecPreviews(input: Path, outputDirectory: Path, profile: MasteringProfile): List<CodecPreviewEvidence> {
        val selectedMaster = input.toAbsolutePath().normalize()
        val directory = outputDirectory.toAbsolutePath().normalize()
        val root = checkNotNull(checkNotNull(directory.parent).parent)
        require(selectedMaster.startsWith(root)) { "Live codec preview input must be the selected master." }
        Files.createDirectories(directory)
        val masterHash = digest(selectedMaster)
        return DeliveryCodec.entries.map { codec ->
            val suffix = codec.name.lowercase()
            val encoded = directory.resolve("master-preview.$suffix")
            val decoded = directory.resolve("master-preview.$suffix.decoded.wav")
            val response = client.execute(CodecPreviewCommand(selectedMaster.toString(), suffix, encoded.toString(), decoded.toString()))
            require(response.status == WorkerStatus.COMPLETED) { "${codec.name} codec preview failed: ${response.error?.message ?: "Unknown worker error"}" }
            val evidence = requireNotNull(response.output) { "${codec.name} codec preview returned no evidence" }
            if (evidence["status"]?.jsonPrimitive?.contentOrNull == "unavailable") {
                CodecPreviewEvidence(codec, CodecPreviewStatus.UNVERIFIED, masterHash,
                    detail = evidence["detail"]?.jsonPrimitive?.contentOrNull ?: "Local $codec preview is unavailable; no platform claim is implied.")
            } else {
                require(evidence["status"]?.jsonPrimitive?.contentOrNull == "measured" && Files.isRegularFile(encoded) && Files.isRegularFile(decoded)) {
                    "${codec.name} codec preview did not publish encode/decode evidence."
                }
                val truePeak = requireNotNull(evidence["truePeakDbtp"]?.jsonPrimitive?.doubleOrNull)
                val clipping = requireNotNull(evidence["clippingSampleCount"]?.jsonPrimitive?.longOrNull?.toInt())
                val status = if (truePeak <= profile.maximumTruePeakDbtp + 0.05 && clipping == 0) CodecPreviewStatus.VERIFIED else CodecPreviewStatus.BLOCKED
                CodecPreviewEvidence(codec, status, masterHash, reference(root, encoded), reference(root, decoded), truePeak, clipping,
                    evidence["detail"]?.jsonPrimitive?.contentOrNull ?: "Local $codec preview measured the selected master.")
            }
        }
    }

    override suspend fun exportMp3(input: Path, output: Path, bitrateKbps: Int): Boolean =
        client.execute(MP3ExportCommand(input.toString(), output.toString(), bitrateKbps)).status == WorkerStatus.COMPLETED

    /** Create project-relative debug evidence only after hashing worker output. */
    private fun reference(root: Path, path: Path) = WorkflowArtifactReference(root.relativize(path).toString().replace('\\', '/'), digest(path))
    private fun digest(path: Path): String = MessageDigest.getInstance("SHA-256")
        .digest(Files.readAllBytes(path)).joinToString("") { "%02x".format(it) }
}
