package app.melotrail.model

import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class NoOpErrorReporterTest {
    @Test
    fun `shared no-op reporter accepts both report forms`() {
        assertSame(ErrorReporter.NoOp, ErrorReporter.NoOp)

        ErrorReporter.NoOp.report("non-fatal validation detail")
        ErrorReporter.NoOp.report("non-fatal validation failure", IllegalArgumentException("invalid artifact"))
    }
}
