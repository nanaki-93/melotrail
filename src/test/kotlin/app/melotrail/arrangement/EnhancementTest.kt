package app.melotrail.arrangement

import app.melotrail.application.CanonicalAnalyzedPartFacts
import app.melotrail.application.CanonicalChord
import app.melotrail.application.CanonicalSelectedPartArtifact
import app.melotrail.application.HarmonicTimelineEntry
import app.melotrail.application.MusicalOccurrence
import app.melotrail.application.PartEnhancementProjection
import app.melotrail.harmony.ChordEvent
import app.melotrail.harmony.ChordEventId
import app.melotrail.harmony.ChordProgression
import app.melotrail.harmony.ChordQuality
import app.melotrail.harmony.HarmonySettings
import app.melotrail.harmony.SectionTypeId as HarmonySectionTypeId
import app.melotrail.music.MusicalKey
import app.melotrail.music.PitchClass
import app.melotrail.music.PitchSpelling
import app.melotrail.music.ScaleModeId
import app.melotrail.music.Tempo
import app.melotrail.music.TimeSignature
import app.melotrail.profile.BundledCompositionProfileCatalog
import app.melotrail.profile.CompositionProfileRef
import app.melotrail.profile.MoodRef
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path

class EnhancementTest {
    @TempDir lateinit var root: Path

    @Test
    fun `context hash covers every cache-relevant field deterministically`() {
        val input = root.resolve("corrected.mid").also { Files.write(it, byteArrayOf(1, 2, 3)) }
        val baseline = context(input)
        val variants = listOf(
            context(input, key = MusicalKey(PitchClass.of(PitchSpelling.D), ScaleModeId.NATURAL_MINOR)),
            context(input, harmony = harmony("chorus")),
            context(input, tempo = 96.0),
            context(input, meter = TimeSignature(3, 4)),
            context(input, mood = MoodRef("dark", 1)),
            context(input, section = SectionTypeId.CHORUS),
            context(input, partId = "B"),
            context(root.resolve("changed.mid").also { Files.write(it, byteArrayOf(3, 2, 1)) }),
            context(input, intensity = EnhancementIntensity.BALANCED),
            context(input, seed = 9),
            context(input, pipelineVersion = "enhancement-v2")
        )

        baseline.requireValid()
        assertEquals(EnhancementIntensity.SUBTLE, baseline.intensity)
        assertEquals(baseline.contextSha256, MusicalProcessingContextHasher.hash(baseline))
        variants.forEach { variant ->
            variant.requireValid()
            assertNotEquals(baseline.contextSha256, variant.contextSha256)
        }
        val changedAuthority = baseline.copy(authorityContextSha256 = "d".repeat(64), contextSha256 = "0".repeat(64)).let { bare ->
            bare.copy(contextSha256 = MusicalProcessingContextHasher.hash(bare))
        }
        assertNotEquals(baseline.contextSha256, changedAuthority.contextSha256)
    }

    @Test
    fun `intensity policies are ordered and bounded`() {
        val policies = EnhancementIntensity.entries.map(EnhancementPolicy::forIntensity)
        assertEquals(0, policies.first { it.intensity == EnhancementIntensity.OFF }.maximumEdits)
        assertTrue(policies.zipWithNext().all { (left, right) -> left.maximumEdits <= right.maximumEdits && left.maximumIdentityDistancePercent <= right.maximumIdentityDistancePercent })
        assertTrue(policies.all { it.maximumTimingShiftMs <= 80 && it.maximumVelocityDelta <= 127 })
    }

    @Test
    fun `off does not call planner or applier and retains enhanced evidence`() {
        val input = root.resolve("corrected.mid").also { Files.write(it, byteArrayOf(1, 2, 3)) }
        val context = context(input, intensity = EnhancementIntensity.OFF)
        var plannerCalls = 0
        var applierCalls = 0
        val service = EnhancementExecutionService(
            EnhancementPlanner { plannerCalls++; error("must not plan") },
            EnhancementPlanApplier { _, _, _, _ -> applierCalls++; error("must not apply") }
        )

        val report = service.enhance(input, null, context)
        val corrected = WorkflowArtifactReference("midi/corrected/A.mid", "a".repeat(64))
        val oldEnhanced = WorkflowArtifactReference("midi/enhancement/A/old.mid", "b".repeat(64))
        val selection = EnhancementSelectionPolicy.select(EnhancementIntensity.OFF, corrected, oldEnhanced)

        assertEquals(0, plannerCalls); assertEquals(0, applierCalls)
        assertEquals(null, report.outputSha256)
        assertEquals(EnhancementSelection.CORRECTED, selection.selected)
        assertEquals(corrected, selection.selectedArtifact)
        assertEquals(oldEnhanced, selection.retainedEnhancedEvidence)
    }

