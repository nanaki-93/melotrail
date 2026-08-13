"""Deterministic, conservative cleanup for transcribed Standard MIDI files."""

from __future__ import annotations

import math
import os
import tempfile
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable

import mido

from worker.errors import MidiCleanupOutputValidationError, MidiCleanupValidationError
from worker.registry import register_command


MIDI_SUFFIXES = {".mid", ".midi"}
SUPPORTED_GRIDS = {"1/4": 1, "1/8": 2, "1/16": 4, "1/32": 8}
DEFAULT_MIN_NOTE_MS = 50
DEFAULT_MIN_VELOCITY = 8
NORMALIZED_VELOCITY_MIN = 32
NORMALIZED_VELOCITY_MAX = 112


@dataclass(frozen=True)
class CleanupOptions:
    quantize: str | None
    strength: float
    min_note_ms: int
    min_velocity: int
    normalize_velocity: bool
    clean_sustain: bool


@dataclass
class TimedEvent:
    tick: int
    index: int
    message: mido.Message | mido.MetaMessage


@dataclass
class MidiNote:
    track: int
    channel: int
    pitch: int
    velocity: int
    start_tick: int
    end_tick: int
    on_index: int
    off_index: int
    removed: bool = False


def _require_value(request: dict, field: str, expected_type: type, default: object) -> object:
    value = request.get(field, default)
    if not isinstance(value, expected_type) or isinstance(value, bool) and expected_type is int:
        raise MidiCleanupValidationError(f"{field} must be a {expected_type.__name__}")
    return value


def _parse_request(request: dict) -> tuple[Path, Path, CleanupOptions]:
    raw_input = request.get("path")
    raw_output = request.get("outputPath")
    if not isinstance(raw_input, str) or not raw_input.strip():
        raise MidiCleanupValidationError("Missing path")
    if not isinstance(raw_output, str) or not raw_output.strip():
        raise MidiCleanupValidationError("Missing outputPath")

    input_path = Path(raw_input).expanduser().resolve(strict=False)
    output_path = Path(raw_output).expanduser().resolve(strict=False)
    if not input_path.is_file():
        raise MidiCleanupValidationError(f"Input MIDI file not found: {input_path}")
    if input_path.suffix.lower() not in MIDI_SUFFIXES:
        raise MidiCleanupValidationError("Input must use a .mid or .midi extension")
    if output_path.suffix.lower() not in MIDI_SUFFIXES:
        raise MidiCleanupValidationError("Output must use a .mid or .midi extension")
    if input_path == output_path or (output_path.exists() and input_path.samefile(output_path)):
        raise MidiCleanupValidationError("Input and output paths must differ")
    if output_path.exists() and output_path.is_dir():
        raise MidiCleanupValidationError(f"Output path is a directory: {output_path}")

    quantize = request.get("quantize")
    if quantize is not None and quantize not in SUPPORTED_GRIDS:
        raise MidiCleanupValidationError(
            "quantize must be one of: " + ", ".join(SUPPORTED_GRIDS)
        )
    strength = request.get("strength", 0.0)
    if not isinstance(strength, (int, float)) or isinstance(strength, bool) or not 0.0 <= float(strength) <= 1.0:
        raise MidiCleanupValidationError("strength must be a number from 0.0 to 1.0")
    if quantize is None and float(strength) != 0.0:
        raise MidiCleanupValidationError("strength requires a quantize grid")

    min_note_ms = _require_value(request, "minNoteMs", int, DEFAULT_MIN_NOTE_MS)
    min_velocity = _require_value(request, "minVelocity", int, DEFAULT_MIN_VELOCITY)
    normalize_velocity = _require_value(request, "normalizeVelocity", bool, False)
    clean_sustain = _require_value(request, "cleanSustain", bool, False)
    if not 0 <= min_note_ms <= 60_000:
        raise MidiCleanupValidationError("minNoteMs must be from 0 to 60000")
    if not 0 <= min_velocity <= 127:
        raise MidiCleanupValidationError("minVelocity must be from 0 to 127")

    return input_path, output_path, CleanupOptions(
        quantize=quantize,
        strength=float(strength),
        min_note_ms=min_note_ms,
        min_velocity=min_velocity,
        normalize_velocity=normalize_velocity,
        clean_sustain=clean_sustain,
    )


