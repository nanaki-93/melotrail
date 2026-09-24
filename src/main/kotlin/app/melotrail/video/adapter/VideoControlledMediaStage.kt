package app.melotrail.video.adapter

import app.melotrail.video.application.*
import app.melotrail.video.domain.*
import java.time.Clock
import java.time.Instant
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.security.MessageDigest
import java.time.Duration
import java.lang.management.ManagementFactory
import com.sun.management.OperatingSystemMXBean
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.Semaphore

/** Claimed controlled-motion worker. Rendering/encoding is supplied by the media pipeline;
 * no native work is performed by submit or while the job ledger is locked. A missing worker
 * after restart is unknown, never a reason to relaunch a PENDING attempt. */
class VideoControlledMediaStage(
    private val jobs: VideoJobPersistence,
    private val admissionDomainId: String,
    private val execute: (VideoGenerationJobRequest, VideoOwnedBackendAttempt, VideoMediaProcessCancellation) -> VideoBackendObservation,
    private val clock: Clock = Clock.systemUTC(),
    private val boundedNative: Boolean = false,
) : VideoGenerationBackendPort {
    /** Production rendering path. The encoded output and terminal reconciliation belong to
     * subsequent slices; render completion alone is never reported as a successful video. */
    constructor(jobs: VideoJobPersistence, admissionDomainId: String, projectRoot: Path, outputRoot: Path,
                renderer: VideoMotionRenderer = VideoMotionRenderer(), clock: Clock = Clock.systemUTC(),
                hostFreeMemoryBytes: () -> Long = {
                    (ManagementFactory.getOperatingSystemMXBean() as? OperatingSystemMXBean)?.freeMemorySize ?: -1L
                }) :
        this(jobs, admissionDomainId, { request, owned, cancellation ->
            renderClaimed(request, owned, cancellation, projectRoot, outputRoot, renderer, hostFreeMemoryBytes)
        }, clock, true)

    override val backendId: String = BACKEND_ID

    override fun availability() = VideoBackendAvailability(
        backendId, VideoBackendAvailabilityStatus.AVAILABLE, Instant.now(clock).toString(),
        setOf(VideoGenerationInputKind.CONTROLLED_MOTION), emptyList(),
        "Controlled media worker configured; ComfyUI and models are not required.",
    )

    override fun submit(command: VideoBackendSubmissionCommand): VideoBackendSubmission {
        val owned = command.ownedAttempt
        val request = try { claimed(owned) } catch (error: Exception) {
            return VideoBackendSubmission.Rejected(error.message ?: "Claim verification failed.", false)
        } ?: return VideoBackendSubmission.Rejected("Current PENDING controlled attempt is not owned by this submission.", false)
        if (request.input != command.input || request.modelRequirements != command.modelRequirements || request.execution != command.execution) {
            return VideoBackendSubmission.Rejected("Submission fields differ from persisted controlled execution.", false)
        }
        val key = "$admissionDomainId:${owned.requestId}:${owned.attemptId}:${owned.ownershipToken}"
        if (workers.containsKey(key)) return VideoBackendSubmission.Uncertain("Controlled attempt was already submitted; observe its existing work.")
        if (boundedNative && !nativeSlot.tryAcquire()) return VideoBackendSubmission.Uncertain("Controlled native slot is occupied; no work was queued.")
        val work = Work()
        if (workers.putIfAbsent(key, work) != null) {
            if (boundedNative) nativeSlot.release()
            return VideoBackendSubmission.Uncertain("Controlled attempt was already submitted; observe its existing work.")
        }
        try {
            pool.execute {
                try {
                    // Cancellation or a terminal reconciliation can win after submit. Never
                    // execute a caller's replacement command or run an unclaimed attempt.
                    val current = claimed(owned, allowAcknowledged = true)
                    work.result = if (current == null || work.cancellation.isCancelled()) {
                        VideoBackendObservation.Unknown("Claim changed or cancellation preceded controlled execution.")
                    } else execute(current, owned, work.cancellation)
                } catch (error: Exception) {
                    work.result = VideoBackendObservation.Unknown(error.message ?: "Controlled worker outcome is uncertain.")
                } finally {
                    if (boundedNative) nativeSlot.release()
                }
            }
        } catch (error: Exception) {
            // No task was enqueued; do not allow another invocation of this identity.
            work.result = VideoBackendObservation.Unknown("Controlled worker could not start: ${error.message}")
            if (boundedNative) nativeSlot.release()
            return VideoBackendSubmission.Uncertain("Controlled worker could not start: ${error.message}")
        }
        return VideoBackendSubmission.Accepted(key)
    }

    override fun observe(ownedAttempt: VideoOwnedBackendAttempt): VideoBackendObservation {
        val key = "$admissionDomainId:${ownedAttempt.requestId}:${ownedAttempt.attemptId}:${ownedAttempt.ownershipToken}"
        if (ownedAttempt.backendId != backendId ||
            ownedAttempt.providerWorkId != null && ownedAttempt.providerWorkId != key) {
            return VideoBackendObservation.Unknown("Controlled attempt identity does not match the worker.")
        }
        val work = workers[key] ?: return VideoBackendObservation.Unknown("Claimed controlled execution has no known worker; do not relaunch it.")
        return work.result ?: VideoBackendObservation.Running(key, null)
    }

    override fun requestCancellation(ownedAttempt: VideoOwnedBackendAttempt): VideoBackendCancellation {
        val key = "$admissionDomainId:${ownedAttempt.requestId}:${ownedAttempt.attemptId}:${ownedAttempt.ownershipToken}"
        if (ownedAttempt.backendId != backendId ||
            ownedAttempt.providerWorkId != null && ownedAttempt.providerWorkId != key) {
            return VideoBackendCancellation.Unknown("Controlled attempt identity does not match the worker.")
        }
        val work = workers[key] ?: return VideoBackendCancellation.Unknown("No owned worker can be confirmed stopped.")
        work.cancellation.cancel()
        return VideoBackendCancellation.Requested
    }

    private fun claimed(owned: VideoOwnedBackendAttempt, allowAcknowledged: Boolean = false): VideoGenerationJobRequest? {
        if (owned.backendId != backendId || owned.providerWorkId != null) return null
        val ledger = jobs.loadOrCreate(admissionDomainId, Instant.now(clock).toString())
        require(ledger.admissionDomainId == admissionDomainId) { "Another admission domain was returned." }
        val job = ledger.jobs.singleOrNull { it.request.id == owned.requestId } ?: return null
        val attempt = job.attempts.lastOrNull() ?: return null
        val request = job.request
        val input = request.input as? VideoControlledMotionGenerationInput ?: return null
        if (attempt.id != owned.attemptId || attempt.ownershipToken != owned.ownershipToken ||
            !(attempt.submissionPhase == VideoSubmissionPhase.PENDING && attempt.status == VideoGenerationAttemptStatus.SUBMITTING ||
                allowAcknowledged && attempt.submissionPhase == VideoSubmissionPhase.ACKNOWLEDGED &&
                attempt.status == VideoGenerationAttemptStatus.ACTIVE && attempt.providerWorkId ==
                "$admissionDomainId:${owned.requestId}:${owned.attemptId}:${owned.ownershipToken}") ||
            (!allowAcknowledged && attempt.providerWorkId != null) || request.backendId != backendId ||
            request.requestFingerprint != owned.requestFingerprint || request.projectId != input.motion.descriptor.projectId ||
            request.execution !is VideoLocalExecutionPolicy || request.execution != input.media.execution ||
            request.requestFingerprint != controlledMotionRequestFingerprint(backendId, input, request.modelRequirements, request.maximumAttempts)
        ) return null
        return request
    }

    private class Work {
        val cancellation = VideoMediaProcessCancellation()
        @Volatile var result: VideoBackendObservation? = null
    }

    companion object {
        const val BACKEND_ID = "controlled-media"

        private fun renderClaimed(request: VideoGenerationJobRequest, owned: VideoOwnedBackendAttempt,
            cancellation: VideoMediaProcessCancellation, projectRoot: Path, outputRoot: Path,
            renderer: VideoMotionRenderer, hostFreeMemoryBytes: () -> Long): VideoBackendObservation {
            val input = request.input as VideoControlledMotionGenerationInput
            val media = input.media
            val motion = input.motion
            val binding = motion.descriptor.runtime
            val start = System.nanoTime()
            val limit = Duration.ofMillis(media.execution.wallClockLimitMillis).toNanos()
            fun remaining(): Duration = Duration.ofNanos((limit - (System.nanoTime() - start)).coerceAtLeast(0))
            fun realDirectory(path: Path): Path {
                val absolute = path.toAbsolutePath().normalize()
                require(Files.isDirectory(absolute, NOFOLLOW_LINKS) && !Files.isSymbolicLink(absolute) && absolute.toRealPath() == absolute) {
                    "Controlled media directory must already exist without a symlink: $absolute"
                }
                return absolute
            }
            if (cancellation.isCancelled()) throw VideoMediaProcessException(VideoMediaProcessFailure.CANCELLED, "Controlled attempt cancelled before setup.")
            val project = realDirectory(projectRoot)
            val output = realDirectory(outputRoot)
            require(output.startsWith(project) && output != project) { "Controlled output must be inside its owned project, separate from sources." }
            require(remaining() > Duration.ZERO) { "Controlled attempt deadline expired before staging." }
            val directory = output.resolve(owned.attemptId)
            Files.createDirectory(directory) // create-new; retain this private attempt evidence on every failure
            val descriptor = directory.resolve("request.json")
            Files.writeString(descriptor, motion.descriptor.requestJson, CREATE_NEW)
            fun hash(path: Path): String {
                val digest = MessageDigest.getInstance("SHA-256")
                Files.newInputStream(path).use { stream ->
                    val bytes = ByteArray(64 * 1024)
                    while (true) { val n = stream.read(bytes); if (n < 0) break; digest.update(bytes, 0, n) }
                }
                return digest.digest().joinToString("") { "%02x".format(it) }
            }
            // The renderer checks its own pins at every chunk. These two media-only pins
            // must also be checked before native setup, not merely trusted from the ledger.
            for (pin in listOf(binding.ffprobe, binding.mediaManifest)) {
                val path = Path.of(requireNotNull(pin.ownedPath))
                require(path.isAbsolute && path.normalize() == path && Files.isRegularFile(path, NOFOLLOW_LINKS) &&
                    !Files.isSymbolicLink(path) && hash(path) == pin.sha256) { "Controlled media pin changed: ${pin.id}" }
            }
            fun usedBytes(): Long {
                var total = 0L
                Files.walk(directory).use { entries -> entries.forEach { file ->
                    require(!Files.isSymbolicLink(file)) { "Symlink in controlled frame staging." }
                    if (Files.isRegularFile(file, NOFOLLOW_LINKS)) total = Math.addExact(total, Files.size(file))
                } }
                return total
            }
            fun budget() {
                val staged = usedBytes()
                if (staged > media.maximumStagingBytes) throw VideoMediaProcessException(
                    VideoMediaProcessFailure.OUTPUT_LIMIT, "Controlled frame staging limit exceeded.")
                // Admit the *entire* remaining staging allowance, not only the output.
                // As frames are written their bytes leave usableSpace, so subtract them
                // from the still-unspent staging allowance to avoid double counting.
                val reserve = Math.addExact(Math.addExact(media.minimumFreeDiskBytes, media.maximumOutputBytes),
                    media.maximumStagingBytes - staged)
                if (Files.getFileStore(directory).usableSpace < reserve) throw VideoMediaProcessException(
                    VideoMediaProcessFailure.OUTPUT_LIMIT, "Controlled staging/output disk admission requires $reserve free bytes.")
            }
            // Admit host capacity before launching; the native supervisor below also
            // measures aggregate RSS of the owned process group and terminates on growth.
            val freeMemory = hostFreeMemoryBytes()
            if (freeMemory < 0 || freeMemory < media.execution.memoryLimitBytes) throw VideoMediaProcessException(
                VideoMediaProcessFailure.OUTPUT_LIMIT,
                "Controlled memory admission requires ${media.execution.memoryLimitBytes} free bytes; available capacity is ${if (freeMemory < 0) "unknown" else freeMemory}.")
            budget() // admission before the first pinned native probe or renderer invocation
            val runtime = VideoMotionRuntime(binding.node, binding.compositor, binding.scenery,
                binding.canvasManifest, binding.canvasArtifacts, binding.ffmpeg, binding.expectedCanvasVersion)
            // Native children can fill a disk before a chunk returns. Watch the owned tree
            // throughout execution, and cancel only this attempt's process token on breach.
            val violation = AtomicReference<Throwable?>()
            val monitor = Thread {
                while (!Thread.currentThread().isInterrupted && !cancellation.isCancelled()) {
                    try {
                        budget()
                        if (remaining() <= Duration.ZERO) throw VideoMediaProcessException(
                            VideoMediaProcessFailure.TIMED_OUT, "Controlled attempt deadline expired.")
                        Thread.sleep(50)
                    } catch (_: InterruptedException) { break }
                    catch (error: Throwable) { violation.compareAndSet(null, error); cancellation.cancel(); break }
                }
            }.apply { name = "video-controlled-budget"; isDaemon = true; start() }
            val result = try {
                renderer.render(VideoMotionRenderRequest(runtime, project, descriptor, directory,
                    motion.startFrame, motion.endFrameExclusive, Duration.ofMillis(media.execution.wallClockLimitMillis),
                    ::remaining, ::budget, media.execution.memoryLimitBytes), cancellation)
            } catch (error: Exception) {
                violation.get()?.let { throw it }
                throw error
            } finally {
                monitor.interrupt()
                monitor.join()
            }
            violation.get()?.let { throw it }
            require(result.invocations.first().startFrame == motion.startFrame &&
                result.invocations.last().endFrameExclusive == motion.endFrameExclusive &&
                result.invocations.zipWithNext().all { (a, b) -> a.endFrameExclusive == b.startFrame } &&
                result.invocations.all { it.endFrameExclusive - it.startFrame in 1..300 }) { "Controlled render chunks are not contiguous." }
            return VideoBackendObservation.Unknown("Verified frames retained in $directory; encoding and completion are not yet available.")
        }

        private val nativeSlot = Semaphore(1)
        private val workers = ConcurrentHashMap<String, Work>()
        private val pool = Executors.newFixedThreadPool(1) { task ->
            Thread(task, "video-controlled-media").apply { isDaemon = true }
        }
    }
}