    @Test
    fun `placeholder is transparent while non-placeholder plans require a licensed identity`() {
        val input = root.resolve("corrected.mid").also { Files.write(it, byteArrayOf(9, 8, 7)) }
        val context = context(input)
        val output = root.resolve("enhanced.mid")
        val noOp = TransparentNoOpEnhancementProcessor()
        val report = EnhancementExecutionService(noOp, noOp).enhance(input, output, context)

        assertTrue(Files.readAllBytes(input).contentEquals(Files.readAllBytes(output)))
        assertTrue(report.placeholder)
        assertEquals(0, report.appliedEdits.size)
        assertFalse(report.message.contains("AI", ignoreCase = true))
        val unlicensed = EnhancementPlan(
            subjectHash = "0".repeat(64), inputSha256 = context.correctedInputSha256, contextSha256 = context.contextSha256,
            processorId = "local-model", processorVersion = "1", placeholder = false
        )
        assertThrows(IllegalArgumentException::class.java) { unlicensed.requireValid(context, EnhancementPolicy.forIntensity(context.intensity)) }
        val strictPlan = Json { encodeDefaults = true }.encodeToString(noOp.plan(context))
        assertEquals(noOp.plan(context), EnhancementPlanCodec.decode(strictPlan, context, EnhancementPolicy.forIntensity(context.intensity)))
        assertThrows(IllegalArgumentException::class.java) {
            EnhancementPlanCodec.decode(strictPlan.dropLast(1) + ",\"unexpected\":true}", context, EnhancementPolicy.forIntensity(context.intensity))
        }
    }

    @Test
    fun `local enhancement adapter stamps application-owned identity onto bounded model plans`() {
        val input = root.resolve("corrected.mid").also { Files.write(it, byteArrayOf(4, 5, 6)) }
        val context = context(input)
        val valid = """{"goals":[],"edits":[]}"""
        val planner = LocalQwenEnhancementPlanner(LocalQwenClient { _, _ -> valid }, EnhancementModelIdentity("qwen", "fixture", "1", "apache-2.0"))
        val accepted = planner.plan(context)
        assertEquals(emptyList<EnhancementEdit>(), accepted.edits)
        assertEquals(context.version, accepted.version)
        assertEquals(sha256Subject(context), accepted.subjectHash)
        assertEquals(context.correctedInputSha256, accepted.inputSha256)
        assertEquals(context.contextSha256, accepted.contextSha256)
        val emptyArrayNoOp = LocalQwenEnhancementPlanner(LocalQwenClient { _, _ -> "[]" }, EnhancementModelIdentity("qwen", "fixture", "1", "apache-2.0"))
            .plan(context)
        assertEquals(emptyList<EnhancementEdit>(), emptyArrayNoOp.edits)
        val firstId = "m-" + "1".repeat(64)
        val secondId = "m-" + "2".repeat(64)
        val lowercaseWirePlan = LocalQwenEnhancementPlanner(LocalQwenClient { _, _ -> """{"goals":["flow_contour","passing_note"],"edits":[{"kind":"velocity","noteId":"$firstId","value":2,"goal":"flow_contour","reason":"smooth contour"},{"kind":"velocity","noteId":"$secondId","value":9,"goal":"flow_contour","reason":"outside subtle policy"}]}""" }, EnhancementModelIdentity("qwen", "fixture", "1", "apache-2.0"))
            .plan(context)
        assertEquals(setOf(EnhancementGoal.FLOW_CONTOUR, EnhancementGoal.PASSING_NOTE), lowercaseWirePlan.goals)
        assertEquals(EnhancementEdit(EnhancementEditKind.VELOCITY, firstId, 2, EnhancementGoal.FLOW_CONTOUR, "smooth contour"), lowercaseWirePlan.edits.single())
        val legacyWrongIdentity = LocalQwenEnhancementPlanner(LocalQwenClient { _, _ -> """{"version":999,"subjectHash":"${"0".repeat(64)}","inputSha256":"${"0".repeat(64)}","contextSha256":"${"0".repeat(64)}","goals":[],"edits":[]}""" }, EnhancementModelIdentity("qwen", "fixture", "1", "apache-2.0"))
        assertEquals(accepted, legacyWrongIdentity.plan(context))
        val unsupportedGoal = LocalQwenEnhancementPlanner(LocalQwenClient { _, _ -> """{"goals":["outside_the_contract"],"edits":[]}""" }, EnhancementModelIdentity("qwen", "fixture", "1", "apache-2.0"))
        assertThrows(IllegalArgumentException::class.java) { unsupportedGoal.plan(context) }
        val unknown = LocalQwenEnhancementPlanner(LocalQwenClient { _, _ -> valid }, EnhancementModelIdentity("qwen", "fixture", "1", "unknown"))
        assertThrows(IllegalArgumentException::class.java) { unknown.plan(context) }
    }

