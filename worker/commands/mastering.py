"""Safe, deterministic audio mastering command.

The mastering stage is deliberately conservative. Processing is bypassed unless
explicitly enabled in the request, and the final file is always written as a
standards-compliant little-endian PCM WAV.
"""

import logging

import numpy as np
from scipy import signal
import soundfile as sf

from worker.registry import register_command

logger = logging.getLogger("worker.mastering")
EPS = 1e-12


@register_command("master")
def master_command(request: dict) -> dict:
    input_path = request.get("path", "")
    output_path = request.get("outputPath", "")
    settings = request.get("settings", {}) or {}

    if not input_path:
        raise ValueError("Missing path")
    if not output_path:
        raise ValueError("Missing outputPath")

    audio, sample_rate = sf.read(input_path, always_2d=False)
    audio = np.asarray(audio, dtype=np.float64)
    audio = np.nan_to_num(audio, nan=0.0, posinf=0.0, neginf=0.0)

    if audio.size == 0:
        raise ValueError("Input audio is empty")

    if settings.get("eq_enabled", False):
        audio = apply_eq(audio, sample_rate, settings.get("eq", {}))

    if settings.get("compressor_enabled", False):
        audio = apply_compressor(
            audio, sample_rate, settings.get("compressor", {})
        )

    if settings.get("saturation_enabled", False):
        audio = apply_saturation(audio, settings.get("saturation", {}))

    if settings.get("stereo_enabled", False):
        audio = apply_stereo(audio, settings.get("stereo", {}))

    if settings.get("limiter_enabled", False):
        audio = apply_limiter(
            audio, sample_rate, settings.get("limiter", {})
        )

    # Final safety gain. Never boost quiet material here.
    target_peak = 10 ** (
        float(settings.get("target_peak_db", -1.0)) / 20.0
    )
    current_peak = float(np.max(np.abs(audio))) if audio.size else 0.0

    if current_peak > target_peak and current_peak > EPS:
        audio *= target_peak / current_peak

    audio = np.nan_to_num(
        audio, nan=0.0, posinf=0.0, neginf=0.0
    )
    audio = np.clip(audio, -1.0, 1.0)

    loudness = analyze_loudness(audio)

    # IMPORTANT:
    # Explicitly specify both the container and PCM subtype.
    #
    # Do NOT pass endian="..." here. libsndfile handles the WAV byte order
    # correctly. Supplying an endian value together with an incompatible
    # format/subtype is what causes:
    #   "Invalid combination of format, subtype and endian"
    #
    # PCM_24 is a valid WAV subtype and is preserved without the old
    # implicit-format ambiguity.
    sf.write(
        output_path,
        audio,
        sample_rate,
        format="WAV",
        subtype="PCM_24",
    )

    logger.info(
        "Mastered %s -> %s (%d Hz, %s)",
        input_path,
        output_path,
        sample_rate,
        "mono" if audio.ndim == 1 else f"{audio.shape[1]}ch",
    )

    return {
        "output": output_path,
        "loudness": loudness,
    }


def _biquad(audio, b, a):
    sos = signal.tf2sos(b, a)

    if audio.ndim == 1:
        return signal.sosfiltfilt(sos, audio)

    return signal.sosfiltfilt(sos, audio, axis=0)


def _valid_frequency(freq, sample_rate):
    return max(10.0, min(float(freq), sample_rate * 0.45))


def _rbj_peaking(freq, gain_db, q, sample_rate):
    freq = _valid_frequency(freq, sample_rate)
    q = max(0.05, float(q))

    A = 10 ** (float(gain_db) / 40.0)
    w0 = 2 * np.pi * freq / sample_rate
    alpha = np.sin(w0) / (2 * q)
    c = np.cos(w0)

    b = [
        1 + alpha * A,
        -2 * c,
        1 - alpha * A,
    ]
    a = [
        1 + alpha / A,
        -2 * c,
        1 - alpha / A,
    ]

    return b, a


def _rbj_shelf(freq, gain_db, sample_rate, high=False):
    freq = _valid_frequency(freq, sample_rate)

    A = 10 ** (float(gain_db) / 40.0)
    w0 = 2 * np.pi * freq / sample_rate
    c = np.cos(w0)
    s = np.sin(w0)

    alpha = s / 2 * np.sqrt(2.0)
    beta = 2 * np.sqrt(A) * alpha

    if high:
        b = [
            A * ((A + 1) + (A - 1) * c + beta),
            -2 * A * ((A - 1) + (A + 1) * c),
            A * ((A + 1) + (A - 1) * c - beta),
        ]
        a = [
            (A + 1) - (A - 1) * c + beta,
            2 * ((A - 1) - (A + 1) * c),
            (A + 1) - (A - 1) * c - beta,
        ]
    else:
        b = [
            A * ((A + 1) - (A - 1) * c + beta),
            2 * A * ((A - 1) - (A + 1) * c),
            A * ((A + 1) - (A - 1) * c - beta),
        ]
        a = [
            (A + 1) + (A - 1) * c + beta,
            -2 * ((A - 1) + (A + 1) * c),
            (A + 1) + (A - 1) * c - beta,
        ]

    return b, a


