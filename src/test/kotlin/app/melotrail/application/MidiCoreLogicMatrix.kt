package app.melotrail.application

import app.melotrail.arrangement.core.MidiCoreSectionPolicy
import app.melotrail.arrangement.core.MidiCoreArrangementStyleCatalog
import app.melotrail.arrangement.core.MidiCoreBassDrumCoordination
import app.melotrail.arrangement.core.MidiCoreChordCompingPhrasePatterns
import app.melotrail.arrangement.core.MidiCoreDrumGenerator
import app.melotrail.arrangement.core.MidiCorePatternCatalog
import app.melotrail.arrangement.core.MidiCorePerformanceProfileCatalog
import app.melotrail.midi.adapter.JdkMidiReader
import app.melotrail.music.core.ProjectKeySpelling
import app.melotrail.music.core.ProjectScaleMode
import app.melotrail.music.core.ProjectTempo
import app.melotrail.project.AuthoritativeChordEvent
import app.melotrail.project.CandidateRole
import app.melotrail.project.MidiCoreAuthorityHasher
import app.melotrail.project.MidiCoreGeneratorInput
import app.melotrail.project.MidiCoreRoleActivity
import app.melotrail.project.MidiCoreProjectSchema
import app.melotrail.project.ProjectKey
import app.melotrail.project.ProjectSectionDefinition
import app.melotrail.project.adapter.MidiCoreArtifactStore
import app.melotrail.structure.MidiCoreBarOccurrencePlacement
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*

