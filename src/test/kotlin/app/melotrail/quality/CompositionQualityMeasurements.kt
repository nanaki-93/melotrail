package app.melotrail.quality

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Deterministic QP-001 measurements. They report defect evidence and do not alter MIDI or audio. */
object CompositionQualityMeasurements {
    fun occurrencePhaseResidual(occurrence: OccurrenceFixture): Int = occurrence.notes.minOf { abs(it.startTick - occurrence.startTick) }

    fun barResidual(occurrence: OccurrenceFixture, barTicks: Int): Int {
        val duration = occurrence.notes.maxOf { it.effectiveEndTick } - occurrence.startTick
        val remainder = duration % barTicks
        return min(remainder, (barTicks - remainder) % barTicks)
    }

    fun maximumWrittenPolyphony(notes: List<MidiNoteFixture>): Int = maximumPolyphony(notes) { it.endTick }

    fun maximumEffectivePolyphony(notes: List<MidiNoteFixture>): Int = maximumPolyphony(notes) { it.effectiveEndTick }

    fun scaleViolations(harmony: HarmonyFixture): Int = harmony.notes.count { it.pitch % 12 !in harmony.projectScalePitchClasses && it.pitch % 12 !in harmony.activeChordPitchClasses }

    fun exposedChordClashes(harmony: HarmonyFixture): Int = harmony.notes.count { it.pitch % 12 !in harmony.activeChordPitchClasses }

    fun sustainTailCollisions(notes: List<MidiNoteFixture>, boundaryTick: Int): Int = notes.count { it.endTick <= boundaryTick && it.effectiveEndTick > boundaryTick }

    fun boundaryRoleIsUnsafe(boundary: BoundaryRolesFixture): Boolean = boundary.bridgeRole !in boundary.activeBefore && boundary.bridgeRole !in boundary.activeAfter

    fun crossSectionVoiceMovement(voicings: List<List<Int>>): Int = voicings.zipWithNext().sumOf { (before, after) ->
        require(before.size == after.size) { "Voice movement requires equal voice counts." }
        before.zip(after).sumOf { (left, right) -> abs(right - left) }
    }

    fun sharedGrooveResidual(groove: GrooveFixture): Int = maxResidual(groove.pianoOffsets, groove.bassOffsets, groove.drumOffsets)

    fun rolePhaseResidual(roleOnsets: List<Int>, gridOnsets: List<Int>): Int = roleOnsets.zip(gridOnsets).maxOf { (role, grid) -> abs(role - grid) }

    fun densityContrast(densityBySection: Map<String, Int>): Int = densityBySection.values.let { it.max() - it.min() }

    fun kickBassOverlap(audio: AudioFixture): Double = audio.kickBandEnergy.zip(audio.bassBandEnergy).sumOf { (kick, bass) -> kick * bass }

    fun peak(samples: List<Double>): Double = samples.maxOf(::abs)

    fun criticTotals(critic: CriticFixture): Int = critic.blockers + critic.critical + critic.warnings

    fun lineageIsComplete(lineage: LineageFixture): Boolean = lineage.selectedKind.isNotBlank() && SHA_256.matches(lineage.selectedHash) &&
        lineage.downstreamHashes.isNotEmpty() && lineage.downstreamHashes.values.all(SHA_256::matches)

    private fun maximumPolyphony(notes: List<MidiNoteFixture>, end: (MidiNoteFixture) -> Int): Int =
        notes.flatMap { note -> listOf(note.startTick to 1, end(note) to -1) }
            .sortedWith(compareBy<Pair<Int, Int>> { it.first }.thenBy { it.second })
            .fold(0 to 0) { (active, peak), (_, delta) ->
                val next = active + delta
                next to max(peak, next)
            }.second

    private fun maxResidual(vararg offsetSeries: List<Int>): Int = offsetSeries.flatMap { it }.maxOf(::abs)

    private val SHA_256 = Regex("[0-9a-f]{64}")
}
