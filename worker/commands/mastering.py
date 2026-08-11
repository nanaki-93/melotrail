"""Mastering chain command."""

import numpy as np
import logging
from scipy import signal
import soundfile as sf
from worker.main import send_progress, send_error, register_command

logger = logging.getLogger('worker.mastering')


@register_command('master')
def master_command(request: dict) -> dict:
    """Apply mastering chain."""
    job_id = request.get('jobId', '')
    input_path = request.get('input', {}).get('path', '')
    settings = request.get('input', {}).get('settings', {})
    output_path = request.get('input', {}).get('output_path', '')

    if not input_path:
        send_error(job_id, 'Missing input path')
        return {}

    logger.info(f"Mastering: {input_path}")

    try:
        audio, sr = sf.read(input_path)

        # Stage 1: EQ
        if settings.get('eq_enabled', False):
            audio = apply_eq(audio, sr, settings.get('eq', {}))
            send_progress(job_id, 0.15, "EQ")

        # Stage 2: Compressor
        if settings.get('compressor_enabled', False):
            audio = apply_compressor(audio, settings.get('compressor', {}))
            send_progress(job_id, 0.35, "Compression")

        # Stage 3: Saturation
        if settings.get('saturation_enabled', False):
            audio = apply_saturation(audio, settings.get('saturation', {}))
            send_progress(job_id, 0.55, "Saturation")

        # Stage 4: Stereo processing
        if settings.get('stereo_enabled', False):
            audio = apply_stereo(audio, settings.get('stereo', {}))
            send_progress(job_id, 0.70, "Stereo")

        # Stage 5: Limiter
        if settings.get('limiter_enabled', False):
            audio = apply_limiter(audio, settings.get('limiter', {}))
            send_progress(job_id, 0.85, "Limiting")

        # Loudness analysis
        loudness = analyze_loudness(audio, sr)

        # Normalize to target peak
        target_peak = 10 ** (settings.get('target_peak_db', -1.0) / 20)
        current_peak = np.max(np.abs(audio))
        if current_peak > 0:
            audio = audio * (target_peak / current_peak)

        sf.write(output_path, audio, sr)

        send_progress(job_id, 1.0, "Complete")
        return {
            'output': output_path,
            'loudness': loudness
        }

    except Exception as e:
        logger.exception("Mastering failed")
        send_error(job_id, f"Mastering failed: {str(e)}")
        return {}


def apply_eq(audio: np.ndarray, sr: int, eq_settings: dict) -> np.ndarray:
    """Apply parametric EQ bands."""
    output = audio.copy()
    for band in eq_settings.get('bands', []):
        freq = band.get('frequency', 1000)
        gain_db = band.get('gain', 0)
        q = band.get('q', 1.0)
        band_type = band.get('type', 'peaking')

        gain = 10 ** (gain_db / 20)

        if band_type == 'lowshelf':
            # Simple low shelf
            b, a = signal.butter(2, freq * 2 / sr, btype='low')
            low = signal.filtfilt(b, a, output)
            output = output * (1 - gain) + low * gain
        elif band_type == 'highshelf':
            b, a = signal.butter(2, freq * 2 / sr, btype='high')
            high = signal.filtfilt(b, a, output)
            output = output * (1 - gain) + high * gain
        elif band_type == 'peaking':
            w0 = 2 * np.pi * freq / sr
            alpha = np.sin(w0) / (2 * q)
            cos_w0 = np.cos(w0)
            # Biquad peaking filter
            b0 = 1 + alpha * gain
            b1 = -2 * cos_w0
            b2 = 1 - alpha * gain
            a0 = 1 + alpha / gain
            a1 = -2 * cos_w0
            a2 = 1 - alpha / gain
            b, a = [b0/a0, b1/a0, b2/a0], [1, a1/a0, a2/a0]
            output = signal.filtfilt(b, a, output)

    return output


def apply_compressor(audio: np.ndarray, settings: dict) -> np.ndarray:
    """Apply dynamic range compression."""
    threshold = 10 ** (settings.get('threshold_db', -24) / 20)
    ratio = settings.get('ratio', 4.0)
    attack_ms = settings.get('attack_ms', 10)
    release_ms = settings.get('release_ms', 100)

    envelope = np.abs(audio)
    gain_reduction = np.ones_like(envelope)

    # Simple compressor
    for i in range(1, len(envelope)):
        if envelope[i] > threshold:
            excess = envelope[i] - threshold
            reduction = 1 - (1 / ratio)
            gain_reduction[i] = 1 - (excess * reduction / (threshold + 0.001))
        else:
            gain_reduction[i] = 1.0

    # Smooth gain reduction
    attack_samples = int(attack_ms / 1000 * len(audio) / (len(audio) / max(1, len(audio))))
    release_samples = int(release_ms / 1000 * len(audio) / (len(audio) / max(1, len(audio))))

    return audio * np.clip(gain_reduction, 0, 1)


def apply_saturation(audio: np.ndarray, settings: dict) -> np.ndarray:
    """Apply tape or tube saturation."""
    amount = settings.get('amount', 0.5)
    drive = 1 + amount * 3
    saturated = np.tanh(audio * drive)
    return saturated * (1 - amount * 0.3)


def apply_stereo(audio: np.ndarray, settings: dict) -> np.ndarray:
    """Apply M/S stereo processing."""
    width = settings.get('width', 1.0)
    if audio.ndim == 1:
        return audio

    mid = (audio[0] + audio[1]) / 2
    side = (audio[0] - audio[1]) / 2
    side = side * width
    return np.array([mid + side, mid - side])


def apply_limiter(audio: np.ndarray, settings: dict) -> np.ndarray:
    """Apply hard limiter."""
    ceiling = 10 ** (settings.get('ceiling_db', -1.0) / 20)
    return np.clip(audio, -ceiling, ceiling)


def analyze_loudness(audio: np.ndarray, sr: int) -> dict:
    """Compute loudness metrics (simplified ITU-R BS.1770)."""
    # K-weighting approximation
    weighted = audio ** 2
    integrated_lufs = 10 * np.log10(np.mean(weighted) + 1e-10) + 0.691
    true_peak = float(np.max(np.abs(audio)))
    rms = float(10 ** (integrated_lufs / 10 - 0.691))

    return {
        'integrated_lufs': round(integrated_lufs, 1),
        'true_peak_db': round(20 * np.log10(true_peak + 1e-10), 1),
        'rms_db': round(20 * np.log10(rms + 1e-10), 1)
    }
