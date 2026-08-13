from __future__ import annotations

import importlib.util
import sys
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).resolve().parents[1] / "transcribe_piano_spike.py"
SPEC = importlib.util.spec_from_file_location("transcribe_piano_spike", SCRIPT)
assert SPEC and SPEC.loader
SPIKE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = SPIKE
SPEC.loader.exec_module(SPIKE)


def midi_fixture(events: bytes) -> bytes:
    header = b"MThd" + (6).to_bytes(4, "big") + (0).to_bytes(2, "big") + (1).to_bytes(2, "big") + (480).to_bytes(2, "big")
    track = events + b"\x00\xff\x2f\x00"
    return header + b"MTrk" + len(track).to_bytes(4, "big") + track


class TranscribePianoSpikeTest(unittest.TestCase):
    def write_fixture(self, data: bytes) -> Path:
        temporary = tempfile.NamedTemporaryFile(suffix=".mid", delete=False)
        temporary.write(data)
        temporary.close()
        self.addCleanup(Path(temporary.name).unlink, missing_ok=True)
        return Path(temporary.name)

    def test_parses_note_on_note_off(self) -> None:
        path = self.write_fixture(midi_fixture(b"\x00\x90\x3c\x64\x83\x60\x80\x3c\x00"))
        notes = SPIKE.parse_midi_notes(path)
        self.assertEqual(notes, (SPIKE.MidiNote(0, 0, 60, 100, 0, 480),))
        self.assertEqual(SPIKE.midi_summary(notes), {
            "notes": 1,
            "min_pitch": 60,
            "max_pitch": 60,
            "first_tick": 0,
            "last_tick": 480,
        })

    def test_parses_running_status_and_velocity_zero_note_off(self) -> None:
        path = self.write_fixture(midi_fixture(b"\x00\x90\x3c\x64\x83\x60\x3c\x00"))
        self.assertEqual(SPIKE.parse_midi_notes(path), (SPIKE.MidiNote(0, 0, 60, 100, 0, 480),))

    def test_rejects_unterminated_note(self) -> None:
        path = self.write_fixture(midi_fixture(b"\x00\x90\x3c\x64"))
        with self.assertRaisesRegex(SPIKE.MidiValidationError, "unterminated"):
            SPIKE.parse_midi_notes(path)

    def test_rejects_non_midi_file(self) -> None:
        path = self.write_fixture(b"not-midi")
        with self.assertRaisesRegex(SPIKE.MidiValidationError, "MThd"):
            SPIKE.parse_midi_notes(path)


if __name__ == "__main__":
    unittest.main()
