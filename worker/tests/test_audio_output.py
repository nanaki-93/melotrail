import numpy as np
import soundfile as sf
import unittest
from pathlib import Path
from tempfile import TemporaryDirectory

from worker.commands.audio_output import write_pcm24_wav


class AudioOutputTest(unittest.TestCase):
    def test_write_pcm24_wav_preserves_non_48khz_multichannel_format(self):
        with TemporaryDirectory() as temp_dir:
            output = Path(temp_dir) / "stage.wav"
            audio = np.array([[0.25, -0.25, 0.5], [-0.5, 0.5, -0.25]], dtype=np.float64)

            write_pcm24_wav(str(output), audio, 32000)

            info = sf.info(output)
            self.assertEqual("WAV", info.format)
            self.assertEqual("PCM_24", info.subtype)
            self.assertEqual(32000, info.samplerate)
            self.assertEqual(3, info.channels)
            self.assertEqual(2, info.frames)

    def test_write_pcm24_wav_rejects_mp3_filename(self):
        with TemporaryDirectory() as temp_dir:
            with self.assertRaisesRegex(ValueError, "Output must be a .wav file"):
                write_pcm24_wav(str(Path(temp_dir) / "master.mp3"), np.array([0.0]), 44100)
