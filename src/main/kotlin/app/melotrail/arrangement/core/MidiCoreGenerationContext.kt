package app.melotrail.arrangement.core

import app.melotrail.midi.domain.MidiPpq
import app.melotrail.midi.domain.MidiProtectedMelodyView
import app.melotrail.music.core.ProjectMeter
import app.melotrail.music.core.ProjectTempo
import app.melotrail.project.CandidateRole
import app.melotrail.project.MidiCoreAcceptedDependency
import app.melotrail.project.MidiCoreAuthorityFingerprint
import app.melotrail.project.MidiCoreAuthorityHasher
import app.melotrail.project.MidiCoreAuthoritySettings
import app.melotrail.project.MidiCoreGenerationFingerprint
import app.melotrail.project.MidiCoreGeneratorInput
import app.melotrail.project.MidiCoreProject
import app.melotrail.project.ProjectKey
import app.melotrail.project.ProjectSectionOccurrence
import app.melotrail.structure.MidiCoreHarmonyTimeline
import app.melotrail.structure.MidiCoreResolvedChordWindow
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/** Immutable authority projection supplied to every target role generator. */
data class MidiCoreAuthoritySnapshot(
    val fingerprint: MidiCoreAuthorityFingerprint,
    val sourceSha256: String,
    val melodySha256: String,
    val ppq: MidiPpq,
    val key: ProjectKey,
    val tempo: ProjectTempo,
    val meter: ProjectMeter,
    val pickupTicks: Long,
    val occurrences: List<ProjectSectionOccurrence>,
    val harmonyWindows: List<MidiCoreResolvedChordWindow>,
) {
    init {
        requireHash(sourceSha256, "Source identity")
        requireHash(melodySha256, "Melody identity")
        require(fingerprint.sourceSha256 == sourceSha256 && fingerprint.melodySha256 == melodySha256) {
            "Authority snapshot identities must match their fingerprint"
        }
        require(fingerprint.scopes.map { it.key.occurrenceId }.distinct().toSet() == occurrences.map(ProjectSectionOccurrence::id).toSet()) {
            "Authority snapshot occurrences must match scoped authority"
        }
        require(pickupTicks >= 0) { "Authority snapshot pickup must not be negative" }
        require(occurrences == occurrences.sortedBy(ProjectSectionOccurrence::startTick)) {
            "Authority snapshot occurrences must be ordered"
        }
        require(occurrences.map(ProjectSectionOccurrence::id).distinct().size == occurrences.size) {
            "Authority snapshot occurrence IDs must be unique"
        }
        val occurrenceById = occurrences.associateBy(ProjectSectionOccurrence::id)
        require(occurrences.isEmpty() || occurrences.first().startTick == 0L) {
            "Authority snapshot timeline must begin at tick zero"
        }
        occurrences.zipWithNext().forEach { (left, right) ->
            require(left.endTick == right.startTick) { "Authority snapshot occurrences must be contiguous" }
        }
        val expectedWindowOrder = compareBy<MidiCoreResolvedChordWindow> { occurrenceById.getValue(it.event.occurrenceId).startTick }
            .thenBy(MidiCoreResolvedChordWindow::startTick)
            .thenBy { it.event.id }
        require(harmonyWindows == harmonyWindows.sortedWith(expectedWindowOrder)) {
            "Authority snapshot harmony windows must follow occurrence order"
        }
        require(harmonyWindows.all { window ->
            val occurrence = occurrenceById[window.event.occurrenceId]
            occurrence != null && window.startTick >= occurrence.startTick && window.endTick <= occurrence.endTick
        }) { "Authority snapshot harmony windows must remain inside occurrences" }
        require(harmonyWindows.map { it.event.id }.distinct().size == harmonyWindows.size) {
            "Authority snapshot harmony event IDs must be unique"
        }
        occurrences.forEach { occurrence ->
            val windows = harmonyWindows.filter { it.event.occurrenceId == occurrence.id }
            require(windows.isNotEmpty()) { "Authority snapshot occurrence '${occurrence.id}' has no harmony" }
            var cursor = occurrence.startTick
            windows.forEach { window ->
                require(window.startTick == cursor) { "Authority snapshot harmony is not gap-free" }
                cursor = window.endTick
            }
            require(cursor == occurrence.endTick) { "Authority snapshot harmony does not cover '${occurrence.id}'" }
        }
    }

    /** Complete-project authority identity retained for export and stale-work checks. */
    val authorityHash: String get() = fingerprint.sha256

    /** Return one occurrence or fail before a role generator can run. */
    fun occurrence(occurrenceId: String): ProjectSectionOccurrence = occurrences.singleOrNull { it.id == occurrenceId }
        ?: throw IllegalArgumentException("Unknown authority occurrence '$occurrenceId'")

    /** Return the exact resolved chord windows for one occurrence. */
    fun harmonyFor(occurrenceId: String): List<MidiCoreResolvedChordWindow> = harmonyWindows.filter {
        it.event.occurrenceId == occurrenceId
    }.also {
        require(it.isNotEmpty()) { "No authoritative harmony exists for occurrence '$occurrenceId'" }
    }

    /** Stable serialization of all authority facts needed to reconstruct the context. */
    val canonicalSerialization: String
        get() = canonicalRecord(
            "authority-snapshot",
            listOf(
                "fingerprint" to fingerprint.canonicalSerialization,
                "source" to sourceSha256,
                "melody" to melodySha256,
                "ppq" to ppq.value.toString(),
                "key-tonic" to key.tonic.toString(),
                "key-mode" to key.modeId,
                "key-spelling" to key.spelling.symbol,
                "tempo" to tempo.microsecondsPerQuarter.toString(),
                "meter-numerator" to meter.numerator.toString(),
                "meter-denominator-exponent" to meter.denominatorExponent.toString(),
                "pickup" to pickupTicks.toString(),
                "occurrences" to occurrences.joinToString(";") { occurrence ->
                    canonicalRecord(
                        "occurrence",
                        listOf(
                            "id" to occurrence.id,
                            "definition" to occurrence.definitionId,
                            "label" to occurrence.label,
                            "start" to occurrence.startTick.toString(),
                            "end" to occurrence.endTick.toString(),
                        ),
                    )
                },
                "harmony" to harmonyWindows.joinToString(";") { window ->
                    canonicalRecord(
                        "window",
                        listOf(
                            "id" to window.event.id,
                            "occurrence" to window.event.occurrenceId,
                            "symbol" to window.event.symbol,
                            "canonical" to window.chord.canonicalSymbol,
                            "start" to window.startTick.toString(),
                            "end" to window.endTick.toString(),
                        ),
                    )
                },
            ),
        )

    companion object {
        /** Build a complete snapshot from already-loaded target project data. */
        fun from(
            project: MidiCoreProject,
            settings: MidiCoreAuthoritySettings = MidiCoreAuthoritySettings(),
        ): MidiCoreAuthoritySnapshot {
            val source = requireNotNull(project.sourceMidi) { "Generation requires an imported source MIDI" }
            val melody = requireNotNull(project.selectedMelody) { "Generation requires a selected protected melody" }
            val authority = requireNotNull(project.authority) { "Generation requires confirmed musical authority" }
            val fingerprint = MidiCoreAuthorityHasher.from(project, settings)
            return MidiCoreAuthoritySnapshot(
                fingerprint = fingerprint,
                sourceSha256 = source.sha256,
                melodySha256 = melody.identitySha256,
                ppq = MidiPpq(source.ppq),
                key = authority.key,
                tempo = authority.tempo,
                meter = authority.meter,
                pickupTicks = authority.pickupTicks,
                occurrences = authority.occurrences,
                harmonyWindows = MidiCoreHarmonyTimeline.build(authority).windows,
            )
        }
    }
}

