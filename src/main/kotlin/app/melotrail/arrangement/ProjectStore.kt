package app.melotrail.arrangement

import app.melotrail.harmony.HarmonySettingsDto
import app.melotrail.preparation.SourceTimingEvidenceReference
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

/** Canonical schema-v4 project-file boundary. Older project formats are unsupported. */
object ProjectStore {
    const val FILE_NAME = "project.json"

    private val json = Json { prettyPrint = true; encodeDefaults = true; ignoreUnknownKeys = false }

    fun create(root: Path, name: String, renderFormat: RenderFormat): Project {
        val project = Project(name = name, renderFormat = renderFormat)
        write(root, project)
        return project
    }

    fun read(root: Path): Project {
        val text = Files.readString(root.resolve(FILE_NAME), StandardCharsets.UTF_8)
        val element = json.parseToJsonElement(text).jsonObject
        val version = element["version"]?.jsonPrimitive?.content?.toIntOrNull()
            ?: throw IllegalArgumentException("Project version is required")
        require(version == Project.CURRENT_VERSION) {
            "Unsupported project version: $version; only schema v${Project.CURRENT_VERSION} is supported"
        }
        return json.decodeFromString<ProjectV4Dto>(text).toProject()
    }

    fun write(root: Path, project: Project) {
        project.requireValid(root)
        require(project.version == Project.CURRENT_VERSION) {
            "Only schema v${Project.CURRENT_VERSION} projects can be saved"
        }
        val serialized = json.encodeToString(project.toV4Dto())
        json.decodeFromString<ProjectV4Dto>(serialized).toProject().requireValid(root)
        atomicWrite(root.resolve(FILE_NAME), serialized)
    }

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

    @Serializable
    private data class ProjectV4Dto(
        val version: Int = Project.CURRENT_VERSION,
        val name: String,
        val renderFormat: RenderFormat,
        val parts: List<PartV4Dto> = emptyList(),
        val structure: List<StructureOccurrence> = emptyList(),
        /** Required so the superseded v4 workflow shape cannot be opened by defaulting it. */
        val workflow: ProjectWorkflowReferences,
        val envelope: ProjectV4EnvelopeDto = ProjectV4EnvelopeDto()
    )

    @Serializable
    private data class ProjectV4EnvelopeDto(
        val compositionSettings: CompositionSettings? = null,
        val harmony: HarmonySettingsDto? = null,
        val evolvedParts: List<EvolvedPartReference> = emptyList(),
        val stageRuns: ProjectStageRunManifestReference = ProjectStageRunManifestReference(),
        val arrangementAssignments: List<ArrangementAssignmentReference> = emptyList()
    )

    @Serializable
    private data class PartV4Dto(
        val id: String,
        val sourceFile: String,
        val name: String,
        val sectionType: SectionTypeId,
        val midi: MidiReferences? = null,
        val analysis: PartAnalysisReference? = null,
        val sourceAttestation: app.melotrail.commercial.SourceRightsAttestation? = null,
        val importEvidence: ImportEvidence? = null,
        val sourceKeyEvidence: SourceKeyEvidence? = null,
        val sourceTimingEvidence: SourceTimingEvidenceReference? = null,
        val stageManifestRef: String? = null,
        val revision: Long = 1,
        val importPending: Boolean = false
    )

    private fun ProjectV4Dto.toProject() = Project(
        version = version,
        name = name,
        parts = parts.map {
            SongPart(
                id = it.id,
                file = it.sourceFile,
                name = it.name,
                sectionType = it.sectionType,
                analysis = it.analysis,
                midi = it.midi,
                sourceAttestation = it.sourceAttestation,
                importEvidence = it.importEvidence,
                sourceKeyEvidence = it.sourceKeyEvidence,
                sourceTimingEvidence = it.sourceTimingEvidence,
                stageManifestRef = it.stageManifestRef,
                revision = it.revision,
                importPending = it.importPending
            )
        },
        renderFormat = renderFormat,
        workflow = workflow,
        envelope = envelope.toDomain().copy(structureOccurrences = structure)
    )

    private fun Project.toV4Dto() = ProjectV4Dto(
        name = name,
        renderFormat = requireNotNull(renderFormat),
        parts = parts.map {
            PartV4Dto(
                id = it.id,
                sourceFile = it.file,
                name = it.name,
                sectionType = it.sectionType,
                midi = it.midi,
                analysis = it.analysis,
                sourceAttestation = it.sourceAttestation,
                importEvidence = it.importEvidence,
                sourceKeyEvidence = it.sourceKeyEvidence,
                sourceTimingEvidence = it.sourceTimingEvidence,
                stageManifestRef = it.stageManifestRef,
                revision = it.revision,
                importPending = it.importPending
            )
        },
        structure = envelope.structureOccurrences,
        workflow = workflow,
        envelope = envelope.toDto()
    )

    private fun ProjectV4EnvelopeDto.toDomain() = ProjectV4Envelope(
        compositionSettings = compositionSettings,
        harmony = harmony?.toDomain(),
        evolvedParts = evolvedParts,
        stageRuns = stageRuns,
        arrangementAssignments = arrangementAssignments
    )

    private fun ProjectV4Envelope.toDto() = ProjectV4EnvelopeDto(
        compositionSettings = compositionSettings,
        harmony = harmony?.let(HarmonySettingsDto::fromDomain),
        evolvedParts = evolvedParts,
        stageRuns = stageRuns,
        arrangementAssignments = arrangementAssignments
    )
}

class ProjectSaveException(val projectFile: Path, val recoveryEvidence: Path, cause: Throwable) : IllegalStateException(
    "Atomic project save is not supported for '$projectFile'. Recovery evidence was retained at '$recoveryEvidence'.", cause
)
