package app.melotrail.application

import app.melotrail.arrangement.core.MidiCoreArrangementStyleCatalog
import app.melotrail.arrangement.core.MidiCorePatternCatalog
import app.melotrail.arrangement.core.MidiCorePerformanceProfileCatalog
import app.melotrail.project.MidiCoreProjectSchema
import app.melotrail.project.ProjectRelativePath
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.security.MessageDigest

internal fun evaluationSha256(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

internal fun evaluationFileSha256(path: Path): String {
    val digest = MessageDigest.getInstance("SHA-256")
    Files.newInputStream(path).use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val size = input.read(buffer)
            if (size < 0) break
            digest.update(buffer, 0, size)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

/** All root-engine runtime dependencies are content-pinned, including compiled algorithms/catalogs. */
internal fun evaluationVersions(): MidiCoreEvaluationVersions {
    val owners = linkedMapOf(
        "engine" to MidiCoreProjectSchema::class.java,
        "kotlin-stdlib" to KotlinVersion::class.java,
        "serialization-core" to kotlinx.serialization.KSerializer::class.java,
        "serialization-json" to kotlinx.serialization.json.Json::class.java,
        "coroutines-core" to kotlinx.coroutines.Job::class.java,
    )
    return MidiCoreEvaluationVersions(
        MidiCoreProjectSchema.VERSION,
        MidiCoreArrangementStyleCatalog.VERSION,
        MidiCorePatternCatalog.VERSION,
        MidiCorePerformanceProfileCatalog.VERSION,
        "${System.getProperty("java.vendor")}/${System.getProperty("java.runtime.version")}",
        KotlinVersion.CURRENT.toString(),
        owners.mapValues { (_, type) ->
            val location = Path.of(requireNotNull(type.protectionDomain.codeSource) { "Cannot identify runtime code for ${type.name}" }.location.toURI())
            if (Files.isDirectory(location)) {
                val hashes = Files.walk(location).use { paths ->
                    paths.filter { Files.isRegularFile(it) }.toList().sortedBy { location.relativize(it).toString() }
                        .joinToString("\n") { "${location.relativize(it).toString().replace('\\', '/')}:${evaluationFileSha256(it)}" }
                }
                evaluationSha256(hashes.encodeToByteArray())
            } else evaluationFileSha256(location)
        },
    )
}

internal fun readEvaluationFile(root: Path, relative: String): ByteArray {
    ProjectRelativePath(relative)
    val realRoot = root.toRealPath()
    val path = realRoot.resolve(relative)
    var part = realRoot
    realRoot.relativize(path).forEach { segment ->
        part = part.resolve(segment)
        require(!Files.isSymbolicLink(part)) { "Evaluation inputs cannot use symlinks: $relative" }
    }
    require(Files.isRegularFile(path, NOFOLLOW_LINKS) && path.toRealPath().startsWith(realRoot)) { "Missing or unconfined evaluation file: $relative" }
    return Files.readAllBytes(path)
}

internal fun writeEvaluationFile(root: Path, relative: String, bytes: ByteArray) {
    ProjectRelativePath(relative)
    val path = root.resolve(relative)
    Files.createDirectories(path.parent)
    Files.write(path, bytes, CREATE_NEW)
}

/** Only a newly created staging tree is cleaned; selected projects and existing outputs are never touched. */
internal fun <T> publishEvaluationDirectory(destination: Path, protectedRoots: List<Path>, write: (Path) -> T): T {
    val requested = destination.toAbsolutePath().normalize()
    val parent = requireNotNull(requested.parent).toRealPath()
    val target = parent.resolve(requested.fileName)
    require(!Files.exists(target, NOFOLLOW_LINKS)) { "Evaluation destination already exists; choose a new directory" }
    require(protectedRoots.none { target.startsWith(it.toRealPath()) }) { "Evaluation output must be outside every supplied project or frozen set" }
    var ancestor: Path? = parent
    while (ancestor != null) {
        require(!Files.exists(ancestor.resolve("project.json"), NOFOLLOW_LINKS)) { "Evaluation output cannot be written inside a MIDI project" }
        require(!Files.exists(ancestor.resolve("frozen-set.json"), NOFOLLOW_LINKS)) { "Evaluation output cannot be written inside an existing frozen set or evaluation package" }
        ancestor = ancestor.parent
    }
    val staging = Files.createTempDirectory(parent, ".q01-")
    try {
        val result = write(staging)
        // No REPLACE_EXISTING or ATOMIC_MOVE (whose existing-target behavior is implementation specific).
        Files.move(staging, target)
        return result
    } finally {
        if (Files.exists(staging, NOFOLLOW_LINKS)) {
            Files.walk(staging).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach { Files.delete(it) } }
        }
    }
}