/** The exact target grid used for authored pattern positions and generated notes. */
data class MidiCoreTickGrid(
    val ppq: MidiPpq,
    val meter: ProjectMeter,
    val subdivisionsPerQuarter: Int = 4,
) {
    init {
        require(subdivisionsPerQuarter in setOf(1, 2, 4, 8, 16)) {
            "Tick grid subdivision must be 1, 2, 4, 8, or 16 per quarter note"
        }
        require(ppq.value % subdivisionsPerQuarter == 0) {
            "PPQ ${ppq.value} cannot represent the selected tick grid exactly"
        }
        require(meter.denominator in setOf(1L, 2L, 4L, 8L, 16L)) {
            "Meter denominator is outside the MIDI Core grid vocabulary"
        }
        require((ppq.value.toLong() * 4L) % meter.denominator == 0L) {
            "PPQ ${ppq.value} cannot represent meter ${meter.numerator}/${meter.denominator} exactly"
        }
    }

    /** Number of MIDI ticks in one quarter-note unit. */
    val ticksPerQuarter: Long get() = ppq.value.toLong()

    /** Number of MIDI ticks in one notated meter beat. */
    val ticksPerBeat: Long get() = (ppq.value.toLong() * 4L) / meter.denominator

    /** Number of MIDI ticks in one authored grid step. */
    val ticksPerSubdivision: Long get() = ppq.value.toLong() / subdivisionsPerQuarter

    /** Number of MIDI ticks in one meter bar. */
    val ticksPerBar: Long get() = Math.multiplyExact(ticksPerBeat, meter.numerator.toLong())

    /** Convert an exact non-negative quarter-note fraction to ticks. */
    fun ticksForQuarterBeats(numerator: Long, denominator: Long = 1L): Long {
        require(numerator >= 0 && denominator > 0) { "Quarter-note position is invalid" }
        val scaled = Math.multiplyExact(numerator, ticksPerQuarter)
        require(scaled % denominator == 0L) { "Quarter-note position is not representable on this grid" }
        return scaled / denominator
    }

    /** Require an absolute tick to land on the authored generation grid. */
    fun requireRepresentable(tick: Long, label: String = "Tick"): Long {
        require(tick >= 0 && tick % ticksPerSubdivision == 0L) {
            "$label $tick is not representable on the selected MIDI Core grid"
        }
        return tick
    }
}

