package ai.music.workstation.arrangement

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * A single, bounded review of an already-valid v3 arrangement. Model output is
 * data only: it can replace selected existing section fields, never alter the
 * song structure, add instruments, or introduce executable content.
 */
interface ArrangementCritic {
    fun critique(input: DetailedArrangementInput, arrangement: DetailedArrangement): ArrangementCritique
}

@Serializable
data class ArrangementCritique(
    val version: Int = CURRENT_VERSION,
    val decision: CriticDecision,
    val issues: List<CriticIssue> = emptyList(),
    val changes: List<CriticSectionChange> = emptyList()
) {
    companion object { const val CURRENT_VERSION = 1 }
}

@Serializable
enum class CriticDecision { @SerialName("accept") ACCEPT, @SerialName("revise") REVISE }

@Serializable
enum class CriticIssueCategory {
    @SerialName("too_repetitive") TOO_REPETITIVE,
    @SerialName("weak_transition") WEAK_TRANSITION,
    @SerialName("abrupt_energy_change") ABRUPT_ENERGY_CHANGE,
    @SerialName("too_many_instruments") TOO_MANY_INSTRUMENTS,
    @SerialName("insufficient_contrast") INSUFFICIENT_CONTRAST,
    @SerialName("weak_climax") WEAK_CLIMAX,
    @SerialName("source_identity_risk") SOURCE_IDENTITY_RISK
}

@Serializable
data class CriticIssue(
    val category: CriticIssueCategory,
    val targetSectionIndexes: List<Int>,
    val rationale: String
)

/** Each optional value replaces one existing section field; no other fields are addressable. */
@Serializable
data class CriticSectionChange(
    val sectionIndex: Int,
    val energy: Double? = null,
    val instruments: List<DetailedInstrumentPlan>? = null,
    val transitionOut: TransitionPlan? = null
) {
    fun replacementFieldCount(): Int = listOf(energy, instruments, transitionOut).count { it != null }
}

data class CriticValidationResult(val errors: List<String>) {
    val isValid: Boolean get() = errors.isEmpty()
}

object ArrangementCritiqueValidator {
    const val MAX_MODIFIED_SECTIONS = 4
    const val MAX_REPLACEMENT_FIELDS_PER_SECTION = 3
    private const val MAX_RATIONALE_LENGTH = 160

    fun validate(
        critique: ArrangementCritique,
        input: DetailedArrangementInput,
        arrangement: DetailedArrangement
    ): CriticValidationResult {
        val errors = mutableListOf<String>()
        if (critique.version != ArrangementCritique.CURRENT_VERSION) errors += "Unsupported arrangement-critique version: ${critique.version}"
        if (critique.changes.size > MAX_MODIFIED_SECTIONS) errors += "A critic pass may modify at most $MAX_MODIFIED_SECTIONS sections"
        if (critique.changes.map { it.sectionIndex }.toSet().size != critique.changes.size) errors += "Critic changes must not target a section more than once"
        critique.issues.forEachIndexed { position, issue ->
            if (issue.targetSectionIndexes.isEmpty()) errors += "Critic issue ${position + 1} must target at least one section"
            issue.targetSectionIndexes.distinct().forEach { index ->
                if (arrangement.sections.none { it.index == index }) errors += "Critic issue ${position + 1} targets unknown section $index"
            }
            if (issue.rationale.isBlank() || issue.rationale.length > MAX_RATIONALE_LENGTH || !isSafeCriticText(issue.rationale)) {
                errors += "Critic issue ${position + 1} rationale must be short non-executable musical text"
            }
        }
        when (critique.decision) {
            CriticDecision.ACCEPT -> if (critique.issues.isNotEmpty() || critique.changes.isNotEmpty()) errors += "An accept critique must not contain issues or changes"
            CriticDecision.REVISE -> if (critique.issues.isEmpty() || critique.changes.isEmpty()) errors += "A revise critique must contain issues and changes"
        }
        critique.changes.forEachIndexed { position, change ->
            val label = "Critic change ${position + 1}"
            if (arrangement.sections.none { it.index == change.sectionIndex }) errors += "$label targets unknown section ${change.sectionIndex}"
            val count = change.replacementFieldCount()
            if (count == 0) errors += "$label must replace at least one field"
            if (count > MAX_REPLACEMENT_FIELDS_PER_SECTION) errors += "$label replaces too many fields"
            change.energy?.let { if (!it.isFinite() || it !in 0.0..1.0) errors += "$label energy must be a finite number from 0 through 1" }
        }
        if (errors.isEmpty()) {
            try {
                applyUnchecked(arrangement, critique).requireValid(input)
            } catch (error: IllegalArgumentException) {
                errors += "Critic changes do not form a valid detailed arrangement: ${error.message}"
            }
        }
        return CriticValidationResult(errors)
    }

