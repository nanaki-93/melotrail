package app.melotrail.arrangement

import app.melotrail.music.Tempo
import app.melotrail.music.TimeSignature
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReleaseSimilarityTest {
    private val critic = ReleaseSimilarityCritic()

    @Test
    fun `fingerprint is deterministic and covers every release arrangement feature`() {
        val first = critic.fingerprint(arrangement(), Tempo(82.0), TimeSignature(4, 4))
        val second = critic.fingerprint(arrangement(), Tempo(82.0), TimeSignature(4, 4))

        assertEquals(first, second)
        assertEquals(listOf("A1:verse:introduction", "B1:chorus:climax"), first.structure)
        assertEquals(listOf(300, 900), first.energyCurve)
        assertEquals(listOf("enter=bass,piano;exit=", "enter=drums,pad;exit="), first.instrumentEntryExitSequence)
        assertEquals(listOf("bass.root-fifth:root_fifth:root_motion", "bass.octave:octave:octaves"), first.bassPatternSequence)
        assertEquals(listOf("absent", "drums.lazy-swing:swung:last_bar"), first.drumGrooveSequence)
        assertEquals(listOf("bridge:1:bass_pickup", "none:0:"), first.transitionSequence)
        assertEquals(listOf(0, 120), first.swingProfile)
        assertEquals(listOf(650, 750), first.arrangementDensityCurve)
    }

    @Test
    fun `comparison produces advisory feature explanations and warning without a policy verdict`() {
        val original = critic.fingerprint(arrangement(), Tempo(82.0), TimeSignature(4, 4))
        val same = critic.review(original, listOf(original))
        assertEquals(ReleaseSimilarityReviewStatus.NOT_COMPARED, same.status, "A release is never compared with itself")

        val close = critic.fingerprint(arrangement(secondEnergy = 0.85), Tempo(82.0), TimeSignature(4, 4))
        val report = critic.review(original, listOf(close))

        assertEquals(ReleaseSimilarityReviewStatus.WARNING, report.status)
        assertEquals(1, report.comparisonCount)
        assertTrue(report.highestSimilarityScore!! >= 0.80)
        assertEquals(ReleaseSimilarityFeature.entries.toList(), report.comparisons.single().explanations.map { it.feature })
        assertTrue(report.comparisons.single().explanations.all { it.explanation.isNotBlank() && it.score in 0.0..1.0 })
        assertTrue(report.optionalReplanScope.containsAll(listOf("arrangement", "groove", "orchestration")))
        assertTrue(report.advisory.contains("does not determine YouTube Partner Program eligibility"))
        assertFalse(report.advisory.contains("is eligible"))
    }

    private fun arrangement(secondEnergy: Double = 0.9) = DetailedArrangement(sections = listOf(
        DetailedArrangementSection(
            index = 0, instanceId = "A1", partId = "verse", role = SongSectionPurpose.INTRODUCTION, energy = 0.3,
            instruments = listOf(
                PianoSourcePlan(),
                BassInstrumentPlan(role = DetailedBassRole.ROOT_FIFTH, density = 0.3, movement = DetailedBassMovement.ROOT_MOTION,
                    register = MusicalRegister.LOW, syncopation = 0.1, pattern = BassPatternId.ROOT_FIFTH)
            ),
            transitionOut = TransitionPlan(TransitionType.BRIDGE, bars = 1, bridge = BridgePlan(elements = listOf(BridgeElement.BASS_PICKUP)))
        ),
        DetailedArrangementSection(
            index = 1, instanceId = "B1", partId = "chorus", role = SongSectionPurpose.CLIMAX, energy = secondEnergy,
            instruments = listOf(
                PianoSourcePlan(),
                BassInstrumentPlan(role = DetailedBassRole.OCTAVE, density = 0.9, movement = DetailedBassMovement.OCTAVES,
                    register = MusicalRegister.LOW, syncopation = 0.2, pattern = BassPatternId.OCTAVE),
                DrumsInstrumentPlan(role = DrumsRole.BUILD, density = 0.9, kickDensity = 0.8, snarePattern = SnarePattern.BEATS_2_4,
                    hiHatDensity = 0.9, swing = 0.12, fillLastBar = true, pattern = DrumGroovePatternId.LAZY_SWING,
                    grooveCharacter = GrooveCharacter.SWUNG, fillPlacement = DrumFillPlacement.LAST_BAR),
                PadInstrumentPlan(role = SustainedRole.TEXTURE, density = 0.2, register = MusicalRegister.MID)
            ),
            transitionOut = TransitionPlan()
        )
    ))
}
