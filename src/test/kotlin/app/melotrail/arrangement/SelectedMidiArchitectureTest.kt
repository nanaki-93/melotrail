package app.melotrail.arrangement

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFalse

/** Guards the concrete consumers that previously bypassed the selected-MIDI boundary. */
class SelectedMidiArchitectureTest {
    @Test
    fun `selected midi consumers do not read repaired references directly`() {
        val source = Path.of("src/main/kotlin/app/melotrail")
        val consumers = listOf(
            "arrangement/StemRenderingMixer.kt",
            "arrangement/PianoBassQualityGate.kt",
            "arrangement/MelodyCohesion.kt"
        )
        consumers.forEach { relative ->
            val text = Files.readString(source.resolve(relative))
            // Repair/report maintenance and explicit A/B preview are outside this guard;
            // canonical rendering and cohesion paths must retain resolver identity.
            val canonicalDirectRead = text.lineSequence().any { line ->
                "midi.clean" in line && "PreviewMidiSource.REPAIRED" !in line &&
                    "val clean =" !in line && "requireNotNull(midi.clean)" !in line
            }
            assertFalse(canonicalDirectRead, "$relative bypasses SelectedMidiArtifactResolver")
        }
    }
}
