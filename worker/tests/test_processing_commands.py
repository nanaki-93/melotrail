import unittest
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest.mock import patch

import numpy as np
import soundfile as sf

from worker.commands.mastering import master_command
from worker.commands.mp3_convert import mp3_convert_command


class ProcessingCommandsTest(unittest.TestCase):
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
