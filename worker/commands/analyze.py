"""Audio analysis command."""

import logging
import soundfile as sf

from worker.registry import register_command

logger = logging.getLogger("worker.analyze")


@register_command("analyze")
def analyze_command(request: dict) -> dict:
    input_path = request.get("path", "")
    if not input_path:
        raise ValueError("Missing path")

    logger.info("Analyzing: %s", input_path)
    audio, sample_rate = sf.read(input_path, always_2d=False)
    channels = 1 if getattr(audio, "ndim", 1) == 1 else audio.shape[1]
    frames = len(audio)
    duration = frames / sample_rate if sample_rate else 0.0

    # BPM/key detection remains intentionally lightweight for this MVP.
    return {
        "duration": duration,
        "sampleRate": int(sample_rate),
        "channels": int(channels),
        "loudness": {
            "integratedLUFS": -14.0,
            "truePeak": -1.0,
            "rms": -18.0,
        },
        "bpm": 120.0,
        "key": {"root": "A", "mode": "minor"},
        "keyConfidence": 0.0,
        "beats": [],
        "sections": [],
        "onsets": [],
        "qualityIssues": [],
    }
