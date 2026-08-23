"""Generate reproducible solo-piano fixtures and benchmark transcription providers."""

from __future__ import annotations

import argparse
import json
import math
from dataclasses import dataclass
from pathlib import Path

import mido
import numpy as np
import soundfile as sf

from worker.commands.midi_clean import midi_clean_command
from worker.commands.transcribe import BasicPitchEngine, TranscriptionEngine


SAMPLE_RATE = 22_050
TICKS_PER_BEAT = 480
TEMPO = 500_000
MATCH_TOLERANCE_MS = 150


@dataclass(frozen=True)
class FixtureNote:
    """One known target note in a synthetic solo-piano fixture."""

    pitch: int
    start_ms: int
    duration_ms: int
    velocity: int


@dataclass(frozen=True)
class Fixture:
    """Named fixture plus the target notes used for objective scoring."""

    name: str
    notes: tuple[FixtureNote, ...]
    duration_ms: int


FIXTURES = (
    Fixture("simple-melody", (
        FixtureNote(60, 0, 450, 88), FixtureNote(62, 500, 450, 84),
        FixtureNote(64, 1_000, 450, 82), FixtureNote(67, 1_500, 750, 92),
    ), 2_500),
    Fixture("chord-heavy", tuple(
        FixtureNote(pitch, start, 850, 86)
        for start, chord in ((0, (48, 52, 55, 60)), (1_000, (50, 53, 57, 62)), (2_000, (45, 48, 52, 57)))
        for pitch in chord
    ), 3_100),
    Fixture("sustain-heavy", (
        FixtureNote(48, 0, 3_000, 76), FixtureNote(55, 0, 3_000, 72),
        FixtureNote(60, 0, 3_000, 80), FixtureNote(64, 750, 1_800, 68),
    ), 3_200),
    Fixture("arpeggiated-fast", tuple(
        FixtureNote((60, 64, 67, 72)[index % 4], index * 125, 110, 74)
        for index in range(24)
    ), 3_100),
    Fixture("expressive-low-velocity", (
        FixtureNote(60, 0, 600, 36), FixtureNote(63, 650, 100, 14),
        FixtureNote(64, 780, 550, 45), FixtureNote(67, 1_400, 120, 13),
        FixtureNote(69, 1_560, 700, 52),
    ), 2_500),
)


def _frequency(pitch: int) -> float:
    """Return a MIDI pitch's equal-tempered frequency."""
    return 440.0 * 2 ** ((pitch - 69) / 12)


def _render_fixture(fixture: Fixture, output: Path) -> None:
    """Render a deterministic piano-like additive signal without external samples."""
    samples = np.zeros(math.ceil(fixture.duration_ms * SAMPLE_RATE / 1_000), dtype=np.float32)
    for note in fixture.notes:
        start = round(note.start_ms * SAMPLE_RATE / 1_000)
        length = round(note.duration_ms * SAMPLE_RATE / 1_000)
        time = np.arange(length, dtype=np.float32) / SAMPLE_RATE
        envelope = (1.0 - np.exp(-time * 90.0)) * np.exp(-time * 2.8)
        tone = sum(weight * np.sin(2 * np.pi * _frequency(note.pitch) * harmonic * time)
                   for harmonic, weight in ((1, 1.0), (2, 0.35), (3, 0.18), (4, 0.08)))
        end = min(len(samples), start + length)
        samples[start:end] += (tone[:end - start] * envelope[:end - start] * (note.velocity / 127.0)).astype(np.float32)
    peak = float(np.max(np.abs(samples)))
    if peak > 0.95:
        samples *= 0.95 / peak
    sf.write(output, samples, SAMPLE_RATE, subtype="PCM_24")


def _write_ground_truth(fixture: Fixture, output: Path) -> None:
    """Write target fixture notes as a Standard MIDI file."""
    midi = mido.MidiFile(ticks_per_beat=TICKS_PER_BEAT)
    track = mido.MidiTrack()
    track.append(mido.MetaMessage("set_tempo", tempo=TEMPO, time=0))
    events = []
    for note in fixture.notes:
        start = round(note.start_ms * TICKS_PER_BEAT * 1_000_000 / (TEMPO * 1_000))
        end = round((note.start_ms + note.duration_ms) * TICKS_PER_BEAT * 1_000_000 / (TEMPO * 1_000))
        events.extend(((start, 1, note), (end, 0, note)))
    previous = 0
    for tick, is_on, note in sorted(events, key=lambda event: (event[0], event[1], event[2].pitch)):
        track.append(mido.Message("note_on" if is_on else "note_off", note=note.pitch,
                                  velocity=note.velocity if is_on else 0, time=tick - previous))
        previous = tick
    midi.tracks.append(track)
    midi.save(output)


def _notes(path: Path) -> list[FixtureNote]:
    """Read complete MIDI note pairs into milliseconds for scoring."""
    midi = mido.MidiFile(path)
    active: dict[tuple[int, int], list[tuple[int, int]]] = {}
    complete: list[FixtureNote] = []
    for track in midi.tracks:
        tick = 0
        for message in track:
            tick += message.time
            if message.type == "note_on" and message.velocity > 0:
                active.setdefault((message.channel, message.note), []).append((tick, message.velocity))
            elif message.type == "note_off" or (message.type == "note_on" and message.velocity == 0):
                starts = active.get((message.channel, message.note), [])
                if starts:
                    start, velocity = starts.pop(0)
                    complete.append(FixtureNote(message.note, round(start * TEMPO / (midi.ticks_per_beat * 1_000)),
                                                round((tick - start) * TEMPO / (midi.ticks_per_beat * 1_000)), velocity))
    return complete


