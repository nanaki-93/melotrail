import tempfile
import unittest
from pathlib import Path

import mido

from worker.commands.midi_clean import midi_clean_command
from worker.errors import MidiCleanupValidationError


def completed_notes(path: Path) -> list[tuple[int, int, int, int, int]]:
    midi = mido.MidiFile(path)
    notes: list[tuple[int, int, int, int, int]] = []
    for track_number, track in enumerate(midi.tracks):
        tick = 0
        active: dict[tuple[int, int], list[tuple[int, int]]] = {}
        for message in track:
            tick += message.time
            if message.type == "note_on" and message.velocity > 0:
                active.setdefault((message.channel, message.note), []).append((tick, message.velocity))
            elif message.type == "note_off" or (message.type == "note_on" and message.velocity == 0):
                starts = active.get((message.channel, message.note), [])
                if starts:
                    start, velocity = starts.pop(0)
                    notes.append((track_number, message.channel, message.note, velocity, start, tick))
    return notes


class MidiCleanCommandTest(unittest.TestCase):
    def write_artifact_fixture(self, path: Path) -> None:
        midi = mido.MidiFile(type=1, ticks_per_beat=480)
        metadata = mido.MidiTrack()
        metadata.append(mido.MetaMessage("track_name", name="Conductor", time=0))
        metadata.append(mido.MetaMessage("set_tempo", tempo=500_000, time=0))
        metadata.append(mido.MetaMessage("time_signature", numerator=4, denominator=4, time=0))
        midi.tracks.append(metadata)
        piano = mido.MidiTrack()
        piano.append(mido.MetaMessage("track_name", name="Piano", time=0))
        piano.append(mido.Message("program_change", channel=0, program=0, time=0))
        # Exact duplicate pair.
        piano.append(mido.Message("note_on", channel=0, note=60, velocity=64, time=0))
        piano.append(mido.Message("note_on", channel=0, note=60, velocity=64, time=0))
        piano.append(mido.Message("note_off", channel=0, note=60, velocity=0, time=480))
        piano.append(mido.Message("note_off", channel=0, note=60, velocity=0, time=0))
        # 25 ms at 120 BPM, then a low-velocity note.
        piano.append(mido.Message("note_on", channel=0, note=61, velocity=70, time=0))
        piano.append(mido.Message("note_off", channel=0, note=61, velocity=0, time=24))
        piano.append(mido.Message("note_on", channel=0, note=62, velocity=4, time=0))
        piano.append(mido.Message("note_off", channel=0, note=62, velocity=0, time=480))
        # Repeated pitch overlap that must be truncated, not merged.
        piano.append(mido.Message("note_on", channel=0, note=64, velocity=80, time=0))
        piano.append(mido.Message("note_on", channel=0, note=64, velocity=90, time=240))
        piano.append(mido.Message("note_off", channel=0, note=64, velocity=0, time=240))
        piano.append(mido.Message("note_off", channel=0, note=64, velocity=0, time=240))
        # A velocity-zero note-off is normalized to note_off in the output.
        piano.append(mido.Message("note_on", channel=0, note=65, velocity=72, time=0))
        piano.append(mido.Message("note_on", channel=0, note=65, velocity=0, time=480))
        piano.append(mido.Message("control_change", channel=0, control=64, value=127, time=0))
        piano.append(mido.Message("control_change", channel=0, control=64, value=127, time=0))
        piano.append(mido.Message("control_change", channel=0, control=64, value=0, time=0))
        midi.tracks.append(piano)
        midi.save(path)

    def write_quantize_fixture(self, path: Path) -> None:
        midi = mido.MidiFile(ticks_per_beat=480)
        track = mido.MidiTrack()
        track.append(mido.Message("note_on", channel=1, note=67, velocity=90, time=101))
        track.append(mido.Message("note_off", channel=1, note=67, velocity=0, time=300))
        midi.tracks.append(track)
        midi.save(path)

    def test_default_cleanup_removes_documented_artifacts_and_preserves_metadata(self) -> None:
        with tempfile.TemporaryDirectory() as directory_name:
            directory = Path(directory_name)
            source = directory / "raw.mid"
            output = directory / "clean.mid"
            self.write_artifact_fixture(source)
            source_bytes = source.read_bytes()

            result = midi_clean_command({"path": str(source), "outputPath": str(output)})

            self.assertTrue(output.is_file())
            self.assertEqual(7, result["inputNoteCount"])
            self.assertEqual(4, result["outputNoteCount"])
            self.assertEqual(1, result["duplicatesRemoved"])
            self.assertEqual(1, result["shortNotesRemoved"])
            self.assertEqual(1, result["lowVelocityNotesRemoved"])
            self.assertEqual("conservative", result["profile"])
            self.assertEqual(2, result["version"])
            self.assertEqual(0, result["overlapsRepaired"])
            self.assertEqual(0, result["quantizedNotes"])
            self.assertEqual(1, result["preservedTempoEvents"])
            self.assertEqual(1, result["preservedTimeSignatureEvents"])
            self.assertEqual(mido.MidiFile(source).type, mido.MidiFile(output).type)
            self.assertEqual(2, len(mido.MidiFile(output).tracks))
            self.assertIn(mido.Message("program_change", channel=0, program=0, time=0), mido.MidiFile(output).tracks[1])
            self.assertEqual(source_bytes, source.read_bytes())

            notes = completed_notes(output)
            overlap_notes = [note for note in notes if note[2] == 64]
            self.assertEqual([(984, 1464), (1224, 1704)], [(note[4], note[5]) for note in overlap_notes])
            self.assertTrue(all(end > start for *_, start, end in notes))
            velocity_zero_events = [message for message in mido.MidiFile(output).tracks[1] if message.type == "note_on" and message.note == 65 and message.velocity == 0]
            self.assertEqual([], velocity_zero_events)

    def test_velocity_normalization_and_tempo_map_threshold_are_explicit(self) -> None:
        with tempfile.TemporaryDirectory() as directory_name:
            directory = Path(directory_name)
            source = directory / "raw.mid"
            normalized = directory / "normalized.mid"
            self.write_artifact_fixture(source)

            midi_clean_command({
                "path": str(source), "outputPath": str(normalized),
                "profile": "transcription-safe", "normalizeVelocity": True,
            })

            velocities = [note[3] for note in completed_notes(normalized)]
            self.assertEqual(32, min(velocities))
            self.assertEqual(112, max(velocities))

            tempo_source = directory / "tempo.mid"
            tempo_output = directory / "tempo-clean.mid"
            midi = mido.MidiFile(ticks_per_beat=480)
            track = mido.MidiTrack()
            track.append(mido.MetaMessage("set_tempo", tempo=500_000, time=0))
            track.append(mido.Message("note_on", note=70, velocity=80, time=0))
            track.append(mido.MetaMessage("set_tempo", tempo=1_000_000, time=240))
            track.append(mido.Message("note_off", note=70, velocity=0, time=240))
            midi.tracks.append(track)
            midi.save(tempo_source)

            result = midi_clean_command({"path": str(tempo_source), "outputPath": str(tempo_output), "minNoteMs": 600})

            self.assertEqual(1, result["outputNoteCount"])

    def test_multiple_channels_and_programs_survive_round_trip(self) -> None:
        with tempfile.TemporaryDirectory() as directory_name:
            directory = Path(directory_name)
            source = directory / "multi.mid"
            output = directory / "multi-clean.mid"
            midi = mido.MidiFile(type=1, ticks_per_beat=480)
            first = mido.MidiTrack()
            first.append(mido.Message("program_change", channel=0, program=0, time=0))
            first.append(mido.Message("note_on", channel=0, note=60, velocity=70, time=0))
            first.append(mido.Message("note_off", channel=0, note=60, velocity=0, time=480))
            second = mido.MidiTrack()
            second.append(mido.Message("program_change", channel=2, program=48, time=0))
            second.append(mido.Message("note_on", channel=2, note=67, velocity=75, time=120))
            second.append(mido.Message("note_off", channel=2, note=67, velocity=0, time=480))
            midi.tracks.extend((first, second))
            midi.save(source)

            result = midi_clean_command({"path": str(source), "outputPath": str(output)})

            cleaned = mido.MidiFile(output)
            self.assertEqual(2, result["outputNoteCount"])
            self.assertIn(mido.Message("program_change", channel=0, program=0, time=0), cleaned.tracks[0])
            self.assertIn(mido.Message("program_change", channel=2, program=48, time=0), cleaned.tracks[1])
            self.assertEqual({0, 2}, {note[1] for note in completed_notes(output)})

    def test_conservative_preserves_orphan_note_offs_and_transcription_safe_removes_them(self) -> None:
        with tempfile.TemporaryDirectory() as directory_name:
            directory = Path(directory_name)
            source = directory / "orphan.mid"
            conservative = directory / "conservative.mid"
            safe = directory / "safe.mid"
            midi = mido.MidiFile(ticks_per_beat=480)
            track = mido.MidiTrack()
            track.append(mido.Message("note_on", channel=0, note=65, velocity=80, time=0))
            track.append(mido.Message("note_off", channel=0, note=65, velocity=0, time=240))
            track.append(mido.Message("note_off", channel=0, note=65, velocity=0, time=120))
            midi.tracks.append(track)
            midi.save(source)

            conservative_result = midi_clean_command({"path": str(source), "outputPath": str(conservative)})
            result = midi_clean_command({
                "path": str(source), "outputPath": str(safe), "profile": "transcription-safe",
            })

            self.assertEqual(0, conservative_result["orphanNoteOffsRemoved"])
            self.assertEqual(1, result["orphanNoteOffsRemoved"])
            self.assertEqual(1, result["outputNoteCount"])
            self.assertEqual(1, len(completed_notes(safe)))
            self.assertEqual(2, sum(
                1 for message in mido.MidiFile(conservative).tracks[0]
                if message.type in {"note_on", "note_off"} and not (message.type == "note_on" and message.velocity > 0)
            ))
            active: set[tuple[int, int]] = set()
            for message in mido.MidiFile(safe).tracks[0]:
                key = (getattr(message, "channel", -1), getattr(message, "note", -1))
                if message.type == "note_on" and message.velocity > 0:
                    active.add(key)
                elif message.type in {"note_on", "note_off"}:
                    self.assertIn(key, active)
                    active.remove(key)
            self.assertEqual(set(), active)

    def test_optional_sustain_cleanup_removes_only_redundant_changes(self) -> None:
        with tempfile.TemporaryDirectory() as directory_name:
            directory = Path(directory_name)
            source = directory / "raw.mid"
            output = directory / "clean.mid"
            self.write_artifact_fixture(source)

            result = midi_clean_command({
                "path": str(source), "outputPath": str(output), "profile": "transcription-safe",
            })

            sustain_values = [
                message.value for message in mido.MidiFile(output).tracks[1]
                if message.type == "control_change" and message.control == 64
            ]
            self.assertEqual([127, 0], sustain_values)
            self.assertEqual(1, result["redundantSustainControlsRemoved"])

    def test_transcription_safe_repairs_retriggers_and_limits_velocity_outliers(self) -> None:
        with tempfile.TemporaryDirectory() as directory_name:
            directory = Path(directory_name)
            source = directory / "raw.mid"
            output = directory / "safe.mid"
            self.write_artifact_fixture(source)
            midi = mido.MidiFile(source)
            for message in midi.tracks[1]:
                if message.type == "note_on" and message.note == 65 and message.velocity > 0:
                    message.velocity = 127
            midi.save(source)

            result = midi_clean_command({
                "path": str(source), "outputPath": str(output), "profile": "transcription-safe",
            })

            self.assertEqual(1, result["overlapsRepaired"])
            self.assertEqual(1, result["velocityOutliersLimited"])
            notes = completed_notes(output)
            self.assertIn((1, 0, 65, 120, 1704, 2184), notes)
            overlap_notes = [note for note in notes if note[2] == 64]
            self.assertEqual([(984, 1224), (1224, 1704)], [(note[4], note[5]) for note in overlap_notes])

    def test_partial_and_full_quantization_follow_strength_and_are_deterministic(self) -> None:
        with tempfile.TemporaryDirectory() as directory_name:
            directory = Path(directory_name)
            source = directory / "raw.mid"
            partial_a = directory / "partial-a.mid"
            partial_b = directory / "partial-b.mid"
            full = directory / "full.mid"
            self.write_quantize_fixture(source)

            partial_request = {"path": str(source), "outputPath": str(partial_a), "profile": "tighten-timing", "quantize": "1/16", "strength": 0.5, "minNoteMs": 0, "minVelocity": 0}
            partial_result = midi_clean_command(partial_request)
            partial_request["outputPath"] = str(partial_b)
            midi_clean_command(partial_request)
            full_result = midi_clean_command({"path": str(source), "outputPath": str(full), "profile": "tighten-timing", "quantize": "1/16", "strength": 1.0, "minNoteMs": 0, "minVelocity": 0})

            self.assertEqual([(0, 1, 67, 90, 111, 380)], completed_notes(partial_a))
            self.assertEqual([(0, 1, 67, 90, 120, 360)], completed_notes(full))
            self.assertEqual(1, partial_result["quantizedNotes"])
            self.assertEqual(1, full_result["quantizedNotes"])
            self.assertEqual(partial_a.read_bytes(), partial_b.read_bytes())

    def test_rejects_invalid_paths_options_and_corrupt_midi_without_creating_output_parent(self) -> None:
        with tempfile.TemporaryDirectory() as directory_name:
            directory = Path(directory_name)
            source = directory / "raw.mid"
            source.write_bytes(b"not midi")
            output = directory / "not-created" / "clean.mid"
            cases = (
                ({"outputPath": str(output)}, "Missing path"),
                ({"path": str(source)}, "Missing outputPath"),
                ({"path": str(source), "outputPath": str(output), "quantize": "1/12"}, "quantize must"),
                ({"path": str(source), "outputPath": str(output), "strength": 0.4}, "strength requires"),
                ({"path": str(source), "outputPath": str(output), "profile": "unknown"}, "profile must"),
                ({"path": str(source), "outputPath": str(output), "quantize": "1/16"}, "quantize requires"),
                ({"path": str(source), "outputPath": str(output), "profile": "tighten-timing", "quantize": "1/16"}, "requires strength"),
                ({"path": str(source), "outputPath": str(output), "cleanSustain": True}, "require transcription-safe"),
                ({"path": str(source), "outputPath": str(output), "minNoteMs": -1}, "minNoteMs must"),
                ({"path": str(source), "outputPath": str(output)}, "Could not parse MIDI input"),
            )
            for request, message in cases:
                with self.subTest(request=request):
                    with self.assertRaisesRegex(MidiCleanupValidationError, message):
                        midi_clean_command(request)
            self.assertFalse(output.parent.exists())


if __name__ == "__main__":
    unittest.main()
