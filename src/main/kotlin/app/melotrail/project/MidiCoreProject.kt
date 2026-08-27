package app.melotrail.project

import app.melotrail.midi.domain.MidiTrackSummary
import app.melotrail.music.core.ProjectKeySpelling
import app.melotrail.music.core.ProjectMeter
import app.melotrail.music.core.ProjectScaleMode
import app.melotrail.music.core.ProjectTempo

/** Immutable target aggregate for a MIDI-only Melotrail project. */
data class MidiCoreProject(
    val id: ProjectId,
    val metadata: ProjectMetadata,
    val sourceMidi: SourceMidiRecord? = null,
    val selectedMelody: SelectedMelodyTrack? = null,
    val authority: ProjectAuthority? = null,
    val candidates: List<MidiCoreCandidate> = emptyList(),
    val acceptances: List<CandidateAcceptance> = emptyList(),
    val exportSnapshots: List<MidiCoreExportSnapshot> = emptyList(),
    val acceptanceHistory: List<CandidateAcceptanceHistory> = emptyList(),
    /** Monotonic optimistic-concurrency revision for the persisted project document. */
    val revision: Long = 0L,
) {
    init {
        require(revision >= 0L) { "Project revision must not be negative" }
        require(selectedMelody == null || sourceMidi != null) { "A selected melody requires an imported source MIDI record" }
        require(sourceMidi != null || (candidates.isEmpty() && acceptances.isEmpty() && exportSnapshots.isEmpty() && acceptanceHistory.isEmpty())) {
            "Candidates, acceptances, and exports require an imported source MIDI record"
        }
        require(selectedMelody != null || (candidates.isEmpty() && acceptances.isEmpty() && exportSnapshots.isEmpty() && acceptanceHistory.isEmpty())) {
            "Candidates, acceptances, and exports require a selected melody"
        }
        require(authority != null || (candidates.isEmpty() && acceptances.isEmpty() && exportSnapshots.isEmpty() && acceptanceHistory.isEmpty())) {
            "Candidates, acceptances, and exports require musical authority"
        }
        require(candidates.map(MidiCoreCandidate::id).distinct().size == candidates.size) {
            "Candidate IDs must be unique"
        }
        val candidateIds = candidates.map(MidiCoreCandidate::id).toSet()
        require(candidates.all { candidate ->
            candidate.acceptedDependencyIds.none { it == candidate.id } && candidate.acceptedDependencyIds.all { it in candidateIds }
        }) {
            "Candidate accepted dependencies must reference existing distinct candidates"
        }
        require(acceptances.map { it.occurrenceId to it.role }.distinct().size == acceptances.size) {
            "A role may have one acceptance per occurrence"
        }
        acceptances.forEach { acceptance ->
            val candidate = candidates.singleOrNull { it.id == acceptance.candidateId }
            require(candidate != null && candidate.role == acceptance.role && candidate.occurrenceId == acceptance.occurrenceId) {
                "Candidate acceptance must reference the same role and occurrence"
            }
            require(candidate.status != MidiCoreCandidateStatus.REJECTED) {
                "Candidate acceptance cannot reference a rejected candidate"
            }
        }
        authority?.let { currentAuthority ->
            val occurrenceIds = currentAuthority.occurrences.map(ProjectSectionOccurrence::id).toSet()
            require(candidates.all { it.status == MidiCoreCandidateStatus.STALE || it.occurrenceId in occurrenceIds }) {
                "Candidate references an unknown occurrence"
            }
        }
        require(exportSnapshots.map(MidiCoreExportSnapshot::id).distinct().size == exportSnapshots.size) {
            "Export snapshot IDs must be unique"
        }
        exportSnapshots.flatMap(MidiCoreExportSnapshot::acceptedCandidates).forEach { reference ->
            val candidate = candidates.singleOrNull { it.id == reference.candidateId }
            require(
                candidate != null && candidate.role == reference.role && candidate.occurrenceId == reference.occurrenceId &&
                    candidate.midi.sha256 == reference.midiSha256 && candidate.validationReport.sha256 == reference.validationReportSha256 &&
                    candidate.authorityHash == reference.authorityHash && candidate.generatorVersion == reference.generatorVersion &&
                    candidate.profileId == reference.profileId && candidate.patternId == reference.patternId && candidate.seed == reference.seed,
            ) {
                "Export snapshot accepted candidate reference does not match its candidate"
            }
        }
        require(acceptanceHistory.map(CandidateAcceptanceHistory::id).distinct().size == acceptanceHistory.size) {
            "Acceptance history IDs must be unique"
        }
        require(acceptanceHistory == acceptanceHistory.sortedWith(compareBy(CandidateAcceptanceHistory::recordedAt))) {
            "Acceptance history must be ordered chronologically"
        }
        acceptanceHistory.forEach { history ->
            val candidate = candidates.singleOrNull { it.id == history.candidateId }
            require(candidate != null && candidate.role == history.role && candidate.occurrenceId == history.occurrenceId) {
                "Acceptance history must reference the same role and occurrence"
            }
        }
        sourceMidi?.let { source ->
            require(exportSnapshots.all { it.sourceSha256 == source.sha256 }) {
                "Every export snapshot must bind the imported source digest"
            }
        }
    }
}

