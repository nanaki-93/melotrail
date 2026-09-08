package app.melotrail.application

import app.melotrail.arrangement.core.MidiCoreArrangementStyle
import app.melotrail.arrangement.core.MidiCoreArrangementStyleCatalog
import app.melotrail.project.CandidateRole
import app.melotrail.project.MidiCoreArrangementPlan
import app.melotrail.project.MidiCoreArrangementPurpose
import app.melotrail.project.MidiCoreBoundaryIntent
import app.melotrail.project.MidiCoreGrooveDrive
import app.melotrail.project.MidiCoreGrooveFeel
import app.melotrail.project.MidiCoreGrooveSubdivision
import app.melotrail.project.MidiCoreOccurrenceArrangementPlan
import app.melotrail.project.MidiCoreRegisterPreference
import app.melotrail.project.MidiCoreRoleActivity
import app.melotrail.project.MidiCoreRolePlanSettings
import app.melotrail.project.MidiCoreSharedGrooveIntent
import app.melotrail.project.MidiCoreAuthorityHasher
import app.melotrail.project.ProjectAuthority
import app.melotrail.project.ProjectSectionOccurrence
import app.melotrail.project.adapter.MidiCoreArtifactStore
import app.melotrail.project.adapter.MidiCoreProjectSaveException
import kotlin.math.roundToInt

/** A deterministic, session-only arrangement intent derived from one current style and authority snapshot. */
data class MidiCoreArrangementPlanProposal(
    val styleId: String,
    val styleVersion: Int,
    val authorityHash: String,
    val plan: MidiCoreArrangementPlan,
) {
    init {
        require(styleId.isNotBlank() && styleVersion > 0) { "Arrangement-plan proposal style identity is invalid" }
        require(authorityHash.matches(Regex("[0-9a-f]{64}"))) { "Arrangement-plan proposal authority hash is invalid" }
    }
}

data class ProposeMidiCoreArrangementPlan(
    val session: MidiCoreProjectSession,
    val styleId: String,
)

/** Confirmation accepts an optionally edited proposal, but only against its original authority snapshot. */
data class ConfirmMidiCoreArrangementPlanProposal(
    val session: MidiCoreProjectSession,
    val proposal: MidiCoreArrangementPlanProposal,
)

/** Cancelling removes only the caller's session-held proposal; project state was never changed. */
data class CancelMidiCoreArrangementPlanProposal(
    val proposal: MidiCoreArrangementPlanProposal,
)

sealed interface MidiCoreArrangementPlanProposalResult {
    data class Proposed(
        val session: MidiCoreProjectSession,
        val proposal: MidiCoreArrangementPlanProposal,
    ) : MidiCoreArrangementPlanProposalResult

    data class Confirmed(
        val session: MidiCoreProjectSession,
        val plan: MidiCoreArrangementPlan,
    ) : MidiCoreArrangementPlanProposalResult

    data class Cancelled(
        val proposal: MidiCoreArrangementPlanProposal,
    ) : MidiCoreArrangementPlanProposalResult

    data class Rejected(val problem: MidiCoreArrangementPlanProposalProblem) : MidiCoreArrangementPlanProposalResult
}

data class MidiCoreArrangementPlanProposalProblem(
    val code: MidiCoreArrangementPlanProposalProblemCode,
    val message: String,
    val nextAction: String,
)

enum class MidiCoreArrangementPlanProposalProblemCode {
    INVALID_PROJECT,
    STALE_PROJECT,
    AUTHORITY_REQUIRED,
    STYLE_NOT_FOUND,
    PLAN_ALREADY_CONFIRMED,
    STALE_PROPOSAL,
    INVALID_PLAN,
    SAVE_FAILED,
}

/**
 * Builds session-only style proposals and commits exactly one when the musician
 * confirms it. A proposal deliberately has no artifact-store write path.
 */