def apply_eq(audio, sample_rate, settings):
    output = audio.copy()

    for band in settings.get("bands", []):
        freq = float(band.get("frequency", 1000))
        gain_db = float(band.get("gain", 0))

        if abs(gain_db) < 1e-9:
            continue

        kind = str(band.get("type", "peaking")).lower()

        if kind == "lowshelf":
            b, a = _rbj_shelf(
                freq, gain_db, sample_rate, high=False
            )
        elif kind == "highshelf":
            b, a = _rbj_shelf(
                freq, gain_db, sample_rate, high=True
            )
        else:
            b, a = _rbj_peaking(
                freq,
                gain_db,
                float(band.get("q", 1.0)),
                sample_rate,
            )

        output = _biquad(output, b, a)

    return output


def apply_compressor(audio, sample_rate, settings):
    """Gentle RMS/envelope compressor with attack/release smoothing."""
    threshold_db = float(settings.get("threshold_db", -24.0))
    ratio = max(1.0, float(settings.get("ratio", 2.0)))
    attack_ms = max(0.1, float(settings.get("attack_ms", 15.0)))
    release_ms = max(1.0, float(settings.get("release_ms", 120.0)))
    makeup_db = float(
        settings.get(
            "makeup_gain_db",
            settings.get("makeup_db", 0.0),
        )
    )

    if audio.ndim == 2:
        mono = np.mean(np.abs(audio), axis=1)
    else:
        mono = np.abs(audio)

    attack = np.exp(
        -1.0 / (sample_rate * attack_ms / 1000.0)
    )
    release = np.exp(
        -1.0 / (sample_rate * release_ms / 1000.0)
    )

    env = np.empty_like(mono)
    envelope = 0.0

    for i, x in enumerate(mono):
        coeff = attack if x > envelope else release
        envelope = coeff * envelope + (1.0 - coeff) * x
        env[i] = envelope

    env_db = 20 * np.log10(np.maximum(env, EPS))
    over = env_db - threshold_db

    gain_reduction_db = np.where(
        over > 0,
        -(over - over / ratio),
        0.0,
    )

    gain = 10 ** (
        (gain_reduction_db + makeup_db) / 20.0
    )

    if audio.ndim == 2:
        return audio * gain[:, None]

    return audio * gain


def apply_saturation(audio, settings):
    amount = float(
        np.clip(settings.get("amount", 0.15), 0.0, 1.0)
    )

    if amount <= 0:
        return audio

    drive = 1.0 + amount * 1.5
    saturated = np.tanh(audio * drive) / np.tanh(drive)

    return (
        audio * (1.0 - amount)
        + saturated * amount
    )


def apply_stereo(audio, settings):
    if audio.ndim != 2 or audio.shape[1] < 2:
        return audio

    width = float(
        np.clip(settings.get("width", 1.0), 0.0, 2.0)
    )

    left = audio[:, 0]
    right = audio[:, 1]

    mid = (left + right) * 0.5
    side = (left - right) * 0.5 * width

    return np.column_stack(
        (mid + side, mid - side)
    )


def apply_limiter(audio, sample_rate, settings):
    """Transparent peak limiter with attack/release smoothing."""
    ceiling_db = float(
        settings.get("ceiling_db", -1.0)
    )
    ceiling = 10 ** (ceiling_db / 20.0)

    attack_ms = max(
        0.1,
        float(settings.get("attack_ms", 1.0)),
    )
    release_ms = max(
        5.0,
        float(settings.get("release_ms", 80.0)),
    )

    if audio.ndim == 2:
        peak = np.max(np.abs(audio), axis=1)
    else:
        peak = np.abs(audio)

    desired = np.minimum(
        1.0,
        ceiling / np.maximum(peak, EPS),
    )

    attack = np.exp(
        -1.0 / (sample_rate * attack_ms / 1000.0)
    )
    release = np.exp(
        -1.0 / (sample_rate * release_ms / 1000.0)
    )

    gain = np.empty_like(desired)
    current_gain = 1.0

    for i, target in enumerate(desired):
        coeff = (
            attack
            if target < current_gain
            else release
        )

        current_gain = (
            coeff * current_gain
            + (1.0 - coeff) * target
        )

        gain[i] = current_gain

    if audio.ndim == 2:
        return audio * gain[:, None]

    return audio * gain


def analyze_loudness(audio):
    if audio.ndim == 2:
        mono = np.mean(audio, axis=1)
    else:
        mono = audio

    rms = float(
        np.sqrt(np.mean(mono * mono) + EPS)
    )
    peak = (
        float(np.max(np.abs(audio)))
        if audio.size
        else 0.0
    )

    return {
        "integrated_lufs": round(
            20 * np.log10(rms + EPS) - 0.691,
            1,
        ),
        "true_peak_db": round(
            20 * np.log10(peak + EPS),
            1,
        ),
        "rms_db": round(
            20 * np.log10(rms + EPS),
            1,
        ),
    }