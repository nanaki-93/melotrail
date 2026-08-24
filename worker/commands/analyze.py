"""Versioned, read-only audio timing evidence for Kotlin-owned decisions."""

import logging

import librosa
import numpy as np
import soundfile as sf

from worker.registry import register_command

logger = logging.getLogger("worker.analyze")
NEAR_SILENCE_RMS = 1e-4
ANALYSIS_CONTRACT_VERSION = 2
MAX_TIMING_POINTS = 256
HOP_LENGTH = 512


@register_command("analyze")
def analyze_command(request: dict) -> dict:
    """Measure bounded timing evidence without changing the requested source."""
    input_path = request.get("path", "")
    if not isinstance(input_path, str) or not input_path:
        raise ValueError("Missing path")
    version = request.get("version", ANALYSIS_CONTRACT_VERSION)
    if version != ANALYSIS_CONTRACT_VERSION:
        raise ValueError(f"Unsupported analyze request version: {version}")

    options = request.get("options", {}) or {}
    if not isinstance(options, dict):
        raise ValueError("Analyze options must be an object")

    logger.info("Analyzing: %s", input_path)
    audio, sample_rate = sf.read(input_path, always_2d=True, dtype="float64")
    audio = np.asarray(audio, dtype=np.float64)
    frames, channels = audio.shape
    duration = frames / sample_rate if sample_rate else 0.0
    peak = float(np.max(np.abs(audio))) if audio.size else 0.0
    rms = float(np.sqrt(np.mean(np.square(audio)))) if audio.size else 0.0
    mono = np.mean(audio, axis=1) if frames and channels > 1 else (audio[:, 0] if frames else np.array([], dtype=np.float64))

    threshold = max(1e-5, peak * 0.01)
    audible = np.flatnonzero(np.abs(mono) >= threshold)
    leading_silence = float(audible[0] / sample_rate) if audible.size else duration
    trailing_silence = float((frames - audible[-1] - 1) / sample_rate) if audible.size else duration
    leading_activity = activity_evidence(int(audible[0]), sample_rate) if audible.size else None
    timing = empty_timing_evidence()
    key_root = None
    key_mode = None
    key_confidence = 0.0

    if not rms <= NEAR_SILENCE_RMS:
        try:
            timing = timing_evidence(mono, sample_rate, options, int(audible[0]))
        except Exception as exc:
            logger.warning("Timing analysis uncertainty for %s: %s", input_path, exc)
            timing = empty_timing_evidence("ANALYSIS_UNAVAILABLE")
        if options.get("detectKey", True) and mono.size:
            try:
                chroma = librosa.feature.chroma_cqt(y=mono.astype(np.float32), sr=sample_rate)
                profile = np.mean(chroma, axis=1)
                pitch = int(np.argmax(profile))
                key_root = ["C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"][pitch]
                key_mode = "minor"  # Conservative: low-confidence harmony remains non-authoritative.
                key_confidence = float(profile[pitch] / (np.sum(profile) + 1e-12))
            except Exception as exc:
                logger.warning("Key analysis uncertainty for %s: %s", input_path, exc)

    return {
        "analysisVersion": ANALYSIS_CONTRACT_VERSION,
        "duration": duration,
        "sampleRate": int(sample_rate),
        "channels": int(channels),
        "frameCount": int(frames),
        "peak": peak,
        "rms": rms,
        "nearSilence": bool(rms <= NEAR_SILENCE_RMS),
        "bpm": timing["tempoCandidates"][0]["bpm"] if timing["tempoCandidates"] else None,
        "key": {"root": key_root, "mode": key_mode} if key_root else None,
        "keyConfidence": key_confidence,
        "leadingSilenceSeconds": leading_silence,
        "trailingSilenceSeconds": trailing_silence,
        "leadingActivity": leading_activity,
        "beats": timing["beats"],
        "onsets": timing["onsets"],
        "tempoCandidates": timing["tempoCandidates"],
        "downbeat": timing["downbeat"],
        "sections": [],
        "qualityIssues": [],
    }


