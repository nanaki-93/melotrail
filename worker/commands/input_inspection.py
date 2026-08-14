"""Read-only, deterministic inspection for MIDI, WAV, and MP3 inputs.

Audio is measured frame-by-frame after decoding. Silence is <= 1e-4 peak per
frame; clipping is >= .999 peak; hum confidence is the largest 50/60 Hz
single-bin projection divided by RMS (0.05/0.15/0.30 thresholds); noise uses
first-difference RMS divided by signal RMS (0.05/0.15/0.30 thresholds).
These deliberately small, bounded indicators only support later opt-in repair
decisions and never alter the supplied input.
"""

from __future__ import annotations

import math
import os
import tempfile
from pathlib import Path

import mido
import librosa
import numpy as np
import soundfile as sf

from worker.errors import InputInspectionDecodeError, InputInspectionValidationError
from worker.registry import register_command

_AUDIO_SUFFIXES = {".wav", ".wave", ".mp3"}
_MIDI_SUFFIXES = {".mid", ".midi"}
_SILENCE_PEAK = 1e-4
_CLIP_PEAK = 0.999
_MAX_FRAMES = 100_000_000


def _require_input(request: dict) -> Path:
    raw = request.get("path")
    if not isinstance(raw, str) or not raw.strip():
        raise InputInspectionValidationError("path is required")
    path = Path(raw).expanduser().resolve(strict=False)
    if not path.is_file():
        raise InputInspectionValidationError("input file does not exist")
    if path.stat().st_size == 0:
        raise InputInspectionValidationError("input file is empty")
    if path.suffix.lower() not in _AUDIO_SUFFIXES | _MIDI_SUFFIXES:
        raise InputInspectionValidationError("input extension must be MIDI, WAV, WAVE, or MP3")
    return path


def _evidence(confidence: float) -> dict:
    confidence = min(1.0, max(0.0, float(confidence)))
    level = "NONE" if confidence < 0.05 else "LOW" if confidence < 0.15 else "MODERATE" if confidence < 0.30 else "HIGH"
    return {"evidence": level, "confidence": confidence}


def _measure(samples: np.ndarray, sample_rate: int) -> dict:
    if samples.ndim != 2 or samples.shape[0] < 1 or samples.shape[1] < 1:
        raise InputInspectionDecodeError("decoded audio has no frames or channels")
    if samples.shape[0] > _MAX_FRAMES:
        raise InputInspectionValidationError("decoded audio exceeds the inspection frame limit")
    if not np.isfinite(samples).all():
        raise InputInspectionDecodeError("decoded audio contains non-finite samples")
    frame_peaks = np.max(np.abs(samples), axis=1)
    silent = frame_peaks <= _SILENCE_PEAK
    clipped = frame_peaks >= _CLIP_PEAK
    clipped_starts = clipped & np.concatenate(([True], ~clipped[:-1]))
    longest = current = 0
    for value in silent:
        current = current + 1 if value else 0
        longest = max(longest, current)
    mono = np.mean(samples, axis=1, dtype=np.float64)
    rms = float(np.sqrt(np.mean(np.square(samples, dtype=np.float64))))
    if rms == 0.0:
        hum = noise = 0.0
    else:
        count = min(len(mono), int(sample_rate * 10))
        window = mono[:count]
        positions = np.arange(count, dtype=np.float64) / sample_rate
        hum = max(abs(float(np.dot(window, np.sin(2 * math.pi * hz * positions)))) for hz in (50.0, 60.0)) * math.sqrt(2.0) / count / rms
        noise = float(np.sqrt(np.mean(np.diff(mono) ** 2))) / rms if len(mono) > 1 else 0.0
    return {
        "peak": float(np.max(frame_peaks)), "rms": rms, "dcOffset": float(np.mean(samples, dtype=np.float64)),
        "clippedRunCount": int(np.count_nonzero(clipped_starts)), "clippedFrameCount": int(np.count_nonzero(clipped)),
        "silence": {"silentFrames": int(np.count_nonzero(silent)), "longestSilentFrames": int(longest)},
        "hum": _evidence(hum), "noise": _evidence(noise),
    }


