package app.melotrail.arrangement

import app.melotrail.audio.AudioBuffer
import app.melotrail.audio.AudioFormat
import app.melotrail.audio.WAVDecoder
import app.melotrail.model.ErrorReporter
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ProductionStemMixerTest {
    private val format = RenderFormat(8_000, 2, 24)

    @Test
    fun `production mixer applies shared room bus processing and section automation deterministically`() {
        val piano = track("piano", FloatArray(640) { 0.4f })
        val plan = MixPlan(
            tracks = mapOf("piano" to MixTrackPlan(
                gainDb = -3.0, pan = -0.5, reverbSend = 1.0, stereoWidth = 1.4,
                filter = FilterPlan(lowPassHz = 4_000.0), eq = listOf(EqBandPlan(200.0, -3.0)),
                compression = CompressionPlan(enabled = true),
                sectionAutomation = listOf(SectionMixAutomation("verse-1", 2, 4, gainDb = -12.0, pan = 0.5))
            )),
            room = SharedRoomPlan(enabled = true, decaySeconds = 0.6, mix = 0.4),
            buses = mapOf(MixBus.MUSIC to MixBusPlan(compression = CompressionPlan(enabled = true)), MixBus.DRUMS to MixBusPlan(enabled = false))
        )

        val mixed = ProductionStemMixer().mix(listOf(piano), plan, format)

        assertTrue(mixed.buffer.samples.all { it.isFinite() })
        assertTrue(mixed.buffer.samples.drop(560).any { kotlin.math.abs(it) > 0.0001f }, "shared room should persist beyond the dry signal")
        assertNotEquals(mixed.buffer.getSample(0, 1), mixed.buffer.getSample(1, 1), "stereo placement must be rendered")
        assertTrue(kotlin.math.abs(mixed.buffer.getSample(0, 2)) < kotlin.math.abs(mixed.buffer.getSample(0, 0)), "section automation should reduce level")
    }

    @Test
    fun `audio critic blocks inaudible melody and measures loudness and stereo correlation`() {
        val piano = track("piano", FloatArray(32) { 0.01f })
        val bass = track("bass", FloatArray(32) { if (it % 2 == 0) 0.8f else 0.8f })
        val mixed = DeterministicStemMixer().mix(listOf(piano, bass), MixSettings(requiredFormat = format))

        val report = AudioMixCritic.analyze(mixed, listOf(piano, bass), "a".repeat(64), "b".repeat(64))

        assertFalse(report.commercialReady)
        assertFalse(requireNotNull(report.melodyAudibility).audible)
        assertTrue(report.stemLoudness.map(StemLoudness::stem).containsAll(listOf("piano", "bass")))
        assertTrue(report.stereoCorrelation != null)
        assertTrue(report.issues.any { it.kind == AudioMixIssueKind.MELODY_AUDIBILITY && it.severity == AudioMixIssueSeverity.BLOCKING })
    }

    @Test
    fun `smoke renders a production WAV with shared ambience and a hash-bound critic report`() {
        val piano = track("piano", FloatArray(1_600) { if (it < 2) 0.6f else 0f })
        val plan = MixPlan(tracks = mapOf("piano" to MixTrackPlan(reverbSend = 0.8)), room = SharedRoomPlan(mix = 0.3))
        val mixed = ProductionStemMixer().mix(listOf(piano), plan, format)
        val file = Files.createTempFile("production-mix-smoke", ".wav")
        try {
            DeterministicStemMixer().writeWav(mixed, file)
            val decoded = WAVDecoder(ErrorReporter.NoOp).decode(file)
            val report = AudioMixCritic.analyze(mixed, listOf(piano), "a".repeat(64), sha256(Files.readAllBytes(file)))

            assertTrue(decoded.format.sampleRate == format.sampleRate && decoded.format.channels == format.channels && decoded.format.bitDepth == 24)
            assertTrue(decoded.length == mixed.buffer.length)
            assertTrue(decoded.samples.drop(560).any { kotlin.math.abs(it) > 0.0001f })
            assertTrue(report.mixSha256.length == 64)
        } finally {
            Files.deleteIfExists(file)
        }
    }

    private fun track(name: String, samples: FloatArray) = MixTrack(
        name, AudioBuffer(samples, AudioFormat(format.sampleRate, format.channels, 24, false, false, "WAV"), samples.size.toDouble() / format.channels / format.sampleRate)
    )

    private fun sha256(bytes: ByteArray): String = java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
