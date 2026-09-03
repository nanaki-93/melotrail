package app.melotrail.audition

import app.melotrail.midi.domain.MidiExportRole
import app.melotrail.midi.domain.MidiExportSong

/** The transport states exposed to the desktop UI for one local MIDI audition. */
enum class MidiAuditionPlaybackState { STOPPED, PLAYING, PAUSED }

/** The musical view currently sent to the audition output. */
sealed interface MidiAuditionScope {
    data object SourceMelody : MidiAuditionScope
    data class Candidate(val candidateId: String, val role: MidiExportRole) : MidiAuditionScope {
        init {
            require(candidateId.isNotBlank()) { "Candidate audition ID must not be blank" }
            require(role != MidiExportRole.MELODY) { "A protected melody is not a generated candidate" }
        }
    }
    data class Occurrence(val occurrenceId: String) : MidiAuditionScope {
        init {
            require(occurrenceId.isNotBlank()) { "Occurrence audition ID must not be blank" }
        }
    }
    /** Ephemeral full-role MIDI preview selected from the Arrange style gallery. */
    data class StylePreview(val styleId: String, val occurrenceId: String) : MidiAuditionScope {
        init {
            require(styleId.matches(STYLE_ID) && occurrenceId.isNotBlank()) { "Style preview identity is invalid" }
        }
    }
    /** Persisted but unaccepted complete-draft evidence selected for one whole-song decision. */
    data class ArrangementDraft(val draftId: String) : MidiAuditionScope {
        init {
            require(draftId.isNotBlank()) { "Arrangement draft audition ID must not be blank" }
        }
    }
    data class Role(val role: MidiExportRole) : MidiAuditionScope
    data object AcceptedArrangement : MidiAuditionScope

    private companion object {
        val STYLE_ID = Regex("[a-z][a-z0-9-]{2,47}")
    }
}

/** A non-empty, exact tick range used for an audition view or loop. */
data class MidiAuditionWindow(val startTick: Long, val endTick: Long) {
    init {
        require(startTick >= 0L) { "Audition window start must not be negative" }
        require(endTick > startTick) { "Audition window end must be after its start" }
    }

    fun contains(tick: Long): Boolean = tick in startTick..endTick
}

/** A continuously repeating range inside the selected audition view. */
data class MidiAuditionLoop(val startTick: Long, val endTick: Long) {
    init {
        require(startTick >= 0L) { "Audition loop start must not be negative" }
        require(endTick > startTick) { "Audition loop end must be after its start" }
    }

    fun asWindow(): MidiAuditionWindow = MidiAuditionWindow(startTick, endTick)
}

