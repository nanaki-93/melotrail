package app.melotrail.midi.domain

import java.util.Collections

/** Immutable identity for one inspected MIDI artifact. */
data class MidiSourceIdentity(
    val sha256: String,
    val originalFilename: String,
    val format: Int,
    val ppq: MidiPpq,
) {
    init {
        require(SHA_256.matches(sha256)) { "MIDI source SHA-256 must be 64 lowercase hexadecimal characters" }
        require(originalFilename.isNotBlank()) { "MIDI source filename must not be blank" }
        require(format in setOf(0, 1)) { "Only SMF format 0 and 1 have a semantic model" }
    }

    private companion object {
        val SHA_256 = Regex("[0-9a-f]{64}")
    }
}

/** PPQ is retained from the source; target code must never silently change it. */
@JvmInline
value class MidiPpq(val value: Int) {
    init {
        require(value in 1..0x7fff) { "MIDI PPQ must be in 1..32767" }
    }
}

/** A non-negative musical position expressed exactly as reduced quarter-note beats. */
data class MidiBeatPosition(val numerator: Long, val denominator: Long) : Comparable<MidiBeatPosition> {
    init {
        require(numerator >= 0) { "Beat position must not be negative" }
        require(denominator > 0) { "Beat denominator must be positive" }
        require(gcd(numerator, denominator) == 1L) { "Beat position must be reduced" }
    }

    fun toTicks(ppq: MidiPpq, policy: MidiTickRoundingPolicy = MidiTickRoundingPolicy.NEAREST_TIES_UP): Long {
        val scaled = Math.multiplyExact(numerator, ppq.value.toLong())
        return policy.round(scaled, denominator)
    }

    override fun compareTo(other: MidiBeatPosition): Int =
        Math.multiplyExact(numerator, other.denominator).compareTo(Math.multiplyExact(other.numerator, denominator))

    companion object {
        val ZERO = MidiBeatPosition(0, 1)

        fun of(numerator: Long, denominator: Long): MidiBeatPosition {
            require(numerator >= 0) { "Beat position must not be negative" }
            require(denominator > 0) { "Beat denominator must be positive" }
            if (numerator == 0L) return ZERO
            val divisor = gcd(numerator, denominator)
            return MidiBeatPosition(numerator / divisor, denominator / divisor)
        }

        fun fromTicks(ticks: Long, ppq: MidiPpq): MidiBeatPosition {
            require(ticks >= 0) { "MIDI tick must not be negative" }
            return of(ticks, ppq.value.toLong())
        }

        private fun gcd(first: Long, second: Long): Long {
            var left = first
            var right = second
            while (right != 0L) {
                val remainder = left % right
                left = right
                right = remainder
            }
            return left
        }
    }
}

/** The one documented conversion policy for unrepresentable beat subdivisions. */
enum class MidiTickRoundingPolicy {
    /** Divide non-negative ticks and round a half tick toward the next tick. */
    NEAREST_TIES_UP;

    fun round(numerator: Long, denominator: Long): Long {
        require(numerator >= 0) { "Tick numerator must not be negative" }
        require(denominator > 0) { "Tick denominator must be positive" }
        val quotient = numerator / denominator
        val remainder = numerator % denominator
        return if (remainder >= denominator - remainder) Math.addExact(quotient, 1L) else quotient
    }
}

/** Stable identity of an event in the original source stream. */
data class MidiSourceEventIdentity(val trackIndex: Int, val eventIndex: Int) {
    init {
        require(trackIndex >= 0) { "Source track index must not be negative" }
        require(eventIndex >= 0) { "Source event index must not be negative" }
    }
}

/** Event priority is part of the persisted semantic ordering contract. */
enum class MidiSemanticEventKind(val priority: Int) {
    TEMPO(10),
    TIME_SIGNATURE(20),
    TRACK_NAME(30),
    MARKER(40),
    TEXT(50),
    CONTROL_CHANGE(60),
    PITCH_BEND(70),
    CHANNEL_PRESSURE(80),
    NOTE(90),
    UNSUPPORTED(100),
}

/**
 * A deterministic key shared by imported and generated events. Imported events
 * use [sourceEvent]; generated events use [generatedEventKey]. Exactly one is set.
 */
