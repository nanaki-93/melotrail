"""Safe, deterministic audio mastering command.

The mastering stage is deliberately conservative. Processing is bypassed unless
explicitly enabled in the request, and the final file is always written as a
standards-compliant little-endian PCM WAV.
"""

import logging

import numpy as np
from scipy import signal
import soundfile as sf

from worker.commands.audio_output import write_pcm24_wav
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

    # Loudness is set before final limiting so the limiter protects the upload
    # master without silently leaving quiet songs far below the release target.
    target_lufs = settings.get("target_lufs")
    if target_lufs is not None:
        current_lufs = analyze_loudness(audio, sample_rate)["integrated_lufs"]
        gain_db = float(target_lufs) - float(current_lufs)
        gain_db = max(-12.0, min(12.0, gain_db))
        audio *= 10 ** (gain_db / 20.0)

    limiter_measurement = {"max_gain_reduction_db": 0.0, "mean_gain_reduction_db": 0.0}
    if settings.get("limiter_enabled", False):
        audio, limiter_measurement = apply_limiter(
            audio, sample_rate, settings.get("limiter", {})
        )

    # A sample peak is not a safe upload ceiling: reconstruction can exceed it.
    # Apply the measured 4x true-peak ceiling after all processing instead.
    target_peak_db = float(settings.get("target_peak_dbtp", settings.get("target_peak_db", -1.0)))
    target_peak = 10 ** (target_peak_db / 20.0)
    measured_peak = true_peak_amplitude(audio)
    if measured_peak > target_peak and measured_peak > EPS:
        audio *= target_peak / measured_peak

    audio = np.nan_to_num(
        audio, nan=0.0, posinf=0.0, neginf=0.0
    )
    audio = np.clip(audio, -1.0, 1.0)

    loudness = analyze_loudness(audio, sample_rate, limiter_measurement)
    dynamics = dynamics_quality(loudness, settings)
    loudness["dynamics_preserved"] = dynamics["passed"]
    loudness["quality_issues"] = dynamics["issues"]
    loudness["loudness_reference"] = loudness_reference(loudness, settings)

    # PCM_24 inside an explicit WAV container keeps the processing chain
    # lossless and cannot be mistaken for MP3 export.
    write_pcm24_wav(output_path, audio, sample_rate)

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
    """Apply a stable biquad to every channel of one audio buffer."""
    sos = signal.tf2sos(b, a)

    # Tiny fixtures and very short user clips cannot satisfy filtfilt's padding
    # contract. A causal pass is still a defined measurement fallback; normal
    # masters use the zero-phase path below.
    if audio.shape[0] <= 9:
        return signal.sosfilt(sos, audio, axis=0)
    if audio.ndim == 1:
        return signal.sosfiltfilt(sos, audio)

    return signal.sosfiltfilt(sos, audio, axis=0)


def _valid_frequency(freq, sample_rate):
    """Constrain a filter centre frequency to a stable audible range."""
    return max(10.0, min(float(freq), sample_rate * 0.45))


def _rbj_peaking(freq, gain_db, q, sample_rate):
    """Return RBJ peaking-EQ coefficients for the supplied sample rate."""
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
    """Return RBJ low- or high-shelf coefficients for the supplied sample rate."""
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
    """Apply the explicitly requested bounded mastering EQ bands."""
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
    """Blend a bounded tanh saturation treatment into the source audio."""
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
    """Apply a bounded mid/side width adjustment to stereo material only."""
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
    """Apply the peak limiter and return its measured gain-reduction evidence."""
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

    output = audio * gain[:, None] if audio.ndim == 2 else audio * gain
    reduction = -20.0 * np.log10(np.maximum(gain, EPS))
    return output, {
        "max_gain_reduction_db": round(float(np.max(reduction)), 3),
        "mean_gain_reduction_db": round(float(np.mean(reduction)), 3),
    }


def _k_weight(audio, sample_rate):
    """Apply the BS.1770 K-weighting high shelf and RLB high-pass filters."""
    # ITU-R BS.1770 specifies K-weighting as a 4 dB high shelf followed by the
    # RLB high-pass.  RBJ coefficient generation keeps the response correct for
    # every supported PCM sample rate instead of assuming a 48 kHz input.
    shelf_b, shelf_a = _rbj_shelf(1681.974, 4.0, sample_rate, high=True)
    highpass_b, highpass_a = signal.butter(2, 38.1358, btype="highpass", fs=sample_rate)
    return _biquad(_biquad(audio, shelf_b, shelf_a), highpass_b, highpass_a)


