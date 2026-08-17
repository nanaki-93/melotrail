package app.melotrail.commercial

import app.melotrail.arrangement.Part
import app.melotrail.arrangement.ProjectStore
import app.melotrail.arrangement.ProjectWorkflowStore
import app.melotrail.arrangement.WorkflowArtifact
import app.melotrail.arrangement.WorkflowArtifactReference
import app.melotrail.arrangement.InstrumentRegistryLoader
import app.melotrail.arrangement.Project
import app.melotrail.arrangement.SelectedMidiArtifactKind
import app.melotrail.arrangement.SelectedMidiArtifactResolver
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/** A creator statement, never an inferred ownership or clearance conclusion. */
@Serializable
enum class SourceRightsClaim { OWNED, COMMERCIAL_PERMISSION, PUBLIC_DOMAIN, NOT_ESTABLISHED }

@Serializable
data class SourceRightsAttestation(
    val claim: SourceRightsClaim,
    /** ISO-8601 instant supplied by the user-facing boundary at confirmation time. */
    val attestedAt: String
) {
    init { require(attestedAt.matches(ISO_INSTANT)) { "Source attestation date must be an ISO-8601 instant" } }
    val supportsCommercialUse get() = claim != SourceRightsClaim.NOT_ESTABLISHED
}

@Serializable
enum class CommercialTerm { PERMITTED, CONDITIONAL, UNKNOWN, BLOCKED }

@Serializable
enum class CommercialDependencyKind { MODEL, SOUND_LIBRARY, SAMPLE }

/** Snapshot of an actually used dependency. It is intentionally data-only. */
@Serializable
data class CommercialDependency(
    val kind: CommercialDependencyKind,
    val identity: String,
    val version: String,
    val contentHash: String?,
    val commercialTerm: CommercialTerm,
    val reviewed: Boolean,
    val license: String,
    val source: String,
    val attribution: String? = null,
    val outputRightsNote: String? = null,
    val promptContract: String? = null,
    val approved: Boolean? = null
) {
    init {
        require(identity.matches(SAFE_ID) && version.isNotBlank()) { "Commercial dependency identity is invalid" }
        require(contentHash == null || SHA_256.matches(contentHash)) { "Commercial dependency hash is invalid" }
        require(license.isNotBlank() && source.isNotBlank()) { "Commercial dependency requires license and source" }
    }
}

data class CommercialReadinessInput(val sources: List<CommercialSource>, val dependencies: List<CommercialDependency>)
data class CommercialSource(val partId: String, val sourceHash: String, val attestation: SourceRightsAttestation?)
data class CommercialReadiness(val ready: Boolean, val reasons: List<String>, val attribution: List<String>)

/** Pure policy table. It never provides legal advice or a rights-clearance conclusion. */
object CommercialReadinessEvaluator {
    fun evaluate(input: CommercialReadinessInput): CommercialReadiness {
        val reasons = buildList {
            input.sources.sortedBy { it.partId }.forEach { source ->
                when (source.attestation?.claim) {
                    SourceRightsClaim.OWNED, SourceRightsClaim.COMMERCIAL_PERMISSION, SourceRightsClaim.PUBLIC_DOMAIN -> Unit
                    SourceRightsClaim.NOT_ESTABLISHED -> add("Source '${source.partId}' is attested as rights not established.")
                    null -> add("Source '${source.partId}' has no creator rights attestation.")
                }
            }
            input.dependencies.sortedWith(compareBy(CommercialDependency::kind, CommercialDependency::identity)).forEach { dependency ->
                if (dependency.contentHash == null) add("${dependency.kind.name.lowercase()} '${dependency.identity}' has no content hash.")
                when (dependency.commercialTerm) {
                    CommercialTerm.PERMITTED -> if (!dependency.reviewed) add("${dependency.kind.name.lowercase()} '${dependency.identity}' has unreviewed commercial terms.")
                    CommercialTerm.CONDITIONAL -> add("${dependency.kind.name.lowercase()} '${dependency.identity}' has conditional commercial terms.")
                    CommercialTerm.UNKNOWN -> add("${dependency.kind.name.lowercase()} '${dependency.identity}' has unknown commercial terms.")
                    CommercialTerm.BLOCKED -> add("${dependency.kind.name.lowercase()} '${dependency.identity}' is blocked for commercial use.")
                }
            }
        }
        return CommercialReadiness(reasons.isEmpty(), reasons, input.dependencies.mapNotNull { it.attribution }.distinct().sorted())
    }
}