    @Test
    fun `local enhancement adapter discards forbidden protected-anchor pitch and removal edits`() {
        val input = root.resolve("protected-anchor.mid").also(::writeMidi)
        val unprotected = context(input)
        val anchorId = unprotected.notes.first().id
        val context = unprotected.copy(protectedAnchorNoteIds = listOf(anchorId), contextSha256 = "0".repeat(64)).let { bare ->
            bare.copy(contextSha256 = MusicalProcessingContextHasher.hash(bare))
        }
        val planner = LocalQwenEnhancementPlanner(
            LocalQwenClient { _, _ ->
                """{"goals":["flow_contour"],"edits":[{"kind":"pitch","noteId":"$anchorId","value":1,"goal":"flow_contour","reason":"forbidden"},{"kind":"remove_note","noteId":"$anchorId","value":0,"goal":"flow_contour","reason":"forbidden"},{"kind":"velocity","noteId":"$anchorId","value":2,"goal":"flow_contour","reason":"allowed"}]}"""
            },
            EnhancementModelIdentity("qwen", "fixture", "1", "apache-2.0")
        )

        val accepted = planner.plan(context)

        assertEquals(listOf(EnhancementEdit(EnhancementEditKind.VELOCITY, anchorId, 2, EnhancementGoal.FLOW_CONTOUR, "allowed")), accepted.edits)
    }

    @Test
    fun `part-local enhancement works before Structure and refuses harmonic rewrites`() {
        val input = root.resolve("part-local.mid")
        writeMidi(input)
        val context = MusicalProcessingContextFactory.buildPartLocal(
            project = project(),
            partId = "A",
            selectedInput = input,
            profiles = BundledCompositionProfileCatalog.load()
        )
        val velocityPlan = EnhancementPlan(
            subjectHash = sha256Subject(context), inputSha256 = context.correctedInputSha256, contextSha256 = context.contextSha256,
            processorId = "fixture", processorVersion = "1", placeholder = false,
            model = EnhancementModelIdentity("qwen", "fixture", "1", "apache-2.0"),
            edits = listOf(EnhancementEdit(EnhancementEditKind.VELOCITY, context.notes[1].id, 2))
        )
        val pitchPlan = velocityPlan.copy(edits = listOf(EnhancementEdit(EnhancementEditKind.PITCH, context.notes[1].id, 2)))

        context.requireValid()
        assertEquals(EnhancementContextScope.PART_LOCAL, context.contextScope)
        assertTrue(context.harmony.isEmpty())
        velocityPlan.requireValid(context, EnhancementPolicy.forIntensity(context.intensity))
        assertThrows(IllegalArgumentException::class.java) { pitchPlan.requireValid(context, EnhancementPolicy.forIntensity(context.intensity)) }
    }

