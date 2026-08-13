"""Final MP3 export using the optional local lameenc encoder."""

import os
from pathlib import Path
from uuid import uuid4

import numpy as np
import soundfile as sf

from worker.registry import register_command

SUPPORTED_BITRATES = {128, 160, 192, 256, 320}


@register_command("mp3_export")
def mp3_export_command(request: dict) -> dict:
    """Encode one validated RIFF/WAVE master and publish it atomically."""
    raw_input = request.get("path", "")
    raw_output = request.get("outputPath", "")
    if not raw_input or not raw_output:
        raise ValueError("Missing path or outputPath")
    input_path = Path(str(raw_input)).expanduser()
    output_path = Path(str(raw_output)).expanduser()
    bitrate = _bitrate(request.get("bitrateKbps", 320))
    _validate_paths(input_path, output_path)
    _validate_riff_wave(input_path)
    try:
        import lameenc
    except ImportError as exc:
        raise ValueError(
            "MP3 export requires lameenc. Run `make python-install` after updating worker dependencies."
        ) from exc

    audio, sample_rate = sf.read(input_path, always_2d=True, dtype="float32")
    if audio.size == 0:
        raise ValueError("Input audio is empty")
    pcm = (np.clip(audio, -1.0, 1.0) * 32767.0).astype("<i2").tobytes()
    encoder = lameenc.Encoder()
    encoder.set_bit_rate(bitrate)
    encoder.set_in_sample_rate(int(sample_rate))
    encoder.set_channels(int(audio.shape[1]))
    encoder.set_quality(2)
    encoded = encoder.encode(pcm) + encoder.flush()
    if not encoded:
        raise ValueError("MP3 encoder produced no data")

    output_path.parent.mkdir(parents=True, exist_ok=True)
    temporary = output_path.with_name(f".{output_path.name}.{uuid4()}.tmp")
    try:
        temporary.write_bytes(encoded)
        _validate_mp3(temporary)
        os.replace(temporary, output_path)
    finally:
        temporary.unlink(missing_ok=True)
    return {
        "output": str(output_path),
        "bitrateKbps": bitrate,
        "sampleRate": int(sample_rate),
        "channels": int(audio.shape[1]),
        "encoder": "lameenc",
    }


def _bitrate(value: object) -> int:
    try:
        bitrate = int(value)
    except (TypeError, ValueError) as exc:
        raise ValueError("bitrateKbps must be an integer") from exc
    if bitrate not in SUPPORTED_BITRATES:
        raise ValueError("bitrateKbps must be one of 128, 160, 192, 256, 320")
    return bitrate


def _validate_paths(input_path: Path, output_path: Path) -> None:
    if input_path.suffix.lower() not in {".wav", ".wave"}:
        raise ValueError("MP3 export input must be a .wav master")
    if output_path.suffix.lower() != ".mp3":
        raise ValueError("MP3 export output must use a .mp3 extension")
    if not input_path.is_file():
        raise ValueError(f"Input WAV does not exist: {input_path}")
    if output_path.exists() and output_path.is_dir():
        raise ValueError(f"Output path is a directory: {output_path}")
    if input_path.resolve() == output_path.resolve():
        raise ValueError("Input and output paths must differ")


def _validate_riff_wave(path: Path) -> None:
    header = path.read_bytes()[:12]
    if len(header) < 12 or header[:4] != b"RIFF" or header[8:12] != b"WAVE":
        raise ValueError("MP3 export input must be a validated RIFF/WAVE file")


def _validate_mp3(path: Path) -> None:
    data = path.read_bytes()
    if not data:
        raise ValueError("MP3 encoder produced an empty file")
    if data[:4] == b"RIFF":
        raise ValueError("MP3 encoder produced a RIFF/WAVE container disguised as MP3")
    has_id3 = data.startswith(b"ID3")
    has_frame = any(data[index] == 0xFF and data[index + 1] & 0xE0 == 0xE0 for index in range(len(data) - 1))
    if not has_id3 and not has_frame:
        raise ValueError("MP3 encoder output has no ID3 or MPEG frame signature")
