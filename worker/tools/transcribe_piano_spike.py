#!/usr/bin/env python
"""Run the local solo-piano WAV-to-MIDI transcription utility.

This script intentionally does not import the application worker or modify a
song project. It loads Basic Pitch only when actual inference is requested, so
``--validate-midi`` and the parser tests work without model dependencies.

See ``worker/README_BAK.md`` for the supported optional runtime and setup.
"""

from __future__ import annotations

import argparse
import os
from dataclasses import dataclass
from pathlib import Path
from tempfile import NamedTemporaryFile
from time import perf_counter
from typing import BinaryIO, Iterable


WAV_SUFFIXES = {".wav", ".wave"}
MIDI_SUFFIXES = {".mid", ".midi"}
PIANO_MIN_FREQUENCY_HZ = 27.5
PIANO_MAX_FREQUENCY_HZ = 4186.01


@dataclass(frozen=True, order=True)
class MidiNote:
    """One validated completed MIDI note, represented in ticks."""

    track: int
    channel: int
    pitch: int
    velocity: int
    start_tick: int
    end_tick: int

    @property
    def duration_ticks(self) -> int:
        return self.end_tick - self.start_tick


class MidiValidationError(ValueError):
    """Raised when a file is not a valid, note-bearing Standard MIDI File."""


def _read_exact(stream: BinaryIO, size: int, label: str) -> bytes:
    value = stream.read(size)
    if len(value) != size:
        raise MidiValidationError(f"Unexpected end of file while reading {label}")
    return value


def _read_variable_length(stream: BinaryIO) -> int:
    value = 0
    for _ in range(4):
        byte = _read_exact(stream, 1, "variable-length value")[0]
        value = (value << 7) | (byte & 0x7F)
        if byte < 0x80:
            return value
    raise MidiValidationError("Variable-length value exceeds four bytes")


def _event_data_length(status: int) -> int:
    status_type = status & 0xF0
    if status_type in (0xC0, 0xD0):
        return 1
    if status_type in (0x80, 0x90, 0xA0, 0xB0, 0xE0):
        return 2
    raise MidiValidationError(f"Unsupported MIDI channel status 0x{status:02X}")


def parse_midi_notes(path: Path) -> tuple[MidiNote, ...]:
    """Parse note-on/off pairs from a Standard MIDI File without third-party code.

    The parser handles format 0/1 files, running status, meta events, SysEx,
    and velocity-zero note-offs. It intentionally validates only the subset
    needed by this spike; it is not a replacement for the Task 003 MIDI model.
    """

    with path.open("rb") as stream:
        if _read_exact(stream, 4, "MIDI header marker") != b"MThd":
            raise MidiValidationError("MIDI header must start with MThd")
        header_length = int.from_bytes(_read_exact(stream, 4, "MIDI header length"), "big")
        if header_length < 6:
            raise MidiValidationError("MIDI header is shorter than six bytes")
        header = _read_exact(stream, header_length, "MIDI header")
        midi_format = int.from_bytes(header[0:2], "big")
        track_count = int.from_bytes(header[2:4], "big")
        division = int.from_bytes(header[4:6], "big")
        if midi_format not in (0, 1):
            raise MidiValidationError(f"Unsupported MIDI format {midi_format}")
        if track_count == 0:
            raise MidiValidationError("MIDI file contains no tracks")
        if division == 0 or division & 0x8000:
            raise MidiValidationError("SMPTE/zero MIDI timing division is unsupported")

        notes: list[MidiNote] = []
        for track_index in range(track_count):
            if _read_exact(stream, 4, f"track {track_index} marker") != b"MTrk":
                raise MidiValidationError(f"Track {track_index} does not start with MTrk")
            track_length = int.from_bytes(_read_exact(stream, 4, f"track {track_index} length"), "big")
            track_data = _read_exact(stream, track_length, f"track {track_index} data")
            _parse_track_notes(track_data, track_index, notes)

        if stream.read(1):
            raise MidiValidationError("Unexpected bytes after declared MIDI tracks")

    completed = tuple(sorted(notes))
    if not completed:
        raise MidiValidationError("MIDI file contains no completed note-on/note-off pairs")
    for note in completed:
        if not 0 <= note.pitch <= 127:
            raise MidiValidationError(f"MIDI note has illegal pitch {note.pitch}")
        if not 1 <= note.velocity <= 127:
            raise MidiValidationError(f"MIDI note has illegal velocity {note.velocity}")
        if note.duration_ticks <= 0:
            raise MidiValidationError("MIDI note has non-positive duration")
    return completed


