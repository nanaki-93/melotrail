package app.melotrail.arrangement

import app.melotrail.audio.AudioBuffer
import app.melotrail.audio.AudioFormat
import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LowEndInteractionTest {
    private val format = AudioFormat(8_000, 1, 24, false, false, "WAV")
    private val hash = "a".repeat(64)

    @Test
    fun `coincident approved kick and bass evidence derives bounded duck then recovers`() {
        val drums = tone(2_400, 100.0, 0.55f)
        val bass = tone(2_400, 100.0, 0.55f)
        val plan = LowEndInteractionPlanner.derive(
            drums, bass, hash, hash, hash, hash, hash, 9, 36,
            listOf(LowEndKickTrigger(0, 200))
        )

        assertEquals(LowEndInteractionStatus.ACTIVE, plan.status)
        assertTrue(plan.duckingDb in 2.0..4.0)
        assertTrue(requireNotNull(plan.bassHighPassHz) in 30.0..60.0)
        val processed = LowEndInteractionProcessor.process("bass", bass, plan)

        assertTrue(windowRms(processed.samples, 240) < windowRms(bass.samples, 240) * 0.8, "bass must duck after the approved kick")
        assertTrue(windowRms(processed.samples, 2_000) > windowRms(processed.samples, 240) * 1.25, "bass must recover after the bounded release")
        assertEquals(bass.length, processed.length)
        assertEquals(bass.duration, processed.duration)
    }

    @Test
    fun `no kick or no bass span is materially unchanged`() {
        val bass = tone(800, 95.0, 0.4f)
        val noKickPlan = LowEndInteractionPlanner.derive(null, bass, null, hash, null, null, hash, null, null, emptyList())

        assertEquals(LowEndInteractionStatus.NOT_APPLICABLE, noKickPlan.status)
        assertTrue(LowEndInteractionProcessor.process("bass", bass, noKickPlan).samples.contentEquals(bass.samples))
        assertTrue(LowEndInteractionProcessor.process("bass", bass, null).samples.contentEquals(bass.samples))
    }

    @Test
    fun `time and band aware critic retains before after collision evidence without pumping`() {
        val drums = tone(2_400, 100.0, 0.55f)
        val bass = tone(2_400, 100.0, 0.55f)
        val plan = LowEndInteractionPlanner.derive(drums, bass, hash, hash, hash, hash, hash, 9, 36, listOf(LowEndKickTrigger(0, 200)))
        val mixed = DeterministicStemMixer().mix(listOf(MixTrack("drums", drums), MixTrack("bass", bass)), MixSettings(requiredFormat = RenderFormat(8_000, 1, 24)))

        val report = AudioMixCritic.analyze(mixed, listOf(MixTrack("drums", drums), MixTrack("bass", bass)), hash, hash, plan)
        val lowEnd = requireNotNull(report.lowEndInteraction)

        assertTrue(lowEnd.after.combinedPeakDbfs < plan.before.combinedPeakDbfs - 1.0)
        assertTrue(lowEnd.timingPreserved && lowEnd.durationPreserved)
        assertFalse(lowEnd.pumpingDetected)
        assertFalse(lowEnd.severeUnresolvedOverlap)
    }

    private fun tone(frames: Int, hz: Double, amplitude: Float): AudioBuffer = AudioBuffer(
        FloatArray(frames) { frame -> (sin(2.0 * PI * hz * frame / format.sampleRate) * amplitude).toFloat() },
        format,
        frames.toDouble() / format.sampleRate
    )

    private fun windowRms(samples: FloatArray, start: Int): Double = kotlin.math.sqrt(
        (start until start + 64).sumOf { samples[it].toDouble() * samples[it] } / 64.0
    )
}
