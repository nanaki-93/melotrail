package app.melotrail.config

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import java.nio.file.Files

class ConfigTest {
    @Test
    fun `should create default config`() {
        val config = AppConfig()
        assertEquals(1, config.configVersion)
        assertEquals(48000, config.audio.sampleRate)
        assertEquals(24, config.audio.bitDepth)
        assertEquals("Melotrail", AppConfigSection().name)
    }

    @Test
    fun `should create config manager`() {
        val tempDir = Files.createTempDirectory("test-config")
        val configPath = tempDir.resolve("config.json")
        Files.writeString(configPath, "{}")
        try {
            val manager = ConfigManager(configPath)
            assertNotNull(manager)
        } finally {
            Files.delete(configPath)
            Files.delete(tempDir)
        }
    }
}
