"""MP3/other compressed audio to lossless WAV conversion."""

import librosa
import numpy as np

from worker.commands.audio_output import write_pcm24_wav
from worker.registry import register_command


@register_command("mp3_convert")
def mp3_convert_command(request: dict) -> dict:
    input_path = request.get("path", "")
    output_path = request.get("outputPath", "")
    if not input_path:
        raise ValueError("Missing path")
    if not output_path:
        raise ValueError("Missing outputPath")

    # Keep the decoded floating-point samples. The previous implementation
    # converted to int16 and then handed that integer array to soundfile,
    # making it easy to introduce an unnecessary quantisation/scaling error.
    audio, sample_rate = librosa.load(input_path, sr=None, mono=False, dtype=np.float32)
    audio = np.asarray(audio, dtype=np.float32)
    if audio.ndim == 1:
        channels = 1
        frames = len(audio)
        data = audio
    else:
        channels = audio.shape[0]
        frames = audio.shape[1]
        data = audio.T

    data = np.nan_to_num(data, nan=0.0, posinf=0.0, neginf=0.0)
    data = np.clip(data, -1.0, 1.0)
    write_pcm24_wav(output_path, data, sample_rate)

    return {
        "output": output_path,
        "sampleRate": int(sample_rate),
        "channels": int(channels),
        "duration": float(frames / sample_rate),
    }