def _load_midi(path: Path) -> mido.MidiFile:
    try:
        midi = mido.MidiFile(path)
    except (OSError, EOFError, ValueError, KeyError) as exc:
        raise MidiCleanupValidationError(f"Could not parse MIDI input: {exc}") from exc
    if midi.type not in (0, 1):
        raise MidiCleanupValidationError(f"Unsupported MIDI format {midi.type}; only format 0 and 1 are supported")
    if not 1 <= midi.ticks_per_beat <= 0x7FFF:
        raise MidiCleanupValidationError("MIDI ticks-per-quarter must be a positive PPQN value")
    if not midi.tracks:
        raise MidiCleanupValidationError("MIDI file contains no tracks")
    return midi


def _timed_tracks(midi: mido.MidiFile) -> list[list[TimedEvent]]:
    tracks: list[list[TimedEvent]] = []
    for track_index, track in enumerate(midi.tracks):
        tick = 0
        events: list[TimedEvent] = []
        for index, message in enumerate(track):
            if message.time < 0:
                raise MidiCleanupValidationError(f"Track {track_index} has a negative delta time")
            tick += message.time
            if not message.is_meta and message.type in {"note_on", "note_off"}:
                if not 0 <= message.channel <= 15 or not 0 <= message.note <= 127 or not 0 <= message.velocity <= 127:
                    raise MidiCleanupValidationError(f"Track {track_index} has an invalid note event")
            events.append(TimedEvent(tick, index, message))
        tracks.append(events)
    return tracks


def _extract_notes(
    timed_tracks: list[list[TimedEvent]],
    orphan_events: set[tuple[int, int]] | None = None,
    reject_orphans: bool = False,
) -> list[MidiNote]:
    notes: list[MidiNote] = []
    for track_index, events in enumerate(timed_tracks):
        active: dict[tuple[int, int], list[TimedEvent]] = {}
        for event in events:
            message = event.message
            if message.is_meta or message.type not in {"note_on", "note_off"}:
                continue
            key = (message.channel, message.note)
            if message.type == "note_on" and message.velocity > 0:
                active.setdefault(key, []).append(event)
                continue
            starts = active.get(key)
            if not starts:
                if reject_orphans:
                    raise MidiCleanupValidationError(
                        f"Track {track_index} has a note-off at tick {event.tick} "
                        f"without a matching note-on (channel {message.channel}, pitch {message.note})"
                    )
                if orphan_events is not None:
                    orphan_events.add((track_index, event.index))
                continue
            start = starts.pop(0)
            notes.append(MidiNote(
                track=track_index,
                channel=message.channel,
                pitch=message.note,
                velocity=start.message.velocity,
                start_tick=start.tick,
                end_tick=event.tick,
                on_index=start.index,
                off_index=event.index,
            ))
            if not starts:
                del active[key]
        if active:
            details = ", ".join(f"channel {channel}, pitch {pitch}" for channel, pitch in sorted(active))
            raise MidiCleanupValidationError(f"Track {track_index} has unterminated notes: {details}")
    return notes


def _tempo_events(timed_tracks: Iterable[list[TimedEvent]]) -> list[tuple[int, int]]:
    events = [(0, 500_000)]
    for track in timed_tracks:
        for event in track:
            if event.message.is_meta and event.message.type == "set_tempo":
                events.append((event.tick, event.message.tempo))
    # At a shared tick, the last tempo event in file order wins deterministically.
    return [value for _, value in sorted(enumerate(events), key=lambda item: (item[1][0], item[0]))]


def _tick_to_microseconds(tick: int, tempos: list[tuple[int, int]], ticks_per_beat: int) -> float:
    total = 0.0
    previous_tick = 0
    tempo = 500_000
    for tempo_tick, next_tempo in tempos:
        if tempo_tick > tick:
            break
        total += (tempo_tick - previous_tick) * tempo / ticks_per_beat
        previous_tick = tempo_tick
        tempo = next_tempo
    total += (tick - previous_tick) * tempo / ticks_per_beat
    return total


def _duration_ms(note: MidiNote, tempos: list[tuple[int, int]], ticks_per_beat: int) -> float:
    return (_tick_to_microseconds(note.end_tick, tempos, ticks_per_beat) - _tick_to_microseconds(note.start_tick, tempos, ticks_per_beat)) / 1000.0


