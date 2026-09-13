package app.melotrail.video.adapter

import com.sun.jna.LastErrorException
import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.NativeLong
import com.sun.jna.Pointer
import com.sun.jna.StringArray
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime
import java.nio.file.attribute.PosixFilePermissions
import java.security.MessageDigest
import java.time.Duration
import java.util.concurrent.atomic.AtomicReference

/** A hash-pinned executable invocation owned by one video job. */
data class VideoMediaProcessRequest(
    val executable: Path,
    val executableSha256: String,
    val arguments: List<String>,
    /** Must name an absent child of an existing directory. It is created with owner-only access. */
    val workingDirectory: Path,
    val timeout: Duration,
    val maxStdoutBytes: Int = 1_048_576,
    val maxStderrBytes: Int = 1_048_576,
    val environment: Map<String, String> = emptyMap(),
)

data class VideoMediaProcessResult(
    val exitCode: Int,
    val stdout: VideoMediaProcessOutput,
    val stderr: VideoMediaProcessOutput,
    val elapsed: Duration,
    val workingDirectory: Path,
)

data class VideoMediaProcessOutput(
    val text: String,
    val totalBytes: Long,
    val truncated: Boolean,
)

enum class VideoMediaProcessFailure {
    UNSUPPORTED_HOST,
    INVALID_REQUEST,
    PIN_MISMATCH,
    CANCELLED,
    TIMED_OUT,
    OUTPUT_LIMIT,
    LAUNCH_FAILED,
    NONZERO_EXIT,
    SIGNAL_EXIT,
    UNEXPECTED_DESCENDANTS,
    SUPERVISION_FAILED,
}

