package app.melotrail.model

interface ErrorReporter {
    fun report(message: String)
    fun report(message: String, cause: Throwable)
}
