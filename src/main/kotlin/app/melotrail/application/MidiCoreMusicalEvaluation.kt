package app.melotrail.application

import app.melotrail.arrangement.core.MidiCoreArrangementStyleCatalog
import app.melotrail.midi.adapter.JdkMidiReader
import app.melotrail.midi.domain.MidiImportDisposition
import app.melotrail.midi.domain.MidiImportValidator
import app.melotrail.midi.domain.MidiMelodySelection
import app.melotrail.midi.domain.MidiProtectedMelodySelector
import app.melotrail.music.core.MidiCoreChordSymbol
import app.melotrail.project.MidiCoreRoleActivity
import app.melotrail.project.MidiCoreAuthorityHasher
import app.melotrail.project.MidiCoreAuthoritySettings
import app.melotrail.project.MidiCoreProject
import app.melotrail.project.MidiCoreProjectSchema
import app.melotrail.project.adapter.MidiCoreArtifactStore
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

/** Freezes supplied settings before generation, then realizes them only in new evaluation-owned projects. */
class MidiCoreMusicalEvaluation {
    private val artifacts = MidiCoreArtifactStore()

    fun freeze(requestFile: Path, destination: Path): String {
        val requestPath = requestFile.toRealPath()
        val request = evaluationJson.decodeFromString<MidiCoreEvaluationRequest>(Files.readString(requestPath))
        val roots = request.cases.map { requestPath.parent.resolve(it.projectDirectory).toRealPath() }
        val versions = evaluationVersions()
        val captures = request.cases.zip(roots).map { (input, root) ->
            MidiCoreProjectWriteCoordinator.withLock(root) { capture(input.case, root) }
        }
        requireDistinctFinalMelodies(captures.map { it.first })
        val frozen = MidiCoreFrozenEvaluationSet(
            evaluationId = request.evaluationId,
            buildIdentity = request.buildIdentity,
            versions = versions,
            versionsSha256 = evaluationSha256(evaluationJson.encodeToString(versions).encodeToByteArray()),
            cases = captures.map { it.first },
        )
        val manifest = evaluationJson.encodeToString(frozen).encodeToByteArray()
        val hash = evaluationSha256(manifest)
        return publishEvaluationDirectory(destination, roots) { staging ->
            captures.forEach { (case, files) ->
                files.forEach { (name, bytes) -> writeEvaluationFile(staging, "${case.case.directory}/$name", bytes) }
            }
            writeEvaluationFile(staging, "frozen-set.json", manifest)
            writeEvaluationFile(staging, "review.md", evaluationReview(frozen, hash, emptyList()).encodeToByteArray())
            check(evaluationVersions() == versions) { "Runtime changed while freezing the set" }
            hash
        }
    }

