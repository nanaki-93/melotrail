"""Strict, conservative input-audio cleanup.

Only the five schema operations below are accepted. They are never selected
implicitly: each must be explicitly requested and is skipped when its measured
evidence is below the documented threshold. Processing preserves frame count;
there is no tail allowance for the current primitives.
"""

from __future__ import annotations

import hashlib
import math
from pathlib import Path

import numpy as np
import soundfile as sf

from worker.commands.audio_output import write_pcm24_wav
from worker.commands.repair import declick, reduce_noise, remove_clipping, remove_dc_offset, remove_hum
from worker.errors import AudioCleanupOutputError, AudioCleanupValidationError
from worker.registry import register_command

_ALLOWED = {"dc_removal", "clip_repair", "declick", "hum_removal", "noise_reduction"}
_DC_THRESHOLD = 0.005
_CLIP_THRESHOLD = 0.999
_DECLICK_JUMP = 0.25
_HUM_CONFIDENCE = 0.15
_NOISE_CONFIDENCE = 0.15
_MAX_FRAMES = 100_000_000


def _validate_path(value: object, label: str) -> Path:
    if not isinstance(value, str) or not value.strip():
        raise AudioCleanupValidationError(f"{label} is required")
    path = Path(value).expanduser().resolve(strict=False)
    if label == "path":
        if not path.is_file() or path.stat().st_size == 0:
            raise AudioCleanupValidationError("input file does not exist or is empty")
        if path.suffix.lower() not in {".wav", ".wave"}:
            raise AudioCleanupValidationError("input extension must be WAV or WAVE")
        header = path.read_bytes()[:12]
        if len(header) < 12 or header[:4] != b"RIFF" or header[8:12] != b"WAVE":
            raise AudioCleanupValidationError("WAV extension does not match a RIFF/WAVE container")
    elif path.suffix.lower() != ".wav":
        raise AudioCleanupValidationError("outputPath must end in .wav")
    return path


def _read_audio(path: Path) -> tuple[np.ndarray, int]:
    try:
        info = sf.info(path)
        if not (1 <= info.samplerate <= 384000 and 1 <= info.channels <= 32 and 1 <= info.frames <= _MAX_FRAMES):
            raise AudioCleanupValidationError("audio format is unsupported or empty")
        samples = sf.read(path, dtype="float64", always_2d=True)[0]
    except AudioCleanupValidationError:
        raise
    except (OSError, RuntimeError, ValueError) as exc:
        raise AudioCleanupValidationError("input audio is corrupt or unsupported") from exc
    if not np.isfinite(samples).all():
        raise AudioCleanupValidationError("input audio contains non-finite samples")
    return samples, int(info.samplerate)


def _confidence(value: float) -> float:
    return min(1.0, max(0.0, float(value)))


def _metrics(samples: np.ndarray, sample_rate: int) -> dict:
    frame_peaks = np.max(np.abs(samples), axis=1)
    mono = np.mean(samples, axis=1, dtype=np.float64)
    rms = float(np.sqrt(np.mean(np.square(samples, dtype=np.float64))))
    dc = float(np.mean(samples, dtype=np.float64))
    clipped = frame_peaks >= _CLIP_THRESHOLD
    starts = clipped & np.concatenate(([True], ~clipped[:-1]))
    jump = float(np.max(np.abs(np.diff(mono)))) if len(mono) > 1 else 0.0
    if rms == 0.0:
        hum = noise = 0.0
    else:
        count = min(len(mono), sample_rate * 10)
        positions = np.arange(count, dtype=np.float64) / sample_rate
        hum = max(abs(float(np.dot(mono[:count], np.sin(2 * math.pi * hz * positions)))) for hz in (50.0, 60.0)) * math.sqrt(2.0) / count / rms
        noise = float(np.sqrt(np.mean(np.diff(mono) ** 2))) / rms if len(mono) > 1 else 0.0
    return {"peak": float(np.max(frame_peaks)), "rms": rms, "dcOffset": dc,
            "clippedRunCount": int(np.count_nonzero(starts)), "clippedFrameCount": int(np.count_nonzero(clipped)),
            "maxFrameJump": jump, "humConfidence": _confidence(hum), "noiseConfidence": _confidence(noise)}


def _operations(value: object) -> list[dict]:
    if not isinstance(value, list):
        raise AudioCleanupValidationError("operations must be a list")
    if len(value) > len(_ALLOWED):
        raise AudioCleanupValidationError("operations may contain at most five entries")
    parsed: list[dict] = []
    seen: set[str] = set()
    for operation in value:
        if not isinstance(operation, dict) or set(operation) - {"type", "params"}:
            raise AudioCleanupValidationError("each operation must contain only type and optional params")
        kind = operation.get("type")
        params = operation.get("params", {})
        if kind not in _ALLOWED or kind in seen or not isinstance(params, dict):
            raise AudioCleanupValidationError("operation type is unknown, duplicated, or has invalid params")
        seen.add(kind)
        parsed.append({"type": kind, "params": _params(kind, params)})
    return parsed


