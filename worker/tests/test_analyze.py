import tempfile
import unittest
from pathlib import Path

import numpy as np
import soundfile as sf

from worker.commands.analyze import analyze_command


class AnalyzeCommandTest(unittest.TestCase):
    def test_mono_44100_reports_frame_and_level_metrics(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "mono.wav"
            audio = np.array([0.0, 0.5, -0.5, 0.0], dtype=np.float64)
            sf.write(path, audio, 44100, subtype="FLOAT")

            result = analyze_command({"path": str(path)})

        self.assertEqual(44100, result["sampleRate"])
        self.assertEqual(1, result["channels"])
        self.assertEqual(4, result["frameCount"])
        self.assertAlmostEqual(4 / 44100, result["duration"])
        self.assertAlmostEqual(0.5, result["peak"])
        self.assertAlmostEqual(np.sqrt(0.125), result["rms"])
        self.assertFalse(result["nearSilence"])

    def test_stereo_48000_silence_preserves_channels_and_is_flagged(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "stereo.wav"
            audio = np.zeros((3, 2), dtype=np.float64)
            sf.write(path, audio, 48000, subtype="FLOAT")

            result = analyze_command({"path": str(path)})

        self.assertEqual(48000, result["sampleRate"])
        self.assertEqual(2, result["channels"])
        self.assertEqual(3, result["frameCount"])
        self.assertAlmostEqual(3 / 48000, result["duration"])
        self.assertEqual(0.0, result["peak"])
        self.assertEqual(0.0, result["rms"])
        self.assertTrue(result["nearSilence"])
