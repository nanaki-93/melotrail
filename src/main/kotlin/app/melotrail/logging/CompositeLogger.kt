package app.melotrail.logging

class CompositeLogger(private val loggers: List<Logger>) : Logger {

    override fun debug(tag: String, message: String) {
        loggers.forEach { it.debug(tag, message) }
    }

    override fun info(tag: String, message: String) {
        loggers.forEach { it.info(tag, message) }
    }

    override fun warning(tag: String, message: String) {
        loggers.forEach { it.warning(tag, message) }
    }

    override fun error(tag: String, message: String) {
        loggers.forEach { it.error(tag, message) }
    }

    override fun error(tag: String, message: String, throwable: Throwable?) {
        loggers.forEach { it.error(tag, message, throwable) }
    }

    override fun log(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
        loggers.forEach { it.log(level, tag, message, throwable) }
    }

    override fun flush() {
        loggers.forEach { it.flush() }
    }
}
