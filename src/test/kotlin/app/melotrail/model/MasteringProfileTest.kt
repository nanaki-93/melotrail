package app.melotrail.model

import kotlin.test.Test
import kotlin.test.assertEquals

class MasteringProfileTest {
    @Test
    fun `lofi profile keeps the delivery reference and dynamics limits explicit`() {
        val profile = MasteringProfiles.LOFI

        assertEquals(-14.0, profile.nominalIntegratedLufs)
        assertEquals(1.0, profile.loudnessToleranceLu)
        assertEquals(-1.0, profile.maximumTruePeakDbtp)
        assertEquals(2.0, profile.minimumLraLu)
        assertEquals(5.0, profile.minimumCrestDb)
        assertEquals(4.0, profile.maximumLimiterGainReductionDb)
    }
}
