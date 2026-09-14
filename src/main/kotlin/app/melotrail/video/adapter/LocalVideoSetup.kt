package app.melotrail.video.adapter

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.FileVisitResult
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.security.MessageDigest

enum class LocalVideoSetupState { READY, MISSING, CORRUPT, UNSUPPORTED_HOST }

enum class LocalVideoSetupIssueKind { MISSING, CORRUPT, UNSUPPORTED_HOST }

data class LocalVideoSetupIssue(
    val kind: LocalVideoSetupIssueKind,
    val component: String,
    val path: Path?,
    val detail: String,
)

data class LocalVideoSetupChoice(
    val id: String,
    val label: String,
    val detail: String,
    val requiresDownload: Boolean,
    /** V17a reports choices; it never executes one. */
    val automatic: Boolean = false,
)

data class LocalVideoTerms(
    val component: String,
    val terms: String,
    val source: String,
)

data class LocalVideoSetupReport(
    val state: LocalVideoSetupState,
    val profileId: String,
    val capability: String,
    val testedHost: String,
    val issues: List<LocalVideoSetupIssue>,
    val choices: List<LocalVideoSetupChoice>,
    val terms: List<LocalVideoTerms>,
    val ready: ReadyLocalVideoSetup?,
)

/** An exact, already verified local installation. Construction is owned by [LocalVideoSetup]. */
class ReadyLocalVideoSetup internal constructor(
    val profileId: String,
    val applicationSupportRoot: Path,
    val comfyUiRoot: Path,
    val workflowRoot: Path,
    val modelsRoot: Path,
    val pythonExecutable: Path,
    val pythonExecutableSha256: String,
    val pythonVirtualEnvironment: Path,
    val pythonSitePackages: Path,
    val mainScript: Path,
    val modelPathsConfig: Path,
    internal val server: PinnedServerProfile,
    internal val verifyForLaunch: () -> Unit,
)

/**
 * Validates the complete separately installed ComfyUI profile. It performs no download, repair,
 * process launch, cloud request, or write. Large model hashes are intentionally checked here,
 * once at an explicit setup action, rather than during MIDI or normal application startup.
 */
