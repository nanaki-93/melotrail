package app.melotrail.commercial

import app.melotrail.application.PersistedMixSettings
import app.melotrail.arrangement.ArtifactRef
import app.melotrail.arrangement.Project
import app.melotrail.arrangement.ProjectStore
import app.melotrail.arrangement.ProjectWorkflowStore
import app.melotrail.arrangement.RenderInstrumentManifest
import app.melotrail.arrangement.SelectedMidiArtifactKind
import app.melotrail.arrangement.SourceLibraryProvenance
import app.melotrail.arrangement.StageRunRecord
import app.melotrail.arrangement.StageRunStatus
import app.melotrail.arrangement.StageRunStore
import app.melotrail.arrangement.StemArtifact
import app.melotrail.arrangement.StemRenderReport
import app.melotrail.arrangement.WorkflowArtifact
import app.melotrail.arrangement.WorkflowArtifactReference
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
data class SourceRightsAttestation(val claim: SourceRightsClaim, val attestedAt: String) {
    init { require(attestedAt.matches(ISO_INSTANT)) { "Source attestation date must be an ISO-8601 instant" } }
    val supportsCommercialUse get() = claim != SourceRightsClaim.NOT_ESTABLISHED
}

@Serializable
enum class CommercialTerm { PERMITTED, CONDITIONAL, UNKNOWN, BLOCKED }

@Serializable
enum class CommercialDependencyKind { MODEL, PROCESSOR, SOUND_LIBRARY, SAMPLE }

/** Snapshot of an actually used dependency. It is intentionally data-only and portable. */
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

    fun portable() = copy(source = redactPortable(source), attribution = attribution?.let(::redactPortable),
        outputRightsNote = outputRightsNote?.let(::redactPortable), promptContract = promptContract?.let(::redactPortable))
}

data class CommercialReadinessInput(
    val sources: List<CommercialSource>,
    val dependencies: List<CommercialDependency>,
    val unresolvedEvidence: List<String> = emptyList()
)
data class CommercialSource(val partId: String, val sourceHash: String, val attestation: SourceRightsAttestation?)
data class CommercialReadiness(val ready: Boolean, val reasons: List<String>, val attribution: List<String>)