/** A source melody note projected into one generation context without source-file access. */
data class MidiCoreProtectedMelodyNote(
    val id: String,
    val startTick: Long,
    val endTick: Long,
    val pitch: Int,
    val velocity: Int,
    val anchor: Boolean,
) {
    init {
        require(id.matches(PROTECTED_NOTE_ID)) { "Protected melody note ID is invalid" }
        require(startTick >= 0 && endTick > startTick && pitch in 0..127 && velocity in 0..127) {
            "Protected melody note values are invalid"
        }
    }

    /** Return whether this immutable melody note occupies any part of a window. */
    fun overlaps(startTick: Long, endTick: Long): Boolean = this.startTick < endTick && startTick < this.endTick

    /** Stable representation used in the context hash. */
    val canonicalSerialization: String
        get() = listOf(id, startTick, endTick, pitch, velocity, anchor).joinToString("|")

    private companion object {
        val PROTECTED_NOTE_ID = Regex("pmn-[0-9a-f]{64}")
    }
}

/** Semantic note evidence supplied by an already accepted dependency. */
data class MidiCoreGenerationNote(
    val startTick: Long,
    val endTick: Long,
    val pitch: Int,
    val velocity: Int,
) {
    init {
        require(startTick >= 0 && endTick > startTick && pitch in 0..127 && velocity in 1..127) {
            "Accepted dependency note values are invalid"
        }
    }

    /** Stable representation used in the context hash. */
    val canonicalSerialization: String get() = listOf(startTick, endTick, pitch, velocity).joinToString("|")
}