@JvmInline
value class ProjectId(val value: String) {
    init { require(SAFE_ID.matches(value)) { "Project ID must be a safe stable identifier" } }
}

data class ProjectMetadata(val name: String, val createdAt: String, val applicationVersion: String? = null) {
    init {
        require(name.isNotBlank() && name.length <= 120 && name.none(Char::isISOControl)) { "Project name is invalid" }
        require(createdAt.matches(ISO_INSTANT)) { "Project creation timestamp must be an ISO-8601 UTC instant" }
        require(applicationVersion == null || applicationVersion.isNotBlank() && applicationVersion.length <= 80) {
            "Application version is invalid"
        }
    }
}

/** A portable, project-root-relative artifact location. Filesystem confinement is owned by MC-011. */
@JvmInline
value class ProjectRelativePath(val value: String) {
    init {
        require(value.isNotBlank() && !value.startsWith('/') && !value.startsWith('\\') && !value.contains('\\')) {
            "Artifact path must be a portable relative path"
        }
        val segments = value.split('/')
        require(segments.all {
            it.isNotBlank() && it != "." && it != ".." &&
                it.none(Char::isISOControl) && it.none { character -> character in PORTABLE_PATH_FORBIDDEN }
        }) {
            "Artifact path must not contain traversal or empty segments"
        }
    }
}

data class ProjectArtifact(val path: ProjectRelativePath, val sha256: String) {
    init { require(SHA_256.matches(sha256)) { "Artifact SHA-256 must be lowercase hexadecimal" } }
}

data class SourceMidiRecord(
    val originalFilename: String,
    val sha256: String,
    val format: Int,
    val ppq: Int,
    val original: ProjectArtifact,
    val importReport: ProjectArtifact,
    val trackSummaries: List<MidiTrackSummary>,
    val sourceEndTick: Long,
) {
    init {
        require(originalFilename.isNotBlank() && originalFilename.length <= 255 && originalFilename.none(Char::isISOControl)) {
            "Source MIDI filename is invalid"
        }
        require(SHA_256.matches(sha256)) { "Source MIDI SHA-256 must be lowercase hexadecimal" }
        require(format in setOf(0, 1)) { "Source MIDI format must be 0 or 1" }
        require(ppq in 1..0x7fff) { "Source MIDI PPQ is invalid" }
        require(original.sha256 == sha256) { "Original MIDI artifact must use the source SHA-256" }
        require(trackSummaries.map(MidiTrackSummary::trackIndex) == trackSummaries.indices.toList()) {
            "Source MIDI track summaries must be ordered from track zero"
        }
        require(sourceEndTick >= 0) { "Source MIDI end tick must not be negative" }
    }
}

data class SelectedMelodyTrack(val trackIndex: Int, val channel: Int, val identitySha256: String) {
    init {
        require(trackIndex >= 0 && channel in 0..15) { "Selected melody track identity is invalid" }
        require(SHA_256.matches(identitySha256)) { "Selected melody identity must be a SHA-256 value" }
    }
}