/** Pure policy table. It never provides legal advice or a rights-clearance conclusion. */
object CommercialReadinessEvaluator {
    fun evaluate(input: CommercialReadinessInput): CommercialReadiness {
        val reasons = buildList {
            input.unresolvedEvidence.sorted().forEach { add("Evidence is unresolved: $it") }
            input.sources.sortedBy { it.partId }.forEach { source ->
                when (source.attestation?.claim) {
                    SourceRightsClaim.OWNED, SourceRightsClaim.COMMERCIAL_PERMISSION, SourceRightsClaim.PUBLIC_DOMAIN -> Unit
                    SourceRightsClaim.NOT_ESTABLISHED -> add("Source '${source.partId}' is attested as rights not established.")
                    null -> add("Source '${source.partId}' has no creator rights attestation.")
                }
            }
            input.dependencies.sortedWith(compareBy(CommercialDependency::kind, CommercialDependency::identity)).forEach { dependency ->
                if (dependency.contentHash == null) add("${dependency.kind.name.lowercase()} '${dependency.identity}' has no content hash.")
                if (dependency.kind == CommercialDependencyKind.MODEL &&
                    (dependency.identity.contains("unknown", ignoreCase = true) || dependency.identity.contains("fake", ignoreCase = true) || dependency.version.equals("unknown", ignoreCase = true))) {
                    add("model '${dependency.identity}' has an unknown or fake identity.")
                }
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
data class ProvenanceArtifact(val path: String, val sha256: String) {
    init { require(SAFE_RELATIVE_PATH.matches(path) && SHA_256.matches(sha256)) { "Release artifact reference is invalid" } }
}

@Serializable
data class ManifestSource(val partId: String, val path: String, val sha256: String, val attestation: SourceRightsAttestation?)

@Serializable
data class SelectedMidiProvenance(val partId: String, val path: String, val sha256: String, val kind: SelectedMidiArtifactKind, val profile: String? = null)

/** A portable revision marker. The underlying project decision remains canonical evidence. */
@Serializable
data class ReleaseDecisionRevision(val kind: String, val revision: Long, val sha256: String) {
    init { require(SAFE_ID.matches(kind) && revision >= 0 && SHA_256.matches(sha256)) { "Release decision revision is invalid" } }
}

/** A selected completed run only; retries and failures remain in the stage manifest, not this closure. */
@Serializable
data class ReleaseStageRun(
    val runId: String,
    val stage: String,
    val subject: String,
    val inputs: List<ProvenanceArtifact>,
    val outputs: List<ProvenanceArtifact>,
    val reports: List<ProvenanceArtifact>,
    val processorId: String? = null,
    val processorVersion: String? = null,
    val modelProvider: String? = null,
    val modelName: String? = null,
    val modelVersion: String? = null,
    val configurationSha256: String? = null,
    val contextSha256: String? = null,
    val seed: Long? = null,
    val schemaVersion: Int
)

/** The exact instrument/stem contribution to the final mix; never reconstructed from a live registry. */
@Serializable
data class ReleaseInstrumentUsage(
    val role: String,
    val stableInstrumentId: String,
    val stem: ProvenanceArtifact,
    val usedInFinalMix: Boolean,
    val absenceFromAudioProvable: Boolean,
    val decisionSha256: List<String>,
    val registryVersion: Int,
    val registrySha256: String,
    val assets: List<ProvenanceArtifact>,
    val license: ReleaseLicenseSnapshot,
    val sourceLibrary: SourceLibraryProvenance
) {
    init {
        require(SAFE_ID.matches(role) && SAFE_ID.matches(stableInstrumentId) && registryVersion > 0 && SHA_256.matches(registrySha256)) {
            "Release instrument usage identity is invalid"
        }
        require(decisionSha256.isNotEmpty() && decisionSha256 == decisionSha256.sorted() && decisionSha256.distinct().size == decisionSha256.size && decisionSha256.all(SHA_256::matches)) {
            "Release instrument decision evidence is invalid"
        }
        require(assets.isNotEmpty()) { "Release instrument assets are missing" }
    }
}

@Serializable
data class ReleaseLicenseSnapshot(
    val displayName: String,
    val source: String,
    val provenance: String,
    val license: String,
    val commercialUse: Boolean,
    val attributionRequired: Boolean,
    val attributionText: String? = null,
    val redistribution: String
) {
    init {
        require(displayName.isNotBlank() && source.isNotBlank() && provenance.isNotBlank() && license.isNotBlank() && redistribution.isNotBlank()) {
            "Release license snapshot is incomplete"
        }
        require(!attributionRequired || !attributionText.isNullOrBlank()) { "Release attribution snapshot is incomplete" }
    }
}

/** A hash-bound user-facing audio copy, never a mutable renderer or registry artifact. */
@Serializable
data class ReleaseAudioExport(
    val id: String,
    val relativePath: String,
    val sha256: String,
    val format: String
) {
    init {
        require(SAFE_AUDIO_EXPORT_ID.matches(id) && SAFE_RELATIVE_PATH.matches(relativePath) && SHA_256.matches(sha256)) {
            "Release audio export is invalid"
        }
        require(format in setOf("WAV", "MP3")) { "Release audio export format is invalid" }
    }
}

/** The immutable, copy-ready credits artifact paired to one final audio export. */
@Serializable
data class ReleaseCreditsArtifact(
    val id: String,
    val relativePath: String,
    val sha256: String,
    val usedInstrumentIds: List<String>,
    val attributionEntryHashes: List<String>,
    val policyVersion: String,
    val templateVersion: String,
    val audioExportId: String,
    val audioExportSha256: String
) {
    init {
        require(SAFE_CREDITS_ID.matches(id) && SAFE_RELATIVE_PATH.matches(relativePath) && SHA_256.matches(sha256)) {
            "Release credits artifact is invalid"
        }
        require(usedInstrumentIds == usedInstrumentIds.distinct().sorted() && usedInstrumentIds.all(SAFE_ID::matches)) {
            "Release credits instrument IDs are invalid"
        }
        require(attributionEntryHashes == attributionEntryHashes.distinct().sorted() && attributionEntryHashes.all(SHA_256::matches)) {
            "Release credits attribution hashes are invalid"
        }
        require(policyVersion == RELEASE_CREDITS_POLICY_VERSION && templateVersion == RELEASE_CREDITS_TEMPLATE_VERSION &&
            SAFE_AUDIO_EXPORT_ID.matches(audioExportId) && SHA_256.matches(audioExportSha256)) {
            "Release credits policy or audio pairing is invalid"
        }
    }
}

@Serializable
data class ReleaseReportReferences(val manifest: String, val report: String, val checklist: String) {
    init { require(listOf(manifest, report, checklist).all(SAFE_RELATIVE_PATH::matches)) { "Release report path is invalid" } }
}

/** Immutable selected-lineage closure. Older v2 closures remain readable evidence. */
@Serializable
data class CommercialProvenanceManifest(
    val version: Int = VERSION,
    val releaseId: String,
    val releaseHash: String,
    val sources: List<ManifestSource>,
    val artifacts: List<ProvenanceArtifact>,
    val decisions: List<ReleaseDecisionRevision>,
    val stageRuns: List<ReleaseStageRun>,
    val selectedMidi: List<SelectedMidiProvenance>,
    val instrumentUsage: List<ReleaseInstrumentUsage>,
    val dependencies: List<CommercialDependency>,
    val unresolvedEvidence: List<String>,
    val commercialReady: Boolean,
    val reasons: List<String>,
    val attribution: List<String>,
    val reports: ReleaseReportReferences,
    val audioExports: List<ReleaseAudioExport> = emptyList(),
    val credits: List<ReleaseCreditsArtifact> = emptyList(),
    val disclaimer: String = COMMERCIAL_DISCLAIMER
) {
    init {
        require(version in 2..VERSION && SAFE_RELEASE_ID.matches(releaseId) && SHA_256.matches(releaseHash)) { "Release manifest identity is invalid" }
        require(artifacts.map(ProvenanceArtifact::path).distinct().size == artifacts.size) { "Release manifest repeats an artifact" }
        require(unresolvedEvidence.all { it.length <= 240 && it.none(Char::isISOControl) }) { "Release manifest unresolved evidence is invalid" }
        require(audioExports.map(ReleaseAudioExport::id).distinct().size == audioExports.size && credits.map(ReleaseCreditsArtifact::id).distinct().size == credits.size) {
            "Release manifest repeats an export or credits artifact"
        }
    }

    companion object { const val VERSION = 3 }
}

data class CommercialExportResult(val readiness: CommercialReadiness, val manifest: Path?, val report: Path?, val checklist: Path?, val releaseId: String? = null)

/** Result of VerifyReleaseLineage(releaseId). All paths are project-relative report references. */
data class ReleaseLineageVerification(
    val releaseId: String,
    val closed: Boolean,
    val missingDependencies: List<String>,
    val tamperedDependencies: List<String>,
    val unresolvedEvidence: List<String>,
    val commercialReady: Boolean,
    val reportReferences: ReleaseReportReferences?
)

/**
 * Project-confined release evidence writer. It closes only selected artifacts,
 * never scans stale directories or consults a live library during verification.
 */
class CommercialProvenanceService(@Suppress("UNUSED_PARAMETER") private val soundLibraryRoot: Path? = null) {
    fun export(root: Path, dependencies: List<CommercialDependency> = emptyList()): CommercialExportResult {
        val projectRoot = root.toAbsolutePath().normalize()
        val project = ProjectStore.read(projectRoot).also { it.requireValid(projectRoot) }
        require(WorkflowArtifact.MASTER !in project.workflow.stale && WorkflowArtifact.RELEASE !in project.workflow.stale) {
            "Commercial evidence requires a current master and release metadata. Rebuild the selected release first."
        }
        val master = safeProjectFile(projectRoot, "output/master.wav")
        val release = safeProjectFile(projectRoot, "output/release.json")
        val sources = project.parts.sortedBy { it.id }.map { part ->
            ManifestSource(part.id, part.file, sha256(safeProjectFile(projectRoot, part.file)), part.sourceAttestation)
        }
        val unresolved = mutableListOf<String>()
        val selectedMidi = selectedMidi(projectRoot, project, unresolved)
        val artifacts = linkedMapOf<String, ProvenanceArtifact>()
        fun include(path: String) {
            val file = safeProjectFile(projectRoot, path)
            artifacts[path] = ProvenanceArtifact(path, sha256(file))
        }
        include("output/master.wav"); include("output/release.json")
        sources.forEach { include(it.path) }
        selectedMidi.forEach { include(it.path) }
        releaseInput(projectRoot, release, unresolved)?.let(::include)
        selectedWorkflowArtifacts(project).forEach { reference -> include(reference.file) }
        val instrumentUsage = usedInstruments(projectRoot, artifacts, unresolved)
        val selectedRuns = selectedRuns(projectRoot, project, artifacts.values.toList(), unresolved)
        selectedRuns.flatMap { it.inputs + it.outputs + it.reports }.forEach { artifact -> artifacts[artifact.path] = artifact }
        val decisions = decisions(project, projectRoot, unresolved)
        val usedDependencies = dependencies.map(CommercialDependency::portable) +
            instrumentUsage.filter(ReleaseInstrumentUsage::usedInFinalMix).map(::instrumentDependency) + selectedRuns.flatMap(::runDependencies)
        val normalizedDependencies = usedDependencies.distinctBy { listOf(it.kind.name, it.identity, it.version, it.contentHash) }
            .sortedWith(compareBy(CommercialDependency::kind, CommercialDependency::identity, CommercialDependency::version))
        val releaseId = releaseId(master, release, artifacts.values, decisions, selectedRuns, instrumentUsage)
        val reports = ReleaseReportReferences(
            "$RELEASES_DIRECTORY/$releaseId/$MANIFEST_FILE",
            "$RELEASES_DIRECTORY/$releaseId/$REPORT_FILE",
            "$RELEASES_DIRECTORY/$releaseId/$CHECKLIST_FILE"
        )
        val readiness = CommercialReadinessEvaluator.evaluate(CommercialReadinessInput(
            sources.map { CommercialSource(it.partId, it.sha256, it.attestation) }, normalizedDependencies, unresolved.distinct().sorted()
        ))
        val manifest = CommercialProvenanceManifest(
            releaseId = releaseId, releaseHash = sha256(master), sources = sources,
            artifacts = artifacts.values.sortedBy(ProvenanceArtifact::path), decisions = decisions,
            stageRuns = selectedRuns, selectedMidi = selectedMidi, instrumentUsage = instrumentUsage,
            dependencies = normalizedDependencies, unresolvedEvidence = unresolved.distinct().sorted(),
            commercialReady = readiness.ready, reasons = readiness.reasons, attribution = readiness.attribution, reports = reports
        )
        val manifestPath = projectRoot.resolve(reports.manifest)
        val reportPath = projectRoot.resolve(reports.report)
        val checklistPath = projectRoot.resolve(reports.checklist)
        publishImmutable(manifestPath, json.encodeToString(manifest))
        publishImmutable(reportPath, report(manifest))
        publishImmutable(checklistPath, checklist(manifest))
        ProjectWorkflowStore.update(projectRoot) { workflow ->
            workflow.copy(commercialProvenance = app.melotrail.arrangement.CommercialProvenanceReferences(
                WorkflowArtifactReference(reports.manifest, sha256(manifestPath)),
                WorkflowArtifactReference("output/release.json", sha256(release))
            )).markCurrent(WorkflowArtifact.COMMERCIAL_EXPORT)
        }
        return CommercialExportResult(readiness, manifestPath, reportPath, checklistPath, releaseId)
    }

    fun verify(root: Path): CommercialReadiness {
        val projectRoot = root.toAbsolutePath().normalize()
        val project = ProjectStore.read(projectRoot)
        val manifest = project.workflow.commercialProvenance?.manifest
            ?: throw IllegalArgumentException("No selected release evidence exists. Create commercial evidence from Export.")
        val releaseId = readManifest(projectRoot.resolve(manifest.file)).releaseId
        val result = verifyReleaseLineage(projectRoot, releaseId)
        require(result.closed) { "Release lineage is not closed: ${(result.missingDependencies + result.tamperedDependencies).joinToString()}." }
        val selected = readManifest(projectRoot.resolve(manifest.file))
        return CommercialReadiness(selected.commercialReady && result.commercialReady, selected.reasons, selected.attribution)
    }

    /** Typed VerifyReleaseLineage(releaseId) contract. It never mutates project evidence. */
    fun verifyReleaseLineage(root: Path, releaseId: String): ReleaseLineageVerification {
        val projectRoot = root.toAbsolutePath().normalize()
        if (!SAFE_RELEASE_ID.matches(releaseId)) return ReleaseLineageVerification(releaseId, false, listOf("release id"), emptyList(), emptyList(), false, null)
        val path = projectRoot.resolve("$RELEASES_DIRECTORY/$releaseId/$MANIFEST_FILE").normalize()
        if (!path.startsWith(projectRoot) || !Files.isRegularFile(path)) {
            return ReleaseLineageVerification(releaseId, false, listOf("release manifest"), emptyList(), emptyList(), false, null)
        }
        val manifest = runCatching { readManifest(path) }.getOrElse {
            return ReleaseLineageVerification(releaseId, false, emptyList(), listOf("release manifest"), emptyList(), false, null)
        }
        val missing = mutableListOf<String>()
        val tampered = mutableListOf<String>()
        manifest.artifacts.forEach { artifact ->
            val file = safeProjectFileOrNull(projectRoot, artifact.path)
            when {
                file == null -> missing += artifact.path
                sha256(file) != artifact.sha256 -> tampered += artifact.path
            }
        }
        manifest.audioExports.forEach { audio ->
            val file = safeProjectFileOrNull(projectRoot, audio.relativePath)
            when {
                file == null -> missing += audio.relativePath
                sha256(file) != audio.sha256 -> tampered += audio.relativePath
            }
        }
        manifest.credits.forEach { credits ->
            val audio = manifest.audioExports.singleOrNull { it.id == credits.audioExportId }
            if (audio == null || audio.sha256 != credits.audioExportSha256) tampered += "credits audio pairing ${credits.id}"
            val file = safeProjectFileOrNull(projectRoot, credits.relativePath)
            when {
                file == null -> missing += credits.relativePath
                sha256(file) != credits.sha256 -> tampered += credits.relativePath
            }
        }
        val project = runCatching { ProjectStore.read(projectRoot) }.getOrNull()
        val stored = project?.workflow?.commercialProvenance?.manifest
        if (stored == null || stored.file != manifest.reports.manifest) missing += "selected release reference"
        else if (stored.sha256 != sha256(path)) tampered += "selected release manifest"
        val readiness = CommercialReadinessEvaluator.evaluate(CommercialReadinessInput(
            manifest.sources.map { CommercialSource(it.partId, it.sha256, it.attestation) }, manifest.dependencies, manifest.unresolvedEvidence
        ))
        if (readiness.ready != manifest.commercialReady) tampered += "commercial readiness result"
        return ReleaseLineageVerification(
            manifest.releaseId, missing.isEmpty() && tampered.isEmpty(), missing.distinct().sorted(), tampered.distinct().sorted(),
            manifest.unresolvedEvidence, readiness.ready, manifest.reports
        )
    }

    private fun selectedMidi(root: Path, project: Project, unresolved: MutableList<String>): List<SelectedMidiProvenance> =
        runCatching {
            project.requireSelectedMidi(root).sortedBy { it.partId }.map {
                SelectedMidiProvenance(it.partId, it.projectRelativePath, it.sha256, it.kind, it.profile?.id)
            }
        }.getOrElse { unresolved += "selected MIDI evidence is unavailable"; emptyList() }

    private fun releaseInput(root: Path, release: Path, unresolved: MutableList<String>): String? = runCatching {
        val input = json.parseToJsonElement(Files.readString(release, StandardCharsets.UTF_8)).jsonObject["inputArtifact"]?.jsonPrimitive?.content
        require(!input.isNullOrBlank() && safeProjectFileOrNull(root, input) != null) { "release input is invalid" }
        input
    }.getOrElse { unresolved += "release metadata has no validated mastering input"; null }

    private fun selectedWorkflowArtifacts(project: Project): List<WorkflowArtifactReference> = buildList {
        if (WorkflowArtifact.ARRANGEMENT !in project.workflow.stale) project.workflow.arrangement?.arrangement?.let(::add)
        if (WorkflowArtifact.COHESION !in project.workflow.stale) project.workflow.cohesion?.let { refs ->
            add(refs.plan); refs.occurrences.filter { it.approved }.forEach { add(it.result) }; refs.boundaries.mapNotNull { it.approved }.forEach(::add)
        }
        if (WorkflowArtifact.HUMANIZATION !in project.workflow.stale) project.workflow.humanization?.let { refs -> add(refs.report); refs.artifacts.forEach { add(it.input); add(it.output) } }
    }.distinctBy(WorkflowArtifactReference::file)

    private fun usedInstruments(root: Path, artifacts: MutableMap<String, ProvenanceArtifact>, unresolved: MutableList<String>): List<ReleaseInstrumentUsage> {
        val reportPath = root.resolve(STEM_RENDER_REPORT)
        if (!Files.exists(reportPath) && !Files.isDirectory(root.resolve("stems"))) return emptyList()
        if (!Files.isRegularFile(reportPath)) { unresolved += "final stem render report is missing"; return emptyList() }
        val report = runCatching { json.decodeFromString<StemRenderReport>(Files.readString(reportPath, StandardCharsets.UTF_8)) }.getOrElse {
            unresolved += "final stem render report is invalid"; return emptyList()
        }
        artifacts[STEM_RENDER_REPORT] = ProvenanceArtifact(STEM_RENDER_REPORT, sha256(reportPath))
        val settings = runCatching {
            val settingsPath = root.resolve(MIX_SETTINGS)
            if (Files.isRegularFile(settingsPath)) json.decodeFromString<PersistedMixSettings>(Files.readString(settingsPath, StandardCharsets.UTF_8)).also { it.requireValid() }
            else PersistedMixSettings()
        }.getOrElse { unresolved += "persisted mix settings are invalid"; PersistedMixSettings() }
        if (Files.isRegularFile(root.resolve(MIX_SETTINGS))) artifacts[MIX_SETTINGS] = ProvenanceArtifact(MIX_SETTINGS, sha256(root.resolve(MIX_SETTINGS)))
        val soloed = report.stems.any { settings.tracks[it.name]?.solo == true }
        return report.stems.sortedBy(StemArtifact::name).mapNotNull { stem ->
            val manifest = report.instruments.singleOrNull { it.role == stem.name && it.stableInstrumentId == stem.stableInstrumentId }
            if (manifest == null) { unresolved += "instrument snapshot for stem '${stem.name}' is missing"; return@mapNotNull null }
            if (stem.stableInstrumentId.isBlank()) { unresolved += "stable instrument ID for stem '${stem.name}' is missing"; return@mapNotNull null }
            val stemPath = safeProjectFileOrNull(root, stem.path)
            if (stemPath == null || sha256(stemPath) != stem.fingerprint) { unresolved += "stem '${stem.name}' no longer matches render evidence"; return@mapNotNull null }
            artifacts[stem.path] = ProvenanceArtifact(stem.path, stem.fingerprint)
            val used = settings.tracks[stem.name]?.let { !it.muted && (!soloed || it.solo) } ?: !soloed
            usage(stem, manifest, report, used)
        }
    }

    private fun usage(stem: StemArtifact, instrument: RenderInstrumentManifest, report: StemRenderReport, used: Boolean): ReleaseInstrumentUsage =
        ReleaseInstrumentUsage(
            role = stem.name, stableInstrumentId = stem.stableInstrumentId, stem = ProvenanceArtifact(stem.path, stem.fingerprint),
            usedInFinalMix = used, absenceFromAudioProvable = !used,
            decisionSha256 = instrument.decisionSha256, registryVersion = report.registryVersion, registrySha256 = report.registrySha256,
            assets = instrument.assets.map { ProvenanceArtifact("asset-${it.kind}-${it.sha256}", it.sha256) },
            license = ReleaseLicenseSnapshot(instrument.license.displayName, redactPortable(instrument.license.source), instrument.license.provenance,
                instrument.license.license, instrument.license.commercialUse, instrument.license.attributionRequired,
                instrument.license.attributionText?.let(::redactPortable), instrument.license.redistribution),
            sourceLibrary = SourceLibraryProvenance(instrument.sourceLibrary.id, instrument.sourceLibrary.name, instrument.sourceLibrary.version, redactPortable(instrument.sourceLibrary.source))
        )

    private fun selectedRuns(root: Path, project: Project, anchors: List<ProvenanceArtifact>, unresolved: MutableList<String>): List<ReleaseStageRun> {
        if (project.envelope.stageRuns.index == null) return emptyList()
        val records = runCatching { StageRunStore().read(root, project.envelope.stageRuns) }.getOrElse {
            unresolved += "stage manifest is invalid"; return emptyList()
        }
        val byOutput = records.filter { it.status == StageRunStatus.COMPLETED }.flatMap { run -> run.outputArtifacts.map { it.path to run } }.toMap()
        val selected = linkedSetOf<String>()
        fun visit(path: String) {
            val run = byOutput[path] ?: return
            if (!selected.add(run.runId)) return
            run.inputArtifacts.forEach { visit(it.path) }
        }
        anchors.forEach { visit(it.path) }
        return records.filter { it.runId in selected && it.status == StageRunStatus.COMPLETED }.map(::stageRun).sortedBy(ReleaseStageRun::runId)
    }

    private fun stageRun(record: StageRunRecord) = ReleaseStageRun(
        record.runId, record.stage.name.lowercase(), record.subject.toString(), record.inputArtifacts.map(::artifact),
        record.outputArtifacts.map(::artifact), record.reportArtifacts.map(::artifact), record.processor?.id, record.processor?.version,
        record.model?.provider, record.model?.model, record.model?.version, record.configurationSha256, record.contextSha256, record.seed, record.schemaVersion
    )

    private fun artifact(reference: ArtifactRef) = ProvenanceArtifact(reference.path, reference.sha256)

    private fun decisions(project: Project, root: Path, unresolved: MutableList<String>): List<ReleaseDecisionRevision> = buildList {
        project.envelope.compositionSettings?.let { settings ->
            if (settings.complete) add(ReleaseDecisionRevision("settings", settings.decisionRevision, settings.decisionSha256))
            else unresolved += "composition settings decision is incomplete"
        } ?: run { unresolved += "composition settings decision is missing" }
        project.envelope.harmony?.let { add(ReleaseDecisionRevision("harmony", it.revision.toLong(), sha256Utf8(it.toString()))) }
            ?: run { unresolved += "harmony decision is missing" }
        project.parts.sortedBy { it.id }.forEach { part -> add(ReleaseDecisionRevision("part-${part.id}", part.revision, sha256Utf8("${part.id}|${part.file}|${part.name}|${part.sectionType.value}"))) }
        project.envelope.structureOccurrences.sortedBy { it.id }.forEach { occurrence ->
            add(ReleaseDecisionRevision("structure-${occurrence.id}", occurrence.revision, sha256Utf8(occurrence.toString())))
        }
        project.envelope.arrangementAssignments.sortedWith(compareBy({ it.occurrenceId }, { it.instrumentId })).forEach { assignment ->
            add(ReleaseDecisionRevision("assignment-${assignment.occurrenceId}-${assignment.instrumentId}", 1, assignment.decisionSha256))
        }
        project.workflow.arrangement?.let { add(ReleaseDecisionRevision("arrangement-approval", 1, it.arrangement.sha256)) }
        project.workflow.cohesion?.let { refs -> if (refs.approved) add(ReleaseDecisionRevision("cohesion-approval", 1, refs.inputSha256)) }
        project.workflow.humanization?.let { refs -> add(ReleaseDecisionRevision("humanization", 1, refs.inputsSha256)) }
        val mix = root.resolve(MIX_SETTINGS)
        if (Files.isRegularFile(mix)) add(ReleaseDecisionRevision("mix", 1, sha256(mix)))
    }.sortedBy(ReleaseDecisionRevision::kind)

    private fun runDependencies(run: ReleaseStageRun): List<CommercialDependency> = buildList {
        if (run.processorId != null && run.processorVersion != null) add(CommercialDependency(
            CommercialDependencyKind.PROCESSOR, run.processorId, run.processorVersion,
            run.configurationSha256, CommercialTerm.PERMITTED, true, "MIT", "Melotrail local processor"
        ))
        if (run.modelProvider != null && run.modelName != null) add(CommercialDependency(
            CommercialDependencyKind.MODEL, "${run.modelProvider}-${run.modelName}", run.modelVersion ?: "unknown", null,
            CommercialTerm.UNKNOWN, false, "unknown", "model identity recorded without reviewed license"
        ))
    }

    private fun instrumentDependency(usage: ReleaseInstrumentUsage): CommercialDependency {
        val license = usage.license
        val term = when {
            !license.commercialUse || license.license.contains("NC", ignoreCase = true) -> CommercialTerm.BLOCKED
            license.license.contains("CC0", ignoreCase = true) || license.provenance == "generated-original" -> CommercialTerm.PERMITTED
            license.license.contains("CC BY", ignoreCase = true) && !license.attributionText.isNullOrBlank() -> CommercialTerm.PERMITTED
            license.license.contains("CC BY", ignoreCase = true) -> CommercialTerm.CONDITIONAL
            else -> CommercialTerm.UNKNOWN
        }
        return CommercialDependency(
            CommercialDependencyKind.SAMPLE, usage.stableInstrumentId, usage.sourceLibrary.version,
            sha256Utf8(usage.assets.joinToString("|") { it.sha256 }), term, term == CommercialTerm.PERMITTED,
            license.license, license.source, license.attributionText?.takeIf { license.attributionRequired }
        )
    }

    private fun releaseId(master: Path, release: Path, artifacts: Collection<ProvenanceArtifact>, decisions: List<ReleaseDecisionRevision>, runs: List<ReleaseStageRun>, instruments: List<ReleaseInstrumentUsage>): String {
        val seed = buildString {
            append(sha256(master)).append('|').append(sha256(release)).append('|')
            artifacts.sortedBy(ProvenanceArtifact::path).forEach { append(it.path).append(':').append(it.sha256).append(';') }
            decisions.forEach { append(it.kind).append(':').append(it.sha256).append(';') }
            runs.forEach { append(it.runId).append(';') }
            instruments.forEach { append(it.role).append(':').append(it.stem.sha256).append(';') }
        }
        return "release-${sha256Utf8(seed).take(32)}"
    }

    private fun report(manifest: CommercialProvenanceManifest): String = buildString {
        appendLine("# Melotrail commercial-readiness report")
        appendLine(); appendLine("**Status: ${if (manifest.commercialReady) "Commercial-ready evidence complete" else "Commercial-ready blocked"}**")
        appendLine(); appendLine(COMMERCIAL_DISCLAIMER); appendLine()
        appendLine("Release ID: `${manifest.releaseId}`")
        if (manifest.reasons.isNotEmpty()) { appendLine(); appendLine("## Unresolved actions"); manifest.reasons.forEach { appendLine("- $it") } }
        appendLine(); appendLine("## Required attribution")
        if (manifest.attribution.isEmpty()) appendLine("None recorded.") else manifest.attribution.forEach { appendLine("- $it") }
        appendLine(); appendLine("This immutable, hash-bound selected lineage is in `${MANIFEST_FILE}`.")
    }

    private fun checklist(manifest: CommercialProvenanceManifest): String = buildString {
        appendLine("# YouTube upload checklist"); appendLine()
        appendLine("- [ ] Review the selected-lineage report; it is not legal advice, copyright clearance, Content ID clearance, or a monetization guarantee.")
        appendLine("- [ ] Resolve every listed evidence action before calling this release commercial-ready.")
        appendLine("- [ ] Add every required attribution: ${manifest.attribution.ifEmpty { listOf("none recorded") }.joinToString("; ")}.")
        appendLine("- [ ] Re-check the official YouTube AI disclosure and monetization policies before uploading; policies can change.")
    }

    private fun readManifest(path: Path): CommercialProvenanceManifest = json.decodeFromString(Files.readString(path, StandardCharsets.UTF_8))
    private fun safeProjectFile(root: Path, reference: String): Path = requireNotNull(safeProjectFileOrNull(root, reference)) { "Release artifact is missing or unsafe: $reference" }
    private fun safeProjectFileOrNull(root: Path, reference: String): Path? = runCatching {
        require(SAFE_RELATIVE_PATH.matches(reference))
        val resolved = root.resolve(reference).normalize()
        require(resolved.startsWith(root) && Files.isRegularFile(resolved) && !Files.isSymbolicLink(resolved))
        resolved
    }.getOrNull()
    private fun sha256(path: Path): String = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)).joinToString("") { "%02x".format(it) }
    private fun sha256Utf8(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }
    private fun publishImmutable(path: Path, text: String) {
        Files.createDirectories(checkNotNull(path.parent))
        if (Files.exists(path)) { require(Files.readString(path, StandardCharsets.UTF_8) == text) { "Release evidence '$path' is immutable; create a new release lineage." }; return }
        val temporary = path.resolveSibling(".${path.fileName}.tmp")
        try {
            Files.writeString(temporary, text, StandardCharsets.UTF_8)
            try { Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE) }
            catch (_: AtomicMoveNotSupportedException) { Files.move(temporary, path) }
        } finally { Files.deleteIfExists(temporary) }
    }

    private companion object {
        const val RELEASES_DIRECTORY = "output/releases"
        const val MANIFEST_FILE = "release-manifest.json"
        const val REPORT_FILE = "commercial-report.md"
        const val CHECKLIST_FILE = "youtube-upload-checklist.md"
        const val STEM_RENDER_REPORT = "stems/stem-render.json"
        const val MIX_SETTINGS = "mix/settings.json"
        val json = Json { prettyPrint = true; encodeDefaults = true; ignoreUnknownKeys = false }
    }
}

/** Command deliberately contains no caller-supplied credits text or filesystem path. */
data class GenerateReleaseCredits(val releaseId: String, val audioExportId: String)

data class ReleaseCreditsPreview(
    val filename: String,
    val text: String,
    val usedInstrumentIds: List<String>,
    val attributionEntryHashes: List<String>
)

/**
 * Derives copy-ready instrument credits exclusively from a frozen release manifest.
 * It never consults the current instrument registry or candidate list.
 */
class ReleaseCreditsService {
    fun preview(root: Path, releaseId: String, audioFilename: String): ReleaseCreditsPreview =
        derive(loadSelectedManifest(root, releaseId), creditsFilename(audioFilename))