data class MidiEventOrderingKey(
    val tick: Long,
    val kind: MidiSemanticEventKind,
    val sourceEvent: MidiSourceEventIdentity? = null,
    val generatedEventKey: Long? = null,
) : Comparable<MidiEventOrderingKey> {
    init {
        require(tick >= 0) { "MIDI event tick must not be negative" }
        require((sourceEvent == null) != (generatedEventKey == null)) {
            "An event ordering key must identify exactly one source or generated event"
        }
        require(generatedEventKey == null || generatedEventKey >= 0) { "Generated event key must not be negative" }
    }

    override fun compareTo(other: MidiEventOrderingKey): Int = compareValuesBy(
        this,
        other,
        MidiEventOrderingKey::tick,
        { it.kind.priority },
        { it.sourceEvent?.trackIndex ?: Int.MAX_VALUE },
        { it.sourceEvent?.eventIndex ?: Int.MAX_VALUE },
        { it.generatedEventKey ?: Long.MIN_VALUE },
    )
}

sealed interface SemanticMidiEvent {
    val orderingKey: MidiEventOrderingKey
    val kind: MidiSemanticEventKind
        get() = orderingKey.kind
}

data class MidiNoteEvent(
    override val orderingKey: MidiEventOrderingKey,
    val endTick: Long,
    val channel: Int,
    val pitch: Int,
    val velocity: Int,
    val releaseVelocity: Int? = null,
) : SemanticMidiEvent {
    init {
        requireKind(orderingKey, MidiSemanticEventKind.NOTE)
        require(endTick > orderingKey.tick) { "MIDI note end tick must be after start tick" }
        requireChannel(channel)
        requireDataByte(pitch, "MIDI note pitch")
        requireDataByte(velocity, "MIDI note velocity")
        require(releaseVelocity == null || releaseVelocity in 0..127) { "MIDI release velocity must be in 0..127" }
    }
}

data class MidiControlChangeEvent(
    override val orderingKey: MidiEventOrderingKey,
    val channel: Int,
    val controller: Int,
    val value: Int,
) : SemanticMidiEvent {
    init {
        requireKind(orderingKey, MidiSemanticEventKind.CONTROL_CHANGE)
        requireChannel(channel)
        requireDataByte(controller, "MIDI controller")
        requireDataByte(value, "MIDI control value")
    }
}

data class MidiPitchBendEvent(
    override val orderingKey: MidiEventOrderingKey,
    val channel: Int,
    val value: Int,
) : SemanticMidiEvent {
    init {
        requireKind(orderingKey, MidiSemanticEventKind.PITCH_BEND)
        requireChannel(channel)
        require(value in -8192..8191) { "MIDI pitch bend must be in -8192..8191" }
    }
}

data class MidiChannelPressureEvent(
    override val orderingKey: MidiEventOrderingKey,
    val channel: Int,
    val pressure: Int,
) : SemanticMidiEvent {
    init {
        requireKind(orderingKey, MidiSemanticEventKind.CHANNEL_PRESSURE)
        requireChannel(channel)
        requireDataByte(pressure, "MIDI channel pressure")
    }
}

data class MidiTempoEvent(override val orderingKey: MidiEventOrderingKey, val microsecondsPerQuarter: Int) : SemanticMidiEvent {
    init {
        requireKind(orderingKey, MidiSemanticEventKind.TEMPO)
        require(microsecondsPerQuarter > 0) { "MIDI tempo microseconds per quarter must be positive" }
    }
}

data class MidiTimeSignatureEvent(
    override val orderingKey: MidiEventOrderingKey,
    val numerator: Int,
    val denominatorExponent: Int,
    val clocksPerMetronome: Int,
    val thirtySecondNotesPerQuarter: Int,
) : SemanticMidiEvent {
    init {
        requireKind(orderingKey, MidiSemanticEventKind.TIME_SIGNATURE)
        require(numerator in 1..255) { "MIDI time signature numerator must be in 1..255" }
        require(denominatorExponent in 0..30) { "MIDI time signature denominator exponent must be in 0..30" }
        require(clocksPerMetronome in 0..255) { "MIDI clocks per metronome must be in 0..255" }
        require(thirtySecondNotesPerQuarter in 0..255) { "MIDI 32nd-notes per quarter must be in 0..255" }
    }

    val denominator: Int get() = 1 shl denominatorExponent
}