    fun apply(input: DetailedArrangementInput, arrangement: DetailedArrangement, critique: ArrangementCritique): DetailedArrangement {
        arrangement.requireValid(input)
        val validation = validate(critique, input, arrangement)
        require(validation.isValid) { validation.errors.joinToString("; ") }
        return applyUnchecked(arrangement, critique).also { it.requireValid(input) }
    }

    private fun applyUnchecked(arrangement: DetailedArrangement, critique: ArrangementCritique): DetailedArrangement {
        val changes = critique.changes.associateBy { it.sectionIndex }
        return arrangement.copy(sections = arrangement.sections.map { section ->
            changes[section.index]?.let { change ->
                section.copy(
                    energy = change.energy ?: section.energy,
                    instruments = change.instruments ?: section.instruments,
                    transitionOut = change.transitionOut ?: section.transitionOut
                )
            } ?: section
        })
    }
}

/** Available without LM Studio and intentionally does not create autonomous refinement loops. */
class DeterministicArrangementCritic : ArrangementCritic {
    override fun critique(input: DetailedArrangementInput, arrangement: DetailedArrangement): ArrangementCritique {
        arrangement.requireValid(input)
        return ArrangementCritique(decision = CriticDecision.ACCEPT)
    }
}

/** Strict Qwen adapter. The model receives no project, renderer, or source-file paths. */
class LocalQwenArrangementCritic(private val client: LocalQwenClient = LmStudioQwenClient()) : ArrangementCritic {
    override fun critique(input: DetailedArrangementInput, arrangement: DetailedArrangement): ArrangementCritique {
        arrangement.requireValid(input)
        val output = client.complete(SYSTEM_PROMPT, createUserPrompt(input, arrangement))
        val critique = try {
            strictJson.decodeFromString<ArrangementCritique>(output)
        } catch (error: Exception) {
            throw IllegalArgumentException("Qwen returned invalid arrangement-critique JSON: ${error.message}", error)
        }
        val validation = ArrangementCritiqueValidator.validate(critique, input, arrangement)
        require(validation.isValid) { "Invalid Qwen arrangement critique: ${validation.errors.joinToString("; ")}" }
        return critique
    }

    private fun createUserPrompt(input: DetailedArrangementInput, arrangement: DetailedArrangement): String = """
        Validated song plan:
        ${promptJson.encodeToString(input.songPlan)}

        Validated proposed arrangement:
        ${promptJson.encodeToString(arrangement)}

        Versioned MIDI analyses without paths:
        ${promptJson.encodeToString(input.planningInput.analyses.toSortedMap().map { CriticAnalysis(it.key, it.value) })}

        Allowed logical instruments: ${promptJson.encodeToString(input.planningInput.allowedInstruments)}
        Allowed role and transition enums: ${promptJson.encodeToString(AllowedCriticValues())}
        Deterministic render metrics: ${promptJson.encodeToString(arrangement.sections.map { CriticRenderMetric(it.index, it.energy, it.instruments.size, it.transitionOut.type != TransitionType.NONE) })}
    """.trimIndent()