    /** Pure preview for an already decoded immutable manifest; useful to UI and tests. */
    fun preview(manifest: CommercialProvenanceManifest, audioFilename: String): ReleaseCreditsPreview =
        derive(manifest, creditsFilename(audioFilename))

    /** Publishes the credits file specified by an already frozen release revision. */
    fun generate(root: Path, request: GenerateReleaseCredits): ReleaseCreditsArtifact {
        require(SAFE_RELEASE_ID.matches(request.releaseId) && SAFE_AUDIO_EXPORT_ID.matches(request.audioExportId)) { "Release credits request is invalid" }
        val projectRoot = root.toAbsolutePath().normalize()
        val manifest = read(projectRoot, request.releaseId)
        validateFrozenLineage(projectRoot, manifest)
        val audio = manifest.audioExports.singleOrNull { it.id == request.audioExportId }
            ?: throw IllegalArgumentException("Release audio export is not part of the frozen release manifest.")
        val artifact = manifest.credits.singleOrNull { it.audioExportId == audio.id }
            ?: throw IllegalArgumentException("Frozen release manifest has no credits artifact for this audio export.")
        val audioPath = safeFile(projectRoot, audio.relativePath)
        require(digest(audioPath) == audio.sha256 && artifact.audioExportSha256 == audio.sha256) { "Release audio export no longer matches frozen lineage." }
        val preview = derive(manifest, Path.of(artifact.relativePath).fileName.toString())
        require(digestText(preview.text) == artifact.sha256 && preview.usedInstrumentIds == artifact.usedInstrumentIds &&
            preview.attributionEntryHashes == artifact.attributionEntryHashes) { "Frozen release credits metadata is inconsistent." }
        publishImmutable(projectRoot.resolve(artifact.relativePath), preview.text)
        return artifact
    }

