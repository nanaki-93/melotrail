package app.melotrail.arrangement

import app.melotrail.harmony.HarmonySettingsDto
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.UUID

/**
 * Versioned project-file boundary. V1-v3 remain readable without changing
 * metadata or files. V4 is the only explicit migration/save target.
 *
 * Remove the v1-v3 readers, fixtures, and README support note together only
 * after the declared project-format support window has ended.
 */
object ProjectStore {
    const val FILE_NAME = "project.json"

    private val json = Json { prettyPrint = true; encodeDefaults = true; ignoreUnknownKeys = false }
    private val legacyJson = Json { ignoreUnknownKeys = true }

    fun create(root: Path, name: String, renderFormat: RenderFormat): Project {
        val project = Project(version = Project.CURRENT_VERSION, name = name, renderFormat = renderFormat)
        write(root, project)
        return project
    }

    fun read(root: Path): Project {
        val text = Files.readString(root.resolve(FILE_NAME), StandardCharsets.UTF_8)
        val element = json.parseToJsonElement(text).jsonObject
        return when (element["version"]?.jsonPrimitive?.content?.toIntOrNull() ?: 1) {
            1 -> legacyJson.decodeFromString<ProjectV1Dto>(text).toProject(compatibility(element, V1_FIELDS, 1))
            2 -> legacyJson.decodeFromString<ProjectV2Dto>(text).toProject(compatibility(element, V2_FIELDS, 2))
            3 -> legacyJson.decodeFromString<ProjectV3Dto>(text).toProject(compatibility(element, V3_FIELDS, 3))
            4 -> json.decodeFromString<ProjectV4Dto>(text).toProject()
            else -> throw IllegalArgumentException("Unsupported project version: ${element["version"]?.jsonPrimitive?.content}")
        }
    }

    /** Pure mapping: it does not touch the filesystem or infer creative settings. */
    fun migrate(project: Project): ProjectMigrationResult {
        val sourceVersion = project.version
        require(sourceVersion in 1..Project.CURRENT_VERSION) { "Unsupported project version: $sourceVersion" }
        val migrated = project.copy(
            version = Project.CURRENT_VERSION,
            renderFormat = project.renderFormat ?: RenderFormat(),
            compatibility = ProjectCompatibility(Project.CURRENT_VERSION)
        )
        val warnings = project.compatibility.warnings + buildList {
            if (sourceVersion < Project.CURRENT_VERSION) add("Project schema v$sourceVersion is readable legacy data; save explicitly to publish v4.")
            if (sourceVersion == 1) add("Legacy source-only parts are preserved without being treated as MIDI; import validated MIDI before MIDI-first processing.")
        }
        return ProjectMigrationResult(migrated, sourceVersion, warnings, migrated.envelope.setupRequirements())
    }

    fun readMigration(root: Path): ProjectMigrationResult = migrate(read(root))

    /** Explicit, validated publication. Opening a project never invokes this method. */
    fun migrateAndSave(root: Path): ProjectMigrationSaveResult {
        val migration = readMigration(root)
        val migrated = migration.project
        migrated.requireValid(root)
        write(root, migrated)
        return ProjectMigrationSaveResult(migration, root.resolve(FILE_NAME))
    }

    fun write(root: Path, project: Project) {
        project.requireValid(root)
        require(project.version == Project.CURRENT_VERSION) {
            "Only schema v${Project.CURRENT_VERSION} projects can be saved; explicitly migrate readable legacy data first."
        }
        val serialized = json.encodeToString(project.toV4Dto())
        json.decodeFromString<ProjectV4Dto>(serialized).toProject().requireValid(root)
        atomicWrite(root.resolve(FILE_NAME), serialized)
    }