def _score(target: list[FixtureNote], actual: list[FixtureNote]) -> dict[str, int | float]:
    """Measure pitch/timing matches, misses, false notes, and chord capture."""
    unmatched = list(actual)
    correct = 0
    timing_matches = 0
    for expected in target:
        candidates = [note for note in unmatched if note.pitch == expected.pitch]
        if not candidates:
            continue
        candidate = min(candidates, key=lambda note: abs(note.start_ms - expected.start_ms))
        if abs(candidate.start_ms - expected.start_ms) <= MATCH_TOLERANCE_MS:
            unmatched.remove(candidate)
            correct += 1
            timing_matches += 1
    chord_targets = [note for note in target if sum(abs(other.start_ms - note.start_ms) <= 25 for other in target) >= 2]
    chord_captured = sum(1 for note in chord_targets if any(
        actual_note.pitch == note.pitch and abs(actual_note.start_ms - note.start_ms) <= MATCH_TOLERANCE_MS
        for actual_note in actual
    ))
    return {
        "correctNotes": correct,
        "falseNotes": len(unmatched),
        "missedNotes": len(target) - correct,
        "timingMatches": timing_matches,
        "chordCapture": chord_captured / len(chord_targets) if chord_targets else 1.0,
    }


def _duplicates(notes: list[FixtureNote]) -> int:
    """Count exact duplicate output notes as manual-cleanup evidence."""
    return len(notes) - len({(note.pitch, note.start_ms, note.duration_ms, note.velocity) for note in notes})


def _engine_registry() -> dict[str, type[TranscriptionEngine]]:
    """Keep benchmark providers behind the same narrow transcription interface."""
    return {"basic-pitch": BasicPitchEngine}


def _benchmark_fixture(fixture: Fixture, engine: TranscriptionEngine, directory: Path) -> dict:
    """Run one provider and deterministic cleanup against one fixture."""
    wav = directory / f"{fixture.name}.wav"
    expected = directory / f"{fixture.name}.expected.mid"
    raw = directory / f"{fixture.name}.{engine.name}.raw.mid"
    clean = directory / f"{fixture.name}.{engine.name}.clean.mid"
    _render_fixture(fixture, wav)
    _write_ground_truth(fixture, expected)
    engine.transcribe(wav, raw, "piano")
    cleanup = midi_clean_command({
        "path": str(raw), "outputPath": str(clean), "profile": "transcription-safe",
        "minNoteMs": 50, "minVelocity": 15, "preserveGraceNotes": True,
        "graceNoteMaxMs": 80, "graceVelocityMax": 32, "duplicateOnsetWindowMs": 35,
    })
    target, raw_notes, clean_notes = _notes(expected), _notes(raw), _notes(clean)
    raw_score, clean_score = _score(target, raw_notes), _score(target, clean_notes)
    return {
        "fixture": fixture.name,
        "targetNotes": len(target),
        "raw": {**raw_score, "duplicates": _duplicates(raw_notes), "shortNotes": sum(note.duration_ms < 50 for note in raw_notes)},
        "clean": {**clean_score, "duplicates": _duplicates(clean_notes), "shortNotes": sum(note.duration_ms < 50 for note in clean_notes)},
        "manualCleanupBurden": cleanup["duplicatesRemoved"] + cleanup["nearDuplicatesMerged"] + cleanup["shortNotesRemoved"] + cleanup["lowVelocityNotesRemoved"],
    }


def main() -> None:
    """Write fixtures and measured JSON evidence for selected local providers."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=Path("build/transcription-benchmark"))
    parser.add_argument("--engine", action="append", choices=sorted(_engine_registry()), help="Provider to run; repeat for each provider")
    parser.add_argument("--fixtures-only", action="store_true", help="Write the five reproducible WAV/MIDI fixtures without model inference")
    arguments = parser.parse_args()
    arguments.output.mkdir(parents=True, exist_ok=True)
    for fixture in FIXTURES:
        _render_fixture(fixture, arguments.output / f"{fixture.name}.wav")
        _write_ground_truth(fixture, arguments.output / f"{fixture.name}.expected.mid")
    if arguments.fixtures_only:
        print(f"wrote {len(FIXTURES)} transcription fixtures to {arguments.output}")
        return
    engines = arguments.engine or ["basic-pitch"]
    evidence = {"fixtures": [fixture.name for fixture in FIXTURES], "engines": []}
    for engine_id in engines:
        engine = _engine_registry()[engine_id]()
        measurements = [_benchmark_fixture(fixture, engine, arguments.output) for fixture in FIXTURES]
        correct = sum(item["clean"]["correctNotes"] for item in measurements)
        false = sum(item["clean"]["falseNotes"] for item in measurements)
        missed = sum(item["clean"]["missedNotes"] for item in measurements)
        f1 = 0.0 if correct == 0 else 2 * correct / (2 * correct + false + missed)
        evidence["engines"].append({"engine": engine.name, "version": engine.version, "f1": f1, "fixtures": measurements})
    evidence["recommendedEngine"] = max(evidence["engines"], key=lambda item: (item["f1"], item["engine"]))["engine"]
    report = arguments.output / "benchmark-report.json"
    report.write_text(json.dumps(evidence, indent=2, sort_keys=True) + "\n")
    print(f"wrote measured transcription benchmark to {report}")


if __name__ == "__main__":
    main()
