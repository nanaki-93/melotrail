"""Audio analysis command."""

import logging
import librosa
import numpy as np
import soundfile as sf

from worker.registry import register_command

logger = logging.getLogger("worker.analyze")
NEAR_SILENCE_RMS = 1e-4


@register_command("analyze")
def analyze_command(request: dict) -> dict:
    input_path = request.get("path", "")
    if not input_path:
        raise ValueError("Missing path")

    logger.info("Analyzing: %s", input_path)
    audio, sample_rate = sf.read(input_path, always_2d=True, dtype="float64")
    audio = np.asarray(audio, dtype=np.float64)
    frames, channels = audio.shape
    duration = frames / sample_rate if sample_rate else 0.0
    peak = float(np.max(np.abs(audio))) if audio.size else 0.0
    rms = float(np.sqrt(np.mean(np.square(audio)))) if audio.size else 0.0

    options = request.get("options", {}) or {}
    mono = np.mean(audio, axis=1) if channels > 1 else audio[:, 0]
    threshold = max(1e-5, peak * 0.01)
    audible = np.flatnonzero(np.abs(mono) >= threshold)
    leading_silence = float(audible[0] / sample_rate) if audible.size else duration
    trailing_silence = float((frames - audible[-1] - 1) / sample_rate) if audible.size else duration
    bpm = None
    onsets = []
    key_root = None
    key_mode = None
    key_confidence = 0.0
    try:
        if options.get("detectBPM", True):
            tempo, beat_frames = librosa.beat.beat_track(y=mono.astype(np.float32), sr=sample_rate)
            bpm = float(np.asarray(tempo).reshape(-1)[0]) if np.size(tempo) else None
        if options.get("detectOnsets", True):
            onsets = librosa.frames_to_time(
                librosa.onset.onset_detect(y=mono.astype(np.float32), sr=sample_rate), sr=sample_rate
            ).astype(float).tolist()[:256]
        if options.get("detectKey", True) and mono.size:
            chroma = librosa.feature.chroma_cqt(y=mono.astype(np.float32), sr=sample_rate)
            profile = np.mean(chroma, axis=1)
            pitch = int(np.argmax(profile))
            key_root = ["C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"][pitch]
            key_mode = "minor"  # conservative: rendering treats low-confidence harmony as non-pitched
            key_confidence = float(profile[pitch] / (np.sum(profile) + 1e-12))
    except Exception as exc:
        logger.warning("Musical analysis fallback for %s: %s", input_path, exc)

    return {
        "duration": duration,
        "sampleRate": int(sample_rate),
        "channels": int(channels),
        "frameCount": int(frames),
        "peak": peak,
        "rms": rms,
        "nearSilence": bool(rms <= NEAR_SILENCE_RMS),
        "bpm": bpm,
        "key": {"root": key_root, "mode": key_mode} if key_root else None,
        "keyConfidence": key_confidence,
        "leadingSilenceSeconds": leading_silence,
        "trailingSilenceSeconds": trailing_silence,
        "onsets": onsets,
        "sections": [],
        "onsets": [],
        "qualityIssues": [],
    }
