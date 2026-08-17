package app.melotrail.application

import kotlin.test.Test
import kotlin.test.assertFailsWith

class MixApplicationServiceTest {
    @Test
    fun `mix settings accept only the gain and pan bounds exposed by the desktop`() {
        LogicalMixSetting(gainDb = -24.0, pan = -1.0).requireValid("piano")
        LogicalMixSetting(gainDb = 12.0, pan = 1.0).requireValid("piano")

        assertFailsWith<IllegalArgumentException> { LogicalMixSetting(gainDb = -24.1).requireValid("piano") }
        assertFailsWith<IllegalArgumentException> { LogicalMixSetting(gainDb = 12.1).requireValid("piano") }
        assertFailsWith<IllegalArgumentException> { LogicalMixSetting(pan = 1.01).requireValid("piano") }
    }
}