/** Developer evidence command. Calls the current use cases; never opens Logic or accesses user projects. */
internal object MidiCoreLogicMatrix {
    private val clock = Clock.fixed(Instant.parse("2026-09-11T00:00:00Z"), ZoneOffset.UTC)
    private const val SEED = 901L
    private val json = Json { prettyPrint = true }

    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 2) { "Usage: MidiCoreLogicMatrix <repository-root> <new-output-directory>" }
        val repository = Path.of(args[0]).toAbsolutePath().normalize()
        val build = LogicMatrixBuild.capture(repository)
        val output = prepare(Path.of(args[1]), build, beforePublish = {
            check(build == LogicMatrixBuild.capture(repository)) { "Build inputs changed during matrix preparation; staging retained." }
        })
        println("Prepared current Logic matrix: $output")
        println("Automated preparation only. Logic import/playback/reopen: PENDING_USER; coordinator checks are separate.")
    }

    fun prepare(
        output: Path,
        build: JsonObject,
        cases: List<LogicMatrixCase> = MidiCoreLogicMatrixFixtures.cases,
        beforePublish: () -> Unit = {},
    ): Path {
        require(cases.isNotEmpty() && cases.map { it.id }.distinct().size == cases.size)
        val destination = output.toAbsolutePath().normalize()
        requireSafeNewDirectory(destination)
        Files.createDirectories(destination.parent)
        // Only this newly allocated staging tree is written. Failed work stays visibly incomplete.
        val staging = Files.createTempDirectory(destination.parent, ".${destination.fileName}-incomplete-")
        val records = cases.map { prepareCase(staging, it, build) }
        val matrix = buildJsonObject {
            put("schema", "melotrail-logic-matrix-v1")
            put("fixtureKind", "owned-synthetic-compatibility-probes")
            put("build", build)
            put("automatedPreparation", "semantic-reimport-and-project-reopen-completed")
            put("coordinatorChecks", "PENDING_COORDINATOR")
            put("logicImportPlaybackReopen", "PENDING_USER")
            put("macOSVersion", JsonNull); put("logicVersion", JsonNull); put("reviewer", JsonNull)
            put("cases", JsonArray(records))
        }
        write(staging.resolve("matrix.json"), json.encodeToString(JsonObject.serializer(), matrix))
        write(staging.resolve("review.md"), review(records))
        val inventory = LogicMatrixBuild.treeHashes(staging, exclude = setOf("SHA256SUMS"))
        write(staging.resolve("SHA256SUMS"), inventory.entries.joinToString("\n", postfix = "\n") { "${it.value}  ${it.key}" })
        beforePublish()
        requireSafeNewDirectory(destination)
        // Same-filesystem rename, without REPLACE_EXISTING or deletion of a previous packet.
        Files.move(staging, destination)
        return destination
    }

    private fun prepareCase(root: Path, case: LogicMatrixCase, build: JsonObject): JsonObject {
        val directory = Files.createDirectory(root.resolve(case.id))
        val input = directory.resolve("source.mid")
        Files.write(input, case.sourceBytes, CREATE_NEW)
        val sourceHash = logicSha256(case.sourceBytes)
        val store = MidiCoreArtifactStore()
        val lifecycle = MidiCoreProjectLifecycle(store, clock)
        var session = expect<MidiCoreProjectLifecycleResult.Opened>(lifecycle.create(
            CreateMidiCoreProject(directory.resolve("project"), "Logic ${case.id}", "q02-${case.id}",
                "q02-${build.getValue("inputTreeSha256").jsonPrimitive.content}"),
        ), case, "create").session
        val initialDocument = Files.readAllBytes(session.root.resolve("project.json"))
        val imported = MidiCoreSourceImport(store).import(ImportMidiCoreSource(session, input))
        if (case.rejectedImport) {
            val rejected = expect<MidiCoreSourceImportResult.Rejected>(imported, case, "reject unsupported source")
            check(rejected.problem.code == MidiCoreSourceImportProblemCode.SINGLE_MELODY_TRACK_REQUIRED)
            check(initialDocument.contentEquals(Files.readAllBytes(session.root.resolve("project.json"))))
            check(case.sourceBytes.contentEquals(Files.readAllBytes(input)))
            return buildJsonObject {
                put("id", case.id); put("sourceSha256", sourceHash); put("result", "EXPECTED_IMPORT_REJECTION")
                put("reason", rejected.problem.code.name); put("instructions", case.instructions)
                put("exportManifestSha256", JsonNull)
            }
        }
        session = expect<MidiCoreSourceImportResult.Imported>(imported, case, "import").session
        val inspection = JdkMidiReader().inspect(input)
        session = expect<MidiCoreAuthorityResult.Confirmed>(MidiCoreMusicalAuthority(store).confirm(
            ConfirmMidiCoreAuthority(session, ProjectKey(ProjectKeySpelling.C, ProjectScaleMode.MAJOR), ProjectTempo(500_000), case.meter),
        ), case, "authority").session
        if (case.pad) session = expect<MidiCoreArrangementExtentResult.Confirmed>(MidiCoreArrangementExtent(store).confirm(
            ConfirmMidiCoreArrangementExtent(session, true),
        ), case, "explicit padding").session
        session = expect<MidiCoreStructureTimelineResult.Updated>(MidiCoreStructureTimeline(store).replace(
            ReplaceMidiCoreStructure(session,
                case.occurrences.map { ProjectSectionDefinition(it.sectionId, it.label) }.distinctBy { it.id },
                case.occurrences.map { MidiCoreBarOccurrencePlacement(it.id, it.sectionId, it.label, it.bars) }),
        ), case, "structure").session
        val chords = case.chords.ifEmpty {
            session.project.authority!!.occurrences.map { AuthoritativeChordEvent("${it.id}-c", it.id, "C", it.startTick, it.endTick) }
        }
        session = expect<MidiCoreAuthoritativeHarmonyResult.Updated>(MidiCoreAuthoritativeHarmony(store).replace(
            ReplaceMidiCoreHarmony(session, chords),
        ), case, "harmony").session
        session = if (case.explicitPatterns || case.terminalFill) acceptExplicitRoles(store, session, case) else acceptDraft(store, session, case)
        val accepted = session
        val song = expect<MidiCoreAcceptedSongAssemblyResult.Assembled>(MidiCoreAcceptedSongAssembly(artifacts = store).assemble(
            AssembleMidiCoreSong(accepted),
        ), case, "accepted assembly").review.song
        val protectedBefore = LogicMatrixBuild.treeHashes(accepted.root)
        fun export(current: MidiCoreProjectSession, id: String) = expect<MidiCoreMidiPackageExportResult.Exported>(
            MidiCoreMidiPackageExporter(artifacts = store, clock = clock,
                snapshotLifecycle = MidiCoreExportSnapshotLifecycle(store, clock)).export(ExportMidiCorePackage(current, snapshotId = id)),
            case, "export",
        ).packageResult
        val first = export(accepted, "q02-current")
        val reopened = expect<MidiCoreProjectLifecycleResult.Opened>(lifecycle.open(accepted.root), case, "project reopen").session
        check(reopened.project == first.session.project)
        val second = export(reopened, "q02-reopened")
        check(first.files.map { it.filename to it.sha256 } == second.files.map { it.filename to it.sha256 }) {
            "${case.id}: reopening changed MIDI output"
        }
        check(first.manifestSha256 == logicSha256(Files.readAllBytes(first.directory.resolve("manifest.json"))))
        protectedBefore.filterKeys { it != "project.json" }.forEach { (path, digest) ->
            check(logicSha256(Files.readAllBytes(accepted.root.resolve(path))) == digest) { "Protected artifact changed: $path" }
        }
        check(case.sourceBytes.contentEquals(Files.readAllBytes(input)))
        check(case.sourceBytes.contentEquals(Files.readAllBytes(store.verify(accepted.root, accepted.project.sourceMidi!!.original))))
        val packageDirectory = Files.createDirectory(directory.resolve("package"))
        (first.files.map { it.filename } + "manifest.json").forEach {
            Files.copy(first.directory.resolve(it), packageDirectory.resolve(it))
        }
        val semantic = LogicMatrixSemantics.verify(song, first, packageDirectory, input)
        val manifest = Json.parseToJsonElement(Files.readString(packageDirectory.resolve("manifest.json"))).jsonObject
        check(manifest.getValue("source").jsonObject.getValue("sha256").jsonPrimitive.content == sourceHash)
        val projectDocument = Json.parseToJsonElement(MidiCoreProjectSchema.encode(accepted.project)).jsonObject.getValue("project").jsonObject
        val record = buildJsonObject {
            put("id", case.id); put("result", "PREPARED_FOR_LOGIC"); put("instructions", case.instructions)
            put("sourceSha256", sourceHash); put("sourceFormat", inspection.sequence.source.format)
            put("sourceEndTick", inspection.sourceEndTick); put("lastNoteEndTick", inspection.lastNoteEndTick)
            put("arrangementEndTick", song.songEndTick); put("paddingConfirmed", case.pad)
            put("authoritySha256", MidiCoreAuthorityHasher.from(accepted.project).sha256)
            put("authority", manifest.getValue("authority"))
            put("selectedMelody", manifest.getValue("selectedMelody"))
            put("confirmedPlan", projectDocument["arrangementPlan"] ?: JsonNull)
            put("style", if (case.explicitPatterns || case.terminalFill) "explicit-role-patterns" else "steady-road"); put("seed", SEED)
            put("catalogVersions", buildJsonObject {
                put("style", MidiCoreArrangementStyleCatalog.VERSION)
                put("patterns", MidiCorePatternCatalog.VERSION)
                put("profiles", MidiCorePerformanceProfileCatalog.VERSION)
            })
            if (case.explicitPatterns || case.terminalFill) put("explicitSectionPolicy", buildJsonObject {
                put("density", 1.0)
                put("drumFillPatternId", if (case.terminalFill) JsonPrimitive("drums.fill.dusty-snare-roll") else JsonNull)
            })
            put("acceptedCandidates", manifest.getValue("acceptedCandidates"))
            put("roles", manifest.getValue("roles"))
            put("plannedRests", JsonArray(accepted.project.acceptedPlannedRests.map { rest -> buildJsonObject {
                put("occurrenceId", rest.occurrenceId); put("role", rest.role.name.lowercase()); put("authoritySha256", rest.authorityHash)
            } }))
            put("exportManifestSha256", first.manifestSha256)
            put("files", JsonArray(first.files.map { file -> buildJsonObject {
                put("filename", file.filename); put("sha256", file.sha256)
                put("semanticSha256", semantic.getValue(file.filename))
            } }))
            put("projectReopen", "verified-same-state-and-MIDI")
            put("logicImportPlaybackReopen", "PENDING_USER")
        }
        write(directory.resolve("case.json"), json.encodeToString(JsonObject.serializer(), record))
        return record
    }

    private fun acceptDraft(store: MidiCoreArtifactStore, initial: MidiCoreProjectSession, case: LogicMatrixCase): MidiCoreProjectSession {
        val planner = MidiCoreArrangementPlanProposalUseCase(store)
        val proposal = expect<MidiCoreArrangementPlanProposalResult.Proposed>(planner.propose(
            ProposeMidiCoreArrangementPlan(initial, "steady-road")), case, "propose").proposal
        var session = expect<MidiCoreArrangementPlanProposalResult.Confirmed>(planner.confirm(
            ConfirmMidiCoreArrangementPlanProposal(initial, proposal)), case, "confirm plan").session
        if (case.rests != LogicMatrixRests.NONE) {
            val plan = session.project.arrangementPlan!!
            val edited = plan.copy(occurrences = plan.occurrences.map { occurrence ->
                occurrence.copy(roleSettings = occurrence.roleSettings.map { settings ->
                    val rest = when (case.rests) {
                        LogicMatrixRests.ALL_GENERATED -> true
                        LogicMatrixRests.ALL_BASS -> settings.role == CandidateRole.BASS
                        LogicMatrixRests.INTRO_BASS -> settings.role == CandidateRole.BASS && occurrence.occurrenceId == "intro"
                        LogicMatrixRests.NONE -> false
                    }
                    if (rest) settings.copy(activity = MidiCoreRoleActivity.INACTIVE, density = 0)
                    else if (case.rests == LogicMatrixRests.INTRO_BASS && settings.role == CandidateRole.BASS)
                        settings.copy(activity = MidiCoreRoleActivity.SUPPORTING, density = 50)
                    else settings
                })
            })
            session = expect<MidiCoreArrangementPlanEditResult.Confirmed>(MidiCoreArrangementPlanEdit(store).confirm(
                ConfirmMidiCoreArrangementPlanEdit(session, edited)), case, "confirm rests").session
        }
        val completed = expect<MidiCoreArrangementDraftGenerationResult.Completed>(runBlocking {
            MidiCoreArrangementDraftGeneration(artifacts = store).generate(
                GenerateMidiCoreArrangementDraft(session, "steady-road", SEED, draftId = "q02-${case.id}"))
        }, case, "draft")
        return expect<MidiCoreArrangementDraftAcceptanceResult.Applied>(MidiCoreArrangementDraftAcceptance(
            artifacts = store, clock = clock, idFactory = { "q02-use" }, historyIdFactory = historyIds(),
        ).use(UseMidiCoreArrangementDraft(completed.session, completed.draft.id)), case, "Use").session
    }

    /** Existing explicit role-selection caller covers brief harmony and optional terminal fills. */
    private fun acceptExplicitRoles(store: MidiCoreArtifactStore, initial: MidiCoreProjectSession, case: LogicMatrixCase): MidiCoreProjectSession {
        var session = initial
        CandidateRole.entries.forEach { role ->
            val pattern = when (role) {
                CandidateRole.CHORDS -> "chords.rhythm.sustained"
                CandidateRole.BASS -> "bass.sustained-root"
                CandidateRole.DRUMS -> "drums.dusty-straight"
            }
            val profile = when (role) {
                CandidateRole.CHORDS -> "chords.sustained"
                CandidateRole.BASS -> "bass.sustained-sub-like"
                CandidateRole.DRUMS -> "drums.dusty"
            }
            val generated = expect<MidiCoreCandidateGenerationResult.Published>(runBlocking {
                MidiCoreCandidateGeneration(artifacts = store).generate(GenerateMidiCoreCandidate(
                    session, role, "verse-1", profile, pattern,
                    MidiCoreGeneratorInput("q02-explicit-roles", currentGeneratorVersion(role, case.terminalFill), pattern, SEED),
                    sectionPolicy = MidiCoreSectionPolicy(density = 1.0,
                        fillPatternId = if (case.terminalFill && role == CandidateRole.DRUMS) "drums.fill.dusty-snare-roll" else null),
                    candidateId = "q02-${role.name.lowercase()}",
                ))
            }, case, "explicit ${role.name} generation")
            session = expect<MidiCoreCandidateLifecycleResult.Updated>(MidiCoreCandidateReview(artifacts = store).accept(
                AcceptMidiCoreCandidate(generated.session, generated.candidate.id)), case, "explicit acceptance").session
        }
        return session
    }

    private fun review(records: List<JsonObject>): String = buildString {
        appendLine("# Current Logic compatibility matrix")
        appendLine()
        appendLine("Owned synthetic probes. Automated preparation is not Logic playback, musical acceptance or release approval. All Logic results are PENDING_USER. The fixed export timestamp is a determinism input, not a listening date.")
        appendLine()
        appendLine("Verify SHA256SUMS before and after review. matrix.json binds Git/build inputs, protected source bytes, authority, engines/seeds, export manifests, output bytes and semantic hashes. Keep this packet unchanged; record results in a separate copy of this form.")
        appendLine()
        appendLine("1. Record exact macOS/build and Logic versions, reviewer and date; identify this packet by matrix.json SHA-256. Record the listed app source/build identity separately from the Logic version.")
        appendLine("2. In a new Logic project, open each package/complete-song.mid at bar 1. Record whether Logic adopts or retains tempo/meter; verify 120 BPM and the listed meter. Do not shift a pickup or trim initial silence.")
        appendLine("3. In a separate empty project with the same tempo/meter, import package/melody.mid and every listed role file at bar 1. Compare to the complete file without playing both copies together. Verify Conductor/Melody/Chords/Bass/Drums order and channels 1/2/3/10, omitted roles and later entries.")
        appendLine("4. Assign consistent instruments and a drum kit. Compare source.mid to Melody note timing/pitch/velocity/release and supported controllers. Bank/program hints are deliberately omitted. Check section markers, unequal chords, initial/rest spans, fills and exact final release; marker display alone may be cosmetic.")
        appendLine("5. Play the full song, then solo/loop relevant roles and boundary bars. Record wrong drum hits, stuck or truncated notes, unexpected expression and final tail. Save, close, reopen in Logic and replay. A Melotrail project reopen or screenshot does not establish this step.")
        appendLine("6. Record PASS, CONDITIONAL PASS with exact action, or FAIL per case below, including exact bars/beats and evidence paths. Timing/note/channel/role corruption is FAIL. Leave anything unperformed pending.")
        appendLine()
        appendLine("macOS: ___; Logic: ___; reviewer/date: ___; matrix.json SHA-256: ___")
        appendLine()
        appendLine("| Case | PPQ / meter | Source / last note / arrangement end ticks | Check | Import / full play / Logic reopen | Notes/evidence |")
        appendLine("| --- | --- | --- | --- | --- | --- |")
        records.forEach { record ->
            val id = record.getValue("id").jsonPrimitive.content
            val ends = listOf("sourceEndTick", "lastNoteEndTick", "arrangementEndTick").joinToString(" / ") { record[it]?.jsonPrimitive?.content ?: "n/a" }
            val authority = record["authority"]?.jsonObject
            val timing = if (authority == null) "n/a" else "${authority.getValue("ppq").jsonPrimitive.content} / ${authority.getValue("meterNumerator").jsonPrimitive.content}/${1 shl authority.getValue("meterDenominatorExponent").jsonPrimitive.int}"
            val status = if (record.getValue("result").jsonPrimitive.content == "EXPECTED_IMPORT_REJECTION") "N/A — expected app rejection" else "PENDING / PENDING / PENDING"
            appendLine("| $id | $timing | $ends | ${record.getValue("instructions").jsonPrimitive.content} | $status | ___ |")
        }
        appendLine()
        appendLine("The 2026-08-28 result in docs/VALIDATION.md remains historical. These regenerated packages do not inherit its approval. The extra-note-track fixture is retained as an expected current import rejection; smf1-meta-only is its supported-contract counterpart.")
    }

    private fun historyIds(): () -> String { var index = 0; return { "q02-history-${index++}" } }
    private fun currentGeneratorVersion(role: CandidateRole, terminalFill: Boolean) = MidiCoreDrumGenerator.generatorVersion(
        MidiCoreBassDrumCoordination.generatorVersion(MidiCoreChordCompingPhrasePatterns.generatorVersion(
            "q02-explicit-v1-fill-${if (terminalFill && role == CandidateRole.DRUMS) "dusty" else "none"}-style-v${MidiCoreArrangementStyleCatalog.VERSION}-patterns-v${MidiCorePatternCatalog.VERSION}-profiles-v${MidiCorePerformanceProfileCatalog.VERSION}", role), role), role)
    private inline fun <reified T> expect(value: Any, case: LogicMatrixCase, stage: String): T =
        value as? T ?: error("${case.id}: $stage failed: $value")
    private fun write(path: Path, content: String) { Files.writeString(path, content, CREATE_NEW) }

    private fun requireSafeNewDirectory(path: Path) {
        require(Files.notExists(path, NOFOLLOW_LINKS)) { "Matrix destination already exists: $path" }
        var ancestor: Path? = path.parent
        while (ancestor != null) {
            require(!Files.isSymbolicLink(ancestor)) { "Matrix destination has a symlink ancestor: $ancestor" }
            ancestor = ancestor.parent
        }
    }
}
