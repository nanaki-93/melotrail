package app.melotrail.arrangement

import app.melotrail.preparation.MidiTimeMappingStore
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import javax.sound.midi.MetaMessage
import javax.sound.midi.MidiEvent
import javax.sound.midi.MidiSystem
import javax.sound.midi.Sequence
import kotlinx.serialization.json.Json

/**
 * The only boundary for the MIDI artifact selected by a MIDI-first part.
 *
 * Cleanup/report code intentionally addresses raw and cleaned evidence directly. Every
 * semantic consumer of the user's current source must use this type instead, so a Lo-fi
 * selection cannot silently fall back to cleaned MIDI.
 */
class SelectedMidiArtifactResolver(
    private val compositionProfiles: app.melotrail.profile.CompositionProfileCatalog = app.melotrail.profile.BundledCompositionProfileCatalog.load()
) {
    fun resolve(projectRoot: Path, project: Project, partId: String): SelectedMidiArtifact =
        resolve(projectRoot, project, project.parts.singleOrNull { it.id == partId }
            ?: throw IllegalArgumentException("Unknown MIDI part '$partId'."))

    /** Resolve the selected chain through Enhance, before the final optional Feel transform. */
    fun resolveBeforeFeel(projectRoot: Path, project: Project, partId: String): SelectedMidiArtifact =
        resolveBeforeFeel(projectRoot, project, project.parts.singleOrNull { it.id == partId }
            ?: throw IllegalArgumentException("Unknown MIDI part '$partId'."))

    /** Resolve the selected chain through Enhance, before the final optional Feel transform. */
    fun resolveBeforeFeel(projectRoot: Path, project: Project, part: SongPart): SelectedMidiArtifact =
        resolveInternal(projectRoot, project, part, includeFeel = false)

    /** Resolve the immutable MIDI baseline used by Technical Correction, without reusing a prior correction or optional AI stage. */
    fun resolveCorrectionBaseline(projectRoot: Path, project: Project, part: SongPart): SelectedMidiArtifact {
        val midi = requireNotNull(part.midi) { "Part '${part.id}' has no MIDI references." }
        val baselinePart = part.copy(midi = midi.copy(
            technicalCorrectionSelection = TechnicalCorrectionSelection.BASE,
            aiFixSelection = MidiAiFixSelection.PENDING,
            enhancementSelection = EnhancementSelection.PENDING,
            analysisInput = MidiAnalysisInput.CURRENT,
            feel = null
        ))
        val baselineProject = project.copy(parts = project.parts.map { candidate ->
            if (candidate.id == part.id) baselinePart else candidate
        })
        return resolveInternal(projectRoot, baselineProject, baselinePart, includeFeel = false)
    }

    fun resolve(projectRoot: Path, project: Project, part: SongPart): SelectedMidiArtifact =
        resolveInternal(projectRoot, project, part, includeFeel = true)

    /** One canonical transformation order: transposed -> corrected -> AI Fix -> Enhance -> Feel. */
    private fun resolveInternal(projectRoot: Path, project: Project, part: SongPart, includeFeel: Boolean): SelectedMidiArtifact {
        val root = projectRoot.toAbsolutePath().normalize()
        val rootReal = root.toRealPath()
        val midi = requireNotNull(part.midi) { "Part '${part.id}' has no MIDI references." }
        val cleanedReference = requireNotNull(midi.clean) {
            "Part '${part.id}' has not been cleaned. Run Clean MIDI before continuing."
        }
        val cleaned = resolveFile(root, rootReal, cleanedReference, "cleaned MIDI")
        val cleanupFreshness = cleanupFreshness(root, part, midi, cleanedReference)
        val normalizedReference = midi.normalized
        val normalized = normalizedReference?.let { resolveFile(root, rootReal, it, "normalized MIDI") }
        val normalizationConfig = MidiNormalizationPolicy.resolve(project, compositionProfiles)
        if (normalized != null) {
            val report = requireNotNull(midi.normalization) { "Part '${part.id}' has normalized MIDI without a report." }
            require(MidiNormalizationReportStore.isCurrent(root, part.id, cleaned, normalized, normalizationConfig, report)) {
                "Part '${part.id}' has stale normalized MIDI evidence. Run Normalize MIDI again."
            }
        }
        val transposedReference = midi.transposed
        val transposed = transposedReference?.let { resolveFile(root, rootReal, it, "transposed MIDI") }
        if (part.sourceKeyEvidence != null && normalized != null) {
            val sourceKey = part.sourceKeyEvidence.effectiveKey
                ?: throw IllegalArgumentException("Part '${part.id}' needs source-key confirmation before transposition.")
            val projectKey = project.envelope.compositionSettings?.takeIf { it.complete }?.key
                ?: throw IllegalArgumentException("Project Setup must define a key before transposition.")
            require(transposed != null && midi.transposition != null) { "Part '${part.id}' has no current transposed MIDI. Run Transpose MIDI." }
            require(MidiTranspositionReportStore.isCurrent(root, part.id, normalized, transposed, sourceKey, projectKey, midi.transposition)) {
                "Part '${part.id}' has stale transposed MIDI evidence. Run Transpose MIDI again."
            }
        }
        val originalBasePath = transposed ?: normalized ?: cleaned
        val originalBaseReference = transposedReference ?: normalizedReference ?: cleanedReference
        val originalBaseSha256 = sha256(originalBasePath)
        val originalBaseKind = when {
            transposed != null -> SelectedMidiBaseKind.TRANSPOSED
            normalized != null -> SelectedMidiBaseKind.NORMALIZED
            else -> SelectedMidiBaseKind.CLEANED
        }
        val mappedBase = timingMappedBase(root, rootReal, project, part,
            BaseCandidate(originalBaseReference, originalBasePath, originalBaseSha256, originalBaseKind))
        val base = mappedBase ?: BaseCandidate(originalBaseReference, originalBasePath, originalBaseSha256, originalBaseKind)
        val correctedBase = when (midi.technicalCorrectionSelection) {
            TechnicalCorrectionSelection.BASE -> base
            TechnicalCorrectionSelection.CORRECTED -> {
                val correction = requireNotNull(midi.technicalCorrection) { "Part '${part.id}' has no technical-correction evidence." }
                correction.requireCanonical(part.id)
                require(correction.input.file == base.reference && correction.input.sha256 == base.sha256) {
                    "Corrected MIDI is stale for part '${part.id}'; recreate correction from the current baseline."
                }
                val output = resolveFile(root, rootReal, correction.output.file, "corrected MIDI")
                require(sha256(output) == correction.output.sha256) { "Corrected MIDI is stale for part '${part.id}'; recreate correction." }
                readMidi(output, part.id)
                BaseCandidate(correction.output.file, output, correction.output.sha256, SelectedMidiBaseKind.CORRECTED)
            }
        }
        val aiFixBase = when (midi.aiFixSelection) {
            // The Melody Parts presentation keeps this stage ordered. A pending
            // current decision uses its corrected baseline.
            MidiAiFixSelection.PENDING -> correctedBase
            MidiAiFixSelection.SKIP -> correctedBase
            MidiAiFixSelection.APPROVED -> {
                val references = requireNotNull(midi.aiFix) { "Part '${part.id}' has no approved AI-fix references." }
                references.requireCanonical(part.id)
                require(references.inputSha256 == correctedBase.sha256) {
                    "Approved AI fix is stale for part '${part.id}'; keep corrected MIDI or regenerate the AI fix."
                }
                val approved = requireNotNull(references.approved) { "Part '${part.id}' has no approved AI-fix artifact." }
                val path = resolveFile(root, rootReal, approved.file, "approved AI-fix MIDI")
                val hash = sha256(path)
                require(hash == approved.sha256) {
                    "Approved AI fix is stale for part '${part.id}'; keep corrected MIDI or regenerate the AI fix."
                }
                readMidi(path, part.id)
                BaseCandidate(approved.file, path, hash, SelectedMidiBaseKind.APPROVED_AI_FIX)
            }
        }
        val upstream = when (midi.enhancementSelection) {
            EnhancementSelection.PENDING, EnhancementSelection.CORRECTED -> fromBase(aiFixBase)
            EnhancementSelection.NO_OP, EnhancementSelection.ENHANCED -> {
                val enhancement = requireNotNull(midi.enhancement) { "Part '${part.id}' has no enhancement evidence." }
                enhancement.requireCanonical(part.id)
                require(enhancement.approval == EnhancementApproval.APPROVED) {
                    "Enhanced MIDI is a draft or was rejected; preview it and approve it before selecting it."
                }
                require(enhancement.input.file == aiFixBase.reference && enhancement.input.sha256 == aiFixBase.sha256) {
                    "Enhanced MIDI is stale for part '${part.id}'; select the prior MIDI or run enhancement again."
                }
                val output = resolveFile(root, rootReal, enhancement.output.file, "enhanced MIDI")
                require(sha256(output) == enhancement.output.sha256) { "Enhanced MIDI is stale for part '${part.id}'; select the prior MIDI or run enhancement again." }
                val report = readEnhancementReport(root, enhancement)
                val noOp = report.appliedEdits.isEmpty()
                if (midi.enhancementSelection == EnhancementSelection.NO_OP) {
                    require(noOp && enhancement.output.sha256 == aiFixBase.sha256) {
                        "No-op enhancement evidence must retain the exact upstream MIDI hash."
                    }
                    Candidate(aiFixBase.reference, aiFixBase.path, SelectedMidiArtifactKind.NO_OP, null, null)
                } else {
                    require(!noOp) { "Zero-edit enhancement must be selected as NO_OP, not ENHANCED." }
                    Candidate(enhancement.output.file, output, SelectedMidiArtifactKind.ENHANCED, null, null)
                }
            }
        }
        val selected = if (!includeFeel || midi.analysisInput == MidiAnalysisInput.CURRENT) upstream else {
            val feel = requireNotNull(midi.feel) { "Part '${part.id}' has no current Lo-fi MIDI Feel artifact." }
            val input = WorkflowArtifactReference(upstream.reference, sha256(upstream.path))
            val derived = resolveFile(root, rootReal, feel.derived.file, "Lo-fi MIDI Feel")
            require(MidiFeelReportStore.isCurrent(root, part.id, input, feel)) {
                "Lo-fi MIDI Feel artifact is missing, malformed, or stale for part '${part.id}'. Regenerate Lo-fi Feel from the current selected upstream MIDI."
            }
            Candidate(feel.derived.file, derived, SelectedMidiArtifactKind.LOFI_FEEL, feel.profile, MidiFeelReportStore.read(root, feel.report).version)
        }
        val sequence = readMidi(selected.path, part.id)
        return SelectedMidiArtifact(
            projectRelativePath = selected.reference,
            path = selected.path,
            partId = part.id,
            kind = selected.kind,
            profile = selected.profile,
            profileVersion = selected.profileVersion,
            sha256 = sha256(selected.path),
            ppq = sequence.resolution,
            timing = MidiTimingSummary(tempoMap(sequence), timeSignatures(sequence)),
            cleanupFreshness = cleanupFreshness,
            baseKind = aiFixBase.kind,
            loFiFreshness = if (selected.kind == SelectedMidiArtifactKind.LOFI_FEEL) MidiLoFiFreshness.CURRENT else MidiLoFiFreshness.NOT_SELECTED
        )
    }

    private fun cleanupFreshness(root: Path, part: SongPart, midi: MidiReferences, cleanedReference: String): MidiCleanupFreshness {
        require(midi.raw != null && midi.cleanup != null && midi.quality != null) {
            "Part '${part.id}' has incomplete MIDI cleanup provenance."
        }
        require(MidiQualityReportStore.isCurrent(root, part.id, midi.raw, cleanedReference, midi.cleanup, midi.quality)) {
            "Part '${part.id}' has stale cleaned MIDI evidence. Run Clean MIDI again."
        }
        require(MidiQualityReportStore.isApproved(root, midi.quality, midi.cleanApproval)) {
            "Part '${part.id}' needs current approval of its Clean MIDI evidence."
        }
        return MidiCleanupFreshness.CURRENT
    }

    private fun fromBase(base: BaseCandidate): Candidate = Candidate(
        base.reference,
        base.path,
        when (base.kind) {
            SelectedMidiBaseKind.CLEANED -> SelectedMidiArtifactKind.CLEANED
            SelectedMidiBaseKind.NORMALIZED -> SelectedMidiArtifactKind.NORMALIZED
            SelectedMidiBaseKind.TRANSPOSED -> SelectedMidiArtifactKind.TRANSPOSED
            SelectedMidiBaseKind.TIMING_MAPPED -> SelectedMidiArtifactKind.TIMING_MAPPED
            SelectedMidiBaseKind.CORRECTED -> SelectedMidiArtifactKind.CORRECTED
            SelectedMidiBaseKind.APPROVED_AI_FIX -> SelectedMidiArtifactKind.APPROVED_AI_FIX
        },
        null,
        null
    )

    /** A user-reviewed QP-003 candidate is selected only while every source, project-grid, and byte binding remains current. */
    private fun timingMappedBase(
        root: Path,
        rootReal: Path,
        project: Project,
        part: SongPart,
        source: BaseCandidate
    ): BaseCandidate? {
        val mapping = part.timingMappingEvidence ?: return null
        require(mapping.sourceMidi.path == source.reference && mapping.sourceMidi.sha256 == source.sha256) {
            "Timing-mapped MIDI is stale for part '${part.id}'; align the current baseline again."
        }
        val report = MidiTimeMappingStore.readReport(root, mapping.report)
        require(report.partId == part.id && report.sourceMidi == mapping.sourceMidi && report.sourceTimingReport == mapping.sourceTimingReport &&
            report.output.sha256 == mapping.candidate.sha256 && report.sourceSha256 == part.sourceTimingEvidence?.sourceSha256) {
            "Timing-mapped MIDI evidence is inconsistent for part '${part.id}'."
        }
        project.envelope.compositionSettings?.takeIf { it.complete }?.let { settings ->
            require(report.targetTempoBpm.toDouble() == settings.tempo.bpm && report.targetMeterNumerator == settings.timeSignature.numerator &&
                report.targetMeterDenominator == settings.timeSignature.denominator) {
                "Timing-mapped MIDI is stale for the current project tempo or meter."
            }
        }
        val candidate = resolveFile(root, rootReal, mapping.candidate.path, "timing-mapped MIDI")
        require(sha256(candidate) == mapping.candidate.sha256) { "Timing-mapped MIDI is stale for part '${part.id}'." }
        readMidi(candidate, part.id)
        return BaseCandidate(mapping.candidate.path, candidate, mapping.candidate.sha256, SelectedMidiBaseKind.TIMING_MAPPED)
    }

    private fun readEnhancementReport(root: Path, enhancement: EnhancementReferences): EnhancementEditReport = try {
        val reportPath = root.resolve(enhancement.report.file).normalize()
        require(reportPath.startsWith(root) && Files.isRegularFile(reportPath) && sha256(reportPath) == enhancement.report.sha256) {
            "Enhancement report is missing or stale."
        }
        json.decodeFromString(EnhancementEditReport.serializer(), Files.readString(reportPath)).also { report ->
            require(report.inputSha256 == enhancement.input.sha256 && report.outputSha256 == enhancement.output.sha256 && report.contextSha256 == enhancement.contextSha256) {
                "Enhancement report does not bind the selected input, output, and context."
            }
        }
    } catch (error: IllegalArgumentException) { throw error
    } catch (error: Exception) { throw IllegalArgumentException("Enhancement report is malformed.", error) }

    private fun resolveFile(root: Path, rootReal: Path, reference: String, label: String): Path {
        val relative = try { Path.of(reference) } catch (error: Exception) {
            throw IllegalArgumentException("$label path is invalid: $reference", error)
        }
        require(reference.isNotBlank() && !relative.isAbsolute) { "$label path must be project-relative." }
        val path = root.resolve(relative).normalize()
        require(path.startsWith(root) && Files.isRegularFile(path)) { "$label is missing: $reference" }
        require(path.toRealPath().startsWith(rootReal)) { "$label path escapes the project root: $reference" }
        return path
    }

    private fun readMidi(path: Path, partId: String): Sequence = try {
        require(Files.size(path) >= 14) { "Selected MIDI for '$partId' is malformed." }
        Files.newInputStream(path).use { require(it.readNBytes(4).decodeToString() == "MThd") { "Selected MIDI for '$partId' is malformed." } }
        MidiSystem.getSequence(path.toFile()).also { sequence ->
            require(sequence.divisionType == Sequence.PPQ && sequence.resolution > 0) { "Selected MIDI for '$partId' must use positive PPQ timing." }
        }
    } catch (error: IllegalArgumentException) {
        throw error
    } catch (error: Exception) {
        throw IllegalArgumentException("Selected MIDI for '$partId' is malformed.", error)
    }

    private fun tempoMap(sequence: Sequence): List<MidiTempoChange> = sequence.events().mapNotNull { event ->
        val message = event.message as? MetaMessage ?: return@mapNotNull null
        if (message.type != 0x51 || message.data.size != 3) return@mapNotNull null
        val micros = ((message.data[0].toInt() and 0xff) shl 16) or ((message.data[1].toInt() and 0xff) shl 8) or (message.data[2].toInt() and 0xff)
        require(micros > 0) { "Selected MIDI has an invalid tempo event." }
        MidiTempoChange(event.tick, 60_000_000.0 / micros)
    }.sortedBy { it.tick }.distinctBy { it.tick }.toList().let { map -> if (map.firstOrNull()?.tick == 0L) map else listOf(MidiTempoChange(0, 120.0, true)) + map }

    private fun timeSignatures(sequence: Sequence): List<MidiTimeSignature> = sequence.events().mapNotNull { event ->
        val message = event.message as? MetaMessage ?: return@mapNotNull null
        if (message.type != 0x58 || message.data.size < 2) return@mapNotNull null
        val numerator = message.data[0].toInt() and 0xff
        val exponent = message.data[1].toInt() and 0xff
        require(numerator > 0 && exponent in 0..5) { "Selected MIDI has an unsupported time signature." }
        MidiTimeSignature(event.tick, numerator, 1 shl exponent)
    }.sortedBy { it.tick }.distinctBy { it.tick }.toList().let { map -> if (map.firstOrNull()?.tick == 0L) map else listOf(MidiTimeSignature(0, 4, 4, true)) + map }

    private fun Sequence.events(): kotlin.sequences.Sequence<MidiEvent> = tracks.asSequence().flatMap { track -> (0 until track.size()).asSequence().map(track::get) }
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
    private data class BaseCandidate(val reference: String, val path: Path, val sha256: String, val kind: SelectedMidiBaseKind)
    private data class Candidate(val reference: String, val path: Path, val kind: SelectedMidiArtifactKind, val profile: MidiFeelProfile?, val profileVersion: Int?)

    private companion object { val json = Json { explicitNulls = false; ignoreUnknownKeys = false } }
}

enum class SelectedMidiBaseKind { CLEANED, NORMALIZED, TRANSPOSED, TIMING_MAPPED, CORRECTED, APPROVED_AI_FIX }
enum class SelectedMidiArtifactKind { CLEANED, NORMALIZED, TRANSPOSED, TIMING_MAPPED, CORRECTED, APPROVED_AI_FIX, NO_OP, ENHANCED, LOFI_FEEL }
enum class MidiCleanupFreshness { CURRENT, STALE }
enum class MidiLoFiFreshness { CURRENT, NOT_SELECTED }
data class MidiTimingSummary(val tempoMap: List<MidiTempoChange>, val timeSignatures: List<MidiTimeSignature>)
data class SelectedMidiArtifact(
    val projectRelativePath: String,
    val path: Path,
    val partId: String,
    val kind: SelectedMidiArtifactKind,
    val profile: MidiFeelProfile?,
    val profileVersion: Int?,
    val sha256: String,
    val ppq: Int,
    val timing: MidiTimingSummary,
    val cleanupFreshness: MidiCleanupFreshness,
    val baseKind: SelectedMidiBaseKind,
    val loFiFreshness: MidiLoFiFreshness
)
