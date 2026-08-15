package app.melotrail.logging

enum class LogLevel {
    DEBUG,
    INFO,
    WARNING,
    ERROR;

    fun toInt(): Int = when (this) {
        DEBUG -> 0
        INFO -> 1
        WARNING -> 2
        ERROR -> 3
    }

    companion object {
        fun fromInt(value: Int): LogLevel = entries.getOrElse(value) { INFO }
    }
}
