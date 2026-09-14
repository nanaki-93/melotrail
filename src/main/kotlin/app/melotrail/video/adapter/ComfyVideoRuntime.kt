package app.melotrail.video.adapter

import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.NativeLong
import com.sun.jna.ptr.NativeLongByReference
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.URI
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean

enum class ComfyVideoRuntimeState { STOPPED, STARTING, READY, STOPPING, FAILED }

enum class ComfyVideoRuntimeFailure {
    INVALID_SETUP,
    INVALID_REQUEST,
    PORT_OCCUPIED,
    STARTUP_TIMEOUT,
    STARTUP_FAILED,
    RESOURCE_LIMIT,
    HEALTH_LOST,
    NOT_RUNNING,
    INFERENCE_BUSY,
    SHUTDOWN_FAILED,
}

class ComfyVideoRuntimeException(
    val failure: ComfyVideoRuntimeFailure,
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

data class ComfyVideoSession(
    val id: String,
    val endpoint: URI,
    val directory: Path,
    val inputDirectory: Path,
    val outputDirectory: Path,
    val temporaryDirectory: Path,
    val userDirectory: Path,
)

enum class ComfyVideoHttpMethod { GET, POST }

/**
 * An unforgeable capability for the exact server launch that issued [sessionId]. Paths are
 * resolved through that launch's private namespace, so a later listener on the public port does
 * not receive a standard ComfyUI operation even if it wins a dispatch race.
 */
class ComfyVideoConnection internal constructor(
    private val runtime: ComfyVideoRuntime,
    private val capability: Any,
    val sessionId: String,
) {
    fun httpUri(method: ComfyVideoHttpMethod, operationPath: String): URI =
        runtime.resolveHttpUri(capability, sessionId, method, operationPath)

    fun webSocketUri(clientId: String): URI =
        runtime.resolveWebSocketUri(capability, sessionId, clientId)
}

/** A single inference admission token. Workflow submission belongs to V17b. */
class ComfyVideoInferenceSlot internal constructor(
    private val runtime: ComfyVideoRuntime,
    internal val session: ComfyVideoSession,
    internal val attemptKey: String,
) : AutoCloseable {
    val sessionId: String get() = session.id
    internal var submissionClaimed = false
    internal fun claimSubmission(): Boolean = runtime.claimInferenceSubmission(this)
    override fun close() = runtime.releaseInferenceSlot(this)
}

data class ComfyHostResources(val pressure: ComfyMemoryPressure, val usedSwapBytes: Long)

enum class ComfyMemoryPressure { NORMAL, WARNING, CRITICAL, UNKNOWN }

/**
 * Owns one dedicated ComfyUI server through [VideoMediaProcess]. The server is lazy, loopback-only,
 * restricted to private per-session directories, and never attaches to a process already on the
 * configured port. This class starts no server during construction or MIDI startup.
 */
class ComfyVideoRuntime internal constructor(
    private val runProcess: (VideoMediaProcessRequest, VideoMediaProcessCancellation) -> VideoMediaProcessResult,
    private val healthProbe: (URI) -> Boolean,
    private val resources: () -> ComfyHostResources,
    private val executor: ExecutorService,
    private val nanoTime: () -> Long,
) : AutoCloseable {
    constructor() : this(
        runProcess = { request, cancellation -> VideoMediaProcess().run(request, cancellation) },
        healthProbe = ::defaultHealthProbe,
        resources = MacComfyHostResources::sample,
        executor = Executors.newCachedThreadPool { runnable ->
            Thread(runnable, "melotrail-comfy-runtime").apply { isDaemon = true }
        },
        nanoTime = System::nanoTime,
    )

    private val lock = Any()
    private var stateValue = ComfyVideoRuntimeState.STOPPED
    private var owned: OwnedSession? = null
    private var lastFailureValue: ComfyVideoRuntimeException? = null
    private var closed = false

    val state: ComfyVideoRuntimeState get() = synchronized(lock) { stateValue }
    val lastFailure: ComfyVideoRuntimeException? get() = synchronized(lock) { lastFailureValue }

    fun start(
        setup: ReadyLocalVideoSetup,
        sessionsParent: Path = setup.applicationSupportRoot.resolve("runs"),
        port: Int = setup.server.defaultPort,
    ): ComfyVideoSession {
        validateStart(setup, sessionsParent, port)
        val owner = synchronized(lock) {
            if (closed || (stateValue != ComfyVideoRuntimeState.STOPPED && stateValue != ComfyVideoRuntimeState.FAILED)) {
                throw ComfyVideoRuntimeException(ComfyVideoRuntimeFailure.INVALID_REQUEST, "ComfyUI runtime is already $stateValue.")
            }
            owned?.let {
                throw ComfyVideoRuntimeException(
                    ComfyVideoRuntimeFailure.INVALID_REQUEST,
                    "A prior ComfyUI process is not yet reaped.",
                )
            }
            stateValue = ComfyVideoRuntimeState.STARTING
            lastFailureValue = null
            try {
                setup.verifyForLaunch()
                ensurePortFree(setup.server.host, port)
                val baseline = checkedResources("before ComfyUI startup")
                if (baseline.pressure == ComfyMemoryPressure.CRITICAL || baseline.pressure == ComfyMemoryPressure.UNKNOWN) {
                    throw ComfyVideoRuntimeException(
                        ComfyVideoRuntimeFailure.RESOURCE_LIMIT,
                        "Refusing to start pinned ComfyUI because memory pressure is ${baseline.pressure.name.lowercase()}.",
                    )
                }
                val session = createSession(setup, sessionsParent, port)
                val cancellation = VideoMediaProcessCancellation()
                val identity = UUID.randomUUID().toString()
                val routeToken = UUID.randomUUID().toString()
                val request = launchRequest(setup, session, identity, routeToken)
                val process = executor.submit<VideoMediaProcessResult> { runProcess(request, cancellation) }
                OwnedSession(setup, session, identity, routeToken, cancellation, process, baseline, nanoTime()).also { owned = it }
            } catch (error: ComfyVideoRuntimeException) {
                stateValue = ComfyVideoRuntimeState.FAILED
                lastFailureValue = error
                throw error
            } catch (error: Exception) {
                val wrapped = ComfyVideoRuntimeException(
                    ComfyVideoRuntimeFailure.STARTUP_FAILED,
                    "Could not register the owned ComfyUI process: ${useful(error)}",
                    error,
                )
                stateValue = ComfyVideoRuntimeState.FAILED
                lastFailureValue = wrapped
                throw wrapped
            }
        }

        try {
            waitForReadiness(owner)
            synchronized(lock) {
                if (owned !== owner || stateValue != ComfyVideoRuntimeState.STARTING) {
                    throw ComfyVideoRuntimeException(ComfyVideoRuntimeFailure.STARTUP_FAILED, "ComfyUI startup was stopped.")
                }
                stateValue = ComfyVideoRuntimeState.READY
                owner.monitor = executor.submit { monitor(owner) }
            }
            return owner.session
        } catch (error: ComfyVideoRuntimeException) {
            stopAfterFailure(owner, error)
            throw error
        } catch (error: Exception) {
            val wrapped = ComfyVideoRuntimeException(
                ComfyVideoRuntimeFailure.STARTUP_FAILED,
                "Pinned ComfyUI startup failed: ${useful(error)}",
                error,
            )
            stopAfterFailure(owner, wrapped)
            throw wrapped
        }
    }

    /** Reopening an active persisted attempt returns its same lease and original deadline. */
    fun acquireInferenceSlot(session: ComfyVideoSession, attemptKey: String): ComfyVideoInferenceSlot = synchronized(lock) {
        val owner = owned
        if (stateValue != ComfyVideoRuntimeState.READY || owner == null || owner.process.isDone) {
            throw ComfyVideoRuntimeException(ComfyVideoRuntimeFailure.NOT_RUNNING, "ComfyUI runtime is not ready.")
        }
        if (owner.session !== session || attemptKey.isBlank() || attemptKey.length > 256) {
            throw ComfyVideoRuntimeException(ComfyVideoRuntimeFailure.INVALID_REQUEST, "Inference requires the exact session and a persisted attempt key.")
        }
        owner.inferenceSlot?.let { existing ->
            if (existing.attemptKey == attemptKey) return@synchronized existing
            throw ComfyVideoRuntimeException(
                ComfyVideoRuntimeFailure.INFERENCE_BUSY,
                "The pinned local runtime permits one inference job at a time.",
            )
        }
        val slot = ComfyVideoInferenceSlot(this, session, attemptKey)
        owner.inferenceStartedNanos = nanoTime()
        owner.inferenceSlot = slot
        slot
    }

    /**
     * Issues a connection only for the exact session instance returned by [start]. Session values
     * are intentionally public metadata; copying one does not copy runtime ownership.
     */
    fun connection(session: ComfyVideoSession): ComfyVideoConnection = synchronized(lock) {
        val owner = owned
        if (stateValue != ComfyVideoRuntimeState.READY || owner == null || owner.process.isDone) {
            throw ComfyVideoRuntimeException(ComfyVideoRuntimeFailure.NOT_RUNNING, "ComfyUI runtime is not ready.")
        }
        if (owner.session !== session) {
            throw ComfyVideoRuntimeException(
                ComfyVideoRuntimeFailure.INVALID_REQUEST,
                "Only the exact session returned by this live runtime can open a ComfyUI connection.",
            )
        }
        ComfyVideoConnection(this, owner.connectionCapability, owner.session.id)
    }

    fun stop() {
        val owner = synchronized(lock) {
            val current = owned ?: run {
                stateValue = ComfyVideoRuntimeState.STOPPED
                return
            }
            stateValue = ComfyVideoRuntimeState.STOPPING
            current.stopRequested.set(true)
            current.inferenceSlot = null
            current.inferenceStartedNanos = null
            current
        }
        owner.cancellation.cancel()
        val failure = awaitTermination(owner)
        owner.monitor?.cancel(true)
        synchronized(lock) {
            if (owned === owner) {
                if (failure == null) owned = null
                owner.inferenceSlot = null
                if (failure == null) {
                    stateValue = ComfyVideoRuntimeState.STOPPED
                    lastFailureValue = null
                } else {
                    stateValue = ComfyVideoRuntimeState.FAILED
                    lastFailureValue = failure
                }
            }
        }
        if (failure != null) throw failure
    }

    override fun close() {
        synchronized(lock) { closed = true }
        try {
            stop()
        } finally {
            // Do not interrupt or discard the only process supervisor after a bounded wait expires.
            synchronized(lock) { if (owned == null) executor.shutdown() }
        }
    }

    internal fun claimInferenceSubmission(slot: ComfyVideoInferenceSlot): Boolean = synchronized(lock) {
        val owner = owned
        if (stateValue != ComfyVideoRuntimeState.READY || owner == null || owner.process.isDone ||
            owner.session !== slot.session || owner.inferenceSlot !== slot || slot.submissionClaimed
        ) return@synchronized false
        slot.submissionClaimed = true
        true
    }

    /** Terminal reconciliation can release an attempt even through a reconstructed backend. */
    fun releaseInferenceSlot(session: ComfyVideoSession, attemptKey: String) {
        synchronized(lock) {
            owned?.takeIf { it.session === session && it.inferenceSlot?.attemptKey == attemptKey }?.let {
                it.inferenceStartedNanos = null
                it.inferenceSlot = null
            }
        }
    }

    internal fun releaseInferenceSlot(slot: ComfyVideoInferenceSlot) {
        synchronized(lock) {
            // A retained handle cannot close a replacement lease, even for a reused key.
            owned?.takeIf { it.session === slot.session && it.inferenceSlot === slot }?.let {
                it.inferenceStartedNanos = null
                it.inferenceSlot = null
            }
        }
    }

    internal fun resolveHttpUri(
        capability: Any,
        sessionId: String,
        method: ComfyVideoHttpMethod,
        operationPath: String,
    ): URI {
        val operation = validateHttpOperation(method, operationPath)
        val target = connectionTarget(capability, sessionId)
        val query = operation.rawQuery?.let { "?$it" }.orEmpty()
        return URI("${target.endpoint.scheme}://${target.endpoint.rawAuthority}${target.namespace}${operation.rawPath}$query")
    }

    internal fun resolveWebSocketUri(capability: Any, sessionId: String, clientId: String): URI {
        if (!CLIENT_ID.matches(clientId)) {
            throw ComfyVideoRuntimeException(
                ComfyVideoRuntimeFailure.INVALID_REQUEST,
                "ComfyUI WebSocket client ID must contain 1-128 letters, digits, dots, underscores or hyphens.",
            )
        }
        val target = connectionTarget(capability, sessionId)
        return URI(
            "ws",
            null,
            target.endpoint.host,
            target.endpoint.port,
            target.namespace + "/ws",
            "clientId=$clientId",
            null,
        )
    }

    private fun connectionTarget(capability: Any, sessionId: String): ConnectionTarget = synchronized(lock) {
        val owner = owned
        if (stateValue != ComfyVideoRuntimeState.READY || owner == null || owner.process.isDone ||
            owner.connectionCapability !== capability || owner.session.id != sessionId
        ) {
            throw ComfyVideoRuntimeException(
                ComfyVideoRuntimeFailure.NOT_RUNNING,
                "The ComfyUI connection is stale or its owned server is not ready.",
            )
        }
        ConnectionTarget(owner.session.endpoint, owner.namespace)
    }

    private fun validateHttpOperation(method: ComfyVideoHttpMethod, operationPath: String): URI {
        val operation = try {
            URI(operationPath)
        } catch (error: Exception) {
            throw invalidOperation("ComfyUI operation path is not a valid URI.", error)
        }
        if (operationPath.length > MAX_OPERATION_LENGTH || operation.isAbsolute || operation.rawAuthority != null ||
            operation.rawFragment != null || operation.rawPath.isNullOrEmpty() || !operation.rawPath.startsWith("/") ||
            operation.rawPath.contains("//")
        ) {
            throw invalidOperation("ComfyUI operation must be one bounded absolute-path reference without authority or fragment.")
        }
        val path = operation.rawPath
        val allowed = when (method) {
            ComfyVideoHttpMethod.GET -> path in READ_OPERATIONS ||
                safeDynamicOperation(path, HISTORY_ITEM, "/history/") ||
                safeDynamicOperation(path, OBJECT_INFO_ITEM, "/object_info/")
            ComfyVideoHttpMethod.POST -> path in MUTATION_OPERATIONS
        }
        if (!allowed) {
            throw invalidOperation("Unsupported ComfyUI ${method.name} operation path: $path")
        }
        return operation
    }

    private fun safeDynamicOperation(path: String, pattern: Regex, prefix: String): Boolean {
        if (!pattern.matches(path)) return false
        return path.removePrefix(prefix) !in setOf(".", "..")
    }

    private fun invalidOperation(message: String, cause: Throwable? = null) = ComfyVideoRuntimeException(
        ComfyVideoRuntimeFailure.INVALID_REQUEST,
        message,
        cause,
    )

    private fun waitForReadiness(owner: OwnedSession) {
        val profile = owner.setup.server
        val deadline = owner.startedNanos + Duration.ofSeconds(profile.startupTimeoutSeconds).toNanos()
        while (nanoTime() < deadline) {
            if (owner.stopRequested.get()) {
                throw ComfyVideoRuntimeException(ComfyVideoRuntimeFailure.STARTUP_FAILED, "ComfyUI startup was stopped.")
            }
            processFailure(owner)?.let { throw it }
            enforceResources(owner, "during ComfyUI startup")
            if (isHealthy(owner)) {
                // The response must carry the nonce installed inside this child, never sent in the HTTP request.
                processFailure(owner)?.let { throw it }
                sleep(profile.pollIntervalMillis.coerceAtMost(100))
                processFailure(owner)?.let { throw it }
                if (!owner.process.isDone) return
            }
            sleep(profile.pollIntervalMillis)
        }
        throw ComfyVideoRuntimeException(
            ComfyVideoRuntimeFailure.STARTUP_TIMEOUT,
            "Pinned ComfyUI did not become healthy within ${profile.startupTimeoutSeconds} seconds.",
        )
    }

    private fun monitor(owner: OwnedSession) {
        val profile = owner.setup.server
        var failedHealthChecks = 0
        while (!Thread.currentThread().isInterrupted) {
            val active = synchronized(lock) { owned === owner && stateValue == ComfyVideoRuntimeState.READY }
            if (!active) return
            val failure = processFailure(owner)
                ?: resourceFailure(owner, "while ComfyUI was running")
                ?: inferenceTimeoutFailure(owner)
            if (failure != null) {
                failAndCancel(owner, failure)
                return
            }
            if (isHealthy(owner)) failedHealthChecks = 0
            else failedHealthChecks++
            if (failedHealthChecks >= MAX_FAILED_HEALTH_CHECKS) {
                failAndCancel(owner, ComfyVideoRuntimeException(
                    ComfyVideoRuntimeFailure.HEALTH_LOST,
                    "Owned ComfyUI server failed $failedHealthChecks consecutive health checks.",
                ))
                return
            }
            sleep(profile.pollIntervalMillis)
        }
    }

    private fun inferenceTimeoutFailure(owner: OwnedSession): ComfyVideoRuntimeException? {
        val started = owner.inferenceStartedNanos ?: return null
        val elapsed = nanoTime() - started
        return if (elapsed >= Duration.ofSeconds(owner.setup.server.inferenceTimeoutSeconds).toNanos()) {
            ComfyVideoRuntimeException(
                ComfyVideoRuntimeFailure.RESOURCE_LIMIT,
                "The single local inference slot exceeded its ${owner.setup.server.inferenceTimeoutSeconds} second bound.",
            )
        } else null
    }

    private fun enforceResources(owner: OwnedSession, phase: String) {
        resourceFailure(owner, phase)?.let { throw it }
    }

    private fun resourceFailure(owner: OwnedSession, phase: String): ComfyVideoRuntimeException? {
        val sample = try { checkedResources(phase) } catch (error: ComfyVideoRuntimeException) { return error }
        if (sample.pressure == ComfyMemoryPressure.CRITICAL || sample.pressure == ComfyMemoryPressure.UNKNOWN) {
            return ComfyVideoRuntimeException(
                ComfyVideoRuntimeFailure.RESOURCE_LIMIT,
                "Stopping pinned ComfyUI $phase because memory pressure is ${sample.pressure.name.lowercase()}.",
            )
        }
        val growth = (sample.usedSwapBytes - owner.baseline.usedSwapBytes).coerceAtLeast(0)
        return if (growth >= owner.setup.server.additionalSwapLimitBytes) {
            ComfyVideoRuntimeException(
                ComfyVideoRuntimeFailure.RESOURCE_LIMIT,
                "Stopping pinned ComfyUI $phase after $growth bytes of additional swap (limit ${owner.setup.server.additionalSwapLimitBytes}).",
            )
        } else null
    }

    private fun checkedResources(phase: String): ComfyHostResources = try {
        resources()
    } catch (error: Exception) {
        throw ComfyVideoRuntimeException(
            ComfyVideoRuntimeFailure.RESOURCE_LIMIT,
            "Cannot read required host resource state $phase: ${useful(error)}",
            error,
        )
    }

    private fun isHealthy(owner: OwnedSession): Boolean = try {
        healthProbe(owner.session.endpoint.resolve("/system_stats#${owner.identity}"))
    } catch (_: Exception) {
        false // A failed probe has the same bounded consecutive-failure policy as unhealthy HTTP.
    }

    private fun failAndCancel(owner: OwnedSession, failure: ComfyVideoRuntimeException) {
        synchronized(lock) {
            if (owned !== owner || stateValue != ComfyVideoRuntimeState.READY) return
            stateValue = ComfyVideoRuntimeState.FAILED
            lastFailureValue = failure
            owner.inferenceSlot = null
            owner.inferenceStartedNanos = null
        }
        stopAfterFailure(owner, failure)
    }

    private fun stopAfterFailure(owner: OwnedSession, failure: ComfyVideoRuntimeException) {
        owner.cancellation.cancel()
        val cleanup = awaitTermination(owner)
        synchronized(lock) {
            if (owned === owner && !owner.stopRequested.get()) {
                // A timeout is not termination. Keep the handle so restart cannot overlap cleanup.
                if (cleanup == null) owned = null
                owner.inferenceSlot = null
                owner.inferenceStartedNanos = null
                stateValue = ComfyVideoRuntimeState.FAILED
                lastFailureValue = failure
            }
        }
        cleanup?.let { failure.addSuppressed(it) }
    }

    private fun awaitTermination(owner: OwnedSession): ComfyVideoRuntimeException? {
        return try {
            owner.process.get(owner.setup.server.shutdownTimeoutSeconds, TimeUnit.SECONDS)
            null
        } catch (error: ExecutionException) {
            val cause = error.cause
            // The supervisor returns ordinary exit/cancel failures only after cleanup. Preserve
            // ownership when it explicitly reports that native supervision could not be confirmed.
            val incomplete = cause is VideoMediaProcessException &&
                (cause.failure == VideoMediaProcessFailure.SUPERVISION_FAILED ||
                    cause.suppressed.any { it is VideoMediaProcessException && it.failure == VideoMediaProcessFailure.SUPERVISION_FAILED })
            if (!incomplete) null else ComfyVideoRuntimeException(
                ComfyVideoRuntimeFailure.SHUTDOWN_FAILED,
                "Owned ComfyUI process did not stop cleanly: ${useful(cause ?: error)}",
                cause ?: error,
            )
        } catch (error: TimeoutException) {
            ComfyVideoRuntimeException(
                ComfyVideoRuntimeFailure.SHUTDOWN_FAILED,
                "Owned ComfyUI process was not reaped within ${owner.setup.server.shutdownTimeoutSeconds} seconds.",
                error,
            )
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            ComfyVideoRuntimeException(
                ComfyVideoRuntimeFailure.SHUTDOWN_FAILED,
                "Interrupted while reaping owned ComfyUI process.",
                error,
            )
        }
    }

    private fun processFailure(owner: OwnedSession): ComfyVideoRuntimeException? {
        if (!owner.process.isDone) return null
        return try {
            owner.process.get()
            ComfyVideoRuntimeException(
                ComfyVideoRuntimeFailure.STARTUP_FAILED,
                "Owned ComfyUI process exited before it was stopped.",
            )
        } catch (error: ExecutionException) {
            ComfyVideoRuntimeException(
                ComfyVideoRuntimeFailure.STARTUP_FAILED,
                "Owned ComfyUI process failed: ${useful(error.cause ?: error)}",
                error.cause ?: error,
            )
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            ComfyVideoRuntimeException(ComfyVideoRuntimeFailure.STARTUP_FAILED, "Interrupted while observing ComfyUI.", error)
        }
    }

    private fun validateStart(setup: ReadyLocalVideoSetup, sessionsParent: Path, port: Int) {
        if (setup.server.host != LOOPBACK || port !in 1024..65535) {
            throw ComfyVideoRuntimeException(
                ComfyVideoRuntimeFailure.INVALID_REQUEST,
                "ComfyUI requires loopback $LOOPBACK and a non-privileged port in 1024..65535.",
            )
        }
        if (!sessionsParent.isAbsolute || Files.isSymbolicLink(sessionsParent) ||
            !Files.isDirectory(sessionsParent, NOFOLLOW_LINKS)
        ) {
            throw ComfyVideoRuntimeException(
                ComfyVideoRuntimeFailure.INVALID_REQUEST,
                "Session parent must be an existing absolute non-symlink directory: $sessionsParent",
            )
        }
        if (!sessionsParent.toRealPath().startsWith(setup.applicationSupportRoot)) {
            throw ComfyVideoRuntimeException(
                ComfyVideoRuntimeFailure.INVALID_REQUEST,
                "Session parent must remain inside the verified MelotrailVideo root.",
            )
        }
    }

    private fun ensurePortFree(host: String, port: Int) {
        try {
            ServerSocket().use { socket ->
                socket.reuseAddress = false
                socket.bind(InetSocketAddress(InetAddress.getByName(host), port), 1)
            }
        } catch (error: Exception) {
            throw ComfyVideoRuntimeException(
                ComfyVideoRuntimeFailure.PORT_OCCUPIED,
                "Refusing to attach to occupied loopback port $host:$port.",
                error,
            )
        }
    }

    private fun createSession(setup: ReadyLocalVideoSetup, parent: Path, port: Int): ComfyVideoSession {
        val id = "comfy-${UUID.randomUUID()}"
        val directory = parent.toRealPath().resolve(id)
        return ComfyVideoSession(
            id,
            URI("http://${setup.server.host}:$port/"),
            directory,
            directory.resolve("input"),
            directory.resolve("output"),
            directory.resolve("temp"),
            directory.resolve("user"),
        )
    }

    private fun launchRequest(
        setup: ReadyLocalVideoSetup,
        session: ComfyVideoSession,
        identity: String,
        routeToken: String,
    ): VideoMediaProcessRequest {
        val arguments = listOf(
            "-c", BOOTSTRAP,
            session.directory.toString(),
            setup.mainScript.toString(),
            identity,
            routeToken,
            "--listen", setup.server.host,
            "--port", session.endpoint.port.toString(),
            "--input-directory", session.inputDirectory.toString(),
            "--output-directory", session.outputDirectory.toString(),
            "--temp-directory", session.temporaryDirectory.toString(),
            "--user-directory", session.userDirectory.toString(),
            "--extra-model-paths-config", setup.modelPathsConfig.toString(),
        ) + setup.server.arguments
        val inheritedPath = System.getenv("PATH").orEmpty()
        return VideoMediaProcessRequest(
            executable = setup.pythonExecutable,
            executableSha256 = setup.pythonExecutableSha256,
            arguments = arguments,
            workingDirectory = session.directory,
            timeout = Duration.ofSeconds(setup.server.sessionTimeoutSeconds),
            maxStdoutBytes = 4 * 1024 * 1024,
            maxStderrBytes = 4 * 1024 * 1024,
            environment = mapOf(
                "VIRTUAL_ENV" to setup.pythonVirtualEnvironment.toString(),
                "PYTHONPATH" to setup.pythonSitePackages.toString(),
                "PYTHONNOUSERSITE" to "1",
                "PYTHONUNBUFFERED" to "1",
                "PYTHONDONTWRITEBYTECODE" to "1",
                "PYTHONPYCACHEPREFIX" to session.temporaryDirectory.resolve("pycache").toString(),
                "HF_HUB_OFFLINE" to "1",
                "HF_HUB_DISABLE_TELEMETRY" to "1",
                "TRANSFORMERS_OFFLINE" to "1",
                "DO_NOT_TRACK" to "1",
                "PATH" to "${setup.pythonVirtualEnvironment.resolve("bin")}:$inheritedPath",
            ),
        )
    }

    private fun sleep(millis: Long) {
        try {
            Thread.sleep(millis.coerceAtLeast(1))
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            throw ComfyVideoRuntimeException(ComfyVideoRuntimeFailure.STARTUP_FAILED, "ComfyUI runtime wait was interrupted.", error)
        }
    }

    private data class OwnedSession(
        val setup: ReadyLocalVideoSetup,
        val session: ComfyVideoSession,
        val identity: String,
        val routeToken: String,
        val cancellation: VideoMediaProcessCancellation,
        val process: Future<VideoMediaProcessResult>,
        val baseline: ComfyHostResources,
        val startedNanos: Long,
        val connectionCapability: Any = Any(),
        var inferenceSlot: ComfyVideoInferenceSlot? = null,
        val stopRequested: AtomicBoolean = AtomicBoolean(false),
        @Volatile var inferenceStartedNanos: Long? = null,
        @Volatile var monitor: Future<*>? = null,
    )

    private data class ConnectionTarget(val endpoint: URI, val namespace: String)

    private val OwnedSession.namespace: String get() = "/_melotrail/$routeToken"

    companion object {
        private const val LOOPBACK = "127.0.0.1"
        private const val MAX_FAILED_HEALTH_CHECKS = 3
        private const val MAX_OPERATION_LENGTH = 8_192
        private val CLIENT_ID = Regex("[A-Za-z0-9._-]{1,128}")
        private val HISTORY_ITEM = Regex("/history/[A-Za-z0-9._~-]{1,256}")
        private val OBJECT_INFO_ITEM = Regex("/object_info/[A-Za-z0-9._~-]{1,256}")
        private val READ_OPERATIONS = setOf(
            "/system_stats",
            "/object_info",
            "/prompt",
            "/history",
            "/queue",
            "/view",
        )
        private val MUTATION_OPERATIONS = setOf(
            "/upload/image",
            "/prompt",
            "/queue",
            "/interrupt",
            "/history",
        )
        private val BOOTSTRAP = """
            import os,pathlib,runpy,sys
            session=pathlib.Path(sys.argv[1])
            [session.joinpath(name).mkdir() for name in ('input','output','temp','user')]
            main=sys.argv[2]
            identity=sys.argv[3]
            route_token=sys.argv[4]
            namespace='/_melotrail/'+route_token
            sys.argv=[main]+sys.argv[5:]
            from aiohttp import web
            original_init=web.Application.__init__
            original_add_routes=web.Application.add_routes
            allowed={
                'GET':{'/system_stats','/object_info','/object_info/{node_class}','/prompt','/history','/history/{prompt_id}','/queue','/view','/ws'},
                'POST':{'/upload/image','/prompt','/queue','/interrupt','/history'},
            }
            @web.middleware
            async def owned_only(request,handler):
                if request.path == '/system_stats' or request.path.startswith(namespace+'/'):
                    return await handler(request)
                return web.Response(status=404)
            async def attest(request,response):
                if request.path == '/system_stats' or request.path == namespace+'/system_stats':
                    response.headers['X-Melotrail-Session']=identity
            def owned_init(app,*args,**kwargs):
                middlewares=list(kwargs.pop('middlewares',[]))
                middlewares.insert(0,owned_only)
                kwargs['middlewares']=middlewares
                original_init(app,*args,**kwargs)
                app.on_response_prepare.append(attest)
            def owned_add_routes(app,routes):
                routes=list(routes)
                aliases=web.RouteTableDef()
                for route in routes:
                    if isinstance(route,web.RouteDef) and route.path in allowed.get(route.method,set()):
                        aliases.route(route.method,namespace+route.path)(route.handler,**route.kwargs)
                original_add_routes(app,aliases)
                return original_add_routes(app,routes)
            web.Application.__init__=owned_init
            web.Application.add_routes=owned_add_routes
            os.chdir(str(pathlib.Path(main).parent))
            runpy.run_path(main,run_name='__main__')
        """.trimIndent()

        internal fun defaultHealthProbe(uri: URI): Boolean {
            val expectedIdentity = uri.fragment ?: return false
            val connection = URI(uri.scheme, uri.authority, uri.path, uri.query, null).toURL().openConnection() as HttpURLConnection
            connection.connectTimeout = 1_000
            connection.readTimeout = 1_000
            connection.instanceFollowRedirects = false
            connection.requestMethod = "GET"
            return try {
                if (connection.responseCode != 200 || connection.getHeaderField("X-Melotrail-Session") != expectedIdentity) false
                else connection.inputStream.use { input ->
                    String(input.readNBytes(65_536), Charsets.UTF_8).contains("\"comfyui_version\"")
                }
            } catch (_: Exception) {
                false
            } finally {
                connection.disconnect()
            }
        }

        private fun useful(error: Throwable): String = error.message ?: error::class.simpleName.orEmpty()
    }
}

/** Read-only macOS host pressure/swap sampling; it does not alter OS memory controls. */
private object MacComfyHostResources {
    fun sample(): ComfyHostResources {
        val pressureMemory = Memory(4)
        val pressureSize = NativeLongByReference(NativeLong(pressureMemory.size()))
        check(Sysctl.instance.sysctlbyname("kern.memorystatus_vm_pressure_level", pressureMemory, pressureSize, null, NativeLong(0)) == 0) {
            "sysctl kern.memorystatus_vm_pressure_level failed"
        }
        val swapMemory = Memory(32)
        val swapSize = NativeLongByReference(NativeLong(swapMemory.size()))
        check(Sysctl.instance.sysctlbyname("vm.swapusage", swapMemory, swapSize, null, NativeLong(0)) == 0) {
            "sysctl vm.swapusage failed"
        }
        val pressure = when (pressureMemory.getInt(0)) {
            1 -> ComfyMemoryPressure.NORMAL
            2 -> ComfyMemoryPressure.WARNING
            4 -> ComfyMemoryPressure.CRITICAL
            else -> ComfyMemoryPressure.UNKNOWN
        }
        // Darwin xsw_usage: total, available, used (uint64), page size (uint32), encrypted.
        return ComfyHostResources(pressure, swapMemory.getLong(16).coerceAtLeast(0))
    }
}

private interface Sysctl : Library {
    fun sysctlbyname(
        name: String,
        oldValue: com.sun.jna.Pointer,
        oldLength: NativeLongByReference,
        newValue: com.sun.jna.Pointer?,
        newLength: NativeLong,
    ): Int

    companion object {
        val instance: Sysctl by lazy { Native.load("System", Sysctl::class.java) }
    }
}
