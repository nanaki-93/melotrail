package app.melotrail.application

import app.melotrail.arrangement.CompositionSettings
import app.melotrail.arrangement.Project
import app.melotrail.arrangement.WorkflowArtifact
import app.melotrail.arrangement.WorkflowArtifactGraph
import app.melotrail.arrangement.WorkflowChange
import app.melotrail.music.MusicalKey
import app.melotrail.music.MusicalOptionModels
import app.melotrail.music.ScaleModeOption
import app.melotrail.music.Tempo
import app.melotrail.music.TimeSignature
import app.melotrail.music.TimeSignatureOption
import app.melotrail.music.TonicOption
import app.melotrail.profile.CompositionProfileCatalog
import app.melotrail.profile.CompositionProfileRef
import app.melotrail.profile.CompositionProfileSummary
import app.melotrail.profile.MoodRef
import app.melotrail.profile.MoodSummary
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest

/** Typed read command. Project access remains owned by [ProjectApplicationService]. */
data class GetCompositionSettings(val root: java.nio.file.Path)

/** Every candidate field and the optimistic decision revision are explicit. */
data class PreviewSettingsChange(
    val root: java.nio.file.Path,
    val expectedRevision: Long,
    val settings: CompositionSettingsInput
)

/** The one application command allowed to change canonical composition settings. */
data class UpdateCompositionSettings(
    val root: java.nio.file.Path,
    val expectedRevision: Long,
    val settings: CompositionSettingsInput
)

data class CompositionSettingsInput(
    val name: String,
    val key: MusicalKey,
    val tempo: Tempo,
    val timeSignature: TimeSignature,
    val profile: CompositionProfileRef,
    val mood: MoodRef
)

data class CompositionSettingsView(
    val name: String,
    val key: MusicalKey,
    val tempo: Tempo,
    val timeSignature: TimeSignature,
    val profile: CompositionProfileRef,
    val mood: MoodRef,
    val decisionRevision: Long,
    val resolvedProfileSha256: String,
    val decisionSha256: String
)

data class CompositionSettingsOptions(
    val tonics: List<TonicOption>,
    val modes: List<ScaleModeOption>,
    val commonMeters: List<TimeSignatureOption>,
    val profiles: List<CompositionProfileSummary>,
    val moods: List<MoodSummary>,
    /** Catalog meters guide the UI only; any core-valid meter remains a valid project decision. */
    val profileMeters: List<TimeSignature>,
    val coreTempoRange: IntRange = 30..240,
    val allowsCustomCoreMeter: Boolean = true
)

data class GetCompositionSettingsResult(
    val settings: CompositionSettingsView?,
    val options: CompositionSettingsOptions,
    val setupRequired: Boolean,
    val validationError: String? = null
)

data class SettingsInvalidationPreview(
    val changes: Set<WorkflowChange>,
    val artifacts: Set<WorkflowArtifact>,
    val affectedStages: Set<WorkflowStage>
)

data class PreviewSettingsChangeResult(
    val currentRevision: Long,
    val candidate: CompositionSettingsView,
    val invalidation: SettingsInvalidationPreview
)

data class UpdateCompositionSettingsResult(
    val snapshot: ProjectSnapshot,
    val settings: CompositionSettingsView,
    val invalidation: SettingsInvalidationPreview
)

/**
 * Pure settings policy: it neither reads nor writes project files. The facade
 * supplies the canonical aggregate under its project mutex and publishes it atomically.
 */
class CompositionSettingsApplicationService(private val catalog: CompositionProfileCatalog) {
    fun query(project: Project): GetCompositionSettingsResult {
        val stored = project.envelope.compositionSettings
        val validation = stored?.let { validateStored(project.name, it) }
        val settings = stored?.takeIf { validation == null && it.complete }?.toView(project.name)
        val selectedProfile = settings?.profile ?: catalog.profiles().firstOrNull()?.ref
        return GetCompositionSettingsResult(
            settings = settings,
            options = options(selectedProfile),
            setupRequired = settings == null,
            validationError = validation
        )
    }

    fun preview(project: Project, expectedRevision: Long, input: CompositionSettingsInput): PreviewSettingsChangeResult {
        val current = project.envelope.compositionSettings
        val currentRevision = current?.decisionRevision ?: 0
        require(expectedRevision == currentRevision) {
            "Composition settings changed from revision $expectedRevision to $currentRevision; reload before saving."
        }
        val candidate = candidate(currentRevision + 1, input)
        return PreviewSettingsChangeResult(currentRevision, candidate.toView(input.name), invalidation(project, input))
    }

    fun update(project: Project, expectedRevision: Long, input: CompositionSettingsInput): PreparedCompositionSettingsUpdate {
        val preview = preview(project, expectedRevision, input)
        val stored = CompositionSettings(
            key = preview.candidate.key,
            tempo = preview.candidate.tempo,
            timeSignature = preview.candidate.timeSignature,
            profile = preview.candidate.profile,
            mood = preview.candidate.mood,
            decisionRevision = preview.candidate.decisionRevision,
            resolvedProfileSha256 = preview.candidate.resolvedProfileSha256,
            decisionSha256 = preview.candidate.decisionSha256
        )
        return PreparedCompositionSettingsUpdate(
            project.copy(
                name = input.name,
                envelope = project.envelope.copy(compositionSettings = stored),
                workflow = preview.invalidation.changes.fold(project.workflow) { workflow, change -> workflow.invalidate(change) }
            ),
            preview
        )
    }

    fun isReady(project: Project): Boolean = query(project).settings != null

