package app.melotrail.application

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertEquals

class MixApplicationServiceTest {
    @Test
    fun `mix settings accept only the gain and pan bounds exposed by the desktop`() {
        LogicalMixSetting(gainDb = -24.0, pan = -1.0).requireValid("piano")
        LogicalMixSetting(gainDb = 12.0, pan = 1.0).requireValid("piano")

        assertFailsWith<IllegalArgumentException> { LogicalMixSetting(gainDb = -24.1).requireValid("piano") }
        assertFailsWith<IllegalArgumentException> { LogicalMixSetting(gainDb = 12.1).requireValid("piano") }
        assertFailsWith<IllegalArgumentException> { LogicalMixSetting(pan = 1.01).requireValid("piano") }
    }

    @Test
    fun `mix approval binds exactly one plan and dry mix fingerprint`() {
        val fingerprint = "a".repeat(64)
        val approval = MixApproval(planSha256 = fingerprint, mixSha256 = fingerprint)

        assertEquals(fingerprint, approval.planSha256)
        assertFailsWith<IllegalArgumentException> { MixApproval(planSha256 = "not-a-fingerprint", mixSha256 = fingerprint) }
    }
}
