"""Upload-ready MP3 export using the local lameenc encoder."""

import numpy as np
import soundfile as sf

from worker.registry import register_command


@register_command("mp3_export")
def mp3_export_command(request: dict) -> dict:
    input_path = request.get("path", "")
    output_path = request.get("outputPath", "")
    bitrate = int(request.get("bitrateKbps", 320))
    if not input_path or not output_path:
        raise ValueError("Missing path or outputPath")
    if bitrate not in (128, 160, 192, 256, 320):
        raise ValueError("bitrateKbps must be one of 128, 160, 192, 256, 320")
    try:
        import lameenc
    except ImportError as exc:
        raise ValueError("MP3 export requires lameenc. Run `make python-install` after updating worker dependencies.") from exc

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
    with open(output_path, "wb") as output:
        output.write(encoded)
    return {"output": output_path, "bitrateKbps": bitrate}