    /** Creates a new immutable revision after the final audio output has been validated. */
    fun prepare(root: Path, parentReleaseId: String, audioPath: Path, audioSha256: String, format: String): GenerateReleaseCredits {
        val projectRoot = root.toAbsolutePath().normalize()
        require(SAFE_RELEASE_ID.matches(parentReleaseId) && SHA_256.matches(audioSha256)) { "Commercial export lineage is invalid" }
        val parent = loadSelectedManifest(projectRoot, parentReleaseId)
        require(parent.commercialReady) { "Commercial export is blocked: ${parent.reasons.joinToString(" ")}" }
        require(format in setOf("WAV", "MP3")) { "Commercial export format is invalid" }
        val normalizedAudio = audioPath.toAbsolutePath().normalize()
        val output = projectRoot.resolve("output").normalize()
        require(normalizedAudio.parent == output && normalizedAudio.startsWith(projectRoot)) { "Commercial export must be in the project output folder." }
        val relativeAudio = projectRoot.relativize(normalizedAudio).toString().replace('\\', '/')
        require(SAFE_RELATIVE_PATH.matches(relativeAudio)) { "Commercial export path is invalid" }
        val audioId = "audio-" + digestText("$relativeAudio|$audioSha256").take(32)
        val audio = ReleaseAudioExport(audioId, relativeAudio, audioSha256, format)
        val preview = derive(parent, creditsFilename(normalizedAudio.fileName.toString()))
        val relativeCredits = "output/${preview.filename}"
        val creditsHash = digestText(preview.text)
        val credits = ReleaseCreditsArtifact(
            id = "credits-" + digestText("$audioId|$creditsHash").take(32), relativePath = relativeCredits, sha256 = creditsHash,
            usedInstrumentIds = preview.usedInstrumentIds, attributionEntryHashes = preview.attributionEntryHashes,
            policyVersion = RELEASE_CREDITS_POLICY_VERSION, templateVersion = RELEASE_CREDITS_TEMPLATE_VERSION,
            audioExportId = audio.id, audioExportSha256 = audio.sha256
        )
        require(!Files.exists(projectRoot.resolve(relativeCredits))) { "Credits target already exists; choose a different export filename." }
        val revisionId = "release-" + digestText("${parent.releaseId}|${audio.id}|${credits.sha256}").take(32)
        val reports = ReleaseReportReferences(
            "output/releases/$revisionId/release-manifest.json", "output/releases/$revisionId/commercial-report.md",
            "output/releases/$revisionId/youtube-upload-checklist.md"
        )
        val revision = parent.copy(
            version = CommercialProvenanceManifest.VERSION, releaseId = revisionId, reports = reports,
            audioExports = (parent.audioExports + audio).distinctBy(ReleaseAudioExport::id).sortedBy(ReleaseAudioExport::id),
            credits = (parent.credits + credits).distinctBy(ReleaseCreditsArtifact::id).sortedBy(ReleaseCreditsArtifact::id)
        )
        publishImmutable(projectRoot.resolve(reports.manifest), json.encodeToString(revision))
        publishImmutable(projectRoot.resolve(reports.report), report(revision))
        publishImmutable(projectRoot.resolve(reports.checklist), checklist(revision))
        return GenerateReleaseCredits(revisionId, audioId)
    }