data class ProjectAuthority(
    val key: ProjectKey,
    val tempo: ProjectTempo,
    val meter: ProjectMeter,
    val sectionDefinitions: List<ProjectSectionDefinition>,
    val occurrences: List<ProjectSectionOccurrence>,
    val chordEvents: List<AuthoritativeChordEvent>,
    /** Explicit leading pickup length in project ticks; zero means no pickup. */
    val pickupTicks: Long = 0L,
) {
    init {
        require(pickupTicks >= 0) { "Pickup length must not be negative" }
        require(sectionDefinitions.map(ProjectSectionDefinition::id).distinct().size == sectionDefinitions.size) {
            "Section definition IDs must be unique"
        }
        require(occurrences.map(ProjectSectionOccurrence::id).distinct().size == occurrences.size) { "Occurrence IDs must be unique" }
        val definitionIds = sectionDefinitions.map(ProjectSectionDefinition::id).toSet()
        require(occurrences.all { it.definitionId in definitionIds }) { "Occurrence references an unknown section definition" }
        require(occurrences == occurrences.sortedBy(ProjectSectionOccurrence::startTick)) { "Occurrences must be ordered" }
        require(occurrences.isEmpty() || occurrences.first().startTick == 0L) { "Occurrence timelines must begin at song tick zero" }
        occurrences.zipWithNext().forEach { (left, right) ->
            require(left.endTick == right.startTick) { "Occurrences must form a contiguous timeline" }
        }
        val occurrenceById = occurrences.associateBy(ProjectSectionOccurrence::id)
        require(chordEvents.map(AuthoritativeChordEvent::id).distinct().size == chordEvents.size) { "Chord event IDs must be unique" }
        require(chordEvents.all { it.occurrenceId in occurrenceById }) { "Chord event references an unknown occurrence" }
        require(chordEvents == chordEvents.sortedWith(compareBy<AuthoritativeChordEvent> { occurrenceById.getValue(it.occurrenceId).startTick }.thenBy(AuthoritativeChordEvent::startTick))) {
            "Chord events must have stable occurrence/tick order"
        }
        chordEvents.forEach { chord ->
            val occurrence = occurrenceById.getValue(chord.occurrenceId)
            require(chord.startTick >= occurrence.startTick && chord.endTick <= occurrence.endTick) {
                "Chord event must remain inside its occurrence"
            }
        }
    }

}

data class ProjectSectionDefinition(val id: String, val name: String) {
    init {
        require(SAFE_ID.matches(id)) { "Section definition identity is invalid" }
        require(name.isNotBlank() && name.length <= 120 && name.none(Char::isISOControl)) {
            "Section definition name is invalid"
        }
    }
}

data class ProjectKey(
    val tonic: Int,
    val modeId: String,
    val spelling: ProjectKeySpelling = ProjectKeySpelling.canonical(tonic),
) {
    init {
        require(tonic in 0..11) { "Project key tonic must be a chromatic pitch class" }
        require(ProjectScaleMode.fromId(modeId) != null) { "Project key mode identifier is not supported in MIDI Core V1" }
        require(spelling.chromatic == tonic) { "Project key spelling must match its chromatic tonic" }
    }

    constructor(spelling: ProjectKeySpelling, mode: ProjectScaleMode) : this(spelling.chromatic, mode.id, spelling)

    val mode: ProjectScaleMode get() = requireNotNull(ProjectScaleMode.fromId(modeId))
    val advisoryPitchClasses: Set<Int> get() = mode.intervals.mapTo(linkedSetOf()) { interval -> Math.floorMod(tonic + interval, 12) }
}

data class ProjectSectionOccurrence(
    val id: String,
    val definitionId: String,
    val label: String,
    val startTick: Long,
    val endTick: Long,
) {
    init {
        require(SAFE_ID.matches(id) && SAFE_ID.matches(definitionId)) { "Section occurrence identity is invalid" }
        require(label.isNotBlank() && label.length <= 120 && label.none(Char::isISOControl)) { "Section occurrence label is invalid" }
        require(startTick >= 0 && endTick > startTick) { "Section occurrence timing is invalid" }
    }
}

data class AuthoritativeChordEvent(
    val id: String,
    val occurrenceId: String,
    val symbol: String,
    val startTick: Long,
    val endTick: Long,
) {
    init {
        require(SAFE_ID.matches(id) && SAFE_ID.matches(occurrenceId)) { "Chord event identity is invalid" }
        require(symbol.isNotBlank() && symbol.length <= 80 && symbol.none(Char::isISOControl)) { "Chord symbol is invalid" }
        require(startTick >= 0 && endTick > startTick) { "Chord event timing is invalid" }
    }
}

enum class CandidateRole { CHORDS, BASS, DRUMS }

enum class MidiCoreCandidateStatus { CURRENT, ACCEPTED, REJECTED, STALE }

