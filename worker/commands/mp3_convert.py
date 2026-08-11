"""MP3 to WAV conversion command."""

import librosa
import numpy as np
import soundfile as sf

from worker.registry import register_command


@register_command("mp3_convert")
def mp3_convert_command(request: dict) -> dict:
    input_path = request.get("path", "")
    output_path = request.get("outputPath", "")

    if not input_path:
        raise ValueError("Missing path")
    if not output_path:
        raise ValueError("Missing outputPath")

    audio, sample_rate = librosa.load(input_path, sr=None, mono=False)
    if audio.dtype != np.int16:
        audio = (audio * 32767).astype(np.int16)

    sf.write(output_path, audio.T, sample_rate, subtype="PCM_16")
    channels = audio.shape[0] if audio.ndim > 1 else 1
    frames = audio.shape[-1] if audio.ndim > 1 else len(audio)

    return {
        "output": output_path,
        "sampleRate": int(sample_rate),
        "channels": int(channels),
        "duration": float(frames / sample_rate),
    }
