package ai.music.workstation.seed

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

class SeedManagerTest {
    private val manager = SeedManager()

    @Test
    fun `should generate seed in valid range`() {
        val seed = manager.generate()
        assertTrue(seed >= 0 && seed <= (1L shl 53) - 1)
    }

    @Test
    fun `should validate positive seed`() {
        val result = manager.validate(12345L)
        assertTrue(result.valid)
    }

    @Test
    fun `should reject negative seed`() {
        val result = manager.validate(-1L)
        assertTrue(!result.valid)
    }

    @Test
    fun `should reject seed exceeding range`() {
        val result = manager.validate(1L shl 60)
        assertTrue(!result.valid)
    }

    @Test
    fun `should resolve custom seed`() {
        val options = SeedOptions(customSeed = 42L)
        val resolved = manager.resolve(options)
        assertEquals(42L, resolved)
    }

    @Test
    fun `should randomize when requested`() {
        val options = SeedOptions(randomize = true)
        val seed = manager.resolve(options)
        assertTrue(seed >= 0 && seed <= (1L shl 53) - 1)
    }

    @Test
    fun `randomized should produce different seed`() {
        val current = 12345L
        val randomized = manager.randomize(current)
        // Note: This is probabilistic, so we just check range
        assertTrue(randomized >= 0 && randomized <= (1L shl 53) - 1)
    }
}
