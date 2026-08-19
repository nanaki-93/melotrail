package app.melotrail.arrangement

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.UUID

private val STAGE_RUN_JSON = Json { prettyPrint = true; encodeDefaults = true; ignoreUnknownKeys = false }

/**
 * Canonical local store for immutable stage-run records. Each record is written
 * once under [RUNS_DIRECTORY]; the small hash-bound index is atomically
 * replaced. An orphan record after an interrupted index publish is recoverable
 * evidence, not a visible run, and [recoverableOrphans] exposes it for a later
 * recovery UI without deleting anything. There is deliberately no automatic
 * compaction: an explicit future maintenance action may replace the index, but
 * must retain indexed records and inspect orphans before deleting any evidence.
 */
class StageRunStore(private val publisher: AtomicStageRunPublisher = FileAtomicStageRunPublisher) {
    fun initialize(projectRoot: Path): ProjectStageRunManifestReference {
        val root = normalizedRoot(projectRoot)
        val index = root.resolve(INDEX_FILE)
        if (!Files.exists(index)) publisher.write(index, STAGE_RUN_JSON.encodeToString(StageRunIndex()))
        return ProjectStageRunManifestReference(artifactRef(root, INDEX_FILE))
    }

    fun append(projectRoot: Path, record: StageRunRecord): ProjectStageRunManifestReference {
        val root = normalizedRoot(projectRoot)
        StageRunValidator.requireValid(root, record)
        val recordPath = runPath(record.runId)
        val absoluteRecord = root.resolve(recordPath)
        require(!Files.exists(absoluteRecord)) { "Stage run '${record.runId}' already exists" }
        publisher.writeNew(absoluteRecord, STAGE_RUN_JSON.encodeToString(record))

        val existing = readIndex(root)
        val entry = StageRunIndexEntry(record.runId, artifactRef(root, recordPath))
        val next = StageRunIndex(existing.schemaVersion, existing.runs + entry)
        publisher.write(root.resolve(INDEX_FILE), STAGE_RUN_JSON.encodeToString(next))
        return ProjectStageRunManifestReference(artifactRef(root, INDEX_FILE))
    }

    fun read(projectRoot: Path, reference: ProjectStageRunManifestReference): List<StageRunRecord> {
        reference.requireCanonical()
        val root = normalizedRoot(projectRoot)
        val indexReference = reference.index ?: return emptyList()
        StageRunValidator.requireArtifact(root, indexReference, "Stage-run index")
        val index = decodeIndex(root.resolve(indexReference.path))
        return index.runs.map { entry ->
            require(entry.record.path == runPath(entry.runId)) { "Stage-run record path is not canonical" }
            StageRunValidator.requireArtifact(root, entry.record, "Stage-run record")
            val record = decodeRecord(root.resolve(entry.record.path))
            require(record.runId == entry.runId) { "Stage-run index does not match its record" }
            StageRunValidator.requireValid(root, record)
            record
        }
    }

    fun summaries(projectRoot: Path, reference: ProjectStageRunManifestReference): List<StageRunSummary> =
        read(projectRoot, reference).map { StageRunSummary(it.runId, it.stage, it.subject, it.status, it.outputArtifacts.size, it.failure?.code) }

    fun current(projectRoot: Path, reference: ProjectStageRunManifestReference, stage: StageId, subject: StageSubject): StageRunRecord? =
        read(projectRoot, reference).lastOrNull { it.stage == stage && it.subject == subject }

    /** The sole selectable output boundary: only an explicit completed selection is returned. */
    fun selectedOutput(projectRoot: Path, reference: ProjectStageRunManifestReference, subject: StageSubject): StageRunSelectedOutput? =
        read(projectRoot, reference)
            .asReversed()
            .firstNotNullOfOrNull { record ->
                record.takeIf { it.subject == subject && it.status == StageRunStatus.COMPLETED }
                    ?.selections?.lastOrNull()?.let { StageRunSelectedOutput(record, it.artifact) }
            }

    /** Exact upstream artifact lineage for one selected or otherwise completed artifact. */
    fun lineage(projectRoot: Path, reference: ProjectStageRunManifestReference, artifact: ArtifactRef): List<StageRunRecord> {
        val records = read(projectRoot, reference)
        val byOutput = records.flatMap { record -> record.outputArtifacts.map { it to record } }.toMap()
        val visited = linkedSetOf<String>()
        fun visit(target: ArtifactRef) {
            val record = byOutput[target] ?: return
            if (!visited.add(record.runId)) return
            record.inputArtifacts.forEach(::visit)
        }
        visit(artifact)
        return records.filter { it.runId in visited }
    }

