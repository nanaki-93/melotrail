package app.melotrail.application

import app.melotrail.arrangement.AnalysisKind
import app.melotrail.arrangement.GeneratedMidiArtifactReference
import app.melotrail.arrangement.MidiAnalysis
import app.melotrail.arrangement.MidiChord
import app.melotrail.arrangement.MidiTempoChange
import app.melotrail.arrangement.MidiTimeSignature
import app.melotrail.arrangement.Project
import app.melotrail.arrangement.ProjectStore
import app.melotrail.arrangement.SelectedMidiArtifact
import app.melotrail.arrangement.SelectedMidiArtifactResolver
import app.melotrail.arrangement.SectionTypeId
import app.melotrail.arrangement.StructureOccurrence
import app.melotrail.arrangement.WorkflowArtifact
import app.melotrail.arrangement.WorkflowArtifactReference
import app.melotrail.arrangement.toMusicalKeyOrNull
import app.melotrail.harmony.ChordEvent
import app.melotrail.harmony.ChordQuality
import app.melotrail.music.MusicalKey
import app.melotrail.music.Tempo
import app.melotrail.music.TimeSignature
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import javax.sound.midi.MidiSystem

/**
 * A derived, read-only view of the musical facts declared by a schema-v4
 * project. It is deliberately not a project artifact and contains no absolute
 * paths, timestamps, or mutable workflow state.
 */
@Serializable
data class CanonicalMusicalAuthority(
    val schemaVersion: Int = SCHEMA_VERSION,
    val projectKey: MusicalKey,
    val tempo: Tempo,
    val meter: TimeSignature,
    val occurrenceTimeline: List<MusicalOccurrence>,
    val harmonicTimeline: HarmonicTimeline,
    val selectedPartArtifacts: List<CanonicalSelectedPartArtifact>,
    val analyzedFacts: List<CanonicalAnalyzedPartFacts>,
    val melodyEvidenceReferences: List<MelodyEvidenceReference>,
    val diagnostics: List<MusicalAuthorityDiagnostic>,
    val contextSha256: String
) {
    companion object { const val SCHEMA_VERSION = 1 }
}

/** Stable occurrence identity and its half-open global bar/tick bounds. */
@Serializable
data class MusicalOccurrence(
    val occurrenceId: String,
    val partId: String,
    val sectionType: SectionTypeId,
    val startBar: Long,
    val endBar: Long,
    val startTick: Long,
    val endTick: Long
)

@Serializable
data class CanonicalSelectedPartArtifact(
    val partId: String,
    val projectRelativePath: String,
    val sha256: String,
    val ppq: Int,
    val kind: String
)

/** Analysis remains descriptive evidence; it never replaces project settings. */
@Serializable
data class CanonicalAnalyzedPartFacts(
    val partId: String,
    val selectedMidiSha256: String,
    val analysisSha256: String,
    val analysis: MidiAnalysis
)

/** Task 121 can extend this reference with anchor detail without changing authority ownership. */
@Serializable
data class MelodyEvidenceReference(
    val partId: String,
    val analysisSha256: String
)

@Serializable
enum class MusicalAuthorityDiagnosticKind {
    ANALYZED_KEY_CONFLICT,
    ANALYZED_TEMPO_CONFLICT,
    ANALYZED_METER_CONFLICT,
    ANALYZED_HARMONY_CONFLICT
}

@Serializable
data class MusicalAuthorityDiagnostic(
    val kind: MusicalAuthorityDiagnosticKind,
    val partId: String,
    val declaredValue: String,
    val analyzedValue: String
)

/** One half-open chord span. [bar] is zero-based in the full arrangement. */
@Serializable
data class HarmonicTimelineEntry(
    val occurrenceId: String,
    val sectionType: SectionTypeId,
    val chord: CanonicalChord,
    val bar: Long,
    val startTick: Long,
    val endTick: Long
)

@Serializable
data class CanonicalChord(val rootChromatic: Int, val rootSymbol: String, val quality: ChordQuality) {
    val symbol: String get() = rootSymbol + quality.symbolSuffix
}

/**
 * Deterministic global harmonic lookup. Tick and bar intervals are half-open:
 * a boundary belongs to the entry beginning at that boundary.
 */
