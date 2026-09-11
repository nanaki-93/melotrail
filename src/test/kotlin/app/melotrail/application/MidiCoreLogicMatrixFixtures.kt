package app.melotrail.application

import app.melotrail.midi.OwnedMidiFixtures
import app.melotrail.music.core.ProjectMeter
import app.melotrail.project.AuthoritativeChordEvent
import java.io.ByteArrayOutputStream
import javax.sound.midi.MetaMessage
import javax.sound.midi.MidiEvent
import javax.sound.midi.MidiSystem
import javax.sound.midi.Sequence
import javax.sound.midi.ShortMessage

/** Owned compatibility probes, not musical evaluation songs or historical DAW approvals. */
internal data class LogicMatrixCase(
    val id: String,
    val sourceBytes: ByteArray,
    val instructions: String,
    val meter: ProjectMeter = ProjectMeter(4, 2),
    val occurrences: List<M01Occurrence> = listOf(M01Occurrence("verse-1", "verse", "Verse", 1)),
    val chords: List<AuthoritativeChordEvent> = emptyList(),
    val pad: Boolean = false,
    val rests: LogicMatrixRests = LogicMatrixRests.NONE,
    val terminalFill: Boolean = false,
    val rejectedImport: Boolean = false,
    val explicitPatterns: Boolean = false,
) {
    init { require(id.matches(Regex("[a-z0-9-]+"))) }
}

internal enum class LogicMatrixRests { NONE, ALL_BASS, INTRO_BASS, ALL_GENERATED }

internal object MidiCoreLogicMatrixFixtures {
    private fun owned(name: String) = OwnedMidiFixtures.all.single { it.fileName == "$name.mid" }.bytes.copyOf()
    private fun section(bars: Int) = listOf(M01Occurrence("verse-1", "verse", "Verse", bars))

    val cases: List<LogicMatrixCase> = listOf(
        LogicMatrixCase("smf0-melody", owned("smf0-melody"),
            "Original SMF 0: note 0–480; explicitly padded to one bar (1920).", pad = true),
        LogicMatrixCase("velocity-zero-note-off", owned("velocity-zero-note-off"),
            "Original SMF 0 note-on velocity zero releases the note at 480; export preserves its semantics with explicit trailing padding to 1920.", pad = true),
        LogicMatrixCase("smf1-meta-only", source(metaOnlyTrack = true),
            "Conductor, one melody and an additional meta-only reference track; no extra musical role."),
        LogicMatrixCase("historical-extra-note-track-rejected", owned("smf1-reference-tracks"),
            "Historical reference-track input is outside the current single-note-track contract. No export; retain its unchanged bytes and rejection.", rejectedImport = true),
        LogicMatrixCase("one-bar", owned("whole-song-one-bar"), "One bar; exact end 1920."),
        LogicMatrixCase("two-bars", owned("whole-song-two-bars"), "Two bars; exact end 3840.", occurrences = section(2)),
        LogicMatrixCase("three-bars", owned("whole-song-three-bars"), "Three bars; exact end 5760.", occurrences = section(3)),
        LogicMatrixCase("pickup-timing", owned("pickup-timing"),
            "Pickup stays at tick 0, second note at 480; no inferred offset. Source end 960, explicitly padded to 1920.", pad = true),
        LogicMatrixCase("sub-bar-harmony", owned("sub-bar-harmony"),
            "Original short source remains 480 ticks. Confirmed C 0–240, G7 240–1920; explicit sustained piano supports the brief phrase, and padding never shifts melody.",
            chords = listOf(AuthoritativeChordEvent("c", "verse-1", "C", 0, 240), AuthoritativeChordEvent("g", "verse-1", "G7", 240, 1920)), pad = true, explicitPatterns = true),
        LogicMatrixCase("unequal-harmony", source(end = 3840),
            "Cmaj7 / Am7 / Dm7 / G7 last 3+1+2+2 quarter-note beats; boundaries 0,1440,1920,2880,3840.",
            occurrences = section(2), chords = listOf(
                AuthoritativeChordEvent("c", "verse-1", "Cmaj7", 0, 1440),
                AuthoritativeChordEvent("am", "verse-1", "Am7", 1440, 1920),
                AuthoritativeChordEvent("dm", "verse-1", "Dm7", 1920, 2880),
                AuthoritativeChordEvent("g", "verse-1", "G7", 2880, 3840),
            )),
        LogicMatrixCase("expressive-controller-pitch", owned("expressive-controller-pitch"),
            "Original CC64 and pitch bend retained; note ends at 480, arrangement padded to 1920. Bend range is unspecified; use consistent instrument settings.", pad = true),
        LogicMatrixCase("expression-channel-remap", source(expression = true, channel = 4),
            "Source channel 5 becomes Melody channel 1, preserving CC64, CC11, pitch bend, channel pressure and release velocity. Bank/program hints are omitted."),
        LogicMatrixCase("trailing-source-end", source(start = 240, noteEnd = 1700, end = 1920),
            "Initial silence 0–240; last note ends 1700, source EOT 1920, no padding."),
        LogicMatrixCase("padded-arrangement-end", source(start = 240, noteEnd = 1700, end = 1800),
            "Initial silence 0–240; last note 1700, source EOT 1800, explicitly confirmed arrangement end 1920.", pad = true),
        LogicMatrixCase("odd-ppq", source(ppq = 481, noteEnd = 1800, end = 1924),
            "481 PPQ retained; end 1924. All generated roles are explicitly planned rests: the generator's sixteenth grid cannot represent this PPQ. This package tests import/authority/export, not generated-pattern support.", rests = LogicMatrixRests.ALL_GENERATED),
        LogicMatrixCase("all-song-bass-rest", owned("whole-song-one-bar"),
            "Accepted Bass rest for the entire song: no Bass track or bass.mid; manifest marks Bass inactive.", rests = LogicMatrixRests.ALL_BASS),
        LogicMatrixCase("intro-bass-rest", owned("whole-song-two-bars"),
            "Accepted Bass rest in bar 1, exact Bass entry at 1920 (bar 2), common end 3840.",
            occurrences = listOf(M01Occurrence("intro", "verse", "Intro", 1), M01Occurrence("verse", "verse", "Verse", 1)), rests = LogicMatrixRests.INTRO_BASS),
        LogicMatrixCase("complete-arrangement-boundary", owned("final-boundary-note"),
            "Original note 1440–1920 plus explicitly selected final drum fill. No note may cross 1920; check final hits and release after reopen.", terminalFill = true),
    ) + M01ComparisonFixtures.cases.map { case ->
        LogicMatrixCase(case.id, case.sourceBytes.copyOf(),
            "Current steady-road draft for ${case.id}; inspect repeated-section/phrase intent, authored meter, terminal role rests and exact protected melody ending. No M01 baseline or old hashes reused.",
            case.meter, case.occurrences,
            case.chords.map { AuthoritativeChordEvent(it.id, it.occurrenceId, it.symbol, it.startTick, it.endTick) })
    }