class MidiCoreArrangementPlanProposalUseCase(
    private val artifacts: MidiCoreArtifactStore = MidiCoreArtifactStore(),
) {
    fun propose(request: ProposeMidiCoreArrangementPlan): MidiCoreArrangementPlanProposalResult {
        val loaded = loadCurrent(request.session) ?: return rejected(
            MidiCoreArrangementPlanProposalProblemCode.INVALID_PROJECT,
            "The project cannot be verified before proposing an arrangement plan.",
            "Open a valid MIDI Core project and try again.",
        )
        if (loaded != request.session.project) return rejected(
            MidiCoreArrangementPlanProposalProblemCode.STALE_PROJECT,
            "The project changed since this arrangement view was opened.",
            "Reload the project before choosing a style.",
        )
        if (loaded.arrangementPlan != null) return rejected(
            MidiCoreArrangementPlanProposalProblemCode.PLAN_ALREADY_CONFIRMED,
            "This project already has a confirmed arrangement plan.",
            "Edit the confirmed plan from Structure & Harmony instead of replacing it implicitly.",
        )
        val authority = loaded.authority ?: return rejected(
            MidiCoreArrangementPlanProposalProblemCode.AUTHORITY_REQUIRED,
            "Confirm structure and harmony before proposing an arrangement plan.",
            "Complete Structure & Harmony, then choose a style.",
        )
        if (authority.occurrences.isEmpty()) return rejected(
            MidiCoreArrangementPlanProposalProblemCode.AUTHORITY_REQUIRED,
            "At least one confirmed section occurrence is required for an arrangement plan.",
            "Define the song's section timeline before choosing a style.",
        )
        val style = try {
            MidiCoreArrangementStyleCatalog.require(request.styleId)
        } catch (_: IllegalArgumentException) {
            return rejected(
                MidiCoreArrangementPlanProposalProblemCode.STYLE_NOT_FOUND,
                "The selected arrangement style is not available.",
                "Choose one of the current named styles and try again.",
            )
        }
        val plan = MidiCoreArrangementPlanProposalFactory.create(authority, style)
        return MidiCoreArrangementPlanProposalResult.Proposed(
            MidiCoreProjectSession(request.session.root.toAbsolutePath().normalize(), loaded),
            MidiCoreArrangementPlanProposal(
                style.id,
                MidiCoreArrangementStyleCatalog.VERSION,
                MidiCoreAuthorityHasher.from(loaded).sha256,
                plan,
            ),
        )
    }

    fun confirm(request: ConfirmMidiCoreArrangementPlanProposal): MidiCoreArrangementPlanProposalResult {
        val root = request.session.root.toAbsolutePath().normalize()
        val current = try {
            artifacts.openProject(root)
        } catch (_: Exception) {
            return rejected(
                MidiCoreArrangementPlanProposalProblemCode.INVALID_PROJECT,
                "The project cannot be verified before confirming an arrangement plan.",
                "Open a valid MIDI Core project and try again.",
            )
        }
        if (current != request.session.project) return rejected(
            MidiCoreArrangementPlanProposalProblemCode.STALE_PROJECT,
            "The project changed since this arrangement view was opened.",
            "Reload the project and review the proposal again.",
        )
        if (current.arrangementPlan != null) return rejected(
            MidiCoreArrangementPlanProposalProblemCode.PLAN_ALREADY_CONFIRMED,
            "This project already has a confirmed arrangement plan.",
            "Edit the confirmed plan explicitly instead of replacing it implicitly.",
        )
        val authority = current.authority ?: return rejected(
            MidiCoreArrangementPlanProposalProblemCode.AUTHORITY_REQUIRED,
            "Confirmed structure and harmony are required before saving an arrangement plan.",
            "Complete Structure & Harmony and create a fresh proposal.",
        )
        if (request.proposal.styleVersion != MidiCoreArrangementStyleCatalog.VERSION ||
            runCatching { MidiCoreArrangementStyleCatalog.require(request.proposal.styleId) }.isFailure
        ) return rejected(
            MidiCoreArrangementPlanProposalProblemCode.STYLE_NOT_FOUND,
            "The proposal uses a style definition that is no longer available.",
            "Create a new proposal with a current named style.",
        )
        val currentHash = MidiCoreAuthorityHasher.from(current).sha256
        if (request.proposal.authorityHash != currentHash) return rejected(
            MidiCoreArrangementPlanProposalProblemCode.STALE_PROPOSAL,
            "The proposal was derived from different confirmed musical authority.",
            "Create a new proposal after reviewing Structure & Harmony.",
        )
        try {
            request.proposal.plan.requireMatches(authority)
        } catch (error: IllegalArgumentException) {
            return rejected(
                MidiCoreArrangementPlanProposalProblemCode.INVALID_PLAN,
                error.message ?: "The arrangement plan does not match current section authority.",
                "Review every occurrence intent and create a fresh proposal.",
            )
        }
        val persisted = try {
            current.copy(arrangementPlan = request.proposal.plan, revision = current.revision + 1L)
        } catch (error: IllegalArgumentException) {
            return rejected(
                MidiCoreArrangementPlanProposalProblemCode.INVALID_PLAN,
                error.message ?: "The arrangement plan is invalid.",
                "Review every occurrence intent and try again.",
            )
        }
        return try {
            artifacts.saveProject(root, persisted)
            MidiCoreArrangementPlanProposalResult.Confirmed(MidiCoreProjectSession(root, persisted), persisted.arrangementPlan!!)
        } catch (_: MidiCoreProjectSaveException) {
            rejected(
                MidiCoreArrangementPlanProposalProblemCode.SAVE_FAILED,
                "The arrangement plan could not be saved safely.",
                "Retry confirmation; the last known-good project remains available.",
            )
        } catch (_: Exception) {
            rejected(
                MidiCoreArrangementPlanProposalProblemCode.INVALID_PROJECT,
                "The arrangement plan could not be saved to this project.",
                "Check the project artifacts and try again.",
            )
        }
    }

    fun cancel(request: CancelMidiCoreArrangementPlanProposal): MidiCoreArrangementPlanProposalResult =
        MidiCoreArrangementPlanProposalResult.Cancelled(request.proposal)

    private fun loadCurrent(session: MidiCoreProjectSession) = try {
        artifacts.openProject(session.root.toAbsolutePath().normalize())
    } catch (_: Exception) {
        null
    }

    private fun rejected(
        code: MidiCoreArrangementPlanProposalProblemCode,
        message: String,
        nextAction: String,
    ) = MidiCoreArrangementPlanProposalResult.Rejected(MidiCoreArrangementPlanProposalProblem(code, message, nextAction))
}

