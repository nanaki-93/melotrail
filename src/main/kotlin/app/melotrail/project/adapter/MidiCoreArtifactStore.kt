package app.melotrail.project.adapter

import app.melotrail.project.CandidateRole
import app.melotrail.project.ExportedFileKind
import app.melotrail.project.MidiCoreProject
import app.melotrail.project.MidiCoreProjectSchema
import app.melotrail.project.ProjectArtifact
import app.melotrail.project.ProjectRelativePath
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.UUID

/**
 * Filesystem adapter for the MIDI Core project layout. Domain records carry only
 * portable relative paths and digests; this adapter owns path resolution,
 * immutable publication, digest verification, and crash-safe project JSON.
 */
class MidiCoreArtifactStore(
    private val atomicWriteObserver: AtomicWriteObserver = AtomicWriteObserver.NONE,
) {
    fun initialize(projectRoot: Path): Path {
        val root = projectRoot.toAbsolutePath().normalize()
        if (Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            require(Files.isDirectory(root)) { "Project root is not a directory: $root" }
        } else {
            Files.createDirectories(root)
        }
        root.toRealPath()
        return root
    }

    fun publishSource(projectRoot: Path, source: Path): ProjectArtifact =
        publishImmutable(projectRoot, SOURCE_MIDI, source)

    fun publishImportReport(projectRoot: Path, reportJson: String): ProjectArtifact =
        publishImmutable(projectRoot, IMPORT_REPORT, reportJson.toByteArray(StandardCharsets.UTF_8))

    /**
     * Removes artifacts created for an import that never became part of the project document.
     * Bound source records are explicitly protected so imported source bytes are never deleted.
     */
    fun discardUnboundImportArtifacts(projectRoot: Path, artifacts: List<ProjectArtifact>) {
        require(artifacts.isNotEmpty()) { "At least one unbound import artifact is required" }
        require(artifacts.map(ProjectArtifact::path).toSet().size == artifacts.size) { "Unbound import artifacts must be unique" }
        require(artifacts.all { it.path == SOURCE_MIDI || it.path == IMPORT_REPORT }) {
            "Only canonical import artifacts may be discarded"
        }
        val root = existingRoot(projectRoot)
        val projectFile = root.resolve(PROJECT_FILE)
        if (Files.exists(projectFile, LinkOption.NOFOLLOW_LINKS)) {
            require(Files.isRegularFile(projectFile, LinkOption.NOFOLLOW_LINKS)) { "Project file is not a regular file" }
            val project = MidiCoreProjectSchema.decode(Files.readString(projectFile, StandardCharsets.UTF_8))
            require(project.sourceMidi == null) { "Bound source artifacts cannot be discarded" }
        }
        artifacts.forEach { artifact ->
            val path = resolve(root, artifact.path)
            if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
                verify(root, artifact)
                Files.delete(path)
            }
        }
    }

    fun publishCandidateMidi(
        projectRoot: Path,
        role: CandidateRole,
        occurrenceId: String,
        candidateId: String,
        source: Path,
    ): ProjectArtifact = publishImmutable(projectRoot, candidateMidiPath(role, occurrenceId, candidateId), source)

    fun publishCandidateReport(projectRoot: Path, candidateId: String, reportJson: String): ProjectArtifact =
        publishImmutable(
            projectRoot,
            candidateReportPath(candidateId),
            reportJson.toByteArray(StandardCharsets.UTF_8),
        )

    fun publishExportFile(
        projectRoot: Path,
        snapshotId: String,
        kind: ExportedFileKind,
        source: Path,
    ): ProjectArtifact = publishImmutable(projectRoot, exportFilePath(snapshotId, kind), source)

    fun publishImmutable(projectRoot: Path, relativePath: ProjectRelativePath, source: Path): ProjectArtifact =
        Files.newInputStream(source).use { input ->
            publishImmutable(projectRoot, relativePath) { temporary, digest ->
                Files.newOutputStream(temporary, StandardOpenOption.TRUNCATE_EXISTING).use { output ->
                    copyAndDigest(input, output::write, digest)
                }
            }
        }

    fun publishImmutable(projectRoot: Path, relativePath: ProjectRelativePath, bytes: ByteArray): ProjectArtifact =
        publishImmutable(projectRoot, relativePath) { temporary, digest ->
            Files.newOutputStream(temporary, StandardOpenOption.TRUNCATE_EXISTING).use { output ->
                digest.update(bytes)
                output.write(bytes)
            }
        }

    fun verify(projectRoot: Path, artifact: ProjectArtifact): Path {
        val root = existingRoot(projectRoot)
        val path = resolve(root, artifact.path)
        require(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) { "Artifact is missing: ${artifact.path.value}" }
        require(path.toRealPath().startsWith(root.toRealPath())) { "Artifact escapes the project root: ${artifact.path.value}" }
        require(sha256(path) == artifact.sha256) { "Artifact digest does not match: ${artifact.path.value}" }
        return path
    }

    fun saveProject(projectRoot: Path, project: MidiCoreProject): Path {
        val root = existingRoot(projectRoot)
        verifyProjectArtifacts(root, project)
        val serialized = MidiCoreProjectSchema.encode(project)
        require(MidiCoreProjectSchema.decode(serialized) == project) { "Project schema validation changed the project" }
        val target = root.resolve(PROJECT_FILE)
        atomicReplace(root, target, serialized.toByteArray(StandardCharsets.UTF_8))
        return target
    }

    fun openProject(projectRoot: Path): MidiCoreProject {
        val root = existingRoot(projectRoot)
        val projectFile = root.resolve(PROJECT_FILE)
        require(Files.isRegularFile(projectFile, LinkOption.NOFOLLOW_LINKS)) { "Project file is missing" }
        require(projectFile.toRealPath().startsWith(root.toRealPath())) { "Project file escapes the project root" }
        val project = MidiCoreProjectSchema.decode(Files.readString(projectFile, StandardCharsets.UTF_8))
        verifyProjectArtifacts(root, project)
        return project
    }

    fun verifyProjectArtifacts(projectRoot: Path, project: MidiCoreProject) {
        val root = existingRoot(projectRoot)
        project.sourceMidi?.let { source ->
            require(source.original.path == SOURCE_MIDI) { "Source MIDI path is not canonical" }
            require(source.importReport.path == IMPORT_REPORT) { "Import report path is not canonical" }
            verify(root, source.original)
            verify(root, source.importReport)
        }
        project.candidates.forEach { candidate ->
            require(candidate.midi.path == candidateMidiPath(candidate.role, candidate.occurrenceId, candidate.id)) {
                "Candidate MIDI path is not canonical: ${candidate.id}"
            }
            require(candidate.validationReport.path == candidateReportPath(candidate.id)) {
                "Candidate report path is not canonical: ${candidate.id}"
            }
            verify(root, candidate.midi)
            verify(root, candidate.validationReport)
        }
        project.exportSnapshots.forEach { snapshot ->
            snapshot.files.forEach { file ->
                require(file.artifact.path == exportFilePath(snapshot.id, file.kind)) {
                    "Export file path is not canonical: ${snapshot.id}/${file.kind}"
                }
                verify(root, file.artifact)
            }
        }
    }

    private fun publishImmutable(
        projectRoot: Path,
        relativePath: ProjectRelativePath,
        write: (Path, MessageDigest) -> Unit,
    ): ProjectArtifact {
        val root = existingRoot(projectRoot)
        val target = resolve(root, relativePath)
        val parent = prepareParent(root, target)
        val temporary = Files.createTempFile(parent, ".${target.fileName}.publish-", ".tmp")
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            write(temporary, digest)
            val artifact = ProjectArtifact(relativePath, digest.hex())
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                requireExistingMatches(root, target, artifact)
                return artifact
            }
            try {
                atomicMoveNew(temporary, target)
            } catch (collision: FileAlreadyExistsException) {
                requireExistingMatches(root, target, artifact)
            }
            verify(root, artifact)
            return artifact
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun requireExistingMatches(root: Path, target: Path, artifact: ProjectArtifact) {
        try {
            verify(root, artifact)
        } catch (error: Exception) {
            throw MidiCoreArtifactCollisionException(target, error)
        }
    }

    private fun atomicReplace(root: Path, target: Path, bytes: ByteArray) {
        val parent = prepareParent(root, target)
        val temporary = Files.createTempFile(parent, ".${target.fileName}.save-", ".tmp")
        var published = false
        try {
            Files.write(temporary, bytes, StandardOpenOption.TRUNCATE_EXISTING)
            atomicWriteObserver.beforePublish(temporary, target)
            atomicMoveReplace(temporary, target)
            published = true
        } catch (error: Exception) {
            val recovery = retainRecoveryEvidence(temporary, target)
            throw MidiCoreProjectSaveException(target, recovery, error)
        } finally {
            if (published) Files.deleteIfExists(temporary)
        }
    }

    private fun retainRecoveryEvidence(temporary: Path, target: Path): Path {
        if (!Files.exists(temporary, LinkOption.NOFOLLOW_LINKS)) return temporary
        val recovery = target.resolveSibling(".${target.fileName}.recovery-${UUID.randomUUID()}.json")
        return try {
            atomicMoveNew(temporary, recovery)
            recovery
        } catch (_: Exception) {
            temporary
        }
    }

    private fun prepareParent(root: Path, target: Path): Path {
        require(target.normalize().startsWith(root)) { "Artifact path escapes the project root" }
        val parent = requireNotNull(target.parent)
        var current = root
        root.relativize(parent).forEach { segment ->
            current = current.resolve(segment)
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                require(!Files.isSymbolicLink(current)) { "Artifact parent may not be a symbolic link: $current" }
                require(Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) { "Artifact parent is not a directory: $current" }
            } else {
                Files.createDirectory(current)
            }
            require(current.toRealPath().startsWith(root.toRealPath())) { "Artifact parent escapes the project root: $current" }
        }
        return parent
    }

    private fun existingRoot(projectRoot: Path): Path {
        val root = projectRoot.toAbsolutePath().normalize()
        require(Files.isDirectory(root)) { "Project root is missing: $root" }
        root.toRealPath()
        return root
    }

    private fun resolve(root: Path, relativePath: ProjectRelativePath): Path =
        root.resolve(relativePath.value).normalize().also { path ->
            require(path.startsWith(root)) { "Artifact path escapes the project root: ${relativePath.value}" }
        }

    companion object {
        const val PROJECT_FILE = "project.json"
        val SOURCE_MIDI = ProjectRelativePath("source/original.mid")
        val IMPORT_REPORT = ProjectRelativePath("reports/import.json")

        fun candidateMidiPath(role: CandidateRole, occurrenceId: String, candidateId: String): ProjectRelativePath {
            requireSafeId(occurrenceId, "Occurrence")
            requireSafeId(candidateId, "Candidate")
            return ProjectRelativePath("candidates/${role.name.lowercase()}/$occurrenceId/$candidateId.mid")
        }

        fun candidateReportPath(candidateId: String): ProjectRelativePath {
            requireSafeId(candidateId, "Candidate")
            return ProjectRelativePath("reports/candidates/$candidateId.json")
        }

        fun exportFilePath(snapshotId: String, kind: ExportedFileKind): ProjectRelativePath {
            requireSafeId(snapshotId, "Export snapshot")
            val filename = when (kind) {
                ExportedFileKind.COMPLETE_SONG -> "complete-song.mid"
                ExportedFileKind.MELODY -> "melody.mid"
                ExportedFileKind.CHORDS -> "chords.mid"
                ExportedFileKind.BASS -> "bass.mid"
                ExportedFileKind.DRUMS -> "drums.mid"
                ExportedFileKind.MANIFEST -> "manifest.json"
            }
            return ProjectRelativePath("exports/$snapshotId/$filename")
        }

        private fun requireSafeId(value: String, label: String) {
            require(SAFE_ID.matches(value)) { "$label ID is invalid" }
        }
    }
}