def _params(kind: str, params: dict) -> dict:
    allowed = {
        "dc_removal": set(), "clip_repair": {"threshold"}, "declick": {"threshold"},
        "hum_removal": {"frequencyHz"}, "noise_reduction": {"strength"},
    }[kind]
    if set(params) - allowed:
        raise AudioCleanupValidationError(f"{kind} has unsupported parameters")
    def bounded(name: str, low: float, high: float, default: float) -> float:
        value = params.get(name, default)
        if isinstance(value, bool) or not isinstance(value, (int, float)) or not math.isfinite(value) or not low <= float(value) <= high:
            raise AudioCleanupValidationError(f"{kind}.{name} must be between {low} and {high}")
        return float(value)
    if kind == "clip_repair": return {"threshold": bounded("threshold", 0.95, 1.0, _CLIP_THRESHOLD)}
    if kind == "declick": return {"threshold": bounded("threshold", 0.5, 0.99, 0.9)}
    if kind == "hum_removal":
        frequency = params.get("frequencyHz", 60)
        if frequency not in (50, 60): raise AudioCleanupValidationError("hum_removal.frequencyHz must be 50 or 60")
        return {"frequencyHz": float(frequency)}
    if kind == "noise_reduction": return {"strength": bounded("strength", 0.05, 0.5, 0.35)}
    return {}


def _has_repairable_clip_run(samples: np.ndarray, threshold: float) -> bool:
    """Match the existing primitive's interior, maximum-32-frame safety limit."""
    for channel in range(samples.shape[1]):
        indexes = np.flatnonzero(np.abs(samples[:, channel]) >= threshold)
        if not len(indexes):
            continue
        starts = indexes[np.r_[True, np.diff(indexes) > 1]]
        ends = indexes[np.r_[np.diff(indexes) > 1, True]]
        if any(0 < start <= end < len(samples) - 1 and end - start + 1 <= 32 for start, end in zip(starts, ends)):
            return True
    return False


def _apply(samples: np.ndarray, rate: int, operation: dict, before: dict) -> tuple[np.ndarray, str | None]:
    kind, params = operation["type"], operation["params"]
    if kind == "dc_removal":
        return (remove_dc_offset(samples), None) if abs(before["dcOffset"]) >= _DC_THRESHOLD else (samples, "DC offset below 0.005")
    if kind == "clip_repair":
        return (remove_clipping(samples, params["threshold"]), None) if _has_repairable_clip_run(samples, params["threshold"]) else (samples, "no interior clipped run of 32 frames or fewer")
    if kind == "declick":
        return (declick(samples, params["threshold"]), None) if before["maxFrameJump"] >= _DECLICK_JUMP else (samples, "no isolated jump at or above 0.25")
    if kind == "hum_removal":
        return (remove_hum(samples, rate, params["frequencyHz"]), None) if before["humConfidence"] >= _HUM_CONFIDENCE else (samples, "hum confidence below 0.15")
    if kind == "noise_reduction":
        if len(samples) < (2048 if rate >= 32000 else 1024): return samples, "audio is too short for stationary-noise analysis"
        return (reduce_noise(samples, rate, strength=params["strength"]), None) if before["noiseConfidence"] >= _NOISE_CONFIDENCE else (samples, "noise confidence below 0.15")
    raise AssertionError(kind)


@register_command("cleanup")
def cleanup_command(request: dict) -> dict:
    input_path = _validate_path(request.get("path"), "path")
    output_path = _validate_path(request.get("outputPath"), "outputPath")
    if input_path == output_path:
        raise AudioCleanupValidationError("outputPath must not overwrite the input")
    operations = _operations(request.get("operations", []))
    original_hash = hashlib.sha256(input_path.read_bytes()).hexdigest()
    samples, rate = _read_audio(input_path)
    before = _metrics(samples, rate)
    output = samples
    applied, skipped, warnings = [], [], []
    for operation in operations:
        candidate, reason = _apply(output, rate, operation, before)
        if reason:
            skipped.append({"type": operation["type"], "reason": reason})
            warnings.append(f"{operation['type']}: {reason}")
        else:
            output = candidate
            applied.append(operation)
    if not np.isfinite(output).all() or output.shape != samples.shape:
        raise AudioCleanupOutputError("cleanup produced invalid or duration-changing audio")
    try:
        write_pcm24_wav(str(output_path), output, rate)
        published, published_rate = _read_audio(output_path)
    except (OSError, RuntimeError, ValueError) as exc:
        raise AudioCleanupOutputError("cleanup output could not be published") from exc
    if published_rate != rate or published.shape != samples.shape or not np.isfinite(published).all():
        raise AudioCleanupOutputError("published cleanup output did not preserve format and duration")
    if hashlib.sha256(input_path.read_bytes()).hexdigest() != original_hash:
        raise AudioCleanupOutputError("input changed during cleanup")
    return {"output": str(output_path), "sampleRate": rate, "channels": int(samples.shape[1]), "frames": int(samples.shape[0]),
            "before": before, "after": _metrics(published, rate), "appliedOperations": applied, "skippedOperations": skipped,
            "warnings": warnings, "toolVersions": {"audio-cleanup": "1.0", "numpy": np.__version__, "scipy": __import__("scipy").__version__, "soundfile": sf.__version__}}
