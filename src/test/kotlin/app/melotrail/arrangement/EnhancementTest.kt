package app.melotrail.arrangement

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
        assertThrows(IllegalArgumentException::class.java) {
            MusicalProcessingContextFactory.build(project(profile = CompositionProfileRef("unknown", 1)), "A", input, profiles = BundledCompositionProfileCatalog.load())
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
        val lowercaseWirePlan = LocalQwenEnhancementPlanner(LocalQwenClient { _, _ -> """{"goals":["flow_contour","passing_note"],"edits":[{"kind":"velocity","noteId":"n-00001","value":2,"goal":"flow_contour","reason":"smooth contour"},{"kind":"velocity","noteId":"n-00002","value":9,"goal":"flow_contour","reason":"outside subtle policy"}]}""" }, EnhancementModelIdentity("qwen", "fixture", "1", "apache-2.0"))
            .plan(context)
        assertEquals(setOf(EnhancementGoal.FLOW_CONTOUR, EnhancementGoal.PASSING_NOTE), lowercaseWirePlan.goals)
        assertEquals(EnhancementEdit(EnhancementEditKind.VELOCITY, "n-00001", 2, EnhancementGoal.FLOW_CONTOUR, "smooth contour"), lowercaseWirePlan.edits.single())
        val legacyWrongIdentity = LocalQwenEnhancementPlanner(LocalQwenClient { _, _ -> """{"version":999,"subjectHash":"${"0".repeat(64)}","inputSha256":"${"0".repeat(64)}","contextSha256":"${"0".repeat(64)}","goals":[],"edits":[]}""" }, EnhancementModelIdentity("qwen", "fixture", "1", "apache-2.0"))
        assertEquals(accepted, legacyWrongIdentity.plan(context))
        val unsupportedGoal = LocalQwenEnhancementPlanner(LocalQwenClient { _, _ -> """{"goals":["outside_the_contract"],"edits":[]}""" }, EnhancementModelIdentity("qwen", "fixture", "1", "apache-2.0"))
        assertThrows(IllegalArgumentException::class.java) { unsupportedGoal.plan(context) }
        val unknown = LocalQwenEnhancementPlanner(LocalQwenClient { _, _ -> valid }, EnhancementModelIdentity("qwen", "fixture", "1", "unknown"))
        assertThrows(IllegalArgumentException::class.java) { unknown.plan(context) }
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
            goals = setOf(EnhancementGoal.FLOW_CONTOUR), edits = listOf(EnhancementEdit(EnhancementEditKind.VELOCITY, "n-00001", 2, EnhancementGoal.FLOW_CONTOUR, "smooth contour"))
        )
        val output = root.resolve("enhanced.mid")
        val report = ValidatedEnhancementMidiApplier().apply(input, output, context, plan)
        assertTrue(Files.isRegularFile(output))
        assertEquals(1, report.appliedEdits.size)
        assertTrue(report.anchorsRetained)
        assertTrue(report.acceptedPlanSha256 != null)
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
                EnhancementEdit(EnhancementEditKind.REMOVE_NOTE, "n-00005", goal = EnhancementGoal.REPETITION_REDUCTION, reason = "remove repeated middle note"),
                EnhancementEdit(EnhancementEditKind.ADD_NOTE, "add-00000", goal = EnhancementGoal.PASSING_NOTE, reason = "connect the middle phrase", pitch = 62,
                    velocity = 68, startTick = 2_400, durationTicks = 120, channel = 0, anchorNoteId = "n-00004"),
                EnhancementEdit(EnhancementEditKind.DURATION, "n-00010", 360, EnhancementGoal.PHRASE_ENDING, "shape the phrase ending")
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

    private fun writeMidi(path: Path, noteCount: Int = 20) {
        val sequence = javax.sound.midi.Sequence(javax.sound.midi.Sequence.PPQ, 480)
        val track = sequence.createTrack()
        repeat(noteCount) { index ->
            val start = index * 480L
            track.add(javax.sound.midi.MidiEvent(javax.sound.midi.ShortMessage(javax.sound.midi.ShortMessage.NOTE_ON, 0, 60, 70), start))
            track.add(javax.sound.midi.MidiEvent(javax.sound.midi.ShortMessage(javax.sound.midi.ShortMessage.NOTE_OFF, 0, 60, 0), start + 240))
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
        .digest("${context.partId}|${context.sectionId}".toByteArray()).joinToString("") { "%02x".format(it) }

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
    ) = MusicalProcessingContextFactory.build(
        project(key, harmony, tempo, meter, mood, section, partId), partId, input, intensity, seed, pipelineVersion, BundledCompositionProfileCatalog.load()
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
