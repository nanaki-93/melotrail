import os
import unittest
from pathlib import Path
from tempfile import TemporaryDirectory

import mido
import numpy as np
import soundfile as sf
from unittest.mock import patch

from worker.commands.input_inspection import input_inspection_command
from worker.errors import InputInspectionValidationError


class InputInspectionTest(unittest.TestCase):
    def test_wav_measurements_are_frame_based_and_preserve_format(self):
        with TemporaryDirectory() as directory:
            path = Path(directory) / "mono.wav"
            audio = np.array([[0.0], [0.0], [0.9999], [0.9999], [0.2]], dtype=np.float64)
            sf.write(path, audio, 22050, format="WAV", subtype="PCM_24")

            result = input_inspection_command({"path": str(path)})

            self.assertEqual("RIFF_WAVE", result["container"])
            self.assertEqual(22050, result["audioFormat"]["sampleRate"])
            self.assertEqual(1, result["audioFormat"]["channels"])
            self.assertEqual(2, result["measurements"]["clippedFrameCount"])
            self.assertEqual(1, result["measurements"]["clippedRunCount"])
            self.assertEqual(2, result["measurements"]["silence"]["longestSilentFrames"])
            self.assertEqual(b"RIFF", path.read_bytes()[:4])

    def test_stereo_and_multichannel_inputs_are_not_forced_to_stereo(self):
        with TemporaryDirectory() as directory:
            path = Path(directory) / "three.wav"
            sf.write(path, np.zeros((16, 3)), 48000, format="WAV", subtype="PCM_16")

            result = input_inspection_command({"path": str(path)})

            self.assertEqual(3, result["audioFormat"]["channels"])
            self.assertEqual(48000, result["audioFormat"]["sampleRate"])

    def test_silence_dc_clip_hum_and_noise_indicators_are_deterministic(self):
        with TemporaryDirectory() as directory:
            root = Path(directory)
            rate = 8000
            time = np.arange(rate) / rate
            cases = {
                "dc.wav": (np.full((rate, 1), 0.2), "dc"),
                "hum.wav": (0.4 * np.sin(2 * np.pi * 50 * time)[:, None], "hum"),
                "noise.wav": (np.random.default_rng(4).normal(0.0, 0.1, (rate, 1)), "noise"),
            }
            for name, (audio, expected) in cases.items():
                path = root / name
                sf.write(path, audio, rate, format="WAV", subtype="PCM_24")
                measurement = input_inspection_command({"path": str(path)})["measurements"]
                if expected == "dc":
                    self.assertGreater(measurement["dcOffset"], 0.19)
                else:
                    self.assertIn(measurement[expected]["evidence"], {"MODERATE", "HIGH"})

    def test_midi_requires_matching_container_and_playable_events(self):
        with TemporaryDirectory() as directory:
            root = Path(directory)
            midi_path = root / "part.mid"
            midi = mido.MidiFile(ticks_per_beat=480)
            track = mido.MidiTrack(); midi.tracks.append(track)
            track.append(mido.Message("note_on", note=60, velocity=64, time=0))
            track.append(mido.Message("note_off", note=60, velocity=0, time=480))
            midi.save(midi_path)

            result = input_inspection_command({"path": str(midi_path)})
            self.assertEqual("MIDI", result["container"])
            self.assertGreater(result["durationSeconds"], 0.0)

            mismatch = root / "mismatch.wav"; mismatch.write_bytes(midi_path.read_bytes())
            with self.assertRaisesRegex(InputInspectionValidationError, "RIFF/WAVE"):
                input_inspection_command({"path": str(mismatch)})

            corrupt = root / "corrupt.mid"; corrupt.write_bytes(b"MThd\x00")
            with self.assertRaises(Exception):
                input_inspection_command({"path": str(corrupt)})

    def test_mp3_is_decoded_to_a_temporary_pcm24_wav_and_cleaned_up(self):
        with TemporaryDirectory() as directory:
            path = Path(directory) / "source.mp3"
            path.write_bytes(b"ID3\x04\x00\x00\x00\x00\x00\x00")
            samples = np.array([[0.25, -0.25], [0.0, 0.5]], dtype=np.float64)
            before = set(Path(os.getenv("TMPDIR", "/tmp")).glob("input-inspection-*.wav"))
            with patch("worker.commands.input_inspection.librosa.load", return_value=(samples.T, 32000)):
                result = input_inspection_command({"path": str(path)})
            after = set(Path(os.getenv("TMPDIR", "/tmp")).glob("input-inspection-*.wav"))

            self.assertEqual("MPEG_AUDIO", result["container"])
            self.assertEqual(32000, result["audioFormat"]["sampleRate"])
            self.assertEqual(2, result["audioFormat"]["channels"])
            self.assertEqual(before, after)

    def test_corrupt_and_empty_inputs_are_rejected_without_temporary_leaks(self):
        with TemporaryDirectory() as directory:
            root = Path(directory)
            empty = root / "empty.mp3"; empty.write_bytes(b"")
            corrupt = root / "corrupt.mp3"; corrupt.write_bytes(b"ID3not-a-real-mp3")
            before = set(Path(os.getenv("TMPDIR", "/tmp")).glob("input-inspection-*.wav"))
            with self.assertRaises(InputInspectionValidationError):
                input_inspection_command({"path": str(empty)})
            with self.assertRaises(Exception):
                input_inspection_command({"path": str(corrupt)})
            after = set(Path(os.getenv("TMPDIR", "/tmp")).glob("input-inspection-*.wav"))
            self.assertEqual(before, after)