@Serializable
data class HarmonicTimeline(
    val ppq: Int,
    val meter: TimeSignature,
    val entries: List<HarmonicTimelineEntry>
) {
    init {
        require(ppq > 0) { "Harmonic timeline PPQ must be positive." }
        require(entries.isNotEmpty()) { "Harmonic timeline must contain at least one chord span." }
        require(entries.zipWithNext().all { (left, right) -> left.endTick == right.startTick && left.bar + 1 == right.bar }) {
            "Harmonic timeline bounds must be contiguous."
        }
        require(entries.all { it.endTick > it.startTick }) { "Harmonic timeline contains an empty chord span." }
    }

    fun forOccurrence(occurrenceId: String): List<HarmonicTimelineEntry> =
        entries.filter { it.occurrenceId == occurrenceId }.also {
            require(it.isNotEmpty()) { "Unknown harmonic occurrence '$occurrenceId'." }
        }

    fun atBar(bar: Long): HarmonicTimelineEntry =
        entries.singleOrNull { it.bar == bar }
            ?: throw IllegalArgumentException("No harmonic chord exists at bar $bar.")

    fun atTick(tick: Long): HarmonicTimelineEntry =
        entries.singleOrNull { tick >= it.startTick && tick < it.endTick }
            ?: throw IllegalArgumentException("No harmonic chord exists at tick $tick.")

    fun forNoteInterval(startTick: Long, endTick: Long): List<HarmonicTimelineEntry> {
        require(startTick >= 0 && endTick > startTick) { "Note interval must be a positive half-open tick range." }
        return entries.filter { it.startTick < endTick && startTick < it.endTick }.also {
            require(it.isNotEmpty()) { "Note interval [$startTick, $endTick) is outside the harmonic timeline." }
        }
    }
}

/** Small, serializable projections. Consumers receive only their required evidence. */
@Serializable
data class PartRepairProjection(
    val schemaVersion: Int = CanonicalMusicalAuthority.SCHEMA_VERSION,
    val contextSha256: String,
    val part: CanonicalSelectedPartArtifact,
    val projectKey: MusicalKey,
    val tempo: Tempo,
    val meter: TimeSignature,
    val occurrences: List<MusicalOccurrence>,
    val harmony: List<HarmonicTimelineEntry>,
    val melodyEvidence: List<MelodyEvidenceReference>
)

@Serializable
data class PartEnhancementProjection(
    val schemaVersion: Int = CanonicalMusicalAuthority.SCHEMA_VERSION,
    val contextSha256: String,
    val part: CanonicalSelectedPartArtifact,
    val projectKey: MusicalKey,
    val tempo: Tempo,
    val meter: TimeSignature,
    val occurrences: List<MusicalOccurrence>,
    val harmony: List<HarmonicTimelineEntry>,
    val analysis: CanonicalAnalyzedPartFacts,
    val melodyEvidence: List<MelodyEvidenceReference>
)

@Serializable
data class ArrangementGenerationProjection(
    val schemaVersion: Int = CanonicalMusicalAuthority.SCHEMA_VERSION,
    val contextSha256: String,
    val projectKey: MusicalKey,
    val tempo: Tempo,
    val meter: TimeSignature,
    val occurrences: List<MusicalOccurrence>,
    val harmony: List<HarmonicTimelineEntry>,
    val selectedParts: List<CanonicalSelectedPartArtifact>,
    val analyzedFacts: List<CanonicalAnalyzedPartFacts>
)

@Serializable
data class ValidatedGeneratedRoleArtifact(val id: String, val artifact: WorkflowArtifactReference)

@Serializable
data class CohesionProjection(
    val schemaVersion: Int = CanonicalMusicalAuthority.SCHEMA_VERSION,
    val contextSha256: String,
    val occurrences: List<MusicalOccurrence>,
    val harmony: List<HarmonicTimelineEntry>,
    val approvedArrangement: WorkflowArtifactReference,
    val generatedRoles: List<ValidatedGeneratedRoleArtifact>
)