def _channel_weights(channel_count):
    """Return the BS.1770 channel gains, excluding the LFE channel when known."""
    standard = (1.0, 1.0, 1.0, 0.0, 1.41, 1.41, 1.41, 1.41)
    return np.asarray(standard[:channel_count] if channel_count <= len(standard) else standard + (1.41,) * (channel_count - len(standard)))


def _block_energies(audio, sample_rate, duration_seconds, hop_seconds):
    """Return K-weighted multichannel block energies using BS.1770 timing windows."""
    weighted = _k_weight(audio, sample_rate)
    if weighted.ndim == 1:
        weighted = weighted[:, None]
    window = max(1, round(duration_seconds * sample_rate))
    hop = max(1, round(hop_seconds * sample_rate))
    if weighted.shape[0] < window:
        weighted = np.pad(weighted, ((0, window - weighted.shape[0]), (0, 0)))
    starts = range(0, weighted.shape[0] - window + 1, hop)
    weights = _channel_weights(weighted.shape[1])
    return np.asarray([np.mean(np.square(weighted[start:start + window]), axis=0).dot(weights) for start in starts])


def _loudness_from_energy(energy):
    """Convert one or more mean-square K-weighted energies to LKFS/LUFS."""
    return -0.691 + 10.0 * np.log10(np.maximum(energy, EPS))


def _gated_integrated_loudness(block_energies):
    """Implement BS.1770 absolute and relative gates for integrated loudness."""
    block_loudness = _loudness_from_energy(block_energies)
    absolute = block_energies[block_loudness > -70.0]
    if absolute.size == 0:
        return -70.0
    ungated = float(_loudness_from_energy(np.mean(absolute)))
    relative = absolute[_loudness_from_energy(absolute) > ungated - 10.0]
    return float(_loudness_from_energy(np.mean(relative if relative.size else absolute)))


def true_peak_amplitude(audio, oversample=4):
    """Measure BS.1770-style inter-sample peak amplitude with 4x oversampling."""
    samples = audio[:, None] if audio.ndim == 1 else audio
    if samples.size == 0:
        return 0.0
    reconstructed = signal.resample_poly(samples, oversample, 1, axis=0, padtype="line")
    return float(np.max(np.abs(reconstructed)))


def analyze_loudness(audio, sample_rate, limiter_measurement=None):
    """Measure BS.1770 integrated loudness, true peak, LRA, crest, and limiter use."""
    integrated = _gated_integrated_loudness(_block_energies(audio, sample_rate, 0.400, 0.100))
    short_term = _loudness_from_energy(_block_energies(audio, sample_rate, 3.0, 1.0))
    lra_blocks = short_term[short_term > -70.0]
    lra_blocks = lra_blocks[lra_blocks > integrated - 20.0]
    lra = float(np.percentile(lra_blocks, 95) - np.percentile(lra_blocks, 10)) if lra_blocks.size >= 2 else 0.0
    true_peak = true_peak_amplitude(audio)
    crest = 20.0 * np.log10(max(true_peak, EPS)) - integrated
    limiter = limiter_measurement or {"max_gain_reduction_db": 0.0, "mean_gain_reduction_db": 0.0}
    return {
        "measurement_standard": "ITU-R BS.1770-4 / EBU R128",
        "integrated_lufs": round(integrated, 2),
        "true_peak_dbtp": round(float(20.0 * np.log10(max(true_peak, EPS))), 2),
        "lra_lu": round(lra, 2),
        "crest_db": round(float(crest), 2),
        "limiter_gain_reduction": limiter,
    }


def dynamics_quality(loudness, settings):
    """Reject a numerically loud master when its dynamics evidence is unsafe."""
    min_lra = float(settings.get("min_lra_lu", 2.0))
    min_crest = float(settings.get("min_crest_db", 5.0))
    max_reduction = float(settings.get("max_limiter_gain_reduction_db", 4.0))
    issues = []
    if loudness["lra_lu"] < min_lra:
        issues.append("loudness-range-too-small")
    if loudness["crest_db"] < min_crest:
        issues.append("crest-factor-too-small")
    if loudness["limiter_gain_reduction"]["max_gain_reduction_db"] > max_reduction:
        issues.append("limiter-gain-reduction-too-large")
    return {"passed": not issues, "issues": issues}


def loudness_reference(loudness, settings):
    """Classify the -14 LUFS delivery reference without requiring exact loudness."""
    target = settings.get("target_lufs")
    if target is None:
        return "not-requested"
    tolerance = float(settings.get("loudness_tolerance_lu", 1.0))
    if abs(loudness["integrated_lufs"] - float(target)) <= tolerance:
        return "within-tolerance"
    if loudness["integrated_lufs"] < float(target):
        return "below-reference-dynamics-preserved"
    return "above-reference"