/** Immutable input to the audition port; it contains no project or filesystem ownership. */
data class MidiAuditionView(
    val scope: MidiAuditionScope,
    val song: MidiExportSong,
    val window: MidiAuditionWindow = MidiAuditionWindow(0L, song.songEndTick),
    val roles: List<MidiExportRole> = song.roles.map { it.role },
) {
    init {
        require(roles.isNotEmpty()) { "An audition view must contain at least one MIDI role" }
        require(roles.distinct().size == roles.size) { "Audition view roles must be unique" }
        require(roles == roles.sortedBy(MidiExportRole::ordinal)) { "Audition view roles must use deterministic order" }
        require(roles.all { role -> song.roles.any { it.role == role } }) {
            "Every audition view role must be present in its song"
        }
        require(window.endTick <= song.songEndTick) { "Audition window must fit inside the song" }
        when (val selectedScope = scope) {
            MidiAuditionScope.SourceMelody -> require(roles == listOf(MidiExportRole.MELODY)) {
                "A source-melody audition view must contain only the Melody role"
            }
            is MidiAuditionScope.Candidate -> require(roles == listOf(selectedScope.role)) {
                "A candidate audition view must contain only its candidate role"
            }
            is MidiAuditionScope.Role -> require(roles == listOf(selectedScope.role)) {
                "A role audition view must contain only its selected role"
            }
            is MidiAuditionScope.StylePreview -> require(roles == MidiExportRole.entries) {
                "A style preview must contain protected melody and every generated role"
            }
            is MidiAuditionScope.ArrangementDraft -> require(roles == MidiExportRole.entries) {
                "An arrangement draft must contain protected melody and every generated role"
            }
            is MidiAuditionScope.Occurrence,
            MidiAuditionScope.AcceptedArrangement,
            -> Unit
        }
    }

    companion object {
        /** Select only the immutable source melody role for audition. */
        fun sourceMelody(song: MidiExportSong): MidiAuditionView = oneRole(MidiAuditionScope.SourceMelody, song, MidiExportRole.MELODY)

        /** Select one generated candidate role while retaining its explicit candidate identity. */
        fun candidate(candidateId: String, role: MidiExportRole, song: MidiExportSong): MidiAuditionView =
            oneRole(MidiAuditionScope.Candidate(candidateId, role), song, role)

        /** Select one role from a song without claiming it is a generated candidate. */
        fun role(role: MidiExportRole, song: MidiExportSong): MidiAuditionView =
            oneRole(MidiAuditionScope.Role(role), song, role)

        /** Select one exact occurrence window without rewriting any event ticks. */
        fun occurrence(occurrenceId: String, song: MidiExportSong, startTick: Long, endTick: Long): MidiAuditionView =
            MidiAuditionView(MidiAuditionScope.Occurrence(occurrenceId), song, MidiAuditionWindow(startTick, endTick))

        /** Select a non-persistent all-role style preview over one exact occurrence window. */
        fun stylePreview(
            styleId: String,
            occurrenceId: String,
            song: MidiExportSong,
            startTick: Long,
            endTick: Long,
        ): MidiAuditionView = MidiAuditionView(
            MidiAuditionScope.StylePreview(styleId, occurrenceId),
            song,
            MidiAuditionWindow(startTick, endTick),
        )

        /** Select a persisted complete draft without claiming it is accepted or exportable. */
        fun draft(draftId: String, song: MidiExportSong): MidiAuditionView =
            MidiAuditionView(MidiAuditionScope.ArrangementDraft(draftId), song)

        /** Select the currently accepted full arrangement. */
        fun accepted(song: MidiExportSong): MidiAuditionView = MidiAuditionView(MidiAuditionScope.AcceptedArrangement, song)

        private fun oneRole(scope: MidiAuditionScope, song: MidiExportSong, role: MidiExportRole): MidiAuditionView =
            MidiAuditionView(scope, song.copy(roles = listOf(song.role(role))), roles = listOf(role))
    }
}

/** Immutable transport settings passed when a view is opened or supersedes another session. */
data class MidiAuditionPlaybackPlan(
    val view: MidiAuditionView,
    val startTick: Long = view.window.startTick,
    val loop: MidiAuditionLoop? = null,
    val mutedRoles: Set<MidiExportRole> = emptySet(),
    val soloRoles: Set<MidiExportRole> = emptySet(),
    val outputDeviceId: String? = null,
) {
    init {
        require(startTick in view.window.startTick..view.window.endTick) {
            "Audition start must remain inside the selected view window"
        }
        require(mutedRoles.all(view.roles::contains)) { "Muted audition roles must be present in the selected view" }
        require(soloRoles.all(view.roles::contains)) { "Solo audition roles must be present in the selected view" }
        loop?.let { candidate ->
            require(candidate.startTick >= view.window.startTick && candidate.endTick <= view.window.endTick) {
                "Audition loop must remain inside the selected view window"
            }
        }
        require(outputDeviceId == null || outputDeviceId.isNotBlank()) { "Audition output device ID must not be blank" }
    }
}

/** A locally discoverable MIDI receiver offered only for non-authoritative audition. */
data class MidiAuditionOutputDevice(
    val id: String,
    val name: String,
    val vendor: String,
    val description: String,
    val version: String,
) {
    init {
        require(id.isNotBlank()) { "MIDI output device ID must not be blank" }
        require(name.isNotBlank()) { "MIDI output device name must not be blank" }
    }
}

