package ai.music.workstation.model

interface ErrorReporter {
    fun report(message: String)
    fun report(message: String, cause: Throwable)
}