@Serializable
data class ProvenanceArtifact(val path: String, val sha256: String)

@Serializable
data class CommercialProvenanceManifest(
    val version: Int = 1,
    val releaseHash: String,
    val sources: List<ManifestSource>,
    val artifacts: List<ProvenanceArtifact>,
    val deterministicOperations: List<String>,
    val dependencies: List<CommercialDependency>,
    val commercialReady: Boolean,
    val reasons: List<String>,
    val attribution: List<String>,
    /** Canonical selected MIDI identity used by MIDI-first analysis/rendering. */
    val selectedMidi: List<SelectedMidiProvenance> = emptyList(),
    val disclaimer: String = COMMERCIAL_DISCLAIMER
)

@Serializable
data class ManifestSource(val partId: String, val path: String, val sha256: String, val attestation: SourceRightsAttestation?)
@Serializable
data class SelectedMidiProvenance(val partId: String, val path: String, val sha256: String, val kind: SelectedMidiArtifactKind, val profile: String? = null)

data class CommercialExportResult(val readiness: CommercialReadiness, val manifest: Path?, val report: Path?, val checklist: Path?)

/**
 * Project-confined evidence writer. Existing output is replaced only after all
 * inputs are validated and a complete temporary file has been written.
 */
class CommercialProvenanceService(private val soundLibraryRoot: Path? = null) {
    fun export(root: Path, dependencies: List<CommercialDependency> = emptyList()): CommercialExportResult {
        val projectRoot = root.toAbsolutePath().normalize()
        val project = ProjectStore.read(projectRoot).also { it.requireValid(projectRoot) }
        val master = projectRoot.resolve("output/master.wav")
        require(Files.isRegularFile(master)) { "Commercial report requires the validated output/master.wav artifact." }

        val sources = project.parts.sortedBy(Part::id).map { part ->
            val path = safeProjectFile(projectRoot, part.file)
            CommercialSource(part.id, sha256(path), part.sourceAttestation)
        }
        val selectedMidi = if (project.version >= Project.MIDI_FIRST_VERSION) {
            runCatching { project.requireSelectedMidi(projectRoot) }.getOrDefault(emptyList()).sortedBy { it.partId }.map {
                SelectedMidiProvenance(it.partId, it.projectRelativePath, it.sha256, it.kind, it.profile?.id)
            }
        } else emptyList()
        val usedDependencies = (dependencies + inferredDependencies(projectRoot)).distinctBy { Triple(it.kind, it.identity, it.version) }
        val readiness = CommercialReadinessEvaluator.evaluate(CommercialReadinessInput(sources, usedDependencies))
        val artifacts = evidenceFiles(projectRoot).map { file -> ProvenanceArtifact(relative(projectRoot, file), sha256(file)) }
        val manifest = CommercialProvenanceManifest(
            releaseHash = sha256(master),
            sources = sources.map { source ->
                val part = project.parts.first { it.id == source.partId }
                ManifestSource(source.partId, part.file, source.sourceHash, source.attestation)
            },
            artifacts = artifacts,
            deterministicOperations = project.parts.mapNotNull { it.midi?.cleanup?.profile?.name }.distinct().sorted() + "commercial-provenance-v1",
            dependencies = usedDependencies.sortedWith(compareBy(CommercialDependency::kind, CommercialDependency::identity, CommercialDependency::version)),
            commercialReady = readiness.ready,
            reasons = readiness.reasons,
            attribution = readiness.attribution,
            selectedMidi = selectedMidi
        )
        val output = projectRoot.resolve("output")
        require(Files.isRegularFile(output.resolve("release.json"))) { "Commercial report requires output/release.json release metadata." }
        val manifestPath = output.resolve(MANIFEST_FILE)
        val reportPath = output.resolve(REPORT_FILE)
        val checklistPath = output.resolve(CHECKLIST_FILE)
        atomicWrite(manifestPath, json.encodeToString(manifest))
        atomicWrite(reportPath, report(manifest))
        atomicWrite(checklistPath, checklist(manifest))
        ProjectWorkflowStore.update(projectRoot) { workflow ->
            workflow.copy(commercialProvenance = app.melotrail.arrangement.CommercialProvenanceReferences(
                WorkflowArtifactReference(relative(projectRoot, manifestPath), sha256(manifestPath)),
                WorkflowArtifactReference("output/release.json", sha256(projectRoot.resolve("output/release.json")))
            )).markCurrent(WorkflowArtifact.COMMERCIAL_EXPORT)
        }
        return CommercialExportResult(readiness, manifestPath, reportPath, checklistPath)
    }

