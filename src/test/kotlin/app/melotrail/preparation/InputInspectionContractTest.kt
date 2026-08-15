package app.melotrail.preparation

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InputInspectionContractTest {
    @Test
    fun `audio report round trips through strict versioned json`() {
        val root = Files.createTempDirectory("inspection-contract")
        val report = audioReport()

        InputInspectionReportStore.write(root, report)

        assertEquals(report, InputInspectionReportStore.read(root, "intro"))
        assertTrue(Files.isRegularFile(root.resolve("prepared/intro/report.json")))
    }

    @Test
    fun `report paths are confined to the expected project preparation directory`() {
        val root = Files.createTempDirectory("inspection-path")
        assertEquals(root.toAbsolutePath().normalize().resolve("prepared/intro/report.json"), InputInspectionPaths.report(root, "intro"))
        assertEquals(root.toAbsolutePath().normalize().resolve("prepared/intro/decoded.wav"), InputInspectionPaths.decodedWav(root, "intro"))
        assertEquals(root.toAbsolutePath().normalize().resolve("prepared/intro/clean.wav"), InputInspectionPaths.cleanWav(root, "intro"))
        assertFailsWith<IllegalArgumentException> { InputInspectionPaths.requirePartId("../escape") }
        assertFailsWith<IllegalArgumentException> { InspectionSourceIdentity("/Users/me/input.wav", digest()).requireValid() }
        assertFailsWith<IllegalArgumentException> { InspectionSourceIdentity("source/../outside.wav", digest()).requireValid() }
    }

    @Test
    fun `report rejects invalid fingerprints non finite measurements and external paths`() {
        assertFailsWith<IllegalArgumentException> { audioReport().copy(source = InspectionSourceIdentity("source/intro.wav", "ABC")).requireValid() }
        assertFailsWith<IllegalArgumentException> { audioReport().copy(measurements = measurements().copy(peak = Double.NaN)).requireValid() }
        assertFailsWith<IllegalArgumentException> { audioReport().copy(measurements = measurements().copy(rms = Double.POSITIVE_INFINITY)).requireValid() }
        assertFailsWith<IllegalArgumentException> { audioReport().copy(warnings = listOf("See /private/audio.wav")).requireValid() }
    }

    @Test
    fun `unknown report versions are rejected while a missing v1 version migrates to version one`() {
        val root = Files.createTempDirectory("inspection-version")
        val target = InputInspectionPaths.report(root, "intro")
        Files.createDirectories(target.parent)
        val v1WithoutVersion = """
            {"partId":"intro","source":{"relativePath":"source/intro.wav","sha256":"${digest()}"},"detectedInput":{"container":"RIFF_WAVE","codec":"PCM_24","extension":"wav"},"durationSeconds":1.0,"audioFormat":{"sampleRate":44100,"channels":2,"bitsPerSample":24},"measurements":{"peak":0.9,"rms":0.2,"dcOffset":0.0,"clippedRunCount":0,"clippedFrameCount":0,"silence":{"silentFrames":0,"longestSilentFrames":0},"hum":{"evidence":"NONE","confidence":0.0},"noise":{"evidence":"LOW","confidence":0.1}}}
        """.trimIndent()
        Files.writeString(target, v1WithoutVersion)
        assertEquals(1, InputInspectionReportStore.read(root, "intro").version)

        Files.writeString(target, v1WithoutVersion.replaceFirst("{", "{\"version\":99,"))
        assertFailsWith<IllegalArgumentException> { InputInspectionReportStore.read(root, "intro") }
    }

    @Test
    fun `midi report forbids audio-only measurements`() {
        val midi = InputInspectionReport(
            partId = "midi_1",
            source = InspectionSourceIdentity("source/midi_1.mid", digest()),
            detectedInput = DetectedInput(InputContainer.MIDI, "SMF", "mid"),
            durationSeconds = 2.0,
            toolVersions = mapOf("inspector" to "1.0")
        )
        midi.requireValid()
        assertFailsWith<IllegalArgumentException> { midi.copy(measurements = measurements()).requireValid() }
        assertFalse(InputInspectionPaths.report(Files.createTempDirectory("inspection-midi"), "midi_1").toString().contains(".."))
    }

    private fun audioReport() = InputInspectionReport(
        partId = "intro",
        source = InspectionSourceIdentity("source/intro.wav", digest()),
        detectedInput = DetectedInput(InputContainer.RIFF_WAVE, "PCM_24", "wav"),
        durationSeconds = 1.25,
        audioFormat = DetectedAudioFormat(44_100, 2, 24),
        measurements = measurements(),
        warnings = listOf("Low-level stationary noise was detected."),
        toolVersions = mapOf("input-inspector" to "1.0.0")
    )

    private fun measurements() = AudioInspectionMeasurements(
        peak = 0.9,
        rms = 0.2,
        dcOffset = 0.01,
        clippedRunCount = 0,
        clippedFrameCount = 0,
        silence = SilenceEvidence(0, 0),
        hum = SignalIndicator(EvidenceLevel.NONE, 0.0),
        noise = SignalIndicator(EvidenceLevel.LOW, 0.1)
    )

    private fun digest() = "a".repeat(64)
}
