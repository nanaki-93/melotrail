package app.melotrail.arrangement

import app.melotrail.harmony.HarmonySettingsDto
import kotlinx.serialization.Serializable
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.JsonPrimitive
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
            4 -> if (element["structure"]?.jsonArray?.firstOrNull() is JsonPrimitive) {
                json.decodeFromString<LegacyProjectV4Dto>(text).toProject()
            } else json.decodeFromString<ProjectV4Dto>(text).toProject()
            else -> throw IllegalArgumentException("Unsupported project version: ${element["version"]?.jsonPrimitive?.content}")
        }
    }

    /** Pure mapping: it does not touch the filesystem or infer creative settings. */
    fun migrate(project: Project): ProjectMigrationResult {
        val sourceVersion = project.version
        require(sourceVersion in 1..Project.CURRENT_VERSION) { "Unsupported project version: $sourceVersion" }
        val migrated = canonicalizeStructure(project).copy(
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
        val migrated = migration.project.let { project ->
            if (project.envelope.stageRuns.index != null) project
            else project.copy(envelope = project.envelope.copy(stageRuns = publishLegacyStageRuns(
                root, LegacyV3StageRunMapper.map(project), project.envelope.stageRuns.legacyRuns
            )))
        }
        migrated.requireValid(root)
        write(root, migrated)
        return ProjectMigrationSaveResult(migration.copy(project = migrated), root.resolve(FILE_NAME))
    }

    fun write(root: Path, project: Project) {
        val canonical = canonicalizeStructure(project)
        canonical.requireValid(root)
        require(canonical.version == Project.CURRENT_VERSION) {
            "Only schema v${Project.CURRENT_VERSION} projects can be saved; explicitly migrate readable legacy data first."
        }
        val serialized = json.encodeToString(canonical.toV4Dto())
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
        val structure: List<StructureOccurrence> = emptyList(),
        val workflow: ProjectWorkflowReferences = ProjectWorkflowReferences(),
        val envelope: ProjectV4EnvelopeDto = ProjectV4EnvelopeDto()
    )
    /** Read-only compatibility for v4 documents written before Structure owned persisted occurrences. */
    @Serializable private data class LegacyProjectV4Dto(
        val version: Int = 4,
        val name: String,
        val renderFormat: RenderFormat,
        val parts: List<PartV4Dto> = emptyList(),
        val structure: List<String> = emptyList(),
        val workflow: ProjectWorkflowReferences = ProjectWorkflowReferences(),
        val envelope: ProjectV4EnvelopeDto = ProjectV4EnvelopeDto()
    )
    @OptIn(ExperimentalSerializationApi::class)
    @Serializable private data class ProjectV4EnvelopeDto(
        val compositionSettings: CompositionSettings? = null,
        val harmony: HarmonySettingsDto? = null,
        val evolvedParts: List<EvolvedPartReference> = emptyList(),
        /** Read-only slot for v4 files written before structure moved to its canonical root field. */
        @EncodeDefault(EncodeDefault.Mode.NEVER) val structureOccurrences: List<StructureOccurrence> = emptyList(),
        val stageRuns: ProjectStageRunManifestReference = ProjectStageRunManifestReference(),
        /** Compatibility read slot for the provisional Task 002 manifest scaffold. */
        @EncodeDefault(EncodeDefault.Mode.NEVER) val manifests: LegacyProjectManifestReferences? = null,
        val arrangementAssignments: List<ArrangementAssignmentReference> = emptyList()
    )
    @Serializable private data class LegacyProjectManifestReferences(val runs: List<LegacyManifestRunReference> = emptyList())
    @Serializable private data class LegacyManifestRunReference(
        val stage: String,
        val status: String,
        val artifacts: List<WorkflowArtifactReference> = emptyList()
    )
    @OptIn(ExperimentalSerializationApi::class)
    @Serializable private data class PartV4Dto(
        val id: String,
        val sourceFile: String,
        val name: String? = null,
        val sectionType: SectionTypeId? = null,
        /** Compatibility read slot for Task 010; canonical writes never emit it. */
        @EncodeDefault(EncodeDefault.Mode.NEVER) val role: String? = null,
        val midi: MidiReferences? = null,
        val analysis: PartAnalysisReference? = null,
        val sourceAttestation: app.melotrail.commercial.SourceRightsAttestation? = null,
        val importEvidence: ImportEvidence? = null,
        val sourceKeyEvidence: SourceKeyEvidence? = null,
        val stageManifestRef: String? = null,
        val revision: Long = 1,
        val importPending: Boolean = false,
        val legacySourceOnly: Boolean = false
    )

    private fun ProjectV1Dto.toProject(compatibility: ProjectCompatibility) = Project(1, name, parts.map {
        SongPart(id = it.id, file = it.file, role = it.role, name = it.id, sectionType = SectionTypeCatalog.fromLegacyRole(it.role), analysis = it.analysis, legacySourceOnly = true)
    }, envelope = ProjectV4Envelope(structureOccurrences = legacyOccurrences(structure)), compatibility = compatibility)
    private fun ProjectV2Dto.toProject(compatibility: ProjectCompatibility) = Project(2, name, parts.map {
        SongPart(id = it.id, file = it.sourceFile, role = it.role, name = it.id, sectionType = SectionTypeCatalog.fromLegacyRole(it.role), analysis = it.analysis, midi = it.midi, sourceAttestation = it.sourceAttestation, importEvidence = it.importEvidence)
    }, renderFormat = renderFormat, envelope = ProjectV4Envelope(structureOccurrences = legacyOccurrences(structure)), compatibility = compatibility)
    private fun ProjectV3Dto.toProject(compatibility: ProjectCompatibility) = Project(3, name, parts.map {
        SongPart(id = it.id, file = it.sourceFile, role = it.role, name = it.id, sectionType = SectionTypeCatalog.fromLegacyRole(it.role), analysis = it.analysis, midi = it.midi, sourceAttestation = it.sourceAttestation, importEvidence = it.importEvidence)
    }, renderFormat = renderFormat, workflow = workflow, envelope = ProjectV4Envelope(structureOccurrences = legacyOccurrences(structure)), compatibility = compatibility)
    private fun ProjectV4Dto.toProject() = Project(4, name, parts.map {
        SongPart(id = it.id, file = it.sourceFile, role = (it.sectionType ?: SectionTypeCatalog.fromLegacyRole(it.role.orEmpty())).value, name = it.name ?: it.id, sectionType = it.sectionType ?: SectionTypeCatalog.fromLegacyRole(it.role.orEmpty()), analysis = it.analysis, midi = it.midi, sourceAttestation = it.sourceAttestation, importEvidence = it.importEvidence, sourceKeyEvidence = it.sourceKeyEvidence, stageManifestRef = it.stageManifestRef, revision = it.revision, importPending = it.importPending, legacySourceOnly = it.legacySourceOnly)
    }, renderFormat = renderFormat, workflow = workflow, envelope = envelope.toDomain().copy(
        structureOccurrences = structure.ifEmpty { envelope.structureOccurrences }
    ))
    private fun LegacyProjectV4Dto.toProject() = Project(4, name, parts.map {
        SongPart(id = it.id, file = it.sourceFile, role = (it.sectionType ?: SectionTypeCatalog.fromLegacyRole(it.role.orEmpty())).value, name = it.name ?: it.id, sectionType = it.sectionType ?: SectionTypeCatalog.fromLegacyRole(it.role.orEmpty()), analysis = it.analysis, midi = it.midi, sourceAttestation = it.sourceAttestation, importEvidence = it.importEvidence, sourceKeyEvidence = it.sourceKeyEvidence, stageManifestRef = it.stageManifestRef, revision = it.revision, importPending = it.importPending, legacySourceOnly = it.legacySourceOnly)
    }, renderFormat = renderFormat, workflow = workflow, envelope = envelope.toDomain().copy(
        structureOccurrences = envelope.structureOccurrences.ifEmpty { legacyOccurrences(structure) }
    ))
    private fun Project.toV4Dto() = ProjectV4Dto(name = name, renderFormat = requireNotNull(renderFormat), parts = parts.map {
        PartV4Dto(id = it.id, sourceFile = it.file, name = it.name, sectionType = it.sectionType, midi = it.midi, analysis = it.analysis, sourceAttestation = it.sourceAttestation, importEvidence = it.importEvidence, sourceKeyEvidence = it.sourceKeyEvidence, stageManifestRef = it.stageManifestRef, revision = it.revision, importPending = it.importPending, legacySourceOnly = it.legacySourceOnly)
    }, structure = envelope.structureOccurrences, workflow = workflow, envelope = envelope.toDto())
    private fun ProjectV4EnvelopeDto.toDomain() = ProjectV4Envelope(
        compositionSettings = compositionSettings,
        harmony = harmony?.toDomain(),
        evolvedParts = evolvedParts,
        structureOccurrences = structureOccurrences,
        stageRuns = stageRuns.copy(legacyRuns = manifests?.runs.orEmpty().map { LegacyManifestRunInput(it.stage, it.status, it.artifacts) }),
        arrangementAssignments = arrangementAssignments
    )
    private fun ProjectV4Envelope.toDto() = ProjectV4EnvelopeDto(
        compositionSettings = compositionSettings,
        harmony = harmony?.let(HarmonySettingsDto::fromDomain),
        evolvedParts = evolvedParts,
        stageRuns = stageRuns,
        arrangementAssignments = arrangementAssignments
    )

    /** The only old-list adapter.  It runs while reading/migrating a legacy document, never in planning. */
    private fun legacyOccurrences(partIds: List<String>): List<StructureOccurrence> {
        val counts = mutableMapOf<String, Int>()
        return partIds.map { partId ->
            val ordinal = (counts[partId] ?: 0) + 1
            counts[partId] = ordinal
            StructureOccurrence(id = legacyOccurrenceId(partId, ordinal), partId = partId, label = "$partId$ordinal")
        }
    }

    private fun legacyOccurrenceId(partId: String, ordinal: Int): String {
        val direct = "occ-$partId-$ordinal"
        return if (SAFE_OCCURRENCE_ID.matches(direct)) direct
        else "occ-${sha256Hex("$partId:$ordinal").take(32)}"
    }

    private fun canonicalizeStructure(project: Project): Project =
        if (project.envelope.structureOccurrences.isNotEmpty() || project.structure.isEmpty()) project
        else project.copy(envelope = project.envelope.copy(structureOccurrences = legacyOccurrences(project.structure)), structure = emptyList())

    private val SAFE_OCCURRENCE_ID = Regex("[A-Za-z0-9_-]{1,80}")

    private fun publishLegacyStageRuns(
        root: Path,
        inputs: List<LegacyStageRunInput>,
        provisionalRuns: List<LegacyManifestRunInput>
    ): ProjectStageRunManifestReference {
        val store = StageRunStore()
        var reference = store.initialize(root)
        inputs.forEach { input ->
            val path = runCatching { root.resolve(input.artifactPath).normalize() }.getOrNull() ?: return@forEach
            if (!path.startsWith(root.toAbsolutePath().normalize()) || !Files.isRegularFile(path)) return@forEach
            val artifact = runCatching { artifactRef(root.toAbsolutePath().normalize(), input.artifactPath) }.getOrNull() ?: return@forEach
            val runId = "legacy-${sha256Hex("${input.stage.name}:${input.subject.key()}:${input.artifactPath}").take(24)}"
            val record = StageRunRecord(
                runId = runId,
                stage = input.stage,
                subject = input.subject,
                status = StageRunStatus.COMPLETED,
                processor = ProcessorIdentity("legacy-v3", "1"),
                createdAt = LEGACY_RUN_TIMESTAMP,
                finishedAt = LEGACY_RUN_TIMESTAMP,
                outputArtifacts = listOf(artifact),
                selections = if (input.selected) listOf(StageOutputSelection(artifact, LEGACY_RUN_TIMESTAMP)) else emptyList()
            )
            reference = store.append(root, record)
        }
        provisionalRuns.forEachIndexed { index, input ->
            val stage = legacyStage(input.stage) ?: return@forEachIndexed
            val status = legacyStatus(input.status) ?: return@forEachIndexed
            val artifacts = if (status == StageRunStatus.COMPLETED) input.artifacts.mapNotNull { legacy ->
                runCatching { artifactRef(root.toAbsolutePath().normalize(), legacy.file) }.getOrNull()
            } else emptyList()
            if (status == StageRunStatus.COMPLETED && (artifacts.isEmpty() || artifacts.size != input.artifacts.size)) return@forEachIndexed
            val runId = "legacy-manifest-${index}-${sha256Hex("${input.stage}:${input.status}").take(16)}"
            val record = StageRunRecord(
                runId = runId,
                stage = stage,
                subject = StageSubject.Project,
                status = status,
                processor = ProcessorIdentity("legacy-v4", "1"),
                createdAt = LEGACY_RUN_TIMESTAMP,
                startedAt = if (status == StageRunStatus.PROCESSING) LEGACY_RUN_TIMESTAMP else null,
                finishedAt = if (status == StageRunStatus.PENDING || status == StageRunStatus.PROCESSING) null else LEGACY_RUN_TIMESTAMP,
                outputArtifacts = artifacts,
                failure = if (status == StageRunStatus.FAILED) SafeFailure(SafeFailureCode.INTERRUPTED, "Review historical run evidence and retry.") else null
            )
            reference = store.append(root, record)
        }
        return reference
    }

    private fun legacyStage(value: String): StageId? = when (value.lowercase()) {
        "structure", "structured" -> StageId.STRUCTURED
        "cohesion" -> StageId.COHESION
        "arrangement", "arranged" -> StageId.ARRANGED
        "generate", "generated" -> StageId.GENERATED
        "render", "rendered" -> StageId.RENDERED
        "mix", "mixed" -> StageId.MIXED
        "master", "mastered" -> StageId.MASTERED
        "export", "exported" -> StageId.EXPORTED
        else -> null
    }

    private fun legacyStatus(value: String): StageRunStatus? = runCatching { StageRunStatus.valueOf(value.uppercase()) }.getOrNull()

    private fun compatibility(element: kotlinx.serialization.json.JsonObject, known: Set<String>, sourceVersion: Int): ProjectCompatibility {
        val unknown = element.keys - known
        return ProjectCompatibility(sourceVersion, unknown.sorted().map { "Legacy v$sourceVersion field '$it' was not understood and was retained only as a migration warning." })
    }

    private val V1_FIELDS = setOf("version", "name", "parts", "structure")
    private val V2_FIELDS = V1_FIELDS + setOf("renderFormat")
    private val V3_FIELDS = V2_FIELDS + setOf("workflow")
    private const val LEGACY_RUN_TIMESTAMP = "1970-01-01T00:00:00Z"
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