    /** Selects the revision only after its audio and credits files both exist. */
    fun selectGeneratedRevision(root: Path, request: GenerateReleaseCredits): ReleaseCreditsArtifact {
        val projectRoot = root.toAbsolutePath().normalize()
        val credits = generate(projectRoot, request)
        val manifest = read(projectRoot, request.releaseId)
        val manifestPath = projectRoot.resolve(manifest.reports.manifest)
        ProjectWorkflowStore.update(projectRoot) { workflow ->
            workflow.copy(commercialProvenance = app.melotrail.arrangement.CommercialProvenanceReferences(
                WorkflowArtifactReference(manifest.reports.manifest, digest(manifestPath)),
                WorkflowArtifactReference("output/release.json", digest(safeFile(projectRoot, "output/release.json")))
            )).markCurrent(WorkflowArtifact.COMMERCIAL_EXPORT)
        }
        return credits
    }

    private fun loadSelectedManifest(root: Path, releaseId: String): CommercialProvenanceManifest {
        val projectRoot = root.toAbsolutePath().normalize()
        val project = ProjectStore.read(projectRoot)
        val selected = project.workflow.commercialProvenance?.manifest
            ?: throw IllegalArgumentException("No selected release evidence exists. Create commercial evidence from Export.")
        val manifest = read(projectRoot, releaseId)
        require(selected.file == manifest.reports.manifest && selected.sha256 == digest(projectRoot.resolve(selected.file))) {
            "Selected release evidence is stale or tampered. Create commercial evidence again."
        }
        val verification = CommercialProvenanceService().verifyReleaseLineage(projectRoot, releaseId)
        require(verification.closed) { "Selected release lineage is incomplete or tampered. Create commercial evidence again." }
        return manifest
    }

