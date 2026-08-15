package app.melotrail.errors

enum class ErrorSeverity {
    RECOVERABLE,   // Can continue, user notified
    SERIOUS,       // Feature broken, workarounds available
    FATAL          // Application cannot continue
}
