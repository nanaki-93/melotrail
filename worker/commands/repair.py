"""Conservative audio repair command.

Repairs are intentionally opt-in. In particular, noise reduction uses a
spectral gate/noise estimate instead of multiplying low-amplitude samples by
0.1. The latter destroys quiet piano harmonics and leaves an unnatural noise
floor.
"""

import logging
import numpy as np
from scipy import signal
import soundfile as sf

from worker.commands.audio_output import write_pcm24_wav
from worker.registry import register_command

logger = logging.getLogger("worker.repair")
EPS = 1e-10


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

    audio, sample_rate = sf.read(input_path, always_2d=False)
    audio = np.asarray(audio, dtype=np.float64)

    for repair in repairs:
        repair_type = repair.get("type", "")
        params = repair.get("params", {}) or {}
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
            audio = reduce_noise(
                audio,
                sample_rate,
                float(params.get("threshold", -45)),
                float(params.get("strength", 0.65)),
                float(params.get("floor", 0.18)),
                float(params.get("noise_percent", 15)),
            )
        elif repair_type == "gain_correction":
            audio = apply_gain(audio, float(params.get("gain_db", 0)))
        else:
            logger.warning("Unknown repair ignored: %s", repair_type)

    audio = np.nan_to_num(audio, nan=0.0, posinf=0.0, neginf=0.0)
    audio = np.clip(audio, -1.0, 1.0)
    write_pcm24_wav(output_path, audio, sample_rate)
    logger.info("Repair complete: %s -> %s", input_path, output_path)
    return {"output": output_path}


def remove_dc_offset(audio):
    axis = 0 if audio.ndim == 1 else 0
    return audio - np.mean(audio, axis=axis, keepdims=True)


def remove_clipping(audio, threshold=0.99):
    """Repair only obvious clipped runs; leave normal piano samples untouched."""
    threshold = float(np.clip(threshold, 0.5, 1.0))
    output = audio.copy()
    if output.ndim == 1:
        output = _repair_clipped_channel(output, threshold)
    else:
        for ch in range(output.shape[1]):
            output[:, ch] = _repair_clipped_channel(output[:, ch], threshold)
    return output


def _repair_clipped_channel(x, threshold):
    y = x.copy()
    clipped = np.abs(x) >= threshold
    idx = np.flatnonzero(clipped)
    if len(idx) == 0:
        return y
    # Only interpolate short clipped runs. Long runs contain no recoverable
    # waveform information and should not be reshaped aggressively.
    starts = idx[np.r_[True, np.diff(idx) > 1]]
    ends = idx[np.r_[np.diff(idx) > 1, True]]
    for start, end in zip(starts, ends):
        if end - start + 1 > 32 or start == 0 or end == len(x) - 1:
            continue
        left, right = x[start - 1], x[end + 1]
        y[start:end + 1] = np.linspace(left, right, end - start + 3)[1:-1]
    return y


def remove_hum(audio, sample_rate, freq=60.0):
    output = audio.copy()
    # Remove mains fundamental and first harmonics with narrow notches.
    for harmonic in range(1, 6):
        f = freq * harmonic
        if f >= sample_rate * 0.45:
            break
        b, a = signal.iirnotch(f, 35, sample_rate)
        if output.ndim == 1:
            output = signal.sosfiltfilt(signal.tf2sos(b, a), output)
        else:
            output = signal.sosfiltfilt(signal.tf2sos(b, a), output, axis=0)
    return output


def normalize(audio, peak=-1.0):
    target = 10 ** (float(peak) / 20.0)
    current_peak = float(np.max(np.abs(audio))) if audio.size else 0.0
    return audio * (target / current_peak) if current_peak > EPS else audio


def remove_silence(audio, threshold_db=-50):
    # Do not destructively remove time from the file. The old command already
    # behaved this way; keeping it a no-op avoids breaking sync/tempo metadata.
    return audio


def declick(audio, threshold=0.9):
    output = audio.copy()
    threshold = float(np.clip(threshold, 0.5, 1.0))
    if output.ndim == 1:
        return _declick_channel(output, threshold)
    for ch in range(output.shape[1]):
        output[:, ch] = _declick_channel(output[:, ch], threshold)
    return output


def _declick_channel(x, threshold):
    y = x.copy()
    # Detect isolated large discontinuities, rather than every sample above a
    # threshold (which would damage loud piano notes).
    jump = np.abs(np.diff(x))
    candidates = np.flatnonzero(jump > max(0.25, 1.5 * np.median(jump + EPS))) + 1
    for i in candidates:
        if 1 <= i < len(x) - 1 and abs(x[i]) >= threshold:
            y[i] = 0.5 * (x[i - 1] + x[i + 1])
    return y


def reduce_noise(audio, sample_rate, threshold_db=-45.0, strength=0.65, floor=0.18, noise_percent=15.0):
    """Gentle spectral noise reduction preserving tonal material.

    The quietest frames are used as an automatic noise profile. A soft gain
    mask is applied in the STFT domain; no hard sample-level gate is used.
    """
    strength = float(np.clip(strength, 0.0, 1.0))
    floor = float(np.clip(floor, 0.05, 1.0))
    if strength <= 0 or audio.size == 0:
        return audio

    if audio.ndim == 1:
        return _reduce_noise_channel(audio, sample_rate, threshold_db, strength, floor, noise_percent)

    channels = [
        _reduce_noise_channel(audio[:, ch], sample_rate, threshold_db, strength, floor, noise_percent)
        for ch in range(audio.shape[1])
    ]
    return np.column_stack(channels)


def _reduce_noise_channel(x, sample_rate, threshold_db, strength, floor, noise_percent):
    n_fft = 2048 if sample_rate >= 32000 else 1024
    hop = n_fft // 4
    if len(x) < n_fft:
        return x

    f, t, z = signal.stft(x, fs=sample_rate, nperseg=n_fft, noverlap=n_fft-hop, boundary="zeros")
    mag = np.abs(z)
    power = mag * mag
    frame_rms = np.sqrt(np.mean(power, axis=0) + EPS)
    threshold = 10 ** (float(threshold_db) / 20.0)
    quiet = frame_rms <= max(threshold, np.percentile(frame_rms, np.clip(noise_percent, 1, 50)))
    if not np.any(quiet):
        quiet[np.argsort(frame_rms)[:max(1, len(frame_rms)//10)]] = True

    noise_power = np.median(power[:, quiet], axis=1, keepdims=True)
    # Wiener-like soft suppression. At strong tonal content the gain quickly
    # approaches 1, while stationary noise is attenuated toward `floor`.
    snr = np.maximum(power / (noise_power + EPS) - 1.0, 0.0)
    gain = snr / (snr + 1.0)
    gain = floor + (1.0 - floor) * gain
    gain = 1.0 - strength * (1.0 - gain)

    _, y = signal.istft(z * gain, fs=sample_rate, nperseg=n_fft, noverlap=n_fft-hop, input_onesided=True, boundary=True)
    if len(y) < len(x):
        y = np.pad(y, (0, len(x) - len(y)))
    return y[:len(x)]


def apply_gain(audio, gain_db=0):
    return audio * (10 ** (float(gain_db) / 20.0))