def timing_evidence(mono: np.ndarray, sample_rate: int, options: dict, leading_activity_frame: int | None) -> dict:
    """Return bounded beat, onset, tempo, and explicitly uncertain downbeat evidence."""
    if sample_rate <= 0 or mono.size == 0:
        return empty_timing_evidence()
    signal = mono.astype(np.float32)
    onset_frames = np.array([], dtype=int)
    beat_frames = np.array([], dtype=int)
    if options.get("detectOnsets", True):
        onset_frames = np.asarray(librosa.onset.onset_detect(y=signal, sr=sample_rate, hop_length=HOP_LENGTH), dtype=int)
    if options.get("detectBPM", True) or options.get("detectBeats", True):
        _tempo, detected = librosa.beat.beat_track(y=signal, sr=sample_rate, hop_length=HOP_LENGTH)
        beat_frames = np.asarray(detected, dtype=int)

    onsets = timing_points(onset_frames, sample_rate, strength=1.0)
    beats = timing_points(beat_frames, sample_rate, confidence=beat_confidence(beat_frames, sample_rate))
    candidates = tempo_candidates(beat_frames, sample_rate)
    return {
        "beats": beats,
        "onsets": onsets,
        "tempoCandidates": candidates,
        "downbeat": downbeat_evidence(beats, onsets, leading_activity_frame, sample_rate),
    }


def timing_points(frames: np.ndarray, sample_rate: int, confidence: float | None = None, strength: float | None = None) -> list[dict]:
    """Convert frame positions into a bounded protocol list without inventing events."""
    unique = np.unique(np.asarray(frames, dtype=int))
    result = []
    for frame in unique[unique >= 0][:MAX_TIMING_POINTS]:
        point = {"frame": int(frame), "timeSeconds": finite_time(frame, sample_rate)}
        if confidence is not None:
            point["confidence"] = confidence
        if strength is not None:
            point["strength"] = strength
        result.append(point)
    return result


def tempo_candidates(beat_frames: np.ndarray, sample_rate: int) -> list[dict]:
    """Derive one confidence-scored tempo only from consistent measured beat intervals."""
    beats = np.asarray(beat_frames, dtype=int)
    if beats.size < 2 or sample_rate <= 0:
        return []
    intervals = np.diff(beats).astype(np.float64) * HOP_LENGTH / sample_rate
    intervals = intervals[np.isfinite(intervals) & (intervals > 0.0)]
    if intervals.size == 0:
        return []
    median = float(np.median(intervals))
    accepted = intervals[np.abs(intervals - median) <= median * 0.15]
    if accepted.size == 0:
        return []
    bpm = 60.0 / float(np.median(accepted))
    if not np.isfinite(bpm) or not 30.0 <= bpm <= 300.0:
        return []
    confidence = min(1.0, float(accepted.size) / 8.0) * float(accepted.size) / float(intervals.size)
    return [{"bpm": round(bpm, 6), "confidence": round(confidence, 6), "supportingIntervals": int(accepted.size)}]


def downbeat_evidence(beats: list[dict], onsets: list[dict], leading_activity_frame: int | None, sample_rate: int) -> dict:
    """Never auto-confirm a bar phase from audio-only evidence without authority."""
    if len(beats) < 4:
        return {"status": "UNKNOWN", "reason": "INSUFFICIENT_BEAT_EVIDENCE"}
    candidate = beats[0]
    onset_support = any(abs(onset["frame"] - candidate["frame"]) <= HOP_LENGTH for onset in onsets)
    activity_support = leading_activity_frame is not None and abs(leading_activity_frame - candidate["frame"] * HOP_LENGTH) <= sample_rate
    confidence = 0.25 + (0.15 if onset_support else 0.0) + (0.10 if activity_support else 0.0)
    return {
        "status": "REVIEW_REQUIRED",
        "candidateBeatIndex": 0,
        "timeSeconds": candidate["timeSeconds"],
        "frame": candidate["frame"],
        "confidence": round(confidence, 6),
        "reason": "AUDIO_ONLY_PHASE_IS_NOT_AUTHORITATIVE",
    }


def activity_evidence(frame: int, sample_rate: int) -> dict:
    """Describe the first audible source-sample frame, never a musical downbeat."""
    return {"frame": frame, "timeSeconds": round(float(frame / sample_rate), 9), "confidence": 1.0}


def empty_timing_evidence(reason: str = "INSUFFICIENT_BEAT_EVIDENCE") -> dict:
    """Represent unavailable timing as explicit uncertainty rather than made-up grid data."""
    return {
        "beats": [],
        "onsets": [],
        "tempoCandidates": [],
        "downbeat": {"status": "UNKNOWN", "reason": reason},
    }


def beat_confidence(beat_frames: np.ndarray, sample_rate: int) -> float:
    """Score consistency only; no score is returned when no beat was measured."""
    if len(beat_frames) < 2 or sample_rate <= 0:
        return 0.0
    intervals = np.diff(np.asarray(beat_frames, dtype=float))
    median = float(np.median(intervals))
    if median <= 0.0:
        return 0.0
    return round(float(np.mean(np.abs(intervals - median) <= median * 0.15)), 6)


def finite_time(frame: int, sample_rate: int) -> float:
    """Return a finite non-negative time for one non-negative frame index."""
    return round(float(frame * HOP_LENGTH / sample_rate), 9)