    fun verify(root: Path): CommercialReadiness {
        val projectRoot = root.toAbsolutePath().normalize()
        val manifestPath = projectRoot.resolve("output/$MANIFEST_FILE")
        val manifest = json.decodeFromString<CommercialProvenanceManifest>(Files.readString(manifestPath, StandardCharsets.UTF_8))
        require(manifest.releaseHash == sha256(safeProjectFile(projectRoot, "output/master.wav"))) { "Commercial provenance is stale: master hash changed." }
        manifest.artifacts.forEach { artifact ->
            require(artifact.sha256 == sha256(safeProjectFile(projectRoot, artifact.path))) { "Commercial provenance is stale: ${artifact.path} hash changed." }
        }
        manifest.sources.forEach { source ->
            require(source.sha256 == sha256(safeProjectFile(projectRoot, source.path))) { "Commercial provenance is stale: ${source.path} hash changed." }
        }
        manifest.selectedMidi.forEach { selected ->
            require(selected.sha256 == sha256(safeProjectFile(projectRoot, selected.path))) { "Commercial provenance is stale: selected MIDI ${selected.path} hash changed." }
        }
        return CommercialReadinessEvaluator.evaluate(CommercialReadinessInput(
            manifest.sources.map { CommercialSource(it.partId, it.sha256, it.attestation) }, manifest.dependencies
        ))
    }

    private fun evidenceFiles(root: Path): List<Path> = EVIDENCE_DIRECTORIES.flatMap { directory ->
        val base = root.resolve(directory)
        if (!Files.isDirectory(base)) emptyList() else Files.walk(base).use { files ->
            files.filter { file ->
                Files.isRegularFile(file) && !Files.isSymbolicLink(file) && relative(root, file) !in setOf(
                    "output/$MANIFEST_FILE", "output/$REPORT_FILE", "output/$CHECKLIST_FILE"
                )
            }.toList()
        }
    }.sortedBy { relative(root, it) }

    /** Never assume a rendered sample pack is approved when its validated root was not supplied. */
    private fun inferredDependencies(projectRoot: Path): List<CommercialDependency> = buildList {
        if (Files.isDirectory(projectRoot.resolve("stems"))) {
            val library = soundLibraryRoot?.toAbsolutePath()?.normalize()
            if (library == null) {
                add(CommercialDependency(CommercialDependencyKind.SOUND_LIBRARY, "unresolved-sound-library", "unknown", null, CommercialTerm.UNKNOWN, false, "unknown", "not recorded"))
            } else {
                val descriptors = InstrumentRegistryLoader(library).load().all()
                descriptors.groupBy { it.license }.toSortedMap(compareBy { it.displayName }).forEach { (license, instruments) ->
                    val files = instruments.flatMap { listOf(it.sfzPath) + it.samplePaths }.distinct().sortedBy(Path::toString)
                    val content = sha256(library, files)
                    add(CommercialDependency(
                        CommercialDependencyKind.SOUND_LIBRARY, license.displayName.replace(Regex("[^A-Za-z0-9._:-]"), "-"), "registry-v1", content,
                        if (license.commercialUse) CommercialTerm.PERMITTED else CommercialTerm.BLOCKED,
                        license.date != null, license.license, license.source,
                        license.attributionText?.takeIf { license.attributionRequired }
                    ))
                }
            }
        }
        if (Files.isRegularFile(projectRoot.resolve("cohesion/provenance.json"))) {
            add(CommercialDependency(CommercialDependencyKind.MODEL, "cohesion-model-unregistered", "unknown", null, CommercialTerm.UNKNOWN, false, "unknown", "cohesion/provenance.json"))
        }
    }

