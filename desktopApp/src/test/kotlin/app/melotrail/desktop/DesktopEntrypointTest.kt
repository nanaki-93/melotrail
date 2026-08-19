package app.melotrail.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class DesktopEntrypointTest {
    @Test
    fun `renamed Compose entry point resolves`() {
        assertNotNull(Class.forName("app.melotrail.desktop.DesktopMainKt"))
    }

    @Test
    fun `desktop composition root loads the bundled composition profile catalog`() {
        assertEquals("lofi", DesktopServiceComposition.compositionProfiles().profiles().single().ref.id)
    }
}
