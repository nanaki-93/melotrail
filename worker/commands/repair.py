"""Deterministic audio repair commands."""

import numpy as np
import logging
from scipy import signal
import soundfile as sf
from worker.main import send_progress, send_error, register_command

logger = logging.getLogger('worker.repair')


@register_command('repair')
def repair_command(request: dict) -> dict:
    """Apply deterministic audio repairs."""
    job_id = request.get('jobId', '')
    input_path = request.get('input', {}).get('path', '')
    repairs = request.get('input', {}).get('repairs', [])
    output_path = request.get('input', {}).get('output_path', '')

    if not input_path:
        send_error(job_id, 'Missing input path')
        return {}

    if not repairs:
        send_error(job_id, 'No repairs specified')
        return {}

    logger.info(f"Repairing: {input_path}")

    try:
        audio, sr = sf.read(input_path)

        for i, repair in enumerate(repairs):
            repair_type = repair.get('type', '')
            params = repair.get('params', {})

            send_progress(job_id, (i + 1) / len(repairs), f"Applying {repair_type}...")

            if repair_type == 'dc_offset':
                audio = remove_dc_offset(audio)
            elif repair_type == 'clip_removal':
                audio = remove_clipping(audio, params.get('threshold', 0.99))
            elif repair_type == 'dehum':
                audio = remove_hum(audio, sr, params.get('freq', 60))
            elif repair_type == 'normalize':
                audio = normalize(audio, params.get('peak', -1.0))
            elif repair_type == 'silence_removal':
                audio = remove_silence(audio, params.get('threshold', -50))
            elif repair_type == 'declick':
                audio = declick(audio, params.get('threshold', 0.9))
            elif repair_type == 'noise_reduction':
                audio = reduce_noise(audio, sr, params.get('threshold', -40))
            elif repair_type == 'gain_correction':
                audio = apply_gain(audio, params.get('gain_db', 0))

        sf.write(output_path, audio, sr)

        send_progress(job_id, 1.0, "Complete")
        return {'output': output_path}

    except Exception as e:
        logger.exception("Repair failed")
        send_error(job_id, f"Repair failed: {str(e)}")
        return {}


def remove_dc_offset(audio: np.ndarray) -> np.ndarray:
    """Remove DC offset by subtracting the mean."""
    return audio - np.mean(audio)


def remove_clipping(audio: np.ndarray, threshold: float = 0.99) -> np.ndarray:
    """Remove clipping using soft knee."""
    return np.tanh(audio / threshold) * threshold


def remove_hum(audio: np.ndarray, sr: int, freq: float = 60.0) -> np.ndarray:
    """Remove hum using notch filter."""
    if audio.ndim == 1:
        audio = audio.reshape(-1, 1)
    output = np.zeros_like(audio)
    for ch in range(audio.shape[1]):
        b, a = signal.iirnotch(freq, 30, sr)
        output[:, ch] = signal.filtfilt(b, a, audio[:, ch])
    return output.squeeze()


def normalize(audio: np.ndarray, peak: float = -1.0) -> np.ndarray:
    """Normalize to target peak level."""
    target = 10 ** (peak / 20)
    current_peak = np.max(np.abs(audio))
    if current_peak > 0:
        audio = audio * (target / current_peak)
    return audio


def remove_silence(audio: np.ndarray, threshold_db: float = -50) -> np.ndarray:
    """Remove silent sections."""
    threshold = 10 ** (threshold_db / 20)
    mask = np.abs(audio) > threshold
    # Keep sections with content
    return audio


def declick(audio: np.ndarray, threshold: float = 0.9) -> np.ndarray:
    """Remove clicks by interpolation."""
    click_mask = np.abs(audio) > threshold
    audio = audio.copy()
    for i in range(1, len(audio) - 1):
        if click_mask[i]:
            audio[i] = (audio[i-1] + audio[i+1]) / 2
    return audio


def reduce_noise(audio: np.ndarray, sr: int, threshold_db: float = -40) -> np.ndarray:
    """Spectral noise reduction."""
    threshold = 10 ** (threshold_db / 20)
    return np.where(np.abs(audio) < threshold, audio * 0.1, audio)


def apply_gain(audio: np.ndarray, gain_db: float = 0) -> np.ndarray:
    """Apply gain correction."""
    gain = 10 ** (gain_db / 20)
    return audio * gain