    /** The separately recorded manifest hash is mandatory; editing a manifest cannot silently redefine the frozen set. */
    fun export(frozenDirectory: Path, expectedSha256: String, destination: Path): MidiCoreEvaluationExportReport {
        require(expectedSha256.matches(Regex("[0-9a-f]{64}"))) { "Supply the SHA-256 printed by freeze" }
        val root = frozenDirectory.toRealPath()
        val manifest = readEvaluationFile(root, "frozen-set.json")
        require(evaluationSha256(manifest) == expectedSha256) { "Frozen set digest differs; preserve this set and use the original freeze hash" }
        val frozen = evaluationJson.decodeFromString<MidiCoreFrozenEvaluationSet>(manifest.decodeToString())
        require(frozen.schemaVersion == 1) { "Unsupported frozen evaluation schema" }
        // Apply the same case/count admission to decoded manifests, before any output is created.
        MidiCoreEvaluationRequest(evaluationId = frozen.evaluationId, buildIdentity = frozen.buildIdentity,
            cases = frozen.cases.map { MidiCoreEvaluationCaseInput(".", it.case) })
        require(frozen.versions == evaluationVersions() && frozen.versionsSha256 == evaluationSha256(evaluationJson.encodeToString(frozen.versions).encodeToByteArray())) {
            "Engine/runtime versions differ from the frozen set; freeze a new evaluation and retain prior results"
        }
        val capturedFiles = frozen.cases.map { case ->
            val files = case.files.mapValues { (relative, hash) ->
                readEvaluationFile(root, "${case.case.directory}/$relative").also {
                    require(evaluationSha256(it) == hash) { "Frozen case ${case.case.id} has a changed file: $relative" }
                }
            }
            verifyFrozenCase(case, files)
            files
        }
        requireDistinctFinalMelodies(frozen.cases)
        return publishEvaluationDirectory(destination, listOf(root)) { staging ->
            writeEvaluationFile(staging, "frozen-set.json", manifest)
            val results = frozen.cases.zip(capturedFiles).map { (case, files) ->
                files.forEach { (name, bytes) -> writeEvaluationFile(staging, "frozen/${case.case.directory}/$name", bytes) }
                val caseRoot = staging.resolve(case.case.directory)
                files.filterKeys { it.startsWith("input/") }.forEach { (name, bytes) ->
                    writeEvaluationFile(caseRoot, "project/${name.removePrefix("input/")}", bytes)
                }
                files.filterKeys { it.startsWith("baseline/") }.forEach { (name, bytes) -> writeEvaluationFile(caseRoot, name, bytes) }
                val result = generate(case, caseRoot)
                writeEvaluationFile(caseRoot, "scores.json", evaluationScoreForm(frozen, expectedSha256, case, result).encodeToByteArray())
                result
            }
            val report = MidiCoreEvaluationExportReport(
                evaluationId = frozen.evaluationId, frozenSetSha256 = expectedSha256, versionsSha256 = frozen.versionsSha256,
                missingFinalSongs = frozen.missingFinalSongs, missingUnseenSongs = frozen.missingUnseenSongs, cases = results,
            )
            writeEvaluationFile(staging, "evaluation.json", evaluationJson.encodeToString(report).encodeToByteArray())
            writeEvaluationFile(staging, "review.md", evaluationReview(frozen, expectedSha256, results).encodeToByteArray())
            check(evaluationVersions() == frozen.versions) { "Runtime changed during evaluation generation" }
            report
        }
    }

    private fun capture(case: MidiCoreEvaluationCase, root: Path): Pair<MidiCoreFrozenEvaluationCase, Map<String, ByteArray>> {
        val suppliedBytes = readEvaluationFile(root, "project.json")
        val supplied = artifacts.openProject(root)
        require(supplied == MidiCoreProjectSchema.decode(suppliedBytes.decodeToString())) { "Project changed during freeze" }
        val project = inputProject(supplied)
        validateAuthority(case, project)
        val source = requireNotNull(project.sourceMidi)
        val files = linkedMapOf(
            "supplied-project.json" to suppliedBytes,
            "input/project.json" to MidiCoreProjectSchema.encode(project).encodeToByteArray(),
        )
        listOf(source.original, source.importReport).forEach { artifact ->
            files["input/${artifact.path.value}"] = Files.readAllBytes(artifacts.verify(root, artifact)).also {
                require(evaluationSha256(it) == artifact.sha256) { "Source/report changed during freeze" }
            }
        }
        baselineFiles(case, supplied).forEach { (name, artifact) ->
            files[name] = Files.readAllBytes(artifacts.verify(root, artifact)).also {
                require(evaluationSha256(it) == artifact.sha256) { "Baseline changed during freeze" }
            }
        }
        val melodyHash = melodyNotesHash(project, artifacts.verify(root, source.original))
        require(readEvaluationFile(root, "project.json").contentEquals(suppliedBytes)) { "Project changed during freeze" }
        return MidiCoreFrozenEvaluationCase(
            case, source.sha256, melodyHash, evaluationSha256(suppliedBytes), MidiCoreAuthorityHasher.from(project).sha256,
            settingsHash(case, project), files.mapValues { evaluationSha256(it.value) }.toSortedMap(),
        ) to files
    }

