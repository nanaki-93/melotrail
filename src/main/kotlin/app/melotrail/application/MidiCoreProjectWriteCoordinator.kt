package app.melotrail.application

import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock

/** Serializes target project-state transactions while allowing generation to run in parallel. */
internal object MidiCoreProjectWriteCoordinator {
    private val locks = ConcurrentHashMap<Path, ReentrantLock>()

    fun <T> withLock(projectRoot: Path, action: () -> T): T {
        val root = projectRoot.toAbsolutePath().normalize()
        val lock = locks.computeIfAbsent(root) { ReentrantLock() }
        lock.lock()
        return try {
            action()
        } finally {
            lock.unlock()
        }
    }
}
