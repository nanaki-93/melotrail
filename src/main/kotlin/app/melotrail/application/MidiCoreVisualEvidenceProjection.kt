package app.melotrail.application

import app.melotrail.audition.MidiAuditionPlaybackPlan
import app.melotrail.midi.domain.MidiExportRole
import app.melotrail.midi.domain.MidiNoteEvent
import app.melotrail.project.CandidateRole
import app.melotrail.project.MidiCoreAuthorityHasher
import app.melotrail.project.MidiCoreCandidateStatus

/** The four stopped-state MIDI evidence scopes that the desktop can render. */
enum class MidiCoreVisualEvidenceScope {
    PROTECTED_SOURCE,
    SELECTED_CANDIDATE,
    DRAFT,
    ACCEPTED,
}

/** Whether a verified visual evidence result was constructed now or reused from its immutable cache entry. */
enum class MidiCoreVisualEvidenceCacheStatus { COLD, WARM }

/** Whether verified evidence still agrees with the currently authoritative musical context. */
enum class MidiCoreVisualEvidenceCurrentness { CURRENT, STALE }

/** Exact musical timing carried with every available stopped-state visual evidence scope. */
data class MidiCoreVisualEvidenceTiming(
    val ppq: Int,
    val songEndTick: Long,
    val tempoMicrosecondsPerQuarter: Int,
    val meterNumerator: Int,
    val meterDenominatorExponent: Int,
    val authoritative: Boolean,
) {
    init {
        require(ppq > 0 && songEndTick > 0L) { "Visual evidence timing must have a positive PPQ and song end" }
        require(tempoMicrosecondsPerQuarter > 0) { "Visual evidence tempo must be positive" }
        require(meterNumerator > 0 && meterDenominatorExponent >= 0) { "Visual evidence meter is invalid" }
    }
}

/** One immutable note or percussion hit that is safe for a stopped-state MIDI lane to display. */
data class MidiCoreVisualEvidenceEvent(
    val startTick: Long,
    val endTick: Long,
    val channel: Int,
    val pitch: Int,
    val velocity: Int,
    val percussion: Boolean,
) {
    init {
        require(startTick >= 0L && endTick > startTick) { "Visual evidence event timing is invalid" }
        require(channel in 0..15 && pitch in 0..127 && velocity in 0..127) { "Visual evidence event data is invalid" }
    }
}

/** One exact role lane, including intentional silence as an empty event list. */
data class MidiCoreVisualEvidenceLane(
    val role: MidiExportRole,
    val events: List<MidiCoreVisualEvidenceEvent>,
) {
    init {
        require(events == events.sortedWith(EVENT_ORDER)) { "Visual evidence events must use deterministic timing order" }
    }

    private companion object {
        val EVENT_ORDER = compareBy<MidiCoreVisualEvidenceEvent> { it.startTick }
            .thenBy { it.endTick }
            .thenBy { it.channel }
            .thenBy { it.pitch }
            .thenBy { it.velocity }
    }
}

/** Immutable source, authority, candidate, and draft identity bound to one visual evidence result. */
data class MidiCoreVisualEvidenceIdentity(
    val projectId: String,
    val sourceSha256: String,
    val authorityHash: String?,
    val candidateIds: List<String> = emptyList(),
    val draftId: String? = null,
) {
    init {
        require(projectId.isNotBlank() && sourceSha256.isNotBlank()) { "Visual evidence identity is incomplete" }
        require(candidateIds == candidateIds.distinct()) { "Visual evidence candidate IDs must be unique" }
    }
}

/** A verified renderable stopped-state lane group; it never owns playback, storage, or project mutation. */
data class MidiCoreVisualEvidenceAvailable(
    val scope: MidiCoreVisualEvidenceScope,
    val identity: MidiCoreVisualEvidenceIdentity,
    val timing: MidiCoreVisualEvidenceTiming,
    val lanes: List<MidiCoreVisualEvidenceLane>,
    val currentness: MidiCoreVisualEvidenceCurrentness,
    val cacheStatus: MidiCoreVisualEvidenceCacheStatus,
) {
    init {
        require(lanes.map(MidiCoreVisualEvidenceLane::role).distinct().size == lanes.size) {
            "Visual evidence lanes must have unique roles"
        }
        require(lanes == lanes.sortedBy { it.role.ordinal }) { "Visual evidence lanes must use role order" }
    }
}