data class MidiTrackNameEvent(override val orderingKey: MidiEventOrderingKey, val name: String) : SemanticMidiEvent {
    init {
        requireKind(orderingKey, MidiSemanticEventKind.TRACK_NAME)
        require(name.isNotBlank()) { "MIDI track name must not be blank" }
    }
}

data class MidiMarkerEvent(override val orderingKey: MidiEventOrderingKey, val marker: String) : SemanticMidiEvent {
    init {
        requireKind(orderingKey, MidiSemanticEventKind.MARKER)
        require(marker.isNotBlank()) { "MIDI marker must not be blank" }
    }
}

enum class MidiTextKind { TEXT, COPYRIGHT, LYRIC, CUE, SEQUENCE_NAME }

data class MidiTextEvent(
    override val orderingKey: MidiEventOrderingKey,
    val textKind: MidiTextKind,
    val text: String,
) : SemanticMidiEvent {
    init {
        requireKind(orderingKey, MidiSemanticEventKind.TEXT)
    }
}

data class MidiUnsupportedEvent(
    override val orderingKey: MidiEventOrderingKey,
    val messageType: String,
    val detail: String,
) : SemanticMidiEvent {
    init {
        requireKind(orderingKey, MidiSemanticEventKind.UNSUPPORTED)
        require(messageType.isNotBlank()) { "Unsupported MIDI event type must not be blank" }
        require(detail.isNotBlank()) { "Unsupported MIDI event detail must not be blank" }
    }
}

/** One source track with an immutable, deterministic event stream. */
class SemanticMidiTrack(val index: Int, events: List<SemanticMidiEvent>) {
    val events: List<SemanticMidiEvent> = immutableSorted(events)

    init {
        require(index >= 0) { "MIDI track index must not be negative" }
        require(this.events.map(SemanticMidiEvent::orderingKey).distinct().size == this.events.size) {
            "MIDI track events must have unique ordering keys"
        }
        require(this.events.all { event -> event.orderingKey.sourceEvent.let { sourceEvent -> sourceEvent == null || sourceEvent.trackIndex == index } }) {
            "Source event identity must belong to its semantic MIDI track"
        }
    }
}

/**
 * Canonical MIDI representation: source identity plus ordered, immutable track
 * streams. It deliberately contains no javax.sound.midi types.
 */
class SemanticMidiSequence(val source: MidiSourceIdentity, tracks: List<SemanticMidiTrack>) {
    val tracks: List<SemanticMidiTrack> = Collections.unmodifiableList(tracks.sortedBy(SemanticMidiTrack::index).toList())

    init {
        require(this.tracks.isNotEmpty()) { "A semantic MIDI sequence must contain at least one track" }
        require(this.tracks.map(SemanticMidiTrack::index).distinct().size == this.tracks.size) { "MIDI track indexes must be unique" }
        require(this.tracks.map(SemanticMidiTrack::index) == this.tracks.indices.toList()) { "MIDI track indexes must be contiguous from zero" }
        require(this.tracks.flatMap(SemanticMidiTrack::events).map(SemanticMidiEvent::orderingKey).distinct().size ==
            this.tracks.sumOf { it.events.size }) { "MIDI sequence events must have globally unique ordering keys" }
    }

    fun orderedEvents(): List<SemanticMidiEvent> = immutableSorted(tracks.flatMap(SemanticMidiTrack::events))

    val endTick: Long get() = tracks.flatMap(SemanticMidiTrack::events).maxOfOrNull { event ->
        if (event is MidiNoteEvent) event.endTick else event.orderingKey.tick
    } ?: 0L
}

private fun requireKind(key: MidiEventOrderingKey, expected: MidiSemanticEventKind) {
    require(key.kind == expected) { "Expected $expected ordering key, received ${key.kind}" }
}

private fun requireChannel(channel: Int) {
    require(channel in 0..15) { "MIDI channel must be in 0..15" }
}

private fun requireDataByte(value: Int, label: String) {
    require(value in 0..127) { "$label must be in 0..127" }
}

private fun <T : SemanticMidiEvent> immutableSorted(events: List<T>): List<T> =
    Collections.unmodifiableList(events.sortedBy(SemanticMidiEvent::orderingKey).toList())
