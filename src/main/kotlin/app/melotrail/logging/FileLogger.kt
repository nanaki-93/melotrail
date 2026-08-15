package app.melotrail.logging

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class FileLogger(
    private val path: Path,
    private val maxFileSize: Long = 10 * 1024 * 1024, // 10 MB
    private val maxFiles: Int = 5,
    private var _level: LogLevel = LogLevel.DEBUG
) : Logger {

    private val lock = ReentrantLock()
    private val dateFormatter = DateTimeFormatterBuilder()
        .appendPattern("yyyy-MM-dd HH:mm:ss.SSS")
        .toFormatter()

    init {
        Files.createDirectories(path.parent)
    }

    override fun debug(component: String, message: String) {
        logIfEnabled(LogLevel.DEBUG, component, message)
    }

    override fun info(component: String, message: String) {
        logIfEnabled(LogLevel.INFO, component, message)
    }

    override fun warning(component: String, message: String) {
        logIfEnabled(LogLevel.WARNING, component, message)
    }

    override fun error(component: String, message: String) {
        logIfEnabled(LogLevel.ERROR, component, message)
    }

    override fun error(component: String, message: String, exception: Throwable?) {
        logIfEnabled(LogLevel.ERROR, component, message, exception)
    }

    override fun log(level: LogLevel, component: String, message: String, exception: Throwable?) {
        if (level.toInt() >= _level.toInt()) {
            lock.withLock {
                val timestampStr = Clock.System.now().toEpochMilliseconds()
                    .let { millis ->
                        val instant = java.time.Instant.ofEpochMilli(millis)
                        dateFormatter.format(
                            java.time.LocalDateTime.ofInstant(instant, java.time.ZoneOffset.UTC)
                        )
                    }
                val line = buildString {
                    append("[$timestampStr] [${level.name}] [$component] $message")
                    exception?.let { append("\nException: $it") }
                    append("\n")
                }
                Files.writeString(path, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND)
                checkRotation()
            }
        }
    }

    override fun flush() {
        // File logger flushes immediately via writeString
    }

    private fun logIfEnabled(
        level: LogLevel,
        component: String,
        message: String,
        exception: Throwable? = null
    ) {
        if (level.toInt() >= _level.toInt()) {
            log(level, component, message, exception)
        }
    }

    private fun checkRotation() {
        if (!Files.exists(path)) return
        val fileSize = Files.size(path)
        if (fileSize > maxFileSize) {
            for (i in maxFiles - 1 downTo 1) {
                val from = path.resolveSibling("app.log.$i")
                val to = path.resolveSibling("app.log.${i + 1}")
                if (Files.exists(from)) {
                    Files.move(from, to, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                }
            }
            val backup = path.resolveSibling("app.log.1")
            if (Files.exists(backup)) {
                Files.delete(backup)
            }
            Files.move(path, backup, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            Files.createFile(path)
        }
    }
}