/** Accepted candidate identity plus semantic notes; no artifact path crosses the generator boundary. */
data class MidiCoreAcceptedDependencyContext(
    val dependency: MidiCoreAcceptedDependency,
    val notes: List<MidiCoreGenerationNote> = emptyList(),
) {
    /** Stable representation used in the context hash. */
    val canonicalSerialization: String
        get() = canonicalRecord(
            "accepted-dependency",
            listOf(
                "role" to dependency.role.name,
                "occurrence" to dependency.occurrenceId,
                "candidate" to dependency.candidateId,
                "authority" to dependency.authorityHash,
                "notes" to notes.sortedWith(
                    compareBy<MidiCoreGenerationNote> { it.startTick }.thenBy { it.endTick }.thenBy { it.pitch }.thenBy { it.velocity },
                ).joinToString(";") { it.canonicalSerialization },
            ),
        )
}

/** Section purpose vocabulary retained for deterministic phrase and fill decisions. */
enum class MidiCoreSectionPurpose {
    UNSPECIFIED,
    INTRO,
    VERSE,
    PRE_CHORUS,
    CHORUS,
    BRIDGE,
    OUTRO,
}

/** Explicit policy for occurrence energy, density, and optional authored drum fill. */
data class MidiCoreSectionPolicy(
    val purpose: MidiCoreSectionPurpose = MidiCoreSectionPurpose.UNSPECIFIED,
    val energy: Double = 0.5,
    val density: Double = 0.5,
    val fillPatternId: String? = null,
) {
    init {
        require(energy.isFinite() && energy in 0.0..1.0 && density.isFinite() && density in 0.0..1.0) {
            "Section energy and density must be between 0 and 1"
        }
        require(fillPatternId == null || fillPatternId in MidiCoreDrumFillPatternId.entries.map { it.id }) {
            "Section fill pattern is not in the curated MIDI Core catalog"
        }
    }

    /** Stable representation used in the context hash. */
    val canonicalSerialization: String
        get() = listOf(purpose.name, energy.toString(), density.toString(), fillPatternId ?: "none").joinToString("|")
}

