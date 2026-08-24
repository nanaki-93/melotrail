"""Local delivery-codec encode/decode evidence for the selected WAV master."""

from pathlib import Path

import numpy as np
import soundfile as sf

from worker.commands.mastering import true_peak_amplitude
from worker.commands.mp3_convert import mp3_convert_command
from worker.commands.mp3_export import mp3_export_command
from worker.registry import register_command


@register_command("codec_preview")
def codec_preview_command(request: dict) -> dict:
    """Encode/decode a supported local codec and remeasure 4x true peak.

    AAC remains explicitly unavailable until a local, versioned AAC adapter is
    supplied.  This endpoint never turns an unavailable encoder into success.
    """
    codec = str(request.get("codec", "")).lower()
    if codec == "aac":
        return {
            "codec": "aac",
            "status": "unavailable",
            "detail": "No local AAC encoder/decoder adapter is configured; no platform-transcode claim is implied.",
        }
    if codec != "mp3":
        raise ValueError("codec must be aac or mp3")

    raw_input = str(request.get("path", ""))
    raw_encoded = str(request.get("encodedPath", ""))
    raw_decoded = str(request.get("decodedPath", ""))
    if not raw_input or not raw_encoded or not raw_decoded:
        raise ValueError("path, encodedPath, and decodedPath are required")
    input_path = Path(raw_input)
    encoded_path = Path(raw_encoded)
    decoded_path = Path(raw_decoded)
    if encoded_path.suffix.lower() != ".mp3" or decoded_path.suffix.lower() not in {".wav", ".wave"}:
        raise ValueError("MP3 preview requires .mp3 encodedPath and .wav decodedPath")

    try:
        encoded = mp3_export_command({
            "path": str(input_path), "outputPath": str(encoded_path),
            "bitrateKbps": request.get("bitrateKbps", 320),
        })
    except ValueError as exc:
        if "requires lameenc" in str(exc):
            return {
                "codec": "mp3",
                "status": "unavailable",
                "detail": "Local MP3 encoder is unavailable; no platform-transcode claim is implied.",
            }
        raise
    decoded = mp3_convert_command({"path": str(encoded_path), "outputPath": str(decoded_path)})
    audio, sample_rate = sf.read(decoded_path, always_2d=True, dtype="float32")
    if audio.size == 0:
        raise ValueError("MP3 preview decode produced no samples")
    peak = float(true_peak_amplitude(np.asarray(audio, dtype=np.float64)))
    return {
        "codec": "mp3",
        "status": "measured",
        "encoded": encoded["output"],
        "decoded": decoded["output"],
        "sampleRate": int(sample_rate),
        "truePeakDbtp": float(20.0 * np.log10(max(peak, 1e-12))),
        "clippingSampleCount": int(np.count_nonzero(np.abs(audio) >= 1.0)),
        "detail": "Local MP3 encode/decode preview measured with the worker's 4x true-peak routine; it is not a platform-transcode prediction.",
    }