data class MidiCoreCandidate(
    val id: String,
    val role: CandidateRole,
    val occurrenceId: String,
    val generatorVersion: String,
    val authorityHash: String,
    val seed: Long,
    val midi: ProjectArtifact,
    val validationReport: ProjectArtifact,
    val createdAt: String,
    val profileId: String = "default",
    val patternId: String = "unspecified",
    val status: MidiCoreCandidateStatus = MidiCoreCandidateStatus.CURRENT,
    val rejectionReason: String? = null,
    val acceptedDependencyIds: List<String> = emptyList(),
) {
    init {
        require(SAFE_ID.matches(id) && SAFE_ID.matches(occurrenceId)) { "Candidate identity is invalid" }
        require(generatorVersion.isNotBlank() && generatorVersion.length <= 120 && generatorVersion.none(Char::isISOControl)) { "Generator version is invalid" }
        require(SHA_256.matches(authorityHash)) { "Candidate authority hash must be a SHA-256 value" }
        require(createdAt.matches(ISO_INSTANT)) { "Candidate timestamp must be an ISO-8601 UTC instant" }
        require(TOKEN.matches(profileId)) { "Candidate profile identifier is invalid" }
        require(TOKEN.matches(patternId)) { "Candidate pattern identifier is invalid" }
        require((status == MidiCoreCandidateStatus.REJECTED) == (rejectionReason != null)) {
            "Only rejected candidates may carry a rejection reason"
        }
        require(rejectionReason == null || rejectionReason.isNotBlank() && rejectionReason.length <= 240 && rejectionReason.none(Char::isISOControl)) {
            "Candidate rejection reason is invalid"
        }
        require(acceptedDependencyIds == acceptedDependencyIds.distinct() && acceptedDependencyIds.all(SAFE_ID::matches)) {
            "Candidate accepted dependency IDs must be unique safe identifiers"
        }
    }
}

data class CandidateAcceptance(val occurrenceId: String, val role: CandidateRole, val candidateId: String, val locked: Boolean) {
    init { require(SAFE_ID.matches(occurrenceId) && SAFE_ID.matches(candidateId)) { "Candidate acceptance identity is invalid" } }
}

enum class MidiCoreAcceptanceAction { ACCEPTED, REPLACED, RESTORED, REJECTED, LOCKED, UNLOCKED }

data class CandidateAcceptanceHistory(
    val id: String,
    val occurrenceId: String,
    val role: CandidateRole,
    val candidateId: String,
    val action: MidiCoreAcceptanceAction,
    val recordedAt: String,
) {
    init {
        require(SAFE_ID.matches(id) && SAFE_ID.matches(occurrenceId) && SAFE_ID.matches(candidateId)) {
            "Acceptance history identity is invalid"
        }
        require(recordedAt.matches(ISO_INSTANT)) { "Acceptance history timestamp must be an ISO-8601 UTC instant" }
    }
}

enum class ExportedFileKind { COMPLETE_SONG, MELODY, CHORDS, BASS, DRUMS, MANIFEST }

data class ExportedSnapshotFile(val kind: ExportedFileKind, val artifact: ProjectArtifact)

data class MidiCoreAcceptedCandidateReference(
    val occurrenceId: String,
    val role: CandidateRole,
    val candidateId: String,
    val midiSha256: String,
    val validationReportSha256: String,
    val authorityHash: String,
    val generatorVersion: String,
    val profileId: String,
    val patternId: String,
    val seed: Long,
) {
    init {
        require(SAFE_ID.matches(occurrenceId) && SAFE_ID.matches(candidateId)) {
            "Accepted candidate reference identity is invalid"
        }
        require(SHA_256.matches(midiSha256) && SHA_256.matches(validationReportSha256) && SHA_256.matches(authorityHash)) {
            "Accepted candidate reference hashes are invalid"
        }
        require(generatorVersion.isNotBlank() && generatorVersion.length <= 120 && generatorVersion.none(Char::isISOControl)) { "Accepted generator version is invalid" }
        require(TOKEN.matches(profileId) && TOKEN.matches(patternId)) { "Accepted candidate profile or pattern is invalid" }
    }
}

