package app.melotrail.application

import app.melotrail.audition.MidiAuditionLoop
import app.melotrail.audition.MidiAuditionPlaybackPlan
import app.melotrail.audition.MidiAuditionView
import app.melotrail.arrangement.core.MidiCoreAcceptedDependencyContext
import app.melotrail.arrangement.core.MidiCoreArrangementStyle
import app.melotrail.arrangement.core.MidiCoreArrangementStyleCatalog
import app.melotrail.arrangement.core.MidiCoreBassGenerator
import app.melotrail.arrangement.core.MidiCoreCandidateEvent
import app.melotrail.arrangement.core.MidiCoreChordGenerator
import app.melotrail.arrangement.core.MidiCoreDrumGenerator
import app.melotrail.arrangement.core.MidiCoreGenerationContext
import app.melotrail.arrangement.core.MidiCoreGenerationNote
import app.melotrail.arrangement.core.MidiCorePerformanceProfileCatalog
import app.melotrail.arrangement.core.MidiCoreRoleCandidate
import app.melotrail.arrangement.core.MidiCoreRoleValidationReport
import app.melotrail.arrangement.core.MidiCoreRoleValidationResult
import app.melotrail.midi.adapter.JdkMidiReader
import app.melotrail.midi.domain.MidiEventOrderingKey
import app.melotrail.midi.domain.MidiExportMarker
import app.melotrail.midi.domain.MidiExportRole
import app.melotrail.midi.domain.MidiExportRoleTrack
import app.melotrail.midi.domain.MidiExportSong
import app.melotrail.midi.domain.MidiMelodySelection
import app.melotrail.midi.domain.MidiNoteEvent
import app.melotrail.midi.domain.MidiProtectedMelodySelector
import app.melotrail.midi.domain.MidiProtectedMelodyView
import app.melotrail.midi.domain.MidiSemanticEventKind
import app.melotrail.project.CandidateRole
import app.melotrail.project.MidiCoreAcceptedDependency
import app.melotrail.project.MidiCoreGeneratorInput
import app.melotrail.project.adapter.MidiCoreArtifactStore
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/** A cache key contains every authority fact that can change an ephemeral style preview. */
data class MidiCoreArrangementStylePreviewKey(
    val authorityHash: String,
    val styleId: String,
    val occurrenceId: String,
    val seed: Long,
) {
    init {
        require(authorityHash.matches(HASH)) { "Preview authority hash must be a SHA-256 value" }
        require(styleId.isNotBlank() && occurrenceId.isNotBlank()) { "Preview style and occurrence must not be blank" }
    }

    private companion object {
        val HASH = Regex("[0-9a-f]{64}")
    }
}

/** The sole input for a non-persistent arrangement-style preview. */
data class PrepareMidiCoreArrangementStylePreview(
    val session: MidiCoreProjectSession,
    val styleId: String,
    val occurrenceId: String,
    val seed: Long = DEFAULT_PREVIEW_SEED,
) {
    init {
        require(styleId.isNotBlank() && occurrenceId.isNotBlank()) { "Preview style and occurrence must not be blank" }
    }

    companion object {
        const val DEFAULT_PREVIEW_SEED = 1L
    }
}

/** Cache evidence is explicit so callers and tests can distinguish cold and warm preview paths. */
enum class MidiCoreArrangementStylePreviewCacheStatus { COLD, WARM }

/** Preview failures are recoverable and deliberately contain no candidate or artifact reference. */
data class MidiCoreArrangementStylePreviewProblem(
    val code: MidiCoreArrangementStylePreviewProblemCode,
    val message: String,
    val nextAction: String,
)

enum class MidiCoreArrangementStylePreviewProblemCode {
    INVALID_PROJECT,
    STALE_PROJECT,
    AUTHORITY_REQUIRED,
    STYLE_NOT_FOUND,
    OCCURRENCE_NOT_FOUND,
    WINDOW_TOO_SHORT,
    SOURCE_NOT_PLAYABLE,
    VALIDATION_REJECTED,
}