/** Pure proposal policy. It derives purpose from the authoritative occurrence order, never a display label. */
internal object MidiCoreArrangementPlanProposalFactory {
    fun create(authority: ProjectAuthority, style: MidiCoreArrangementStyle): MidiCoreArrangementPlan {
        require(authority.occurrences.isNotEmpty()) { "Arrangement-plan proposals require occurrences" }
        val purposes = purposes(authority.occurrences.size)
        val ordinals = mutableMapOf<MidiCoreArrangementPurpose, Int>()
        return MidiCoreArrangementPlan(
            MidiCoreArrangementPlan.VERSION,
            sharedGroove(style),
            authority.occurrences.mapIndexed { index, occurrence ->
                val purpose = purposes[index]
                val repeatOrdinal = (ordinals[purpose] ?: 0) + 1
                ordinals[purpose] = repeatOrdinal
                occurrencePlan(index, occurrence, purpose, repeatOrdinal, style)
            },
        )
    }

    private fun occurrencePlan(
        index: Int,
        occurrence: ProjectSectionOccurrence,
        purpose: MidiCoreArrangementPurpose,
        repeatOrdinal: Int,
        style: MidiCoreArrangementStyle,
    ): MidiCoreOccurrenceArrangementPlan {
        val intent = intent(purpose)
        val roles = CandidateRole.entries.map { role ->
            val baseline = style.role(role).sectionPolicy
            val activity = intent.activity.getValue(role)
            MidiCoreRolePlanSettings(
                role,
                activity,
                if (activity == MidiCoreRoleActivity.INACTIVE) 0 else scaledPercent(baseline.density, intent.densityScale),
                intent.register.getValue(role),
            )
        }
        val averageEnergy = CandidateRole.entries.map { style.role(it).sectionPolicy.energy }.average()
        return MidiCoreOccurrenceArrangementPlan(
            occurrence.id,
            purpose,
            "phrase-${index + 1}",
            "purpose-${purpose.name.lowercase()}",
            repeatOrdinal,
            scaledPercent(averageEnergy, intent.energyScale),
            roles,
            intent.entry,
            intent.exit,
        )
    }

