package app.melotrail.desktop

import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.security.MessageDigest
import java.time.Instant
import java.util.Properties
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.*

/** Host-only DMG install/startup verification. No installer, process runner or test dependency is shipped. */
object MidiCoreNativeInstallCheck {
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 3) { "Expected repository, DMG directory and new evidence directory" }
        check(System.getProperty("os.name") == "Mac OS X") { "The configured native distribution is a macOS DMG" }
        val repository = Path.of(args[0]).toRealPath()
        val dmgDirectory = Path.of(args[1]).toRealPath()
        val output = Path.of(args[2]).toAbsolutePath().normalize()
        Files.createDirectory(output) // Existing evidence is never replaced or cleaned.
        val logs = Files.createDirectory(output.resolve("logs"))
        var commandNumber = 0
        fun command(vararg command: String): String {
            val log = logs.resolve("${++commandNumber}-${Path.of(command[0]).fileName}.log")
            runProcess(command.toList(), repository, log, 120)
            return Files.readString(log).trim()
        }
        val facts = linkedMapOf<String, JsonElement>(
            "status" to JsonPrimitive("INCOMPLETE"),
            "recordedAt" to JsonPrimitive(Instant.now().toString()),
            "onscreenCompositorCapture" to JsonPrimitive("NOT_MEASURED"),
            "humanApproval" to JsonPrimitive("PENDING"),
        )
        var failure: Throwable? = null
        try {
            val identityBefore = U07Evidence.identity()
            facts["identity"] = identityBefore
            facts["machine"] = U07Evidence.machine()
            facts["macOS"] = JsonPrimitive(command("/usr/bin/sw_vers"))
            val retired = listOf("worker", "sounds", "data/audio", ".venv-worker")
            check(retired.none { Files.exists(repository.resolve(it), NOFOLLOW_LINKS) }) {
                "Clean-install checkout contains a retired runtime/data owner"
            }
            facts["absentRuntimeOwners"] = JsonArray(retired.map(::JsonPrimitive))
            val tracked = command("git", "ls-files", "-z").split('\u0000').filter(String::isNotBlank)
            val candidateFiles = command("git", "ls-files", "--cached", "--others", "--exclude-standard", "-z")
                .split('\u0000').filter(String::isNotBlank).distinct()
            facts["reduction"] = reduction(repository, tracked, candidateFiles)
            val protected = candidateFiles.filter {
                it.startsWith("docs/pictures/") || it.startsWith("docs/checks/") ||
                    it.endsWith(".mid", true) || it.endsWith(".midi", true)
            }.associateWith {
                check(Files.isRegularFile(repository.resolve(it), NOFOLLOW_LINKS)) { "Protected input is absent or a symlink: $it" }
                hashFile(repository.resolve(it))
            }
            facts["protectedInputs"] = JsonObject(protected.mapValues { JsonPrimitive(it.value) })
            val dmg = Files.list(dmgDirectory).use { paths ->
                paths.filter { it.fileName.toString().endsWith(".dmg") && Files.isRegularFile(it, NOFOLLOW_LINKS) }.toList().single()
            }
            facts["dmg"] = buildJsonObject {
                put("file", dmg.toString()); put("bytes", Files.size(dmg)); put("sha256", hashFile(dmg))
            }
            val mount = Files.createDirectory(output.resolve("mount"))
            val installed = Files.createDirectory(output.resolve("installed")).resolve("Melotrail.app")
            var attached = false
            var installFailure: Throwable? = null
            try {
                command("/usr/bin/hdiutil", "attach", "-readonly", "-nobrowse", "-mountpoint", mount.toString(), dmg.toString())
                attached = true
                val source = mount.resolve("Melotrail.app")
                val packagedFiles = inventory(source)
                command("/usr/bin/ditto", source.toString(), installed.toString())
                val installedFiles = inventory(installed)
                check(packagedFiles == installedFiles) { "Installed app differs from the read-only DMG payload" }
                validatePayload(installedFiles.keys)
                facts["installedFiles"] = JsonObject(installedFiles.mapValues { JsonPrimitive(it.value) })
                facts["installedBytes"] = JsonPrimitive(Files.walk(installed).use { paths ->
                    paths.filter { Files.isRegularFile(it, NOFOLLOW_LINKS) }.mapToLong(Files::size).sum()
                })
            } catch (problem: Throwable) {
                installFailure = problem
                throw problem
            } finally {
                if (attached || Files.exists(mount.resolve("Melotrail.app"), NOFOLLOW_LINKS)) {
                    try {
                        command("/usr/bin/hdiutil", "detach", mount.toString())
                    } catch (detachFailure: Throwable) {
                        installFailure?.addSuppressed(detachFailure) ?: throw detachFailure
                    }
                }
            }
            val executable = installed.resolve("Contents/MacOS/Melotrail")
            check(Files.isExecutable(executable)) { "Installed launcher is missing or not executable" }
            val startup = output.resolve("startup") // The installed launcher reserves this directory itself.
            val working = Files.createDirectory(output.resolve("working"))
            val temporary = Files.createDirectory(output.resolve("tmp"))
            val log = logs.resolve("installed-startup.log")
            val launch = listOf(executable.toString(), "--startup-check", startup.toString())
            facts["launch"] = JsonArray(launch.map(::JsonPrimitive))
            // No inherited JAVA_HOME, CLASSPATH, model/worker settings, dylib injection or project cwd.
            runProcess(launch, working, log, 60, mapOf(
                "PATH" to "/usr/bin:/bin:/usr/sbin:/sbin",
                "TMPDIR" to temporary.toString(),
                "LANG" to "en_US.UTF-8",
            ))
            val startupReport = Properties().apply {
                Files.newInputStream(startup.resolve("startup.properties")).use(::load)
            }
            validateStartup(startupReport, installed)
            facts["startup"] = JsonObject(startupReport.stringPropertyNames().associateWith { JsonPrimitive(startupReport.getProperty(it)) })
            check(inventory(installed) == facts.getValue("installedFiles").jsonObject.mapValues { it.value.jsonPrimitive.content }) {
                "Startup modified the installed app payload"
            }
            check(protected.all { (file, digest) -> hashFile(repository.resolve(file)) == digest }) {
                "A protected source/reference changed during installation or startup"
            }
            check(identityBefore["inputTreeSha256"] == U07Evidence.identity()["inputTreeSha256"]) {
                "Candidate inputs changed during the check"
            }
            facts["status"] = JsonPrimitive("PASS")
        } catch (problem: Throwable) {
            failure = problem
            facts["failure"] = JsonPrimitive("${problem.javaClass.simpleName}: ${problem.message}")
        } finally {
            Files.writeString(output.resolve("native-install.json"),
                Json { prettyPrint = true }.encodeToString(JsonElement.serializer(), JsonObject(facts)), CREATE_NEW)
        }
        failure?.let { throw it }
        println("Native installation/startup evidence: ${output.resolve("native-install.json")}")
    }

    internal fun inventory(root: Path): Map<String, String> {
        check(Files.isDirectory(root, NOFOLLOW_LINKS)) { "Missing app bundle: $root" }
        return Files.walk(root).use { paths ->
            paths.filter { !Files.isDirectory(it, NOFOLLOW_LINKS) }.sorted().toList().associate { file ->
                val value = if (Files.isSymbolicLink(file)) {
                    val target = Files.readSymbolicLink(file)
                    check(!target.isAbsolute && file.toRealPath().startsWith(root.toRealPath())) { "App symlink escapes its bundle: $file" }
                    "symlink:$target"
                } else {
                    check(Files.isRegularFile(file, NOFOLLOW_LINKS)) { "Unsupported app payload: $file" }
                    "sha256:${hashFile(file)}"
                }
                root.relativize(file).toString() to value
            }
        }
    }

    internal fun validatePayload(paths: Set<String>) {
        check("Contents/MacOS/Melotrail" in paths && "Contents/app/Melotrail.cfg" in paths) { "Incomplete native payload" }
        val forbidden = paths.filter { path ->
            val name = path.substringAfterLast('/').lowercase()
            name.endsWith(".py") || name.endsWith(".sfz") || name.endsWith(".sf2") || name.endsWith(".gguf") ||
                listOf("junit", "kotlin-test", "ui-test", "ffmpeg", "sfizz", "qwen").any(name::contains) ||
                path.split('/').any { it.lowercase() in setOf("worker", "sounds", "companion", ".venv-worker") }
        }
        check(forbidden.isEmpty()) { "Non-MIDI runtime/test payload shipped: $forbidden" }
    }

    internal fun validateStartup(report: Properties, installed: Path) {
        check(report.getProperty("status") == "READY") { "Native startup never became ready" }
        check(report.getProperty("entrypoint") == "app.melotrail.desktop.DesktopMainKt")
        check(report.getProperty("preferences") == "ISOLATED_NO_OP")
        check(report.getProperty("window.showing") == "true" && report.getProperty("audition.closed") == "true")
        check(report.getProperty("window.width").toInt() >= 720 && report.getProperty("window.height").toInt() >= 620)
        check(report.getProperty("destinations") == midiCoreWorkspaceDestinations.joinToString(",") { it.route })
        check(Path.of(report.getProperty("java.home")).toRealPath().startsWith(installed.resolve("Contents/runtime").toRealPath())) {
            "Startup used an external JDK"
        }
        check(Path.of(report.getProperty("codeSource")).toRealPath().startsWith(installed.resolve("Contents/app").toRealPath())) {
            "Startup used classes outside the installed image"
        }
    }

    internal fun reduction(repository: Path, tracked: List<String>, candidateFiles: List<String>): JsonObject {
        val kotlinFiles = candidateFiles.filter { it.endsWith(".kt") && Files.isRegularFile(repository.resolve(it), NOFOLLOW_LINKS) }
        fun count(prefixes: List<String>) = kotlinFiles.filter { file -> prefixes.any(file::startsWith) }
        val production = count(listOf("src/main/", "desktopApp/src/main/"))
        val tests = count(listOf("src/test/", "desktopApp/src/test/"))
        fun lines(files: List<String>) = files.sumOf { Files.readAllLines(repository.resolve(it)).size }
        return buildJsonObject {
            put("baselineProductionFiles", 236); put("baselineProductionLines", 73677)
            put("productionFiles", production.size); put("productionLines", lines(production))
            put("productionLineReductionPercent", 100.0 * (73677 - lines(production)) / 73677)
            put("testFiles", tests.size); put("testLines", lines(tests))
            put("candidatePythonFiles", candidateFiles.count { it.endsWith(".py") && Files.exists(repository.resolve(it), NOFOLLOW_LINKS) })
            put("trackedPayloadBytes", tracked.sumOf { file ->
                val path = repository.resolve(file)
                when {
                    Files.isSymbolicLink(path) -> Files.readSymbolicLink(path).toString().toByteArray().size.toLong()
                    Files.isRegularFile(path, NOFOLLOW_LINKS) -> Files.size(path)
                    else -> 0L
                }
            })
            put("scope", "Source counts include uncommitted new inputs; tracked payload excludes untracked/ignored files, installed image, Git history and external user data")
            put("deletedByThisCheck", 0)
        }
    }

    private fun hashFile(path: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { stream ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    internal fun runProcess(command: List<String>, cwd: Path, log: Path, seconds: Long, environment: Map<String, String>? = null) {
        val builder = ProcessBuilder(command).directory(cwd.toFile()).redirectErrorStream(true).redirectOutput(log.toFile())
        environment?.let { builder.environment().clear(); builder.environment().putAll(it) }
        val process = builder.start()
        try {
            check(process.waitFor(seconds, TimeUnit.SECONDS)) { "Timed out: ${command.first()}; see $log\n${diagnosticTail(log)}" }
            check(process.exitValue() == 0) { "Exit ${process.exitValue()}: ${command.first()}; see $log\n${diagnosticTail(log)}" }
        } finally {
            if (process.isAlive) {
                val children = process.descendants().use { it.toList() }
                children.forEach { it.destroy() }
                process.destroy()
                if (!process.waitFor(5, TimeUnit.SECONDS)) {
                    process.destroyForcibly()
                    check(process.waitFor(5, TimeUnit.SECONDS)) { "Process did not terminate: ${command.first()}" }
                }
                children.filter { it.isAlive }.forEach { it.destroyForcibly() }
            }
        }
    }

    // Keep concrete native failures in the coordinator check log as well as the retained full log.
    private fun diagnosticTail(log: Path): String = java.io.RandomAccessFile(log.toFile(), "r").use { file ->
        val count = minOf(4096L, file.length()).toInt()
        file.seek(file.length() - count)
        val bytes = ByteArray(count)
        file.readFully(bytes)
        String(bytes, Charsets.UTF_8)
    }
}
