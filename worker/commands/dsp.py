"""Optional, conservative DSP command.

The command is intentionally a transparent pass-through when no DSP settings
are supplied. It never changes the source simply because the endpoint was
called.
"""

import numpy as np
import soundfile as sf

from worker.commands.audio_output import write_pcm24_wav
from worker.registry import register_command


@register_command("apply_dsp")
def apply_dsp_command(request: dict) -> dict:
    input_path = request.get("path", "")
    output_path = request.get("outputPath", "")
    settings = request.get("settings", {}) or {}
    if not input_path:
        raise ValueError("Missing path")
    if not output_path:
        raise ValueError("Missing outputPath")

    audio, sample_rate = sf.read(input_path, always_2d=False)
    audio = np.asarray(audio, dtype=np.float64)

    # This endpoint is currently a safe transport boundary. If the caller asks
    # for gain, apply only that explicit operation.
    gain_db = float(settings.get("gain_db", 0.0))
    if gain_db:
        audio *= 10 ** (gain_db / 20.0)

    audio = np.clip(audio, -1.0, 1.0)
    write_pcm24_wav(output_path, audio, sample_rate)
    return {"output": output_path, "settings": settings}