/** A visible reason why a scope must not be drawn as verified/current musical evidence. */
data class MidiCoreVisualEvidenceUnavailable(
    val scope: MidiCoreVisualEvidenceScope,
    val code: String,
    val message: String,
    val nextAction: String,
) {
    init {
        require(code.isNotBlank() && message.isNotBlank() && nextAction.isNotBlank()) { "Unavailable visual evidence must explain recovery" }
    }
}

/** One supported stopped-state visual evidence scope, either renderable facts or an explicit unavailable state. */
sealed interface MidiCoreVisualEvidence {
    val scope: MidiCoreVisualEvidenceScope

    data class Available(val value: MidiCoreVisualEvidenceAvailable) : MidiCoreVisualEvidence {
        override val scope: MidiCoreVisualEvidenceScope get() = value.scope
    }

    data class Unavailable(val value: MidiCoreVisualEvidenceUnavailable) : MidiCoreVisualEvidence {
        override val scope: MidiCoreVisualEvidenceScope get() = value.scope
    }
}

/** Complete non-playing evidence supplied to future lane renderers for the currently open project. */
data class MidiCoreVisualEvidenceProjection(
    val source: MidiCoreVisualEvidence,
    val selectedCandidate: MidiCoreVisualEvidence,
    val draft: MidiCoreVisualEvidence,
    val accepted: MidiCoreVisualEvidence,
) {
    init {
        require(source.scope == MidiCoreVisualEvidenceScope.PROTECTED_SOURCE) { "Source evidence scope is invalid" }
        require(selectedCandidate.scope == MidiCoreVisualEvidenceScope.SELECTED_CANDIDATE) { "Candidate evidence scope is invalid" }
        require(draft.scope == MidiCoreVisualEvidenceScope.DRAFT) { "Draft evidence scope is invalid" }
        require(accepted.scope == MidiCoreVisualEvidenceScope.ACCEPTED) { "Accepted evidence scope is invalid" }
    }
}

/** A read-only request that preserves the active project session and optional UI selections at dispatch time. */
data class ProjectMidiCoreVisualEvidence(
    val session: MidiCoreProjectSession,
    val selectedCandidateId: String? = null,
    val draftId: String? = session.project.arrangementDrafts.lastOrNull()?.id,
)

/**
 * Builds stopped-state note-lane facts exclusively from the already audited source,
 * candidate, draft, and accepted-arrangement preparation paths. The small cache stores
 * immutable mapped lanes only after the corresponding preparer has revalidated the
 * project and artifact digests for this request.
 */