    private fun report(manifest: CommercialProvenanceManifest): String = buildString {
        appendLine("# Melotrail commercial-readiness report")
        appendLine()
        appendLine("**Status: ${if (manifest.commercialReady) "Commercial-ready" else "Not commercial-ready"}**")
        appendLine()
        appendLine(COMMERCIAL_DISCLAIMER)
        appendLine()
        appendLine("AI transformations—including transposition, timing changes, repair, arrangement, and AI patching—do not automatically clear rights attached to an input melody.")
        appendLine()
        if (manifest.reasons.isNotEmpty()) { appendLine("## Blocking reasons"); manifest.reasons.forEach { appendLine("- $it") }; appendLine() }
        appendLine("## Required attribution")
        if (manifest.attribution.isEmpty()) appendLine("None recorded.") else manifest.attribution.forEach { appendLine("- $it") }
        appendLine(); appendLine("The machine-readable hash-bound evidence is in $MANIFEST_FILE.")
    }

    private fun checklist(manifest: CommercialProvenanceManifest): String = buildString {
        appendLine("# YouTube upload checklist")
        appendLine()
        appendLine("- [ ] Review this report; it is not legal advice, copyright clearance, Content ID clearance, or a monetization guarantee.")
        appendLine("- [ ] For AI-generated music, consider/select the YouTube Studio AI-use disclosure when the policy applies.")
        appendLine("- [ ] Add every required attribution: ${manifest.attribution.ifEmpty { listOf("none recorded") }.joinToString("; ")}.")
        appendLine("- [ ] Add original, non-mass-produced video/channel value; YouTube reviews channel-level originality and authenticity.")
        appendLine("- [ ] Re-check the official YouTube AI disclosure and monetization policies before uploading; policies can change.")
        appendLine("- [ ] Do not describe this work as copyright free solely because Melotrail processed it.")
    }

    private fun safeProjectFile(root: Path, reference: String): Path {
        val relative = Path.of(reference)
        require(!relative.isAbsolute && !reference.split('/', '\\').contains("..")) { "Commercial provenance path must be project-relative: $reference" }
        val resolved = root.resolve(relative).normalize()
        require(resolved.startsWith(root) && Files.isRegularFile(resolved) && !Files.isSymbolicLink(resolved)) { "Commercial provenance artifact is missing or unsafe: $reference" }
        return resolved
    }
    private fun relative(root: Path, path: Path) = root.relativize(path).toString().replace('\\', '/')
    private fun sha256(path: Path) = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)).joinToString("") { "%02x".format(it) }
    private fun sha256(base: Path, paths: List<Path>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        paths.forEach { path -> digest.update(base.relativize(path).toString().replace('\\', '/').toByteArray(StandardCharsets.UTF_8)); digest.update(Files.readAllBytes(path)) }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    private fun atomicWrite(path: Path, text: String) {
        Files.createDirectories(checkNotNull(path.parent)); val temporary = path.resolveSibling(".${path.fileName}.tmp")
        try { Files.writeString(temporary, text, StandardCharsets.UTF_8); try { Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) } catch (_: AtomicMoveNotSupportedException) { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING) } } finally { Files.deleteIfExists(temporary) }
    }
    private companion object {
        const val MANIFEST_FILE = "provenance-manifest.json"; const val REPORT_FILE = "commercial-report.md"; const val CHECKLIST_FILE = "youtube-upload-checklist.md"
        val EVIDENCE_DIRECTORIES = listOf("source", "midi/raw", "midi/clean", "midi/derived", "midi/feel", "analysis", "cohesion", "midi/generated", "stems", "mix", "output")
        val json = Json { prettyPrint = true; encodeDefaults = true; ignoreUnknownKeys = false }
    }
}

private val SHA_256 = Regex("[0-9a-f]{64}")
private val SAFE_ID = Regex("[A-Za-z0-9._:-]{1,160}")
private val ISO_INSTANT = Regex("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?Z")
private const val COMMERCIAL_DISCLAIMER = "This report is workflow evidence and assistance, not legal advice, copyright clearance, Content ID clearance, or a YouTube monetization guarantee."
