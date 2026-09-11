package app.melotrail.desktop

import app.melotrail.arrangement.core.MidiCoreRoleValidationReport
import app.melotrail.application.MidiCoreCandidateReviewItem
import app.melotrail.application.MidiCoreVisualEvidence
import app.melotrail.application.MidiCoreVisualEvidenceAvailable
import app.melotrail.application.MidiCoreVisualEvidenceCacheStatus
import app.melotrail.application.MidiCoreVisualEvidenceCurrentness
import app.melotrail.application.MidiCoreVisualEvidenceEvent
import app.melotrail.application.MidiCoreVisualEvidenceIdentity
import app.melotrail.application.MidiCoreVisualEvidenceLane
import app.melotrail.application.MidiCoreVisualEvidenceProjection
import app.melotrail.application.MidiCoreVisualEvidenceScope
import app.melotrail.application.MidiCoreVisualEvidenceTiming
import app.melotrail.midi.domain.MidiExportRole
import app.melotrail.project.AuthoritativeChordEvent
import app.melotrail.project.CandidateAcceptance
import app.melotrail.project.CandidateRole
import app.melotrail.project.MidiCoreArrangementDraft
import app.melotrail.project.MidiCoreArrangementDraftCandidateReference
import app.melotrail.project.MidiCoreArrangementDraftValidationSummary
import app.melotrail.project.MidiCoreAuthorityHasher
import app.melotrail.project.MidiCoreCandidate
import app.melotrail.project.MidiCoreCandidateStatus
import app.melotrail.project.MidiCorePlannedRest
import app.melotrail.project.MidiCoreProject
import app.melotrail.project.ProjectArtifact
import app.melotrail.project.ProjectAuthority
import app.melotrail.project.ProjectId
import app.melotrail.project.ProjectKey
import app.melotrail.project.ProjectMetadata
import app.melotrail.project.ProjectRelativePath
import app.melotrail.project.ProjectSectionDefinition
import app.melotrail.project.ProjectSectionOccurrence
import app.melotrail.project.SelectedMelodyTrack
import app.melotrail.project.SourceMidiRecord
import app.melotrail.midi.domain.MidiChannelSummary
import app.melotrail.midi.domain.MidiTrackRoleHint
import app.melotrail.midi.domain.MidiTrackSummary
import app.melotrail.music.core.ProjectKeySpelling
import app.melotrail.music.core.ProjectMeter
import app.melotrail.music.core.ProjectScaleMode
import app.melotrail.music.core.ProjectTempo

import app.melotrail.project.MidiCoreArrangementPlan
import app.melotrail.project.MidiCoreArrangementDraftAcceptanceHistory
import app.melotrail.project.MidiCoreSharedGrooveIntent
import app.melotrail.project.MidiCoreGrooveFeel
import app.melotrail.project.MidiCoreGrooveSubdivision
import app.melotrail.project.MidiCoreGrooveDrive
import app.melotrail.project.MidiCoreOccurrenceArrangementPlan
import app.melotrail.project.MidiCoreArrangementPurpose
import app.melotrail.project.MidiCoreRolePlanSettings
import app.melotrail.project.MidiCoreRoleActivity
import app.melotrail.project.MidiCoreRegisterPreference
import app.melotrail.project.MidiCoreBoundaryIntent
import app.melotrail.project.MidiCoreExportSnapshot
import app.melotrail.project.MidiCoreAcceptedCandidateReference
import app.melotrail.project.ExportedSnapshotFile
import app.melotrail.project.ExportedFileKind
import java.nio.file.Path