    private fun read(root: Path, releaseId: String): CommercialProvenanceManifest {
        val path = root.resolve("output/releases/$releaseId/release-manifest.json").normalize()
        require(path.startsWith(root) && Files.isRegularFile(path) && !Files.isSymbolicLink(path)) { "Release manifest is missing." }
        return json.decodeFromString(Files.readString(path, StandardCharsets.UTF_8))
    }

    private fun derive(manifest: CommercialProvenanceManifest, filename: String): ReleaseCreditsPreview {
        val used = manifest.instrumentUsage.filter { it.usedInFinalMix || !it.absenceFromAudioProvable }
            .sortedWith(compareBy(ReleaseInstrumentUsage::stableInstrumentId, ReleaseInstrumentUsage::role))
        val blocks = used.map { usage ->
            val license = usage.license
            val dependency = manifest.dependencies.singleOrNull { it.kind == CommercialDependencyKind.SAMPLE && it.identity == usage.stableInstrumentId }
            require(dependency != null && dependency.commercialTerm == CommercialTerm.PERMITTED && dependency.reviewed) {
                "Instrument '${usage.stableInstrumentId}' is not admitted for commercial release."
            }
            val attribution = license.attributionText?.let(::normalizeAttribution)
            require(!license.attributionRequired || !attribution.isNullOrBlank()) {
                "Instrument '${usage.stableInstrumentId}' requires complete attribution."
            }
            if (license.attributionRequired) {
                require(normalizeAttribution(requireNotNull(dependency.attribution)) == attribution) {
                    "Instrument '${usage.stableInstrumentId}' has contradictory attribution evidence."
                }
                usage.stableInstrumentId to requireNotNull(attribution)
            } else null
        }.filterNotNull()
        val distinct = blocks.groupBy({ it.second }, { it.first }).map { (text, ids) -> ids.min() to text }
            .sortedWith(compareBy<Pair<String, String>>({ it.second }, { it.first }))
        val text = if (distinct.isEmpty()) NO_ATTRIBUTION_TEXT + "\n" else distinct.joinToString("\n\n", postfix = "\n") { it.second }
        return ReleaseCreditsPreview(filename, text, used.map(ReleaseInstrumentUsage::stableInstrumentId).distinct().sorted(),
            distinct.map { digestText(it.second) }.distinct().sorted())
    }

