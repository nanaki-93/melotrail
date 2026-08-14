"""Shared validation and atomic lossless WAV output for worker stages."""

import os
from pathlib import Path
import tempfile

import numpy as np
import soundfile as sf


def write_pcm24_wav(output_path: str, audio, sample_rate: int) -> None:
    """Atomically publish an explicit finite WAV/PCM-24 intermediate."""
    path = Path(output_path)
    if path.suffix.lower() != ".wav":
        raise ValueError("Output must be a .wav file; MP3 export is a separate final step")
    samples = np.asarray(audio)
    if not isinstance(sample_rate, int) or sample_rate < 1:
        raise ValueError("sample_rate must be a positive integer")
    if samples.ndim not in (1, 2) or samples.shape[0] < 1:
        raise ValueError("audio must contain at least one frame")
    if not np.isfinite(samples).all():
        raise ValueError("audio contains non-finite samples")
    path.parent.mkdir(parents=True, exist_ok=True)
    handle = tempfile.NamedTemporaryFile(prefix=f".{path.stem}.", suffix=".wav", dir=path.parent, delete=False)
    handle.close()
    temporary = Path(handle.name)
    try:
        sf.write(temporary, samples, sample_rate, format="WAV", subtype="PCM_24")
        info = sf.info(temporary)
        expected_channels = 1 if samples.ndim == 1 else samples.shape[1]
        if (info.format, info.subtype, info.samplerate, info.channels, info.frames) != (
            "WAV", "PCM_24", sample_rate, expected_channels, samples.shape[0]
        ):
            raise ValueError("written WAV did not preserve the requested format")
        os.replace(temporary, path)
    except Exception:
        temporary.unlink(missing_ok=True)
        raise