def _round_half_away_from_zero(value: float) -> int:
    return math.floor(value + 0.5) if value >= 0 else math.ceil(value - 0.5)


def _quantized_tick(tick: int, grid_ticks: int, strength: float) -> int:
    nearest = _round_half_away_from_zero(tick / grid_ticks) * grid_ticks
    return tick + _round_half_away_from_zero((nearest - tick) * strength)


def _remove_redundant_sustain(timed_tracks: list[list[TimedEvent]], removed_events: set[tuple[int, int]]) -> None:
    for track_index, events in enumerate(timed_tracks):
        previous_values: dict[int, int] = {}
        for event in events:
            message = event.message
            if message.is_meta or message.type != "control_change" or message.control != 64:
                continue
            if previous_values.get(message.channel) == message.value:
                removed_events.add((track_index, event.index))
            else:
                previous_values[message.channel] = message.value


def _render_midi(
    source: mido.MidiFile,
    timed_tracks: list[list[TimedEvent]],
    notes: list[MidiNote],
    removed_events: set[tuple[int, int]],
    normalize_velocity: bool,
) -> mido.MidiFile:
    notes_by_event: dict[tuple[int, int], tuple[MidiNote, bool]] = {}
    kept_notes = [note for note in notes if not note.removed]
    velocities = [note.velocity for note in kept_notes]
    velocity_min, velocity_max = min(velocities, default=0), max(velocities, default=0)

    def cleaned_velocity(note: MidiNote) -> int:
        if not normalize_velocity or velocity_min == velocity_max:
            return note.velocity
        ratio = (note.velocity - velocity_min) / (velocity_max - velocity_min)
        return _round_half_away_from_zero(NORMALIZED_VELOCITY_MIN + ratio * (NORMALIZED_VELOCITY_MAX - NORMALIZED_VELOCITY_MIN))

    for note in kept_notes:
        notes_by_event[(note.track, note.on_index)] = (note, True)
        notes_by_event[(note.track, note.off_index)] = (note, False)

    output = mido.MidiFile(type=source.type, ticks_per_beat=source.ticks_per_beat, charset=source.charset)
    for track_index, events in enumerate(timed_tracks):
        track_events: list[tuple[int, int, mido.Message | mido.MetaMessage]] = []
        end_of_track: TimedEvent | None = None
        for event in events:
            if (track_index, event.index) in removed_events:
                continue
            if event.message.is_meta and event.message.type == "end_of_track":
                end_of_track = event
                continue
            mapped_note = notes_by_event.get((track_index, event.index))
            if mapped_note is not None:
                note, is_start = mapped_note
                if is_start:
                    message = mido.Message(
                        "note_on", channel=note.channel, note=note.pitch, velocity=cleaned_velocity(note)
                    )
                    tick = note.start_tick
                else:
                    # Velocity-zero note-ons are canonicalized as legal note-off messages.
                    message = mido.Message("note_off", channel=note.channel, note=note.pitch, velocity=0)
                    tick = note.end_tick
                track_events.append((tick, event.index, message))
            else:
                track_events.append((event.tick, event.index, event.message.copy()))
        track_events.sort(key=lambda item: (item[0], item[1]))
        last_tick = 0
        output_track = mido.MidiTrack()
        for tick, _, message in track_events:
            output_track.append(message.copy(time=tick - last_tick))
            last_tick = tick
        if end_of_track is not None:
            output_track.append(end_of_track.message.copy(time=max(end_of_track.tick, last_tick) - last_tick))
        output.tracks.append(output_track)
    return output


def _validate_output(path: Path) -> list[MidiNote]:
    try:
        midi = _load_midi(path)
        notes = _extract_notes(_timed_tracks(midi), reject_orphans=True)
    except MidiCleanupValidationError as exc:
        raise MidiCleanupOutputValidationError(f"Cleaned MIDI is invalid: {exc}") from exc
    if any(note.end_tick <= note.start_tick for note in notes):
        raise MidiCleanupOutputValidationError("Cleaned MIDI contains a non-positive-duration note")
    return notes