/** Fixed, synthetic presentation fixture shared with the existing Arrange behavior tests. */
internal object MidiCoreVisualFixture {
    fun state(
        styleId: String? = null,
        selectedOccurrenceId: String? = "verse-1",
        operation: MidiCoreWorkspaceOperation = MidiCoreWorkspaceOperation.idle(),
    ): MidiCoreWorkspaceState {
        val authority = ProjectAuthority(
            key = ProjectKey(ProjectKeySpelling.C, ProjectScaleMode.MAJOR),
            tempo = ProjectTempo(500_000), meter = ProjectMeter(4, 2),
            sectionDefinitions = listOf(ProjectSectionDefinition("verse", "Verse")),
            occurrences = listOf(
                ProjectSectionOccurrence("verse-1", "verse", "Verse", 0L, 1920L),
                ProjectSectionOccurrence("verse-2", "verse", "Verse", 1920L, 3840L),
            ),
            chordEvents = listOf(
                AuthoritativeChordEvent("chord-1", "verse-1", "C", 0L, 1920L),
                AuthoritativeChordEvent("chord-2", "verse-2", "G", 1920L, 3840L),
            ),
        )
        val trackSummaries = listOf(
            MidiTrackSummary(
                trackIndex = 0,
                name = "Lead",
                channels = listOf(MidiChannelSummary(0, 1, 60, 60, 0, listOf(MidiTrackRoleHint.MELODY))),
                durationTicks = 3840L,
            ),
        )
        val sourceMidi = SourceMidiRecord(
            "source.mid",
            "f".repeat(64),
            1,
            480,
            ProjectArtifact(ProjectRelativePath("source/original.mid"), "f".repeat(64)),
            ProjectArtifact(ProjectRelativePath("reports/import.json"), "0".repeat(64)),
            trackSummaries,
            3840L,
        )
        val selectedMelody = SelectedMelodyTrack(0, 0, "1".repeat(64))
        val candidate = MidiCoreCandidate(
            id = "candidate-existing", role = CandidateRole.CHORDS, occurrenceId = "verse-1", generatorVersion = "midi-core-v1",
            authorityHash = "a".repeat(64), seed = 5L,
            midi = ProjectArtifact(ProjectRelativePath("candidates/chords/verse-1/candidate-existing.mid"), "b".repeat(64)),
            validationReport = ProjectArtifact(ProjectRelativePath("reports/candidates/candidate-existing.json"), "c".repeat(64)),
            createdAt = "2026-08-28T00:00:00Z", profileId = "chords.sustained", patternId = "chords.rhythm.sustained",
            status = MidiCoreCandidateStatus.REJECTED, rejectionReason = "Try another rhythm.",
        )
        val report = MidiCoreRoleValidationReport(
            contextSha256 = "d".repeat(64), candidateSha256 = "e".repeat(64), role = CandidateRole.CHORDS, occurrenceId = "verse-1", noteCount = 4,
            findings = emptyList(),
        )
        return MidiCoreWorkspaceState(
            project = MidiCoreProject(
                id = ProjectId("arrange-project"), metadata = ProjectMetadata("Arrange", "2026-08-28T00:00:00Z"),
                sourceMidi = sourceMidi, selectedMelody = selectedMelody,
                authority = authority, candidates = listOf(candidate), revision = 3L,
            ),
            source = MidiCoreSourceUiState(
                status = MidiCoreSourceStatus.IMPORTED,
                originalFilename = sourceMidi.originalFilename,
                sha256 = sourceMidi.sha256,
                format = sourceMidi.format,
                ppq = sourceMidi.ppq,
                lastNoteEndTick = sourceMidi.lastNoteEndTick,
                sourceEndTick = sourceMidi.sourceEndTick,
                trackSummaries = sourceMidi.trackSummaries,
                reportAvailable = true,
            ),
            melody = MidiCoreMelodyUiState(selectedMelody),
            authority = MidiCoreAuthorityUiState(confirmed = authority),
            stylePreview = MidiCoreArrangementStyleUiState(selectedStyleId = styleId, occurrenceId = selectedOccurrenceId),
            arrangement = MidiCoreArrangementUiState(selectedOccurrenceId = selectedOccurrenceId),
            review = MidiCoreCandidateReviewUiState(role = CandidateRole.CHORDS, occurrenceId = "verse-1", candidates = listOf(MidiCoreCandidateReviewItem(candidate, report, emptyList(), authorityCurrent = true, accepted = false, locked = false))),
            operation = operation,
        )
    }

    /** Exercise draft, accepted notes/rests and a stale scope without writing any musical artifact. */
    fun populatedState(): MidiCoreWorkspaceState {
        val base = state(styleId = "late-night")
        val project = requireNotNull(base.project)
        val hasher = MidiCoreAuthorityHasher.from(project)
        val template = project.candidates.single()
        val candidates = requireNotNull(project.authority).occurrences.flatMap { occurrence ->
            CandidateRole.entries.filterNot { occurrence.id == "verse-1" && it == CandidateRole.BASS }.map { role ->
                template.copy(id = "visual-${occurrence.id}-${role.name.lowercase()}", role = role,
                    occurrenceId = occurrence.id, authorityHash = hasher.sha256, rejectionReason = null,
                    status = when {
                        occurrence.id == "verse-1" && role == CandidateRole.CHORDS -> MidiCoreCandidateStatus.ACCEPTED
                        occurrence.id == "verse-2" && role == CandidateRole.DRUMS -> MidiCoreCandidateStatus.STALE
                        else -> MidiCoreCandidateStatus.CURRENT
                    })
            }
        }
        val rest = MidiCorePlannedRest("verse-1", CandidateRole.BASS, hasher.scopeHash("verse-1", CandidateRole.BASS))
        val draft = MidiCoreArrangementDraft(
            id = "visual-draft", styleId = "late-night", styleVersion = 1,
            authorityHash = hasher.sha256, rootSeed = 1L,
            candidateReferences = candidates.map { candidate -> MidiCoreArrangementDraftCandidateReference(
                occurrenceId = candidate.occurrenceId, role = candidate.role, candidateId = candidate.id,
                midiSha256 = candidate.midi.sha256, validationReportSha256 = candidate.validationReport.sha256,
                authorityHash = hasher.sha256,
            ) },
            plannedRests = listOf(rest),
            validation = MidiCoreArrangementDraftValidationSummary(6, 4, true, "d".repeat(64)),
            createdAt = "2026-09-04T00:00:00Z",
        )
        val accepted = candidates.first { it.status == MidiCoreCandidateStatus.ACCEPTED }
        return base.copy(
            project = project.copy(candidates = candidates, arrangementDrafts = listOf(draft),
                acceptances = listOf(CandidateAcceptance("verse-1", CandidateRole.CHORDS, accepted.id, false)),
                acceptedPlannedRests = listOf(rest)),
            review = MidiCoreCandidateReviewUiState(role = CandidateRole.CHORDS, occurrenceId = "verse-1"),
            visualEvidence = evidence(withRest = true),
        )
    }

