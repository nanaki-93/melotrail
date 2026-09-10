package app.melotrail.arrangement.core

import app.melotrail.project.MidiCoreGrooveDrive
import app.melotrail.project.MidiCoreGrooveFeel
import app.melotrail.project.MidiCoreGrooveSubdivision

/**
 * Small, plan-owned rhythmic rules shared by Bass and Drums.
 *
 * These rules consume only confirmed groove intent. Bass never reads Drums,
 * while Drums may additionally read the already generated Bass dependency.
 */
object MidiCoreBassDrumCoordination {
    /** Increment when shared Bass/Drums coordination changes audible MIDI intent. */
    const val VERSION = 1

    /** Bind the coordination rule only to the two roles that consume it. */
    fun generatorVersion(baseVersion: String, role: app.melotrail.project.CandidateRole): String {
        require(baseVersion.isNotBlank()) { "Generator version must not be blank" }
        return baseVersion + if (role in setOf(app.melotrail.project.CandidateRole.BASS, app.melotrail.project.CandidateRole.DRUMS)) {
            "-bass-drums-v$VERSION"
        } else {
            ""
        }
    }

    /** Preserve the historic unplanned behavior while making confirmed plans explicit. */
    fun bassAttackStride(context: MidiCoreGenerationContext): Long? = when (context.occurrencePlan?.sharedGroove?.subdivision) {
        MidiCoreGrooveSubdivision.QUARTER -> context.tickGrid.ticksPerQuarter
        MidiCoreGrooveSubdivision.EIGHTH, MidiCoreGrooveSubdivision.SIXTEENTH, null -> null
    }

    /** A restrained groove establishes harmony; steadier feels retain authored motion. */
    fun isRestrained(context: MidiCoreGenerationContext): Boolean =
        context.occurrencePlan?.sharedGroove?.drive == MidiCoreGrooveDrive.RESTRAINED

    /** Whether one exact Bass onset is an eligible shared off-beat kick position. */
    fun supportsKickAt(context: MidiCoreGenerationContext, tick: Long): Boolean {
        if (tick !in context.occurrence.startTick until context.occurrence.endTick) return false
        val offset = tick - context.occurrence.startTick
        val grid = context.tickGrid.ticksPerSubdivision
        if (offset % grid != 0L || offset % metricalPulse(context) == 0L) return false
        val sixteenth = ((offset % context.tickGrid.ticksPerBar) / grid).toInt()
        val groove = context.occurrencePlan?.sharedGroove ?: return sixteenth in LEGACY_PICKUP_SIXTEENTHS
        val positions = when (groove.subdivision) {
            MidiCoreGrooveSubdivision.QUARTER -> LEGACY_PICKUP_SIXTEENTHS
            MidiCoreGrooveSubdivision.EIGHTH -> EIGHTH_PICKUP_SIXTEENTHS
            MidiCoreGrooveSubdivision.SIXTEENTH -> SIXTEENTH_PICKUP_SIXTEENTHS
        }
        val feelPositions = if (groove.feel == MidiCoreGrooveFeel.SWING) positions.intersect(SWUNG_PICKUP_SIXTEENTHS) else positions
        return sixteenth in feelPositions
    }

    /** Treat 6/8 as two dotted-quarter pulses while retaining the authoritative eighth-note grid. */
    private fun metricalPulse(context: MidiCoreGenerationContext): Long =
        if (context.authority.meter.numerator == 6 && context.authority.meter.denominator == 8L) {
            context.tickGrid.ticksPerBeat * 3L
        } else {
            context.tickGrid.ticksPerBeat
        }

    /** A confirmed groove controls only added support kicks, never its authored drum groove. */
    fun kickLimitPerBar(context: MidiCoreGenerationContext, fallback: Int): Int = when (context.occurrencePlan?.sharedGroove?.drive) {
        MidiCoreGrooveDrive.RESTRAINED -> 0
        MidiCoreGrooveDrive.STEADY -> minOf(1, fallback)
        MidiCoreGrooveDrive.DRIVING -> fallback
        null -> fallback
    }

    private val LEGACY_PICKUP_SIXTEENTHS = setOf(6, 10)
    private val EIGHTH_PICKUP_SIXTEENTHS = setOf(2, 6, 10, 14)
    private val SIXTEENTH_PICKUP_SIXTEENTHS = setOf(3, 6, 10, 14)
    private val SWUNG_PICKUP_SIXTEENTHS = setOf(6, 14)
}
