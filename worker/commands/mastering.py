"""Audio mastering command."""

import logging
import numpy as np
from scipy import signal
import soundfile as sf

from worker.registry import register_command

logger = logging.getLogger("worker.mastering")


@register_command("master")
def master_command(request: dict) -> dict:
    input_path = request.get("path", "")
    output_path = request.get("outputPath", "")
    settings = request.get("settings", {})

    if not input_path:
        raise ValueError("Missing path")
    if not output_path:
        raise ValueError("Missing outputPath")

    audio, sample_rate = sf.read(input_path)

    if settings.get("eq_enabled", False):
        audio = apply_eq(audio, sample_rate, settings.get("eq", {}))
    if settings.get("compressor_enabled", False):
        audio = apply_compressor(audio, settings.get("compressor", {}))
    if settings.get("saturation_enabled", False):
        audio = apply_saturation(audio, settings.get("saturation", {}))
    if settings.get("stereo_enabled", False):
        audio = apply_stereo(audio, settings.get("stereo", {}))
    if settings.get("limiter_enabled", False):
        audio = apply_limiter(audio, settings.get("limiter", {}))

    loudness = analyze_loudness(audio)
    target_peak = 10 ** (float(settings.get("target_peak_db", -1.0)) / 20)
    current_peak = np.max(np.abs(audio))
    if current_peak > 0:
        audio = audio * (target_peak / current_peak)

    sf.write(output_path, audio, sample_rate)
    return {"output": output_path, "loudness": loudness}


def apply_eq(audio, sample_rate, settings):
    output = audio.copy()
    for band in settings.get("bands", []):
        freq = float(band.get("frequency", 1000))
        gain_db = float(band.get("gain", 0))
        q = float(band.get("q", 1.0))
        band_type = band.get("type", "peaking")
        gain = 10 ** (gain_db / 20)

        if band_type == "lowshelf":
            b, a = signal.butter(2, min(freq * 2 / sample_rate, 0.99), btype="low")
            low = signal.filtfilt(b, a, output, axis=0)
            output = output * (1 - gain) + low * gain
        elif band_type == "highshelf":
            b, a = signal.butter(2, min(freq * 2 / sample_rate, 0.99), btype="high")
            high = signal.filtfilt(b, a, output, axis=0)
            output = output * (1 - gain) + high * gain
        elif band_type == "peaking":
            w0 = 2 * np.pi * freq / sample_rate
            alpha = np.sin(w0) / (2 * q)
            cos_w0 = np.cos(w0)
            b0 = 1 + alpha * gain
            b1 = -2 * cos_w0
            b2 = 1 - alpha * gain
            a0 = 1 + alpha / gain
            a1 = -2 * cos_w0
            a2 = 1 - alpha / gain
            b = [b0 / a0, b1 / a0, b2 / a0]
            a = [1, a1 / a0, a2 / a0]
            output = signal.filtfilt(b, a, output, axis=0)
    return output


def apply_compressor(audio, settings):
    threshold = 10 ** (float(settings.get("threshold_db", -24)) / 20)
    ratio = float(settings.get("ratio", 4.0))
    envelope = np.abs(audio)
    reduction = np.ones_like(envelope)
    mask = envelope > threshold
    reduction[mask] = 1 - ((envelope[mask] - threshold) * (1 - 1 / ratio) / (threshold + 0.001))
    return audio * np.clip(reduction, 0, 1)


def apply_saturation(audio, settings):
    amount = float(settings.get("amount", 0.5))
    return np.tanh(audio * (1 + amount * 3)) * (1 - amount * 0.3)


def apply_stereo(audio, settings):
    if audio.ndim != 2 or audio.shape[1] < 2:
        return audio
    width = float(settings.get("width", 1.0))
    left, right = audio[:, 0], audio[:, 1]
    mid = (left + right) / 2
    side = (left - right) / 2 * width
    return np.column_stack((mid + side, mid - side))


def apply_limiter(audio, settings):
    ceiling = 10 ** (float(settings.get("ceiling_db", -1.0)) / 20)
    return np.clip(audio, -ceiling, ceiling)


def analyze_loudness(audio):
    weighted = audio ** 2
    integrated_lufs = 10 * np.log10(np.mean(weighted) + 1e-10) + 0.691
    true_peak = float(np.max(np.abs(audio)))
    rms = float(10 ** (integrated_lufs / 10 - 0.691))
    return {
        "integrated_lufs": round(float(integrated_lufs), 1),
        "true_peak_db": round(20 * np.log10(true_peak + 1e-10), 1),
        "rms_db": round(20 * np.log10(rms + 1e-10), 1),
    }
