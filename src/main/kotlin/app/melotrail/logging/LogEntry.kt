package app.melotrail.logging

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class LogEntry(
    val timestamp: Instant,
    val level: LogLevel,
    val component: String,
    val message: String,
    val exception: String? = null
) {
    override fun toString(): String = buildString {
        append("[$timestamp] [${level.name}] [$component] $message")
        exception?.let { append("\nException: $it") }
    }
}