    @Test
    fun `one source reused in equivalent verse occurrences has one bounded enhancement context`() {
        val input = root.resolve("repeated-verse.mid")
        writeMidi(input)
        val analysis = MidiPartAnalyzer().analyze(input, "verse")
        val duration = analysis.durationTicks
        val occurrences = listOf(
            MusicalOccurrence("verse-1", "verse", SectionTypeId.VERSE, 0, 5, 0, duration),
            MusicalOccurrence("verse-2", "verse", SectionTypeId.VERSE, 5, 10, duration, duration * 2)
        )
        fun chord(occurrence: MusicalOccurrence, root: Int = 0) = HarmonicTimelineEntry(
            occurrence.occurrenceId, SectionTypeId.VERSE, CanonicalChord(root, if (root == 0) "C" else "D", ChordQuality.MINOR),
            occurrence.startBar, occurrence.startTick, occurrence.endTick
        )
        val hash = sha256(input)
        val projection = PartEnhancementProjection(
            contextSha256 = "a".repeat(64),
            part = CanonicalSelectedPartArtifact("verse", "midi/corrected/verse.mid", hash, analysis.ppq, "corrected"),
            projectKey = MusicalKey(PitchClass.of(PitchSpelling.C), ScaleModeId.NATURAL_MINOR),
            tempo = Tempo(80.0), meter = TimeSignature(4, 4),
            profile = CompositionProfileRef("lofi", 1), mood = MoodRef("warm", 1),
            occurrences = occurrences, harmony = occurrences.map(::chord), harmonyPpq = analysis.ppq,
            analysis = CanonicalAnalyzedPartFacts("verse", hash, "b".repeat(64), analysis), melodyEvidence = emptyList()
        )

        val context = MusicalProcessingContextFactory.build(
            projection, input, EnhancementIntensity.BALANCED, profiles = BundledCompositionProfileCatalog.load()
        )

        assertEquals("verse-1", context.occurrenceId)
        assertEquals(1, context.harmony.size)
        assertThrows(IllegalArgumentException::class.java) {
            MusicalProcessingContextFactory.build(
                projection.copy(harmony = listOf(chord(occurrences[0]), chord(occurrences[1], 2))),
                input,
                profiles = BundledCompositionProfileCatalog.load()
            )
        }
    }

    @Test
    fun `validated applier publishes only a bounded MIDI draft and reports its plan hash`() {
        val input = root.resolve("corrected.mid")
        writeMidi(input)
        val context = context(input)
        val plan = EnhancementPlan(
            subjectHash = sha256Subject(context), inputSha256 = context.correctedInputSha256, contextSha256 = context.contextSha256,
            processorId = "fixture", processorVersion = "1", placeholder = false,
            model = EnhancementModelIdentity("qwen", "fixture", "1", "apache-2.0"),
            goals = setOf(EnhancementGoal.FLOW_CONTOUR), edits = listOf(EnhancementEdit(EnhancementEditKind.VELOCITY, context.notes[1].id, 2, EnhancementGoal.FLOW_CONTOUR, "smooth contour"))
        )
        val output = root.resolve("enhanced.mid")
        val report = ValidatedEnhancementMidiApplier().apply(input, output, context, plan)
        assertTrue(Files.isRegularFile(output))
        assertEquals(1, report.appliedEdits.size)
        assertTrue(report.anchorsRetained)
        assertTrue(report.acceptedPlanSha256 != null)
        assertEquals(MidiMutationStage.ENHANCE, report.mutationReport?.stage)
    }

    @Test
    fun `validated applier may add remove and resize notes without changing melody anchors or source`() {
        val input = root.resolve("corrected.mid")
        writeMidi(input, noteCount = 40)
        val sourceBytes = Files.readAllBytes(input)
        val context = context(input, intensity = EnhancementIntensity.CREATIVE)
        val plan = EnhancementPlan(
            subjectHash = sha256Subject(context), inputSha256 = context.correctedInputSha256, contextSha256 = context.contextSha256,
            processorId = "fixture", processorVersion = "1", placeholder = false,
            model = EnhancementModelIdentity("qwen", "fixture", "1", "apache-2.0"),
            goals = setOf(EnhancementGoal.PASSING_NOTE, EnhancementGoal.PHRASE_ENDING),
            edits = listOf(
                EnhancementEdit(EnhancementEditKind.REMOVE_NOTE, context.notes[5].id, goal = EnhancementGoal.REPETITION_REDUCTION, reason = "remove repeated middle note"),
                EnhancementEdit(EnhancementEditKind.ADD_NOTE, "add-00000", goal = EnhancementGoal.PASSING_NOTE, reason = "connect the middle phrase", pitch = 62,
                    velocity = 68, startTick = 2_400, durationTicks = 120, channel = 0, anchorNoteId = context.notes[4].id),
                EnhancementEdit(EnhancementEditKind.DURATION, context.notes[10].id, 360, EnhancementGoal.PHRASE_ENDING, "shape the phrase ending")
            )
        )

        val output = root.resolve("enhanced-structural.mid")
        val report = ValidatedEnhancementMidiApplier().apply(input, output, context, plan)
        val notes = midiNotes(output)

        assertArrayEquals(sourceBytes, Files.readAllBytes(input))
        assertEquals(40, notes.size)
        assertEquals(60 to 60, notes.first().second to notes.last().second)
        assertTrue(notes.any { (start, pitch, end) -> start == 2_400L && pitch == 62 && end == 2_520L })
        assertTrue(notes.any { (start, _, end) -> start == 4_800L && end == 5_160L })
        assertEquals(7, report.identityDistancePercent)
    }