    /** Separate coherent ready state: every scope accepted and the saved snapshot matches it. */
    fun readyState(): MidiCoreWorkspaceState {
        val base = state(styleId = "late-night")
        val original = requireNotNull(base.project)
        val plan = MidiCoreArrangementPlan(
            version = 1,
            sharedGroove = MidiCoreSharedGrooveIntent(MidiCoreGrooveFeel.STRAIGHT,
                MidiCoreGrooveSubdivision.EIGHTH, MidiCoreGrooveDrive.STEADY),
            occurrences = requireNotNull(original.authority).occurrences.mapIndexed { index, occurrence ->
                MidiCoreOccurrenceArrangementPlan(
                    occurrenceId = occurrence.id, purpose = MidiCoreArrangementPurpose.VERSE,
                    phraseGroupId = "phrase-verse", repeatFamilyId = "repeat-verse", repeatOrdinal = index + 1,
                    energy = 50, roleSettings = CandidateRole.entries.map { role ->
                        val rest = index == 0 && role == CandidateRole.BASS
                        MidiCoreRolePlanSettings(role,
                            if (rest) MidiCoreRoleActivity.INACTIVE else MidiCoreRoleActivity.SUPPORTING,
                            if (rest) 0 else 50, MidiCoreRegisterPreference.MID)
                    }, entryIntent = MidiCoreBoundaryIntent.NONE, exitIntent = MidiCoreBoundaryIntent.NONE,
                )
            },
        )
        val project = original.copy(candidates = emptyList(), arrangementPlan = plan)
        val hash = MidiCoreAuthorityHasher.from(project)
        val candidates = populatedState().project!!.candidates.map { candidate ->
            candidate.copy(status = MidiCoreCandidateStatus.ACCEPTED,
                authorityHash = hash.scopeHash(candidate.occurrenceId, candidate.role))
        }
        val rest = MidiCorePlannedRest("verse-1", CandidateRole.BASS, hash.scopeHash("verse-1", CandidateRole.BASS))
        val draft = populatedState().project!!.arrangementDrafts.single().copy(
            authorityHash = hash.sha256, plannedRests = listOf(rest),
            candidateReferences = candidates.map { c -> MidiCoreArrangementDraftCandidateReference(
                c.occurrenceId, c.role, c.id, c.midi.sha256, c.validationReport.sha256, c.authorityHash) },
        )
        val references = candidates.sortedWith(compareBy<MidiCoreCandidate> { it.occurrenceId }.thenBy { it.role.ordinal })
            .map { c -> MidiCoreAcceptedCandidateReference(c.occurrenceId, c.role, c.id,
                c.midi.sha256, c.validationReport.sha256, c.authorityHash,
                c.generatorVersion, c.profileId, c.patternId, c.seed) }
        val snapshot = MidiCoreExportSnapshot(
            id = "visual-export", sourceSha256 = project.sourceMidi!!.sha256, authorityHash = hash.sha256,
            files = ExportedFileKind.entries.map { kind ->
                val name = when (kind) {
                    ExportedFileKind.COMPLETE_SONG -> "complete-song.mid"
                    ExportedFileKind.MANIFEST -> "manifest.json"
                    else -> kind.name.lowercase() + ".mid"
                }
                ExportedSnapshotFile(kind, ProjectArtifact(ProjectRelativePath("exports/visual-export/$name"), "e".repeat(64)))
            },
            createdAt = "2026-09-04T00:00:00Z", acceptedCandidates = references,
            generatorVersions = references.associate { "${it.occurrenceId}.${it.role.name.lowercase()}" to it.generatorVersion },
        )
        val acceptances = candidates.map { CandidateAcceptance(it.occurrenceId, it.role, it.id, false) }
        val history = MidiCoreArrangementDraftAcceptanceHistory("visual-use", draft.id, emptyList(),
            acceptances, "2026-09-04T00:00:00Z", appliedPlannedRests = listOf(rest))
        val ready = project.copy(candidates = candidates, arrangementDrafts = listOf(draft),
            arrangementDraftAcceptanceHistory = listOf(history),
            acceptances = acceptances,
            acceptedPlannedRests = listOf(rest), exportSnapshots = listOf(snapshot))
        val projection = evidence(withRest = true)
        fun current(e: MidiCoreVisualEvidence) = (e as MidiCoreVisualEvidence.Available).let {
            it.copy(value = it.value.copy(currentness = MidiCoreVisualEvidenceCurrentness.CURRENT,
                identity = it.value.identity.copy(authorityHash = hash.sha256, candidateIds = candidates.map { c -> c.id }),
                lanes = (projection.draft as MidiCoreVisualEvidence.Available).value.lanes))
        }
        return base.copy(project = ready, projectRoot = Path.of("/visual-fixtures/arrange-project"),
            review = MidiCoreCandidateReviewUiState(role = CandidateRole.CHORDS, occurrenceId = "verse-1"),
            export = MidiCoreExportUiState(latestSnapshot = snapshot),
            visualEvidence = projection.copy(draft = current(projection.draft), accepted = current(projection.accepted)))
            .also {
                check(exportReadiness(it).isEmpty()) { "Ready fixture must enable export" }
                check(midiCoreExportMatchesAcceptedWork(it, snapshot)) { "Saved snapshot must match accepted work" }
            }
    }