class VideoMediaProcessException(
    val failure: VideoMediaProcessFailure,
    message: String,
    val stdout: VideoMediaProcessOutput = VideoMediaProcessOutput("", 0, false),
    val stderr: VideoMediaProcessOutput = VideoMediaProcessOutput("", 0, false),
    val exitCode: Int? = null,
    val signal: Int? = null,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/**
 * Cancellation is race-safe with native launch: cancellation either prevents posix_spawn or
 * observes the newly registered process group and requests its supervised termination.
 */
class VideoMediaProcessCancellation {
    private val lock = Any()
    private var cancelled = false
    private var activeCancellation: (() -> Unit)? = null

    fun cancel() {
        val action = synchronized(lock) {
            cancelled = true
            activeCancellation
        }
        action?.invoke()
    }

    fun isCancelled(): Boolean = synchronized(lock) { cancelled }

    internal fun <T> launch(spawn: () -> Pair<T, () -> Unit>): T = synchronized(lock) {
        if (cancelled) throw cancelledFailure()
        val (value, action) = spawn()
        activeCancellation = action
        value
    }

    internal fun clear(action: () -> Unit) = synchronized(lock) {
        if (activeCancellation === action) activeCancellation = null
    }

    private fun cancelledFailure() = VideoMediaProcessException(
        VideoMediaProcessFailure.CANCELLED,
        "Video media process was cancelled before launch.",
    )
}

/**
 * macOS-arm64-only process supervision for the lazy video adapter boundary.
 *
 * The native binding is initialized only when [run] is called. The executable is launched directly
 * with argv, in a process group created atomically by posix_spawn. This is lifecycle ownership for
 * normal child trees; it is not a sandbox for a hostile executable.
 */
class VideoMediaProcess internal constructor(private val testHooks: VideoMediaProcessTestHooks) {
    constructor() : this(VideoMediaProcessTestHooks())

    fun run(
        request: VideoMediaProcessRequest,
        cancellation: VideoMediaProcessCancellation = VideoMediaProcessCancellation(),
    ): VideoMediaProcessResult {
        requireSupportedHost()
        if (cancellation.isCancelled() || Thread.currentThread().isInterrupted) {
            throw VideoMediaProcessException(
                VideoMediaProcessFailure.CANCELLED,
                "Video media process was cancelled before validation or launch.",
            )
        }
        val executable = validateExecutable(request, cancellation)
        validateArgumentsAndEnvironment(request)
        val workingDirectory = validateWorkingDirectory(request.workingDirectory)

        val stopReason = AtomicReference<StopReason?>()
        lateinit var cancelAction: () -> Unit
        val group = try {
            cancellation.launch {
                if (Thread.currentThread().isInterrupted) {
                    throw VideoMediaProcessException(VideoMediaProcessFailure.CANCELLED, "Video media launch was interrupted.")
                }
                verifyExecutableIdentity(executable)
                val directory = createPrivateWorkingDirectory(workingDirectory)
                val spawned = OwnedDarwinProcess.spawn(
                    executable = executable.path,
                    arguments = request.arguments,
                    directory = directory,
                    environment = request.environment,
                    stdoutLimit = request.maxStdoutBytes,
                    stderrLimit = request.maxStderrBytes,
                    onOverflow = { stream -> stopReason.compareAndSet(null, StopReason.Output(stream)) },
                    onDrainFailure = { error -> stopReason.compareAndSet(null, StopReason.Supervision(error)) },
                    testHooks = testHooks,
                )
                cancelAction = {
                    stopReason.compareAndSet(null, StopReason.Cancelled)
                }
                spawned to cancelAction
            }
        } catch (error: VideoMediaProcessException) {
            throw error
        } catch (error: Exception) {
            throw VideoMediaProcessException(
                VideoMediaProcessFailure.LAUNCH_FAILED,
                "Could not launch pinned video media executable '${executable.path}': ${usefulMessage(error)}",
                cause = error,
            )
        }

        val started = System.nanoTime()
        val deadline = started + request.timeout.toNanos()
        var interrupted = false
        var cleanupFailure: VideoMediaProcessException? = null
        try {
            while (true) {
                if (Thread.interrupted()) {
                    interrupted = true
                    stopReason.compareAndSet(null, StopReason.Cancelled)
                }
                group.pollOutput()
                group.pollLeader()
                if (group.leaderFinished() && group.hasLiveGroupMembers()) {
                    stopReason.compareAndSet(null, StopReason.UnexpectedDescendants)
                }
                if (stopReason.get() != null || group.isComplete()) break

                if (stopReason.get() == null && System.nanoTime() >= deadline) {
                    stopReason.compareAndSet(null, StopReason.TimedOut)
                }
                if (stopReason.get() != null) break
                Thread.sleep(POLL_MILLIS)
            }
        } catch (_: InterruptedException) {
            interrupted = true
            stopReason.compareAndSet(null, StopReason.Cancelled)
        } catch (error: VideoMediaProcessException) {
            stopReason.compareAndSet(null, StopReason.Supervision(error))
        } finally {
            try {
                cleanupFailure = group.finish()
            } finally {
                cancellation.clear(cancelAction)
                if (interrupted) Thread.currentThread().interrupt()
            }
        }

        val elapsed = Duration.ofNanos(System.nanoTime() - started)
        val stdout = group.stdoutSnapshot()
        val stderr = group.stderrSnapshot()
        fun withCleanup(error: VideoMediaProcessException): VideoMediaProcessException {
            cleanupFailure?.let { error.addSuppressed(it) }
            return error
        }
        val cleanupDiagnostic = cleanupFailure?.let { " Cleanup incomplete: ${it.message}" }.orEmpty()
        when (val reason = stopReason.get()) {
            StopReason.Cancelled -> throw withCleanup(failure(
                VideoMediaProcessFailure.CANCELLED,
                "Video media process cancellation requested.$cleanupDiagnostic",
                group,
                stdout,
                stderr,
            ))
            StopReason.TimedOut -> throw withCleanup(failure(
                VideoMediaProcessFailure.TIMED_OUT,
                "Video media process exceeded its ${request.timeout.toMillis()} ms deadline.$cleanupDiagnostic",
                group,
                stdout,
                stderr,
            ))
            is StopReason.Output -> throw withCleanup(failure(
                VideoMediaProcessFailure.OUTPUT_LIMIT,
                "Video media process exceeded the ${reason.stream.lowercase()} capture limit.$cleanupDiagnostic",
                group,
                stdout,
                stderr,
            ))
            is StopReason.Supervision -> throw withCleanup(VideoMediaProcessException(
                VideoMediaProcessFailure.SUPERVISION_FAILED,
                "Video media process supervision failed: ${reason.error.message}$cleanupDiagnostic",
                stdout,
                stderr,
                group.exitCode,
                group.signal,
                reason.error,
            ))
            StopReason.UnexpectedDescendants -> throw withCleanup(failure(
                VideoMediaProcessFailure.UNEXPECTED_DESCENDANTS,
                "Video media executable exited while descendants remained in its owned process group; termination requested.$cleanupDiagnostic",
                group,
                stdout,
                stderr,
            ))
            null -> Unit
        }
        cleanupFailure?.let { throw it }

        group.signal?.let { signal ->
            throw VideoMediaProcessException(
                VideoMediaProcessFailure.SIGNAL_EXIT,
                "Video media process crashed or was terminated by signal $signal.",
                stdout,
                stderr,
                signal = signal,
            )
        }
        val exitCode = checkNotNull(group.exitCode) { "Owned process completed without an exit status" }
        if (exitCode != 0) {
            throw VideoMediaProcessException(
                VideoMediaProcessFailure.NONZERO_EXIT,
                "Video media process exited with code $exitCode${diagnosticSuffix(stderr)}.",
                stdout,
                stderr,
                exitCode = exitCode,
            )
        }
        return VideoMediaProcessResult(exitCode, stdout, stderr, elapsed, workingDirectory)
    }

    private fun validateExecutable(
        request: VideoMediaProcessRequest,
        cancellation: VideoMediaProcessCancellation,
    ): ValidatedExecutable {
        val raw = requireAbsoluteNormalized(request.executable, "Executable path")
        if (!SHA256.matches(request.executableSha256)) {
            invalid("Executable SHA-256 must contain 64 lowercase hexadecimal characters.")
        }
        if (Files.isSymbolicLink(raw) || !Files.isRegularFile(raw, NOFOLLOW_LINKS)) {
            invalid("Executable must be a regular non-symbolic-link file: $raw")
        }
        if (!Files.isExecutable(raw)) invalid("Executable is not executable: $raw")
        val actual = raw.toRealPath()
        val before = Files.readAttributes(actual, BasicFileAttributes::class.java, NOFOLLOW_LINKS)
        val digest = sha256(actual, cancellation)
        val after = Files.readAttributes(actual, BasicFileAttributes::class.java, NOFOLLOW_LINKS)
        if (before.fileKey() != after.fileKey() || before.size() != after.size() ||
            before.lastModifiedTime() != after.lastModifiedTime()
        ) {
            invalid("Executable changed while its identity was being validated: $raw")
        }
        if (digest != request.executableSha256) {
            throw VideoMediaProcessException(
                VideoMediaProcessFailure.PIN_MISMATCH,
                "Executable pin mismatch for '$raw': expected ${request.executableSha256}, found $digest.",
            )
        }
        if (cancellation.isCancelled()) {
            throw VideoMediaProcessException(
                VideoMediaProcessFailure.CANCELLED,
                "Video media process was cancelled while validating the executable pin.",
            )
        }
        return ValidatedExecutable(actual, after.fileKey(), after.size(), after.lastModifiedTime())
    }

    private fun verifyExecutableIdentity(executable: ValidatedExecutable) {
        val attributes = try {
            Files.readAttributes(executable.path, BasicFileAttributes::class.java, NOFOLLOW_LINKS)
        } catch (error: Exception) {
            invalid("Pinned executable disappeared before launch: ${executable.path}")
        }
        if (!Files.isRegularFile(executable.path, NOFOLLOW_LINKS) || !Files.isExecutable(executable.path) ||
            attributes.fileKey() != executable.fileKey || attributes.size() != executable.size ||
            attributes.lastModifiedTime() != executable.modifiedAt
        ) {
            invalid("Pinned executable identity changed before launch: ${executable.path}")
        }
    }

    private fun validateArgumentsAndEnvironment(request: VideoMediaProcessRequest) {
        if (request.arguments.size > MAX_ARGUMENTS || request.arguments.sumOf { it.length.toLong() } > MAX_ARGUMENT_CHARACTERS) {
            invalid("Executable arguments exceed the bounded argv size.")
        }
        if (request.arguments.any { '\u0000' in it }) invalid("Executable arguments cannot contain NUL characters.")
        if (request.environment.size > MAX_ENVIRONMENT_ENTRIES) invalid("Environment overrides exceed the bounded entry count.")
        request.environment.forEach { (key, value) ->
            if (!ENVIRONMENT_KEY.matches(key)) invalid("Invalid environment variable name '$key'.")
            if ('\u0000' in value) invalid("Environment variable '$key' cannot contain NUL characters.")
        }
        if (request.timeout.isZero || request.timeout.isNegative || request.timeout > MAX_TIMEOUT) {
            invalid("Timeout must be greater than zero and no more than ${MAX_TIMEOUT.toHours()} hours.")
        }
        if (request.maxStdoutBytes !in MIN_CAPTURE_BYTES..MAX_CAPTURE_BYTES ||
            request.maxStderrBytes !in MIN_CAPTURE_BYTES..MAX_CAPTURE_BYTES
        ) {
            invalid("Each output capture limit must be $MIN_CAPTURE_BYTES..$MAX_CAPTURE_BYTES bytes.")
        }
    }

    private fun validateWorkingDirectory(raw: Path): Path {
        val path = requireAbsoluteNormalized(raw, "Working directory")
        if (Files.exists(path, NOFOLLOW_LINKS)) invalid("Refusing to reuse existing job working directory: $path")
        val parent = path.parent ?: invalid("Working directory must have a parent: $path")
        if (!Files.isDirectory(parent, NOFOLLOW_LINKS)) invalid("Working-directory parent must exist: $parent")
        return parent.toRealPath().resolve(path.fileName.toString())
    }

    private fun createPrivateWorkingDirectory(path: Path): Path = try {
        Files.createDirectory(path, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")))
    } catch (error: Exception) {
        throw VideoMediaProcessException(
            VideoMediaProcessFailure.LAUNCH_FAILED,
            "Could not create private job working directory '$path': ${usefulMessage(error)}",
            cause = error,
        )
    }

    private fun requireAbsoluteNormalized(path: Path, label: String): Path {
        if (!path.isAbsolute) invalid("$label must be absolute: $path")
        if (path.any { it.toString() == "." || it.toString() == ".." }) {
            invalid("$label must not contain '.' or '..' path components: $path")
        }
        return path
    }

    private fun sha256(path: Path, cancellation: VideoMediaProcessCancellation): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                if (cancellation.isCancelled() || Thread.currentThread().isInterrupted) {
                    throw VideoMediaProcessException(
                        VideoMediaProcessFailure.CANCELLED,
                        "Video media process was cancelled while hashing '$path'.",
                    )
                }
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun failure(
        kind: VideoMediaProcessFailure,
        message: String,
        group: OwnedDarwinProcess,
        stdout: VideoMediaProcessOutput,
        stderr: VideoMediaProcessOutput,
    ) = VideoMediaProcessException(kind, message, stdout, stderr, group.exitCode, group.signal)

    private fun diagnosticSuffix(stderr: VideoMediaProcessOutput): String {
        val diagnostic = stderr.text.trim().lineSequence().lastOrNull()?.take(240).orEmpty()
        return if (diagnostic.isEmpty()) "" else ": $diagnostic"
    }

    private fun invalid(message: String): Nothing = throw VideoMediaProcessException(
        VideoMediaProcessFailure.INVALID_REQUEST,
        message,
    )

    private fun requireSupportedHost() {
        val os = System.getProperty("os.name")
        val architecture = System.getProperty("os.arch")
        if (os != "Mac OS X" || architecture !in setOf("aarch64", "arm64")) {
            throw VideoMediaProcessException(
                VideoMediaProcessFailure.UNSUPPORTED_HOST,
                "Video media process supervision requires macOS arm64; found $os/$architecture.",
            )
        }
    }

    private sealed interface StopReason {
        data object Cancelled : StopReason
        data object TimedOut : StopReason
        data class Output(val stream: String) : StopReason
        data class Supervision(val error: VideoMediaProcessException) : StopReason
        data object UnexpectedDescendants : StopReason
    }

    private data class ValidatedExecutable(
        val path: Path,
        val fileKey: Any?,
        val size: Long,
        val modifiedAt: FileTime,
    )

    companion object {
        private const val POLL_MILLIS = 10L
        private const val MIN_CAPTURE_BYTES = 64
        private const val MAX_CAPTURE_BYTES = 4 * 1024 * 1024
        private const val MAX_ARGUMENTS = 4_096
        private const val MAX_ARGUMENT_CHARACTERS = 1_048_576L
        private const val MAX_ENVIRONMENT_ENTRIES = 512
        private val MAX_TIMEOUT = Duration.ofHours(24)
        private val SHA256 = Regex("[0-9a-f]{64}")
        private val ENVIRONMENT_KEY = Regex("[A-Za-z_][A-Za-z0-9_]*")
    }
}

/** Fault observations for native regressions; spawning, reads, signals and waitpid remain real. */
internal class VideoMediaProcessTestHooks(
    val holdOutputEof: Boolean = false,
    val holdCompletion: Boolean = false,
    val onDescriptorClosed: (Int) -> Unit = {},
    val onLifecycle: (String, Int) -> Unit = { _, _ -> },
)

private class OwnedDarwinProcess private constructor(
    private val pid: Int,
    private val stdoutDrain: NativeOutputDrain,
    private val stderrDrain: NativeOutputDrain,
    private val testHooks: VideoMediaProcessTestHooks,
) {
    @Volatile var exitCode: Int? = null
        private set
    @Volatile var signal: Int? = null
        private set

    private val stopLock = Any()
    private var stopRequested = false
    private var killSent = false
    private var stopStartedNanos = 0L
    // Once the kernel reports the owned group gone, never signal that numeric ID again.
    private var groupGone = false
    private var leaderExited = false
    private var leaderReaped = false
    private var signalingClosed = false

    fun pollOutput() {
        stdoutDrain.poll()
        stderrDrain.poll()
    }

    fun requestStop() {
        val shouldSignal = synchronized(stopLock) {
            if (stopRequested) false else {
                stopStartedNanos = System.nanoTime()
                stopRequested = true
                true
            }
        }
        if (shouldSignal) signalGroup(SIGTERM)
    }

    fun advanceStop() {
        val shouldSignal = synchronized(stopLock) {
            if (stopRequested && !killSent && System.nanoTime() - stopStartedNanos >= TERM_GRACE.toNanos()) {
                killSent = true
                true
            } else {
                false
            }
        }
        if (shouldSignal) signalGroup(SIGKILL)
    }

    fun pollLeader() {
        if (leaderExited) return
        // Darwin arm64 siginfo_t: six 32-bit fields, two pointers, long, seven unsigned longs.
        Memory(104).use { info ->
            info.clear()
            try {
                if (LibC.instance.waitid(P_PID, pid, info, WEXITED or WNOHANG or WNOWAIT) != 0) {
                    val error = Native.getLastError()
                    if (error != EINTR) throw supervision("Cannot observe owned process $pid: ${errnoMessage(error)}")
                    return
                }
            } catch (error: LastErrorException) {
                if (error.errorCode == EINTR) return
                throw supervision("Cannot observe owned process $pid: ${errnoMessage(error.errorCode)}", error)
            }
            if (info.getInt(12) == pid) {
                leaderExited = true
                testHooks.onLifecycle("observed", pid)
            }
        }
    }

    private fun reapLeader() {
        if (leaderReaped) return
        val status = IntByReference()
        val result = try {
            LibC.instance.waitpid(pid, status, WNOHANG)
        } catch (error: LastErrorException) {
            if (error.errorCode == EINTR) return
            throw supervision("Cannot observe owned process $pid: ${errnoMessage(error.errorCode)}", error)
        }
        when {
            result == 0 -> Unit
            result == pid -> {
                decodeWaitStatus(status.value)
                leaderReaped = true
                testHooks.onLifecycle("reaped", pid)
            }
            result < 0 -> {
                val error = Native.getLastError()
                if (error == EINTR) return
                throw supervision("Cannot observe owned process $pid: ${errnoMessage(error)}")
            }
        }
    }

    fun isComplete(): Boolean = leaderExited && !groupExists() &&
        stdoutDrain.isFinished() && stderrDrain.isFinished() && !testHooks.holdCompletion

    fun leaderFinished(): Boolean = leaderExited

    fun hasLiveGroupMembers(): Boolean = groupExists()

    /** One fixed cleanup budget, including unsuccessful observations and interrupted sleeps. */
    fun finish(): VideoMediaProcessException? {
        val deadline = System.nanoTime() + STOP_WAIT.toNanos()
        var interrupted = Thread.interrupted()
        var firstFailure: VideoMediaProcessException? = null
        fun attempt(action: () -> Unit) {
            try {
                action()
            } catch (error: VideoMediaProcessException) {
                if (firstFailure == null) firstFailure = error
            }
        }
        var complete = false
        try {
            attempt { complete = isComplete() }
            if (!complete) attempt { requestStop() }
            while (!complete && System.nanoTime() < deadline) {
                attempt { pollOutput() }
                attempt { pollLeader() }
                attempt { advanceStop() }
                attempt { complete = isComplete() }
                if (!complete) {
                    try {
                        Thread.sleep(POLL_MILLIS)
                    } catch (_: InterruptedException) {
                        interrupted = true
                    }
                }
            }
            // WNOWAIT has reserved the leader PID throughout all group observations/signals.
            // Never inspect or signal the numeric group after releasing that reservation.
            signalingClosed = true
            do {
                attempt { reapLeader() }
                if (leaderReaped || System.nanoTime() >= deadline) break
                try {
                    Thread.sleep(POLL_MILLIS)
                } catch (_: InterruptedException) {
                    interrupted = true
                }
            } while (System.nanoTime() < deadline)
            if (!complete || !leaderReaped) {
                val failure = supervision(
                    "Owned process $pid cleanup exceeded ${STOP_WAIT.toMillis()} ms " +
                        "(leader reaped=$leaderReaped, group gone=$groupGone, " +
                        "stdout EOF=${stdoutDrain.isFinished()}, stderr EOF=${stderrDrain.isFinished()}); " +
                        "output descriptors were closed. Termination/reaping could not be fully confirmed.",
                )
                firstFailure?.let { failure.addSuppressed(it) }
                firstFailure = failure
            }
        } finally {
            signalingClosed = true
            try {
                attempt { reapLeader() }
            } finally {
                stdoutDrain.close()
                stderrDrain.close()
            }
            if (interrupted) Thread.currentThread().interrupt()
        }
        return firstFailure
    }

    fun stdoutSnapshot(): VideoMediaProcessOutput = stdoutDrain.snapshot()
    fun stderrSnapshot(): VideoMediaProcessOutput = stderrDrain.snapshot()

    private fun decodeWaitStatus(raw: Int) {
        val terminatingSignal = raw and 0x7f
        if (terminatingSignal == 0) {
            exitCode = (raw ushr 8) and 0xff
        } else if (terminatingSignal != 0x7f) {
            signal = terminatingSignal
        }
    }

    private fun groupExists(): Boolean {
        if (groupGone || signalingClosed) return false
        // Query only the atomically owned group, never a global descendant/name scan. Keeping
        // the leader waitable prevents the numeric identity from being reused during this query.
        Memory(4L * MAX_GROUP_MEMBERS).use { members ->
            val count = try {
                LibProc.instance.proc_listpids(PROC_PGRP_ONLY, pid, members, members.size().toInt())
            } catch (error: LastErrorException) {
                throw supervision("Cannot inspect owned process group $pid: ${errnoMessage(error.errorCode)}", error)
            }
            if (count < 0 || count % 4 != 0 || count >= members.size()) {
                throw supervision("Cannot inspect owned process group $pid within $MAX_GROUP_MEMBERS member bound (bytes=$count).")
            }
            val exists = (0 until count / 4).any { index ->
                val member = members.getInt(index * 4L)
                member > 0 && (member != pid || !leaderExited)
            }
            if (!exists && leaderExited) groupGone = true
            return exists
        }
    }

    private fun signalGroup(requestedSignal: Int) {
        if (groupGone || signalingClosed) return
        testHooks.onLifecycle("signal:$requestedSignal", pid)
        try {
            val result = LibC.instance.kill(-pid, requestedSignal)
            if (result != 0) signalFailure(requestedSignal, Native.getLastError())
        } catch (error: LastErrorException) {
            signalFailure(requestedSignal, error.errorCode, error)
        }
    }

    private fun signalFailure(requestedSignal: Int, error: Int, cause: Throwable? = null) {
        if (error == ESRCH) {
            groupGone = true
        } else {
            throw supervision(
                "Cannot signal owned process group $pid with signal $requestedSignal: ${errnoMessage(error)}",
                cause,
            )
        }
    }

    private fun supervision(message: String, cause: Throwable? = null) = VideoMediaProcessException(
        VideoMediaProcessFailure.SUPERVISION_FAILED,
        message,
        stdoutSnapshot(),
        stderrSnapshot(),
        exitCode,
        signal,
        cause,
    )

    companion object {
        fun spawn(
            executable: Path,
            arguments: List<String>,
            directory: Path,
            environment: Map<String, String>,
            stdoutLimit: Int,
            stderrLimit: Int,
            onOverflow: (String) -> Unit,
            onDrainFailure: (VideoMediaProcessException) -> Unit,
            testHooks: VideoMediaProcessTestHooks,
        ): OwnedDarwinProcess {
            val libc = LibC.instance
            // Resolve both system bridges before owning any descriptors or child process.
            LibProc.instance
            val stdoutPipe = createPipe(libc, testHooks)
            val stderrPipe = try {
                createPipe(libc, testHooks)
            } catch (error: Exception) {
                stdoutPipe.closeAll()
                throw error
            }
            val actions = PointerByReference()
            val attributes = PointerByReference()
            var actionsReady = false
            var attributesReady = false
            var spawned = false
            try {
                checkedSpawn(libc.posix_spawn_file_actions_init(actions), "initialize spawn file actions")
                actionsReady = true
                checkedSpawn(libc.posix_spawnattr_init(attributes), "initialize spawn attributes")
                attributesReady = true
                checkedSpawn(
                    libc.posix_spawn_file_actions_addopen(actions, STDIN, "/dev/null", O_RDONLY, 0.toShort()),
                    "redirect stdin",
                )
                checkedSpawn(libc.posix_spawn_file_actions_adddup2(actions, stdoutPipe.write.number, STDOUT), "redirect stdout")
                checkedSpawn(libc.posix_spawn_file_actions_adddup2(actions, stderrPipe.write.number, STDERR), "redirect stderr")
                listOf(stdoutPipe.read, stdoutPipe.write, stderrPipe.read, stderrPipe.write).forEach { fd ->
                    checkedSpawn(libc.posix_spawn_file_actions_addclose(actions, fd.number), "close inherited pipe descriptor")
                }
                checkedSpawn(libc.posix_spawn_file_actions_addchdir_np(actions, directory.toString()), "select job working directory")
                checkedSpawn(libc.posix_spawnattr_setpgroup(attributes, 0), "select owned process group")
                checkedSpawn(
                    libc.posix_spawnattr_setflags(attributes, (POSIX_SPAWN_SETPGROUP or POSIX_SPAWN_CLOEXEC_DEFAULT).toShort()),
                    "enable atomic process-group ownership",
                )
                val pid = IntByReference()
                val argv = StringArray((listOf(executable.toString()) + arguments).toTypedArray(), StandardCharsets.UTF_8.name())
                val inherited = System.getenv().toMutableMap().apply { putAll(environment) }
                val envp = StringArray(
                    inherited.toSortedMap().map { (key, value) -> "$key=$value" }.toTypedArray(),
                    StandardCharsets.UTF_8.name(),
                )
                val stdoutDrain = NativeOutputDrain(stdoutPipe.read, stdoutLimit, "STDOUT", onOverflow, onDrainFailure, testHooks)
                val stderrDrain = NativeOutputDrain(stderrPipe.read, stderrLimit, "STDERR", onOverflow, onDrainFailure, testHooks)
                val result = libc.posix_spawn(pid, executable.toString(), actions, attributes, argv, envp)
                checkedSpawn(result, "launch executable")
                spawned = true
                stdoutPipe.write.close()
                stderrPipe.write.close()
                return OwnedDarwinProcess(pid.value, stdoutDrain, stderrDrain, testHooks)
            } finally {
                if (attributesReady) libc.posix_spawnattr_destroy(attributes)
                if (actionsReady) libc.posix_spawn_file_actions_destroy(actions)
                if (!spawned) {
                    stdoutPipe.closeAll()
                    stderrPipe.closeAll()
                }
            }
        }

        private fun createPipe(libc: LibC, testHooks: VideoMediaProcessTestHooks): NativePipe {
            val descriptors = IntArray(2)
            try {
                val result = libc.pipe(descriptors)
                if (result != 0) {
                    val error = Native.getLastError()
                    throw VideoMediaProcessException(
                        VideoMediaProcessFailure.LAUNCH_FAILED,
                        "Could not create output pipe: ${errnoMessage(error)}",
                    )
                }
            } catch (error: LastErrorException) {
                throw VideoMediaProcessException(
                    VideoMediaProcessFailure.LAUNCH_FAILED,
                    "Could not create output pipe: ${errnoMessage(error.errorCode)}",
                    cause = error,
                )
            }
            val pipe = NativePipe(
                OwnedDescriptor(descriptors[0], testHooks.onDescriptorClosed),
                OwnedDescriptor(descriptors[1], testHooks.onDescriptorClosed),
            )
            try {
                val flags = libc.fcntl(pipe.read.number, F_GETFL, 0)
                if (flags < 0 || libc.fcntl(pipe.read.number, F_SETFL, flags or O_NONBLOCK) < 0 ||
                    libc.fcntl(pipe.read.number, F_GETFL, 0).let { it < 0 || (it and O_NONBLOCK) == 0 }
                ) {
                    throw VideoMediaProcessException(
                        VideoMediaProcessFailure.LAUNCH_FAILED,
                        "Could not make output pipe nonblocking: ${errnoMessage(Native.getLastError())}",
                    )
                }
                return pipe
            } catch (error: Exception) {
                pipe.closeAll()
                throw error
            }
        }

        private fun checkedSpawn(code: Int, operation: String) {
            if (code != 0) {
                throw VideoMediaProcessException(
                    VideoMediaProcessFailure.LAUNCH_FAILED,
                    "Could not $operation: ${errnoMessage(code)}",
                )
            }
        }

        private val TERM_GRACE = Duration.ofMillis(200)
        private val STOP_WAIT = Duration.ofSeconds(3)
        private const val POLL_MILLIS = 10L
    }
}

/** A descriptor is closed exactly once by its owner, never by a background reader. */
private class OwnedDescriptor(val number: Int, private val onClosed: (Int) -> Unit) {
    private var closed = false

    fun close() {
        if (closed) return
        closed = true
        // Do not retry close after EINTR: the number may already have been reused.
        closeQuietly(LibC.instance, number)
        onClosed(number)
    }
}

private data class NativePipe(val read: OwnedDescriptor, val write: OwnedDescriptor) {
    fun closeAll() {
        read.close()
        write.close()
    }
}

private class NativeOutputDrain(
    private val descriptor: OwnedDescriptor,
    limit: Int,
    private val label: String,
    private val onOverflow: (String) -> Unit,
    private val onFailure: (VideoMediaProcessException) -> Unit,
    private val testHooks: VideoMediaProcessTestHooks,
) {
    private val bytes = BoundedBytes(limit)
    private var finished = false

    /** Nonblocking reads with a per-turn byte budget keep deadlines and the other pipe responsive. */
    fun poll() {
        if (finished) return
        Memory(8_192).use { buffer ->
            repeat(16) {
                val count = try {
                    LibC.instance.read(descriptor.number, buffer, NativeLong(buffer.size())).toLong()
                } catch (error: LastErrorException) {
                    if (error.errorCode == EINTR || error.errorCode == EAGAIN) return
                    fail(error.errorCode, error)
                    return
                }
                if (count == 0L) {
                    if (!testHooks.holdOutputEof) finished = true
                    return
                }
                if (count < 0) {
                    val error = Native.getLastError()
                    if (error != EINTR && error != EAGAIN) fail(error)
                    return
                }
                if (bytes.append(buffer.getByteArray(0, count.toInt()))) onOverflow(label)
            }
        }
    }

    private fun fail(error: Int, cause: Throwable? = null) {
        finished = true
        onFailure(VideoMediaProcessException(
            VideoMediaProcessFailure.SUPERVISION_FAILED,
            "Could not drain $label: ${errnoMessage(error)}",
            cause = cause,
        ))
    }

    fun close() = descriptor.close()
    fun isFinished(): Boolean = finished
    fun snapshot(): VideoMediaProcessOutput = bytes.snapshot()
}

private class BoundedBytes(private val limit: Int) {
    private val headLimit = (limit * 3) / 4
    private val tailLimit = limit - headLimit
    private val head = ByteArrayOutputStream(headLimit)
    private val tail = ByteArray(tailLimit)
    private var tailCount = 0
    private var tailCursor = 0
    private var total = 0L
    private var overflowReported = false

    @Synchronized
    fun append(chunk: ByteArray): Boolean {
        total += chunk.size
        var offset = 0
        if (head.size() < headLimit) {
            val count = minOf(chunk.size, headLimit - head.size())
            head.write(chunk, 0, count)
            offset = count
        }
        while (offset < chunk.size && tailLimit > 0) {
            tail[tailCursor] = chunk[offset++]
            tailCursor = (tailCursor + 1) % tailLimit
            if (tailCount < tailLimit) tailCount++
        }
        return if (total > limit && !overflowReported) {
            overflowReported = true
            true
        } else {
            false
        }
    }

    @Synchronized
    fun snapshot(): VideoMediaProcessOutput {
        val headBytes = head.toByteArray()
        if (total <= limit) {
            val plain = ByteArray(headBytes.size + tailCount)
            headBytes.copyInto(plain)
            repeat(tailCount) { index -> plain[headBytes.size + index] = tail[index] }
            return VideoMediaProcessOutput(String(plain, StandardCharsets.UTF_8), total, false)
        }
        val tailBytes = ByteArray(tailCount)
        repeat(tailCount) { index ->
            val start = if (tailCount == tailLimit) tailCursor else 0
            tailBytes[index] = tail[(start + index) % tailLimit]
        }
        val omitted = total - headBytes.size - tailBytes.size
        val text = buildString {
            append(String(headBytes, StandardCharsets.UTF_8))
            append("\n... <$omitted bytes omitted> ...\n")
            append(String(tailBytes, StandardCharsets.UTF_8))
        }
        return VideoMediaProcessOutput(text, total, true)
    }
}

private interface LibC : Library {
    fun posix_spawn_file_actions_init(actions: PointerByReference): Int
    fun posix_spawn_file_actions_destroy(actions: PointerByReference): Int
    fun posix_spawn_file_actions_addopen(
        actions: PointerByReference,
        fd: Int,
        path: String,
        flags: Int,
        mode: Short,
    ): Int
    fun posix_spawn_file_actions_adddup2(actions: PointerByReference, fd: Int, newFd: Int): Int
    fun posix_spawn_file_actions_addclose(actions: PointerByReference, fd: Int): Int
    fun posix_spawn_file_actions_addchdir_np(actions: PointerByReference, path: String): Int
    fun posix_spawnattr_init(attributes: PointerByReference): Int
    fun posix_spawnattr_destroy(attributes: PointerByReference): Int
    fun posix_spawnattr_setpgroup(attributes: PointerByReference, group: Int): Int
    fun posix_spawnattr_setflags(attributes: PointerByReference, flags: Short): Int
    fun posix_spawn(
        pid: IntByReference,
        path: String,
        actions: PointerByReference,
        attributes: PointerByReference,
        argv: Pointer,
        environment: Pointer,
    ): Int

    @Throws(LastErrorException::class)
    fun pipe(descriptors: IntArray): Int

    @Throws(LastErrorException::class)
    fun read(descriptor: Int, buffer: Pointer, count: NativeLong): NativeLong

    @Throws(LastErrorException::class)
    // Darwin arm64 has a distinct variadic calling convention; preserve fcntl's native varargs.
    fun fcntl(descriptor: Int, command: Int, vararg arguments: Any): Int

    @Throws(LastErrorException::class)
    fun close(descriptor: Int): Int

    @Throws(LastErrorException::class)
    fun waitpid(pid: Int, status: IntByReference, options: Int): Int

    @Throws(LastErrorException::class)
    fun waitid(idType: Int, pid: Int, info: Pointer, options: Int): Int

    @Throws(LastErrorException::class)
    fun kill(pid: Int, signal: Int): Int

    fun strerror(error: Int): Pointer?

    companion object {
        val instance: LibC by lazy { Native.load("System", LibC::class.java) }
    }
}

private interface LibProc : Library {
    @Throws(LastErrorException::class)
    fun proc_listpids(type: Int, typeInfo: Int, buffer: Pointer, bufferSize: Int): Int

    companion object {
        val instance: LibProc by lazy { Native.load("proc", LibProc::class.java) }
    }
}

private fun closeQuietly(libc: LibC, descriptor: Int) {
    try {
        libc.close(descriptor)
    } catch (_: LastErrorException) {
        // The descriptor is already closed or cleanup is best-effort after a more useful failure.
    }
}

private fun errnoMessage(error: Int): String = LibC.instance.strerror(error)?.getString(0).orEmpty().ifEmpty {
    "native error $error"
}

private fun usefulMessage(error: Throwable): String = error.message ?: error.javaClass.simpleName

private const val STDIN = 0
private const val STDOUT = 1
private const val STDERR = 2
private const val O_RDONLY = 0
private const val POSIX_SPAWN_SETPGROUP = 0x0002
private const val POSIX_SPAWN_CLOEXEC_DEFAULT = 0x4000
private const val WNOHANG = 0x00000001
private const val ESRCH = 3
private const val EINTR = 4
private const val SIGKILL = 9
private const val SIGTERM = 15

private const val F_GETFL = 3
private const val F_SETFL = 4
private const val O_NONBLOCK = 4
private const val EAGAIN = 35

private const val WEXITED = 4
private const val WNOWAIT = 32
private const val P_PID = 1
private const val PROC_PGRP_ONLY = 2
private const val MAX_GROUP_MEMBERS = 4_096