    @Test
    fun `pitch edits use the active chord at the edited bar`() {
        val input = root.resolve("chord-change.mid")
        writeMidi(input)
        val base = context(input)
        val context = withHarmony(base, listOf(
            EnhancementHarmonicSpan(base.occurrenceId, 0, 480, 0, ChordQuality.MAJOR),
            EnhancementHarmonicSpan(base.occurrenceId, 480, 100_000, 2, ChordQuality.MAJOR)
        ))
        val plan = plan(context, EnhancementEdit(EnhancementEditKind.PITCH, context.notes[1].id, 2, reason = "fit D major"))

        ValidatedEnhancementMidiApplier().apply(input, root.resolve("chord-change-out.mid"), context, plan)
    }

    @Test
    fun `short resolving scale passing tone is accepted while a sustained clash is rejected`() {
        val shortInput = root.resolve("passing.mid")
        writeMidi(shortInput, durationTicks = 120)
        val shortContext = context(shortInput)
        val passing = plan(shortContext, EnhancementEdit(EnhancementEditKind.PITCH, shortContext.notes[1].id, 2, EnhancementGoal.PASSING_NOTE, "resolve to C"))
        val first = ValidatedEnhancementMidiApplier().apply(shortInput, root.resolve("passing-a.mid"), shortContext, passing)
        val second = ValidatedEnhancementMidiApplier().apply(shortInput, root.resolve("passing-b.mid"), shortContext, passing)

        assertEquals(Files.readAllBytes(root.resolve("passing-a.mid")).toList(), Files.readAllBytes(root.resolve("passing-b.mid")).toList())
        assertEquals(first.mutationReport, second.mutationReport)
        val sustainedInput = root.resolve("clash.mid")
        writeMidi(sustainedInput)
        val sustainedContext = context(sustainedInput)
        val clash = plan(sustainedContext, EnhancementEdit(EnhancementEditKind.PITCH, sustainedContext.notes[1].id, 2, EnhancementGoal.CHORD_CLASH, "sustained clash"))
        assertThrows(IllegalArgumentException::class.java) {
            ValidatedEnhancementMidiApplier().apply(sustainedInput, root.resolve("clash-out.mid"), sustainedContext, clash)
        }
        assertFalse(Files.exists(root.resolve("clash-out.mid")))
    }

    private fun writeMidi(path: Path, noteCount: Int = 20, durationTicks: Long = 240) {
        val sequence = javax.sound.midi.Sequence(javax.sound.midi.Sequence.PPQ, 480)
        val track = sequence.createTrack()
        repeat(noteCount) { index ->
            val start = index * 480L
            track.add(javax.sound.midi.MidiEvent(javax.sound.midi.ShortMessage(javax.sound.midi.ShortMessage.NOTE_ON, 0, 60, 70), start))
            track.add(javax.sound.midi.MidiEvent(javax.sound.midi.ShortMessage(javax.sound.midi.ShortMessage.NOTE_OFF, 0, 60, 0), start + durationTicks))
        }
        javax.sound.midi.MidiSystem.write(sequence, 1, path.toFile())
    }

    private fun midiNotes(path: Path): List<Triple<Long, Int, Long>> {
        val active = mutableMapOf<Pair<Int, Int>, ArrayDeque<Long>>()
        val notes = mutableListOf<Triple<Long, Int, Long>>()
        javax.sound.midi.MidiSystem.getSequence(path.toFile()).tracks.forEach { track ->
            (0 until track.size()).forEach { index ->
                val event = track[index]
                val message = event.message as? javax.sound.midi.ShortMessage ?: return@forEach
                val key = message.channel to message.data1
                if (message.command == javax.sound.midi.ShortMessage.NOTE_ON && message.data2 > 0) {
                    active.getOrPut(key) { ArrayDeque() }.addLast(event.tick)
                } else if (message.command == javax.sound.midi.ShortMessage.NOTE_OFF ||
                    message.command == javax.sound.midi.ShortMessage.NOTE_ON && message.data2 == 0) {
                    notes += Triple(active.getValue(key).removeFirst(), message.data1, event.tick)
                }
            }
        }
        return notes.sortedWith(compareBy<Triple<Long, Int, Long>> { it.first }.thenBy { it.second })
    }

    private fun sha256Subject(context: MusicalProcessingContext): String = java.security.MessageDigest.getInstance("SHA-256")
        .digest("${context.partId}|${context.occurrenceId}".toByteArray()).joinToString("") { "%02x".format(it) }

