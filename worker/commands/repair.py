"""Deterministic audio repair command."""

import logging
import numpy as np
from scipy import signal
import soundfile as sf

from worker.registry import register_command

logger = logging.getLogger("worker.repair")


@register_command("repair")
def repair_command(request: dict) -> dict:
    input_path = request.get("path", "")
    output_path = request.get("outputPath", "")
    repairs = request.get("repairs", [])

    if not input_path:
        raise ValueError("Missing path")
    if not output_path:
        raise ValueError("Missing outputPath")
    if not isinstance(repairs, list) or not repairs:
        raise ValueError("No repairs specified")

    audio, sample_rate = sf.read(input_path)
    for repair in repairs:
        repair_type = repair.get("type", "")
        params = repair.get("params", {})

        if repair_type in ("dc_offset", "denorm"):
            audio = remove_dc_offset(audio)
        elif repair_type in ("clip_removal", "clipping"):
            audio = remove_clipping(audio, float(params.get("threshold", 0.99)))
        elif repair_type == "dehum":
            audio = remove_hum(audio, sample_rate, float(params.get("freq", 60)))
        elif repair_type == "normalize":
            audio = normalize(audio, float(params.get("peak", -1.0)))
        elif repair_type == "silence_removal":
            audio = remove_silence(audio, float(params.get("threshold", -50)))
        elif repair_type == "declick":
            audio = declick(audio, float(params.get("threshold", 0.9)))
        elif repair_type == "noise_reduction":
            audio = reduce_noise(audio, sample_rate, float(params.get("threshold", -40)))
        elif repair_type == "gain_correction":
            audio = apply_gain(audio, float(params.get("gain_db", 0)))

    sf.write(output_path, audio, sample_rate)
    logger.info("Repair complete: %s -> %s", input_path, output_path)
    return {"output": output_path}


def remove_dc_offset(audio):
    return audio - np.mean(audio, axis=0)


def remove_clipping(audio, threshold=0.99):
    return np.tanh(audio / threshold) * threshold


def remove_hum(audio, sample_rate, freq=60.0):
    if audio.ndim == 1:
        b, a = signal.iirnotch(freq, 30, sample_rate)
        return signal.filtfilt(b, a, audio)
    output = np.zeros_like(audio)
    b, a = signal.iirnotch(freq, 30, sample_rate)
    for ch in range(audio.shape[1]):
        output[:, ch] = signal.filtfilt(b, a, audio[:, ch])
    return output


def normalize(audio, peak=-1.0):
    target = 10 ** (peak / 20)
    current_peak = np.max(np.abs(audio))
    return audio * (target / current_peak) if current_peak > 0 else audio


def remove_silence(audio, threshold_db=-50):
    # Placeholder: preserve samples; no destructive trimming in this MVP.
    return audio


def declick(audio, threshold=0.9):
    output = audio.copy()
    mask = np.abs(output) > threshold
    if output.ndim == 1:
        for i in np.flatnonzero(mask):
            if 0 < i < len(output) - 1:
                output[i] = (output[i - 1] + output[i + 1]) / 2
    return output


def reduce_noise(audio, sample_rate, threshold_db=-40):
    threshold = 10 ** (threshold_db / 20)
    return np.where(np.abs(audio) < threshold, audio * 0.1, audio)


def apply_gain(audio, gain_db=0):
    return audio * (10 ** (gain_db / 20))