/** Recoverable failures raised by a local MIDI output adapter. */
class MidiAuditionOutputException(
    val code: MidiAuditionProblemCode,
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/** Stable problem codes suitable for a user-facing audition error message. */
enum class MidiAuditionProblemCode {
    INVALID_REQUEST,
    NO_ACTIVE_SESSION,
    DEVICE_UNAVAILABLE,
    DEVICE_LOST,
    OUTPUT_FAILURE,
    CLOSED,
}

/** A typed, recoverable audition problem; it never owns project mutation. */
data class MidiAuditionProblem(
    val code: MidiAuditionProblemCode,
    val message: String,
    val nextAction: String,
)

/** The complete UI-visible transport state of the one active audition controller. */
data class MidiAuditionState(
    val isClosed: Boolean = false,
    val playback: MidiAuditionPlaybackState = MidiAuditionPlaybackState.STOPPED,
    val sessionId: Long? = null,
    val scope: MidiAuditionScope? = null,
    val window: MidiAuditionWindow? = null,
    val positionTick: Long = 0L,
    val loop: MidiAuditionLoop? = null,
    val mutedRoles: Set<MidiExportRole> = emptySet(),
    val soloRoles: Set<MidiExportRole> = emptySet(),
    val outputDeviceId: String? = null,
    val outputDevices: List<MidiAuditionOutputDevice> = emptyList(),
    val lastProblem: MidiAuditionProblem? = null,
)

enum class MidiAuditionAction { SELECT_SCOPE, SELECT_OUTPUT, PLAY, PAUSE, STOP, SEEK, LOOP, MUTE, SOLO }

/** Result of a transport operation, with a recoverable failure instead of an exception. */
sealed interface MidiAuditionResult {
    val state: MidiAuditionState

    data class Applied(val action: MidiAuditionAction, override val state: MidiAuditionState) : MidiAuditionResult
    data class Failed(val problem: MidiAuditionProblem, override val state: MidiAuditionState) : MidiAuditionResult
}

/** Callback from an output adapter when the current non-looping view reaches its end. */
fun interface MidiAuditionOutputListener {
    fun onPlaybackEnded()
}

/** Platform-neutral output boundary used by the stateful audition controller and tests. */
interface MidiAuditionOutput : AutoCloseable {
    /** Discover dedicated local MIDI receivers without opening a playback session. */
    fun availableDevices(): List<MidiAuditionOutputDevice> = emptyList()

    fun open(plan: MidiAuditionPlaybackPlan, listener: MidiAuditionOutputListener): MidiAuditionOutputSession

    override fun close() = Unit
}

/** One opened output resource; closing it must stop playback and release device resources. */
interface MidiAuditionOutputSession : AutoCloseable {
    fun play()
    fun pause()
    fun stop()
    /** Return the adapter's exact transport tick when it can expose one. */
    fun positionTick(): Long? = null
    fun seek(tick: Long)
    fun setLoop(loop: MidiAuditionLoop?)
    fun setMutedRoles(roles: Set<MidiExportRole>)
    fun setSoloRoles(roles: Set<MidiExportRole>)
}

/** Application port for MIDI-only audition. It has no project-store or audio-rendering dependency. */
interface MidiAuditionPort : AutoCloseable {
    val state: MidiAuditionState
    val stateHistory: List<MidiAuditionState>

    fun selectScope(plan: MidiAuditionPlaybackPlan): MidiAuditionResult
    fun play(plan: MidiAuditionPlaybackPlan): MidiAuditionResult
    fun play(): MidiAuditionResult
    fun pause(): MidiAuditionResult
    fun stop(): MidiAuditionResult
    fun seek(tick: Long): MidiAuditionResult
    fun setLoop(loop: MidiAuditionLoop?): MidiAuditionResult
    fun setMutedRole(role: MidiExportRole, muted: Boolean): MidiAuditionResult
    fun setSoloRole(role: MidiExportRole, solo: Boolean): MidiAuditionResult
    fun selectOutputDevice(outputDeviceId: String?): MidiAuditionResult
}

/** Serializes one local audition session and supersedes/cleans up older output sessions. */
class MidiAuditionController(
    private val output: MidiAuditionOutput,
) : MidiAuditionPort {
    private data class ActiveSession(
        val id: Long,
        val plan: MidiAuditionPlaybackPlan,
        val output: MidiAuditionOutputSession,
    )

    private var current = MidiAuditionState()
    private val history = mutableListOf(current)
    private var selectedPlan: MidiAuditionPlaybackPlan? = null
    private var active: ActiveSession? = null
    private var nextSessionId = 0L

    override val state: MidiAuditionState get() = current
    override val stateHistory: List<MidiAuditionState> get() = history.toList()

    @Synchronized
    override fun selectScope(plan: MidiAuditionPlaybackPlan): MidiAuditionResult {
        if (current.isClosed) return reject(MidiAuditionProblemCode.CLOSED, "The MIDI audition controller is closed.", "Create a new audition controller.")
        val cleanupFailure = disposeActive()
        if (cleanupFailure != null) return failAfterCleanup(cleanupFailure)
        selectedPlan = plan
        record(selectedState(plan, plan.startTick, discoverOutputDevices()))
        return applied(MidiAuditionAction.SELECT_SCOPE)
    }

    @Synchronized
    override fun play(plan: MidiAuditionPlaybackPlan): MidiAuditionResult {
        if (current.isClosed) return reject(MidiAuditionProblemCode.CLOSED, "The MIDI audition controller is closed.", "Create a new audition controller.")
        val cleanupFailure = disposeActive()
        if (cleanupFailure != null) return failAfterCleanup(cleanupFailure)
        selectedPlan = plan
        val devices = discoverOutputDevices()
        record(selectedState(plan, plan.startTick, devices))
        val id = ++nextSessionId
        try {
            val opened = output.open(plan, MidiAuditionOutputListener { onPlaybackEnded(id) })
            active = ActiveSession(id, plan, opened)
            opened.play()
            record(current.copy(playback = MidiAuditionPlaybackState.PLAYING, sessionId = id, lastProblem = null))
            return applied(MidiAuditionAction.PLAY)
        } catch (error: Exception) {
            val problem = problemFrom(error)
            disposeActive()
            record(current.copy(playback = MidiAuditionPlaybackState.STOPPED, sessionId = null, positionTick = plan.startTick, lastProblem = problem))
            return MidiAuditionResult.Failed(problem, current)
        }
    }

    @Synchronized
    override fun play(): MidiAuditionResult {
        if (current.isClosed) return reject(MidiAuditionProblemCode.CLOSED, "The MIDI audition controller is closed.", "Create a new audition controller.")
        val session = active
        if (session == null) {
            val plan = selectedPlan ?: return reject(
                MidiAuditionProblemCode.NO_ACTIVE_SESSION,
                "No MIDI audition scope is selected.",
                "Select a source, candidate, occurrence, role, or accepted arrangement first.",
            )
            return play(plan)
        }
        if (current.playback == MidiAuditionPlaybackState.PLAYING) return applied(MidiAuditionAction.PLAY)
        return try {
            session.output.play()
            record(current.copy(playback = MidiAuditionPlaybackState.PLAYING, lastProblem = null))
            applied(MidiAuditionAction.PLAY)
        } catch (error: Exception) {
            failActive(error)
        }
    }

    @Synchronized
    override fun pause(): MidiAuditionResult {
        if (current.isClosed) return reject(MidiAuditionProblemCode.CLOSED, "The MIDI audition controller is closed.", "Create a new audition controller.")
        val session = active ?: return reject(
            MidiAuditionProblemCode.NO_ACTIVE_SESSION,
            "There is no active MIDI audition to pause.",
            "Select a view and start MIDI audition first.",
        )
        if (current.playback == MidiAuditionPlaybackState.PAUSED) return applied(MidiAuditionAction.PAUSE)
        return try {
            session.output.pause()
            val positionTick = transportPosition(session)
            selectedPlan = selectedPlan?.copy(startTick = positionTick)
            record(current.copy(playback = MidiAuditionPlaybackState.PAUSED, positionTick = positionTick, lastProblem = null))
            applied(MidiAuditionAction.PAUSE)
        } catch (error: Exception) {
            failActive(error)
        }
    }

    @Synchronized
    override fun stop(): MidiAuditionResult {
        if (current.isClosed) return reject(MidiAuditionProblemCode.CLOSED, "The MIDI audition controller is closed.", "Create a new audition controller.")
        val cleanupFailure = disposeActive()
        val plan = selectedPlan
        val resetTick = plan?.view?.window?.startTick ?: current.positionTick
        if (plan != null) selectedPlan = plan.copy(startTick = resetTick)
        val next = current.copy(
            playback = MidiAuditionPlaybackState.STOPPED,
            sessionId = null,
            positionTick = resetTick,
            lastProblem = cleanupFailure,
        )
        record(next)
        return cleanupFailure?.let { MidiAuditionResult.Failed(it, current) } ?: applied(MidiAuditionAction.STOP)
    }

    @Synchronized
    override fun seek(tick: Long): MidiAuditionResult {
        if (current.isClosed) return reject(MidiAuditionProblemCode.CLOSED, "The MIDI audition controller is closed.", "Create a new audition controller.")
        val plan = selectedPlan ?: return reject(
            MidiAuditionProblemCode.NO_ACTIVE_SESSION,
            "There is no selected MIDI audition scope to seek.",
            "Select a MIDI audition view first.",
        )
        val updated = try {
            plan.copy(startTick = tick)
        } catch (error: IllegalArgumentException) {
            return reject(MidiAuditionProblemCode.INVALID_REQUEST, error.message ?: "The seek position is outside the audition view.", "Seek inside the selected view boundaries.")
        }
        active?.let { session ->
            try {
                session.output.seek(tick)
            } catch (error: Exception) {
                return failActive(error)
            }
        }
        selectedPlan = updated
        record(current.copy(positionTick = tick, lastProblem = null))
        return applied(MidiAuditionAction.SEEK)
    }

    @Synchronized
    override fun setLoop(loop: MidiAuditionLoop?): MidiAuditionResult {
        if (current.isClosed) return reject(MidiAuditionProblemCode.CLOSED, "The MIDI audition controller is closed.", "Create a new audition controller.")
        val plan = selectedPlan ?: return reject(
            MidiAuditionProblemCode.NO_ACTIVE_SESSION,
            "There is no selected MIDI audition scope for looping.",
            "Select a MIDI audition view first.",
        )
        val updated = try {
            plan.copy(loop = loop)
        } catch (error: IllegalArgumentException) {
            return reject(MidiAuditionProblemCode.INVALID_REQUEST, error.message ?: "The loop is outside the audition view.", "Set a loop wholly inside the selected view boundaries.")
        }
        active?.let { session ->
            try {
                session.output.setLoop(loop)
            } catch (error: Exception) {
                return failActive(error)
            }
        }
        selectedPlan = updated
        record(current.copy(loop = loop, lastProblem = null))
        return applied(MidiAuditionAction.LOOP)
    }

    @Synchronized
    override fun setMutedRole(role: MidiExportRole, muted: Boolean): MidiAuditionResult {
        if (current.isClosed) return reject(MidiAuditionProblemCode.CLOSED, "The MIDI audition controller is closed.", "Create a new audition controller.")
        val plan = selectedPlan ?: return rejectNoScope("muting")
        if (role !in plan.view.roles) return rejectRole(role)
        val roles = if (muted) plan.mutedRoles + role else plan.mutedRoles - role
        val updated = plan.copy(mutedRoles = roles)
        active?.let { session ->
            try {
                session.output.setMutedRoles(roles)
            } catch (error: Exception) {
                return failActive(error)
            }
        }
        selectedPlan = updated
        record(current.copy(mutedRoles = roles, lastProblem = null))
        return applied(MidiAuditionAction.MUTE)
    }

    @Synchronized
    override fun setSoloRole(role: MidiExportRole, solo: Boolean): MidiAuditionResult {
        if (current.isClosed) return reject(MidiAuditionProblemCode.CLOSED, "The MIDI audition controller is closed.", "Create a new audition controller.")
        val plan = selectedPlan ?: return rejectNoScope("soloing")
        if (role !in plan.view.roles) return rejectRole(role)
        val roles = if (solo) plan.soloRoles + role else plan.soloRoles - role
        val updated = plan.copy(soloRoles = roles)
        active?.let { session ->
            try {
                session.output.setSoloRoles(roles)
            } catch (error: Exception) {
                return failActive(error)
            }
        }
        selectedPlan = updated
        record(current.copy(soloRoles = roles, lastProblem = null))
        return applied(MidiAuditionAction.SOLO)
    }

    @Synchronized
    override fun selectOutputDevice(outputDeviceId: String?): MidiAuditionResult {
        if (current.isClosed) return reject(MidiAuditionProblemCode.CLOSED, "The MIDI audition controller is closed.", "Create a new audition controller.")
        val plan = selectedPlan ?: return rejectNoScope("selecting an output")
        val devices = discoverOutputDevices()
        if (outputDeviceId != null && devices.none { it.id == outputDeviceId }) {
            return reject(
                MidiAuditionProblemCode.INVALID_REQUEST,
                "The selected MIDI output is no longer available.",
                "Choose the built-in synthesizer or reconnect the device, then retry.",
            )
        }
        val positionTick = active?.let { session ->
            try {
                transportPosition(session)
            } catch (error: Exception) {
                return failActive(error)
            }
        } ?: current.positionTick
        val updated = plan.copy(startTick = positionTick, outputDeviceId = outputDeviceId)
        val previousPlayback = current.playback
        val cleanupFailure = disposeActive()
        if (cleanupFailure != null) return failAfterCleanup(cleanupFailure)
        selectedPlan = updated
        if (previousPlayback == MidiAuditionPlaybackState.STOPPED) {
            record(selectedState(updated, updated.startTick, devices))
            return applied(MidiAuditionAction.SELECT_OUTPUT)
        }
        val id = ++nextSessionId
        return try {
            val opened = output.open(updated, MidiAuditionOutputListener { onPlaybackEnded(id) })
            active = ActiveSession(id, updated, opened)
            if (previousPlayback == MidiAuditionPlaybackState.PLAYING) opened.play()
            record(selectedState(updated, updated.startTick, devices).copy(playback = previousPlayback, sessionId = id))
            applied(MidiAuditionAction.SELECT_OUTPUT)
        } catch (error: Exception) {
            val problem = problemFrom(error)
            disposeActive()
            record(selectedState(updated, updated.startTick, devices).copy(lastProblem = problem))
            MidiAuditionResult.Failed(problem, current)
        }
    }

    @Synchronized
    override fun close() {
        if (current.isClosed) return
        val cleanupFailure = disposeActive()
        val outputFailure = try {
            output.close()
            null
        } catch (error: Exception) {
            problemFrom(error)
        }
        record(current.copy(
            isClosed = true,
            playback = MidiAuditionPlaybackState.STOPPED,
            sessionId = null,
            lastProblem = cleanupFailure ?: outputFailure ?: current.lastProblem,
        ))
    }

    @Synchronized
    private fun onPlaybackEnded(id: Long) {
        val session = active ?: return
        if (session.id != id) return
        active = null
        val closeFailure = try {
            session.output.close()
            null
        } catch (error: Exception) {
            problemFrom(error)
        }
        val completedPlan = selectedPlan ?: session.plan
        selectedPlan = completedPlan.copy(startTick = completedPlan.view.window.endTick)
        record(current.copy(
            playback = MidiAuditionPlaybackState.STOPPED,
            sessionId = null,
            positionTick = completedPlan.view.window.endTick,
            lastProblem = closeFailure,
        ))
    }

    private fun failActive(error: Exception): MidiAuditionResult.Failed {
        val problem = problemFrom(error)
        disposeActive()
        record(current.copy(playback = MidiAuditionPlaybackState.STOPPED, sessionId = null, lastProblem = problem))
        return MidiAuditionResult.Failed(problem, current)
    }

    private fun disposeActive(): MidiAuditionProblem? {
        val session = active ?: return null
        active = null
        var failure: MidiAuditionProblem? = null
        try {
            session.output.stop()
        } catch (error: Exception) {
            failure = problemFrom(error)
        } finally {
            try {
                session.output.close()
            } catch (error: Exception) {
                if (failure == null) failure = problemFrom(error)
            }
        }
        return failure
    }

    private fun transportPosition(session: ActiveSession): Long = session.output.positionTick()
        ?.coerceIn(session.plan.view.window.startTick, session.plan.view.window.endTick)
        ?: current.positionTick

    private fun selectedState(
        plan: MidiAuditionPlaybackPlan,
        positionTick: Long,
        outputDevices: List<MidiAuditionOutputDevice>,
    ): MidiAuditionState = current.copy(
        playback = MidiAuditionPlaybackState.STOPPED,
        sessionId = null,
        scope = plan.view.scope,
        window = plan.view.window,
        positionTick = positionTick,
        loop = plan.loop,
        mutedRoles = plan.mutedRoles,
        soloRoles = plan.soloRoles,
        outputDeviceId = plan.outputDeviceId,
        outputDevices = outputDevices,
        lastProblem = null,
    )

    private fun discoverOutputDevices(): List<MidiAuditionOutputDevice> = try {
        output.availableDevices()
            .distinctBy(MidiAuditionOutputDevice::id)
            .sortedWith(compareBy(MidiAuditionOutputDevice::name, MidiAuditionOutputDevice::vendor, MidiAuditionOutputDevice::id))
    } catch (_: Exception) {
        // A dedicated device list is optional. The default JVM receiver remains a valid fallback.
        emptyList()
    }

    private fun rejectNoScope(action: String): MidiAuditionResult = reject(
        MidiAuditionProblemCode.NO_ACTIVE_SESSION,
        "There is no selected MIDI audition scope for $action.",
        "Select a MIDI audition view first.",
    )

    private fun rejectRole(role: MidiExportRole): MidiAuditionResult = reject(
        MidiAuditionProblemCode.INVALID_REQUEST,
        "Role ${role.trackName} is not present in the selected audition view.",
        "Select a view containing that role before changing its audition state.",
    )

    private fun reject(code: MidiAuditionProblemCode, message: String, nextAction: String): MidiAuditionResult.Failed {
        val problem = MidiAuditionProblem(code, message, nextAction)
        record(current.copy(lastProblem = problem))
        return MidiAuditionResult.Failed(problem, current)
    }

    private fun failAfterCleanup(problem: MidiAuditionProblem): MidiAuditionResult.Failed {
        record(current.copy(playback = MidiAuditionPlaybackState.STOPPED, sessionId = null, lastProblem = problem))
        return MidiAuditionResult.Failed(problem, current)
    }

    private fun applied(action: MidiAuditionAction): MidiAuditionResult.Applied = MidiAuditionResult.Applied(action, current)

    private fun record(next: MidiAuditionState) {
        current = next
        history += next
    }

    private fun problemFrom(error: Throwable): MidiAuditionProblem {
        val code = (error as? MidiAuditionOutputException)?.code ?: MidiAuditionProblemCode.OUTPUT_FAILURE
        val nextAction = when (code) {
            MidiAuditionProblemCode.DEVICE_UNAVAILABLE,
            MidiAuditionProblemCode.DEVICE_LOST,
            -> "Reconnect the MIDI output or select another available device, then retry audition."
            MidiAuditionProblemCode.CLOSED -> "Create a new audition controller."
            else -> "Retry MIDI audition; project and acceptance state were not changed."
        }
        return MidiAuditionProblem(code, error.message ?: "MIDI audition output failed.", nextAction)
    }
}
