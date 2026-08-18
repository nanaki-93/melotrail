package app.melotrail.model

interface ErrorReporter {
    fun report(message: String)
    fun report(message: String, cause: Throwable)

    /** Explicit opt-out for boundaries that validate data without emitting diagnostics. */
    object NoOp : ErrorReporter {
        override fun report(message: String) = Unit
        override fun report(message: String, cause: Throwable) = Unit
    }
}