    private fun creditsFilename(audioFilename: String): String {
        val base = audioFilename.substringBeforeLast('.', audioFilename).trim()
        require(base.isNotBlank()) { "Export filename has no usable base." }
        val sanitized = base.map { if (it.isLetterOrDigit() || it == '-' || it == '_') it else '-' }.joinToString("")
            .replace(Regex("-+"), "-").trim('-').take(120)
        require(sanitized.isNotBlank()) { "Export filename has no safe credits base." }
        return "$sanitized-credits.txt"
    }

    private fun normalizeAttribution(value: String): String {
        require(value.length <= 8_000 && value.none { it.isISOControl() && it != '\n' && it != '\r' && it != '\t' }) { "Attribution text is invalid." }
        return value.replace("\r\n", "\n").replace('\r', '\n').lineSequence().joinToString("\n") { it.trimEnd() }.trim()
    }

    private fun safeFile(root: Path, relative: String): Path {
        require(SAFE_RELATIVE_PATH.matches(relative)) { "Release artifact path is invalid." }
        val file = root.resolve(relative).normalize()
        require(file.startsWith(root) && Files.isRegularFile(file) && !Files.isSymbolicLink(file)) { "Release artifact is missing or unsafe: $relative" }
        return file
    }

    private fun validateFrozenLineage(root: Path, manifest: CommercialProvenanceManifest) {
        manifest.artifacts.forEach { artifact ->
            val file = safeFile(root, artifact.path)
            require(digest(file) == artifact.sha256) { "Frozen release lineage is stale or tampered: ${artifact.path}" }
        }
        require(manifest.releaseHash == digest(safeFile(root, "output/master.wav"))) {
            "Frozen release master is stale or tampered."
        }
    }

