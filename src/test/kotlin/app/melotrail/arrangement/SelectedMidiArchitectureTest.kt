package app.melotrail.arrangement

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Guards the concrete consumers that previously bypassed the selected-MIDI boundary. */
class SelectedMidiArchitectureTest {
    @Test
    fun `selected midi consumers do not read cleaned references directly`() {
        val source = Path.of("src/main/kotlin/app/melotrail")
        val consumers = listOf(
            "arrangement/StemRenderingMixer.kt"
        )
        consumers.forEach { relative ->
            val text = Files.readString(source.resolve(relative))
            // Cleanup/report maintenance and explicit A/B preview are outside this guard;
            // canonical rendering and cohesion paths must retain resolver identity.
            val canonicalDirectRead = text.lineSequence().any { line ->
                "midi.clean" in line && "PreviewMidiSource.CLEANED" !in line &&
                    "val clean =" !in line && "requireNotNull(midi.clean)" !in line
            }
            assertFalse(canonicalDirectRead, "$relative bypasses SelectedMidiArtifactResolver")
        }
    }

    @Test
    fun `piano rendering resolves occurrence MIDI rather than a shared part artifact`() {
        val mixer = Files.readString(Path.of("src/main/kotlin/app/melotrail/arrangement/StemRenderingMixer.kt"))

        assertTrue(mixer.contains("OccurrenceMidiArtifactResolver"), "piano rendering must resolve the current occurrence MIDI")
        assertFalse(mixer.contains("SelectedMidiArtifact::partId"), "piano rendering must not collapse repeated occurrences to one part MIDI")
    }

    @Test
    fun `selected resolver has no stage-run selection bypass`() {
        val resolver = Files.readString(Path.of("src/main/kotlin/app/melotrail/arrangement/SelectedMidiArtifactResolver.kt"))

        assertFalse(resolver.contains("selectedOutput("), "stage-run output selection must not compete with the explicit selected-artifact chain")
    }
}
