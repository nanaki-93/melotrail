package app.melotrail.application

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Evaluation provenance only. Musical authority continues to use MidiCoreProjectSchema. */
@Serializable
data class MidiCoreEvaluationRequest(
    val schemaVersion: Int = 1,
    val evaluationId: String,
    val buildIdentity: String,
    val cases: List<MidiCoreEvaluationCaseInput>,
) {
    init {
        require(schemaVersion == 1) { "Unsupported evaluation request schema" }
        evaluationId.requireEvaluationId()
        require(buildIdentity.isNotBlank() && buildIdentity.none(Char::isISOControl)) { "Record the build/commit being evaluated" }
        require(cases.size <= 20 && cases.map { it.case.id }.distinct().size == cases.size) { "Use at most 20 uniquely named cases" }
        require(cases.count { it.case.classification != MidiCoreEvaluationClassification.DEVELOPMENT } <= 5) { "The final set contains at most five songs" }
    }
}

@Serializable
data class MidiCoreEvaluationCaseInput(val projectDirectory: String, val case: MidiCoreEvaluationCase) {
    init { require(projectDirectory.isNotBlank()) { "Supply an existing current MIDI project" } }
}

@Serializable
enum class MidiCoreEvaluationClassification { FINAL_UNSEEN, FINAL_SEEN, DEVELOPMENT }

@Serializable
enum class MidiCoreEvaluationOwnership { USER_OWNED, CLEARLY_LICENSED }

@Serializable
data class MidiCoreEvaluationCase(
    val id: String,
    val classification: MidiCoreEvaluationClassification,
    val ownership: MidiCoreEvaluationOwnership,
    val ownershipEvidence: String,
    val exposureNote: String,
    val fullSong: Boolean,
    val synthetic: Boolean,
    val tuned: Boolean,
    val variety: List<String>,
    val styleId: String,
    val seed: Long,
    val pianoMelodyLoop: MidiCoreEvaluationBars,
    val fullArrangementLoop: MidiCoreEvaluationBars,
    val instruments: List<MidiCoreEvaluationInstrument>,
    val baselineSnapshotId: String? = null,
) {
    init {
        id.requireEvaluationId()
        require(ownershipEvidence.isNotBlank() && exposureNote.isNotBlank()) { "Ownership and prior exposure need explicit supplied statements" }
        require(variety.isNotEmpty() && variety.all { it.isNotBlank() } && variety.distinct().size == variety.size) { "Describe the song's musical variety" }
        require(styleId.isNotBlank()) { "A frozen style is required" }
        require(instruments.map { it.role }.toSet() == setOf("melody", "chords", "bass", "drums") && instruments.size == 4) {
            "Record one instrument and level for each of melody, chords, bass and drums"
        }
        require(classification == MidiCoreEvaluationClassification.DEVELOPMENT || (fullSong && !synthetic && !tuned)) {
            "Synthetic, partial or tuned cases belong to DEVELOPMENT, never the final set"
        }
        baselineSnapshotId?.requireEvaluationId()
    }

    val directory: String get() = "${if (classification == MidiCoreEvaluationClassification.DEVELOPMENT) "development" else "final"}/$id"
}

@Serializable
data class MidiCoreEvaluationBars(val startBar: Int, val endBar: Int) {
    init { require(startBar > 0 && endBar >= startBar) { "Loops require positive inclusive bar ranges" } }
}

@Serializable
data class MidiCoreEvaluationInstrument(val role: String, val patch: String, val levelDb: Double) {
    init {
        require(patch.isNotBlank() && levelDb.isFinite()) { "Specify the same real instrument patch and finite level for A and B" }
    }
}

@Serializable
data class MidiCoreEvaluationVersions(
    val projectSchema: Int,
    val styleCatalog: Int,
    val patternCatalog: Int,
    val performanceProfileCatalog: Int,
    val javaRuntime: String,
    val kotlinVersion: String,
    /** Content hashes of the running engine and every root runtime library, without machine paths. */
    val runtimeSha256: Map<String, String>,
)

@Serializable
data class MidiCoreFrozenEvaluationCase(
    val case: MidiCoreEvaluationCase,
    val sourceSha256: String,
    val melodyNotesSha256: String,
    val suppliedProjectSha256: String,
    val authoritySha256: String,
    val settingsSha256: String,
    val files: Map<String, String>,
)

@Serializable
data class MidiCoreFrozenEvaluationSet(
    val schemaVersion: Int = 1,
    val evaluationId: String,
    val buildIdentity: String,
    val versions: MidiCoreEvaluationVersions,
    val versionsSha256: String,
    val cases: List<MidiCoreFrozenEvaluationCase>,
) {
    val missingFinalSongs: Int get() = (5 - cases.count { it.case.classification != MidiCoreEvaluationClassification.DEVELOPMENT }).coerceAtLeast(0)
    val missingUnseenSongs: Int get() = (3 - cases.count { it.case.classification == MidiCoreEvaluationClassification.FINAL_UNSEEN }).coerceAtLeast(0)
}

@Serializable
data class MidiCoreEvaluationCaseResult(
    val caseId: String,
    val status: String,
    val problem: String? = null,
    val snapshotId: String? = null,
    val candidateIds: List<String> = emptyList(),
    val generatorVersions: Map<String, String> = emptyMap(),
    val files: Map<String, String> = emptyMap(),
    val baselineAvailable: Boolean = false,
)

@Serializable
data class MidiCoreEvaluationExportReport(
    val schemaVersion: Int = 1,
    val evaluationId: String,
    val frozenSetSha256: String,
    val versionsSha256: String,
    val missingFinalSongs: Int,
    val missingUnseenSongs: Int,
    val listeningStatus: String = "UNSCORED",
    val cases: List<MidiCoreEvaluationCaseResult>,
)

internal val evaluationJson = Json { prettyPrint = true; encodeDefaults = true; ignoreUnknownKeys = false }

internal fun String.requireEvaluationId() {
    require(matches(Regex("[A-Za-z0-9][A-Za-z0-9_-]{0,99}"))) { "Evaluation IDs must be safe portable names of at most 100 characters" }
}