@Serializable
data class WholeSongAnalysisProjection(
    val schemaVersion: Int = CanonicalMusicalAuthority.SCHEMA_VERSION,
    val contextSha256: String,
    val projectKey: MusicalKey,
    val tempo: Tempo,
    val meter: TimeSignature,
    val occurrences: List<MusicalOccurrence>,
    val harmony: List<HarmonicTimelineEntry>,
    val selectedParts: List<CanonicalSelectedPartArtifact>,
    val analyzedFacts: List<CanonicalAnalyzedPartFacts>,
    val melodyEvidence: List<MelodyEvidenceReference>,
    val approvedArrangement: WorkflowArtifactReference,
    val generatedRoles: List<ValidatedGeneratedRoleArtifact>
)

/** Application boundary for the one canonical musical context. */
class MusicalAuthorityBuilder(
    private val selectedMidiArtifactResolver: SelectedMidiArtifactResolver = SelectedMidiArtifactResolver()
) {
    fun build(projectRoot: Path): CanonicalMusicalAuthority {
        val root = projectRoot.toAbsolutePath().normalize()
        val project = ProjectStore.read(root)
        project.requireValid(root)
        val settings = requireNotNull(project.envelope.compositionSettings) {
            "Project Setup must declare key, tempo, and meter before musical context can be built."
        }
        require(settings.key.isExecutable) { "Project key mode '${settings.key.modeId.value}' is not executable." }
        val structure = project.envelope.structureOccurrences
        require(structure.isNotEmpty()) { "Save a non-empty Structure before building musical context." }

        val selected = project.parts.sortedBy { it.id }.associate { part ->
            part.id to selectedMidiArtifactResolver.resolve(root, project, part)
        }
        val analyses = project.parts.sortedBy { it.id }.associate { part ->
            part.id to readCurrentAnalysis(root, part.id, requireNotNull(part.analysis) {
                "Missing MIDI analysis for part '${part.id}'. Run part analyze first."
            }, selected.getValue(part.id))
        }
        val globalPpq = canonicalPpq(selected.values.map(SelectedMidiArtifact::ppq), settings.timeSignature)
        val occurrences = occurrenceTimeline(project, structure, analyses, globalPpq)
        val harmony = HarmonicTimeline(globalPpq, settings.timeSignature, harmonicEntries(project, occurrences, globalPpq))
        val selectedArtifacts = selected.values.map(::selectedArtifact).sortedBy(CanonicalSelectedPartArtifact::partId)
        val facts = analyses.values.map { it.facts }.sortedBy(CanonicalAnalyzedPartFacts::partId)
        val melodyEvidence = facts.map { MelodyEvidenceReference(it.partId, it.analysisSha256) }
        val diagnostics = diagnostics(project, occurrences, analyses, harmony).take(MAX_DIAGNOSTICS)
        val unhashed = CanonicalMusicalAuthority(
            projectKey = settings.key, tempo = settings.tempo, meter = settings.timeSignature,
            occurrenceTimeline = occurrences, harmonicTimeline = harmony, selectedPartArtifacts = selectedArtifacts,
            analyzedFacts = facts, melodyEvidenceReferences = melodyEvidence, diagnostics = diagnostics, contextSha256 = ""
        )
        return unhashed.copy(contextSha256 = sha256(canonicalJson.encodeToString(AuthorityHashPayload.from(unhashed)).toByteArray(Charsets.UTF_8)))
    }

    fun partRepair(projectRoot: Path, partId: String): PartRepairProjection {
        val authority = build(projectRoot)
        val part = authority.selectedPartArtifacts.singleOrNull { it.partId == partId }
            ?: throw IllegalArgumentException("Unknown MIDI part '$partId'.")
        val occurrences = authority.occurrenceTimeline.filter { it.partId == partId }
        return PartRepairProjection(
            contextSha256 = authority.contextSha256, part = part, projectKey = authority.projectKey,
            tempo = authority.tempo, meter = authority.meter, occurrences = occurrences,
            harmony = occurrences.flatMap { authority.harmonicTimeline.forOccurrence(it.occurrenceId) },
            melodyEvidence = authority.melodyEvidenceReferences.filter { it.partId == partId }
        )
    }

    fun partEnhancement(projectRoot: Path, partId: String): PartEnhancementProjection {
        val authority = build(projectRoot)
        val part = authority.selectedPartArtifacts.singleOrNull { it.partId == partId }
            ?: throw IllegalArgumentException("Unknown MIDI part '$partId'.")
        val occurrences = authority.occurrenceTimeline.filter { it.partId == partId }
        return PartEnhancementProjection(
            contextSha256 = authority.contextSha256, part = part, projectKey = authority.projectKey,
            tempo = authority.tempo, meter = authority.meter, occurrences = occurrences,
            harmony = occurrences.flatMap { authority.harmonicTimeline.forOccurrence(it.occurrenceId) },
            analysis = authority.analyzedFacts.single { it.partId == partId },
            melodyEvidence = authority.melodyEvidenceReferences.filter { it.partId == partId }
        )
    }

    fun arrangementGeneration(projectRoot: Path): ArrangementGenerationProjection = build(projectRoot).let { authority ->
        ArrangementGenerationProjection(
            contextSha256 = authority.contextSha256, projectKey = authority.projectKey, tempo = authority.tempo,
            meter = authority.meter, occurrences = authority.occurrenceTimeline, harmony = authority.harmonicTimeline.entries,
            selectedParts = authority.selectedPartArtifacts, analyzedFacts = authority.analyzedFacts
        )
    }

    fun cohesion(projectRoot: Path): CohesionProjection {
        val authority = build(projectRoot)
        val outputs = validatedArrangementAndGenerated(projectRoot)
        return CohesionProjection(
            contextSha256 = authority.contextSha256, occurrences = authority.occurrenceTimeline,
            harmony = authority.harmonicTimeline.entries, approvedArrangement = outputs.arrangement, generatedRoles = outputs.roles
        )
    }

    fun wholeSongAnalysis(projectRoot: Path): WholeSongAnalysisProjection {
        val authority = build(projectRoot)
        val outputs = validatedArrangementAndGenerated(projectRoot)
        return WholeSongAnalysisProjection(
            contextSha256 = authority.contextSha256, projectKey = authority.projectKey, tempo = authority.tempo,
            meter = authority.meter, occurrences = authority.occurrenceTimeline, harmony = authority.harmonicTimeline.entries,
            selectedParts = authority.selectedPartArtifacts, analyzedFacts = authority.analyzedFacts,
            melodyEvidence = authority.melodyEvidenceReferences, approvedArrangement = outputs.arrangement, generatedRoles = outputs.roles
        )
    }

    private fun occurrenceTimeline(
        project: Project,
        structure: List<StructureOccurrence>,
        analyses: Map<String, LoadedAnalysis>,
        globalPpq: Int
    ): List<MusicalOccurrence> {
        val meter = requireNotNull(project.envelope.compositionSettings).timeSignature
        val barTicks = ticksPerBar(globalPpq, meter)
        var startTick = 0L
        var startBar = 0L
        return structure.map { occurrence ->
            val analysis = analyses.getValue(occurrence.partId).facts.analysis
            val duration = scaleTicks(analysis.durationTicks, analysis.ppq, globalPpq, "occurrence '${occurrence.id}'")
            require(duration > 0) { "Structure occurrence '${occurrence.id}' has zero-length analyzed MIDI. Run part analyze again." }
            val bars = ceilDiv(duration, barTicks)
            val result = MusicalOccurrence(
                occurrence.id, occurrence.partId, project.parts.single { it.id == occurrence.partId }.sectionType,
                startBar, Math.addExact(startBar, bars), startTick, Math.addExact(startTick, duration)
            )
            startTick = result.endTick
            startBar = result.endBar
            result
        }.also { built ->
            require(built.zipWithNext().all { (left, right) -> left.endTick == right.startTick && left.endBar == right.startBar }) {
                "Structure occurrence bounds overlap or leave a gap."
            }
        }
    }

    private fun harmonicEntries(project: Project, occurrences: List<MusicalOccurrence>, ppq: Int): List<HarmonicTimelineEntry> {
        val harmony = requireNotNull(project.envelope.harmony) {
            "Save canonical harmony for every structured section before building musical context."
        }
        val bySection = harmony.progressions.associateBy { it.sectionType.value }
        val meter = requireNotNull(project.envelope.compositionSettings).timeSignature
        val barTicks = ticksPerBar(ppq, meter)
        return occurrences.flatMap { occurrence ->
            val progression = requireNotNull(bySection[occurrence.sectionType.value]) {
                "Missing canonical harmony for section '${occurrence.sectionType.value}'."
            }
            require(progression.events.isNotEmpty()) { "Canonical harmony for section '${occurrence.sectionType.value}' is empty." }
            progression.requireWellFormed(); progression.requireExecutable()
            (occurrence.startBar until occurrence.endBar).map { bar ->
                val start = Math.addExact(occurrence.startTick, Math.multiplyExact(bar - occurrence.startBar, barTicks))
                val end = minOf(occurrence.endTick, Math.addExact(start, barTicks))
                require(end > start) { "Structure occurrence '${occurrence.occurrenceId}' has an empty harmonic bar." }
                val event = progression.events[((bar - occurrence.startBar) % progression.events.size).toInt()]
                HarmonicTimelineEntry(
                    occurrence.occurrenceId, occurrence.sectionType, chord(event), bar, start, end
                )
            }
        }
    }

    private fun diagnostics(
        project: Project,
        occurrences: List<MusicalOccurrence>,
        analyses: Map<String, LoadedAnalysis>,
        timeline: HarmonicTimeline
    ): List<MusicalAuthorityDiagnostic> {
        val settings = requireNotNull(project.envelope.compositionSettings)
        return analyses.values.sortedBy { it.facts.partId }.flatMap { loaded ->
            val analysis = loaded.facts.analysis
            buildList {
                analysis.key?.toMusicalKeyOrNull()?.takeIf { it != settings.key }?.let { inferred ->
                    add(MusicalAuthorityDiagnostic(MusicalAuthorityDiagnosticKind.ANALYZED_KEY_CONFLICT, loaded.facts.partId, settings.key.displayName, inferred.displayName))
                }
                analysis.tempoMap.firstOrNull()?.bpm?.takeIf { it != settings.tempo.bpm }?.let { inferred ->
                    add(MusicalAuthorityDiagnostic(MusicalAuthorityDiagnosticKind.ANALYZED_TEMPO_CONFLICT, loaded.facts.partId, settings.tempo.bpm.toString(), inferred.toString()))
                }
                analysis.timeSignatures.firstOrNull()?.let { inferred ->
                    if (inferred.numerator != settings.timeSignature.numerator || inferred.denominator != settings.timeSignature.denominator) {
                        add(MusicalAuthorityDiagnostic(MusicalAuthorityDiagnosticKind.ANALYZED_METER_CONFLICT, loaded.facts.partId, settings.timeSignature.displayName, "${inferred.numerator}/${inferred.denominator}"))
                    }
                }
                val expected = occurrences.firstOrNull { it.partId == loaded.facts.partId }
                    ?.let { timeline.forOccurrence(it.occurrenceId).map(HarmonicTimelineEntry::chord).map(CanonicalChord::symbol) }
                    .orEmpty()
                val inferred = analysis.chords.mapNotNull { it.symbol }
                if (inferred.isNotEmpty() && expected.isNotEmpty() && inferred.zip(expected).any { (actual, declared) -> actual != declared }) {
                    add(MusicalAuthorityDiagnostic(MusicalAuthorityDiagnosticKind.ANALYZED_HARMONY_CONFLICT, loaded.facts.partId, expected.joinToString(","), inferred.joinToString(",")))
                }
            }
        }.sortedWith(compareBy<MusicalAuthorityDiagnostic> { it.partId }.thenBy { it.kind.name })
    }

    private fun readCurrentAnalysis(root: Path, partId: String, reference: app.melotrail.arrangement.PartAnalysisReference, selected: SelectedMidiArtifact): LoadedAnalysis {
        require(reference.kind == AnalysisKind.MIDI) { "MIDI analysis is required for part '$partId'. Run part analyze first." }
        val path = resolveProjectFile(root, reference.file, "MIDI analysis for part '$partId'")
        val bytes = Files.readAllBytes(path)
        val decoded = try { canonicalJson.decodeFromString(MidiAnalysis.serializer(), bytes.decodeToString()) }
        catch (error: Exception) { throw IllegalArgumentException("MIDI analysis for part '$partId' is malformed. Run part analyze again.", error) }
        val analysis = canonicalize(decoded)
        require(analysis.version == 1 && analysis.partId == partId && analysis.ppq > 0 && analysis.durationTicks > 0) {
            "MIDI analysis for part '$partId' is invalid. Run part analyze again."
        }
        require(analysis.ppq == selected.ppq && currentDurationTicks(selected) == analysis.durationTicks) {
            "MIDI analysis for part '$partId' is stale for the selected MIDI. Run part analyze again."
        }
        require(analysis.durationSeconds.isFinite() && analysis.durationSeconds >= 0.0 && analysis.bars >= 0 && analysis.noteCount >= 0) {
            "MIDI analysis for part '$partId' has invalid measured facts. Run part analyze again."
        }
        val canonicalBytes = canonicalJson.encodeToString(MidiAnalysis.serializer(), analysis).toByteArray(Charsets.UTF_8)
        return LoadedAnalysis(CanonicalAnalyzedPartFacts(partId, selected.sha256, sha256(canonicalBytes), analysis))
    }

    private fun currentDurationTicks(selected: SelectedMidiArtifact): Long = try {
        MidiSystem.getSequence(selected.path.toFile()).tickLength
    } catch (error: Exception) {
        throw IllegalArgumentException("Selected MIDI for '${selected.partId}' cannot be measured. Re-select or repair the MIDI.", error)
    }

    private fun validatedArrangementAndGenerated(projectRoot: Path): ValidatedOutputs {
        val root = projectRoot.toAbsolutePath().normalize()
        val project = ProjectStore.read(root)
        project.requireValid(root)
        require(WorkflowArtifact.ARRANGEMENT !in project.workflow.stale && WorkflowArtifact.GENERATED_MIDI !in project.workflow.stale) {
            "Approved arrangement or generated MIDI is stale. Regenerate and approve Arrangement first."
        }
        val arrangement = requireNotNull(project.workflow.arrangement) {
            "A current approved arrangement is required. Generate and approve Arrangement first."
        }.arrangement
        require(sha256(Files.readAllBytes(resolveProjectFile(root, arrangement.file, "approved arrangement"))) == arrangement.sha256) {
            "Approved arrangement is stale. Regenerate and approve Arrangement first."
        }
        val generated = requireNotNull(project.workflow.generatedMidi) {
            "Current generated MIDI is required. Generate Arrangement first."
        }
        require(generated.arrangementSha256 == arrangement.sha256) {
            "Generated MIDI is stale for the approved arrangement. Generate Arrangement again."
        }
        val roles = generated.artifacts.sortedBy(GeneratedMidiArtifactReference::id).map { generatedArtifact ->
            val artifact = generatedArtifact.artifact
            require(sha256(Files.readAllBytes(resolveProjectFile(root, artifact.file, "generated MIDI '${generatedArtifact.id}'"))) == artifact.sha256) {
                "Generated MIDI '${generatedArtifact.id}' is stale. Generate Arrangement again."
            }
            ValidatedGeneratedRoleArtifact(generatedArtifact.id, artifact)
        }
        require(roles.isNotEmpty()) { "Current generated MIDI is required. Generate Arrangement first." }
        return ValidatedOutputs(arrangement, roles)
    }

    private fun selectedArtifact(selected: SelectedMidiArtifact) = CanonicalSelectedPartArtifact(
        selected.partId, selected.projectRelativePath, selected.sha256, selected.ppq, selected.kind.name
    )

    /** Analysis files are evidence, so their text layout and JSON property order never affect context identity. */
    private fun canonicalize(analysis: MidiAnalysis): MidiAnalysis = analysis.copy(
        tempoMap = analysis.tempoMap.sortedWith(compareBy<MidiTempoChange> { it.tick }.thenBy { it.bpm }.thenBy { it.inferred }),
        timeSignatures = analysis.timeSignatures.sortedWith(compareBy<MidiTimeSignature> { it.tick }.thenBy { it.numerator }.thenBy { it.denominator }.thenBy { it.inferred }),
        chords = analysis.chords.sortedWith(compareBy<MidiChord> { it.startTick }.thenBy { it.endTick }.thenBy { it.symbol.orEmpty() }.thenBy { it.confidence })
    )

    private fun resolveProjectFile(root: Path, reference: String, label: String): Path {
        val relative = try { Path.of(reference) } catch (error: Exception) { throw IllegalArgumentException("$label path is invalid.", error) }
        require(reference.isNotBlank() && !relative.isAbsolute && relative.none { it.toString() == ".." }) { "$label path must be project-relative." }
        val rootReal = root.toRealPath()
        val path = root.resolve(relative).normalize()
        require(path.startsWith(root) && Files.isRegularFile(path) && path.toRealPath().startsWith(rootReal)) { "$label is missing or escapes the project root." }
        return path
    }

    private fun canonicalPpq(partPpqs: List<Int>, meter: TimeSignature): Int {
        require(partPpqs.isNotEmpty() && partPpqs.all { it > 0 }) { "Selected MIDI must use positive PPQ timing." }
        val meterDivisor = meter.denominator / gcd(meter.denominator, 4)
        val combined = try {
            (partPpqs + meterDivisor).fold(1L) { current, value -> lcm(current, value.toLong()) }
        } catch (error: ArithmeticException) {
            throw IllegalArgumentException("Selected MIDI PPQ values cannot form a safe shared harmonic timeline.", error)
        }
        return combined
            .also { require(it in 1..MAX_CANONICAL_PPQ) { "Selected MIDI PPQ values cannot form a safe shared harmonic timeline." } }
            .toInt()
    }

    private fun ticksPerBar(ppq: Int, meter: TimeSignature): Long =
        Math.multiplyExact(Math.multiplyExact(ppq.toLong(), 4L) / meter.denominator, meter.numerator.toLong()).also {
            require(it > 0) { "Project meter cannot form positive harmonic bars." }
        }

    private fun scaleTicks(ticks: Long, fromPpq: Int, toPpq: Int, label: String): Long {
        require(ticks > 0 && fromPpq > 0 && toPpq % fromPpq == 0) { "$label has unsupported MIDI timing." }
        return Math.multiplyExact(ticks, (toPpq / fromPpq).toLong())
    }

    private fun chord(event: ChordEvent) = CanonicalChord(event.root.chromatic, event.root.toString(), event.quality)

    private data class LoadedAnalysis(val facts: CanonicalAnalyzedPartFacts)
    private data class ValidatedOutputs(val arrangement: WorkflowArtifactReference, val roles: List<ValidatedGeneratedRoleArtifact>)

    @Serializable
    private data class AuthorityHashPayload(
        val schemaVersion: Int,
        val projectKey: MusicalKey,
        val tempo: Tempo,
        val meter: TimeSignature,
        val occurrenceTimeline: List<MusicalOccurrence>,
        val harmonicTimeline: HarmonicTimeline,
        val selectedPartArtifacts: List<CanonicalSelectedPartArtifact>,
        val analyzedFacts: List<CanonicalAnalyzedPartFacts>,
        val melodyEvidenceReferences: List<MelodyEvidenceReference>,
        val diagnostics: List<MusicalAuthorityDiagnostic>
    ) {
        companion object {
            fun from(authority: CanonicalMusicalAuthority) = AuthorityHashPayload(
                authority.schemaVersion, authority.projectKey, authority.tempo, authority.meter,
                authority.occurrenceTimeline, authority.harmonicTimeline, authority.selectedPartArtifacts,
                authority.analyzedFacts, authority.melodyEvidenceReferences, authority.diagnostics
            )
        }
    }

    private companion object {
        const val MAX_DIAGNOSTICS = 32
        const val MAX_CANONICAL_PPQ = 1_000_000L
        val canonicalJson = Json { encodeDefaults = true; explicitNulls = true; ignoreUnknownKeys = false }
        fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)
        fun gcd(a: Long, b: Long): Long = if (b == 0L) a else gcd(b, a % b)
        fun lcm(a: Long, b: Long): Long = Math.multiplyExact(a / gcd(a, b), b)
        fun ceilDiv(value: Long, divisor: Long): Long = Math.addExact(value, divisor - 1) / divisor
        fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { byte ->
            (byte.toInt() and 0xff).toString(16).padStart(2, '0')
        }
    }
}