    /**
     * Opens never rewrite metadata. Call this explicit atomic boundary only
     * after the caller has presented the legacy migration state to the user.
     */
    private fun atomicWrite(path: Path, text: String) {
        Files.createDirectories(checkNotNull(path.parent))
        val temporary = path.resolveSibling(".${path.fileName}.save-${UUID.randomUUID()}.tmp")
        var published = false
        Files.writeString(temporary, text, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
        try {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            published = true
        } catch (error: Exception) {
            val recovery = path.resolveSibling(".${path.fileName}.recovery-${UUID.randomUUID()}.json")
            val evidence = runCatching { Files.move(temporary, recovery, StandardCopyOption.ATOMIC_MOVE); recovery }
                .getOrElse { temporary }
            throw ProjectSaveException(path, evidence, error)
        } finally {
            if (published) Files.deleteIfExists(temporary)
        }
    }

    @Serializable private data class ProjectV1Dto(val version: Int = 1, val name: String, val parts: List<PartV1Dto> = emptyList(), val structure: List<String> = emptyList())
    @Serializable private data class PartV1Dto(val id: String, val file: String, val role: String = "", val analysis: PartAnalysisReference? = null)
    @Serializable private data class ProjectV2Dto(val version: Int = 2, val name: String, val renderFormat: RenderFormat, val parts: List<PartV2Dto> = emptyList(), val structure: List<String> = emptyList())
    @Serializable private data class PartV2Dto(
        val id: String,
        val role: String = "",
        val sourceFile: String,
        val midi: MidiReferences,
        val analysis: PartAnalysisReference? = null,
        val sourceAttestation: app.melotrail.commercial.SourceRightsAttestation? = null,
        val importEvidence: ImportEvidence? = null
    )
    @Serializable private data class ProjectV3Dto(val version: Int = 3, val name: String, val renderFormat: RenderFormat, val parts: List<PartV2Dto> = emptyList(), val structure: List<String> = emptyList(), val workflow: ProjectWorkflowReferences = ProjectWorkflowReferences())
    @Serializable private data class ProjectV4Dto(
        val version: Int = 4,
        val name: String,
        val renderFormat: RenderFormat,
        val parts: List<PartV4Dto> = emptyList(),
        val structure: List<String> = emptyList(),
        val workflow: ProjectWorkflowReferences = ProjectWorkflowReferences(),
        val envelope: ProjectV4EnvelopeDto = ProjectV4EnvelopeDto()
    )
    @Serializable private data class ProjectV4EnvelopeDto(
        val compositionSettings: CompositionSettings? = null,
        val harmony: HarmonySettingsDto? = null,
        val evolvedParts: List<EvolvedPartReference> = emptyList(),
        val structureOccurrences: List<StructureOccurrence> = emptyList(),
        val manifests: ProjectManifestReferences = ProjectManifestReferences(),
        val arrangementAssignments: List<ArrangementAssignmentReference> = emptyList()
    )
    @Serializable private data class PartV4Dto(
        val id: String,
        val role: String = "",
        val sourceFile: String,
        val midi: MidiReferences? = null,
        val analysis: PartAnalysisReference? = null,
        val sourceAttestation: app.melotrail.commercial.SourceRightsAttestation? = null,
        val importEvidence: ImportEvidence? = null,
        val legacySourceOnly: Boolean = false
    )

    private fun ProjectV1Dto.toProject(compatibility: ProjectCompatibility) = Project(1, name, parts.map { Part(it.id, it.file, it.role, it.analysis, legacySourceOnly = true) }, structure, compatibility = compatibility)
    private fun ProjectV2Dto.toProject(compatibility: ProjectCompatibility) = Project(2, name, parts.map { Part(it.id, it.sourceFile, it.role, it.analysis, it.midi, it.sourceAttestation, it.importEvidence) }, structure, renderFormat, compatibility = compatibility)
    private fun ProjectV3Dto.toProject(compatibility: ProjectCompatibility) = Project(3, name, parts.map { Part(it.id, it.sourceFile, it.role, it.analysis, it.midi, it.sourceAttestation, it.importEvidence) }, structure, renderFormat, workflow, compatibility = compatibility)
    private fun ProjectV4Dto.toProject() = Project(4, name, parts.map { Part(it.id, it.sourceFile, it.role, it.analysis, it.midi, it.sourceAttestation, it.importEvidence, it.legacySourceOnly) }, structure, renderFormat, workflow, envelope.toDomain())
    private fun Project.toV4Dto() = ProjectV4Dto(name = name, renderFormat = requireNotNull(renderFormat), parts = parts.map { PartV4Dto(it.id, it.role, it.file, it.midi, it.analysis, it.sourceAttestation, it.importEvidence, it.legacySourceOnly) }, structure = structure, workflow = workflow, envelope = envelope.toDto())
    private fun ProjectV4EnvelopeDto.toDomain() = ProjectV4Envelope(
        compositionSettings, harmony?.toDomain(), evolvedParts, structureOccurrences, manifests, arrangementAssignments
    )
    private fun ProjectV4Envelope.toDto() = ProjectV4EnvelopeDto(
        compositionSettings, harmony?.let(HarmonySettingsDto::fromDomain), evolvedParts,
        structureOccurrences, manifests, arrangementAssignments
    )

    private fun compatibility(element: kotlinx.serialization.json.JsonObject, known: Set<String>, sourceVersion: Int): ProjectCompatibility {
        val unknown = element.keys - known
        return ProjectCompatibility(sourceVersion, unknown.sorted().map { "Legacy v$sourceVersion field '$it' was not understood and was retained only as a migration warning." })
    }

    private val V1_FIELDS = setOf("version", "name", "parts", "structure")
    private val V2_FIELDS = V1_FIELDS + setOf("renderFormat")
    private val V3_FIELDS = V2_FIELDS + setOf("workflow")
}

data class ProjectMigrationResult(
    val project: Project,
    val sourceVersion: Int,
    val warnings: List<String>,
    val setupRequirements: Set<ProjectSetupRequirement>
)

data class ProjectMigrationSaveResult(val migration: ProjectMigrationResult, val projectFile: Path)

class ProjectSaveException(val projectFile: Path, val recoveryEvidence: Path, cause: Throwable) : IllegalStateException(
    "Atomic project save is not supported for '$projectFile'. Recovery evidence was retained at '$recoveryEvidence'.", cause
)
