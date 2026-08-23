package app.melotrail.arrangement

import kotlin.math.abs

data class BassQualityIssue(val code: String, val startTick: Long, val endTick: Long, val detail: String)
data class BassQualityReport(val issues: List<BassQualityIssue>) { val passed: Boolean get() = issues.isEmpty() }

/** Deterministic acceptance gate for generated bass candidates. */
class BassQualityValidator {
    fun validate(notes: List<BassMidiNote>, request: BassGenerationRequest): BassQualityReport {
        val issues = mutableListOf<BassQualityIssue>()
        val ordered = notes.sortedBy(BassMidiNote::startTick)
        val piano = request.arrangementState?.requireTrack(ArrangementState.PIANO)?.notes.orEmpty()
        ordered.forEach { note ->
            if (note.pitch !in LOWEST..HIGHEST) issues += issue("register", note, "Bass is outside E1..C3")
            val local = note.startTick - request.sectionStartTick
            val chord = request.chords.firstOrNull { local >= it.startTick && local < it.endTick }
            val root = chord?.takeIf { it.confidence >= CONFIDENCE }?.symbol?.let(::pitchClass)
            if (root != null && note.pitch % 12 !in chordTones(root)) {
                val next = nextRoot(request, chord.endTick)
                val resolves = next != null && note.endTick <= request.sectionStartTick + chord.endTick &&
                    (note.pitch % 12 == next || ordered.any { it.startTick in note.endTick..request.sectionStartTick + chord.endTick && it.pitch % 12 == next })
                if (!resolves) issues += issue("harmony", note, "Non-chord bass note does not resolve to the next chord root")
            }
            if (piano.any { it.pitch == note.pitch && it.startTick < note.endTick && note.startTick < it.endTick }) {
                issues += issue("melody-collision", note, "Bass duplicates an overlapping accepted piano pitch")
            }
        }
        ordered.zipWithNext().forEach { (left, right) ->
            if (abs(right.pitch - left.pitch) > MAX_LEAP) issues += issue("leap", right, "Bass leap exceeds one octave")
        }
        val previous = request.previousAcceptedBassNote
        if (previous != null && ordered.firstOrNull()?.let { abs(it.pitch - previous.pitch) > MAX_LEAP } == true) issues += issue("voice-leading", ordered.first(), "Section entry does not voice-lead from the accepted bass note")
        ordered.windowed(5).filter { window -> window.map(BassMidiNote::pitch).distinct().size == 1 }.forEach { issues += issue("repetition", it.last(), "Five repeated bass attacks") }
        val beat = request.ppq.toLong() * 4 / request.timeSignatures.first().denominator
        ordered.groupBy { (it.startTick - request.sectionStartTick) / beat }.filterValues { it.size > 1 }.values.flatten().forEach { issues += issue("density", it, "More than one bass attack in a beat") }
        return BassQualityReport(issues.distinctBy { listOf(it.code, it.startTick, it.endTick) }.sortedBy(BassQualityIssue::startTick))
    }

    /** Alters only notes named by the report; unchanged regions retain their exact events. */
    fun correct(notes: List<BassMidiNote>, request: BassGenerationRequest, report: BassQualityReport): List<BassMidiNote> {
        val affected = report.issues.map { it.startTick to it.endTick }.toSet()
        var previous = request.previousAcceptedBassNote
        return notes.sortedBy(BassMidiNote::startTick).map { note ->
            val corrected = if (note.startTick to note.endTick !in affected) note else {
                val root = harmonyRoot(request, note.startTick - request.sectionStartTick)
                val target = root?.let { lowBass(it) } ?: note.pitch.coerceIn(LOWEST, HIGHEST)
                note.copy(pitch = closestToPrevious(target, previous))
            }
            previous = corrected
            corrected
        }.filterIndexed { index, note ->
            val densityIssue = report.issues.any { it.code == "density" && it.startTick == note.startTick && it.endTick == note.endTick }
            !densityIssue || index == 0 || notes[index - 1].startTick != note.startTick
        }
    }

    private fun issue(code: String, note: BassMidiNote, detail: String) = BassQualityIssue(code, note.startTick, note.endTick, detail)
    private fun harmonyRoot(request: BassGenerationRequest, tick: Long): Int? = request.chords.firstOrNull { tick >= it.startTick && tick < it.endTick }
        ?.takeIf { it.confidence >= CONFIDENCE }?.symbol?.let(::pitchClass)
    private fun nextRoot(request: BassGenerationRequest, tick: Long): Int? = request.chords.firstOrNull { it.startTick >= tick && it.confidence >= CONFIDENCE }?.symbol?.let(::pitchClass)
    private fun chordTones(root: Int) = setOf(root, (root + 4) % 12, (root + 7) % 12, (root + 3) % 12, (root + 10) % 12, (root + 11) % 12)
    private fun lowBass(root: Int): Int { var result = 36 + root; while (result > HIGHEST) result -= 12; while (result < LOWEST) result += 12; return result }
    private fun closestToPrevious(pitch: Int, previous: BassMidiNote?): Int {
        if (previous == null) return pitch
        return listOf(pitch - 12, pitch, pitch + 12).filter { it in LOWEST..HIGHEST }.minBy { abs(it - previous.pitch) }
    }
    private fun pitchClass(symbol: String): Int? { val base = when (symbol.trim().firstOrNull()?.uppercaseChar()) { 'C' -> 0; 'D' -> 2; 'E' -> 4; 'F' -> 5; 'G' -> 7; 'A' -> 9; 'B' -> 11; else -> return null }; return when (symbol.trim().getOrNull(1)) { '#' -> (base + 1) % 12; 'b' -> (base + 11) % 12; else -> base } }
    private companion object { const val LOWEST = 28; const val HIGHEST = 48; const val MAX_LEAP = 12; const val CONFIDENCE = 0.75 }
}
