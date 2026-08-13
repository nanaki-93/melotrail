import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import numpy as np
import soundfile as sf

from worker.commands.transcribe import (
    TranscriptionModelError,
    TranscriptionOutputValidationError,
    TranscriptionValidationError,
    transcribe_command,
)


def midi_fixture() -> bytes:
    header = b"MThd" + (6).to_bytes(4, "big") + (0).to_bytes(2, "big") + (1).to_bytes(2, "big") + (480).to_bytes(2, "big")
    events = b"\x00\x90\x3c\x64\x83\x60\x80\x3c\x00\x00\xff\x2f\x00"
    return header + b"MTrk" + len(events).to_bytes(4, "big") + events


class FakeEngine:
    name = "fake-engine"
    version = "1.2.3"

    def __init__(self) -> None:
        self.inputs: list[Path] = []

    def transcribe(self, input_path: Path, output_path: Path, instrument: str) -> None:
        self.inputs.append(input_path)
        self.instrument = instrument
        output_path.write_bytes(midi_fixture())


class FailingEngine(FakeEngine):
    def transcribe(self, input_path: Path, output_path: Path, instrument: str) -> None:
        output_path.write_bytes(b"partial")
        raise RuntimeError("model failed")


class InvalidMidiEngine(FakeEngine):
    def transcribe(self, input_path: Path, output_path: Path, instrument: str) -> None:
        output_path.write_bytes(b"not a MIDI file")


class TranscribeCommandTest(unittest.TestCase):
    def write_wav(self, directory: Path, name: str = "piano.wav") -> Path:
        path = directory / name
        sf.write(path, np.zeros((16_000, 2), dtype=np.float32), 32_000, subtype="PCM_24")
        return path

    def request(self, source: Path, output: Path, instrument: str = "piano") -> dict:
        return {"path": str(source), "outputPath": str(output), "instrument": instrument}

    def test_valid_wav_uses_fake_engine_and_reports_midi_metadata(self) -> None:
        with tempfile.TemporaryDirectory() as directory_name:
            directory = Path(directory_name)
            source = self.write_wav(directory)
            output = directory / "nested" / "result.mid"
            engine = FakeEngine()

            result = transcribe_command(self.request(source, output), engine)

            self.assertEqual(output.resolve(), Path(result["output"]))
            self.assertEqual(1, result["notes"])
            self.assertEqual(0.5, result["duration"])
            self.assertEqual("fake-engine", result["engine"])
            self.assertEqual("1.2.3", result["engineVersion"])
            self.assertTrue(output.is_file())
            self.assertEqual("piano", engine.instrument)
            self.assertEqual(source.resolve(), engine.inputs[0])

    def test_mp3_decodes_to_temporary_lossless_wav_before_engine(self) -> None:
        with tempfile.TemporaryDirectory() as directory_name:
            directory = Path(directory_name)
            source = directory / "source.MP3"
            source.write_bytes(b"mp3 source remains unchanged")
            output = directory / "result.midi"
            engine = FakeEngine()
            decoded_paths: list[Path] = []

            def decode(request: dict) -> dict:
                decoded = Path(request["outputPath"])
                decoded_paths.append(decoded)
                sf.write(decoded, np.zeros(22_050, dtype=np.float32), 22_050, subtype="PCM_24")
                return {"output": str(decoded)}

            with patch("worker.commands.transcribe.mp3_convert_command", side_effect=decode):
                transcribe_command(self.request(source, output), engine)

            self.assertEqual(b"mp3 source remains unchanged", source.read_bytes())
            self.assertEqual(decoded_paths[0], engine.inputs[0])
            self.assertEqual(".wav", engine.inputs[0].suffix)
            self.assertFalse(decoded_paths[0].parent.exists())

    def test_rejects_missing_invalid_and_same_paths_before_creating_output_parent(self) -> None:
        with tempfile.TemporaryDirectory() as directory_name:
            directory = Path(directory_name)
            source = self.write_wav(directory)
            output_parent = directory / "not-created"
            cases = (
                ({"outputPath": str(output_parent / "result.mid"), "instrument": "piano"}, "Missing path"),
                ({"path": str(source), "instrument": "piano"}, "Missing outputPath"),
                ({"path": str(source), "outputPath": str(output_parent / "result.mid")}, "Missing instrument"),
                (self.request(directory / "missing.wav", output_parent / "result.mid"), "Input file not found"),
                (self.request(source, output_parent / "result.txt"), "Output must use"),
                (self.request(source, output_parent / "result.mid", "drums"), "Unsupported instrument"),
                (self.request(directory / "same.mid", directory / "same.mid"), "Input and output paths must differ"),
            )
            (directory / "same.mid").write_bytes(b"source")
            for request, message in cases:
                with self.subTest(request=request):
                    with self.assertRaisesRegex(TranscriptionValidationError, message):
                        transcribe_command(request, FakeEngine())
            self.assertFalse(output_parent.exists())

    def test_engine_failure_leaves_no_final_or_temporary_midi(self) -> None:
        with tempfile.TemporaryDirectory() as directory_name:
            directory = Path(directory_name)
            source = self.write_wav(directory)
            output = directory / "result.mid"

            with self.assertRaisesRegex(TranscriptionModelError, "engine failed"):
                transcribe_command(self.request(source, output), FailingEngine())

            self.assertFalse(output.exists())
            self.assertEqual([], list(directory.glob(".result.*.mid")))

    def test_invalid_generated_midi_is_not_published(self) -> None:
        with tempfile.TemporaryDirectory() as directory_name:
            directory = Path(directory_name)
            source = self.write_wav(directory)
            output = directory / "result.mid"

            with self.assertRaises(TranscriptionOutputValidationError):
                transcribe_command(self.request(source, output), InvalidMidiEngine())

            self.assertFalse(output.exists())


if __name__ == "__main__":
    unittest.main()
