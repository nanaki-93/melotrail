package ai.music.workstation.dsp

import ai.music.workstation.model.DSPSettings
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue

class LOFIPresetTest {
    @Test
    fun `should have default presets`() {
        val presets = LOFIPresets.DEFAULT_PRESETS
        assertNotNull(presets)
        assertTrue(presets.isNotEmpty())
    }

    @Test
    fun `should find preset by name`() {
        val preset = LOFIPresets.getByName("Warm Cassette")
        assertNotNull(preset)
        assertEquals("Warm Cassette", preset!!.name)
    }

    @Test
    fun `should return null for unknown preset`() {
        val preset = LOFIPresets.getByName("Nonexistent")
        assertNull(preset)
    }

    @Test
    fun `default preset should have valid settings`() {
        val preset = LOFIPresets.getByName("Warm Cassette")
        assertNotNull(preset)

        val settings = preset!!.settings
        assertNotNull(settings)
        assertTrue(settings.amount >= 0.0 && settings.amount <= 1.0)
        assertTrue(settings.tape >= 0.0 && settings.tape <= 1.0)
    }
}
