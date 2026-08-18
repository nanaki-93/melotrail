package app.melotrail.desktop

/**
 * UI-neutral description of a local operation.  It deliberately contains no
 * Compose types, paths, or backend payloads so it can be used by desktop
 * adapters without making them orchestration owners.
 */
enum class OperationKind {
    PROJECT_OPEN, PROJECT_HYDRATION, IMPORT, INSPECTION, AUDIO_CLEANUP,
    TRANSCRIPTION, MIDI_CLEANUP, MIDI_RENDER, PREVIEW_DECODE_RENDER,
    COHESION, ARRANGEMENT, APPROVAL, STEM_RENDER, MIXING, AUDIO_LOFI,
    MASTERING, EXPORT
}

enum class OperationPhase {
    IDLE, LOCAL, WAITING_FOR_WORKER, WAITING_FOR_MODEL, WAITING_FOR_RENDERER,
    VALIDATING, CANCELLING, COMPLETE, FAILED
}

enum class OperationSeverity { INFORMATION, WARNING, SUCCESS, ERROR }

enum class OperationRetryAction { RETRY_SAFE_OPERATION }

data class OperationWork(val completed: Int, val total: Int) {
    init {
        require(total > 0) { "Operation total must be positive" }
        require(completed in 0..total) { "Completed work must be within the known total" }
    }
}

data class OperationFeedback(
    val sessionId: String,
    val kind: OperationKind? = null,
    val phase: OperationPhase = OperationPhase.IDLE,
    val work: OperationWork? = null,
    val message: String,
    val artifactLabel: String? = null,
    val cancellableAtBoundary: Boolean = false,
    val retryAction: OperationRetryAction? = null,
    val outcomeSeverity: OperationSeverity? = null
) {
    init {
        require(artifactLabel?.contains('/') != true && artifactLabel?.contains('\\') != true) {
            "Only a safe artifact label may be exposed"
        }
        require(phase != OperationPhase.COMPLETE && phase != OperationPhase.FAILED || outcomeSeverity != null) {
            "Completed feedback must have a typed outcome"
        }
        require(phase !in setOf(OperationPhase.COMPLETE, OperationPhase.FAILED) || !cancellableAtBoundary) {
            "Only active operations may be cancellable"
        }
    }

    val active: Boolean get() = phase in setOf(
        OperationPhase.LOCAL, OperationPhase.WAITING_FOR_WORKER, OperationPhase.WAITING_FOR_MODEL,
        OperationPhase.WAITING_FOR_RENDERER, OperationPhase.VALIDATING, OperationPhase.CANCELLING
    )
    val determinate: Boolean get() = active && work != null

    companion object {
        fun idle() = OperationFeedback("idle", message = "Ready.")
    }
}

/**
 * Single-threaded reducer used by the ViewModel.  Callers retain the returned
 * session id and callbacks carrying an old id are ignored, rather than being
 * allowed to overwrite a newer user-visible result.
 */
class OperationFeedbackTracker {
    private var nextId = 0L
    var current: OperationFeedback = OperationFeedback.idle()
        private set

    fun begin(
        kind: OperationKind,
        phase: OperationPhase,
        message: String,
        artifactLabel: String? = null,
        cancellableAtBoundary: Boolean = false
    ): OperationFeedback = OperationFeedback(
        sessionId = "operation-${++nextId}", kind = kind, phase = phase, message = message,
        artifactLabel = artifactLabel, cancellableAtBoundary = cancellableAtBoundary
    ).also { current = it }

    fun progress(
        sessionId: String,
        phase: OperationPhase,
        message: String,
        completed: Int? = null,
        total: Int? = null,
        artifactLabel: String? = null,
        kind: OperationKind? = null
    ): OperationFeedback? {
        if (sessionId != current.sessionId || !current.active) return null
        val work = when {
            completed == null && total == null -> null
            completed != null && total != null && total > 0 -> OperationWork(completed, total)
            else -> null // Unknown or malformed totals must not invent a percentage.
        }
        return current.copy(kind = kind ?: current.kind, phase = phase, message = message, work = work, artifactLabel = artifactLabel ?: current.artifactLabel).also { current = it }
    }

    fun complete(sessionId: String, message: String, severity: OperationSeverity = OperationSeverity.SUCCESS, artifactLabel: String? = null): OperationFeedback? =
        finish(sessionId, OperationPhase.COMPLETE, message, severity, artifactLabel, null)

    fun fail(sessionId: String, message: String, retryAction: OperationRetryAction? = null): OperationFeedback? =
        finish(sessionId, OperationPhase.FAILED, message, OperationSeverity.ERROR, null, retryAction)

    fun cancelAtBoundary(sessionId: String, message: String): OperationFeedback? {
        if (sessionId != current.sessionId || !current.active || !current.cancellableAtBoundary) return null
        return current.copy(phase = OperationPhase.CANCELLING, message = message, work = null, cancellableAtBoundary = false).also { current = it }
    }

    fun dismiss(sessionId: String): Boolean {
        if (sessionId != current.sessionId || current.active) return false
        current = OperationFeedback.idle()
        return true
    }

    private fun finish(
        sessionId: String,
        phase: OperationPhase,
        message: String,
        severity: OperationSeverity,
        artifactLabel: String?,
        retryAction: OperationRetryAction?
    ): OperationFeedback? {
        if (sessionId != current.sessionId || !current.active) return null
        return current.copy(
            phase = phase, message = message, work = null, artifactLabel = artifactLabel ?: current.artifactLabel,
            cancellableAtBoundary = false, retryAction = retryAction, outcomeSeverity = severity
        ).also { current = it }
    }
}

internal fun OperationProgressFeedbackPhase(progress: app.melotrail.application.OperationProgress): OperationPhase = when (progress.operation) {
    "import-part", "inspect-part", "clean-midi" -> OperationPhase.WAITING_FOR_WORKER
    "arrange" -> OperationPhase.WAITING_FOR_MODEL
    "render", "generate-midi" -> OperationPhase.WAITING_FOR_RENDERER
    "analyze-part", "mix" -> OperationPhase.VALIDATING
    "build" -> when (progress.stageIndex) {
        1 -> OperationPhase.VALIDATING
        2 -> OperationPhase.LOCAL
        3 -> OperationPhase.WAITING_FOR_RENDERER
        4 -> OperationPhase.LOCAL
        5, 7, 8 -> OperationPhase.WAITING_FOR_WORKER
        6, 9 -> OperationPhase.LOCAL
        else -> OperationPhase.LOCAL
    }
    else -> OperationPhase.LOCAL
}

internal fun OperationProgressFeedbackKind(progress: app.melotrail.application.OperationProgress): OperationKind = when (progress.operation) {
    "import-part" -> OperationKind.IMPORT
    "inspect-part" -> OperationKind.INSPECTION
    "clean-midi" -> OperationKind.MIDI_CLEANUP
    "analyze-part" -> OperationKind.COHESION
    "arrange" -> OperationKind.ARRANGEMENT
    "generate-midi" -> OperationKind.MIDI_RENDER
    "render" -> OperationKind.STEM_RENDER
    "mix" -> OperationKind.MIXING
    "build" -> when (progress.stageIndex) {
        2 -> OperationKind.MIDI_RENDER
        3 -> OperationKind.STEM_RENDER
        4 -> OperationKind.MIXING
        5 -> OperationKind.COHESION
        6 -> OperationKind.AUDIO_LOFI
        7 -> OperationKind.MASTERING
        8, 9 -> OperationKind.EXPORT
        else -> OperationKind.MASTERING
    }
    else -> OperationKind.PROJECT_HYDRATION
}