def _inspect_midi(path: Path) -> dict:
    if path.read_bytes()[:4] != b"MThd":
        raise InputInspectionValidationError("MIDI extension does not match a Standard MIDI container")
    try:
        midi = mido.MidiFile(path)
    except (OSError, EOFError, ValueError, KeyError) as exc:
        raise InputInspectionDecodeError("MIDI input is corrupt or unsupported") from exc
    events = sum(1 for track in midi.tracks for message in track if not message.is_meta)
    duration = float(midi.length)
    if events == 0 or not math.isfinite(duration) or duration <= 0.0:
        raise InputInspectionValidationError("MIDI input contains no playable events")
    return {"container": "MIDI", "codec": f"SMF_{midi.type}", "extension": path.suffix.lower().lstrip("."), "durationSeconds": duration,
            "warnings": [], "toolVersions": {"input-inspector": "1.0", "mido": getattr(mido, "__version__", "unknown")}}


def _inspect_audio(path: Path) -> dict:
    extension = path.suffix.lower()
    header = path.read_bytes()[:12]
    if extension in {".wav", ".wave"}:
        if len(header) < 12 or header[:4] != b"RIFF" or header[8:12] != b"WAVE":
            raise InputInspectionValidationError("WAV extension does not match a RIFF/WAVE container")
        decoded_path = path
    else:
        if not (header.startswith(b"ID3") or header[:2] == b"\xff\xfb" or header[:2] == b"\xff\xf3" or header[:2] == b"\xff\xf2"):
            raise InputInspectionValidationError("MP3 extension does not match an MPEG audio container")
        decoded_path = _decode_mp3_temp(path)
    try:
        info = sf.info(decoded_path)
        if info.samplerate < 1 or info.samplerate > 384000 or info.channels < 1 or info.channels > 32 or info.frames < 1:
            raise InputInspectionValidationError("audio format is unsupported or empty")
        samples = sf.read(decoded_path, dtype="float64", always_2d=True)[0]
        measurement = _measure(samples, info.samplerate)
        codec = info.subtype.replace(" ", "_") if extension != ".mp3" else "MPEG_AUDIO"
        return {"container": "RIFF_WAVE" if extension in {".wav", ".wave"} else "MPEG_AUDIO", "codec": codec,
                "extension": extension.lstrip("."), "durationSeconds": float(info.frames / info.samplerate),
                "audioFormat": {"sampleRate": int(info.samplerate), "channels": int(info.channels), "bitsPerSample": _bits(info.subtype)},
                "measurements": measurement, "warnings": [], "toolVersions": {"input-inspector": "1.0", "soundfile": sf.__version__}}
    except InputInspectionValidationError:
        raise
    except (RuntimeError, OSError, ValueError) as exc:
        raise InputInspectionDecodeError("audio input is corrupt or unsupported") from exc
    finally:
        if decoded_path != path:
            os.unlink(decoded_path)


def _decode_mp3_temp(path: Path) -> Path:
    try:
        decoded, rate = librosa.load(path, sr=None, mono=False, dtype=np.float64)
        decoded = np.asarray(decoded, dtype=np.float64)
        samples = decoded[:, None] if decoded.ndim == 1 else decoded.T
    except (RuntimeError, OSError, ValueError) as exc:
        raise InputInspectionDecodeError("MP3 decoding failed") from exc
    if not np.isfinite(samples).all() or rate < 1:
        raise InputInspectionDecodeError("MP3 decoding produced invalid audio")
    handle = tempfile.NamedTemporaryFile(prefix="input-inspection-", suffix=".wav", delete=False)
    handle.close()
    try:
        sf.write(handle.name, samples, rate, format="WAV", subtype="PCM_24")
    except Exception:
        os.unlink(handle.name)
        raise
    return Path(handle.name)


def _bits(subtype: str) -> int | None:
    for value in (32, 24, 16, 8):
        if str(value) in subtype:
            return value
    return None


@register_command("inspect-input")
def input_inspection_command(request: dict) -> dict:
    path = _require_input(request)
    result = _inspect_midi(path) if path.suffix.lower() in _MIDI_SUFFIXES else _inspect_audio(path)
    result["preparation"] = "INSPECT_ONLY"
    return result