    private fun context(
        input: Path,
        key: MusicalKey = MusicalKey(PitchClass.of(PitchSpelling.C), ScaleModeId.MAJOR),
        harmony: HarmonySettings? = null,
        tempo: Double = 80.0,
        meter: TimeSignature = TimeSignature(4, 4),
        mood: MoodRef = MoodRef("warm", 1),
        section: SectionTypeId = SectionTypeId.VERSE,
        partId: String = "A",
        intensity: EnhancementIntensity = EnhancementIntensity.SUBTLE,
        seed: Long = 0,
        pipelineVersion: String = "enhancement-v1"
    ): MusicalProcessingContext {
        val ppq = runCatching { javax.sound.midi.MidiSystem.getSequence(input.toFile()).resolution }.getOrDefault(480)
        val notes = runCatching { MelodyIdentityBuilder.build(input, ppq * 4L / meter.denominator).notes.map { note ->
            EnhancementNoteSummary(note.id.value, note.channel, note.pitch, note.velocity, note.originalStartTick, note.originalEndTick, 0)
        } }.getOrDefault(emptyList())
        val chord = harmony?.progressions?.firstOrNull()?.events?.firstOrNull()
        val occurrence = harmony?.progressions?.firstOrNull()?.sectionType?.value ?: section.value
        val bare = MusicalProcessingContext(
            projectKey = key,
            scalePitchClasses = key.scalePitchClasses().map { it.chromatic },
            occurrenceId = "$occurrence-1",
            harmony = listOf(EnhancementHarmonicSpan("$occurrence-1", 0, 100_000, chord?.root?.chromatic ?: 0, chord?.quality ?: ChordQuality.MAJOR)),
            bpm = tempo.toInt(), ppq = ppq, meterNumerator = meter.numerator, meterDenominator = meter.denominator,
            profile = EnhancementProfileContext(CompositionProfileRef("lofi", 1), mood, "a".repeat(64), 50, 20, 12),
            authorityContextSha256 = "c".repeat(64),
            partId = partId, correctedInputSha256 = java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(input)).joinToString("") { "%02x".format(it) },
            intensity = intensity, seed = seed, pipelineVersion = pipelineVersion, notes = notes, contextSha256 = "0".repeat(64)
        )
        return bare.copy(contextSha256 = MusicalProcessingContextHasher.hash(bare))
    }

    private fun withHarmony(context: MusicalProcessingContext, harmony: List<EnhancementHarmonicSpan>): MusicalProcessingContext {
        val bare = context.copy(harmony = harmony, contextSha256 = "0".repeat(64))
        return bare.copy(contextSha256 = MusicalProcessingContextHasher.hash(bare))
    }

    private fun plan(context: MusicalProcessingContext, edit: EnhancementEdit) = EnhancementPlan(
        subjectHash = sha256Subject(context), inputSha256 = context.correctedInputSha256, contextSha256 = context.contextSha256,
        processorId = "fixture", processorVersion = "1", placeholder = false,
        model = EnhancementModelIdentity("qwen", "fixture", "1", "apache-2.0"), edits = listOf(edit)
    )

    private fun project(
        key: MusicalKey = MusicalKey(PitchClass.of(PitchSpelling.C), ScaleModeId.MAJOR),
        harmony: HarmonySettings? = null,
        tempo: Double = 80.0,
        meter: TimeSignature = TimeSignature(4, 4),
        mood: MoodRef = MoodRef("warm", 1),
        section: SectionTypeId = SectionTypeId.VERSE,
        partId: String = "A",
        profile: CompositionProfileRef = CompositionProfileRef("lofi", 1)
    ) = Project(
        version = Project.CURRENT_VERSION,
        name = "Test",
        parts = listOf(SongPart(partId, "source/$partId.mid", sectionType = section)),
        envelope = ProjectV4Envelope(
            compositionSettings = CompositionSettings(key = key, tempo = Tempo(tempo), timeSignature = meter, profile = profile, mood = mood,
                decisionRevision = 1, resolvedProfileSha256 = "a".repeat(64), decisionSha256 = "b".repeat(64)),
            harmony = harmony
        )
    )

    private fun harmony(section: String) = HarmonySettings(progressions = listOf(ChordProgression(
        HarmonySectionTypeId(section), listOf(ChordEvent(ChordEventId("c-1"), PitchClass.of(PitchSpelling.C), ChordQuality.MAJOR, 0))
    )))
}
