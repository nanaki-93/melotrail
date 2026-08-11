"""MP3 to WAV conversion command."""

import logging
from worker.main import send_progress, send_response, send_error, register_command

logger = logging.getLogger('worker.mp3_convert')


@register_command('mp3_convert')
def mp3_convert_command(request: dict) -> dict:
    """Convert MP3 file to WAV using librosa."""
    job_id = request.get('jobId', '')
    input_path = request.get('input', {}).get('path', '')
    output_path = request.get('input', {}).get('output_path', '')

    if not input_path:
        send_error(job_id, 'Missing input path')
        return {}

    if not output_path:
        send_error(job_id, 'Missing output path')
        return {}

    logger.info(f"Converting MP3: {input_path} -> {output_path}")

    try:
        send_progress(job_id, 0.0, "Loading audio...")

        import librosa
        import soundfile as sf
        import numpy as np

        # Load MP3 with librosa
        audio, sr = librosa.load(input_path, sr=None, mono=False)

        send_progress(job_id, 0.5, "Encoding WAV...")

        # Convert to 16-bit PCM if needed
        if audio.dtype != np.int16:
            audio = (audio * 32767).astype(np.int16)

        # Write WAV
        sf.write(output_path, audio.T, sr, subtype='PCM_16')

        send_progress(job_id, 1.0, "Complete")

        return {
            'output': output_path,
            'sampleRate': int(sr),
            'channels': audio.shape[0] if audio.ndim > 1 else 1,
            'duration': float(len(audio[0]) / sr) if audio.ndim > 1 else float(len(audio) / sr)
        }

    except Exception as e:
        logger.exception("MP3 conversion failed")
        send_error(job_id, f"MP3 conversion failed: {str(e)}")
        return {}
