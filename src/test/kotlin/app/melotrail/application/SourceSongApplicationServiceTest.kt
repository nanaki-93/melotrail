package app.melotrail.application

import app.melotrail.arrangement.CompositionSettings
import app.melotrail.arrangement.MidiAnalysisStore
import app.melotrail.arrangement.MidiPartAnalyzer
import app.melotrail.arrangement.MidiQualityReportStore
import app.melotrail.arrangement.MidiQualityReporter
import app.melotrail.arrangement.Project
import app.melotrail.arrangement.ProjectStore
import app.melotrail.arrangement.ProjectV4Envelope
import app.melotrail.arrangement.RenderFormat
import app.melotrail.arrangement.RenderResult
import app.melotrail.arrangement.InstrumentRenderer
import app.melotrail.arrangement.LogicalInstrument
import app.melotrail.arrangement.MonophonicMelodyPreparationReport
import app.melotrail.arrangement.MelodyHarmonyFitReport
import app.melotrail.arrangement.SectionTypeId
import app.melotrail.arrangement.SongPart
import app.melotrail.arrangement.SourceSong
import app.melotrail.arrangement.OccurrenceMidiArtifactResolver
import app.melotrail.arrangement.SectionInstance
import app.melotrail.arrangement.StructureOccurrence
import app.melotrail.arrangement.SourceSongCritic
import app.melotrail.arrangement.SourceSongCriticInput
import app.melotrail.arrangement.SourceSongIssueCategory
import app.melotrail.arrangement.SourceSongIssueSeverity
import app.melotrail.arrangement.SourceKeyEvidence
import app.melotrail.arrangement.SourceSongApprovalMode
import app.melotrail.arrangement.WorkflowArtifactReference
import app.melotrail.arrangement.canonicalMidiReferences
import app.melotrail.harmony.ChordEvent
import app.melotrail.harmony.ChordEventId
import app.melotrail.harmony.ChordProgression
import app.melotrail.harmony.ChordQuality
import app.melotrail.harmony.HarmonySettings
import app.melotrail.music.MusicalKey
import app.melotrail.music.PitchClass
import app.melotrail.music.PitchSpelling
import app.melotrail.music.ScaleModeId
import app.melotrail.music.Tempo
import app.melotrail.music.TimeSignature
import app.melotrail.profile.CompositionProfileRef
import app.melotrail.profile.MoodRef
import kotlinx.serialization.json.Json
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import javax.sound.midi.MetaMessage
import javax.sound.midi.MidiEvent
import javax.sound.midi.MidiSystem
import javax.sound.midi.Sequence
import javax.sound.midi.ShortMessage
import kotlin.io.path.createDirectories
import kotlin.test.assertEquals
import kotlin.test.assertContentEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class SourceSongApplicationServiceTest {
    @TempDir lateinit var tempDir: Path

    @Test
    fun `assembles repeated source MIDI into distinct structured instances with authoritative timing and harmony`() {
        val root = project()
        val selectedBefore = listOf("A", "B", "C").associateWith { Files.readAllBytes(root.resolve("midi/clean/$it.mid")) }

        val artifact = SourceSongApplicationService().assemble(root)
        val repeated = SourceSongApplicationService().assemble(root)
        val assembled = MidiSystem.getSequence(artifact.song.assembledMidi.file.let(root::resolve).toFile())
        val persisted = Json.decodeFromString(SourceSong.serializer(), Files.readString(artifact.metadataPath))

        assertEquals(artifact.song, repeated.song)
        assertEquals(artifact.song, persisted)
        assertEquals(listOf("A1", "A2", "B1", "C1", "B2"), artifact.song.sections.map { "${it.sourcePartId}${it.occurrenceNumber}" })
        assertEquals(listOf("a-one", "a-two", "b-one", "c-one", "b-two"), artifact.song.sections.map { it.instance.instanceId })
        assertEquals(listOf(0L to 1_920L, 1_920L to 3_840L, 3_840L to 5_760L, 5_760L to 7_680L, 7_680L to 9_600L),
            artifact.song.sections.map { it.startTick to it.endTick })
        assertEquals(listOf("verse", "verse", "chorus", "bridge", "chorus"), artifact.song.sections.map { it.sectionRole.value })
        assertEquals(listOf("C", "C", "Dm", "G", "Dm"), artifact.song.sections.map { it.canonicalHarmony.single().rootSymbol + it.canonicalHarmony.single().quality.symbolSuffix })
        assertEquals(480, assembled.resolution)
        assertEquals(9_600L, assembled.tickLength)
        assertEquals(listOf(90), tempoEvents(assembled))
        assertEquals(listOf(4 to 4), meterEvents(assembled))
        assertEquals(2, assembled.tracks.size)
        val fullMelodyTracks = assembled.tracks.filter { trackName(it) == "full-melody" }
        assertEquals(1, fullMelodyTracks.size)
        assertTrue((0 until fullMelodyTracks.single().size()).none { index ->
            (fullMelodyTracks.single()[index].message as? ShortMessage)?.command == ShortMessage.CONTROL_CHANGE
        })
        assertEquals(1, artifact.song.fullMelody.maximumPolyphony)
        assertEquals(listOf("a-one", "a-two", "b-one", "c-one", "b-two"), artifact.song.fullMelody.occurrences.map { it.occurrenceId })
        assertTrue(artifact.song.fullMelody.noteLineage.any { it.protectedAnchor })
        assertEquals(artifact.song.sections.map { it.canonicalHarmony }, artifact.song.fullMelody.occurrences.map { window ->
            artifact.song.sections.single { it.instance.instanceId == window.occurrenceId }.canonicalHarmony
        })
        artifact.song.fullMelody.occurrences.forEach { window ->
            val section = artifact.song.sections.single { it.instance.instanceId == window.occurrenceId }
            assertEquals(section.sourceMidi.sha256, window.sourceMidiSha256)
            assertEquals(section.sourceMidi.preparationReport, window.monophonicPreparationReport)
            assertEquals(section.sourceMidi.harmonyFitReport, window.harmonyFitReport)
            assertEquals(section.startTick, window.startTick)
            assertEquals(section.endTick, window.endTick)
        }
        assertEquals(artifact.song.fullMelody.noteLineage.size, fullMelodyTracks.single().noteOnCount())
        assertTrue(artifact.song.fullMelody.noteLineage.zipWithNext().all { (left, right) -> left.endTick <= right.startTick })
        assertEquals(artifact.song.sections.map { it.instance.instanceId }, markerOccurrences(assembled))
        val templates = artifact.song.fullMelody.grooveMap.occurrenceTemplateFingerprints.associateBy { it.occurrenceId }
        assertEquals(templates.getValue("a-one").fingerprint, templates.getValue("a-two").fingerprint)
        assertEquals(templates.getValue("b-one").fingerprint, templates.getValue("b-two").fingerprint)
        assertTrue(artifact.song.fullMelody.grooveMap.boundaries.all { it.status == app.melotrail.arrangement.FullSongGrooveBoundaryStatus.CONTINUOUS })
        assertTrue(artifact.metadataPath.startsWith(root.resolve("source-song")))
        assertTrue(artifact.metadataPath.toString().contains("source-song/v2/"))
        assertTrue(artifact.song.sections.all { it.sourceMidi.kind == "HARMONY_FITTED" && it.sourceMidi.preparationReport != null && it.sourceMidi.harmonyFitReport != null })
        artifact.song.sections.forEach { section ->
            val source = section.sourceMidi
            val preparation = Json.decodeFromString(MonophonicMelodyPreparationReport.serializer(), Files.readString(root.resolve(requireNotNull(source.preparationReport).file)))
            val fit = Json.decodeFromString(MelodyHarmonyFitReport.serializer(), Files.readString(root.resolve(requireNotNull(source.harmonyFitReport).file)))
            assertEquals(preparation.output, fit.input)
            assertEquals(source.sha256, fit.output?.sha256)
            assertTrue(fit.outputNotes.all { it.eligibility in setOf(app.melotrail.arrangement.MelodyHarmonyEligibility.CHORD_TONE, app.melotrail.arrangement.MelodyHarmonyEligibility.COMMON_TONE_TIE) })
            assertEquals(1, preparation.maximumOutputPolyphony)
            assertTrue(Files.isRegularFile(root.resolve(source.projectRelativePath)))
        }
        selectedBefore.forEach { (partId, bytes) -> assertContentEquals(bytes, Files.readAllBytes(root.resolve("midi/clean/$partId.mid"))) }
    }

    @Test
    fun `source critic preserves the harmony-fitted source and permits ordinary approval`() {
        val root = project()
        val critic = DefaultSourceSongCriticApplicationService()

        val report = critic.run(root)

        assertTrue(report.report.issues.none { it.category == SourceSongIssueCategory.EXPOSED_CHORD_FIT })
        assertTrue(!report.report.hasBlockingIssues)
        val approval = critic.approve(root)

        assertEquals(report.report.connectedMidi.sha256, approval.approval.connectedMidiSha256)
        assertTrue(approval.approval.overriddenBlockingIssueIds.isEmpty())
        assertEquals(approval.approval, critic.requireApproved(root).approval)
        assertEquals(approval.approval, critic.requireQualityCertifiedApproved(root).approval)
        val approved = critic.requireApprovedMelody(root)
        assertEquals(approval.approval.connectedMidiSha256, approved.connectedMidi.sha256)
        assertTrue(approved.sourceSongSidecar.file.endsWith("source-song.json"))
        assertTrue(approved.connectionSidecar.file.endsWith("connection.json"))
        assertTrue(approved.approvalSidecar.file.endsWith("approval.json"))
    }

    @Test
    fun `source approval becomes stale after an authoritative structure change`() {
        val root = project()
        val critic = DefaultSourceSongCriticApplicationService()
        critic.run(root)
        critic.approve(root)
        val current = ProjectStore.read(root)
        ProjectStore.write(root, current.copy(envelope = current.envelope.copy(structureOccurrences = current.envelope.structureOccurrences.reversed())))

        assertFailsWith<IllegalArgumentException> { critic.requireApproved(root) }
    }

    @Test
    fun `source critic reports complete non-overridable evidence for QP-001 melody defects`() {
        val root = project()
        val source = SourceSongApplicationService().assemble(root).song
        val connection = app.melotrail.arrangement.MelodyConnectionPlanner().connect(root, source).connection
        val critic = SourceSongCritic()

        val tailAndOverlap = mutateConnected(root, connection.outputMidi) { track ->
            track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_ON, 0, 67, 90), 1_800))
            track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, 0, 67, 0), 1_980))
        }
        val invalid = critic.criticize(SourceSongCriticInput(root, source, connection.copy(outputMidi = tailAndOverlap), setOf(0, 2, 4, 5, 7, 9, 11), setOf("A")))

        assertTrue(invalid.issues.any { it.category == SourceSongIssueCategory.GLOBAL_MONOPHONY && it.severity == SourceSongIssueSeverity.HARD_BLOCKER })
        assertTrue(invalid.issues.any { it.category == SourceSongIssueCategory.SUSTAIN_BOUNDARY_TAIL && it.severity == SourceSongIssueSeverity.HARD_BLOCKER })
        assertTrue(invalid.issues.any { it.category == SourceSongIssueCategory.KEY_ELIGIBILITY && it.severity == SourceSongIssueSeverity.HARD_BLOCKER })
        assertEquals(invalid.issues.size, invalid.counts.total)
        assertEquals(invalid.issues.count { it.severity == SourceSongIssueSeverity.HARD_BLOCKER }, invalid.counts.hardBlockers)

        val misaligned = mutateConnected(root, connection.outputMidi) { track ->
            track.add(MidiEvent(MetaMessage(0x2F, byteArrayOf(), 0), source.sections.last().endTick + 1))
        }
        val misalignedReport = critic.criticize(SourceSongCriticInput(root, source, connection.copy(outputMidi = misaligned), setOf(0, 2, 4, 5, 7, 9, 11)))
        assertTrue(misalignedReport.issues.any { it.category == SourceSongIssueCategory.TIMING_MAPPING && it.severity == SourceSongIssueSeverity.HARD_BLOCKER })

        val exposed = mutateConnected(root, connection.outputMidi) { track ->
            val events = (0 until track.size()).map(track::get)
            val event = events.first { (it.message as? ShortMessage)?.let { message ->
                message.command == ShortMessage.NOTE_ON && message.data2 > 0
            } == true }
            val message = event.message as ShortMessage
            val originalPitch = message.data1
            message.setMessage(ShortMessage.NOTE_ON, message.channel, 62, message.data2)
            val release = events.dropWhile { it !== event }.drop(1).first { (it.message as? ShortMessage)?.let { candidate ->
                candidate.data1 == originalPitch && (candidate.command == ShortMessage.NOTE_OFF || candidate.command == ShortMessage.NOTE_ON && candidate.data2 == 0)
            } == true }
            val releaseMessage = release.message as ShortMessage
            releaseMessage.setMessage(releaseMessage.command, releaseMessage.channel, 62, releaseMessage.data2)
        }
        val exposedReport = critic.criticize(SourceSongCriticInput(root, source, connection.copy(outputMidi = exposed), setOf(0, 2, 4, 5, 7, 9, 11)))
        assertTrue(exposedReport.issues.any { it.category == SourceSongIssueCategory.EXPOSED_CHORD_FIT && it.severity == SourceSongIssueSeverity.HARD_BLOCKER })
    }

    @Test
    fun `low confidence source-key evidence is a non-overridable hard gate`() {
        val root = project()
        val original = ProjectStore.read(root)
        val key = MusicalKey(PitchClass.of(PitchSpelling.C), ScaleModeId.MAJOR)
        ProjectStore.write(root, original.copy(parts = original.parts.map { part ->
            if (part.id == "A") part.copy(sourceKeyEvidence = SourceKeyEvidence(key, 0.20, SourceKeyEvidence.ALGORITHM_VERSION, "f".repeat(64))) else part
        }))
        val critic = DefaultSourceSongCriticApplicationService()

        val report = critic.run(root).report

        assertTrue(report.issues.any { it.category == SourceSongIssueCategory.KEY_ELIGIBILITY && it.severity == SourceSongIssueSeverity.HARD_BLOCKER })
        assertFailsWith<IllegalArgumentException> { critic.approve(root, overrideBlockingIssues = true, overrideReason = "Private review cannot bypass an unconfirmed key.") }
    }

    @Test
    fun `source approval becomes stale after a canonical selected melody changes`() {
        val root = project()
        val critic = DefaultSourceSongCriticApplicationService()
        critic.run(root)
        critic.approve(root)
        replaceFirstNotePitch(root.resolve("midi/clean/B.mid"), 86)
        ProjectStore.write(root, refreshCleanQuality(root, "B"))

        assertFailsWith<IllegalArgumentException> { critic.requireApproved(root) }
    }

    @Test
    fun `private source override remains explicitly experimental and cannot satisfy quality certification`() {
        val root = project()
        val clean = root.resolve("midi/clean/B.mid")
        replaceFirstNotePitch(clean, 86)
        ProjectStore.write(root, refreshCleanQuality(root, "B"))
        val critic = DefaultSourceSongCriticApplicationService()

        val report = critic.run(root).report
        assertTrue(report.issues.any { it.category == SourceSongIssueCategory.EXTREME_JUMP && it.severity == SourceSongIssueSeverity.BLOCKING })
        assertTrue(!report.hasHardBlockers)
        val approval = critic.approve(root, overrideBlockingIssues = true, overrideReason = "Private audition of the authored jump.")

        assertEquals(SourceSongApprovalMode.PRIVATE_AUDITION, approval.approval.mode)
        assertTrue(critic.requireApprovedMelody(root).experimental)
        assertFailsWith<IllegalArgumentException> { critic.requireQualityCertifiedApproved(root) }
    }

    @Test
    fun `canonical melody review previews use verified source prepared full and bounded boundary inputs before approval`() = runTest {
        val root = project()
        val source = SourceSongApplicationService().assemble(root).song
        val connection = app.melotrail.arrangement.MelodyConnectionPlanner().connect(root, source).connection
        val renderer = CapturingRenderer()
        val preview = DefaultPartPreviewApplicationService(renderer)

        val sourceResult = preview.resolveSourceSongReview(root, SourceSongReviewPreviewRequest(SourceSongReviewPreview.SOURCE, partId = "A"))
        assertIs<PreviewResult.Prerequisite>(sourceResult)
        assertTrue(renderer.input?.fileName.toString()?.endsWith(".mid") == true)

        val preparedResult = preview.resolveSourceSongReview(root, SourceSongReviewPreviewRequest(SourceSongReviewPreview.PREPARED))
        assertIs<PreviewResult.Prerequisite>(preparedResult)
        assertEquals(source.assembledMidi.file, root.relativize(requireNotNull(renderer.input)).toString().replace('\\', '/'))

        val fullResult = preview.resolveSourceSongReview(root, SourceSongReviewPreviewRequest(SourceSongReviewPreview.FULL_MELODY))
        assertIs<PreviewResult.Prerequisite>(fullResult)
        assertEquals(connection.outputMidi.file, root.relativize(requireNotNull(renderer.input)).toString().replace('\\', '/'))

        val boundaryId = connection.boundaries.first().decision.boundaryId
        val boundaryResult = preview.resolveSourceSongReview(root, SourceSongReviewPreviewRequest(SourceSongReviewPreview.BOUNDARY, boundaryId = boundaryId))
        assertIs<PreviewResult.Prerequisite>(boundaryResult)
        val boundary = requireNotNull(renderer.input)
        assertTrue(boundary.startsWith(root.resolve("previews")))
        assertEquals(3_840L, MidiSystem.getSequence(boundary.toFile()).tickLength)
    }

    @Test
    fun `occurrence views are clipped from one approved connected melody with canonical bounds`() {
        val root = project()
        val critic = DefaultSourceSongCriticApplicationService()
        val project = ProjectStore.read(root)
        val occurrences = project.envelope.structureOccurrences.mapIndexed { index, occurrence ->
            SectionInstance(index, occurrence.partId, occurrence.id)
        }

        val missingApproval = assertFailsWith<IllegalArgumentException> {
            OccurrenceMidiArtifactResolver().resolve(root, project, occurrences)
        }
        assertTrue(missingApproval.message.orEmpty().contains("Run the critic"))

        critic.run(root); val approved = critic.approve(root)
        val views = OccurrenceMidiArtifactResolver().resolve(root, project, occurrences)
        val exact = critic.requireApprovedMelody(root)
        assertEquals(approved.approval.connectedMidiSha256, exact.connectedMidi.sha256)
        assertTrue(views.all { it.canonicalFullMelodySha256 == exact.connectedMidi.sha256 })
        assertEquals(exact.sourceSong.fullMelody.occurrences.map { it.startTick to it.endTick }, views.map { it.startTick to it.endTick })
        assertTrue(views.all { MidiSystem.getSequence(it.path.toFile()).tickLength == it.endTick - it.startTick })
    }

    private fun project(): Path {
        val root = tempDir.resolve("source-song")
        root.resolve("source").createDirectories(); root.resolve("midi/clean").createDirectories()
        val definitions = listOf(
            Triple("A", SectionTypeId.VERSE, PitchSpelling.C),
            Triple("B", SectionTypeId.CHORUS, PitchSpelling.D),
            Triple("C", SectionTypeId.BRIDGE, PitchSpelling.G)
        )
        definitions.forEachIndexed { index, (id, _, _) ->
            writeMidi(root.resolve("source/$id.mid"), 60 + index, 60.0, 3, 4)
            writeMidi(root.resolve("midi/clean/$id.mid"), 60 + index, 60.0, 3, 4)
        }
        val project = Project(
            name = "structured-source",
            parts = definitions.map { (id, role, _) -> SongPart(id, "source/$id.mid", sectionType = role, midi = canonicalMidiReferences(root, id)) },
            renderFormat = RenderFormat(),
            envelope = ProjectV4Envelope(
                compositionSettings = CompositionSettings(
                    key = MusicalKey(PitchClass.of(PitchSpelling.C), ScaleModeId.MAJOR), tempo = Tempo(90.0), timeSignature = TimeSignature(4, 4),
                    profile = CompositionProfileRef("lofi", 1), mood = MoodRef("warm", 1), decisionRevision = 1,
                    resolvedProfileSha256 = "a".repeat(64), decisionSha256 = "b".repeat(64)
                ),
                harmony = HarmonySettings(progressions = definitions.map { (_, role, chord) ->
                    ChordProgression(app.melotrail.harmony.SectionTypeId(role.value), listOf(
                        ChordEvent(ChordEventId("${role.value}-one"), PitchClass.of(chord), if (role == SectionTypeId.CHORUS) ChordQuality.MINOR else ChordQuality.MAJOR, 0)
                    ))
                }),
                structureOccurrences = listOf(
                    StructureOccurrence("a-one", "A"), StructureOccurrence("a-two", "A"), StructureOccurrence("b-one", "B"),
                    StructureOccurrence("c-one", "C"), StructureOccurrence("b-two", "B")
                )
            )
        )
        ProjectStore.write(root, project)
        project.parts.forEach { part ->
            val current = ProjectStore.read(root)
            MidiAnalysisStore.write(root, current, part.id, MidiPartAnalyzer().analyze(root.resolve("midi/clean/${part.id}.mid"), part.id))
        }
        return root
    }

    private fun writeMidi(path: Path, pitch: Int, bpm: Double, numerator: Int, denominator: Int) {
        Files.createDirectories(path.parent)
        val sequence = Sequence(Sequence.PPQ, 480)
        val track = sequence.createTrack()
        val micros = (60_000_000.0 / bpm).toInt()
        track.add(MidiEvent(MetaMessage().also { it.setMessage(0x51, byteArrayOf((micros shr 16).toByte(), (micros shr 8).toByte(), micros.toByte()), 3) }, 0))
        track.add(MidiEvent(MetaMessage().also { it.setMessage(0x58, byteArrayOf(numerator.toByte(), Integer.numberOfTrailingZeros(denominator).toByte(), 24, 8), 4) }, 0))
        track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_ON, 0, pitch, 90), 0))
        track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, 0, pitch, 0), 1_920))
        MidiSystem.write(sequence, 1, path.toFile())
    }

    private fun mutateConnected(root: Path, reference: WorkflowArtifactReference, mutate: (javax.sound.midi.Track) -> Unit): WorkflowArtifactReference {
        val sequence = MidiSystem.getSequence(root.resolve(reference.file).toFile())
        mutate(sequence.tracks.single { trackName(it) == "full-melody" })
        val output = root.resolve("fixtures/critic-${System.nanoTime()}.mid")
        Files.createDirectories(output.parent)
        MidiSystem.write(sequence, 1, output.toFile())
        return WorkflowArtifactReference(root.relativize(output).toString(), sha256(output))
    }

    private fun sha256(path: Path): String = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)).joinToString("") { "%02x".format(it) }

    private fun replaceFirstNotePitch(path: Path, pitch: Int) {
        val sequence = MidiSystem.getSequence(path.toFile())
        val track = sequence.tracks.first()
        val events = (0 until track.size()).map(track::get)
        val noteOn = events.first { (it.message as? ShortMessage)?.let { message -> message.command == ShortMessage.NOTE_ON && message.data2 > 0 } == true }
        val on = noteOn.message as ShortMessage
        val originalPitch = on.data1
        on.setMessage(ShortMessage.NOTE_ON, on.channel, pitch, on.data2)
        val noteOff = events.dropWhile { it !== noteOn }.drop(1).first { (it.message as? ShortMessage)?.let { message ->
            message.data1 == originalPitch && (message.command == ShortMessage.NOTE_OFF || message.command == ShortMessage.NOTE_ON && message.data2 == 0)
        } == true }
        val off = noteOff.message as ShortMessage
        off.setMessage(off.command, off.channel, pitch, off.data2)
        MidiSystem.write(sequence, 1, path.toFile())
    }

    private fun refreshCleanQuality(root: Path, partId: String): Project {
        val project = ProjectStore.read(root)
        return project.copy(parts = project.parts.map { part ->
            if (part.id != partId) part else {
                val midi = requireNotNull(part.midi)
                val clean = root.resolve(requireNotNull(midi.clean))
                val report = MidiQualityReporter().report(partId, root.resolve("midi/raw/$partId.mid"), clean, requireNotNull(midi.cleanup))
                val reportPath = MidiQualityReportStore.write(root, report)
                val relative = root.relativize(reportPath).toString()
                part.copy(midi = midi.copy(quality = relative, cleanApproval = MidiQualityReportStore.approval(root, relative, report)))
            }
        })
    }

    private inner class CapturingRenderer : InstrumentRenderer {
        var input: Path? = null
        override suspend fun render(midi: Path, instrument: LogicalInstrument, output: Path, format: RenderFormat, expectedFrames: Long): RenderResult {
            input = midi
            error("renderer unavailable")
        }
    }

    private fun tempoEvents(sequence: Sequence): List<Int> = sequence.tracks.flatMap { track -> (0 until track.size()).map(track::get) }
        .mapNotNull { it.message as? MetaMessage }.filter { it.type == 0x51 }.map { message ->
            val micros = ((message.data[0].toInt() and 0xff) shl 16) or ((message.data[1].toInt() and 0xff) shl 8) or (message.data[2].toInt() and 0xff)
            (60_000_000.0 / micros).toInt()
        }

    private fun meterEvents(sequence: Sequence): List<Pair<Int, Int>> = sequence.tracks.flatMap { track -> (0 until track.size()).map(track::get) }
        .mapNotNull { it.message as? MetaMessage }.filter { it.type == 0x58 }.map { it.data[0].toInt() and 0xff to (1 shl (it.data[1].toInt() and 0xff)) }

    private fun trackName(track: javax.sound.midi.Track): String? = (0 until track.size()).map(track::get).firstNotNullOfOrNull { event ->
        (event.message as? MetaMessage)?.takeIf { it.type == 0x03 }?.data?.toString(Charsets.UTF_8)
    }

    private fun javax.sound.midi.Track.noteOnCount(): Int = (0 until size()).count { index ->
        val message = get(index).message as? ShortMessage
        message?.command == ShortMessage.NOTE_ON && message.data2 > 0
    }

    private fun markerOccurrences(sequence: Sequence): List<String> = sequence.tracks.first().let { conductor ->
        (0 until conductor.size()).map(conductor::get).mapNotNull { event ->
            (event.message as? MetaMessage)?.takeIf { it.type == 0x06 }?.data?.toString(Charsets.UTF_8)
                ?.substringAfter("occurrence=")?.substringBefore(';')
        }
    }
}
