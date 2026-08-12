"""Shared validation and lossless WAV output for worker processing stages."""

from pathlib import Path

import soundfile as sf


def write_pcm24_wav(output_path: str, audio, sample_rate: int) -> None:
    """Write an explicit WAV/PCM-24 intermediate, never a misleading MP3 path."""
    path = Path(output_path)
    if path.suffix.lower() != ".wav":
        raise ValueError("Output must be a .wav file; MP3 export is a separate final step")
    path.parent.mkdir(parents=True, exist_ok=True)
    sf.write(path, audio, sample_rate, format="WAV", subtype="PCM_24")