    /** Returns ignored immutable records that were published before an index write failed. */
    fun recoverableOrphans(projectRoot: Path, reference: ProjectStageRunManifestReference): List<ArtifactRef> {
        val root = normalizedRoot(projectRoot)
        val indexed = read(projectRoot, reference).mapTo(mutableSetOf()) { runPath(it.runId) }
        val directory = root.resolve(RUNS_DIRECTORY)
        if (!Files.isDirectory(directory)) return emptyList()
        Files.list(directory).use { stream ->
            return stream.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".json") }
                .map { root.relativize(it).toString().replace('\\', '/') }
                .filter { it !in indexed }
                .sorted()
                .map { artifactRef(root, it) }
                .toList()
        }
    }

    private fun readIndex(root: Path): StageRunIndex {
        val path = root.resolve(INDEX_FILE)
        if (!Files.exists(path)) return StageRunIndex()
        return decodeIndex(path)
    }

    private fun decodeIndex(path: Path): StageRunIndex = try {
        STAGE_RUN_JSON.decodeFromString(Files.readString(path, StandardCharsets.UTF_8))
    } catch (error: Exception) {
        throw IllegalArgumentException("Stage-run index is invalid", error)
    }

    private fun decodeRecord(path: Path): StageRunRecord = try {
        STAGE_RUN_JSON.decodeFromString(Files.readString(path, StandardCharsets.UTF_8))
    } catch (error: Exception) {
        throw IllegalArgumentException("Stage-run record is invalid", error)
    }

    private fun normalizedRoot(projectRoot: Path): Path {
        val root = projectRoot.toAbsolutePath().normalize()
        require(Files.isDirectory(root)) { "Project root is missing" }
        return root
    }

    companion object {
        const val DIRECTORY = "workflow-runs"
        const val RUNS_DIRECTORY = "$DIRECTORY/runs"
        const val INDEX_FILE = "$DIRECTORY/index.json"
        fun runPath(runId: String): String {
            require(Regex("[A-Za-z0-9][A-Za-z0-9_-]{0,79}").matches(runId)) { "Stage run ID is invalid" }
            return "$RUNS_DIRECTORY/$runId.json"
        }
    }
}

data class StageRunSelectedOutput(val record: StageRunRecord, val artifact: ArtifactRef)

object StageRunValidator {
    fun requireValid(projectRoot: Path, record: StageRunRecord) {
        record.inputArtifacts.forEach { requireArtifact(projectRoot, it, "Stage input") }
        record.reportArtifacts.forEach { requireArtifact(projectRoot, it, "Stage report") }
        if (record.status == StageRunStatus.COMPLETED) {
            record.outputArtifacts.forEach { requireArtifact(projectRoot, it, "Completed stage output") }
        }
    }

    fun requireArtifact(projectRoot: Path, artifact: ArtifactRef, label: String) {
        val root = projectRoot.toAbsolutePath().normalize()
        val rootReal = root.toRealPath()
        val path = root.resolve(artifact.path).normalize()
        require(path.startsWith(root) && Files.isRegularFile(path)) { "$label is missing: ${artifact.path}" }
        require(path.toRealPath().startsWith(rootReal)) { "$label path escapes the project root: ${artifact.path}" }
        require(sha256(path) == artifact.sha256) { "$label fingerprint does not match: ${artifact.path}" }
    }
}

interface AtomicStageRunPublisher {
    fun write(path: Path, text: String)
    fun writeNew(path: Path, text: String)
}

object FileAtomicStageRunPublisher : AtomicStageRunPublisher {
    override fun write(path: Path, text: String) = publish(path, text, replace = true)
    override fun writeNew(path: Path, text: String) = publish(path, text, replace = false)

    private fun publish(path: Path, text: String, replace: Boolean) {
        Files.createDirectories(checkNotNull(path.parent))
        val temporary = path.resolveSibling(".${path.fileName}.save-${UUID.randomUUID()}.tmp")
        Files.writeString(temporary, text, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW)
        try {
            if (replace) Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            else Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }
}

internal fun artifactRef(root: Path, relative: String): ArtifactRef = ArtifactRef(relative, sha256(root.resolve(relative)))

internal fun sha256(path: Path): String {
    val digest = MessageDigest.getInstance("SHA-256")
    Files.newInputStream(path).use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