    private fun verifyFrozenCase(case: MidiCoreFrozenEvaluationCase, files: Map<String, ByteArray>) {
        val supplied = MidiCoreProjectSchema.decode(files.getValue("supplied-project.json").decodeToString())
        val project = MidiCoreProjectSchema.decode(files.getValue("input/project.json").decodeToString())
        require(project == inputProject(supplied)) { "Frozen inputs differ from supplied authority" }
        validateAuthority(case.case, project)
        val source = requireNotNull(project.sourceMidi)
        val expected = mapOf(
            "supplied-project.json" to case.suppliedProjectSha256,
            "input/project.json" to evaluationSha256(MidiCoreProjectSchema.encode(project).encodeToByteArray()),
            "input/${source.original.path.value}" to source.original.sha256,
            "input/${source.importReport.path.value}" to source.importReport.sha256,
        ) + baselineFiles(case.case, supplied).mapValues { it.value.sha256 }
        require(case.files == expected && case.sourceSha256 == source.sha256 &&
            case.authoritySha256 == MidiCoreAuthorityHasher.from(project).sha256 && case.settingsSha256 == settingsHash(case.case, project)) {
            "Frozen source/settings/baseline identity is inconsistent"
        }
    }

    /** New evaluation-owned aggregate: original authority/plan, no imported decisions or output history. */
    private fun inputProject(supplied: MidiCoreProject) = MidiCoreProject(
        id = supplied.id, metadata = supplied.metadata, sourceMidi = supplied.sourceMidi, selectedMelody = supplied.selectedMelody,
        authority = supplied.authority, arrangementPlan = supplied.arrangementPlan,
    )

    private fun requireDistinctFinalMelodies(cases: List<MidiCoreFrozenEvaluationCase>) {
        require(cases.groupBy { it.melodyNotesSha256 }.values.all { group ->
            group.size == 1 || group.all { it.case.classification == MidiCoreEvaluationClassification.DEVELOPMENT }
        }) { "The same melody cannot count as multiple final songs or appear in both development and final sets" }
    }

    private fun validateAuthority(case: MidiCoreEvaluationCase, project: MidiCoreProject) {
        val source = requireNotNull(project.sourceMidi) { "Import a protected melody before freezing" }
        requireNotNull(project.selectedMelody)
        val authority = requireNotNull(project.authority) { "Confirm musical authority before freezing" }
        requireNotNull(project.arrangementPlan) { "Confirm the arrangement plan before freezing; evaluation never confirms suggestions" }
        require(authority.occurrences.isNotEmpty() && authority.chordEvents.isNotEmpty()) { "Confirm sections and harmony before freezing" }
        MidiCoreArrangementStyleCatalog.require(case.styleId)
        val barNumerator = source.ppq.toLong() * 4 * authority.meter.numerator
        require(barNumerator % authority.meter.denominator == 0L) { "The confirmed bar grid must be exactly representable" }
        val barTicks = barNumerator / authority.meter.denominator
        require(authority.arrangementEndTick % barTicks == 0L) { "Confirm whole-bar arrangement extent before freezing" }
        val bars = authority.arrangementEndTick / barTicks
        require(case.pianoMelodyLoop.endBar <= bars && case.fullArrangementLoop.endBar <= bars) { "Listening loops exceed the confirmed arrangement" }
        authority.occurrences.forEach { occurrence ->
            require(occurrence.startTick % barTicks == 0L && occurrence.endTick % barTicks == 0L) { "Sections must use confirmed whole bars" }
            val chords = authority.chordEvents.filter { it.occurrenceId == occurrence.id }
            require(chords.isNotEmpty() && chords.first().startTick == occurrence.startTick && chords.last().endTick == occurrence.endTick &&
                chords.zipWithNext().all { (left, right) -> left.endTick == right.startTick } &&
                chords.all { MidiCoreChordSymbol.parse(it.symbol) != null }) { "Frozen harmony must be supported and cover every occurrence without gaps or overlaps" }
        }
        MidiCoreAuthorityHasher.from(project) // Includes source, explicit end, all confirmed plan/neighbor inputs.
    }