data class MidiCoreExportSnapshot(
    val id: String,
    val sourceSha256: String,
    val authorityHash: String,
    val files: List<ExportedSnapshotFile>,
    val createdAt: String,
    val acceptedCandidates: List<MidiCoreAcceptedCandidateReference> = emptyList(),
    val roleSettings: Map<String, String> = emptyMap(),
    val generatorVersions: Map<String, String> = emptyMap(),
    val enabledRoles: List<CandidateRole> = CandidateRole.entries,
) {
    init {
        require(SAFE_ID.matches(id)) { "Export snapshot ID is invalid" }
        require(SHA_256.matches(sourceSha256) && SHA_256.matches(authorityHash)) { "Export snapshot hashes are invalid" }
        require(files.map(ExportedSnapshotFile::kind).distinct().size == files.size) { "Export snapshot file kinds must be unique" }
        require(ExportedFileKind.MANIFEST in files.map(ExportedSnapshotFile::kind)) { "Export snapshot requires a manifest" }
        require(createdAt.matches(ISO_INSTANT)) { "Export snapshot timestamp must be an ISO-8601 UTC instant" }
        require(acceptedCandidates.map { it.occurrenceId to it.role }.distinct().size == acceptedCandidates.size) {
            "Export snapshot accepted candidates must have one reference per role and occurrence"
        }
        require(roleSettings.keys.all { SETTING_KEY.matches(it) }) { "Export snapshot role setting keys are invalid" }
        require(roleSettings.values.all { it.length <= 1_000 && it.none(Char::isISOControl) }) {
            "Export snapshot role setting values are invalid"
        }
        require(generatorVersions.keys.all { SETTING_KEY.matches(it) }) { "Export snapshot generator version keys are invalid" }
        require(generatorVersions.values.all { it.isNotBlank() && it.length <= 120 && it.none(Char::isISOControl) }) {
            "Export snapshot generator versions are invalid"
        }
        require(enabledRoles == enabledRoles.distinct().sortedBy(CandidateRole::ordinal)) {
            "Export snapshot enabled roles must use deterministic order without duplicates"
        }
        require(acceptedCandidates.all { it.role in enabledRoles }) {
            "Export snapshot accepted candidates must belong to enabled roles"
        }
    }

    fun isCurrent(authorityHash: String): Boolean = this.authorityHash == authorityHash

    fun isCurrent(sourceSha256: String, authorityHash: String): Boolean =
        this.sourceSha256 == sourceSha256 && this.authorityHash == authorityHash

    fun isCurrent(project: MidiCoreProject): Boolean {
        val source = project.sourceMidi ?: return false
        val authority = try {
            MidiCoreAuthorityHasher.from(project, MidiCoreAuthoritySettings(roleSettings))
        } catch (_: IllegalArgumentException) {
            return false
        }
        if (!isCurrent(source.sha256, authority.sha256)) return false
        val candidates = project.candidates.associateBy(MidiCoreCandidate::id)
        val currentReferences = project.acceptances
            .filter { it.role in enabledRoles }
            .sortedWith(compareBy<CandidateAcceptance> { it.occurrenceId }.thenBy { it.role.ordinal })
            .mapNotNull { acceptance ->
                val candidate = candidates[acceptance.candidateId] ?: return@mapNotNull null
                if (candidate.status == MidiCoreCandidateStatus.STALE || candidate.status == MidiCoreCandidateStatus.REJECTED) return false
                MidiCoreAcceptedCandidateReference(
                    candidate.occurrenceId,
                    candidate.role,
                    candidate.id,
                    candidate.midi.sha256,
                    candidate.validationReport.sha256,
                    candidate.authorityHash,
                    candidate.generatorVersion,
                    candidate.profileId,
                    candidate.patternId,
                    candidate.seed,
                )
            }
        if (acceptedCandidates.isNotEmpty() || currentReferences.isNotEmpty()) {
            if (acceptedCandidates != currentReferences) return false
        }
        val currentGeneratorVersions = currentReferences.associate { "${it.occurrenceId}.${it.role.name.lowercase()}" to it.generatorVersion }
        return generatorVersions.toSortedMap() == currentGeneratorVersions.toSortedMap()
    }

    fun isStale(project: MidiCoreProject): Boolean = !isCurrent(project)
}

private val SAFE_ID = Regex("[A-Za-z0-9][A-Za-z0-9_-]{0,119}")
private val SHA_256 = Regex("[0-9a-f]{64}")
private val TOKEN = Regex("[A-Za-z0-9][A-Za-z0-9_.:-]{0,119}")
private val SETTING_KEY = Regex("[A-Za-z0-9][A-Za-z0-9_.-]{0,119}")
private val ISO_INSTANT = Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(?:\\.[0-9]{1,9})?Z")
private val PORTABLE_PATH_FORBIDDEN = setOf('<', '>', ':', '"', '|', '?', '*')