    private fun sharedGroove(style: MidiCoreArrangementStyle): MidiCoreSharedGrooveIntent = when (style.id) {
        "late-night" -> MidiCoreSharedGrooveIntent(MidiCoreGrooveFeel.SWING, MidiCoreGrooveSubdivision.EIGHTH, MidiCoreGrooveDrive.STEADY)
        "rising-room" -> MidiCoreSharedGrooveIntent(MidiCoreGrooveFeel.STRAIGHT, MidiCoreGrooveSubdivision.SIXTEENTH, MidiCoreGrooveDrive.DRIVING)
        "wide-bridge" -> MidiCoreSharedGrooveIntent(MidiCoreGrooveFeel.HALF_TIME, MidiCoreGrooveSubdivision.EIGHTH, MidiCoreGrooveDrive.RESTRAINED)
        "open-sky" -> MidiCoreSharedGrooveIntent(MidiCoreGrooveFeel.STRAIGHT, MidiCoreGrooveSubdivision.EIGHTH, MidiCoreGrooveDrive.RESTRAINED)
        else -> MidiCoreSharedGrooveIntent(MidiCoreGrooveFeel.STRAIGHT, MidiCoreGrooveSubdivision.EIGHTH, MidiCoreGrooveDrive.STEADY)
    }

    private fun purposes(count: Int): List<MidiCoreArrangementPurpose> = when (count) {
        1 -> listOf(MidiCoreArrangementPurpose.OTHER)
        2 -> listOf(MidiCoreArrangementPurpose.INTRO, MidiCoreArrangementPurpose.OUTRO)
        3 -> listOf(MidiCoreArrangementPurpose.INTRO, MidiCoreArrangementPurpose.VERSE, MidiCoreArrangementPurpose.OUTRO)
        4 -> listOf(MidiCoreArrangementPurpose.INTRO, MidiCoreArrangementPurpose.VERSE, MidiCoreArrangementPurpose.CHORUS, MidiCoreArrangementPurpose.OUTRO)
        5 -> listOf(MidiCoreArrangementPurpose.INTRO, MidiCoreArrangementPurpose.VERSE, MidiCoreArrangementPurpose.CHORUS, MidiCoreArrangementPurpose.BRIDGE, MidiCoreArrangementPurpose.OUTRO)
        else -> buildList {
            add(MidiCoreArrangementPurpose.INTRO)
            add(MidiCoreArrangementPurpose.VERSE)
            add(MidiCoreArrangementPurpose.PRE_CHORUS)
            add(MidiCoreArrangementPurpose.CHORUS)
            repeat(count - 6) { index -> add(if (index % 2 == 0) MidiCoreArrangementPurpose.VERSE else MidiCoreArrangementPurpose.CHORUS) }
            add(MidiCoreArrangementPurpose.BRIDGE)
            add(MidiCoreArrangementPurpose.OUTRO)
        }
    }

    private fun scaledPercent(value: Double, scale: Double): Int = (value * scale * 100.0).roundToInt().coerceIn(0, 100)

