package app.melotrail.arrangement

import app.melotrail.harmony.HarmonySettings

/**
 * Converts saved, musician-approved Harmony into the exact tick segments used
 * by every deterministic instrument generator. MIDI chord detection remains a
 * fallback only when no progression was saved for the part's section type.
 */
object ArrangementHarmonyContext {
    fun apply(analysis: MidiAnalysis, sectionType: SectionTypeId, harmony: HarmonySettings?): MidiAnalysis {
        val progression = harmony?.progressions?.singleOrNull { it.sectionType.value == sectionType.value }
            ?.takeIf { it.events.isNotEmpty() }
            ?: return analysis
        progression.requireExecutable()
        require(analysis.ppq > 0 && analysis.durationTicks > 0 && analysis.timeSignatures.isNotEmpty()) {
            "Structured arrangement harmony requires valid MIDI timing"
        }

        val events = progression.events.sortedBy { it.order }
        val chords = mutableListOf<MidiChord>()
        var tick = 0L
        var chordIndex = 0
        while (tick < analysis.durationTicks) {
            val signature = analysis.timeSignatures.lastOrNull { it.tick <= tick }
                ?: analysis.timeSignatures.first()
            val ticksPerBeat = analysis.ppq * 4L / signature.denominator
            val barEnd = Math.addExact(tick, ticksPerBeat * signature.numerator)
            val nextSignature = analysis.timeSignatures.firstOrNull { it.tick > tick }?.tick
            val end = minOf(analysis.durationTicks, barEnd, nextSignature ?: Long.MAX_VALUE)
            require(end > tick) { "Structured arrangement harmony encountered a non-positive measure" }
            val chord = events[chordIndex % events.size]
            chords += MidiChord(tick, end, "${chord.root}${chord.quality.symbolSuffix}", 1.0)
            tick = end
            chordIndex++
        }
        return analysis.copy(chords = chords)
    }
}