    private fun baselineFiles(case: MidiCoreEvaluationCase, project: MidiCoreProject): Map<String, app.melotrail.project.ProjectArtifact> {
        val id = case.baselineSnapshotId ?: return emptyMap()
        val snapshot = requireNotNull(project.exportSnapshots.singleOrNull { it.id == id }) { "The supplied baseline snapshot is missing" }
        require(snapshot.sourceSha256 == project.sourceMidi?.sha256 &&
            snapshot.authorityHash == MidiCoreAuthorityHasher.from(project, MidiCoreAuthoritySettings(snapshot.roleSettings)).sha256) {
            "A comparison baseline must use the same protected source and confirmed authority/settings"
        }
        val activeScopes = requireNotNull(project.arrangementPlan).occurrences.flatMap { occurrence ->
            occurrence.roleSettings.filter { it.activity != MidiCoreRoleActivity.INACTIVE }.map { occurrence.occurrenceId to it.role }
        }.toSet()
        require(snapshot.acceptedCandidates.map { it.occurrenceId to it.role }.toSet() == activeScopes) {
            "Baseline must cover every active scope; an export with deselected roles is not a full comparison"
        }
        // Authority deliberately excludes generation seed/style. Bind those to the
        // saved draft that actually supplied this snapshot's immutable candidates,
        // rather than treating a newly requested seed as the baseline's settings.
        val baselineCandidateIds = snapshot.acceptedCandidates.map { it.candidateId }.toSet()
        val currentAuthorityHash = MidiCoreAuthorityHasher.from(project).sha256
        val baselineDrafts = project.arrangementDrafts.filter { draft ->
            draft.authorityHash == currentAuthorityHash &&
                draft.candidateReferences.map { it.candidateId }.toSet() == baselineCandidateIds
        }
        require(baselineDrafts.isNotEmpty()) {
            "Baseline generation settings cannot be verified from a saved full draft; choose a baseline with retained draft provenance or omit it"
        }
        require(baselineDrafts.any { it.rootSeed == case.seed && it.styleId == case.styleId }) {
            "Baseline generation seed/style differs from the requested evaluation settings; preserve the original seed/style or omit this mismatched baseline"
        }
        val files = snapshot.files.associate { "baseline/${Path.of(it.artifact.path.value).fileName}" to it.artifact }
        require(files.keys.containsAll(setOf("baseline/manifest.json", "baseline/complete-song.mid", "baseline/melody.mid"))) {
            "Baseline must be a complete accepted-only MIDI snapshot"
        }
        return files
    }

    private fun settingsHash(case: MidiCoreEvaluationCase, project: MidiCoreProject): String = evaluationSha256(
        (MidiCoreAuthorityHasher.from(project).canonicalSerialization + "\n" + evaluationJson.encodeToString(case)).encodeToByteArray(),
    )

    private fun melodyNotesHash(project: MidiCoreProject, source: Path): String {
        val inspection = JdkMidiReader().inspect(source)
        require(MidiImportValidator().validate(inspection).disposition != MidiImportDisposition.REJECTED) { "Frozen source violates the MIDI import contract" }
        val selected = requireNotNull(project.selectedMelody)
        val melody = MidiProtectedMelodySelector().select(inspection.sequence, MidiMelodySelection(selected.trackIndex, selected.channel))
        require(melody.identitySha256 == selected.identitySha256) { "Protected melody identity does not match the supplied source" }
        // Ignore source filename, track/channel wrappers and metadata when detecting duplicate songs.
        val notes = melody.notes.map { "${it.startTick}:${it.endTick}:${it.pitch}:${it.velocity}:${it.releaseVelocity}" }.sorted()
        return evaluationSha256(("${melody.ppq.value}\n" + notes.joinToString("\n")).encodeToByteArray())
    }