    private fun intent(purpose: MidiCoreArrangementPurpose): PurposeIntent = when (purpose) {
        MidiCoreArrangementPurpose.INTRO -> PurposeIntent(
            .35, .45, activities(MidiCoreRoleActivity.SPARSE, MidiCoreRoleActivity.INACTIVE, MidiCoreRoleActivity.INACTIVE),
            registers(MidiCoreRegisterPreference.OPEN, MidiCoreRegisterPreference.LOW, MidiCoreRegisterPreference.MID),
            MidiCoreBoundaryIntent.GRADUAL_ENTRY, MidiCoreBoundaryIntent.HOLD,
        )
        MidiCoreArrangementPurpose.VERSE -> PurposeIntent(
            .62, .68, activities(MidiCoreRoleActivity.SUPPORTING, MidiCoreRoleActivity.SUPPORTING, MidiCoreRoleActivity.SPARSE),
            registers(MidiCoreRegisterPreference.MID, MidiCoreRegisterPreference.LOW, MidiCoreRegisterPreference.MID),
            MidiCoreBoundaryIntent.GRADUAL_ENTRY, MidiCoreBoundaryIntent.NONE,
        )
        MidiCoreArrangementPurpose.PRE_CHORUS -> PurposeIntent(
            .80, .82, activities(MidiCoreRoleActivity.SUPPORTING, MidiCoreRoleActivity.SUPPORTING, MidiCoreRoleActivity.SUPPORTING),
            registers(MidiCoreRegisterPreference.MID, MidiCoreRegisterPreference.LOW, MidiCoreRegisterPreference.MID),
            MidiCoreBoundaryIntent.PICKUP, MidiCoreBoundaryIntent.PICKUP,
        )
        MidiCoreArrangementPurpose.CHORUS -> PurposeIntent(
            1.0, 1.0, activities(MidiCoreRoleActivity.PROMINENT, MidiCoreRoleActivity.PROMINENT, MidiCoreRoleActivity.PROMINENT),
            registers(MidiCoreRegisterPreference.OPEN, MidiCoreRegisterPreference.LOW, MidiCoreRegisterPreference.MID),
            MidiCoreBoundaryIntent.PICKUP, MidiCoreBoundaryIntent.HOLD,
        )
        MidiCoreArrangementPurpose.BRIDGE -> PurposeIntent(
            .72, .70, activities(MidiCoreRoleActivity.SUPPORTING, MidiCoreRoleActivity.SPARSE, MidiCoreRoleActivity.SPARSE),
            registers(MidiCoreRegisterPreference.OPEN, MidiCoreRegisterPreference.LOW, MidiCoreRegisterPreference.MID),
            MidiCoreBoundaryIntent.RELEASE, MidiCoreBoundaryIntent.RELEASE,
        )
        MidiCoreArrangementPurpose.OUTRO -> PurposeIntent(
            .30, .40, activities(MidiCoreRoleActivity.SPARSE, MidiCoreRoleActivity.INACTIVE, MidiCoreRoleActivity.INACTIVE),
            registers(MidiCoreRegisterPreference.OPEN, MidiCoreRegisterPreference.LOW, MidiCoreRegisterPreference.MID),
            MidiCoreBoundaryIntent.RELEASE, MidiCoreBoundaryIntent.RELEASE,
        )
        MidiCoreArrangementPurpose.OTHER -> PurposeIntent(
            .70, .70, activities(MidiCoreRoleActivity.SUPPORTING, MidiCoreRoleActivity.SUPPORTING, MidiCoreRoleActivity.SUPPORTING),
            registers(MidiCoreRegisterPreference.MID, MidiCoreRegisterPreference.LOW, MidiCoreRegisterPreference.MID),
            MidiCoreBoundaryIntent.NONE, MidiCoreBoundaryIntent.NONE,
        )
    }

    private fun activities(
        chords: MidiCoreRoleActivity,
        bass: MidiCoreRoleActivity,
        drums: MidiCoreRoleActivity,
    ) = mapOf(CandidateRole.CHORDS to chords, CandidateRole.BASS to bass, CandidateRole.DRUMS to drums)

    private fun registers(
        chords: MidiCoreRegisterPreference,
        bass: MidiCoreRegisterPreference,
        drums: MidiCoreRegisterPreference,
    ) = mapOf(CandidateRole.CHORDS to chords, CandidateRole.BASS to bass, CandidateRole.DRUMS to drums)

    private data class PurposeIntent(
        val energyScale: Double,
        val densityScale: Double,
        val activity: Map<CandidateRole, MidiCoreRoleActivity>,
        val register: Map<CandidateRole, MidiCoreRegisterPreference>,
        val entry: MidiCoreBoundaryIntent,
        val exit: MidiCoreBoundaryIntent,
    )
}