class MidiCoreVisualEvidenceProvider(
    private val sourceAudition: MidiCoreSourceAudition = MidiCoreSourceAudition(),
    private val reviewAudition: MidiCoreReviewAudition = MidiCoreReviewAudition(),
) {
    /** Revalidate the requested project and return truthful stopped-state data for all four supported scopes. */
    fun project(request: ProjectMidiCoreVisualEvidence): MidiCoreVisualEvidenceProjection {
        val project = request.session.project
        val authorityHash = runCatching { MidiCoreAuthorityHasher.from(project).sha256 }.getOrNull()
        return MidiCoreVisualEvidenceProjection(
            source = source(request, authorityHash),
            selectedCandidate = selectedCandidate(request, authorityHash),
            draft = draft(request, authorityHash),
            accepted = accepted(request, authorityHash),
        )
    }

    private fun source(request: ProjectMidiCoreVisualEvidence, authorityHash: String?): MidiCoreVisualEvidence = when (
        val prepared = sourceAudition.prepare(PrepareMidiCoreSourceAudition(request.session))
    ) {
        is MidiCoreSourceAuditionResult.Ready -> available(
            MidiCoreVisualEvidenceScope.PROTECTED_SOURCE,
            CacheKey(
                MidiCoreVisualEvidenceScope.PROTECTED_SOURCE,
                request.session.project.id.value,
                requireNotNull(request.session.project.sourceMidi).sha256,
                authorityHash,
                listOf(requireNotNull(request.session.project.selectedMelody).identitySha256),
            ),
            identity(request, authorityHash),
            prepared.plan,
            MidiCoreVisualEvidenceCurrentness.CURRENT,
        )
        is MidiCoreSourceAuditionResult.Rejected -> unavailable(
            MidiCoreVisualEvidenceScope.PROTECTED_SOURCE,
            prepared.problem.code.name,
            prepared.problem.message,
            prepared.problem.nextAction,
        )
    }

    private fun selectedCandidate(request: ProjectMidiCoreVisualEvidence, authorityHash: String?): MidiCoreVisualEvidence {
        val candidateId = request.selectedCandidateId ?: return unavailable(
            MidiCoreVisualEvidenceScope.SELECTED_CANDIDATE,
            "NO_CANDIDATE_SELECTION",
            "No candidate is selected for stopped-state lane display.",
            "Choose an inspectable candidate in Review before displaying its role lane.",
        )
        val candidate = request.session.project.candidates.singleOrNull { it.id == candidateId } ?: return unavailable(
            MidiCoreVisualEvidenceScope.SELECTED_CANDIDATE,
            "CANDIDATE_NOT_FOUND",
            "The selected candidate is not part of the current project.",
            "Refresh Review and select an available candidate.",
        )
        val prepared = reviewAudition.candidate(
            PrepareMidiCoreCandidateAudition(
                request.session,
                candidate.id,
                candidate.role,
                candidate.occurrenceId,
                request.session.project.revision,
            ),
        )
        return when (prepared) {
            is MidiCoreReviewAuditionResult.Ready -> {
                val authorityCurrent = runCatching {
                    candidate.authorityHash == MidiCoreAuthorityHasher.from(request.session.project)
                        .scopeHash(candidate.occurrenceId, candidate.role)
                }.getOrDefault(false)
                available(
                    MidiCoreVisualEvidenceScope.SELECTED_CANDIDATE,
                    CacheKey(
                        MidiCoreVisualEvidenceScope.SELECTED_CANDIDATE,
                        request.session.project.id.value,
                        requireNotNull(request.session.project.sourceMidi).sha256,
                        authorityHash,
                        listOf(candidate.id, candidate.midi.sha256, candidate.validationReport.sha256, candidate.authorityHash, candidate.status.name),
                    ),
                    identity(request, authorityHash, candidateIds = listOf(candidate.id)),
                    prepared.plan,
                    if (authorityCurrent && candidate.status in CURRENT_CANDIDATE_STATUSES) {
                        MidiCoreVisualEvidenceCurrentness.CURRENT
                    } else {
                        MidiCoreVisualEvidenceCurrentness.STALE
                    },
                )
            }
            is MidiCoreReviewAuditionResult.Rejected -> unavailable(
                MidiCoreVisualEvidenceScope.SELECTED_CANDIDATE,
                "CANDIDATE_EVIDENCE_UNAVAILABLE",
                prepared.problem.message,
                prepared.problem.nextAction,
            )
        }
    }

    private fun draft(request: ProjectMidiCoreVisualEvidence, authorityHash: String?): MidiCoreVisualEvidence {
        val draftId = request.draftId ?: return unavailable(
            MidiCoreVisualEvidenceScope.DRAFT,
            "NO_DRAFT",
            "No complete draft is available for stopped-state lane display.",
            "Create a complete draft before reviewing its all-role evidence.",
        )
        val draft = request.session.project.arrangementDrafts.singleOrNull { it.id == draftId } ?: return unavailable(
            MidiCoreVisualEvidenceScope.DRAFT,
            "DRAFT_NOT_FOUND",
            "The selected draft is not part of the current project.",
            "Reload Arrange and choose a persisted complete draft.",
        )
        return when (val prepared = reviewAudition.draft(PrepareMidiCoreArrangementDraftAudition(request.session, draft.id))) {
            is MidiCoreReviewAuditionResult.Ready -> available(
                MidiCoreVisualEvidenceScope.DRAFT,
                CacheKey(
                    MidiCoreVisualEvidenceScope.DRAFT,
                    request.session.project.id.value,
                    requireNotNull(request.session.project.sourceMidi).sha256,
                    authorityHash,
                    listOf(draft.id, draft.authorityHash, draft.validation.reportDigestSha256) +
                        draft.candidateReferences.flatMap { reference ->
                            listOf(reference.candidateId, reference.midiSha256, reference.validationReportSha256, reference.authorityHash)
                        },
                ),
                identity(request, authorityHash, candidateIds = draft.candidateReferences.map { it.candidateId }, draftId = draft.id),
                prepared.plan,
                MidiCoreVisualEvidenceCurrentness.CURRENT,
            )
            is MidiCoreReviewAuditionResult.Rejected -> unavailable(
                MidiCoreVisualEvidenceScope.DRAFT,
                "DRAFT_EVIDENCE_UNAVAILABLE",
                prepared.problem.message,
                prepared.problem.nextAction,
            )
        }
    }

    private fun accepted(request: ProjectMidiCoreVisualEvidence, authorityHash: String?): MidiCoreVisualEvidence = when (
        val prepared = reviewAudition.acceptedArrangement(PrepareMidiCoreAcceptedArrangementAudition(request.session))
    ) {
        is MidiCoreReviewAuditionResult.Ready -> {
            val acceptedIds = request.session.project.acceptances
                .sortedWith(compareBy<app.melotrail.project.CandidateAcceptance> { it.occurrenceId }.thenBy { it.role.ordinal })
                .map { it.candidateId }
            val candidates = request.session.project.candidates.associateBy { it.id }
            available(
                MidiCoreVisualEvidenceScope.ACCEPTED,
                CacheKey(
                    MidiCoreVisualEvidenceScope.ACCEPTED,
                    request.session.project.id.value,
                    requireNotNull(request.session.project.sourceMidi).sha256,
                    authorityHash,
                    acceptedIds.flatMap { candidateId ->
                        val candidate = requireNotNull(candidates[candidateId])
                        listOf(candidateId, candidate.midi.sha256, candidate.validationReport.sha256, candidate.authorityHash, candidate.status.name)
                    },
                ),
                identity(request, authorityHash, candidateIds = acceptedIds),
                prepared.plan,
                MidiCoreVisualEvidenceCurrentness.CURRENT,
            )
        }
        is MidiCoreReviewAuditionResult.Rejected -> unavailable(
            MidiCoreVisualEvidenceScope.ACCEPTED,
            "ACCEPTED_EVIDENCE_UNAVAILABLE",
            prepared.problem.message,
            prepared.problem.nextAction,
        )
    }

    private fun available(
        scope: MidiCoreVisualEvidenceScope,
        key: CacheKey,
        identity: MidiCoreVisualEvidenceIdentity,
        plan: MidiAuditionPlaybackPlan,
        currentness: MidiCoreVisualEvidenceCurrentness,
    ): MidiCoreVisualEvidence {
        val cached = synchronized(cache) { cache[key] }
        if (cached != null) return MidiCoreVisualEvidence.Available(cached.copy(cacheStatus = MidiCoreVisualEvidenceCacheStatus.WARM))
        val song = plan.view.song
        val rendered = MidiCoreVisualEvidenceAvailable(
            scope = scope,
            identity = identity,
            timing = MidiCoreVisualEvidenceTiming(
                ppq = song.ppq.value,
                songEndTick = song.songEndTick,
                tempoMicrosecondsPerQuarter = song.tempoMicrosecondsPerQuarter,
                meterNumerator = song.meterNumerator,
                meterDenominatorExponent = song.meterDenominatorExponent,
                authoritative = identity.authorityHash != null,
            ),
            lanes = song.roles.map { track ->
                MidiCoreVisualEvidenceLane(
                    track.role,
                    track.events.filterIsInstance<MidiNoteEvent>().map { note ->
                        MidiCoreVisualEvidenceEvent(
                            note.orderingKey.tick,
                            note.endTick,
                            note.channel,
                            note.pitch,
                            note.velocity,
                            note.channel == MidiExportRole.DRUMS.channel,
                        )
                    },
                )
            },
            currentness = currentness,
            cacheStatus = MidiCoreVisualEvidenceCacheStatus.COLD,
        )
        synchronized(cache) { cache[key] = rendered }
        return MidiCoreVisualEvidence.Available(rendered)
    }

    private fun identity(
        request: ProjectMidiCoreVisualEvidence,
        authorityHash: String?,
        candidateIds: List<String> = emptyList(),
        draftId: String? = null,
    ): MidiCoreVisualEvidenceIdentity = MidiCoreVisualEvidenceIdentity(
        request.session.project.id.value,
        requireNotNull(request.session.project.sourceMidi).sha256,
        authorityHash,
        candidateIds,
        draftId,
    )

    private fun unavailable(
        scope: MidiCoreVisualEvidenceScope,
        code: String,
        message: String,
        nextAction: String,
    ): MidiCoreVisualEvidence = MidiCoreVisualEvidence.Unavailable(
        MidiCoreVisualEvidenceUnavailable(scope, code, message, nextAction),
    )

    private data class CacheKey(
        val scope: MidiCoreVisualEvidenceScope,
        val projectId: String,
        val sourceSha256: String,
        val authorityHash: String?,
        val immutableEvidenceHashes: List<String>,
    )

    private companion object {
        val CURRENT_CANDIDATE_STATUSES = setOf(MidiCoreCandidateStatus.CURRENT, MidiCoreCandidateStatus.ACCEPTED)
        val cache = object : LinkedHashMap<CacheKey, MidiCoreVisualEvidenceAvailable>(MAX_CACHE_ENTRIES, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<CacheKey, MidiCoreVisualEvidenceAvailable>?): Boolean =
                size > MAX_CACHE_ENTRIES
        }
        const val MAX_CACHE_ENTRIES = 32
    }
}
