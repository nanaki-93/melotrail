package app.melotrail.application

import kotlinx.coroutines.sync.Mutex
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

/** One local JVM-wide lock per canonical project root. */
object ProjectMutationCoordinator {
    private val locks = ConcurrentHashMap<Path, Mutex>()

    fun lock(root: Path): Mutex = locks.computeIfAbsent(root.toAbsolutePath().normalize()) { Mutex() }
}
