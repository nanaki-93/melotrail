import unittest
import sys
from pathlib import Path
from tempfile import TemporaryDirectory
from types import SimpleNamespace
from unittest.mock import patch

import numpy as np
import soundfile as sf

from worker.commands.mastering import analyze_loudness, master_command, true_peak_amplitude
from worker.commands.mp3_convert import mp3_convert_command
from worker.commands.mp3_export import mp3_export_command


class ProcessingCommandsTest(unittest.TestCase):
    def test_mp3_export_validates_and_atomically_publishes_encoder_output(self):
        with TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            input_path = root / "master.wav"
            output_path = root / "song.mp3"
            sf.write(input_path, np.array([[0.2], [-0.2]], dtype=np.float32), 22050, format="WAV", subtype="PCM_24")
            output_path.write_bytes(b"old export")

            class Encoder:
                def set_bit_rate(self, value): self.bitrate = value
                def set_in_sample_rate(self, value): self.sample_rate = value
                def set_channels(self, value): self.channels = value
                def set_quality(self, value): self.quality = value
                def encode(self, value): return b"ID3\x04\x00\x00\x00\x00\x00\x00"
                def flush(self): return b"\xff\xfb\x00\x00"

            with patch.dict(sys.modules, {"lameenc": SimpleNamespace(Encoder=Encoder)}):
                result = mp3_export_command({"path": str(input_path), "outputPath": str(output_path), "bitrateKbps": 256})

            self.assertEqual(str(output_path), result["output"])
            self.assertEqual(256, result["bitrateKbps"])
            self.assertEqual(22050, result["sampleRate"])
            self.assertEqual(1, result["channels"])
            self.assertTrue(output_path.read_bytes().startswith(b"ID3"))
            self.assertFalse(any(path.name.startswith(".song.mp3.") for path in root.iterdir()))

    def test_mp3_export_rejects_invalid_bitrate_paths_and_riff_disguised_output(self):
        with TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            input_path = root / "master.wav"
            sf.write(input_path, np.array([[0.2]], dtype=np.float32), 22050, format="WAV", subtype="PCM_24")
            with self.assertRaisesRegex(ValueError, "bitrateKbps"):
                mp3_export_command({"path": str(input_path), "outputPath": str(root / "song.mp3"), "bitrateKbps": 111})
            with self.assertRaisesRegex(ValueError, r"\.mp3"):
                mp3_export_command({"path": str(input_path), "outputPath": str(root / "song.wav")})

            class RiffEncoder:
                def set_bit_rate(self, value): pass
                def set_in_sample_rate(self, value): pass
                def set_channels(self, value): pass
                def set_quality(self, value): pass
                def encode(self, value): return b"RIFFfakeWAVE"
                def flush(self): return b""

            output_path = root / "song.mp3"
            output_path.write_bytes(b"previous valid export")
            with patch.dict(sys.modules, {"lameenc": SimpleNamespace(Encoder=RiffEncoder)}):
                with self.assertRaisesRegex(ValueError, "RIFF/WAVE"):
                    mp3_export_command({"path": str(input_path), "outputPath": str(output_path)})
            self.assertEqual(b"previous valid export", output_path.read_bytes())

    def test_mp3_conversion_writes_lossless_wav_with_decoded_format(self):
        with TemporaryDirectory() as temp_dir:
            output = Path(temp_dir) / "decoded.wav"
            decoded = np.array(
                [[0.25, -0.25, 0.5], [-0.5, 0.5, -0.25]], dtype=np.float32
            )
            with patch("worker.commands.mp3_convert.librosa.load", return_value=(decoded, 32000)):
                result = mp3_convert_command({"path": "fixture.mp3", "outputPath": str(output)})

            info = sf.info(output)
            self.assertEqual(str(output), result["output"])
            self.assertEqual(32000, result["sampleRate"])
            self.assertEqual(2, result["channels"])
            self.assertEqual("WAV", info.format)
            self.assertEqual("PCM_24", info.subtype)
            self.assertEqual(32000, info.samplerate)
            self.assertEqual(2, info.channels)
            self.assertEqual(3, info.frames)

    def test_mastering_writes_valid_wav_without_changing_format(self):
        with TemporaryDirectory() as temp_dir:
            input_path = Path(temp_dir) / "mix.wav"
            output_path = Path(temp_dir) / "master.wav"
            audio = np.array(
                [[0.2, -0.2, 0.1], [-0.1, 0.1, -0.2], [0.05, -0.05, 0.0]],
                dtype=np.float64,
            )
            sf.write(input_path, audio, 32000, format="WAV", subtype="PCM_24")

            result = master_command(
                {
                    "path": str(input_path),
                    "outputPath": str(output_path),
                    "settings": {"target_peak_db": -1.0},
                }
            )

            info = sf.info(output_path)
            self.assertEqual(str(output_path), result["output"])
            self.assertEqual("WAV", info.format)
            self.assertEqual("PCM_24", info.subtype)
            self.assertEqual(32000, info.samplerate)
            self.assertEqual(3, info.channels)
            self.assertEqual(3, info.frames)

    def test_mastering_reports_bs1770_loudness_true_peak_and_dynamics_evidence(self):
        sample_rate = 48000
        seconds = 4
        time = np.arange(sample_rate * seconds) / sample_rate
        # This near-Nyquist sine has an inter-sample peak above its sample peak.
        audio = 0.8 * np.sin(2 * np.pi * 19000 * time)

        report = analyze_loudness(audio, sample_rate)

        self.assertEqual("ITU-R BS.1770-4 / EBU R128", report["measurement_standard"])
        self.assertGreater(true_peak_amplitude(audio), float(np.max(np.abs(audio))))
        self.assertIn("lra_lu", report)
        self.assertIn("crest_db", report)
        self.assertIn("limiter_gain_reduction", report)

    def test_mastering_does_not_accept_target_loudness_after_excessive_limiting(self):
        with TemporaryDirectory() as temp_dir:
            input_path = Path(temp_dir) / "mix.wav"
            output_path = Path(temp_dir) / "master.wav"
            sample_rate = 48000
            audio = 0.95 * np.sin(2 * np.pi * 440 * np.arange(sample_rate * 4) / sample_rate)
            sf.write(input_path, audio, sample_rate, format="WAV", subtype="PCM_24")

            result = master_command({
                "path": str(input_path), "outputPath": str(output_path),
                "settings": {"target_lufs": -14.0, "limiter_enabled": True,
                             "limiter": {"ceiling_db": -18.0},
                             "max_limiter_gain_reduction_db": 1.0},
            })

            self.assertFalse(result["loudness"]["dynamics_preserved"])
            self.assertIn("limiter-gain-reduction-too-large", result["loudness"]["quality_issues"])
