package ai.music.workstation.cli

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AudioPipelineAnalysisTest {
    @Test
    fun `worker JSON analysis retains timing format and plain key text`() {
        val output = buildJsonObject {
            put("duration", 16.27)
            put("sampleRate", 44_100)
            put("channels", 2)
            put("bpm", 82.5)
            put("key", buildJsonObject { put("root", "G"); put("mode", "minor") })
        }

        val result = analysisResultFrom(output)

        assertEquals(16.27, result.duration)
        assertEquals(44_100, result.sampleRate)
        assertEquals(2, result.channels)
        assertEquals(82.5, result.bpm)
        assertEquals("G", result.key)
        assertNull(result.loudness)
    }
}
