package app.melotrail.modellifecycle

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

class ModelHandleTest {
    @Test
    fun `should generate correct id`() {
        val handle = ModelHandle("test-model", "1.0")
        assertEquals("test-model/1.0", handle.id)
    }

    @Test
    fun `should increment reference count`() {
        val handle = ModelHandle("test-model", "1.0")
        assertEquals(0, handle.referenceCount)

        handle.incrementReference()
        assertEquals(1, handle.referenceCount)

        handle.incrementReference()
        assertEquals(2, handle.referenceCount)
    }

    @Test
    fun `should decrement reference count`() {
        val handle = ModelHandle("test-model", "1.0")
        handle.incrementReference()
        handle.incrementReference()
        assertEquals(2, handle.referenceCount)

        val previous = handle.decrementReference()
        assertEquals(2, previous)  // Returns the old value (post-decrement)
        assertEquals(1, handle.referenceCount)  // But referenceCount is now 1
    }

    @Test
    fun `should start with idle state`() {
        val handle = ModelHandle("test-model", "1.0")
        assertEquals(ModelState.IDLE, handle.state)
    }

    @Test
    fun `should update state`() {
        val handle = ModelHandle("test-model", "1.0")
        handle.state = ModelState.LOADING
        assertEquals(ModelState.LOADING, handle.state)

        handle.state = ModelState.LOADED
        assertEquals(ModelState.LOADED, handle.state)
    }
}