fun interface AtomicWriteObserver {
    fun beforePublish(temporary: Path, target: Path)

    companion object {
        val NONE = AtomicWriteObserver { _, _ -> }
    }
}

class MidiCoreArtifactCollisionException(val artifactPath: Path, cause: Throwable? = null) :
    IllegalStateException("Immutable artifact already exists with different content: $artifactPath", cause)

class MidiCoreProjectSaveException(
    val projectFile: Path,
    val recoveryEvidence: Path,
    cause: Throwable,
) : IOException(
    "Project save failed; the last known-good project was preserved and recovery evidence remains at '$recoveryEvidence'",
    cause,
)

private fun copyAndDigest(
    input: java.io.InputStream,
    write: (ByteArray, Int, Int) -> Unit,
    digest: MessageDigest,
) {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        digest.update(buffer, 0, count)
        write(buffer, 0, count)
    }
}

private fun sha256(path: Path): String {
    val digest = MessageDigest.getInstance("SHA-256")
    Files.newInputStream(path).use { input -> copyAndDigest(input, { _, _, _ -> }, digest) }
    return digest.hex()
}

private fun MessageDigest.hex(): String = digest().joinToString("") { "%02x".format(it) }

private fun atomicMoveNew(source: Path, target: Path) {
    try {
        Files.move(source, target, StandardCopyOption.ATOMIC_MOVE)
    } catch (_: AtomicMoveNotSupportedException) {
        Files.move(source, target)
    }
}

private fun atomicMoveReplace(source: Path, target: Path) {
    try {
        Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    } catch (_: AtomicMoveNotSupportedException) {
        Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
    }
}

private val SAFE_ID = Regex("[A-Za-z0-9][A-Za-z0-9_-]{0,119}")
