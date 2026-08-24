package app.melotrail.quality

/**
 * Compact, deterministic evidence for the musical and delivery defects that
 * the quality-pipeline tasks close. These fixtures deliberately describe bad
 * candidates; they are never source or approved project artifacts.
 */
object CompositionQualityFixtures {
    const val PPQ = 480
    const val BEATS_PER_BAR = 4
    const val BAR_TICKS = PPQ * BEATS_PER_BAR

    /** Returns one fixture for each QP-001 defect category. */
    fun baseline() = CompositionQualityBaseline(
        fractionalOccurrence = OccurrenceFixture("fractional", 0, BAR_TICKS, listOf(MidiNoteFixture(60, 0, 2_400))),
        phaseShiftedOccurrence = OccurrenceFixture("late-downbeat", 0, BAR_TICKS, listOf(MidiNoteFixture(60, 60, 480))),
        modeMismatch = HarmonyFixture(
            projectScalePitchClasses = setOf(0, 2, 3, 5, 7, 8, 10), // C natural minor
            activeChordPitchClasses = setOf(0, 3, 7),
            notes = listOf(MidiNoteFixture(64, 0, PPQ)) // E natural
        ),
        overlappingMelody = listOf(
            MidiNoteFixture(60, 0, PPQ),
            MidiNoteFixture(64, PPQ / 2, PPQ + PPQ / 2),
            MidiNoteFixture(67, PPQ + PPQ / 4, PPQ * 2)
        ),
        pedalTail = listOf(
            MidiNoteFixture(60, 0, PPQ * 2, effectiveEndTick = PPQ * 2 + PPQ / 2),
            MidiNoteFixture(62, PPQ * 2, PPQ * 3)
        ),
        flatArrangement = mapOf("intro" to 8, "verse" to 8, "chorus" to 8, "bridge" to 8),
        unsafeBoundary = BoundaryRolesFixture(
            activeBefore = setOf("bass", "pad"),
            activeAfter = setOf("bass", "strings"),
            bridgeRole = "drums"
        ),
        resetVoicings = listOf(
            listOf(48, 52, 55),
            listOf(60, 64, 67)
        ),
        groove = GrooveFixture(
            pianoOffsets = listOf(48, 0, -24, 24),
            bassOffsets = listOf(0, 0, 0, 0),
            drumOffsets = listOf(0, 0, 0, 0)
        ),
        audio = AudioFixture(
            pianoOnsetFrames = listOf(48, 528),
            gridRoleOnsetFrames = listOf(0, 480),
            kickBandEnergy = listOf(0.0, 0.8, 0.9, 0.0),
            bassBandEnergy = listOf(0.0, 0.7, 0.8, 0.0),
            selectedMasterSamples = listOf(0.94, -0.95, 0.91),
            decodedLossyPreviewSamples = listOf(0.96, -1.03, 0.98)
        ),
        critic = CriticFixture(blockers = 3, critical = 2, warnings = 4),
        lineage = LineageFixture(
            selectedKind = "enhanced",
            selectedHash = "a".repeat(64),
            downstreamHashes = mapOf("arrangement" to "b".repeat(64), "render" to "c".repeat(64))
        )
    )
}

data class CompositionQualityBaseline(
    val fractionalOccurrence: OccurrenceFixture,
    val phaseShiftedOccurrence: OccurrenceFixture,
    val modeMismatch: HarmonyFixture,
    val overlappingMelody: List<MidiNoteFixture>,
    val pedalTail: List<MidiNoteFixture>,
    val flatArrangement: Map<String, Int>,
    val unsafeBoundary: BoundaryRolesFixture,
    val resetVoicings: List<List<Int>>,
    val groove: GrooveFixture,
    val audio: AudioFixture,
    val critic: CriticFixture,
    val lineage: LineageFixture
)

data class OccurrenceFixture(val id: String, val startTick: Int, val endTick: Int, val notes: List<MidiNoteFixture>)

data class MidiNoteFixture(
    val pitch: Int,
    val startTick: Int,
    val endTick: Int,
    val channel: Int = 0,
    val effectiveEndTick: Int = endTick
)

data class HarmonyFixture(val projectScalePitchClasses: Set<Int>, val activeChordPitchClasses: Set<Int>, val notes: List<MidiNoteFixture>)

data class BoundaryRolesFixture(val activeBefore: Set<String>, val activeAfter: Set<String>, val bridgeRole: String)

data class GrooveFixture(val pianoOffsets: List<Int>, val bassOffsets: List<Int>, val drumOffsets: List<Int>)

data class AudioFixture(
    val pianoOnsetFrames: List<Int>,
    val gridRoleOnsetFrames: List<Int>,
    val kickBandEnergy: List<Double>,
    val bassBandEnergy: List<Double>,
    val selectedMasterSamples: List<Double>,
    val decodedLossyPreviewSamples: List<Double>
)

data class CriticFixture(val blockers: Int, val critical: Int, val warnings: Int)

data class LineageFixture(val selectedKind: String, val selectedHash: String, val downstreamHashes: Map<String, String>)