    @Serializable private data class CriticRenderMetric(val sectionIndex: Int, val energy: Double, val activeInstrumentCount: Int, val hasTransition: Boolean)
    @Serializable private data class CriticAnalysis(val partId: String, val analysis: MidiAnalysis)
    @Serializable private data class AllowedCriticValues(
        val bassRoles: List<String> = DetailedBassRole.entries.map { it.name.lowercase() },
        val drumsRoles: List<String> = DrumsRole.entries.map { it.name.lowercase() },
        val sustainedRoles: List<String> = SustainedRole.entries.map { it.name.lowercase() },
        val stringsRoles: List<String> = StringsRole.entries.map { it.name.lowercase() },
        val transitions: List<String> = TransitionType.entries.map { it.name.lowercase() }
    )

    private companion object {
        val strictJson = Json { ignoreUnknownKeys = false }
        val promptJson = Json { encodeDefaults = true }
        const val SYSTEM_PROMPT = """
            You are a music-arrangement critic. Return JSON only, without markdown or prose. You do not generate audio, notes,
            MIDI events, code, commands, paths, sample data, or renderer settings. Top-level fields are exactly version, decision,
            issues, and changes. Version is 1. Decision is accept or revise. Issues contain only category, targetSectionIndexes,
            and a short musical rationale. Categories are too_repetitive, weak_transition, abrupt_energy_change,
            too_many_instruments, insufficient_contrast, weak_climax, source_identity_risk. Changes contain only sectionIndex
            and optional replacement values for energy, instruments, transitionOut. A pass modifies at most 4 sections and 3
            fields per section. For accept use empty issues and changes. For revise use at least one issue and one change.
            Preserve every section identity, order, part, role, source piano plan, allowed instrument, and song-plan constraint.
            Do not add fields or use JSON Patch.
        """
    }
}

object ArrangementCriticStore {
    const val PRE_CRITIC_FILE = "arrangement_v1.json"

    /** Writes review artifacts only after the whole critique and resulting arrangement have validated. */
    fun writeReviewArtifacts(
        projectRoot: java.nio.file.Path,
        input: DetailedArrangementInput,
        approvedText: String,
        proposed: DetailedArrangement,
        critique: ArrangementCritique
    ): java.nio.file.Path {
        val approved = Json { ignoreUnknownKeys = false }.decodeFromString<DetailedArrangement>(approvedText)
        approved.requireValid(input)
        ArrangementCritiqueValidator.apply(input, approved, critique).also { require(it == proposed) { "Critic result did not match the validated draft" } }
        val root = projectRoot.toAbsolutePath().normalize()
        // Write the draft first. If it cannot be written, an existing pre-critic
        // snapshot remains untouched; the approved arrangement is never a target.
        val draft = DetailedArrangementStore.writeDraft(root, input, proposed)
        writeAtomically(root.resolve(PRE_CRITIC_FILE), approvedText)
        return draft
    }

    private fun writeAtomically(target: java.nio.file.Path, content: String) {
        java.nio.file.Files.createDirectories(target.parent)
        val temporary = target.resolveSibling(".${target.fileName}.tmp")
        java.nio.file.Files.writeString(temporary, content, java.nio.charset.StandardCharsets.UTF_8)
        try {
            java.nio.file.Files.move(temporary, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE)
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            java.nio.file.Files.move(temporary, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        }
    }
}

private fun isSafeCriticText(value: String): Boolean =
    value.none { it.isISOControl() } && !UNSAFE_CRITIC_TEXT.containsMatchIn(value)

private val UNSAFE_CRITIC_TEXT = Regex(
    """[\\/]|\.\.|[;|&`$<>{}]|(?i)\b(rm|curl|wget|sh|bash|python|java|cmd|powershell|exec|function|class)\b"""
)