    private fun generate(case: MidiCoreFrozenEvaluationCase, caseRoot: Path): MidiCoreEvaluationCaseResult {
        val root = caseRoot.resolve("project")
        val baseline = case.case.baselineSnapshotId != null
        fun failed(problem: String) = MidiCoreEvaluationCaseResult(case.case.id, "FAILED",
            problem.replace(caseRoot.toString(), case.case.directory), baselineAvailable = baseline)
        return try {
            val project = artifacts.openProject(root)
            require(melodyNotesHash(project, artifacts.verify(root, requireNotNull(project.sourceMidi).original)) == case.melodyNotesSha256) {
                "Frozen melody notes differ"
            }
            val result = runBlocking {
                MidiCoreArrangementDraftGeneration(artifacts = artifacts).generate(
                    GenerateMidiCoreArrangementDraft(MidiCoreProjectSession(root, project), case.case.styleId, case.case.seed, "evaluation-draft"),
                )
            }
            val completed = when (result) {
                is MidiCoreArrangementDraftGenerationResult.Completed -> result
                is MidiCoreArrangementDraftGenerationResult.Incomplete -> return failed("${result.problem.code}: ${result.problem.message} Scope: ${result.problem.scope}; ${result.problem.nextAction}")
                is MidiCoreArrangementDraftGenerationResult.Cancelled -> return failed("Generation cancelled; partial candidates retained, no complete package")
            }
            var history = 0
            // Explicit export-command authorization applies only to this isolated evaluation copy.
            val accepted = MidiCoreArrangementDraftAcceptance(artifacts, FIXED_CLOCK, { "evaluation-use" }, { "evaluation-use-${history++}" })
                .use(UseMidiCoreArrangementDraft(completed.session, completed.draft.id))
            val session = (accepted as? MidiCoreArrangementDraftAcceptanceResult.Applied)?.session
                ?: return failed("Evaluation draft acceptance failed: $accepted")
            val exported = MidiCoreMidiPackageExporter(
                artifacts = artifacts,
                snapshotLifecycle = MidiCoreExportSnapshotLifecycle(artifacts, FIXED_CLOCK, idFactory = { "evaluation-snapshot" }),
                clock = FIXED_CLOCK, snapshotIdFactory = { "evaluation-current" },
            ).export(ExportMidiCorePackage(session))
            val output = (exported as? MidiCoreMidiPackageExportResult.Exported)?.packageResult
                ?: return failed("Evaluation package export failed: $exported")
            val files = (output.files.map { it.filename } + "manifest.json").associate { name ->
                val bytes = readEvaluationFile(output.directory, name)
                writeEvaluationFile(caseRoot, "current/$name", bytes)
                "current/$name" to evaluationSha256(bytes)
            } + case.files.filterKeys { it.startsWith("baseline/") }
            MidiCoreEvaluationCaseResult(
                case.case.id, "PREPARED", snapshotId = output.snapshot.id,
                candidateIds = output.snapshot.acceptedCandidates.map { it.candidateId },
                generatorVersions = output.snapshot.acceptedCandidates.associate { "${it.occurrenceId}:${it.role}" to it.generatorVersion },
                files = files.toSortedMap(), baselineAvailable = baseline,
            )
        } catch (error: Exception) {
            // Keep a failed attempt and its partial project; no retry/seed hunting or case omission.
            failed("${error.javaClass.simpleName}: ${error.message}")
        }
    }

    private companion object {
        // Reproducible package provenance. The clock is not evidence of a human review date.
        val FIXED_CLOCK: Clock = Clock.fixed(Instant.parse("2000-01-01T00:00:00Z"), ZoneOffset.UTC)
    }
}