/** One immutable, occurrence-scoped request shared by the three core role engines. */
data class MidiCoreGenerationContext(
    val authority: MidiCoreAuthoritySnapshot,
    val role: CandidateRole,
    val occurrence: ProjectSectionOccurrence,
    val chordWindows: List<MidiCoreResolvedChordWindow>,
    val protectedMelodyNotes: List<MidiCoreProtectedMelodyNote>,
    val acceptedDependencies: List<MidiCoreAcceptedDependencyContext>,
    val performanceProfile: MidiCorePerformanceProfile,
    val patternId: String,
    val generator: MidiCoreGeneratorInput,
    val sectionPolicy: MidiCoreSectionPolicy = MidiCoreSectionPolicy(),
    val tickGrid: MidiCoreTickGrid = MidiCoreTickGrid(authority.ppq, authority.meter),
) {
    init {
        require(authority.occurrence(occurrence.id) == occurrence) { "Generation occurrence is not in the authority snapshot" }
        require(chordWindows == authority.harmonyFor(occurrence.id)) {
            "Generation context must contain the complete authoritative harmony for its occurrence"
        }
        require(protectedMelodyNotes == protectedMelodyNotes.sortedWith(
            compareBy<MidiCoreProtectedMelodyNote> { it.startTick }.thenBy { it.endTick }.thenBy { it.pitch }.thenBy { it.id },
        )) { "Protected melody notes must be deterministic" }
        require(protectedMelodyNotes.all { it.overlaps(occurrence.startTick, occurrence.endTick) }) {
            "Protected melody notes must be scoped to the requested occurrence"
        }
        require(acceptedDependencies.map { it.dependency.role }.distinct().size == acceptedDependencies.size) {
            "Generation context accepts at most one dependency per role"
        }
        require(acceptedDependencies.all { dependency ->
            dependency.dependency.occurrenceId == occurrence.id && dependency.dependency.role != role &&
                dependency.notes.all { note -> note.startTick >= occurrence.startTick && note.endTick <= occurrence.endTick }
        }) { "Accepted dependency context must belong to the requested occurrence and other roles" }
        require(performanceProfile.role == role) { "Performance profile does not belong to the requested role" }
        require(MidiCorePerformanceProfileCatalog.requireForRole(role, performanceProfile.id) == performanceProfile) {
            "Performance profile is not the curated profile for the requested role"
        }
        require(generator.patternId == patternId) { "Generator identity pattern does not match the context pattern" }
        MidiCorePatternCatalog.requireAllowed(role, patternId)
        if (role != CandidateRole.DRUMS) {
            require(sectionPolicy.fillPatternId == null) { "Only drum generation may select a fill pattern" }
        }
        require(tickGrid.ppq == authority.ppq && tickGrid.meter == authority.meter) {
            "Generation tick grid must match authority timing"
        }
        authority.fingerprint.scope(occurrence.id, role)
    }

    /** Alias used by application code when making the authority boundary explicit. */
    val authoritySnapshot: MidiCoreAuthoritySnapshot get() = authority

    /** Current scoped authority hash consumed by this role and occurrence. */
    val authorityHash: String get() = authority.fingerprint.scopeHash(occurrence.id, role)

    /** Explicit seed recorded by the generation fingerprint and candidate metadata. */
    val seed: Long get() = generator.seed

    /** Generator version recorded by the candidate publication boundary. */
    val generatorVersion: String get() = generator.generatorVersion

    /** Complete scoped fingerprint used to admit asynchronous generation results. */
    val generationFingerprint: MidiCoreGenerationFingerprint
        get() = MidiCoreGenerationFingerprint(
            authority.fingerprint,
            app.melotrail.project.MidiCoreAuthorityScopeKey(occurrence.id, role),
            generator,
            acceptedDependencies.map(MidiCoreAcceptedDependencyContext::dependency),
        )

    /** Stable context serialization excluding unrelated occurrences from its identity. */
    val canonicalSerialization: String
        get() = canonicalRecord(
            "generation-context",
            listOf(
                "scope" to authorityHash,
                "source" to authority.sourceSha256,
                "melody" to authority.melodySha256,
                "ppq" to authority.ppq.value.toString(),
                "key" to listOf(authority.key.tonic, authority.key.modeId, authority.key.spelling.symbol).joinToString("|"),
                "tempo" to authority.tempo.microsecondsPerQuarter.toString(),
                "meter" to "${authority.meter.numerator}/${authority.meter.denominatorExponent}",
                "pickup" to authority.pickupTicks.toString(),
                "occurrence" to listOf(occurrence.id, occurrence.definitionId, occurrence.startTick, occurrence.endTick).joinToString("|"),
                "harmony" to chordWindows.joinToString(";") { window ->
                    listOf(window.event.id, window.event.symbol, window.startTick, window.endTick, window.chord.canonicalSymbol).joinToString("|")
                },
                "protected-melody" to protectedMelodyNotes.joinToString(";") { it.canonicalSerialization },
                "accepted" to acceptedDependencies.sortedWith(
                    compareBy<MidiCoreAcceptedDependencyContext> { it.dependency.role.ordinal }.thenBy { it.dependency.candidateId },
                ).joinToString(";") { it.canonicalSerialization },
                "profile" to performanceProfile.canonicalSerialization,
                "pattern" to patternId,
                "generator" to generator.canonicalSerialization,
                "section" to sectionPolicy.canonicalSerialization,
                "grid" to listOf(tickGrid.ppq.value, tickGrid.meter.numerator, tickGrid.meter.denominatorExponent, tickGrid.subdivisionsPerQuarter).joinToString("|"),
            ),
        )

    /** SHA-256 identity for this exact immutable request. */
    val contextSha256: String get() = sha256(canonicalSerialization)

    /** Return an accepted semantic dependency by role, if one was supplied. */
    fun dependency(role: CandidateRole): MidiCoreAcceptedDependencyContext? = acceptedDependencies.singleOrNull {
        it.dependency.role == role
    }

    companion object {
        /** Create a normalized request for exactly one occurrence from a snapshot. */
        fun forOccurrence(
            authority: MidiCoreAuthoritySnapshot,
            role: CandidateRole,
            occurrenceId: String,
            performanceProfile: MidiCorePerformanceProfile,
            patternId: String,
            generator: MidiCoreGeneratorInput,
            protectedMelodyNotes: List<MidiCoreProtectedMelodyNote> = emptyList(),
            acceptedDependencies: List<MidiCoreAcceptedDependencyContext> = emptyList(),
            sectionPolicy: MidiCoreSectionPolicy = MidiCoreSectionPolicy(),
            tickGrid: MidiCoreTickGrid = MidiCoreTickGrid(authority.ppq, authority.meter),
        ): MidiCoreGenerationContext {
            val occurrence = authority.occurrence(occurrenceId)
            return MidiCoreGenerationContext(
                authority = authority,
                role = role,
                occurrence = occurrence,
                chordWindows = authority.harmonyFor(occurrenceId),
                protectedMelodyNotes = protectedMelodyNotes.filter { it.overlaps(occurrence.startTick, occurrence.endTick) }
                    .sortedWith(compareBy<MidiCoreProtectedMelodyNote> { it.startTick }.thenBy { it.endTick }.thenBy { it.pitch }.thenBy { it.id }),
                acceptedDependencies = acceptedDependencies.sortedWith(
                    compareBy<MidiCoreAcceptedDependencyContext> { it.dependency.role.ordinal }.thenBy { it.dependency.candidateId },
                ),
                performanceProfile = performanceProfile,
                patternId = patternId,
                generator = generator,
                sectionPolicy = sectionPolicy,
                tickGrid = tickGrid,
            )
        }

        /** Create a request from loaded project authority and the selected protected melody view. */
        fun from(
            project: MidiCoreProject,
            role: CandidateRole,
            occurrenceId: String,
            performanceProfile: MidiCorePerformanceProfile,
            patternId: String,
            generator: MidiCoreGeneratorInput,
            protectedMelody: MidiProtectedMelodyView? = null,
            acceptedDependencies: List<MidiCoreAcceptedDependencyContext> = emptyList(),
            sectionPolicy: MidiCoreSectionPolicy = MidiCoreSectionPolicy(),
            settings: MidiCoreAuthoritySettings = MidiCoreAuthoritySettings(),
        ): MidiCoreGenerationContext {
            val authority = MidiCoreAuthoritySnapshot.from(project, settings)
            val selected = project.selectedMelody
            if (protectedMelody != null) {
                require(selected != null && protectedMelody.identitySha256 == selected.identitySha256) {
                    "Protected melody view does not match project selection"
                }
                require(protectedMelody.sourceSha256 == authority.sourceSha256 && protectedMelody.ppq == authority.ppq) {
                    "Protected melody view does not match project source identity"
                }
            }
            val notes = protectedMelody?.let { view ->
                view.notes.map { note ->
                    MidiCoreProtectedMelodyNote(
                        id = note.id.value,
                        startTick = note.startTick,
                        endTick = note.endTick,
                        pitch = note.pitch,
                        velocity = note.velocity,
                        anchor = note.id in view.protectedAnchorIds,
                    )
                }
            }.orEmpty()
            return forOccurrence(
                authority,
                role,
                occurrenceId,
                performanceProfile,
                patternId,
                generator,
                notes,
                acceptedDependencies,
                sectionPolicy,
            )
        }
    }
}

private val HASH = Regex("[0-9a-f]{64}")

/** Validate a digest before it enters an immutable context projection. */
private fun requireHash(value: String, label: String) {
    require(HASH.matches(value)) { "$label must be a lowercase SHA-256 value" }
}

/** Encode fields with lengths so adjacent context values cannot collide. */
private fun canonicalRecord(type: String, fields: List<Pair<String, String>>): String = buildString {
    append(type)
    fields.forEach { (name, value) -> append('|').append(name).append('=').append(value.length).append(':').append(value) }
}

/** Hash one canonical context serialization with the target SHA-256 policy. */
private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(StandardCharsets.UTF_8))
    .joinToString("") { byte -> "%02x".format(byte) }