def _parse_track_notes(track_data: bytes, track_index: int, notes: list[MidiNote]) -> None:
    from io import BytesIO

    stream = BytesIO(track_data)
    tick = 0
    running_status: int | None = None
    active: dict[tuple[int, int], list[tuple[int, int]]] = {}

    while stream.tell() < len(track_data):
        tick += _read_variable_length(stream)
        first_byte = _read_exact(stream, 1, f"track {track_index} event")[0]
        if first_byte == 0xFF:
            _read_exact(stream, 1, f"track {track_index} meta type")
            meta_length = _read_variable_length(stream)
            _read_exact(stream, meta_length, f"track {track_index} meta data")
            running_status = None
            continue
        if first_byte in (0xF0, 0xF7):
            sysex_length = _read_variable_length(stream)
            _read_exact(stream, sysex_length, f"track {track_index} SysEx data")
            running_status = None
            continue

        if first_byte & 0x80:
            status = first_byte
            if status < 0x80 or status > 0xEF:
                raise MidiValidationError(f"Unsupported system status 0x{status:02X}")
            running_status = status
            data = _read_exact(stream, _event_data_length(status), f"track {track_index} event data")
        else:
            if running_status is None:
                raise MidiValidationError("Running-status event appears before a status byte")
            status = running_status
            data = bytes((first_byte,)) + _read_exact(
                stream,
                _event_data_length(status) - 1,
                f"track {track_index} running-status event data",
            )

        if any(value & 0x80 for value in data):
            raise MidiValidationError("MIDI event data byte has high bit set")
        status_type = status & 0xF0
        if status_type not in (0x80, 0x90):
            continue

        channel, pitch, velocity = status & 0x0F, data[0], data[1]
        key = (channel, pitch)
        if status_type == 0x90 and velocity > 0:
            active.setdefault(key, []).append((tick, velocity))
            continue
        starts = active.get(key)
        if not starts:
            continue
        start_tick, start_velocity = starts.pop(0)
        notes.append(MidiNote(track_index, channel, pitch, start_velocity, start_tick, tick))
        if not starts:
            del active[key]

    if active:
        missing = ", ".join(f"channel {channel}, pitch {pitch}" for channel, pitch in sorted(active))
        raise MidiValidationError(f"Track {track_index} contains unterminated notes: {missing}")


def midi_summary(notes: Iterable[MidiNote]) -> dict[str, int]:
    values = tuple(notes)
    return {
        "notes": len(values),
        "min_pitch": min(note.pitch for note in values),
        "max_pitch": max(note.pitch for note in values),
        "first_tick": min(note.start_tick for note in values),
        "last_tick": max(note.end_tick for note in values),
    }


def transcribe(input_path: Path, output_path: Path) -> tuple[MidiNote, ...]:
    """Run Basic Pitch CoreML inference and atomically write validated MIDI."""

    try:
        from basic_pitch import ICASSP_2022_MODEL_PATH
        from basic_pitch.inference import Model, predict
    except ImportError as error:
        raise RuntimeError(
            "Basic Pitch is unavailable. Create the documented isolated environment "
            "from worker/README_BAK.md."
        ) from error

    output_path.parent.mkdir(parents=True, exist_ok=True)
    with NamedTemporaryFile(
        prefix=f".{output_path.stem}.", suffix=".mid", dir=output_path.parent, delete=False
    ) as temporary:
        temporary_path = Path(temporary.name)
    try:
        model = Model(ICASSP_2022_MODEL_PATH)
        _, midi_data, _ = predict(
            str(input_path),
            model,
            minimum_frequency=PIANO_MIN_FREQUENCY_HZ,
            maximum_frequency=PIANO_MAX_FREQUENCY_HZ,
        )
        midi_data.write(str(temporary_path))
        notes = parse_midi_notes(temporary_path)
        os.replace(temporary_path, output_path)
        return notes
    finally:
        temporary_path.unlink(missing_ok=True)


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Task 001 local solo-piano WAV-to-MIDI spike")
    parser.add_argument("input", type=Path, nargs="?", help="Input solo-piano WAV file")
    parser.add_argument("output", type=Path, nargs="?", help="Output Standard MIDI File (.mid)")
    parser.add_argument("--validate-midi", type=Path, help="Validate a MIDI file without loading Basic Pitch")
    return parser


def main() -> int:
    args = _parser().parse_args()
    if args.validate_midi is not None:
        if args.input is not None or args.output is not None:
            raise SystemExit("--validate-midi cannot be combined with input/output arguments")
        notes = parse_midi_notes(args.validate_midi)
        print(f"validated {args.validate_midi}: {midi_summary(notes)}")
        return 0

    if args.input is None or args.output is None:
        raise SystemExit("Usage: transcribe_piano_spike.py <input.wav> <output.mid>")
    input_path = args.input.expanduser().resolve()
    output_path = args.output.expanduser().resolve()
    if input_path.suffix.lower() not in WAV_SUFFIXES:
        raise SystemExit("Task 001 accepts only lossless WAV input; convert MP3 in Task 002")
    if output_path.suffix.lower() not in MIDI_SUFFIXES:
        raise SystemExit("Output must use a .mid or .midi extension")
    if not input_path.is_file():
        raise SystemExit(f"Input WAV does not exist: {input_path}")
    if input_path == output_path:
        raise SystemExit("Input and output paths must differ")

    started = perf_counter()
    notes = transcribe(input_path, output_path)
    elapsed = perf_counter() - started
    print(f"transcribed {input_path} -> {output_path}: {midi_summary(notes)}, elapsed_seconds={elapsed:.3f}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