def midi_clean_command(request: dict) -> dict:
    """Clean a MIDI file, preserve tracks/metadata, then atomically publish it."""
    input_path, output_path, options = _parse_request(request)
    source = _load_midi(input_path)
    timed_tracks = _timed_tracks(source)
    removed_events: set[tuple[int, int]] = set()
    notes = _extract_notes(timed_tracks, orphan_events=removed_events)
    input_note_count = len(notes)
    tempos = _tempo_events(timed_tracks)
    stats = {
        "duplicatesRemoved": 0,
        "shortNotesRemoved": 0,
        "lowVelocityNotesRemoved": 0,
        "overlapsRepaired": 0,
        "orphanNoteOffsRemoved": len(removed_events),
        "quantizedNotes": 0,
    }

    seen: set[tuple[int, int, int, int]] = set()
    for note in notes:
        key = (note.channel, note.pitch, note.start_tick, note.end_tick)
        if key in seen:
            note.removed = True
            stats["duplicatesRemoved"] += 1
        else:
            seen.add(key)
    for note in notes:
        if note.removed:
            continue
        if _duration_ms(note, tempos, source.ticks_per_beat) < options.min_note_ms:
            note.removed = True
            stats["shortNotesRemoved"] += 1
        elif note.velocity < options.min_velocity:
            note.removed = True
            stats["lowVelocityNotesRemoved"] += 1

    by_pitch: dict[tuple[int, int], list[MidiNote]] = {}
    for note in notes:
        if not note.removed:
            by_pitch.setdefault((note.channel, note.pitch), []).append(note)
    for same_pitch_notes in by_pitch.values():
        same_pitch_notes.sort(key=lambda note: (note.start_tick, note.end_tick, note.track, note.on_index))
        for earlier, later in zip(same_pitch_notes, same_pitch_notes[1:]):
            if earlier.end_tick > later.start_tick:
                earlier.end_tick = later.start_tick
                stats["overlapsRepaired"] += 1
                if earlier.end_tick <= earlier.start_tick:
                    earlier.removed = True

    if options.quantize is not None and options.strength > 0:
        divisor = SUPPORTED_GRIDS[options.quantize]
        if source.ticks_per_beat % divisor != 0:
            raise MidiCleanupValidationError(
                f"quantize grid {options.quantize} is not representable at {source.ticks_per_beat} ticks per quarter"
            )
        grid_ticks = source.ticks_per_beat // divisor
        for note in notes:
            if note.removed:
                continue
            start_tick = _quantized_tick(note.start_tick, grid_ticks, options.strength)
            end_tick = _quantized_tick(note.end_tick, grid_ticks, options.strength)
            if end_tick <= start_tick:
                # Preserve a legal note; full quantization uses the next grid boundary.
                end_tick = start_tick + (grid_ticks if options.strength == 1.0 else 1)
            if start_tick != note.start_tick or end_tick != note.end_tick:
                stats["quantizedNotes"] += 1
                note.start_tick, note.end_tick = start_tick, end_tick

    for note in notes:
        if note.removed:
            removed_events.add((note.track, note.on_index))
            removed_events.add((note.track, note.off_index))
    if options.clean_sustain:
        _remove_redundant_sustain(timed_tracks, removed_events)

    output_path.parent.mkdir(parents=True, exist_ok=True)
    temporary_path: Path | None = None
    try:
        with tempfile.NamedTemporaryFile(prefix=f".{output_path.stem}.", suffix=".mid", dir=output_path.parent, delete=False) as temporary:
            temporary_path = Path(temporary.name)
        _render_midi(source, timed_tracks, notes, removed_events, options.normalize_velocity).save(temporary_path)
        output_notes = _validate_output(temporary_path)
        os.replace(temporary_path, output_path)
    except MidiCleanupOutputValidationError:
        raise
    except Exception as exc:
        raise MidiCleanupOutputValidationError("Could not write validated cleaned MIDI") from exc
    finally:
        if temporary_path is not None:
            temporary_path.unlink(missing_ok=True)

    return {
        "output": str(output_path),
        "inputNoteCount": input_note_count,
        "outputNoteCount": len(output_notes),
        **stats,
        "preservedTempoEvents": sum(
            1 for track in timed_tracks for event in track
            if event.message.is_meta and event.message.type == "set_tempo"
        ),
        "preservedTimeSignatureEvents": sum(
            1 for track in timed_tracks for event in track
            if event.message.is_meta and event.message.type == "time_signature"
        ),
    }


@register_command("midi-clean")
def registered_midi_clean_command(request: dict) -> dict:
    return midi_clean_command(request)
