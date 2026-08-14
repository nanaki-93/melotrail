import hashlib
import unittest
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest.mock import patch

import numpy as np
import soundfile as sf

from worker.commands.cleanup import cleanup_command
from worker.errors import AudioCleanupOutputError, AudioCleanupValidationError


class AudioCleanupTest(unittest.TestCase):
    def write(self, path: Path, samples: np.ndarray, rate: int = 22050) -> None:
        sf.write(path, samples, rate, format="WAV", subtype="PCM_24")

    def request(self, source: Path, output: Path, operations: list[dict]) -> dict:
        return cleanup_command({"path": str(source), "outputPath": str(output), "operations": operations})

    def test_clean_noop_is_atomic_pcm24_and_preserves_source_hash(self):
        with TemporaryDirectory() as directory:
            root = Path(directory); source = root / "source.wav"; output = root / "clean.wav"
            audio = (0.1 * np.sin(2 * np.pi * 440 * np.arange(1000) / 22050))[:, None]
            self.write(source, audio)
            source_hash = hashlib.sha256(source.read_bytes()).hexdigest()

            result = self.request(source, output, [])

            info = sf.info(output)
            self.assertEqual(source_hash, hashlib.sha256(source.read_bytes()).hexdigest())
            self.assertEqual([], result["appliedOperations"])
            self.assertEqual([], result["skippedOperations"])
            self.assertEqual((22050, 1, 1000), (info.samplerate, info.channels, info.frames))
            self.assertEqual(("WAV", "PCM_24"), (info.format, info.subtype))
            self.assertFalse(any(p.name.startswith(".clean.") for p in root.iterdir()))

    def test_each_requested_operation_is_measurable_and_deterministic(self):
        with TemporaryDirectory() as directory:
            root = Path(directory); rate = 8000; time = np.arange(rate) / rate
            cases = {
                "dc_removal": (np.full((rate, 1), 0.1), {}, "dcOffset"),
                "clip_repair": (np.array([0.0, 1.0, 1.0, 0.0] + [0.0] * (rate - 4))[:, None], {}, "clippedFrameCount"),
                "declick": (np.array([0.0, 0.0, 0.95, 0.0] + [0.0] * (rate - 4))[:, None], {}, "maxFrameJump"),
                "hum_removal": ((0.4 * np.sin(2 * np.pi * 50 * time))[:, None], {"frequencyHz": 50}, "humConfidence"),
                "noise_reduction": (np.random.default_rng(14).normal(0.0, 0.08, (rate, 1)), {"strength": 0.35}, "noiseConfidence"),
            }
            for name, (audio, params, metric) in cases.items():
                with self.subTest(name=name):
                    source = root / f"{name}.wav"; first = root / f"{name}-first.wav"; second = root / f"{name}-second.wav"
                    self.write(source, audio, rate)
                    operation = {"type": name, **({"params": params} if params else {})}
                    result = self.request(source, first, [operation])
                    repeat = self.request(source, second, [operation])
                    self.assertEqual([name], [item["type"] for item in result["appliedOperations"]])
                    self.assertLessEqual(result["after"][metric], result["before"][metric] + 1e-9)
                    np.testing.assert_allclose(sf.read(first, dtype="float64", always_2d=True)[0], sf.read(second, dtype="float64", always_2d=True)[0], atol=0.0)
                    self.assertEqual(result["after"], repeat["after"])

    def test_combined_operations_preserve_stereo_rate_frames_and_finite_output(self):
        with TemporaryDirectory() as directory:
            root = Path(directory); rate = 32000; frames = rate
            time = np.arange(frames) / rate
            noise = np.random.default_rng(2).normal(0.0, 0.04, (frames, 2))
            audio = noise + 0.08 + (0.2 * np.sin(2 * np.pi * 60 * time))[:, None]
            audio[100:102] = 1.0; audio[300] = 0.95
            source = root / "stereo.wav"; output = root / "clean.wav"; self.write(source, audio, rate)

            result = self.request(source, output, [
                {"type": "dc_removal"}, {"type": "clip_repair"}, {"type": "declick"},
                {"type": "hum_removal", "params": {"frequencyHz": 60}}, {"type": "noise_reduction"},
            ])

            published, published_rate = sf.read(output, dtype="float64", always_2d=True)
            self.assertEqual((rate, 2, frames), (published_rate, published.shape[1], published.shape[0]))
            self.assertTrue(np.isfinite(published).all())
            self.assertGreaterEqual(len(result["appliedOperations"]), 4)

    def test_absent_evidence_skips_only_the_requested_operation(self):
        with TemporaryDirectory() as directory:
            root = Path(directory); source = root / "source.wav"; output = root / "clean.wav"
            self.write(source, np.zeros((1000, 1)))

            result = self.request(source, output, [{"type": "dc_removal"}, {"type": "hum_removal"}])

            self.assertEqual([], result["appliedOperations"])
            self.assertEqual(["dc_removal", "hum_removal"], [item["type"] for item in result["skippedOperations"]])
            self.assertEqual(2, len(result["warnings"]))

    def test_clip_repair_skips_long_or_edge_clipped_runs(self):
        with TemporaryDirectory() as directory:
            root = Path(directory); source = root / "source.wav"; output = root / "clean.wav"
            audio = np.zeros((80, 1)); audio[:2] = 1.0; audio[20:53] = 1.0
            self.write(source, audio)

            result = self.request(source, output, [{"type": "clip_repair"}])

            self.assertEqual([], result["appliedOperations"])
            self.assertEqual("clip_repair", result["skippedOperations"][0]["type"])

    def test_rejects_unknown_unbounded_invalid_or_overwriting_requests(self):
        with TemporaryDirectory() as directory:
            root = Path(directory); source = root / "source.wav"; self.write(source, np.zeros((16, 1)))
            cases = [
                {"path": str(source), "outputPath": str(root / "out.wav"), "operations": [{"type": "normalize"}]},
                {"path": str(source), "outputPath": str(root / "out.wav"), "operations": [{"type": "declick", "params": {"gain": 2}}]},
                {"path": str(source), "outputPath": str(root / "out.wav"), "operations": [{"type": "noise_reduction", "params": {"strength": 0.9}}]},
                {"path": str(source), "outputPath": str(source), "operations": []},
                {"path": str(source), "outputPath": str(root / "out.mp3"), "operations": []},
            ]
            for request in cases:
                with self.subTest(request=request):
                    with self.assertRaises(AudioCleanupValidationError): cleanup_command(request)

    def test_publish_failure_preserves_existing_output(self):
        with TemporaryDirectory() as directory:
            root = Path(directory); source = root / "source.wav"; output = root / "clean.wav"
            self.write(source, np.full((100, 1), 0.1)); output.write_bytes(b"previous output")
            with patch("worker.commands.cleanup.write_pcm24_wav", side_effect=OSError("disk full")):
                with self.assertRaises(AudioCleanupOutputError): self.request(source, output, [{"type": "dc_removal"}])
            self.assertEqual(b"previous output", output.read_bytes())
