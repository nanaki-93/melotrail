package app.melotrail.application

import app.melotrail.midi.adapter.JdkMidiReader
import app.melotrail.midi.domain.MidiControlChangeEvent
import app.melotrail.midi.domain.MidiNoteEvent
import app.melotrail.midi.domain.MidiPitchBendEvent
import app.melotrail.midi.domain.MidiTimeSignatureEvent
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class MidiCoreComparisonHarnessTest {
    @TempDir lateinit var root: Path

    @Test
    fun `freezes deterministic side by side packages and an unscored review form`() {
        val output = Path.of("build", "m01-comparison")
        val review = MidiCoreComparisonHarness(root.resolve("work")).writeSideBySideReview(M01ComparisonFixtures.cases, output)

        assertEquals(M01ComparisonFixtures.cases.size, review.baseline.size)
        assertTrue(review.baseline.all { it is M01ComparisonCapture.Published })
        assertTrue(review.candidate.all { it is M01ComparisonCapture.Published })
        assertTrue(review.comparisons.all { comparison ->
            !comparison.equivalent && comparison.differences.any { it == "Frozen style, seed, or engine inputs differ." }
        })
        assertTrue(Files.isRegularFile(review.reviewForm))
        val form = Files.readString(review.reviewForm)
        assertTrue(form.contains("5/10 average"))
        assertTrue(form.contains("___ / 10"))
        assertFalse(form.contains("8/10"))
        assertTrue(form.contains("Baseline: steady-road / seed 901 / `midi-core-style-v1` / style catalog 1"))
        assertTrue(form.contains("Candidate: steady-road / seed 901 / `midi-core-style-v2` / style catalog 2"))
        review.baseline.filterIsInstance<M01ComparisonCapture.Published>().forEach { capture ->
            assertTrue(capture.sourceUnchanged, capture.case.id)
            assertEquals(setOf("complete-song.mid", "melody.mid", "chords.mid", "bass.mid", "drums.mid"), capture.midiSha256.keys)
            assertEquals(capture.midiSha256.keys, capture.semanticSha256.keys)
            assertTrue(capture.frozenInputsSha256.matches(Regex("[0-9a-f]{64}")), capture.case.id)
            assertTrue(capture.midiSha256.values.all { it.matches(Regex("[0-9a-f]{64}")) }, capture.case.id)
            assertTrue(Files.isRegularFile(capture.packageDirectory.resolve("manifest.json")))
            val comparisonManifest = capture.packageDirectory.resolve("comparison.json")
            assertTrue(Files.isRegularFile(comparisonManifest))
            assertTrue(Files.readString(comparisonManifest).contains("\"sourceUnchanged\": true"))
            assertTrue(Files.readString(comparisonManifest).contains("\"frozenInputsSha256\""))
            assertTrue(Files.readString(comparisonManifest).contains("\"patternCatalogVersion\""))
            assertTrue(Files.readString(comparisonManifest).contains("\"pianoMelodyLoop\""))
        }
        review.candidate.filterIsInstance<M01ComparisonCapture.Published>().forEach { capture ->
            assertTrue(capture.sourceUnchanged, capture.case.id)
            assertTrue(capture.packageDirectory.startsWith(output.resolve("candidate")), capture.case.id)
        }
    }

    @Test
    fun `case taxonomy and frozen manifest inputs cover every M01 development scenario`() {
        val required = setOf(
            "held-close-melody-piano", "passing-tones", "low-register", "sub-bar-chords", "repeated-chorus", "bridge",
            "expressive-pedal", "initial-silence", "dense-sparse-phrases", "3-4", "6-8", "ending",
        )
        val declared = M01ComparisonFixtures.cases.flatMap { it.scenarioTags }.toSet()
        assertEquals(required, declared)
        M01ComparisonFixtures.cases.forEach { case ->
            assertTrue(case.sourceBytes.isNotEmpty(), case.id)
            assertTrue(case.occurrences.map(M01Occurrence::id).distinct().size == case.occurrences.size, case.id)
            assertTrue(case.chords.all { chord -> chord.occurrenceId in case.occurrences.map(M01Occurrence::id) }, case.id)
            val totalBars = case.occurrences.sumOf(M01Occurrence::bars)
            assertTrue(case.pianoMelodyLoop.endBar <= totalBars, case.id)
            assertTrue(case.fullArrangementLoop.endBar <= totalBars, case.id)
        }
    }

    @Test
    fun `each case can be captured twice with identical byte and semantic hashes`() {
        val harness = MidiCoreComparisonHarness(root.resolve("work"))

        M01ComparisonFixtures.cases.forEach { case ->
            val first = assertIs<M01ComparisonCapture.Published>(
                harness.captureCandidate(case, root.resolve("first-output")),
            )
            val second = assertIs<M01ComparisonCapture.Published>(
                harness.captureCandidate(case, root.resolve("second-output")),
            )

            assertEquals(first.midiSha256, second.midiSha256, case.id)
            assertEquals(first.semanticSha256, second.semanticSha256, case.id)
            assertEquals(first.manifestSha256, second.manifestSha256, case.id)
        }
    }

    @Test
    fun `baseline manifest validation rejects malformed frozen fields`() {
        val case = M01ComparisonFixtures.cases.first()
        val capture = assertIs<M01ComparisonCapture.Published>(
            MidiCoreComparisonHarness(root.resolve("work")).loadBaseline(case, root.resolve("output")),
        )
        val manifest = Files.readString(capture.packageDirectory.resolve("comparison.json"))
        val legacyAuthoritySha256 = case.baselineAuthoritySha256
        val legacyFrozenInputsSha256 = m01FrozenInputsSha256(
            capture.sourceSha256,
            legacyAuthoritySha256,
            capture.engineInputs,
        )
        val corruptions = mapOf(
            "scenario tags" to manifest.replace("\"held-close-melody-piano\"", "\"unowned-scenario\""),
            "source hash" to manifest.replace(capture.sourceSha256, "0".repeat(64)),
            "authority hash" to manifest.replace(legacyAuthoritySha256, "0".repeat(64)),
            "frozen inputs hash" to manifest.replace(legacyFrozenInputsSha256, "0".repeat(64)),
            "engine ID" to manifest.replace("midi-core-style-v", "changed-engine-v"),
            "style catalog" to manifest.replace("\"styleCatalogVersion\": 1", "\"styleCatalogVersion\": 999"),
            "pattern catalog" to manifest.replace("\"patternCatalogVersion\": 1", "\"patternCatalogVersion\": 999"),
            "performance catalog" to manifest.replace("\"performanceProfileCatalogVersion\": 1", "\"performanceProfileCatalogVersion\": 999"),
            "piano melody loop" to manifest.replace("\"pianoMelodyLoop\": {\"startBar\": 1, \"endBar\": 2}", "\"pianoMelodyLoop\": {\"startBar\": 2, \"endBar\": 2}"),
            "full arrangement loop" to manifest.replace("\"fullArrangementLoop\": {\"startBar\": 1, \"endBar\": 4}", "\"fullArrangementLoop\": {\"startBar\": 1, \"endBar\": 3}"),
        )

        corruptions.forEach { (field, malformed) ->
            assertFalse(manifest == malformed, "Test corruption did not alter $field")
            assertFailsWith<IllegalArgumentException>(field) {
                M01BaselinePackageResource.validateManifest(case, malformed)
            }
        }
    }

    @Test
    fun `v1 baseline remains loadable and reports simulated v2 candidate engine inputs`() {
        val case = M01ComparisonFixtures.cases.first()
        val harness = MidiCoreComparisonHarness(root.resolve("work"))
        val baseline = assertIs<M01ComparisonCapture.Published>(
            harness.loadBaseline(case, root.resolve("output")),
        )
        val v2Inputs = baseline.engineInputs.copy(
            styleCatalogVersion = 2,
            patternCatalogVersion = 2,
            performanceProfileCatalogVersion = 2,
            engineId = "midi-core-style-v2",
        )
        val simulatedV2Candidate = baseline.copy(
            engineInputs = v2Inputs,
            frozenInputsSha256 = m01FrozenInputsSha256(
                baseline.sourceSha256,
                baseline.authoritySha256,
                v2Inputs,
            ),
        )

        assertEquals(1L, baseline.engineInputs.styleCatalogVersion)
        assertEquals("midi-core-style-v1", baseline.engineInputs.engineId)
        val comparison = harness.compare(baseline, simulatedV2Candidate)
        assertFalse(comparison.equivalent)
        assertEquals(listOf("Frozen style, seed, or engine inputs differ."), comparison.differences)
    }

    @Test
    fun `full song fixture proves its declared musical scenarios from MIDI semantics`() {
        val fixture = M01ComparisonFixtures.cases.single { it.id == "repeated-chorus-bridge" }
        val source = root.resolve("${fixture.id}.mid")
        Files.write(source, fixture.sourceBytes)
        val events = JdkMidiReader().inspect(source).sequence.orderedEvents()
        val notes = events.filterIsInstance<MidiNoteEvent>()

        // Silence before the first melody onset is intentional: no generated
        // comparison may move the song origin to the first note.
        assertEquals(240L, notes.minOf { it.orderingKey.tick })
        // The opening sustained close-note case and the short passing tone are
        // deliberately distinct, rather than being labels on an arbitrary file.
        assertTrue(notes.any { it.pitch == 72 && it.orderingKey.tick == 240L && it.endTick == 960L })
        assertTrue(notes.any { it.pitch == 74 && it.orderingKey.tick == 960L && it.endTick == 1200L })
        assertTrue(notes.any { it.pitch == 50 && it.orderingKey.tick == 1920L && it.endTick == 2880L })
        // Pedal and pitch expression remain part of the protected source case.
        assertTrue(events.filterIsInstance<MidiControlChangeEvent>().any { it.controller == 64 && it.value == 127 && it.orderingKey.tick == 0L })
        assertTrue(events.filterIsInstance<MidiControlChangeEvent>().any { it.controller == 64 && it.value == 0 && it.orderingKey.tick == 7680L })
        assertTrue(events.filterIsInstance<MidiPitchBendEvent>().any { it.orderingKey.tick == 480L })
        // The off-bar harmony change and the repeated Chorus identity are
        // inputs to the comparison, not mere scenario tags.
        assertTrue(fixture.chords.any { it.startTick == 960L && it.endTick == 1920L })
        assertEquals(2, fixture.occurrences.count { it.sectionId == "chorus" })
        assertEquals("chorus-1", fixture.occurrences.first { it.sectionId == "chorus" }.id)
        assertEquals("chorus-2", fixture.occurrences.last { it.sectionId == "chorus" }.id)
        assertEquals(7680L, notes.maxOf(MidiNoteEvent::endTick))
    }

    @Test
    fun `meter fixtures prove their authored meters and final boundaries from MIDI semantics`() {
        val expected = mapOf("three-four" to Pair(3, 2), "six-eight" to Pair(6, 3))
        M01ComparisonFixtures.cases.filter { it.id in expected }.forEach { fixture ->
            val source = root.resolve("${fixture.id}.mid")
            Files.write(source, fixture.sourceBytes)
            val inspection = JdkMidiReader().inspect(source)
            val meter = inspection.sequence.orderedEvents().filterIsInstance<MidiTimeSignatureEvent>().single()

            assertEquals(expected.getValue(fixture.id).first, meter.numerator, fixture.id)
            assertEquals(expected.getValue(fixture.id).second, meter.denominatorExponent, fixture.id)
            assertEquals(
                fixture.occurrences.sumOf(M01Occurrence::bars) * 480L * fixture.meter.numerator * 4 / fixture.meter.denominator,
                inspection.sourceEndTick,
                fixture.id,
            )
        }
    }

    @Test
    fun `owned fixture headers use the authoritative 480 PPQ tick grid`() {
        M01ComparisonFixtures.cases.forEach { case ->
            val source = root.resolve("${case.id}.mid")
            Files.write(source, case.sourceBytes)

            val inspection = JdkMidiReader().inspect(source)
            val expectedEnd = case.occurrences.sumOf(M01Occurrence::bars) * 480L * case.meter.numerator * 4 / case.meter.denominator
            assertEquals(480, inspection.sequence.source.ppq.value, case.id)
            assertEquals(expectedEnd, inspection.sourceEndTick, case.id)
        }
    }

    @Test
    fun `invalid source case is rejected without producing a MIDI package`() {
        val output = root.resolve("output")
        val result = MidiCoreComparisonHarness(root.resolve("work")).captureCandidate(M01ComparisonFixtures.invalidCase(), output)

        val rejected = assertIs<M01ComparisonCapture.Rejected>(result)
        assertEquals("SOURCE_IMPORT", rejected.stage)
        assertFalse(Files.exists(output.resolve("candidate").resolve("invalid-source")))
    }

    @Test
    fun `invalid review loop is rejected before comparison capture`() {
        assertFailsWith<IllegalArgumentException> {
            M01ReviewLoop(0, 1)
        }
    }

    @Test
    fun `semantic comparison reports changed protected source independently of package paths`() {
        val harness = MidiCoreComparisonHarness(root.resolve("work"))
        val fixture = M01ComparisonFixtures.cases.first()
        val baseline = harness.loadBaseline(fixture, root.resolve("output"))
        val candidate = harness.captureCandidate(fixture, root.resolve("output"))
        val baselinePublished = baseline as M01ComparisonCapture.Published
        val candidatePublished = candidate as M01ComparisonCapture.Published

        // The immutable M01 manifest retains its original authority proof,
        // while the comparison projection carries M03's arrangement-end fact.
        assertTrue(Files.readString(baselinePublished.packageDirectory.resolve("comparison.json")).contains(fixture.baselineAuthoritySha256))
        assertFalse(baselinePublished.authoritySha256 == fixture.baselineAuthoritySha256)
        assertEquals(candidatePublished.authoritySha256, baselinePublished.authoritySha256)
        assertFalse(candidatePublished.frozenInputsSha256 == baselinePublished.frozenInputsSha256)
        assertTrue(harness.compare(baseline, candidate).differences.contains("Frozen style, seed, or engine inputs differ."))
        // Isolate corruption checks from the intentional v1-to-v2 engine change.
        // Package location alone must not change the comparison result.
        val sameEngine = candidatePublished.copy(packageDirectory = baselinePublished.packageDirectory)
        assertTrue(harness.compare(sameEngine, candidate).equivalent)
        val changedSource = sameEngine.copy(sourceSha256 = "0".repeat(64))
        val comparison = harness.compare(changedSource, candidate)
        assertFalse(comparison.equivalent)
        assertTrue(comparison.differences.single().contains("Protected source"))

        val changedInputs = sameEngine.copy(frozenInputsSha256 = "0".repeat(64))
        val inputComparison = harness.compare(changedInputs, candidate)
        assertFalse(inputComparison.equivalent)
        assertTrue(inputComparison.differences.single().contains("Frozen style"))

        val changedSemanticMidi = sameEngine.copy(semanticSha256 = sameEngine.semanticSha256 + ("complete-song.mid" to "0".repeat(64)))
        val semanticComparison = harness.compare(changedSemanticMidi, candidate)
        assertFalse(semanticComparison.equivalent)
        assertTrue(semanticComparison.differences.single().contains("Re-imported semantic MIDI"))
    }
}
