package ai.music.workstation.logging

import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.logging.*

interface Logger {
    fun info(tag: String, message: String)
    fun warning(tag: String, message: String)
    fun error(tag: String, message: String)
    fun error(tag: String, message: String, throwable: Throwable?)
    fun debug(tag: String, message: String)
    fun log(level: LogLevel, tag: String, message: String, throwable: Throwable? = null)
    fun flush()
}

class DefaultLogger : Logger {
    private val logger = java.util.logging.Logger.getLogger("ai.music.workstation")

    override fun info(tag: String, message: String) {
        logger.info("[$tag] $message")
    }

    override fun warning(tag: String, message: String) {
        logger.warning("[$tag] $message")
    }

    override fun error(tag: String, message: String) {
        logger.severe("[$tag] $message")
    }

    override fun error(tag: String, message: String, throwable: Throwable?) {
        logger.severe("[$tag] $message: ${throwable?.message}")
        throwable?.printStackTrace()
    }

    override fun debug(tag: String, message: String) {
        logger.fine("[$tag] $message")
    }

    override fun log(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
        when (level) {
            LogLevel.DEBUG -> debug(tag, message)
            LogLevel.INFO -> info(tag, message)
            LogLevel.WARNING -> warning(tag, message)
            LogLevel.ERROR -> error(tag, message, throwable)
        }
    }

    override fun flush() {
        // No-op for java.util.logging
    }
}
