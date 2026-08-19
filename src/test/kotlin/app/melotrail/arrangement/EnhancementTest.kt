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