    /** JDK-authored inputs are independent of the production export writer. */
    private fun source(
        ppq: Int = 480, start: Long = 0, noteEnd: Long = 1920, end: Long = 1920,
        metaOnlyTrack: Boolean = false, expression: Boolean = false, channel: Int = 0,
    ): ByteArray {
        val sequence = Sequence(Sequence.PPQ, ppq)
        val conductor = sequence.createTrack()
        conductor.add(MidiEvent(MetaMessage(0x51, byteArrayOf(7, 0xa1.toByte(), 0x20), 3), 0))
        conductor.add(MidiEvent(MetaMessage(0x58, byteArrayOf(4, 2, 24, 8), 4), 0))
        val melody = sequence.createTrack()
        fun event(command: Int, first: Int, second: Int, tick: Long) {
            melody.add(MidiEvent(ShortMessage(command, channel, first, second), tick))
        }
        if (expression) {
            event(ShortMessage.CONTROL_CHANGE, 0, 1, 0)
            event(ShortMessage.CONTROL_CHANGE, 32, 2, 0)
            event(ShortMessage.PROGRAM_CHANGE, 10, 0, 0)
            event(ShortMessage.CONTROL_CHANGE, 64, 127, 0)
            event(ShortMessage.CONTROL_CHANGE, 11, 90, 120)
            event(ShortMessage.PITCH_BEND, 0, 72, 240)
            event(ShortMessage.CHANNEL_PRESSURE, 45, 0, 360)
            event(ShortMessage.PITCH_BEND, 0, 64, 1440)
            event(ShortMessage.CONTROL_CHANGE, 64, 0, 1800)
        }
        event(ShortMessage.NOTE_ON, 72, 96, start)
        event(ShortMessage.NOTE_OFF, 72, if (expression) 33 else 0, noteEnd)
        melody.add(MidiEvent(MetaMessage(0x2f, byteArrayOf(), 0), end))
        if (metaOnlyTrack) sequence.createTrack().add(MidiEvent(MetaMessage(1, "Reference only".toByteArray(), 14), 240))
        return ByteArrayOutputStream().use { output ->
            check(MidiSystem.write(sequence, 1, output) > 0)
            output.toByteArray()
        }
    }
}
