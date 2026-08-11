package ai.music.workstation.logging

class CompositeLogger(private val loggers: List<Logger>) : Logger {

    override fun debug(component: String, message: String) {
        loggers.forEach { it.debug(component, message) }
    }

    override fun info(component: String, message: String) {
        loggers.forEach { it.info(component, message) }
    }

    override fun warning(component: String, message: String) {
        loggers.forEach { it.warning(component, message) }
    }

    override fun error(component: String, message: String) {
        loggers.forEach { it.error(component, message) }
    }

    override fun error(component: String, message: String, exception: Throwable?) {
        loggers.forEach { it.error(component, message, exception) }
    }

    override fun log(level: LogLevel, component: String, message: String, exception: Throwable?) {
        loggers.forEach { it.log(level, component, message, exception) }
    }

    override fun flush() {
        loggers.forEach { it.flush() }
    }
}