class LocalVideoSetup private constructor(
    private val profile: LocalVideoRuntimeProfile,
    private val host: LocalVideoHost,
) {
    fun inspect(applicationSupportRoot: Path = defaultApplicationSupportRoot()): LocalVideoSetupReport {
        val choices = setupChoices()
        if (host.osName != "Mac OS X" || host.architecture !in setOf("arm64", "aarch64")) {
            val issue = LocalVideoSetupIssue(
                LocalVideoSetupIssueKind.UNSUPPORTED_HOST,
                "host",
                null,
                "Pinned ComfyUI supervision requires macOS arm64; found ${host.osName}/${host.architecture}.",
            )
            return report(LocalVideoSetupState.UNSUPPORTED_HOST, listOf(issue), choices, null)
        }

        val issues = mutableListOf<LocalVideoSetupIssue>()
        val root = absoluteNormalized(applicationSupportRoot, "Application-support root", issues)
            ?: return report(LocalVideoSetupState.CORRUPT, issues, choices, null)
        val comfyRoot = child(root, profile.comfyUi.relativePath, "ComfyUI root", issues)
        val workflowRoot = child(root, profile.workflow.relativePath, "workflow root", issues)
        val modelsRoot = child(root, profile.modelsRelativePath, "models root", issues)
        listOf(
            "application-support root" to root,
            "ComfyUI root" to comfyRoot,
            "workflow root" to workflowRoot,
            "models root" to modelsRoot,
        ).forEach { (label, path) -> requireDirectory(label, path, issues) }

        verifyGitCommit("ComfyUI ${profile.comfyUi.version}", comfyRoot, profile.comfyUi.commit, issues)
        profile.comfyUi.customNodes.forEach { node ->
            verifyGitCommit(
                "custom node ${node.directory}",
                child(comfyRoot, "custom_nodes/${node.directory}", "custom node", issues),
                node.commit,
                issues,
            )
        }

        verifySources(comfyRoot, issues)
        val main = verifyFile("ComfyUI main.py", comfyRoot, profile.comfyUi.main, issues)
        profile.comfyUi.files.forEach { pinned ->
            verifyFile(pinned.name, comfyRoot, PinnedFile(pinned.relativePath, pinned.size, pinned.sha256), issues)
        }
        val modelPaths = verifyFile("workflow model paths", workflowRoot, profile.workflow.modelPaths, issues)
        verifyFile("workflow provenance", workflowRoot, profile.workflow.provenance, issues)
        profile.comfyUi.packages.forEach { pinned ->
            verifyFile(
                "Python package ${pinned.name} ${pinned.version}",
                comfyRoot,
                PinnedFile(pinned.metadataRelativePath, pinned.size, pinned.sha256),
                issues,
            )
        }
        profile.models.forEach { model ->
            verifyFile("model ${model.name}", modelsRoot, PinnedFile(model.relativePath, model.size, model.sha256), issues)
        }

        val virtualEnvironment = child(comfyRoot, ".venv", "Python virtual environment", issues)
        requireDirectory("Python virtual environment", virtualEnvironment, issues)
        val pythonLink = child(comfyRoot, profile.comfyUi.python.linkRelativePath, "Python link", issues)
        val pythonExecutable = verifyPython(pythonLink, profile.comfyUi.python, issues)
        val sitePackages = child(comfyRoot, profile.comfyUi.python.sitePackagesRelativePath, "site-packages", issues)
        requireDirectory("Python ${profile.comfyUi.python.version} site-packages", sitePackages, issues)

        val ready = if (issues.isEmpty() && main != null && modelPaths != null && pythonExecutable != null) {
            ReadyLocalVideoSetup(
                profile.profileId,
                root.toRealPath(),
                comfyRoot.toRealPath(),
                workflowRoot.toRealPath(),
                modelsRoot.toRealPath(),
                pythonExecutable,
                profile.comfyUi.python.resolvedSha256,
                virtualEnvironment.toRealPath(),
                sitePackages.toRealPath(),
                main,
                modelPaths,
                profile.server,
                verifyForLaunch = {
                    val launchIssues = mutableListOf<LocalVideoSetupIssue>()
                    // Recheck directory traversal as well as contents when consuming an older report.
                    child(root, profile.comfyUi.relativePath, "ComfyUI root", launchIssues)
                    child(root, profile.workflow.relativePath, "workflow root", launchIssues)
                    child(comfyRoot, profile.comfyUi.python.linkRelativePath, "Python link", launchIssues)
                    verifyGitCommit("ComfyUI ${profile.comfyUi.version}", comfyRoot, profile.comfyUi.commit, launchIssues)
                    profile.comfyUi.customNodes.forEach { node ->
                        verifyGitCommit("custom node ${node.directory}", comfyRoot.resolve("custom_nodes/${node.directory}"), node.commit, launchIssues)
                    }
                    verifySources(comfyRoot, launchIssues)
                    verifyFile("ComfyUI main.py", comfyRoot, profile.comfyUi.main, launchIssues)
                    profile.comfyUi.files.forEach { pin ->
                        verifyFile(pin.name, comfyRoot, PinnedFile(pin.relativePath, pin.size, pin.sha256), launchIssues)
                    }
                    profile.comfyUi.packages.forEach { pin ->
                        verifyFile("Python package ${pin.name}", comfyRoot, PinnedFile(pin.metadataRelativePath, pin.size, pin.sha256), launchIssues)
                    }
                    verifyFile("workflow model paths", workflowRoot, profile.workflow.modelPaths, launchIssues)
                    verifyFile("workflow provenance", workflowRoot, profile.workflow.provenance, launchIssues)
                    verifyPython(pythonLink, profile.comfyUi.python, launchIssues)
                    if (launchIssues.isNotEmpty()) {
                        throw ComfyVideoRuntimeException(ComfyVideoRuntimeFailure.INVALID_SETUP,
                            "Pinned executable setup changed; inspect setup again: " + launchIssues.joinToString { "${it.component}: ${it.detail}" })
                    }
                },
            )
        } else null
        val state = when {
            ready != null -> LocalVideoSetupState.READY
            issues.any { it.kind == LocalVideoSetupIssueKind.CORRUPT } -> LocalVideoSetupState.CORRUPT
            else -> LocalVideoSetupState.MISSING
        }
        return report(state, issues, choices, ready)
    }

    private fun verifySources(comfyRoot: Path, issues: MutableList<LocalVideoSetupIssue>) {
        profile.comfyUi.sourceTrees.forEach { pin ->
            val sourceRoot = if (pin.relativePath.isEmpty()) comfyRoot else child(comfyRoot, pin.relativePath, "executable source", issues)
            if (!Files.exists(sourceRoot, NOFOLLOW_LINKS)) {
                missing("executable source ${pin.relativePath}", sourceRoot, issues)
            } else try {
                verifySourceTree(sourceRoot, pin)
            } catch (error: Exception) {
                corrupt("executable source ${pin.relativePath}", sourceRoot, useful(error), issues)
            }
        }
    }

    private fun verifyPython(
        link: Path,
        python: PinnedPython,
        issues: MutableList<LocalVideoSetupIssue>,
    ): Path? {
        if (!Files.exists(link, NOFOLLOW_LINKS)) {
            missing("Python ${python.version} virtual-environment link", link, issues)
            return null
        }
        if (!Files.isSymbolicLink(link)) {
            corrupt("Python ${python.version} virtual-environment link", link, "expected the tested venv symlink", issues)
            return null
        }
        val resolved = try {
            link.toRealPath()
        } catch (error: Exception) {
            corrupt("Python ${python.version} executable", link, "broken or unreadable symlink: ${useful(error)}", issues)
            return null
        }
        if (!Files.isRegularFile(resolved, NOFOLLOW_LINKS) || Files.isSymbolicLink(resolved) || !Files.isExecutable(resolved)) {
            corrupt("Python ${python.version} executable", resolved, "resolved target is not an executable regular file", issues)
            return null
        }
        verifyIdentity("Python ${python.version} executable", resolved, python.resolvedSize, python.resolvedSha256, issues)
        return resolved.takeIf { issues.none { issue -> issue.path == resolved } }
    }

    private fun verifyFile(
        component: String,
        root: Path,
        pin: PinnedFile,
        issues: MutableList<LocalVideoSetupIssue>,
    ): Path? {
        val path = child(root, pin.relativePath, component, issues)
        if (!Files.exists(path, NOFOLLOW_LINKS)) {
            missing(component, path, issues)
            return null
        }
        if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, NOFOLLOW_LINKS)) {
            corrupt(component, path, "expected a regular non-symlink file", issues)
            return null
        }
        verifyIdentity(component, path, pin.size, pin.sha256, issues)
        return path.takeIf { issues.none { issue -> issue.path == path } }
    }

    private fun verifyIdentity(
        component: String,
        path: Path,
        expectedSize: Long,
        expectedSha256: String,
        issues: MutableList<LocalVideoSetupIssue>,
    ) {
        val size = try {
            Files.size(path)
        } catch (error: Exception) {
            corrupt(component, path, "cannot read size: ${useful(error)}", issues)
            return
        }
        if (size != expectedSize) {
            corrupt(component, path, "size mismatch: expected $expectedSize bytes, found $size", issues)
            return
        }
        val actual = try {
            sha256(path)
        } catch (error: Exception) {
            corrupt(component, path, "cannot calculate SHA-256: ${useful(error)}", issues)
            return
        }
        if (actual != expectedSha256) {
            corrupt(component, path, "SHA-256 mismatch: expected $expectedSha256, found $actual", issues)
        }
    }

    private fun verifyGitCommit(
        component: String,
        repository: Path,
        expected: String,
        issues: MutableList<LocalVideoSetupIssue>,
    ) {
        if (!Files.isDirectory(repository, NOFOLLOW_LINKS)) {
            missing(component, repository, issues)
            return
        }
        val git = repository.resolve(".git")
        if (!Files.isDirectory(git, NOFOLLOW_LINKS) || Files.isSymbolicLink(git)) {
            corrupt(component, git, "expected a local Git metadata directory", issues)
            return
        }
        val head = readSmallText(git.resolve("HEAD"), component, issues) ?: return
        val commit = if (head.startsWith("ref: ")) {
            val ref = head.removePrefix("ref: ").trim()
            if (ref.split('/').any { it == "." || it == ".." }) {
                corrupt(component, git.resolve("HEAD"), "unsafe Git HEAD reference", issues)
                return
            }
            readOptionalSmallText(git.resolve(ref))
                ?: readPackedRef(git.resolve("packed-refs"), ref, component, issues)
        } else head.trim()
        if (commit != expected) corrupt(component, git.resolve("HEAD"), "commit mismatch: expected $expected, found ${commit ?: "unknown"}", issues)
    }

    private fun readPackedRef(
        path: Path,
        ref: String,
        component: String,
        issues: MutableList<LocalVideoSetupIssue>,
    ): String? {
        if (!Files.isRegularFile(path, NOFOLLOW_LINKS)) {
            corrupt(component, path, "Git HEAD reference '$ref' is missing", issues)
            return null
        }
        return Files.readAllLines(path).firstOrNull { it.endsWith(" $ref") }?.substringBefore(' ')
            ?: run { corrupt(component, path, "Git HEAD reference '$ref' is missing", issues); null }
    }

    private fun readSmallText(path: Path, component: String, issues: MutableList<LocalVideoSetupIssue>): String? {
        if (!Files.isRegularFile(path, NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) {
            corrupt(component, path, "required Git identity file is missing or unsafe", issues)
            return null
        }
        return try {
            if (Files.size(path) > 4096) {
                corrupt(component, path, "Git identity file exceeds 4096 bytes", issues)
                null
            } else Files.readString(path).trim()
        } catch (error: Exception) {
            corrupt(component, path, "cannot read Git identity: ${useful(error)}", issues)
            null
        }
    }

    private fun readOptionalSmallText(path: Path): String? = try {
        if (!Files.isRegularFile(path, NOFOLLOW_LINKS) || Files.isSymbolicLink(path) || Files.size(path) > 4096) null
        else Files.readString(path).trim()
    } catch (_: Exception) {
        null
    }

    private fun requireDirectory(component: String, path: Path, issues: MutableList<LocalVideoSetupIssue>) {
        if (!Files.exists(path, NOFOLLOW_LINKS)) missing(component, path, issues)
        else if (Files.isSymbolicLink(path) || !Files.isDirectory(path, NOFOLLOW_LINKS)) {
            corrupt(component, path, "expected a non-symlink directory", issues)
        }
    }

    private fun child(root: Path, relative: String, component: String, issues: MutableList<LocalVideoSetupIssue>): Path {
        val parsed = try { Path.of(relative) } catch (error: Exception) {
            corrupt(component, root, "invalid pinned relative path '$relative'", issues)
            return root.resolve("invalid-profile-path")
        }
        if (parsed.isAbsolute || parsed.any { it.toString() in setOf(".", "..") }) {
            corrupt(component, root, "unsafe pinned relative path '$relative'", issues)
            return root.resolve("invalid-profile-path")
        }
        val resolved = root.resolve(parsed).normalize()
        var ancestor = root
        parsed.forEach { part ->
            ancestor = ancestor.resolve(part)
            if (ancestor != resolved && Files.isSymbolicLink(ancestor)) corrupt(component, ancestor, "symlink within pinned root", issues)
        }
        if (!resolved.startsWith(root)) {
            corrupt(component, resolved, "path escapes its pinned root", issues)
            return root.resolve("invalid-profile-path")
        }
        return resolved
    }

    private fun absoluteNormalized(
        path: Path,
        component: String,
        issues: MutableList<LocalVideoSetupIssue>,
    ): Path? {
        if (!path.isAbsolute || path.any { it.toString() in setOf(".", "..") }) {
            corrupt(component, path, "must be an absolute normalized path", issues)
            return null
        }
        return path.normalize()
    }

    private fun setupChoices() = listOf(
        LocalVideoSetupChoice(
            "reuse-verified",
            "Use verified local installation",
            "Validate and reuse every pinned file in the selected MelotrailVideo root.",
            requiresDownload = false,
        ),
        LocalVideoSetupChoice(
            "install-or-repair-pinned",
            "Install or repair pinned package",
            "Explicitly download the exact disclosed sources and accept their separate terms, then validate again. V17a does not perform this action.",
            requiresDownload = true,
        ),
        LocalVideoSetupChoice(
            "leave-unavailable",
            "Leave local video unavailable",
            "Keep using the MIDI application without installing or launching any video dependency.",
            requiresDownload = false,
        ),
    )

    private fun report(
        state: LocalVideoSetupState,
        issues: List<LocalVideoSetupIssue>,
        choices: List<LocalVideoSetupChoice>,
        ready: ReadyLocalVideoSetup?,
    ) = LocalVideoSetupReport(
        state,
        profile.profileId,
        profile.capability,
        profile.testedHost,
        issues.toList(),
        choices,
        profile.terms.map { LocalVideoTerms(it.component, it.terms, it.source) },
        ready,
    )

    private fun missing(component: String, path: Path, issues: MutableList<LocalVideoSetupIssue>) {
        issues += LocalVideoSetupIssue(LocalVideoSetupIssueKind.MISSING, component, path, "required pinned dependency is missing")
    }

    private fun corrupt(component: String, path: Path?, detail: String, issues: MutableList<LocalVideoSetupIssue>) {
        issues += LocalVideoSetupIssue(LocalVideoSetupIssueKind.CORRUPT, component, path, detail)
    }

    private fun sha256(path: Path): String {
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

    private fun defaultApplicationSupportRoot(): Path =
        Path.of(System.getProperty("user.home")).resolve(profile.applicationSupportRelativePath)

    private fun useful(error: Exception) = error.message ?: error::class.simpleName.orEmpty()

    companion object {
        private const val PROFILE_RESOURCE = "/video/comfyui/runtime-profile.json"
        private val JSON = Json { ignoreUnknownKeys = false }

        fun bundled(): LocalVideoSetup = LocalVideoSetup(loadBundledProfile(), LocalVideoHost.current())

        internal fun fromJson(json: String, host: LocalVideoHost = LocalVideoHost.current()): LocalVideoSetup =
            LocalVideoSetup(JSON.decodeFromString<LocalVideoRuntimeProfile>(json), host)

        private fun loadBundledProfile(): LocalVideoRuntimeProfile {
            val stream = LocalVideoSetup::class.java.getResourceAsStream(PROFILE_RESOURCE)
                ?: error("Missing bundled local-video profile $PROFILE_RESOURCE")
            return stream.bufferedReader().use { JSON.decodeFromString<LocalVideoRuntimeProfile>(it.readText()) }
        }
    }
}

internal data class LocalVideoHost(val osName: String, val architecture: String) {
    companion object {
        fun current() = LocalVideoHost(System.getProperty("os.name"), System.getProperty("os.arch"))
    }
}

@Serializable
internal data class LocalVideoRuntimeProfile(
    val schemaVersion: Int,
    val profileId: String,
    val applicationSupportRelativePath: String,
    val testedHost: String,
    val capability: String,
    val comfyUi: PinnedComfyUi,
    val workflow: PinnedWorkflow,
    val modelsRelativePath: String,
    val models: List<PinnedModel>,
    val server: PinnedServerProfile,
    val terms: List<PinnedTerms>,
) {
    init {
        require(schemaVersion == 1) { "Unsupported local-video profile schema $schemaVersion" }
    }
}

@Serializable
internal data class PinnedComfyUi(
    val relativePath: String,
    val version: String,
    val commit: String,
    val main: PinnedFile,
    val files: List<PinnedNamedFile>,
    val sourceTrees: List<PinnedSourceTree>,
    val python: PinnedPython,
    val packages: List<PinnedPackage>,
    val customNodes: List<PinnedCustomNode>,
)
@Serializable internal data class PinnedNamedFile(
    val name: String,
    val relativePath: String,
    val size: Long,
    val sha256: String,
)

@Serializable internal data class PinnedPython(
    val linkRelativePath: String,
    val version: String,
    val resolvedSize: Long,
    val resolvedSha256: String,
    val sitePackagesRelativePath: String,
)
@Serializable internal data class PinnedPackage(
    val name: String,
    val version: String,
    val metadataRelativePath: String,
    val size: Long,
    val sha256: String,
)
@Serializable internal data class PinnedCustomNode(val directory: String, val commit: String)
@Serializable internal data class PinnedWorkflow(
    val relativePath: String,
    val modelPaths: PinnedFile,
    val provenance: PinnedFile,
)
@Serializable internal data class PinnedFile(val relativePath: String, val size: Long, val sha256: String)
@Serializable internal data class PinnedModel(
    val name: String,
    val relativePath: String,
    val size: Long,
    val sha256: String,
    val source: String,
)
@Serializable internal data class PinnedServerProfile(
    val host: String,
    val defaultPort: Int,
    val startupTimeoutSeconds: Long,
    val sessionTimeoutSeconds: Long,
    val inferenceTimeoutSeconds: Long,
    val shutdownTimeoutSeconds: Long,
    val additionalSwapLimitBytes: Long,
    val pollIntervalMillis: Long,
    val arguments: List<String>,
)
@Serializable internal data class PinnedTerms(val component: String, val terms: String, val source: String)

/** Exact Python/native import source set. Models, mutable run data and bytecode caches are excluded. */
@Serializable
internal data class PinnedSourceTree(
    val relativePath: String,
    val excludedDirectories: List<String>,
    val fileCount: Int,
    val totalBytes: Long,
    val sha256: String,
)

internal fun verifySourceTree(root: Path, pin: PinnedSourceTree) {
    check(Files.isDirectory(root, NOFOLLOW_LINKS) && !Files.isSymbolicLink(root)) { "source root is not a regular directory" }
    val files = mutableListOf<Path>()
    var entries = 0
    var totalBytes = 0L
    Files.walkFileTree(root, setOf(), 64, object : SimpleFileVisitor<Path>() {
        override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
            check(++entries <= 20_000) { "source tree exceeds entry bound" }
            if (dir != root && (dir.fileName.toString() == "__pycache__" ||
                    root.relativize(dir).toString() in pin.excludedDirectories)) return FileVisitResult.SKIP_SUBTREE
            return FileVisitResult.CONTINUE
        }
        override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
            check(++entries <= 20_000) { "source tree exceeds entry bound" }
            check(!attrs.isSymbolicLink && !attrs.isDirectory) { "unsafe source path: $file" }
            if (SOURCE_SUFFIXES.any { file.fileName.toString().endsWith(it) }) {
                check(attrs.isRegularFile) { "source is not a regular file: $file" }
                files.add(file)
                totalBytes += attrs.size()
                check(files.size <= pin.fileCount && totalBytes <= pin.totalBytes) { "executable source set or size changed: $file" }
            }
            return FileVisitResult.CONTINUE
        }
    })
    check(files.size == pin.fileCount && totalBytes == pin.totalBytes) { "executable source set or size changed" }
    val digest = MessageDigest.getInstance("SHA-256")
    var bytesRead = 0L
    files.sortedBy { root.relativize(it).toString() }.forEach { file ->
        val contentDigest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                bytesRead += count
                check(bytesRead <= pin.totalBytes) { "source grew while reading: $file" }
                contentDigest.update(buffer, 0, count)
            }
        }
        val hash = contentDigest.digest().joinToString("") { "%02x".format(it) }
        digest.update("${root.relativize(file)}\u0000${Files.size(file)}\u0000$hash\n".toByteArray(Charsets.UTF_8))
    }
    val actual = digest.digest().joinToString("") { "%02x".format(it) }
    check(actual == pin.sha256) { "executable source SHA-256 mismatch: expected ${pin.sha256}, found $actual" }
}

private val SOURCE_SUFFIXES = setOf(".py", ".pyi", ".pyc", ".so", ".dylib", ".pyd", ".zip", ".pth")
