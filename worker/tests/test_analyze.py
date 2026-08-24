import tempfile
import unittest
from pathlib import Path

import numpy as np
import soundfile as sf

from worker.commands.analyze import ANALYSIS_CONTRACT_VERSION, HOP_LENGTH, analyze_command, timing_evidence


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
        self.assertEqual(ANALYSIS_CONTRACT_VERSION, result["analysisVersion"])
        self.assertIn("beats", result)
        self.assertIn("onsets", result)
        self.assertIn("tempoCandidates", result)
        self.assertIn(result["downbeat"]["status"], {"UNKNOWN", "REVIEW_REQUIRED"})

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
        self.assertEqual([], result["beats"])
        self.assertEqual([], result["onsets"])
        self.assertEqual("UNKNOWN", result["downbeat"]["status"])
        self.assertEqual("INSUFFICIENT_BEAT_EVIDENCE", result["downbeat"]["reason"])

    def test_empty_audio_is_explicitly_uncertain(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "empty.wav"
            sf.write(path, np.array([], dtype=np.float64), 44100, subtype="FLOAT")

            result = analyze_command({"path": str(path)})

        self.assertEqual(0, result["frameCount"])
        self.assertTrue(result["nearSilence"])
        self.assertEqual([], result["beats"])
        self.assertEqual("UNKNOWN", result["downbeat"]["status"])

    def test_synthetic_pickup_and_human_offset_evidence_is_bounded_and_review_required(self):
        evidence = timing_evidence(
            np.ones(44100 * 4, dtype=np.float64),
            44100,
            {"detectBPM": False, "detectBeats": False, "detectOnsets": False},
            128,
        )
        self.assertEqual([], evidence["beats"])
        self.assertEqual("UNKNOWN", evidence["downbeat"]["status"])

        # This direct timing fixture stands in for a pickup-free four-beat body.
        from unittest.mock import patch
        with patch("worker.commands.analyze.librosa.beat.beat_track", return_value=(120.0, np.array([20, 63, 106, 149]))), patch(
            "worker.commands.analyze.librosa.onset.onset_detect", return_value=np.array([21, 64, 107, 150])
        ):
            evidence = timing_evidence(np.ones(44100 * 4), 44100, {}, 128)
        self.assertEqual([20, 63, 106, 149], [beat["frame"] for beat in evidence["beats"]])
        self.assertEqual([21, 64, 107, 150], [onset["frame"] for onset in evidence["onsets"]])
        self.assertEqual("REVIEW_REQUIRED", evidence["downbeat"]["status"])
        self.assertAlmostEqual(20 * HOP_LENGTH / 44100, evidence["downbeat"]["timeSeconds"])
        self.assertEqual(1, len(evidence["tempoCandidates"]))

    def test_rejects_unsupported_analyze_contract_version(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "mono.wav"
            sf.write(path, np.ones(100, dtype=np.float64), 44100, subtype="FLOAT")
            with self.assertRaisesRegex(ValueError, "Unsupported analyze request version"):
                analyze_command({"version": 1, "path": str(path)})