    private fun candidate(revision: Long, input: CompositionSettingsInput): CandidateCompositionSettings {
        require(input.name.isNotBlank() && input.name == input.name.trim() && input.name.length <= 160) {
            "Project name must be 1 to 160 non-whitespace-trimmed characters."
        }
        require(input.key.isExecutable) { "Scale mode '${input.key.modeId.value}' is not executable by this version." }
        require(input.tempo.bpm in 30.0..240.0) { "Tempo must be from 30 to 240 BPM." }
        require(input.timeSignature.numerator in 1..32) { "Time-signature numerator must be from 1 to 32." }
        val resolved = catalog.resolve(input.profile, input.mood)
        val decision = CompositionSettingsDecision(input.name, input.key, input.tempo, input.timeSignature, input.profile, input.mood, resolved.resolvedHash)
        return CandidateCompositionSettings(revision, decision, sha256(json.encodeToString(decision)))
    }

    private fun invalidation(project: Project, candidate: CompositionSettingsInput): SettingsInvalidationPreview {
        val current = project.envelope.compositionSettings?.takeIf { it.complete }
        val changes = buildSet {
            if (current == null) {
                // Uncontextualized legacy outputs remain inspectable, but cannot become current under a new decision.
                add(WorkflowChange.COMPOSITION_KEY)
                add(WorkflowChange.COMPOSITION_TEMPO_OR_METER)
                add(WorkflowChange.COMPOSITION_PROFILE_OR_MOOD)
                return@buildSet
            }
            if (current.key != candidate.key) add(WorkflowChange.COMPOSITION_KEY)
            if (current.tempo != candidate.tempo || current.timeSignature != candidate.timeSignature) add(WorkflowChange.COMPOSITION_TEMPO_OR_METER)
            if (current.profile != candidate.profile || current.mood != candidate.mood) add(WorkflowChange.COMPOSITION_PROFILE_OR_MOOD)
        }
        val artifacts = changes.flatMapTo(linkedSetOf()) { WorkflowArtifactGraph.invalidatedBy(it) }
        return SettingsInvalidationPreview(changes, artifacts, artifacts.mapTo(linkedSetOf(), ::stageFor))
    }

    private fun options(selectedProfile: CompositionProfileRef?): CompositionSettingsOptions {
        val profiles = catalog.profiles()
        val profile = selectedProfile ?: profiles.firstOrNull()?.ref
        val moods = profile?.let(catalog::moods).orEmpty()
        val meters = profile?.let { catalog.resolve(it).supportedMeters.map { meter -> TimeSignature(meter.numerator, meter.denominator) } }.orEmpty()
        return CompositionSettingsOptions(MusicalOptionModels.tonics, MusicalOptionModels.modes, MusicalOptionModels.timeSignatures, profiles, moods, meters)
    }

    private fun validateStored(name: String, settings: CompositionSettings): String? {
        if (!settings.complete) return null
        return runCatching {
            val resolved = catalog.resolve(requireNotNull(settings.profile), requireNotNull(settings.mood))
        require(resolved.resolvedHash == settings.resolvedProfileSha256) {
            "The saved profile/mood resolution differs from the installed catalog; choose and save settings again."
        }
            val decision = CompositionSettingsDecision(name, settings.key, settings.tempo, settings.timeSignature, requireNotNull(settings.profile), requireNotNull(settings.mood), settings.resolvedProfileSha256)
            require(sha256(json.encodeToString(decision)) == settings.decisionSha256) {
                "The saved composition decision fingerprint is invalid; choose and save settings again."
            }
        }.exceptionOrNull()?.message
    }

    private fun CompositionSettings.toView(name: String) = CompositionSettingsView(
        name, key, tempo, timeSignature, requireNotNull(profile), requireNotNull(mood), decisionRevision,
        resolvedProfileSha256, decisionSha256
    )

    private fun CandidateCompositionSettings.toView(name: String) = CompositionSettingsView(
        name, decision.key, decision.tempo, decision.timeSignature, decision.profile, decision.mood,
        revision, decision.resolvedProfileSha256, decisionSha256
    )

    private fun stageFor(artifact: WorkflowArtifact): WorkflowStage = when (artifact) {
        WorkflowArtifact.COHESION -> WorkflowStage.COHESION
        WorkflowArtifact.ARRANGEMENT -> WorkflowStage.ARRANGEMENT
        WorkflowArtifact.GENERATED_MIDI, WorkflowArtifact.STEMS -> WorkflowStage.RENDER
        WorkflowArtifact.DRY_MIX, WorkflowArtifact.AUDIO_TEXTURE -> WorkflowStage.MIX
        WorkflowArtifact.MASTER, WorkflowArtifact.RELEASE -> WorkflowStage.MASTER
        WorkflowArtifact.COMMERCIAL_EXPORT -> WorkflowStage.COMMERCIAL_EXPORT
        else -> WorkflowStage.ANALYSIS
    }

    private companion object {
        val json = Json { encodeDefaults = true }
        fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}

data class PreparedCompositionSettingsUpdate(
    val project: Project,
    val preview: PreviewSettingsChangeResult
)

@Serializable
private data class CompositionSettingsDecision(
    val name: String,
    val key: MusicalKey,
    val tempo: Tempo,
    val timeSignature: TimeSignature,
    val profile: CompositionProfileRef,
    val mood: MoodRef,
    val resolvedProfileSha256: String
)

private data class CandidateCompositionSettings(
    val revision: Long,
    val decision: CompositionSettingsDecision,
    val decisionSha256: String
)