    fun operation(failed: Boolean) = MidiCoreWorkspaceOperation(
        id = 3L, kind = MidiCoreWorkspaceOperationKind.DRAFT_GENERATION,
        phase = if (failed) MidiCoreWorkspaceOperationPhase.FAILED else MidiCoreWorkspaceOperationPhase.RUNNING,
        message = if (failed) "Retry the incomplete draft." else "Creating draft: verse-2 · bass (4/6)",
        progress = if (failed) null else MidiCoreWorkspaceOperationProgress(4, 6),
        cancellableAtBoundary = !failed,
        retry = if (failed) MidiCoreWorkspaceIntent.CreateArrangementDraft("late-night", 1L, "visual-draft") else null,
        outcome = if (failed) MidiCoreWorkspaceOperationOutcome.FAILURE else null,
    )

    fun evidence(withRest: Boolean = false): MidiCoreVisualEvidenceProjection {
        fun available(scope: MidiCoreVisualEvidenceScope) = MidiCoreVisualEvidence.Available(
            MidiCoreVisualEvidenceAvailable(
                scope = scope,
                identity = MidiCoreVisualEvidenceIdentity("arrange-project", "f".repeat(64), authorityHash = "a".repeat(64)),
                timing = MidiCoreVisualEvidenceTiming(480, 3840L, 500_000, 4, 2, authoritative = true),
                lanes = MidiExportRole.entries.filter {
                    !withRest || scope != MidiCoreVisualEvidenceScope.PROTECTED_SOURCE || it == MidiExportRole.MELODY
                }.map { role ->
                    MidiCoreVisualEvidenceLane(
                        role,
                        if (withRest && scope == MidiCoreVisualEvidenceScope.ACCEPTED &&
                            role in listOf(MidiExportRole.BASS, MidiExportRole.DRUMS)) emptyList()
                        else {
                            val start = if (withRest && role == MidiExportRole.BASS) 1920L else 0L
                            listOf(MidiCoreVisualEvidenceEvent(start, start + 960L, role.channel,
                                60 + role.ordinal, 96, role == MidiExportRole.DRUMS))
                        },
                    )
                },
                currentness = if (withRest && scope == MidiCoreVisualEvidenceScope.DRAFT)
                    MidiCoreVisualEvidenceCurrentness.STALE else MidiCoreVisualEvidenceCurrentness.CURRENT,
                cacheStatus = MidiCoreVisualEvidenceCacheStatus.WARM,
            ),
        )
        return MidiCoreVisualEvidenceProjection(
            source = available(MidiCoreVisualEvidenceScope.PROTECTED_SOURCE),
            selectedCandidate = available(MidiCoreVisualEvidenceScope.SELECTED_CANDIDATE),
            draft = available(MidiCoreVisualEvidenceScope.DRAFT),
            accepted = available(MidiCoreVisualEvidenceScope.ACCEPTED),
        )
    }

}