    private fun publishImmutable(path: Path, text: String) {
        Files.createDirectories(requireNotNull(path.parent))
        if (Files.exists(path)) { require(Files.readString(path, StandardCharsets.UTF_8) == text) { "Release evidence '$path' is immutable; create a new release lineage." }; return }
        val temporary = path.resolveSibling(".${path.fileName}.${java.util.UUID.randomUUID()}.tmp")
        try {
            Files.writeString(temporary, text, StandardCharsets.UTF_8)
            try { Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE) }
            catch (_: AtomicMoveNotSupportedException) { Files.move(temporary, path) }
        } finally { Files.deleteIfExists(temporary) }
    }

    private fun report(manifest: CommercialProvenanceManifest) = "# Melotrail commercial-readiness report\n\n**Status: Commercial-ready evidence complete**\n\n$COMMERCIAL_DISCLAIMER\n\nRelease ID: `${manifest.releaseId}`\n\nCredits are paired to ${manifest.credits.size} audio export(s).\n"
    private fun checklist(manifest: CommercialProvenanceManifest) = "# YouTube upload checklist\n\n- [ ] Copy the required instrument attribution from the hash-paired credits text.\n- [ ] Review release `${manifest.releaseId}`; it is not legal advice or a monetization guarantee.\n"
    private fun digest(path: Path): String = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)).joinToString("") { "%02x".format(it) }
    private fun digestText(text: String): String = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private companion object {
        val json = Json { prettyPrint = true; encodeDefaults = true; ignoreUnknownKeys = false }
        const val NO_ATTRIBUTION_TEXT = "No instrument attribution required."
    }
}

private fun redactPortable(value: String): String = value.replace(ABSOLUTE_PATH, "[redacted-path]").replace(SECRET, "[redacted]")
private val SHA_256 = Regex("[0-9a-f]{64}")
private val SAFE_ID = Regex("[A-Za-z0-9._:-]{1,160}")
private val SAFE_RELEASE_ID = Regex("release-[0-9a-f]{32}")
private val SAFE_AUDIO_EXPORT_ID = Regex("audio-[0-9a-f]{32}")
private val SAFE_CREDITS_ID = Regex("credits-[0-9a-f]{32}")
private val SAFE_RELATIVE_PATH = Regex("(?!.*(?:^|/)\\.\\.(?:/|$))[A-Za-z0-9._/-]{1,240}")
private val ISO_INSTANT = Regex("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?Z")
private val ABSOLUTE_PATH = Regex("(?:[A-Za-z]:\\\\|/)(?:Users|home|private|var|tmp|opt)(?:[/\\\\][^\\s]*)?", RegexOption.IGNORE_CASE)
private val SECRET = Regex("(?i)(?:api[_-]?key|token|secret|password)\\s*[:=]\\s*[^\\s,;]+")
private const val COMMERCIAL_DISCLAIMER = "This report is workflow evidence and assistance, not legal advice, copyright clearance, Content ID clearance, or a YouTube monetization guarantee."
private const val RELEASE_CREDITS_POLICY_VERSION = "instrument-credits-policy-v1"
private const val RELEASE_CREDITS_TEMPLATE_VERSION = "instrument-credits-template-v1"