/** A complete in-memory preview or its typed rejection. No case mutates project state. */
sealed interface MidiCoreArrangementStylePreviewResult {
    data class Ready(
        val key: MidiCoreArrangementStylePreviewKey,
        val plan: MidiAuditionPlaybackPlan,
        val validation: List<MidiCoreRoleValidationReport>,
        val cacheStatus: MidiCoreArrangementStylePreviewCacheStatus,
    ) : MidiCoreArrangementStylePreviewResult

    data class Rejected(val problem: MidiCoreArrangementStylePreviewProblem) : MidiCoreArrangementStylePreviewResult
}

/** Small in-memory cache metrics kept only for diagnostics and regression tests. */
data class MidiCoreArrangementStylePreviewCacheStats(val hits: Int, val misses: Int, val entries: Int)

/**
 * Creates an exact two-to-four-bar MIDI preview for one style and occurrence.
 * It reads immutable source/project evidence, calls only the pure role engines,
 * and keeps the result in memory for the persistent audition player. It never
 * creates candidates, artifacts, acceptance records, revisions, or audio.
 */
class MidiCoreArrangementStylePreview(
    private val artifacts: MidiCoreArtifactStore = MidiCoreArtifactStore(),
    private val reader: JdkMidiReader = JdkMidiReader(),
    private val melodySelector: MidiProtectedMelodySelector = MidiProtectedMelodySelector(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val cacheCapacity: Int = DEFAULT_CACHE_CAPACITY,
) {
    private val cache = object : LinkedHashMap<MidiCoreArrangementStylePreviewKey, CachedPreview>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<MidiCoreArrangementStylePreviewKey, CachedPreview>?): Boolean = size > cacheCapacity
    }
    private var hits = 0
    private var misses = 0

    init {
        require(cacheCapacity in 1..128) { "Preview cache capacity must be bounded" }
    }

    /** Render once or return a warm immutable plan; all work stays off the caller's UI dispatcher. */
    suspend fun prepare(request: PrepareMidiCoreArrangementStylePreview): MidiCoreArrangementStylePreviewResult =
        withContext(dispatcher) { prepareOnWorker(request) }

    /** Testable diagnostics; cache contents remain private and non-persistent. */
    @Synchronized
    fun cacheStats(): MidiCoreArrangementStylePreviewCacheStats = MidiCoreArrangementStylePreviewCacheStats(hits, misses, cache.size)

    private suspend fun prepareOnWorker(request: PrepareMidiCoreArrangementStylePreview): MidiCoreArrangementStylePreviewResult {
        coroutineContext.ensureActive()
        val loaded = when (val result = load(request)) {
            is PreviewLoad.Ready -> result.preview
            is PreviewLoad.Rejected -> return result.result
        }
        val key = MidiCoreArrangementStylePreviewKey(
            loaded.authority.authorityHash,
            loaded.style.id,
            loaded.occurrenceId,
            request.seed,
        )
        synchronized(this) {
            cache[key]?.let { cached ->
                hits += 1
                return MidiCoreArrangementStylePreviewResult.Ready(key, cached.plan, cached.validation, MidiCoreArrangementStylePreviewCacheStatus.WARM)
            }
            misses += 1
        }
        coroutineContext.ensureActive()

        val preview = render(loaded, key)
        if (preview is MidiCoreArrangementStylePreviewResult.Rejected) return preview
        val ready = preview as MidiCoreArrangementStylePreviewResult.Ready
        synchronized(this) { cache[key] = CachedPreview(ready.plan, ready.validation) }
        return ready
    }

    private fun load(request: PrepareMidiCoreArrangementStylePreview): PreviewLoad {
        val root = request.session.root.toAbsolutePath().normalize()
        val project = try {
            artifacts.openProject(root)
        } catch (_: Exception) {
            return PreviewLoad.Rejected(rejected(
                MidiCoreArrangementStylePreviewProblemCode.INVALID_PROJECT,
                "The project cannot be verified before a style preview.",
                "Reopen a valid MIDI Core project and choose a style again.",
            ))
        }
        if (project != request.session.project) {
            return PreviewLoad.Rejected(rejected(
                MidiCoreArrangementStylePreviewProblemCode.STALE_PROJECT,
                "The project changed while the style preview was being prepared.",
                "Reload the project and choose the style again.",
            ))
        }
        val source = project.sourceMidi
        val selectedMelody = project.selectedMelody
        if (source == null || selectedMelody == null || project.authority == null) {
            return PreviewLoad.Rejected(rejected(
                MidiCoreArrangementStylePreviewProblemCode.AUTHORITY_REQUIRED,
                "A protected source melody and confirmed musical authority are required before previewing a style.",
                "Import MIDI and complete Structure & Harmony first.",
            ))
        }
        val style = try {
            MidiCoreArrangementStyleCatalog.require(request.styleId)
        } catch (_: IllegalArgumentException) {
            return PreviewLoad.Rejected(rejected(
                MidiCoreArrangementStylePreviewProblemCode.STYLE_NOT_FOUND,
                "The selected arrangement style is no longer available.",
                "Choose one of the displayed styles and try again.",
            ))
        }
        val authority = try {
            app.melotrail.arrangement.core.MidiCoreAuthoritySnapshot.from(project)
        } catch (error: IllegalArgumentException) {
            return PreviewLoad.Rejected(rejected(
                MidiCoreArrangementStylePreviewProblemCode.AUTHORITY_REQUIRED,
                error.message ?: "Musical authority is incomplete.",
                "Complete Structure & Harmony before previewing a style.",
            ))
        }
        val occurrence = authority.occurrences.singleOrNull { it.id == request.occurrenceId }
        if (occurrence == null) {
            return PreviewLoad.Rejected(rejected(
                MidiCoreArrangementStylePreviewProblemCode.OCCURRENCE_NOT_FOUND,
                "Choose a saved section occurrence before previewing a style.",
                "Select a section from the Arrange page and try again.",
            ))
        }
        val ticksPerBar = app.melotrail.arrangement.core.MidiCoreTickGrid(authority.ppq, authority.meter).ticksPerBar
        val previewEnd = minOf(occurrence.endTick, occurrence.startTick + PREVIEW_BARS * ticksPerBar)
        if (previewEnd - occurrence.startTick < MINIMUM_PREVIEW_BARS * ticksPerBar) {
            return PreviewLoad.Rejected(rejected(
                MidiCoreArrangementStylePreviewProblemCode.WINDOW_TOO_SHORT,
                "The selected section needs at least two complete bars for a style preview.",
                "Choose a section with two or more bars, or extend this section in Structure & Harmony.",
            ))
        }
        val melody = try {
            val inspected = reader.inspect(artifacts.verify(root, source.original))
            require(inspected.sequence.source.sha256 == source.sha256 && inspected.sequence.source.ppq.value == source.ppq) {
                "The preserved source MIDI no longer matches project identity"
            }
            melodySelector.select(inspected.sequence, MidiMelodySelection(selectedMelody.trackIndex, selectedMelody.channel)).also {
                require(it.identitySha256 == selectedMelody.identitySha256 && it.sourceSha256 == source.sha256) {
                    "The protected melody no longer matches project identity"
                }
            }
        } catch (_: Exception) {
            return PreviewLoad.Rejected(rejected(
                MidiCoreArrangementStylePreviewProblemCode.SOURCE_NOT_PLAYABLE,
                "The protected source melody cannot be re-derived for this preview.",
                "Restore the immutable source MIDI or import it into a new project.",
            ))
        }
        return PreviewLoad.Ready(LoadedPreview(project, authority, style, request.occurrenceId, occurrence.startTick, previewEnd, melody))
    }

    private suspend fun render(
        loaded: LoadedPreview,
        key: MidiCoreArrangementStylePreviewKey,
    ): MidiCoreArrangementStylePreviewResult {
        val roles = mutableListOf<Pair<MidiCoreRoleCandidate, MidiCoreRoleValidationReport>>()
        val dependencyNotes = mutableListOf<MidiCoreAcceptedDependencyContext>()
        CandidateRole.entries.forEach { role ->
            coroutineContext.ensureActive()
            val choice = loaded.style.role(role)
            val context = try {
                MidiCoreGenerationContext.from(
                    project = loaded.project,
                    role = role,
                    occurrenceId = loaded.occurrenceId,
                    performanceProfile = MidiCorePerformanceProfileCatalog.requireForRole(role, choice.performanceProfileId),
                    patternId = choice.patternId,
                    generator = MidiCoreGeneratorInput(
                        PREVIEW_GENERATOR_ID,
                        "style-catalog-v${MidiCoreArrangementStyleCatalog.VERSION}",
                        choice.patternId,
                        derivedSeed(key, role),
                    ),
                    protectedMelody = loaded.melody,
                    acceptedDependencies = dependencyNotes.toList(),
                    sectionPolicy = choice.sectionPolicy,
                )
            } catch (error: IllegalArgumentException) {
                return rejected(
                    MidiCoreArrangementStylePreviewProblemCode.VALIDATION_REJECTED,
                    error.message ?: "The selected style cannot be generated safely.",
                    "Choose another style or adjust the section authority.",
                )
            }
            val generated = when (role) {
                CandidateRole.CHORDS -> MidiCoreChordGenerator.generate(context).let { it.candidate to it.validation }
                CandidateRole.BASS -> MidiCoreBassGenerator.generate(context).let { it.candidate to it.validation }
                CandidateRole.DRUMS -> MidiCoreDrumGenerator.generate(context).let { it.candidate to it.validation }
            }
            val validation = generated.second
            if (validation is MidiCoreRoleValidationResult.Rejected) {
                return rejected(
                    MidiCoreArrangementStylePreviewProblemCode.VALIDATION_REJECTED,
                    "${role.name.lowercase().replaceFirstChar(Char::uppercaseChar)} preview notes do not satisfy the current protected melody and harmony.",
                    "Choose another style or adjust the authoritative harmony before previewing again.",
                )
            }
            val fullOccurrenceCandidate = generated.first
            val candidate = fullOccurrenceCandidate.copy(
                events = fullOccurrenceCandidate.events.filterIsInstance<MidiCoreCandidateEvent.Note>().filter { note ->
                    note.startTick >= loaded.startTick && note.endTick <= loaded.endTick
                },
            )
            // The complete occurrence is validated before this exact loop projection.
            // Filtering cannot introduce an out-of-range, harmonic, or collision event.
            require(candidate.events.filterIsInstance<MidiCoreCandidateEvent.Note>().all { note ->
                note.startTick >= loaded.startTick && note.endTick <= loaded.endTick
            }) { "Preview generator emitted notes outside the bounded preview window" }
            roles += candidate to validation.report
            dependencyNotes += MidiCoreAcceptedDependencyContext(
                MidiCoreAcceptedDependency(role, loaded.occurrenceId, "preview-${loaded.style.id}-${role.name.lowercase()}", context.authorityHash),
                fullOccurrenceCandidate.events.filterIsInstance<MidiCoreCandidateEvent.Note>().map { note ->
                    MidiCoreGenerationNote(note.startTick, note.endTick, note.pitch, note.velocity)
                },
            )
        }
        coroutineContext.ensureActive()
        val song = try {
            previewSong(loaded, roles.map(Pair<MidiCoreRoleCandidate, MidiCoreRoleValidationReport>::first))
        } catch (error: IllegalArgumentException) {
            return rejected(
                MidiCoreArrangementStylePreviewProblemCode.VALIDATION_REJECTED,
                error.message ?: "The generated style preview is invalid.",
                "Choose another style or review Structure & Harmony.",
            )
        }
        val plan = MidiAuditionPlaybackPlan(
            view = MidiAuditionView.stylePreview(loaded.style.id, loaded.occurrenceId, song, loaded.startTick, loaded.endTick),
            loop = MidiAuditionLoop(loaded.startTick, loaded.endTick),
        )
        return MidiCoreArrangementStylePreviewResult.Ready(
            key,
            plan,
            roles.map(Pair<MidiCoreRoleCandidate, MidiCoreRoleValidationReport>::second),
            MidiCoreArrangementStylePreviewCacheStatus.COLD,
        )
    }

    private fun previewSong(loaded: LoadedPreview, candidates: List<MidiCoreRoleCandidate>): MidiExportSong {
        val melodyEvents = loaded.melody.events.filter { event ->
            event.orderingKey.tick >= loaded.startTick && when (event) {
                is MidiNoteEvent -> event.endTick <= loaded.endTick
                else -> event.orderingKey.tick < loaded.endTick
            }
        }
        val generatedTracks = candidates.map { candidate ->
            val role = when (candidate.role) {
                CandidateRole.CHORDS -> MidiExportRole.CHORDS
                CandidateRole.BASS -> MidiExportRole.BASS
                CandidateRole.DRUMS -> MidiExportRole.DRUMS
            }
            MidiExportRoleTrack(role, candidate.events.filterIsInstance<MidiCoreCandidateEvent.Note>().mapIndexed { index, note ->
                MidiNoteEvent(
                    MidiEventOrderingKey(note.startTick, MidiSemanticEventKind.NOTE, generatedEventKey = index.toLong()),
                    note.endTick,
                    candidate.channel,
                    note.pitch,
                    note.velocity,
                )
            }.sortedBy(MidiNoteEvent::orderingKey))
        }
        return MidiExportSong(
            ppq = loaded.authority.ppq,
            sequenceName = "Melotrail ${loaded.style.displayName} preview",
            tempoMicrosecondsPerQuarter = loaded.authority.tempo.microsecondsPerQuarter,
            meterNumerator = loaded.authority.meter.numerator,
            meterDenominatorExponent = loaded.authority.meter.denominatorExponent,
            markers = listOf(MidiExportMarker(1, loaded.style.displayName, loaded.startTick)),
            roles = (listOf(MidiExportRoleTrack(MidiExportRole.MELODY, melodyEvents)) + generatedTracks).sortedBy(MidiExportRoleTrack::role),
            songEndTick = loaded.endTick,
        )
    }

    private fun derivedSeed(key: MidiCoreArrangementStylePreviewKey, role: CandidateRole): Long {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest("${key.authorityHash}|${key.styleId}|${key.occurrenceId}|${key.seed}|${role.name}".toByteArray())
        return bytes.take(8).fold(0L) { value, byte -> (value shl 8) or (byte.toLong() and 0xffL) } and Long.MAX_VALUE
    }

    private fun rejected(
        code: MidiCoreArrangementStylePreviewProblemCode,
        message: String,
        nextAction: String,
    ) = MidiCoreArrangementStylePreviewResult.Rejected(MidiCoreArrangementStylePreviewProblem(code, message, nextAction))

    private data class CachedPreview(
        val plan: MidiAuditionPlaybackPlan,
        val validation: List<MidiCoreRoleValidationReport>,
    )

    private data class LoadedPreview(
        val project: app.melotrail.project.MidiCoreProject,
        val authority: app.melotrail.arrangement.core.MidiCoreAuthoritySnapshot,
        val style: MidiCoreArrangementStyle,
        val occurrenceId: String,
        val startTick: Long,
        val endTick: Long,
        val melody: MidiProtectedMelodyView,
    )

    private sealed interface PreviewLoad {
        data class Ready(val preview: LoadedPreview) : PreviewLoad
        data class Rejected(val result: MidiCoreArrangementStylePreviewResult.Rejected) : PreviewLoad
    }

    private companion object {
        const val PREVIEW_GENERATOR_ID = "midi-core-style-preview"
        const val PREVIEW_BARS = 4L
        const val MINIMUM_PREVIEW_BARS = 2L
        const val DEFAULT_CACHE_CAPACITY = 24
    }
}
