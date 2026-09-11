package app.melotrail.application

import app.melotrail.midi.adapter.JdkMidiReader
import app.melotrail.midi.domain.MidiNoteEvent
import app.melotrail.music.core.ProjectMeter
import app.melotrail.project.AuthoritativeChordEvent
import app.melotrail.project.CandidateRole
import app.melotrail.project.ProjectSectionDefinition
import app.melotrail.project.adapter.MidiCoreArtifactStore
import app.melotrail.structure.MidiCoreBarOccurrencePlacement
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class MidiCoreMusicalEvaluationTest {
    @TempDir lateinit var root: Path
    private val store = MidiCoreArtifactStore()

    @Test
    fun `real command freezes before generation and reproduces packages without changing supplied work`() {
        val session = project(M01ComparisonFixtures.cases.first())
        val input = input(session)
        val before = hashes(session.root)
        val request = request(listOf(input))
        val frozen = root.resolve("frozen")
        assertEquals(0, command("freeze", request.toString(), frozen.toString()))
        assertEquals(before, hashes(session.root))
        val set = frozenSet(frozen)
        assertEquals(5, set.missingFinalSongs)
        assertEquals(3, set.missingUnseenSongs)
        val frozenProject = store.openProject(frozen.resolve("development/${input.case.id}/input"))
        assertTrue(frozenProject.candidates.isEmpty())
        assertTrue(frozenProject.exportSnapshots.isEmpty())
        assertEquals(session.project.authority, frozenProject.authority)
        assertEquals(session.project.arrangementPlan, frozenProject.arrangementPlan)
        val frozenBefore = hashes(frozen)
        val freezeAgain = root.resolve("frozen-again")
        assertEquals(0, command("freeze", request.toString(), freezeAgain.toString()))
        assertEquals(frozenBefore, hashes(freezeAgain))
        val hash = evaluationFileSha256(frozen.resolve("frozen-set.json"))
        val first = root.resolve("first")
        val second = root.resolve("second")
        assertEquals(0, command("export", frozen.toString(), hash, first.toString()))
        assertEquals(0, command("export", frozen.toString(), hash, second.toString()))
        val report = report(first)
        val result = report.cases.single()
        assertEquals("PREPARED", result.status, result.problem)
        assertFalse(result.baselineAvailable)
        assertTrue(result.candidateIds.isNotEmpty())
        assertTrue(result.generatorVersions.values.any { "drums-transitions-v2" in it })
        assertEquals(report, report(second))
        assertEquals(hashes(first.resolve("development/${input.case.id}/current")), hashes(second.resolve("development/${input.case.id}/current")))
        result.files.forEach { (name, digest) ->
            assertEquals(digest, evaluationFileSha256(first.resolve("development/${input.case.id}/$name")))
        }
        val scoreFile = first.resolve("development/${input.case.id}/scores.json")
        val form = evaluationJson.parseToJsonElement(Files.readString(scoreFile)).jsonObject
        assertEquals(JsonNull, form["reviewer"])
        assertEquals(JsonNull, form["firstDraftToUseMinutes"])
        assertEquals(JsonNull, form["decision"])
        assertEquals(JsonNull, form["baselineScores"])
        assertEquals(7, form.getValue("scores").jsonObject.size)
        assertTrue(form.getValue("scores").jsonObject.values.all { it.jsonObject.values.all { value -> value == JsonNull } })
        val review = Files.readString(first.resolve("review.md"))
        assertTrue("Missing unseen songs: 3/3" in review && "UNSCORED" in review && "baseline unavailable" in review)
        assertFalse(review.contains(root.toString()))
        val sourceNotes = notes(session.root.resolve("source/original.mid"))
        assertEquals(sourceNotes, notes(first.resolve("development/${input.case.id}/current/melody.mid")))
        assertEquals(before, hashes(session.root))
        assertEquals(frozenBefore, hashes(frozen))
    }

    @Test
    fun `accepted baseline is copied exactly and source candidates acceptances and exports are preserved`() {
        val original = project(M01ComparisonFixtures.cases.first())
        val request = request(listOf(input(original)))
        val frozen = root.resolve("initial-freeze")
        val engine = MidiCoreMusicalEvaluation()
        val hash = engine.freeze(request, frozen)
        val first = root.resolve("initial-export")
        val firstResult = engine.export(frozen, hash, first).cases.single()
        assertEquals("PREPARED", firstResult.status, firstResult.problem)
        val acceptedRoot = first.resolve("development/${original.project.id.value}/project")
        val accepted = MidiCoreProjectSession(acceptedRoot, store.openProject(acceptedRoot))
        assertTrue(accepted.project.acceptances.isNotEmpty())
        val before = hashes(acceptedRoot)
        val baselineInput = input(accepted).let { it.copy(case = it.case.copy(baselineSnapshotId = firstResult.snapshotId)) }
        val comparison = root.resolve("comparison-freeze")
        val comparisonHash = engine.freeze(request(listOf(baselineInput), "baseline-request"), comparison)
        val output = root.resolve("comparison-export")
        val result = engine.export(comparison, comparisonHash, output).cases.single()
        assertEquals("PREPARED", result.status, result.problem)
        assertTrue(result.baselineAvailable)
        assertEquals(before, hashes(acceptedRoot))
        val caseDirectory = output.resolve("development/${baselineInput.case.id}")
        val baselineScores = evaluationJson.parseToJsonElement(Files.readString(caseDirectory.resolve("scores.json")))
            .jsonObject.getValue("baselineScores").jsonObject
        assertEquals(7, baselineScores.size)
        assertTrue(baselineScores.values.all { it.jsonObject.getValue("score1To10") == JsonNull })
        val snapshot = accepted.project.exportSnapshots.single { it.id == firstResult.snapshotId }
        snapshot.files.forEach { file ->
            val name = Path.of(file.artifact.path.value).fileName.toString()
            assertContentEquals(Files.readAllBytes(acceptedRoot.resolve(file.artifact.path.value)), Files.readAllBytes(caseDirectory.resolve("baseline/$name")))
        }
        assertEquals(notes(caseDirectory.resolve("baseline/melody.mid")), notes(caseDirectory.resolve("current/melody.mid")))
        // The generated evaluation input cannot inherit old acceptances or reuse old candidates.
        val frozenInput = store.openProject(comparison.resolve("development/${baselineInput.case.id}/input"))
        assertTrue(frozenInput.candidates.isEmpty() && frozenInput.acceptances.isEmpty() && frozenInput.exportSnapshots.isEmpty())
        val mismatchedSeed = baselineInput.copy(case = baselineInput.case.copy(seed = baselineInput.case.seed + 1))
        val mismatchMessages = mutableListOf<String>()
        val mismatchDestination = root.resolve("mismatched-seed")
        assertEquals(1, MidiCoreMusicalEvaluationCommand.run(listOf("freeze",
            request(listOf(mismatchedSeed), "mismatched-seed-request").toString(), mismatchDestination.toString())) { mismatchMessages += it })
        assertTrue(mismatchMessages.single().contains("Baseline generation seed/style differs"))
        assertFalse(Files.exists(mismatchDestination))
        assertEquals(before, hashes(acceptedRoot))
        val mismatchedStyle = baselineInput.copy(case = baselineInput.case.copy(styleId = "late-night"))
        assertEquals(1, command("freeze", request(listOf(mismatchedStyle), "mismatched-style-request").toString(), root.resolve("mismatched-style").toString()))
        assertEquals(before, hashes(acceptedRoot))
        // Admission also rechecks baseline settings for an older or modified frozen
        // manifest, even when the caller supplies that manifest's new outer hash.
        val manifest = comparison.resolve("frozen-set.json")
        val originalManifest = Files.readAllBytes(manifest)
        val changedSet = frozenSet(comparison).let { set ->
            set.copy(cases = set.cases.map { it.copy(case = it.case.copy(seed = it.case.seed + 1)) })
        }
        Files.writeString(manifest, evaluationJson.encodeToString(changedSet))
        mismatchMessages.clear()
        assertEquals(1, MidiCoreMusicalEvaluationCommand.run(listOf("export", comparison.toString(),
            evaluationFileSha256(manifest), mismatchDestination.toString())) { mismatchMessages += it })
        assertTrue(mismatchMessages.single().contains("Baseline generation seed/style differs"))
        assertFalse(Files.exists(mismatchDestination))
        assertEquals(before, hashes(acceptedRoot))
        Files.write(manifest, originalManifest)
        val missing = baselineInput.copy(case = baselineInput.case.copy(baselineSnapshotId = "missing-snapshot"))
        assertEquals(1, command("freeze", request(listOf(missing), "missing-baseline").toString(), root.resolve("missing").toString()))
        assertEquals(before, hashes(acceptedRoot))
        val partial = assertIs<MidiCoreMidiPackageExportResult.Exported>(MidiCoreMidiPackageExporter(store).export(
            ExportMidiCorePackage(accepted, enabledRoles = setOf(CandidateRole.CHORDS), snapshotId = "partial-baseline"),
        )).packageResult
        val partialInput = input(partial.session).let { it.copy(case = it.case.copy(baselineSnapshotId = partial.snapshot.id)) }
        val partialBefore = hashes(acceptedRoot)
        assertEquals(1, command("freeze", request(listOf(partialInput), "partial-baseline").toString(), root.resolve("partial").toString()))
        assertEquals(partialBefore, hashes(acceptedRoot))
    }

    @Test
    fun `empty supplied set reports all five songs and three unseen missing through the real command`() {
        val request = Path.of("src/test/resources/fixtures/q01-evaluation/empty-request.json")
        val evidenceParent = Path.of("build/q01-evaluation")
        Files.createDirectories(evidenceParent)
        val evidence = Files.createTempDirectory(evidenceParent, "preparation-")
        val frozen = evidence.resolve("frozen")
        assertEquals(0, command("freeze", request.toString(), frozen.toString()))
        val output = evidence.resolve("packages")
        assertEquals(0, command("export", frozen.toString(), evaluationFileSha256(frozen.resolve("frozen-set.json")), output.toString()))
        val report = report(output)
        assertTrue(report.cases.isEmpty())
        assertEquals(5, report.missingFinalSongs)
        assertEquals(3, report.missingUnseenSongs)
        assertFalse(Files.exists(output.resolve("final")))
        assertTrue(Files.readString(output.resolve("review.md")).contains("Only supplied owned cases are frozen"))
        System.out.println("Q01 empty-set preparation (no final songs supplied): ${output.toAbsolutePath()}")
    }

    @Test
    fun `final census counts supplied unseen declarations separately from seen and development`() {
        // Model-only declarations exercise the census; no synthetic MIDI is frozen as a final song.
        val dev = input(project(M01ComparisonFixtures.cases.first())).case
        val definitions = listOf(MidiCoreEvaluationClassification.FINAL_UNSEEN, MidiCoreEvaluationClassification.FINAL_UNSEEN,
            MidiCoreEvaluationClassification.FINAL_SEEN, MidiCoreEvaluationClassification.DEVELOPMENT)
        val cases = definitions.mapIndexed { index, classification ->
            MidiCoreFrozenEvaluationCase(dev.copy(id = "declaration-$index", classification = classification,
                fullSong = true, synthetic = classification == MidiCoreEvaluationClassification.DEVELOPMENT),
                "1".repeat(64), "$index".repeat(64), "2".repeat(64), "3".repeat(64), "4".repeat(64), emptyMap())
        }
        val set = MidiCoreFrozenEvaluationSet(evaluationId = "model-only", buildIdentity = "test", versions = evaluationVersions(),
            versionsSha256 = "5".repeat(64), cases = cases)
        assertEquals(2, set.missingFinalSongs)
        assertEquals(1, set.missingUnseenSongs)
    }

    @Test
    fun `synthetic partial tuned unowned and duplicated cases cannot become final evidence`() {
        val session = project(M01ComparisonFixtures.cases.first())
        val input = input(session)
        val definition = input.case
        assertFailsWith<IllegalArgumentException> { definition.copy(classification = MidiCoreEvaluationClassification.FINAL_UNSEEN) }
        assertFailsWith<IllegalArgumentException> { definition.copy(classification = MidiCoreEvaluationClassification.FINAL_SEEN, synthetic = false, fullSong = false) }
        assertFailsWith<IllegalArgumentException> { definition.copy(classification = MidiCoreEvaluationClassification.FINAL_UNSEEN, synthetic = false, fullSong = true, tuned = true) }
        assertFailsWith<IllegalArgumentException> { definition.copy(ownershipEvidence = "") }
        assertFailsWith<IllegalArgumentException> { definition.copy(exposureNote = "") }
        assertFailsWith<IllegalArgumentException> { definition.copy(id = "../escape") }
        assertFailsWith<IllegalArgumentException> { definition.copy(instruments = definition.instruments.dropLast(1)) }
        // An incorrectly relabeled copy cannot escape a known development designation in this set.
        val renamed = input.copy(case = definition.copy(id = "pretends-unseen", classification = MidiCoreEvaluationClassification.FINAL_UNSEEN,
            synthetic = false, fullSong = true))
        val destination = root.resolve("duplicate")
        assertEquals(1, command("freeze", request(listOf(input, renamed)).toString(), destination.toString()))
        assertFalse(Files.exists(destination))
        val developmentVariant = input.copy(case = definition.copy(id = "development-variant", seed = definition.seed + 1))
        val development = root.resolve("development-variants")
        assertEquals(0, command("freeze", request(listOf(input, developmentVariant), "variants").toString(), development.toString()))
        assertEquals(3, frozenSet(development).missingUnseenSongs)
        val outOfRange = input.copy(case = definition.copy(pianoMelodyLoop = MidiCoreEvaluationBars(1, 500)))
        assertEquals(1, command("freeze", request(listOf(outOfRange), "bad-loop").toString(), destination.toString()))
        assertFalse(Files.exists(destination))
    }

    @Test
    fun `changed source manifest runtime and settings are rejected without touching previous outputs`() {
        val session = project(M01ComparisonFixtures.cases.first())
        val engine = MidiCoreMusicalEvaluation()
        val frozen = root.resolve("freeze")
        val originalHash = engine.freeze(request(listOf(input(session))), frozen)
        val output = root.resolve("must-not-publish")
        val source = frozen.resolve("development/${session.project.id.value}/input/source/original.mid")
        val originalSource = Files.readAllBytes(source)
        Files.write(source, originalSource + byteArrayOf(0))
        assertEquals(1, command("export", frozen.toString(), originalHash, output.toString()))
        assertFalse(Files.exists(output))
        Files.write(source, originalSource)
        val manifest = frozen.resolve("frozen-set.json")
        val originalManifest = Files.readAllBytes(manifest)
        val set = frozenSet(frozen)
        val changed = set.copy(cases = set.cases.map { it.copy(case = it.case.copy(seed = it.case.seed + 1)) })
        Files.writeString(manifest, evaluationJson.encodeToString(changed))
        assertEquals(1, command("export", frozen.toString(), originalHash, output.toString()))
        // Even with a new pin, inconsistent inner settings cannot be admitted.
        assertEquals(1, command("export", frozen.toString(), evaluationFileSha256(manifest), output.toString()))
        Files.writeString(manifest, evaluationJson.encodeToString(set.copy(versions = set.versions.copy(patternCatalog = -1))))
        assertEquals(1, command("export", frozen.toString(), evaluationFileSha256(manifest), output.toString()))
        assertFalse(Files.exists(output))
        Files.write(manifest, originalManifest)
        assertEquals(0, command("export", frozen.toString(), originalHash, output.toString()))
        val previous = hashes(output)
        assertEquals(1, command("export", frozen.toString(), originalHash, output.toString()))
        assertEquals(previous, hashes(output))
        assertEquals(1, command("freeze", request(listOf(input(session)), "again").toString(), frozen.toString()))
        assertContentEquals(originalManifest, Files.readAllBytes(manifest))
    }

    @Test
    fun `source changes after freeze do not retarget immutable evaluation inputs`() {
        val session = project(M01ComparisonFixtures.cases.first())
        val engine = MidiCoreMusicalEvaluation()
        val frozen = root.resolve("freeze")
        val hash = engine.freeze(request(listOf(input(session))), frozen)
        val source = session.root.resolve("source/original.mid")
        Files.write(source, byteArrayOf(1, 2, 3)) // An external change must not become evaluation input.
        val output = root.resolve("export")
        val result = engine.export(frozen, hash, output).cases.single()
        assertEquals("PREPARED", result.status, result.problem)
        assertContentEquals(byteArrayOf(1, 2, 3), Files.readAllBytes(source))
        assertEquals(frozenSet(frozen).cases.single().sourceSha256,
            evaluationFileSha256(output.resolve("development/${session.project.id.value}/project/source/original.mid")))
    }

    @Test
    fun `unsupported project schema and altered original MIDI reject freeze without rewriting artifacts`() {
        val session = project(M01ComparisonFixtures.cases.first())
        val request = request(listOf(input(session)))
        val projectFile = session.root.resolve("project.json")
        val originalDocument = Files.readString(projectFile)
        Files.writeString(projectFile, originalDocument.replace("\"version\": 4", "\"version\": 0"))
        assertNotEquals(originalDocument, Files.readString(projectFile))
        val unsupportedBefore = hashes(session.root)
        val destination = root.resolve("no-freeze")
        assertEquals(1, command("freeze", request.toString(), destination.toString()))
        assertEquals(unsupportedBefore, hashes(session.root))
        assertFalse(Files.exists(destination))
        Files.writeString(projectFile, originalDocument)
        val source = session.root.resolve("source/original.mid")
        Files.write(source, Files.readAllBytes(source) + byteArrayOf(0))
        val changedBefore = hashes(session.root)
        assertEquals(1, command("freeze", request.toString(), destination.toString()))
        assertEquals(changedBefore, hashes(session.root))
        assertFalse(Files.exists(destination))
    }

    @Test
    fun `symlink frozen artifacts output aliases and destinations inside projects are rejected`() {
        val session = project(M01ComparisonFixtures.cases.first())
        val input = input(session)
        val request = request(listOf(input))
        val before = hashes(session.root)
        val projectAlias = root.resolve("project-alias")
        Files.createSymbolicLink(projectAlias, session.root)
        assertEquals(1, command("freeze", request.toString(), projectAlias.resolve("evaluation").toString()))
        assertEquals(before, hashes(session.root))
        val engine = MidiCoreMusicalEvaluation()
        val frozen = root.resolve("freeze")
        val hash = engine.freeze(request, frozen)
        val source = frozen.resolve("${input.case.directory}/input/source/original.mid")
        val copied = root.resolve("same-hash.mid")
        Files.copy(source, copied)
        Files.delete(source)
        Files.createSymbolicLink(source, copied)
        val output = root.resolve("no-output")
        assertEquals(1, command("export", frozen.toString(), hash, output.toString()))
        assertFalse(Files.exists(output))
        Files.delete(source)
        Files.copy(copied, source)
        assertEquals(1, command("export", frozen.toString(), hash, frozen.resolve("nested").toString()))
        val sentinel = root.resolve("existing")
        Files.createDirectory(sentinel)
        Files.writeString(sentinel.resolve("keep"), "untouched")
        val alias = root.resolve("output-alias")
        Files.createSymbolicLink(alias, sentinel)
        assertEquals(1, command("export", frozen.toString(), hash, alias.toString()))
        assertEquals("untouched", Files.readString(sentinel.resolve("keep")))
    }

    @Test
    fun `generation failure is retained with the complete set and never converted to a rest or omitted`() {
        val valid = project(M01ComparisonFixtures.cases.first())
        val unsupported = project(M01ComparisonFixtures.cases[1])
        val oldAuthority = requireNotNull(unsupported.project.authority)
        // A confirmed 5/4 grid with explicit trailing silence is legal authority, but comping is unauthored.
        val authority = oldAuthority.copy(meter = ProjectMeter(5, 2), arrangementEndTick = 2400,
            occurrences = oldAuthority.occurrences.map { it.copy(endTick = 2400) },
            chordEvents = oldAuthority.chordEvents.map { it.copy(endTick = 2400) })
        val changed = unsupported.project.copy(authority = authority)
        store.saveProject(unsupported.root, changed)
        val request = request(listOf(input(valid), input(unsupported.copy(project = changed))))
        val frozen = root.resolve("freeze")
        val engine = MidiCoreMusicalEvaluation()
        val hash = engine.freeze(request, frozen)
        val output = root.resolve("attempt")
        assertEquals(2, command("export", frozen.toString(), hash, output.toString()))
        val report = report(output)
        assertEquals(2, report.cases.size)
        assertEquals("PREPARED", report.cases.first().status, report.cases.first().problem)
        assertEquals("FAILED", report.cases.last().status)
        assertTrue(report.cases.last().problem.orEmpty().isNotBlank())
        val failedDirectory = output.resolve("development/${unsupported.project.id.value}")
        assertTrue(Files.exists(failedDirectory.resolve("project/project.json")))
        assertTrue(Files.exists(failedDirectory.resolve("scores.json")))
        assertFalse(Files.exists(failedDirectory.resolve("current/manifest.json")))
        assertTrue(store.openProject(failedDirectory.resolve("project")).acceptedPlannedRests.isEmpty())
    }

    @Test
    fun `settings hash changes with seed and instrument mapping while source identity stays fixed`() {
        val session = project(M01ComparisonFixtures.cases.first())
        val original = input(session)
        val changedSeed = original.copy(case = original.case.copy(seed = original.case.seed + 1))
        val changedMapping = original.copy(case = original.case.copy(instruments = original.case.instruments.map { it.copy(levelDb = -3.0) }))
        val captures = listOf(original, changedSeed, changedMapping).mapIndexed { index, case ->
            val frozen = root.resolve("freeze-$index")
            MidiCoreMusicalEvaluation().freeze(request(listOf(case), "request-$index"), frozen)
            frozenSet(frozen).cases.single()
        }
        assertEquals(1, captures.map { it.sourceSha256 }.distinct().size)
        assertEquals(1, captures.map { it.authoritySha256 }.distinct().size)
        assertEquals(3, captures.map { it.settingsSha256 }.distinct().size)
        assertNotEquals(captures[0].settingsSha256, captures[1].settingsSha256)
    }

    private fun project(fixture: M01ComparisonCase): MidiCoreProjectSession {
        val input = root.resolve("${fixture.id}.mid")
        Files.write(input, fixture.sourceBytes)
        var session = assertIs<MidiCoreProjectLifecycleResult.Opened>(MidiCoreProjectLifecycle(store).create(
            CreateMidiCoreProject(root.resolve(fixture.id), "Owned Q01 development fixture", fixture.id, "q01-test"),
        )).session
        session = assertIs<MidiCoreSourceImportResult.Imported>(MidiCoreSourceImport(store).import(ImportMidiCoreSource(session, input))).session
        session = assertIs<MidiCoreAuthorityResult.Confirmed>(MidiCoreMusicalAuthority(store).confirm(
            ConfirmMidiCoreAuthority(session, fixture.key, fixture.tempo, fixture.meter),
        )).session
        session = assertIs<MidiCoreStructureTimelineResult.Updated>(MidiCoreStructureTimeline(store).replace(
            ReplaceMidiCoreStructure(session, fixture.occurrences.map { ProjectSectionDefinition(it.sectionId, it.label) }.distinctBy { it.id },
                fixture.occurrences.map { MidiCoreBarOccurrencePlacement(it.id, it.sectionId, it.label, it.bars) }),
        )).session
        session = assertIs<MidiCoreAuthoritativeHarmonyResult.Updated>(MidiCoreAuthoritativeHarmony(store).replace(
            ReplaceMidiCoreHarmony(session, fixture.chords.map { AuthoritativeChordEvent(it.id, it.occurrenceId, it.symbol, it.startTick, it.endTick) }),
        )).session
        val proposalUseCase = MidiCoreArrangementPlanProposalUseCase(store)
        val proposal = assertIs<MidiCoreArrangementPlanProposalResult.Proposed>(proposalUseCase.propose(ProposeMidiCoreArrangementPlan(session, fixture.styleId))).proposal
        return assertIs<MidiCoreArrangementPlanProposalResult.Confirmed>(proposalUseCase.confirm(ConfirmMidiCoreArrangementPlanProposal(session, proposal))).session
    }

    private fun input(session: MidiCoreProjectSession): MidiCoreEvaluationCaseInput {
        val fixture = M01ComparisonFixtures.cases.single { it.id == session.project.id.value }
        return MidiCoreEvaluationCaseInput(session.root.toString(), MidiCoreEvaluationCase(
            id = fixture.id, classification = MidiCoreEvaluationClassification.DEVELOPMENT,
            ownership = MidiCoreEvaluationOwnership.USER_OWNED, ownershipEvidence = "Repository-owned hand-authored M01 test fixture",
            exposureNote = "Synthetic development material used by engine tests; never unseen", fullSong = false, synthetic = true, tuned = false,
            variety = fixture.scenarioTags.sorted(), styleId = fixture.styleId, seed = fixture.seed,
            pianoMelodyLoop = MidiCoreEvaluationBars(fixture.pianoMelodyLoop.startBar, fixture.pianoMelodyLoop.endBar),
            fullArrangementLoop = MidiCoreEvaluationBars(fixture.fullArrangementLoop.startBar, fixture.fullArrangementLoop.endBar),
            instruments = listOf("melody", "chords", "bass", "drums").map { MidiCoreEvaluationInstrument(it, "Fixed owned test mapping for $it", 0.0) },
        ))
    }

    private fun request(cases: List<MidiCoreEvaluationCaseInput>, name: String = "request"): Path = root.resolve("$name.json").also {
        Files.writeString(it, evaluationJson.encodeToString(MidiCoreEvaluationRequest(evaluationId = "q01-test", buildIdentity = "UNCOMMITTED-test-fixtures", cases = cases)))
    }

    private fun command(vararg args: String): Int = MidiCoreMusicalEvaluationCommand.run(args.toList()) { message -> System.out.println(message) }
    private fun frozenSet(path: Path) = evaluationJson.decodeFromString<MidiCoreFrozenEvaluationSet>(Files.readString(path.resolve("frozen-set.json")))
    private fun report(path: Path) = evaluationJson.decodeFromString<MidiCoreEvaluationExportReport>(Files.readString(path.resolve("evaluation.json")))
    private fun hashes(directory: Path): Map<String, String> = Files.walk(directory).use { paths ->
        paths.filter { Files.isRegularFile(it) }.toList().associate { directory.relativize(it).toString() to evaluationFileSha256(it) }
    }
    private fun notes(path: Path) = JdkMidiReader().inspect(path).sequence.orderedEvents().filterIsInstance<MidiNoteEvent>()
        .map { listOf(it.orderingKey.tick, it.endTick, it.pitch, it.velocity, it.releaseVelocity) }
}
